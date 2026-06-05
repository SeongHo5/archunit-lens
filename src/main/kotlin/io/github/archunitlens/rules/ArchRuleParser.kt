package io.github.archunitlens.rules

import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression

/**
 * Converts supported Java ArchUnit DSL method chains into live inspection rules.
 *
 * Unsupported or ambiguous chains return `null` rather than partially guessing.
 */
object ArchRuleParser {
    fun parse(source: ArchRuleSource): LiveArchRule? {
        val calls = MethodCallChain.from(source.initializer)
        if (calls.isEmpty()) return null

        return parsePackageDependencyBan(source, calls)
            ?: parseClassNameSuffix(source, calls)
            ?: parseForbiddenAnnotation(source, calls)
            ?: parseAnnotationExclusivity(source, calls)
    }

    private fun parsePackageDependencyBan(
        source: ArchRuleSource,
        calls: List<MethodCall>,
    ): PackageDependencyBanRule? {
        if (calls.firstOrNull()?.name != "noClasses") return null
        if (calls.any { it.name == "resideInAnyPackage" }) return null

        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        val dependencyIndex = calls.indexOfFirst { it.name == "dependOnClassesThat" }
        if (shouldIndex < 0 || dependencyIndex < 0 || dependencyIndex < shouldIndex) return null

        val sourcePattern = calls.take(shouldIndex)
            .firstStringArg("resideInAPackage") ?: return null
        val forbiddenPatterns = calls.drop(dependencyIndex + 1)
            .flatMap { call ->
                when (call.name) {
                    "resideInAPackage" -> call.stringArgs
                    else -> emptyList()
                }
            }
        if (forbiddenPatterns.isEmpty()) return null

        return PackageDependencyBanRule(
            ruleName = source.ruleName,
            sourcePackagePattern = sourcePattern,
            forbiddenPackagePatterns = forbiddenPatterns,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseClassNameSuffix(
        source: ArchRuleSource,
        calls: List<MethodCall>,
    ): ClassNameSuffixRule? {
        if (calls.firstOrNull()?.name != "classes") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null

        val sourcePattern = calls.take(shouldIndex)
            .firstStringArg("resideInAPackage") ?: return null
        val suffix = calls.firstStringArg("haveSimpleNameEndingWith") ?: return null

        return ClassNameSuffixRule(
            ruleName = source.ruleName,
            sourcePackagePattern = sourcePattern,
            requiredSuffix = suffix,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseForbiddenAnnotation(
        source: ArchRuleSource,
        calls: List<MethodCall>,
    ): ForbiddenAnnotationRule? {
        if (calls.firstOrNull()?.name != "noClasses") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null

        val sourcePattern = calls.take(shouldIndex)
            .firstStringArg("resideInAPackage") ?: return null
        val annotationName = calls.firstOrNull { it.name == "beAnnotatedWith" }
            ?.classLiteralArgs
            ?.firstOrNull()
            ?: return null

        return ForbiddenAnnotationRule(
            ruleName = source.ruleName,
            sourcePackagePattern = sourcePattern,
            forbiddenAnnotationQualifiedName = qualifyClassLiteral(annotationName, source.initializer) ?: return null,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun parseAnnotationExclusivity(
        source: ArchRuleSource,
        calls: List<MethodCall>,
    ): AnnotationExclusivityRule? {
        if (calls.firstOrNull()?.name != "classes") return null
        val shouldIndex = calls.indexOfFirst { it.name == "should" }
        if (shouldIndex < 0) return null

        val requiredAnnotation = calls
            .take(shouldIndex)
            .firstAnnotationArg("areAnnotatedWith", source.initializer)
            ?: return null
        val forbiddenAnnotation = calls
            .drop(shouldIndex + 1)
            .firstAnnotationArg("notBeAnnotatedWith", source.initializer)
            ?: return null

        return AnnotationExclusivityRule(
            ruleName = source.ruleName,
            requiredAnnotationQualifiedName = requiredAnnotation,
            forbiddenAnnotationQualifiedName = forbiddenAnnotation,
            sourcePointer = source.fieldPointer,
            analyzeScope = source.analyzeScope,
            reason = calls.reason(),
        )
    }

    private fun List<MethodCall>.firstStringArg(methodName: String): String? = firstOrNull { it.name == methodName }?.stringArgs?.firstOrNull()

    private fun List<MethodCall>.reason(): String? = firstStringArg("because")

    private fun List<MethodCall>.firstAnnotationArg(
        methodName: String,
        context: PsiExpression,
    ): String? {
        val call = firstOrNull { it.name == methodName } ?: return null
        return call.stringArgs.firstOrNull()
            ?: call.classLiteralArgs.firstOrNull()?.let { qualifyClassLiteral(it, context) }
    }

    private fun qualifyClassLiteral(name: String, context: PsiExpression): String? {
        if (name.contains('.')) return name
        val javaFile = context.containingFile as? PsiJavaFile ?: return null
        return javaFile.importList
            ?.allImportStatements
            ?.filterIsInstance<PsiImportStatement>()
            ?.firstOrNull { it.qualifiedName?.endsWith(".$name") == true }
            ?.qualifiedName
    }
}

private data class MethodCall(
    val name: String,
    val stringArgs: List<String>,
    val classLiteralArgs: List<String>,
)

private object MethodCallChain {
    fun from(expression: PsiExpression): List<MethodCall> {
        val calls = mutableListOf<MethodCall>()
        expression.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                expression.methodExpression.referenceName?.let { methodName ->
                    calls += MethodCall(
                        name = methodName,
                        stringArgs = expression.argumentList.expressions.mapNotNull { it.stringLiteralValue() },
                        classLiteralArgs = expression.argumentList.expressions.mapNotNull { it.classLiteralName() },
                    )
                }
                super.visitMethodCallExpression(expression)
            }
        })
        return calls.asReversed()
    }

    private fun PsiExpression.stringLiteralValue(): String? = (this as? PsiLiteralExpression)?.value as? String

    private fun PsiExpression.classLiteralName(): String? = (this as? PsiClassObjectAccessExpression)?.operand?.type?.canonicalText
}

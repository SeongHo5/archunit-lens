package io.github.archunitlens.rules

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

/**
 * Finds ArchUnit rule fields that are safe for the conservative parser to read.
 */
object ArchRuleSourceFinder {
    /**
     * Lightweight text prefilter for files that can contain supported ArchUnit rule
     * sources. It avoids walking PSI trees for ordinary Java files during
     * project-level discovery while keeping the precise checks in [findInFile].
     */
    fun mayContainArchRuleSources(file: PsiJavaFile): Boolean {
        val text = file.text
        return text.contains("ArchTest") && text.contains("ArchRule")
    }

    fun findInFile(file: PsiFile): List<ArchRuleSource> = PsiTreeUtil.findChildrenOfType(file, PsiField::class.java)
        .filter { it.hasArchTestAnnotation() && it.isStaticFinal() && it.isArchRuleField() && !it.isArchIgnored() }
        .mapNotNull { field ->
            val initializer = field.initializer ?: return@mapNotNull null
            ArchRuleSource(
                ruleName = field.name,
                fieldPointer = SmartPointerManager.createPointer(field),
                initializer = initializer,
                analyzeScope = field.containingClass?.analyzeScope() ?: AnalyzeScope.All,
            )
        }

    private fun PsiField.isArchIgnored(): Boolean = modifierList?.annotations?.any { it.isArchIgnoreAnnotation() } == true ||
        containingClass?.modifierList?.annotations?.any { it.isArchIgnoreAnnotation() } == true

    private fun PsiAnnotation.isArchIgnoreAnnotation(): Boolean {
        val reference = nameReferenceElement ?: return false
        if (reference.referenceName != "ArchIgnore") return false
        val annotationClass = reference.resolve() as? PsiClass ?: return false
        return annotationClass.isAnnotationType && annotationClass.qualifiedName == ARCH_IGNORE_FQN
    }

    private fun PsiField.hasArchTestAnnotation(): Boolean = modifierList?.annotations?.any { it.isArchTestAnnotation() } == true

    private fun PsiAnnotation.isArchTestAnnotation(): Boolean {
        val name = qualifiedName ?: text.removePrefix("@").substringBefore("(")
        return name == ARCH_TEST_FQN || (name == "ArchTest" && containingJavaFile().imports(ARCH_TEST_FQN))
    }

    private fun PsiAnnotation.isAnalyzeClassesAnnotation(): Boolean {
        val name = qualifiedName ?: text.removePrefix("@").substringBefore("(")
        return name == ANALYZE_CLASSES_FQN || (name == "AnalyzeClasses" && containingJavaFile().imports(ANALYZE_CLASSES_FQN))
    }

    internal fun scopeRequiresResolution(file: PsiJavaFile): Boolean = PsiTreeUtil.findChildrenOfType(file, PsiAnnotation::class.java)
        .filter { it.isAnalyzeClassesAnnotation() }
        .any { annotation ->
            annotation.parameterList.attributes.any {
                PsiTreeUtil.findChildOfType(it, PsiReferenceExpression::class.java) != null
            }
        }

    private fun PsiClass.analyzeScope(): AnalyzeScope {
        val annotation = modifierList?.annotations?.firstOrNull { it.isAnalyzeClassesAnnotation() }
            ?: return AnalyzeScope.All
        val attributes = annotation.parameterList.attributes
        if (attributes.any { it.name !in ANALYZE_CLASSES_ATTRIBUTES || it.value == null } || attributes.map { it.name }.distinct().size != attributes.size) {
            return AnalyzeScope.Unknown
        }
        if (listOf("locations", "importOptions", "classes").any { !annotation.hasEmptyAttribute(it) }) {
            return AnalyzeScope.Unknown
        }
        val wholeClasspath = annotation.findDeclaredAttributeValue("wholeClasspath")
        if (wholeClasspath != null && wholeClasspath.constantValue() != false) return AnalyzeScope.Unknown

        val packages = annotation.packageValues("packages") { it.constantValue() as? String }
            ?: return AnalyzeScope.Unknown
        val packagesOf = annotation.packageValues("packagesOf") { value ->
            val classLiteral = value as? PsiClassObjectAccessExpression ?: return@packageValues null
            val packageClass = PsiUtil.resolveClassInClassTypeOnly(classLiteral.operand.type) ?: return@packageValues null
            (packageClass.containingFile as? PsiJavaFile)?.packageName
        } ?: return AnalyzeScope.Unknown
        val configured = (packages + packagesOf).distinct()
        val packageNames = configured.ifEmpty {
            listOf((containingFile as? PsiJavaFile)?.packageName ?: return AnalyzeScope.Unknown)
        }
        return AnalyzeScope.Packages(packageNames)
    }

    private fun PsiAnnotation.hasEmptyAttribute(name: String): Boolean {
        val value = findDeclaredAttributeValue(name) ?: return true
        return value is PsiArrayInitializerMemberValue && value.initializers.isEmpty()
    }

    private fun PsiAnnotation.packageValues(
        name: String,
        extract: (PsiAnnotationMemberValue) -> String?,
    ): List<String>? {
        val value = findDeclaredAttributeValue(name) ?: return emptyList()
        val values = if (value is PsiArrayInitializerMemberValue) value.initializers.toList() else listOf(value)
        return values.map { extract(it) ?: return null }
    }

    private fun PsiAnnotationMemberValue.constantValue(): Any? = (this as? PsiExpression)?.let {
        JavaPsiFacade.getInstance(project).constantEvaluationHelper.computeConstantExpression(it)
    }

    private fun PsiField.isArchRuleField(): Boolean {
        val typeText = type.canonicalText
        return typeText == ARCH_RULE_FQN || (typeText == "ArchRule" && containingJavaFile().imports(ARCH_RULE_FQN))
    }

    private fun PsiField.isStaticFinal(): Boolean = hasModifierProperty(PsiModifier.STATIC) && hasModifierProperty(PsiModifier.FINAL)

    private fun PsiAnnotation.containingJavaFile(): PsiJavaFile? = containingFile as? PsiJavaFile

    private fun PsiField.containingJavaFile(): PsiJavaFile? = containingFile as? PsiJavaFile

    private fun PsiJavaFile?.imports(qualifiedName: String): Boolean = this?.importList
        ?.allImportStatements
        ?.filterIsInstance<PsiImportStatement>()
        ?.any { it.qualifiedName == qualifiedName } == true

    private val ANALYZE_CLASSES_ATTRIBUTES = setOf("packages", "packagesOf", "locations", "importOptions", "wholeClasspath", "cacheMode", "classes")

    private const val ARCH_IGNORE_FQN = "com.tngtech.archunit.junit.ArchIgnore"
    private const val ARCH_TEST_FQN = "com.tngtech.archunit.junit.ArchTest"
    private const val ARCH_RULE_FQN = "com.tngtech.archunit.lang.ArchRule"
    private const val ANALYZE_CLASSES_FQN = "com.tngtech.archunit.junit.AnalyzeClasses"
}

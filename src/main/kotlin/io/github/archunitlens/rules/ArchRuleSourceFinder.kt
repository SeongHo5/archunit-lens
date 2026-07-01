package io.github.archunitlens.rules

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil

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
        .filter { it.hasArchTestAnnotation() && it.isStaticFinal() && it.isArchRuleField() }
        .mapNotNull { field ->
            val initializer = field.initializer ?: return@mapNotNull null
            ArchRuleSource(
                ruleName = field.name,
                fieldPointer = SmartPointerManager.createPointer(field),
                initializer = initializer,
                analyzeScope = field.containingClass?.analyzeScope() ?: AnalyzeScope.All,
            )
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

    private fun PsiClass.analyzeScope(): AnalyzeScope {
        val packages = modifierList
            ?.annotations
            ?.firstOrNull { it.isAnalyzeClassesAnnotation() }
            ?.packageAttributes()
            .orEmpty()
        return if (packages.isEmpty()) AnalyzeScope.All else AnalyzeScope.Packages(packages)
    }

    private fun PsiAnnotation.packageAttributes(): List<String> {
        val attributes = parameterList.attributes
        val packagesAttribute = attributes.firstOrNull { it.name == "packages" }
            ?: attributes.singleOrNull { it.name == null }
        return packagesAttribute?.value?.stringValues().orEmpty()
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

    private fun com.intellij.psi.PsiAnnotationMemberValue.stringValues(): List<String> = when (this) {
        is PsiLiteralExpression -> listOfNotNull(value as? String)
        is PsiArrayInitializerMemberValue -> initializers.flatMap { it.stringValues() }
        else -> emptyList()
    }

    private const val ARCH_TEST_FQN = "com.tngtech.archunit.junit.ArchTest"
    private const val ARCH_RULE_FQN = "com.tngtech.archunit.lang.ArchRule"
    private const val ANALYZE_CLASSES_FQN = "com.tngtech.archunit.junit.AnalyzeClasses"
}

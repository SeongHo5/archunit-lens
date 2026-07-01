package io.github.archunitlens.rules

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * Parsed ArchUnit rule that can be evaluated by the live inspection.
 */
sealed interface LiveArchRule {
    val ruleName: String
    val sourcePointer: SmartPsiElementPointer<out PsiElement>
    val analyzeScope: AnalyzeScope
    val reason: String?
}

/**
 * Rule requiring classes in [sourcePackagePatterns] to avoid dependencies on
 * [forbiddenPackagePatterns].
 */
data class PackageDependencyBanRule(
    override val ruleName: String,
    val sourcePackagePatterns: List<String>,
    val forbiddenPackagePatterns: List<String>,
    override val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    override val analyzeScope: AnalyzeScope = AnalyzeScope.All,
    override val reason: String? = null,
) : LiveArchRule

/**
 * Rule requiring classes in [sourcePackagePattern] to end with [requiredSuffix].
 */
data class ClassNameSuffixRule(
    override val ruleName: String,
    val sourcePackagePattern: String,
    val requiredSuffix: String,
    override val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    override val analyzeScope: AnalyzeScope = AnalyzeScope.All,
    override val reason: String? = null,
) : LiveArchRule

/**
 * Rule forbidding [forbiddenAnnotationQualifiedName] on classes in
 * [sourcePackagePattern].
 */
data class ForbiddenAnnotationRule(
    override val ruleName: String,
    val sourcePackagePattern: String,
    val forbiddenAnnotationQualifiedName: String,
    override val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    override val analyzeScope: AnalyzeScope = AnalyzeScope.All,
    override val reason: String? = null,
) : LiveArchRule

/**
 * Rule forbidding [forbiddenAnnotationQualifiedName] when a class already has
 * [requiredAnnotationQualifiedName].
 */
data class AnnotationExclusivityRule(
    override val ruleName: String,
    val requiredAnnotationQualifiedName: String,
    val forbiddenAnnotationQualifiedName: String,
    override val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    override val analyzeScope: AnalyzeScope = AnalyzeScope.All,
    override val reason: String? = null,
) : LiveArchRule

/**
 * Rule requiring classes with [requiredSuffix] to be interfaces and, when the
 * target type can be resolved, assignable to [assignableToQualifiedName].
 */
data class InterfaceNamingRule(
    override val ruleName: String,
    val requiredSuffix: String,
    val assignableToQualifiedName: String,
    override val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    override val analyzeScope: AnalyzeScope = AnalyzeScope.All,
    override val reason: String? = null,
) : LiveArchRule

/**
 * Rule forbidding annotations that are themselves annotated with
 * [forbiddenMetaAnnotationQualifiedName] on interfaces.
 */
data class ClassMetaAnnotationRule(
    override val ruleName: String,
    val forbiddenMetaAnnotationQualifiedName: String,
    override val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    override val analyzeScope: AnalyzeScope = AnalyzeScope.All,
    override val reason: String? = null,
) : LiveArchRule

/**
 * Rule forbidding annotations that are themselves annotated with
 * [forbiddenMetaAnnotationQualifiedName] on methods declared in interfaces.
 */
data class MethodMetaAnnotationRule(
    override val ruleName: String,
    val forbiddenMetaAnnotationQualifiedName: String,
    override val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    override val analyzeScope: AnalyzeScope = AnalyzeScope.All,
    override val reason: String? = null,
) : LiveArchRule

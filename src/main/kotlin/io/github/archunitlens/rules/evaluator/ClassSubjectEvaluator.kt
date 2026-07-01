package io.github.archunitlens.rules.evaluator

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import io.github.archunitlens.rules.AnnotationExclusivityRule
import io.github.archunitlens.rules.ClassMetaAnnotationRule
import io.github.archunitlens.rules.ClassNameSuffixRule
import io.github.archunitlens.rules.ForbiddenAnnotationRule
import io.github.archunitlens.rules.InterfaceNamingRule
import io.github.archunitlens.rules.MethodMetaAnnotationRule
import io.github.archunitlens.rules.PackageDependencyBanRule
import io.github.archunitlens.rules.PackagePattern

/**
 * Common evaluator for statically supported class-subject ArchUnit descriptors.
 *
 * The evaluator never executes ArchUnit or user helper code. It only consumes PSI
 * facts that are already available to the IntelliJ inspection.
 */
object ClassSubjectEvaluator {
    fun appliesToPackage(
        rule: PackageDependencyBanRule,
        packageName: String,
    ): Boolean = rule.analyzeScope.includes(packageName) &&
        rule.sourcePackagePatterns.any { PackagePattern.matches(it, packageName) }

    fun appliesToPackage(
        rule: ClassNameSuffixRule,
        packageName: String,
    ): Boolean = rule.analyzeScope.includes(packageName) && PackagePattern.matches(rule.sourcePackagePattern, packageName)

    fun appliesToPackage(
        rule: ForbiddenAnnotationRule,
        packageName: String,
    ): Boolean = rule.analyzeScope.includes(packageName) && PackagePattern.matches(rule.sourcePackagePattern, packageName)

    fun matchedForbiddenDependencyPattern(
        rule: PackageDependencyBanRule,
        targetQualifiedName: String,
    ): String? = rule.forbiddenPackagePatterns.firstOrNull { PackagePattern.matches(it, targetQualifiedName) }

    fun isMissingRequiredSuffix(
        aClass: PsiClass,
        rule: ClassNameSuffixRule,
    ): Boolean = aClass.name?.endsWith(rule.requiredSuffix) == false

    fun isMissingInterface(aClass: PsiClass): Boolean = !aClass.isInterface

    fun isMissingAssignableType(
        aClass: PsiClass,
        rule: InterfaceNamingRule,
    ): Boolean {
        if (aClass.qualifiedName == rule.assignableToQualifiedName) return false
        val targetClass = JavaPsiFacade.getInstance(aClass.project).findClass(rule.assignableToQualifiedName, aClass.resolveScope)
            ?: return false
        return !aClass.isInheritor(targetClass, true)
    }

    fun hasQualifiedAnnotation(
        aClass: PsiClass,
        rule: AnnotationExclusivityRule,
    ): Boolean = aClass.modifierList
        ?.annotations
        ?.any { it.qualifiedName == rule.requiredAnnotationQualifiedName } == true

    fun isForbiddenAnnotation(
        annotation: PsiAnnotation,
        rule: ForbiddenAnnotationRule,
    ): Boolean = annotation.qualifiedName == rule.forbiddenAnnotationQualifiedName

    fun isForbiddenAnnotation(
        annotation: PsiAnnotation,
        rule: AnnotationExclusivityRule,
        annotatedClass: PsiClass,
    ): Boolean = annotation.qualifiedName == rule.forbiddenAnnotationQualifiedName && hasQualifiedAnnotation(annotatedClass, rule)

    fun isForbiddenMetaAnnotation(
        annotation: PsiAnnotation,
        rule: ClassMetaAnnotationRule,
    ): Boolean = annotation.isMetaAnnotatedWith(rule.forbiddenMetaAnnotationQualifiedName)

    fun isForbiddenMetaAnnotation(
        annotation: PsiAnnotation,
        rule: MethodMetaAnnotationRule,
    ): Boolean = annotation.isMetaAnnotatedWith(rule.forbiddenMetaAnnotationQualifiedName)

    private fun PsiAnnotation.isMetaAnnotatedWith(qualifiedName: String): Boolean {
        val annotationClass = resolveAnnotationType()
            ?: this.qualifiedName
                ?.let { JavaPsiFacade.getInstance(project).findClass(it, resolveScope) }
        return annotationClass
            ?.modifierList
            ?.annotations
            ?.any { metaAnnotation ->
                metaAnnotation.qualifiedName == qualifiedName ||
                    metaAnnotation.resolveAnnotationType()?.qualifiedName == qualifiedName
            } == true
    }
}

package io.github.archunitlens.rules.evaluator

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import io.github.archunitlens.rules.AnnotationExclusivityRule
import io.github.archunitlens.rules.ClassConventionRule
import io.github.archunitlens.rules.ClassMetaAnnotationRule
import io.github.archunitlens.rules.ClassNameSuffixRule
import io.github.archunitlens.rules.ConditionExpr
import io.github.archunitlens.rules.ForbiddenAnnotationRule
import io.github.archunitlens.rules.InterfaceNamingRule
import io.github.archunitlens.rules.MethodMetaAnnotationRule
import io.github.archunitlens.rules.PackageDependencyBanRule
import io.github.archunitlens.rules.PackagePattern
import io.github.archunitlens.rules.PredicateExpr

internal sealed interface ClassConditionViolation {
    data class MissingAnnotation(val qualifiedName: String) : ClassConditionViolation
    data class ForbiddenAnnotation(val qualifiedName: String) : ClassConditionViolation
    data class OutsidePackages(val patterns: List<String>) : ClassConditionViolation
    data class MissingSuffix(val suffix: String) : ClassConditionViolation
    data class ForbiddenSuffix(val suffix: String) : ClassConditionViolation
    data object MustBeInterface : ClassConditionViolation
    data object MustNotBeInterface : ClassConditionViolation
    data object MustBeEnum : ClassConditionViolation
    data object MustNotBeEnum : ClassConditionViolation
    data class MissingAssignableType(val qualifiedName: String) : ClassConditionViolation
}

/**
 * Common evaluator for statically supported class-subject ArchUnit descriptors.
 *
 * The evaluator never executes ArchUnit or user helper code. It only consumes PSI
 * facts that are already available to the IntelliJ inspection.
 */
object ClassSubjectEvaluator {
    fun matches(
        rule: ClassConventionRule,
        aClass: PsiClass,
        packageName: String,
    ): Boolean = rule.analyzeScope.includes(packageName) && evaluatePredicate(aClass, packageName, rule.predicate) == true

    internal fun violations(
        rule: ClassConventionRule,
        aClass: PsiClass,
        packageName: String,
    ): List<ClassConditionViolation> = evaluateCondition(aClass, packageName, rule.condition).orEmpty()

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

    private fun evaluatePredicate(
        aClass: PsiClass,
        packageName: String,
        predicate: PredicateExpr,
    ): Boolean? = when (predicate) {
        PredicateExpr.All -> true
        is PredicateExpr.Leaf -> null
        is PredicateExpr.AreAnnotatedWith -> aClass.hasAnnotation(predicate.qualifiedName)
        is PredicateExpr.AreNotAnnotatedWith -> !aClass.hasAnnotation(predicate.qualifiedName)
        is PredicateExpr.ResideInPackages -> predicate.patterns.any { PackagePattern.matches(it, packageName) }
        is PredicateExpr.HaveSimpleNameEndingWith -> aClass.name?.endsWith(predicate.suffix)
        is PredicateExpr.HaveSimpleNameNotEndingWith -> aClass.name?.endsWith(predicate.suffix)?.not()
        is PredicateExpr.AreInterfaces -> aClass.isInterface == predicate.expected
        is PredicateExpr.AreEnums -> aClass.isEnum == predicate.expected
        is PredicateExpr.And -> {
            val left = evaluatePredicate(aClass, packageName, predicate.left) ?: return null
            val right = evaluatePredicate(aClass, packageName, predicate.right) ?: return null
            left && right
        }
        is PredicateExpr.Or -> {
            val left = evaluatePredicate(aClass, packageName, predicate.left) ?: return null
            val right = evaluatePredicate(aClass, packageName, predicate.right) ?: return null
            left || right
        }
    }

    private fun evaluateCondition(
        aClass: PsiClass,
        packageName: String,
        condition: ConditionExpr,
    ): List<ClassConditionViolation>? = when (condition) {
        is ConditionExpr.Leaf -> null
        is ConditionExpr.BeAnnotatedWith -> if (aClass.hasAnnotation(condition.qualifiedName) == condition.required) {
            emptyList()
        } else if (condition.required) {
            listOf(ClassConditionViolation.MissingAnnotation(condition.qualifiedName))
        } else {
            listOf(ClassConditionViolation.ForbiddenAnnotation(condition.qualifiedName))
        }
        is ConditionExpr.ResideInPackages -> if (condition.patterns.any { PackagePattern.matches(it, packageName) }) {
            emptyList()
        } else {
            listOf(ClassConditionViolation.OutsidePackages(condition.patterns))
        }
        is ConditionExpr.HaveSimpleNameEndingWith -> {
            val endsWith = aClass.name?.endsWith(condition.suffix) ?: return null
            if (endsWith == condition.required) {
                emptyList()
            } else if (condition.required) {
                listOf(ClassConditionViolation.MissingSuffix(condition.suffix))
            } else {
                listOf(ClassConditionViolation.ForbiddenSuffix(condition.suffix))
            }
        }
        is ConditionExpr.BeInterfaces -> if (aClass.isInterface == condition.required) {
            emptyList()
        } else if (condition.required) {
            listOf(ClassConditionViolation.MustBeInterface)
        } else {
            listOf(ClassConditionViolation.MustNotBeInterface)
        }
        is ConditionExpr.BeEnums -> if (aClass.isEnum == condition.required) {
            emptyList()
        } else if (condition.required) {
            listOf(ClassConditionViolation.MustBeEnum)
        } else {
            listOf(ClassConditionViolation.MustNotBeEnum)
        }
        is ConditionExpr.BeAssignableTo -> {
            val targetClass = JavaPsiFacade.getInstance(aClass.project).findClass(condition.qualifiedName, aClass.resolveScope)
                ?: return null
            if (aClass.qualifiedName == condition.qualifiedName || aClass.isInheritor(targetClass, true)) {
                emptyList()
            } else {
                listOf(ClassConditionViolation.MissingAssignableType(condition.qualifiedName))
            }
        }
        is ConditionExpr.And -> {
            val left = evaluateCondition(aClass, packageName, condition.left) ?: return null
            val right = evaluateCondition(aClass, packageName, condition.right) ?: return null
            left + right
        }
    }
}

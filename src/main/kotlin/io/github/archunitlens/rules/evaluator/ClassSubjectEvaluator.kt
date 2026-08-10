package io.github.archunitlens.rules.evaluator

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import io.github.archunitlens.rules.AnnotationExclusivityRule
import io.github.archunitlens.rules.ClassConventionRule
import io.github.archunitlens.rules.ClassMetaAnnotationRule
import io.github.archunitlens.rules.ClassModifier
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
    data object MustBeRecord : ClassConditionViolation
    data object MustNotBeRecord : ClassConditionViolation
    data class MissingModifier(val modifier: ClassModifier) : ClassConditionViolation
    data class ForbiddenModifier(val modifier: ClassModifier) : ClassConditionViolation
    data class MissingMetaAnnotation(val qualifiedName: String) : ClassConditionViolation
    data class ForbiddenMetaAnnotation(val qualifiedName: String) : ClassConditionViolation
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

    internal fun matchesPredicate(
        aClass: PsiClass,
        packageName: String,
        predicate: PredicateExpr,
    ): Boolean? = evaluatePredicate(aClass, packageName, predicate)

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

    private fun PsiClass.metaAnnotationState(qualifiedName: String): Boolean? {
        var unresolved = false
        modifierList?.annotations.orEmpty().forEach { annotation ->
            when (annotation.metaAnnotationState(qualifiedName, mutableSetOf())) {
                true -> return true
                null -> unresolved = true
                false -> Unit
            }
        }
        return if (unresolved) null else false
    }

    private fun PsiAnnotation.metaAnnotationState(
        qualifiedName: String,
        visitedAnnotationTypes: MutableSet<String>,
    ): Boolean? {
        if (this.qualifiedName == qualifiedName) return true
        val annotationClass = resolveAnnotationType()
            ?: this.qualifiedName
                ?.let { JavaPsiFacade.getInstance(project).findClass(it, resolveScope) }
            ?: return null
        val annotationQualifiedName = annotationClass.qualifiedName ?: return null
        if (!visitedAnnotationTypes.add(annotationQualifiedName)) return false
        if (annotationQualifiedName == qualifiedName) return true

        var unresolved = false
        annotationClass.modifierList?.annotations.orEmpty().forEach { metaAnnotation ->
            when (metaAnnotation.metaAnnotationState(qualifiedName, visitedAnnotationTypes)) {
                true -> return true
                null -> unresolved = true
                false -> Unit
            }
        }
        return if (unresolved) null else false
    }

    fun isForbiddenMetaAnnotation(
        annotation: PsiAnnotation,
        rule: MethodMetaAnnotationRule,
    ): Boolean = annotation.isMetaAnnotatedWith(rule.forbiddenMetaAnnotationQualifiedName)

    private fun PsiAnnotation.isMetaAnnotatedWith(qualifiedName: String): Boolean = isMetaAnnotatedWith(qualifiedName, mutableSetOf())

    private fun PsiAnnotation.isMetaAnnotatedWith(
        qualifiedName: String,
        visitedAnnotationTypes: MutableSet<String>,
    ): Boolean {
        if (this.qualifiedName == qualifiedName) return true

        val annotationClass = resolveAnnotationType()
            ?: this.qualifiedName
                ?.let { JavaPsiFacade.getInstance(project).findClass(it, resolveScope) }
            ?: return false
        val annotationQualifiedName = annotationClass.qualifiedName ?: return false
        if (!visitedAnnotationTypes.add(annotationQualifiedName)) return false
        if (annotationQualifiedName == qualifiedName) return true

        return annotationClass.modifierList
            ?.annotations
            ?.any { metaAnnotation ->
                metaAnnotation.isMetaAnnotatedWith(qualifiedName, visitedAnnotationTypes)
            } == true
    }

    private fun evaluatePredicate(
        aClass: PsiClass,
        packageName: String,
        predicate: PredicateExpr,
    ): Boolean? = when (predicate) {
        PredicateExpr.All -> true
        is PredicateExpr.Leaf -> null
        is PredicateExpr.AreAnnotatedWith -> aClass.annotationMatch(predicate.qualifiedName)
        is PredicateExpr.AreNotAnnotatedWith -> aClass.annotationMatch(predicate.qualifiedName)?.not()
        is PredicateExpr.AreAssignableTo -> {
            val target = JavaPsiFacade.getInstance(aClass.project).findClass(predicate.qualifiedName, aClass.resolveScope)
                ?: return null
            (aClass == target || aClass.isInheritor(target, true)) == predicate.expected
        }
        is PredicateExpr.Implement -> {
            val target = aClass.implementsListTypes
                .mapNotNull(PsiClassType::resolve)
                .firstOrNull { it.qualifiedName == predicate.qualifiedName }
                ?: JavaPsiFacade.getInstance(aClass.project).findClass(predicate.qualifiedName, aClass.resolveScope)
                    ?.takeIf { it.isInterface } ?: return null
            val implementsTarget = !aClass.isInterface &&
                (
                    aClass.implementsListTypes.any {
                        it.canonicalText == target.qualifiedName || it.resolve()?.qualifiedName == target.qualifiedName
                    } ||
                        aClass.implementsList?.referenceElements?.any {
                            it.qualifiedName == target.qualifiedName || (it.resolve() as? PsiClass)?.qualifiedName == target.qualifiedName
                        } == true ||
                        aClass.interfaces.any { it.qualifiedName == target.qualifiedName } ||
                        aClass.isInheritor(target, true)
                    )
            implementsTarget == predicate.expected
        }
        is PredicateExpr.ResideInPackages -> predicate.patterns.any { PackagePattern.matches(it, packageName) }
        is PredicateExpr.HaveSimpleNameEndingWith -> aClass.name?.endsWith(predicate.suffix)
        is PredicateExpr.HaveSimpleNameNotEndingWith -> aClass.name?.endsWith(predicate.suffix)?.not()
        is PredicateExpr.AreInterfaces -> aClass.isInterface == predicate.expected
        is PredicateExpr.AreEnums -> aClass.isEnum == predicate.expected
        is PredicateExpr.AreRecords -> aClass.isRecord == predicate.expected
        is PredicateExpr.AreMetaAnnotatedWith -> aClass.metaAnnotationState(predicate.qualifiedName)
            ?.let { isMetaAnnotated -> isMetaAnnotated == predicate.expected }
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

    private fun PsiClass.annotationMatch(qualifiedName: String): Boolean? {
        val matches = modifierList?.annotations.orEmpty().map { annotation ->
            annotation.resolveAnnotationType()?.qualifiedName?.let { it == qualifiedName }
        }
        return when {
            matches.any { it == true } -> true
            matches.any { it == null } -> null
            else -> false
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
        is ConditionExpr.BeRecords -> if (aClass.isRecord == condition.required) {
            emptyList()
        } else if (condition.required) {
            listOf(ClassConditionViolation.MustBeRecord)
        } else {
            listOf(ClassConditionViolation.MustNotBeRecord)
        }
        is ConditionExpr.HaveModifier -> {
            val hasModifier = aClass.hasModifier(condition.modifier)
            if (hasModifier == condition.required) {
                emptyList()
            } else if (condition.required) {
                listOf(ClassConditionViolation.MissingModifier(condition.modifier))
            } else {
                listOf(ClassConditionViolation.ForbiddenModifier(condition.modifier))
            }
        }
        is ConditionExpr.BeMetaAnnotatedWith -> {
            val isMetaAnnotated = aClass.metaAnnotationState(condition.qualifiedName) ?: return null
            if (isMetaAnnotated == condition.required) {
                emptyList()
            } else if (condition.required) {
                listOf(ClassConditionViolation.MissingMetaAnnotation(condition.qualifiedName))
            } else {
                listOf(ClassConditionViolation.ForbiddenMetaAnnotation(condition.qualifiedName))
            }
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

    private fun PsiClass.hasModifier(modifier: ClassModifier): Boolean = when (modifier) {
        ClassModifier.FINAL -> hasModifierProperty(PsiModifier.FINAL)
    }
}

package io.github.archunitlens.rules.evaluator

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import io.github.archunitlens.rules.MemberConditionExpr
import io.github.archunitlens.rules.MemberConventionRule
import io.github.archunitlens.rules.MemberPredicateExpr
import io.github.archunitlens.rules.MemberSubjectKind

/** Semantic reason why a statically evaluable member condition was violated. */
internal sealed interface MemberConditionViolation {
    data object MustBePrivate : MemberConditionViolation
    data object MustBeStatic : MemberConditionViolation
    data class WrongRawReturnType(val qualifiedName: String) : MemberConditionViolation
}

/**
 * Evaluates member declaration facts only. It never reads method bodies or executes
 * ArchUnit and returns no result when PSI resolution cannot prove a fact.
 */
internal object MemberSubjectEvaluator {
    fun matches(
        rule: MemberConventionRule,
        method: PsiMethod,
        packageName: String,
    ): Boolean = rule.analyzeScope.includes(packageName) &&
        subjectMatches(rule.subject, method) &&
        evaluatePredicate(method, packageName, rule.predicate) == true

    fun violations(
        rule: MemberConventionRule,
        method: PsiMethod,
    ): List<MemberConditionViolation> = evaluateCondition(method, rule.condition).orEmpty()

    fun matchesImplicitConstructor(
        rule: MemberConventionRule,
        aClass: PsiClass,
        packageName: String,
    ): Boolean = rule.subject == MemberSubjectKind.Constructors &&
        rule.analyzeScope.includes(packageName) &&
        evaluateImplicitConstructorPredicate(aClass, packageName, rule.predicate) == true

    fun implicitConstructorViolations(
        rule: MemberConventionRule,
        aClass: PsiClass,
    ): List<MemberConditionViolation> = evaluateImplicitConstructorCondition(
        condition = rule.condition,
        isPrivate = aClass.hasModifierProperty(PsiModifier.PRIVATE),
    )

    private fun subjectMatches(subject: MemberSubjectKind, method: PsiMethod): Boolean = when (subject) {
        MemberSubjectKind.Methods -> !method.isConstructor
        MemberSubjectKind.Constructors -> method.isConstructor
    }

    private fun evaluatePredicate(
        method: PsiMethod,
        packageName: String,
        predicate: MemberPredicateExpr,
    ): Boolean? = when (predicate) {
        MemberPredicateExpr.All -> true
        is MemberPredicateExpr.IsAnnotatedWith ->
            method.modifierList.annotations
                .map { annotation -> annotation.matches(predicate.qualifiedName, predicate.metaAnnotated) }
                .combinedAnnotationMatch()
        is MemberPredicateExpr.DeclaredInClasses -> method.containingClass?.let { declaringClass ->
            ClassSubjectEvaluator.matchesPredicate(declaringClass, packageName, predicate.predicate)
        }
        is MemberPredicateExpr.And -> {
            val left = evaluatePredicate(method, packageName, predicate.left) ?: return null
            val right = evaluatePredicate(method, packageName, predicate.right) ?: return null
            left && right
        }
        is MemberPredicateExpr.Or -> {
            val left = evaluatePredicate(method, packageName, predicate.left) ?: return null
            val right = evaluatePredicate(method, packageName, predicate.right) ?: return null
            left || right
        }
    }

    private fun evaluateImplicitConstructorPredicate(
        aClass: PsiClass,
        packageName: String,
        predicate: MemberPredicateExpr,
    ): Boolean? = when (predicate) {
        MemberPredicateExpr.All -> true
        is MemberPredicateExpr.IsAnnotatedWith -> false
        is MemberPredicateExpr.DeclaredInClasses -> ClassSubjectEvaluator.matchesPredicate(aClass, packageName, predicate.predicate)
        is MemberPredicateExpr.And -> {
            val left = evaluateImplicitConstructorPredicate(aClass, packageName, predicate.left) ?: return null
            val right = evaluateImplicitConstructorPredicate(aClass, packageName, predicate.right) ?: return null
            left && right
        }
        is MemberPredicateExpr.Or -> {
            val left = evaluateImplicitConstructorPredicate(aClass, packageName, predicate.left) ?: return null
            val right = evaluateImplicitConstructorPredicate(aClass, packageName, predicate.right) ?: return null
            left || right
        }
    }

    private fun evaluateCondition(
        method: PsiMethod,
        condition: MemberConditionExpr,
    ): List<MemberConditionViolation>? = when (condition) {
        MemberConditionExpr.BePrivate -> if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            emptyList()
        } else {
            listOf(MemberConditionViolation.MustBePrivate)
        }
        MemberConditionExpr.BeStatic -> if (method.hasModifierProperty(PsiModifier.STATIC)) {
            emptyList()
        } else {
            listOf(MemberConditionViolation.MustBeStatic)
        }
        is MemberConditionExpr.HaveRawReturnType -> {
            val returnType = method.returnType ?: return null
            val resolvedType = (returnType as? PsiClassType)
                ?.rawType()
                ?.resolve()
                ?.qualifiedName
            when {
                returnType is PsiClassType && resolvedType == null -> null
                resolvedType == condition.qualifiedName -> emptyList()
                else -> listOf(MemberConditionViolation.WrongRawReturnType(condition.qualifiedName))
            }
        }
        is MemberConditionExpr.And -> {
            val left = evaluateCondition(method, condition.left) ?: return null
            val right = evaluateCondition(method, condition.right) ?: return null
            left + right
        }
    }

    private fun PsiAnnotation.matches(
        qualifiedName: String,
        metaAnnotated: Boolean,
    ): Boolean? {
        val annotationClass = resolveAnnotationType()
            ?: this.qualifiedName?.let { JavaPsiFacade.getInstance(project).findClass(it, resolveScope) }
            ?: return null
        if (annotationClass.qualifiedName == qualifiedName) return true
        if (!metaAnnotated) return false
        return annotationClass.modifierList?.annotations
            ?.map { annotation -> annotation.matchesMetaAnnotation(qualifiedName, mutableSetOf()) }
            .orEmpty()
            .combinedAnnotationMatch()
    }

    private fun PsiAnnotation.matchesMetaAnnotation(
        qualifiedName: String,
        visitedAnnotationTypes: MutableSet<String>,
    ): Boolean? {
        val annotationClass = resolveAnnotationType()
            ?: this.qualifiedName?.let { JavaPsiFacade.getInstance(project).findClass(it, resolveScope) }
            ?: return null
        val annotationQualifiedName = annotationClass.qualifiedName ?: return false
        if (!visitedAnnotationTypes.add(annotationQualifiedName)) return false
        if (annotationQualifiedName == qualifiedName) return true
        return annotationClass.modifierList?.annotations
            ?.map { annotation -> annotation.matchesMetaAnnotation(qualifiedName, visitedAnnotationTypes) }
            .orEmpty()
            .combinedAnnotationMatch()
    }

    private fun List<Boolean?>.combinedAnnotationMatch(): Boolean? = when {
        any { it == true } -> true
        any { it == null } -> null
        else -> false
    }

    private fun evaluateImplicitConstructorCondition(
        condition: MemberConditionExpr,
        isPrivate: Boolean,
    ): List<MemberConditionViolation> = when (condition) {
        MemberConditionExpr.BePrivate -> if (isPrivate) emptyList() else listOf(MemberConditionViolation.MustBePrivate)
        is MemberConditionExpr.And -> evaluateImplicitConstructorCondition(condition.left, isPrivate) +
            evaluateImplicitConstructorCondition(condition.right, isPrivate)
        else -> emptyList()
    }
}

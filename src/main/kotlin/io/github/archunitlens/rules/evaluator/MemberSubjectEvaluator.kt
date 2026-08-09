package io.github.archunitlens.rules.evaluator

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import io.github.archunitlens.rules.MemberConditionExpr
import io.github.archunitlens.rules.MemberConventionRule
import io.github.archunitlens.rules.MemberPredicateExpr
import io.github.archunitlens.rules.MemberSubjectKind
import io.github.archunitlens.rules.RulePolarity

internal sealed interface MemberConditionViolation {
    data object MustBePrivate : MemberConditionViolation
    data object MustBeStatic : MemberConditionViolation
    data class WrongRawReturnType(val qualifiedName: String) : MemberConditionViolation
    data class MissingAnnotation(val qualifiedName: String) : MemberConditionViolation
    data class ForbiddenAnnotation(val qualifiedName: String) : MemberConditionViolation
    data class RequiredModifier(val modifier: String) : MemberConditionViolation
    data class ForbiddenModifier(val modifier: String) : MemberConditionViolation
    data class RequiredName(val name: String) : MemberConditionViolation
    data class ForbiddenName(val name: String) : MemberConditionViolation
    data class RequiredNamePattern(val pattern: String) : MemberConditionViolation
    data class ForbiddenNamePattern(val pattern: String) : MemberConditionViolation
    data class ForbiddenCondition(val description: String) : MemberConditionViolation
}

/** Evaluates declaration PSI only and fails closed whenever a required symbol cannot be resolved. */
internal object MemberSubjectEvaluator {
    fun matches(rule: MemberConventionRule, member: PsiMember, packageName: String): Boolean = rule.analyzeScope.includes(packageName) &&
        subjectMatches(rule.subject, member) &&
        evaluatePredicate(member, packageName, rule.predicate) == true

    fun violations(rule: MemberConventionRule, member: PsiMember): List<MemberConditionViolation> {
        val result = evaluateCondition(member, rule.condition) ?: return emptyList()
        return when (rule.polarity) {
            RulePolarity.POSITIVE -> if (result.satisfied) emptyList() else result.violations
            RulePolarity.NEGATIVE -> if (result.satisfied) {
                listOf(MemberConditionViolation.ForbiddenCondition(rule.condition.display()))
            } else {
                emptyList()
            }
        }
    }

    fun matchesImplicitConstructor(rule: MemberConventionRule, aClass: PsiClass, packageName: String): Boolean = rule.subject == MemberSubjectKind.Constructors &&
        rule.analyzeScope.includes(packageName) &&
        evaluateImplicitConstructorPredicate(aClass, packageName, rule.predicate) == true

    fun implicitConstructorViolations(rule: MemberConventionRule, aClass: PsiClass): List<MemberConditionViolation> = if (rule.polarity == RulePolarity.POSITIVE && !aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        listOf(MemberConditionViolation.MustBePrivate)
    } else {
        emptyList()
    }

    private fun subjectMatches(subject: MemberSubjectKind, member: PsiMember): Boolean = when (subject) {
        MemberSubjectKind.Fields -> member is PsiField
        MemberSubjectKind.Methods -> member is PsiMethod && !member.isConstructor
        MemberSubjectKind.Constructors -> member is PsiMethod && member.isConstructor
    }

    private fun evaluatePredicate(member: PsiMember, packageName: String, predicate: MemberPredicateExpr): Boolean? = when (predicate) {
        MemberPredicateExpr.All -> true
        is MemberPredicateExpr.IsAnnotatedWith -> (member as? PsiModifierListOwner)?.modifierList?.annotations
            ?.map { it.matches(predicate.qualifiedName, predicate.metaAnnotated) }
            .orEmpty().combinedAnnotationMatch()
        is MemberPredicateExpr.DeclaredInClasses -> member.containingClass?.let {
            ClassSubjectEvaluator.matchesPredicate(it, packageName, predicate.predicate)
        }
        is MemberPredicateExpr.And -> combineBoolean(
            evaluatePredicate(member, packageName, predicate.left),
            evaluatePredicate(member, packageName, predicate.right),
        ) { left, right -> left && right }
        is MemberPredicateExpr.Or -> combineBoolean(
            evaluatePredicate(member, packageName, predicate.left),
            evaluatePredicate(member, packageName, predicate.right),
        ) { left, right -> left || right }
    }

    private fun evaluateImplicitConstructorPredicate(aClass: PsiClass, packageName: String, predicate: MemberPredicateExpr): Boolean? = when (predicate) {
        MemberPredicateExpr.All -> true
        is MemberPredicateExpr.IsAnnotatedWith -> false
        is MemberPredicateExpr.DeclaredInClasses -> ClassSubjectEvaluator.matchesPredicate(aClass, packageName, predicate.predicate)
        is MemberPredicateExpr.And -> combineBoolean(
            evaluateImplicitConstructorPredicate(aClass, packageName, predicate.left),
            evaluateImplicitConstructorPredicate(aClass, packageName, predicate.right),
        ) { left, right -> left && right }
        is MemberPredicateExpr.Or -> combineBoolean(
            evaluateImplicitConstructorPredicate(aClass, packageName, predicate.left),
            evaluateImplicitConstructorPredicate(aClass, packageName, predicate.right),
        ) { left, right -> left || right }
    }

    private fun evaluateCondition(member: PsiMember, condition: MemberConditionExpr): ConditionResult? {
        return when (condition) {
            MemberConditionExpr.BePrivate -> modifierResult(member, PsiModifier.PRIVATE, true, MemberConditionViolation.MustBePrivate)
            MemberConditionExpr.BeStatic -> modifierResult(member, PsiModifier.STATIC, true, MemberConditionViolation.MustBeStatic)
            is MemberConditionExpr.HaveRawReturnType -> {
                val returnType = (member as? PsiMethod)?.returnType ?: return null
                val resolved = (returnType as? PsiClassType)?.rawType()?.resolve()?.qualifiedName
                if (returnType is PsiClassType && resolved == null) return null
                conditionResult(resolved == condition.qualifiedName, MemberConditionViolation.WrongRawReturnType(condition.qualifiedName))
            }
            is MemberConditionExpr.BeAnnotatedWith -> {
                val matches = (member as? PsiModifierListOwner)?.modifierList?.annotations
                    ?.map { it.matches(condition.qualifiedName, condition.metaAnnotated) }
                    .orEmpty().combinedAnnotationMatch() ?: return null
                conditionResult(
                    matches == condition.required,
                    if (condition.required) MemberConditionViolation.MissingAnnotation(condition.qualifiedName) else MemberConditionViolation.ForbiddenAnnotation(condition.qualifiedName),
                )
            }
            is MemberConditionExpr.HaveModifier -> {
                val present = if (condition.modifier == "package-private") {
                    listOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE).none(member::hasModifierProperty)
                } else {
                    member.hasModifierProperty(condition.modifier)
                }
                conditionResult(
                    present == condition.required,
                    if (condition.required) MemberConditionViolation.RequiredModifier(condition.modifier) else MemberConditionViolation.ForbiddenModifier(condition.modifier),
                )
            }
            is MemberConditionExpr.HaveName -> conditionResult(
                (member.name == condition.name) == condition.required,
                if (condition.required) MemberConditionViolation.RequiredName(condition.name) else MemberConditionViolation.ForbiddenName(condition.name),
            )
            is MemberConditionExpr.HaveNameMatching -> {
                val matches = Regex(condition.pattern).matches(member.name ?: return null)
                conditionResult(
                    matches == condition.required,
                    if (condition.required) MemberConditionViolation.RequiredNamePattern(condition.pattern) else MemberConditionViolation.ForbiddenNamePattern(condition.pattern),
                )
            }
            is MemberConditionExpr.And -> combineConditions(evaluateCondition(member, condition.left), evaluateCondition(member, condition.right)) { left, right ->
                ConditionResult(left.satisfied && right.satisfied, left.violations + right.violations)
            }
            is MemberConditionExpr.Or -> combineConditions(evaluateCondition(member, condition.left), evaluateCondition(member, condition.right)) { left, right ->
                ConditionResult(left.satisfied || right.satisfied, left.violations + right.violations)
            }
        }
    }

    private fun modifierResult(member: PsiMember, modifier: String, required: Boolean, violation: MemberConditionViolation) = conditionResult(member.hasModifierProperty(modifier) == required, violation)

    private fun conditionResult(satisfied: Boolean, violation: MemberConditionViolation): ConditionResult = ConditionResult(satisfied, if (satisfied) emptyList() else listOf(violation))

    private fun PsiAnnotation.matches(qualifiedName: String, metaAnnotated: Boolean): Boolean? {
        val annotationClass = resolveAnnotationType()
            ?: this.qualifiedName?.let { JavaPsiFacade.getInstance(project).findClass(it, resolveScope) }
            ?: return null
        if (annotationClass.qualifiedName == qualifiedName) return true
        if (!metaAnnotated) return false
        return annotationClass.modifierList?.annotations.orEmpty()
            .map { it.matchesMetaAnnotation(qualifiedName, mutableSetOf()) }.combinedAnnotationMatch()
    }

    private fun PsiAnnotation.matchesMetaAnnotation(qualifiedName: String, visited: MutableSet<String>): Boolean? {
        val annotationClass = resolveAnnotationType()
            ?: this.qualifiedName?.let { JavaPsiFacade.getInstance(project).findClass(it, resolveScope) }
            ?: return null
        val current = annotationClass.qualifiedName ?: return false
        if (!visited.add(current)) return false
        if (current == qualifiedName) return true
        return annotationClass.modifierList?.annotations.orEmpty()
            .map { it.matchesMetaAnnotation(qualifiedName, visited) }.combinedAnnotationMatch()
    }

    private fun List<Boolean?>.combinedAnnotationMatch(): Boolean? = when {
        any { it == true } -> true
        any { it == null } -> null
        else -> false
    }

    private fun <T> combineConditions(left: T?, right: T?, combine: (T, T) -> T): T? = if (left == null || right == null) null else combine(left, right)
    private fun combineBoolean(left: Boolean?, right: Boolean?, combine: (Boolean, Boolean) -> Boolean): Boolean? = if (left == null || right == null) null else combine(left, right)

    private data class ConditionResult(val satisfied: Boolean, val violations: List<MemberConditionViolation>)

    private fun MemberConditionExpr.display(): String = when (this) {
        MemberConditionExpr.BePrivate -> "bePrivate"
        MemberConditionExpr.BeStatic -> "beStatic"
        is MemberConditionExpr.HaveRawReturnType -> "haveRawReturnType($qualifiedName)"
        is MemberConditionExpr.BeAnnotatedWith -> "${if (required) "be" else "notBe"}${if (metaAnnotated) "Meta" else ""}AnnotatedWith($qualifiedName)"
        is MemberConditionExpr.HaveModifier -> "${if (required) "be" else "notBe"} $modifier"
        is MemberConditionExpr.HaveName -> "${if (required) "haveName" else "notHaveName"}($name)"
        is MemberConditionExpr.HaveNameMatching -> "${if (required) "haveNameMatching" else "notHaveNameMatching"}($pattern)"
        is MemberConditionExpr.And -> "(${left.display()} AND ${right.display()})"
        is MemberConditionExpr.Or -> "(${left.display()} OR ${right.display()})"
    }
}

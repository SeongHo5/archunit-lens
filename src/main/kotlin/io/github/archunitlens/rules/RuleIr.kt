package io.github.archunitlens.rules

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * Top-level ArchUnit DSL subject inferred from `ArchRuleDefinition` entry points.
 */
sealed interface SubjectKind {
    data object Classes : SubjectKind
    data object SingleClass : SubjectKind
    data object Members : SubjectKind
    data object Fields : SubjectKind
    data object CodeUnits : SubjectKind
    data object Constructors : SubjectKind
    data object Methods : SubjectKind
    data class CustomTransformer(val description: String?) : SubjectKind
}

/**
 * Boolean predicate tree retained for normalized rule metadata.
 */
sealed interface PredicateExpr {
    data object All : PredicateExpr
    data class Leaf(val predicate: String) : PredicateExpr
    data class AreAnnotatedWith(val qualifiedName: String) : PredicateExpr
    data class AreNotAnnotatedWith(val qualifiedName: String) : PredicateExpr
    data class AreMetaAnnotatedWith(val qualifiedName: String, val expected: Boolean) : PredicateExpr
    data class AreAssignableTo(val qualifiedName: String, val expected: Boolean) : PredicateExpr
    data class Implement(val qualifiedName: String, val expected: Boolean) : PredicateExpr
    data class ResideInPackages(val patterns: List<String>) : PredicateExpr
    data class HaveSimpleNameEndingWith(val suffix: String) : PredicateExpr
    data class HaveSimpleNameNotEndingWith(val suffix: String) : PredicateExpr
    data class AreInterfaces(val expected: Boolean) : PredicateExpr
    data class AreEnums(val expected: Boolean) : PredicateExpr
    data class AreRecords(val expected: Boolean) : PredicateExpr
    data class And(val left: PredicateExpr, val right: PredicateExpr) : PredicateExpr
    data class Or(val left: PredicateExpr, val right: PredicateExpr) : PredicateExpr
}

/**
 * Boolean condition tree used by future subject-specific handlers.
 */
sealed interface ConditionExpr {
    data class Leaf(val condition: String) : ConditionExpr
    data class BeAnnotatedWith(val qualifiedName: String, val required: Boolean) : ConditionExpr
    data class ResideInPackages(val patterns: List<String>) : ConditionExpr
    data class HaveSimpleNameEndingWith(val suffix: String, val required: Boolean) : ConditionExpr
    data class BeInterfaces(val required: Boolean) : ConditionExpr
    data class BeEnums(val required: Boolean) : ConditionExpr
    data class BeRecords(val required: Boolean) : ConditionExpr
    data class HaveModifier(val modifier: ClassModifier, val required: Boolean) : ConditionExpr
    data class BeMetaAnnotatedWith(val qualifiedName: String, val required: Boolean) : ConditionExpr
    data class BeAssignableTo(val qualifiedName: String) : ConditionExpr
    data class AccessField(val ownerQualifiedName: String, val fieldName: String) : ConditionExpr
    data class CallMethod(
        val ownerQualifiedName: String,
        val methodName: String,
        val parameterTypeQualifiedNames: List<String>,
    ) : ConditionExpr
    data class And(val left: ConditionExpr, val right: ConditionExpr) : ConditionExpr
    data class Or(val left: ConditionExpr, val right: ConditionExpr) : ConditionExpr
}

/**
 * Statically evaluable selector facts for positive method and constructor subjects.
 */
sealed interface MemberPredicateExpr {
    data object All : MemberPredicateExpr
    data class IsAnnotatedWith(
        val qualifiedName: String,
        val metaAnnotated: Boolean,
    ) : MemberPredicateExpr

    data class DeclaredInClasses(val predicate: PredicateExpr) : MemberPredicateExpr
    data class And(val left: MemberPredicateExpr, val right: MemberPredicateExpr) : MemberPredicateExpr
    data class Or(val left: MemberPredicateExpr, val right: MemberPredicateExpr) : MemberPredicateExpr
}

/**
 * Statically evaluable declaration conditions for positive member subjects.
 */
sealed interface MemberConditionExpr {
    data object BePrivate : MemberConditionExpr
    data object BeStatic : MemberConditionExpr
    data class HaveRawReturnType(val qualifiedName: String) : MemberConditionExpr
    data class BeAnnotatedWith(
        val qualifiedName: String,
        val metaAnnotated: Boolean,
        val required: Boolean,
    ) : MemberConditionExpr

    data class HaveModifier(val modifier: String, val required: Boolean) : MemberConditionExpr
    data class HaveName(val name: String, val required: Boolean) : MemberConditionExpr
    data class HaveNameMatching(
        val pattern: String,
        val required: Boolean,
    ) : MemberConditionExpr {
        internal val compiledPattern = Regex(pattern)
    }
    data class And(val left: MemberConditionExpr, val right: MemberConditionExpr) : MemberConditionExpr
    data class Or(val left: MemberConditionExpr, val right: MemberConditionExpr) : MemberConditionExpr
}

/**
 * Positive declaration subjects that can be evaluated without visiting method bodies.
 */
sealed interface MemberSubjectKind {
    data object Fields : MemberSubjectKind
    data object Methods : MemberSubjectKind
    data object Constructors : MemberSubjectKind
}

/**
 * Class-level ArchUnit modifier facts that Java PSI can prove without loading
 * bytecode-only metadata or interpreting user code.
 */
enum class ClassModifier {
    FINAL,
}

/**
 * Determines whether a supported rule requires or forbids declarations that satisfy its condition.
 * [POSITIVE] reports selected declarations whose condition is not satisfied; [NEGATIVE] reports
 * selected declarations whose condition is satisfied.
 */
enum class RulePolarity {
    POSITIVE,
    NEGATIVE,
}

/**
 * Parser support state for a discovered ArchUnit rule or call chain.
 */
sealed interface SupportStatus {
    data object Supported : SupportStatus
    data class Unsupported(val reason: UnsupportedReason) : SupportStatus
}

/**
 * Stable reason why a discovered rule cannot safely produce live diagnostics.
 */
sealed interface UnsupportedReason {
    data object UnsupportedMultiPackageRuleShape : UnsupportedReason
    data object CustomOrMetaAnnotationPredicates : UnsupportedReason
    data object HelperBackedCustomCondition : UnsupportedReason
    data class UnsupportedEntryPoint(val entryPoint: String) : UnsupportedReason
    data class InvalidArity(
        val methodName: String,
        val expected: String,
        val actual: Int,
    ) : UnsupportedReason

    data class UnsupportedArgument(
        val methodName: String,
        val position: Int,
        val kind: String,
    ) : UnsupportedReason

    data class UnresolvedSymbol(
        val methodName: String,
        val symbol: String,
    ) : UnsupportedReason

    data object UnsupportedOrAmbiguousRuleChain : UnsupportedReason
}

/**
 * Normalized rule descriptor for engine metadata.
 */
data class RuleDescriptor(
    val subject: SubjectKind,
    val sourcePointer: SmartPsiElementPointer<out PsiElement>,
    val scope: AnalyzeScope,
    val predicate: PredicateExpr,
    val condition: ConditionExpr,
    val reason: String?,
    val supportStatus: SupportStatus,
    val polarity: RulePolarity = RulePolarity.POSITIVE,
)

/**
 * One discovered ArchUnit rule source with retained normalized metadata.
 *
 * [liveRule] stays null for unsupported shapes so current inspections keep
 * reporting only the rules they can evaluate safely.
 */
data class DiscoveredArchRule(
    val ruleName: String,
    val descriptor: RuleDescriptor,
    val liveRule: LiveArchRule?,
)

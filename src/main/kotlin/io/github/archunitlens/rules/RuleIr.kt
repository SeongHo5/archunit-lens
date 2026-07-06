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
    data class And(val left: PredicateExpr, val right: PredicateExpr) : PredicateExpr
    data class Or(val left: PredicateExpr, val right: PredicateExpr) : PredicateExpr
}

/**
 * Boolean condition tree used by future subject-specific handlers.
 */
sealed interface ConditionExpr {
    data class Leaf(val condition: String) : ConditionExpr
    data class And(val left: ConditionExpr, val right: ConditionExpr) : ConditionExpr
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
    data class UnsupportedEntryPoint(val entryPoint: String) : UnsupportedReason
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

package io.github.archunitlens.rules

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
 * Boolean predicate tree used by future subject-specific handlers.
 */
sealed interface PredicateExpr {
    data object All : PredicateExpr
    data class Leaf(val predicate: String) : PredicateExpr
    data class And(val left: PredicateExpr, val right: PredicateExpr) : PredicateExpr
    data class Or(val left: PredicateExpr, val right: PredicateExpr) : PredicateExpr
    data class Not(val inner: PredicateExpr) : PredicateExpr
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
    data class Unsupported(val reason: String) : SupportStatus
}

/**
 * Normalized rule descriptor for the extensible engine roadmap.
 */
data class RuleDescriptor(
    val subject: SubjectKind,
    val scope: AnalyzeScope,
    val predicate: PredicateExpr,
    val condition: ConditionExpr,
    val reason: String?,
    val supportStatus: SupportStatus,
)

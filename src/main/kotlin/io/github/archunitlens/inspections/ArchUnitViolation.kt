package io.github.archunitlens.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.InspectionMessage
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.inspections.quickfix.AppendClassSuffixQuickFix
import io.github.archunitlens.inspections.quickfix.GoToArchRuleQuickFix
import io.github.archunitlens.inspections.quickfix.RemoveAnnotationQuickFix
import io.github.archunitlens.rules.LiveArchRule

/**
 * Semantic reason why a supported ArchUnit rule was violated.
 *
 * The inspection visitor should report these condition-level violations instead
 * of hard-coding quick fixes per PSI branch.
 */
internal sealed interface ArchUnitViolation {
    val rule: LiveArchRule

    data class ForbiddenDependency(
        override val rule: LiveArchRule,
        val targetQualifiedName: String,
        val forbiddenPackagePattern: String,
        val referenceKind: String,
    ) : ArchUnitViolation

    data class MissingClassNameSuffix(
        override val rule: LiveArchRule,
        val requiredSuffix: String,
    ) : ArchUnitViolation

    data class ForbiddenAnnotation(
        override val rule: LiveArchRule,
        val annotationName: String,
    ) : ArchUnitViolation

    data class MissingInterface(
        override val rule: LiveArchRule,
    ) : ArchUnitViolation

    data class MissingAssignableType(
        override val rule: LiveArchRule,
        val assignableToQualifiedName: String,
    ) : ArchUnitViolation
}

/**
 * Builds quick fixes from condition semantics.
 */
internal fun ArchUnitViolation.quickFixes(): Array<LocalQuickFix> = when (this) {
    is ArchUnitViolation.ForbiddenDependency -> navigationFixes()
    is ArchUnitViolation.MissingClassNameSuffix -> arrayOf(
        GoToArchRuleQuickFix(rule.ruleName, rule.sourcePointer),
        AppendClassSuffixQuickFix(requiredSuffix),
    )
    is ArchUnitViolation.ForbiddenAnnotation -> arrayOf(
        RemoveAnnotationQuickFix(annotationName),
        GoToArchRuleQuickFix(rule.ruleName, rule.sourcePointer),
    )
    is ArchUnitViolation.MissingInterface -> navigationFixes()
    is ArchUnitViolation.MissingAssignableType -> navigationFixes()
}

@InspectionMessage
internal fun ArchUnitViolation.problemMessage(): String = listOfNotNull(
    ArchUnitLensBundle.message("inspection.problem.message", rule.ruleName),
    dependencyMessage(),
    rule.reason?.let { ArchUnitLensBundle.message("inspection.problem.reason", it) },
).joinToString(separator = "\n")

private fun ArchUnitViolation.navigationFixes(): Array<LocalQuickFix> = arrayOf(
    GoToArchRuleQuickFix(rule.ruleName, rule.sourcePointer),
)

private fun ArchUnitViolation.dependencyMessage(): String? = when (this) {
    is ArchUnitViolation.ForbiddenDependency -> ArchUnitLensBundle.message(
        "inspection.problem.dependency",
        referenceKind,
        targetQualifiedName,
        forbiddenPackagePattern,
    )
    else -> null
}

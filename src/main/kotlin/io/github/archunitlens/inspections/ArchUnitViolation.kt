package io.github.archunitlens.inspections

import com.intellij.codeInspection.LocalQuickFix
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
    ) : ArchUnitViolation

    data class MissingClassNameSuffix(
        override val rule: LiveArchRule,
        val requiredSuffix: String,
    ) : ArchUnitViolation

    data class ForbiddenAnnotation(
        override val rule: LiveArchRule,
        val annotationName: String,
    ) : ArchUnitViolation

    data class MissingAnnotation(
        override val rule: LiveArchRule,
        val annotationName: String,
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
    is ArchUnitViolation.MissingAnnotation -> navigationFixes()
}

internal fun ArchUnitViolation.problemMessage(): String = buildString {
    append("ArchUnit rule violation: ")
    append(rule.ruleName)
    rule.reason?.let {
        append("\nReason: ")
        append(it)
    }
}

private fun ArchUnitViolation.navigationFixes(): Array<LocalQuickFix> = arrayOf(
    GoToArchRuleQuickFix(rule.ruleName, rule.sourcePointer),
)

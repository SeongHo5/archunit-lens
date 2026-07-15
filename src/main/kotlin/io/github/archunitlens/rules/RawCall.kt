package io.github.archunitlens.rules

import com.intellij.psi.PsiArrayAccessExpression
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression

/**
 * A single static ArchUnit DSL method call extracted from a PSI initializer.
 */
data class RawCall(
    val name: String,
    val arguments: List<RawArgument>,
) {
    val stringArgs: List<String>
        get() = arguments.filterIsInstance<RawArgument.StringLiteral>().map { it.value }

    val classLiteralArgs: List<String>
        get() = arguments.filterIsInstance<RawArgument.ClassLiteral>().map { it.canonicalName }
}

/** Ordered, PSI-free argument fact retained for fail-closed rule parsing. */
sealed interface RawArgument {
    val position: Int

    data class StringLiteral(
        override val position: Int,
        val value: String,
    ) : RawArgument

    data class ClassLiteral(
        override val position: Int,
        val canonicalName: String,
        val resolvedQualifiedName: String? = null,
    ) : RawArgument

    data class Reference(
        override val position: Int,
        val text: String,
    ) : RawArgument

    data class NestedCall(
        override val position: Int,
        val methodName: String?,
    ) : RawArgument

    data class Lambda(
        override val position: Int,
    ) : RawArgument

    data class CustomExpression(
        override val position: Int,
        val text: String,
    ) : RawArgument
}

/**
 * Extracts ArchUnit DSL calls in source order without executing user code.
 */
object RawCallExtractor {
    fun from(expression: PsiExpression): List<RawCall> {
        val calls = mutableListOf<RawCall>()
        var current = expression.unwrapped() as? PsiMethodCallExpression
        while (current != null) {
            current.methodExpression.referenceName?.let { methodName ->
                calls += RawCall(
                    name = methodName,
                    arguments = current.argumentList.expressions.mapIndexed { index, argument ->
                        argument.toRawArgument(index)
                    },
                )
            }
            current = current.methodExpression.qualifierExpression?.unwrapped() as? PsiMethodCallExpression
        }
        return calls.asReversed()
    }

    private fun PsiExpression.toRawArgument(position: Int): RawArgument = when (this) {
        is PsiLiteralExpression -> (value as? String)
            ?.let { RawArgument.StringLiteral(position, it) }
            ?: RawArgument.CustomExpression(position, text)
        is PsiClassObjectAccessExpression -> RawArgument.ClassLiteral(
            position,
            operand.type.canonicalText,
            (operand.type as? PsiClassType)?.resolve()?.qualifiedName,
        )
        is PsiMethodCallExpression -> RawArgument.NestedCall(position, methodExpression.referenceName)
        is PsiLambdaExpression -> RawArgument.Lambda(position)
        is PsiNewExpression -> RawArgument.CustomExpression(position, text)
        is PsiReferenceExpression, is PsiArrayAccessExpression ->
            RawArgument.Reference(position, text)
        else -> RawArgument.CustomExpression(position, text)
    }

    private fun PsiExpression.unwrapped(): PsiExpression = when (this) {
        is PsiParenthesizedExpression -> expression?.unwrapped() ?: this
        else -> this
    }
}

package io.github.archunitlens.rules

import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression

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

    private fun PsiExpression.stringLiteralValue(): String? = (this as? PsiLiteralExpression)?.value as? String

    private fun PsiExpression.classLiteralName(): String? = (this as? PsiClassObjectAccessExpression)?.operand?.type?.canonicalText

    private fun PsiExpression.toRawArgument(position: Int): RawArgument = when {
        stringLiteralValue() != null -> RawArgument.StringLiteral(position, stringLiteralValue()!!)
        classLiteralName() != null -> RawArgument.ClassLiteral(position, classLiteralName()!!)
        this is PsiMethodCallExpression -> RawArgument.NestedCall(position, methodExpression.referenceName)
        this is PsiLambdaExpression -> RawArgument.Lambda(position)
        this is PsiNewExpression -> RawArgument.CustomExpression(position, text)
        this is com.intellij.psi.PsiReferenceExpression || this is com.intellij.psi.PsiArrayAccessExpression ->
            RawArgument.Reference(position, text)
        else -> RawArgument.CustomExpression(position, text)
    }

    private fun PsiExpression.unwrapped(): PsiExpression = when (this) {
        is PsiParenthesizedExpression -> expression?.unwrapped() ?: this
        else -> this
    }
}

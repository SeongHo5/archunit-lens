package io.github.archunitlens.rules

import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression

/**
 * A single static ArchUnit DSL method call extracted from a PSI initializer.
 */
data class RawCall(
    val name: String,
    val stringArgs: List<String>,
    val classLiteralArgs: List<String>,
)

/**
 * Extracts ArchUnit DSL calls in source order without executing user code.
 */
object RawCallExtractor {
    fun from(expression: PsiExpression): List<RawCall> {
        val calls = mutableListOf<RawCall>()
        expression.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                expression.methodExpression.referenceName?.let { methodName ->
                    calls += RawCall(
                        name = methodName,
                        stringArgs = expression.argumentList.expressions.mapNotNull { it.stringLiteralValue() },
                        classLiteralArgs = expression.argumentList.expressions.mapNotNull { it.classLiteralName() },
                    )
                }
                super.visitMethodCallExpression(expression)
            }
        })
        return calls.asReversed()
    }

    private fun PsiExpression.stringLiteralValue(): String? = (this as? PsiLiteralExpression)?.value as? String

    private fun PsiExpression.classLiteralName(): String? = (this as? PsiClassObjectAccessExpression)?.operand?.type?.canonicalText
}

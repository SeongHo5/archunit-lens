package io.github.archunitlens.rules.evaluator

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import io.github.archunitlens.rules.ConditionExpr

internal data class ResolvedFieldAccess(
    val ownerQualifiedName: String,
    val fieldName: String,
)

internal data class ResolvedMethodCall(
    val ownerQualifiedName: String,
    val methodName: String,
    val parameterTypeQualifiedNames: List<String>,
)

/**
 * Resolves prefiltered Java access candidates without retaining PSI.
 *
 * The resolved member proves name and signature identity. Its symbolic owner
 * remains the qualifier's JVM-erased static type, matching ArchUnit's access
 * target owner; inherited, overridden, and differently bounded generic
 * accesses therefore differ from the exact owner named by the rule.
 */
internal object ExactCodeAccessEvaluator {
    fun resolveFieldAccess(reference: PsiReferenceExpression): ResolvedFieldAccess? {
        val field = reference.resolve() as? PsiField ?: return null
        val ownerQualifiedName = reference.qualifierExpression.symbolicOwnerQualifiedName()
            ?: unqualifiedTargetOwner(reference, field.hasModifierProperty(PsiModifier.STATIC), field.containingClass)?.qualifiedName
            ?: return null
        return ResolvedFieldAccess(ownerQualifiedName, field.name)
    }

    fun resolveMethodCall(call: PsiMethodCallExpression): ResolvedMethodCall? {
        val method = call.resolveMethod() ?: return null
        val ownerQualifiedName = call.methodExpression.qualifierExpression.symbolicOwnerQualifiedName()
            ?: unqualifiedTargetOwner(
                call.methodExpression,
                method.hasModifierProperty(PsiModifier.STATIC),
                method.containingClass,
            )?.qualifiedName
            ?: return null
        return ResolvedMethodCall(
            ownerQualifiedName = ownerQualifiedName,
            methodName = method.name,
            parameterTypeQualifiedNames = method.parameterList.parameters.map { it.type.erasureText() },
        )
    }

    fun matches(condition: ConditionExpr.AccessField, access: ResolvedFieldAccess): Boolean = condition.ownerQualifiedName == access.ownerQualifiedName &&
        condition.fieldName == access.fieldName

    fun matches(condition: ConditionExpr.CallMethod, call: ResolvedMethodCall): Boolean = condition.ownerQualifiedName == call.ownerQualifiedName &&
        condition.methodName == call.methodName &&
        condition.parameterTypeQualifiedNames == call.parameterTypeQualifiedNames

    private fun unqualifiedTargetOwner(
        expression: PsiReferenceExpression,
        isStatic: Boolean,
        declarationOwner: PsiClass?,
    ): PsiClass? = if (isStatic) {
        declarationOwner
    } else {
        PsiTreeUtil.getParentOfType(expression, PsiClass::class.java)
    }

    private fun com.intellij.psi.PsiExpression?.symbolicOwnerQualifiedName(): String? = this?.type?.erasureText()

    private fun com.intellij.psi.PsiType.erasureText(): String = TypeConversionUtil.erasure(this).canonicalText
}

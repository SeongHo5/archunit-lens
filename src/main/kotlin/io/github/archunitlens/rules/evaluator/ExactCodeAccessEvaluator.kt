package io.github.archunitlens.rules.evaluator

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
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

internal data class ResolvedConstructorCall(
    val ownerQualifiedName: String,
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
        val ownerQualifiedName = reference.qualifierExpression.symbolicOwnerQualifiedName(
            declarationOwner = field.containingClass,
            isStatic = field.hasModifierProperty(PsiModifier.STATIC),
        )
            ?: unqualifiedTargetOwner(reference, field.containingClass)?.qualifiedName
            ?: return null
        return ResolvedFieldAccess(ownerQualifiedName, field.name)
    }

    fun resolveMethodCall(call: PsiMethodCallExpression): ResolvedMethodCall? {
        val method = call.resolveMethod()?.takeUnless(PsiMethod::isConstructor) ?: return null
        val ownerQualifiedName = call.methodExpression.qualifierExpression.symbolicOwnerQualifiedName(
            declarationOwner = method.containingClass,
            isStatic = method.hasModifierProperty(PsiModifier.STATIC),
        )
            ?: unqualifiedTargetOwner(call.methodExpression, method.containingClass)?.qualifiedName
            ?: return null
        return ResolvedMethodCall(
            ownerQualifiedName = ownerQualifiedName,
            methodName = method.name,
            parameterTypeQualifiedNames = method.rawParameterTypeQualifiedNames(),
        )
    }

    fun resolveNewExpression(expression: PsiNewExpression): ResolvedConstructorCall? {
        if (expression.anonymousClass != null) return null
        val constructor = expression.resolveConstructor() ?: return null
        val ownerQualifiedName = expression.type?.erasureText() ?: return null
        return ResolvedConstructorCall(ownerQualifiedName, constructor.rawParameterTypeQualifiedNames())
    }

    fun resolveExplicitConstructorCall(call: PsiMethodCallExpression): ResolvedConstructorCall? {
        val referenceName = call.methodExpression.referenceName
        if (referenceName != "this" && referenceName != "super") return null
        val constructor = call.resolveMethod()?.takeIf(PsiMethod::isConstructor) ?: return null
        val ownerQualifiedName = if (referenceName == "this") {
            PsiTreeUtil.getParentOfType(call, PsiClass::class.java)?.qualifiedName
        } else {
            constructor.containingClass?.qualifiedName
        } ?: return null
        return ResolvedConstructorCall(ownerQualifiedName, constructor.rawParameterTypeQualifiedNames())
    }

    fun matches(condition: ConditionExpr.AccessField, access: ResolvedFieldAccess): Boolean = condition.ownerQualifiedName == access.ownerQualifiedName &&
        condition.fieldName == access.fieldName

    fun matches(condition: ConditionExpr.CallMethod, call: ResolvedMethodCall): Boolean = condition.ownerQualifiedName == call.ownerQualifiedName &&
        condition.methodName == call.methodName &&
        condition.parameterTypeQualifiedNames == call.parameterTypeQualifiedNames

    fun matches(condition: ConditionExpr.CallConstructor, call: ResolvedConstructorCall): Boolean = condition.ownerQualifiedName == call.ownerQualifiedName &&
        condition.parameterTypeQualifiedNames == call.parameterTypeQualifiedNames

    private fun unqualifiedTargetOwner(
        expression: PsiReferenceExpression,
        declarationOwner: PsiClass?,
    ): PsiClass? {
        if (declarationOwner == null) return null
        return generateSequence(PsiTreeUtil.getParentOfType(expression, PsiClass::class.java)) { lexicalOwner ->
            PsiTreeUtil.getParentOfType(lexicalOwner, PsiClass::class.java, true)
        }.firstOrNull { lexicalOwner -> InheritanceUtil.isInheritorOrSelf(lexicalOwner, declarationOwner, true) }
            ?: declarationOwner
    }

    private fun com.intellij.psi.PsiExpression?.symbolicOwnerQualifiedName(
        declarationOwner: PsiClass?,
        isStatic: Boolean,
    ): String? = this?.type?.erasureText()
        ?: (this as? PsiReferenceExpression)
            ?.takeIf { isStatic }
            ?.symbolicStaticOwnerQualifiedName(declarationOwner)

    private fun PsiReferenceExpression.symbolicStaticOwnerQualifiedName(declarationOwner: PsiClass?): String? {
        val qualifierName = qualifiedName ?: return null
        if ('.' in qualifierName) return qualifierName

        generateSequence(PsiTreeUtil.getParentOfType(this, PsiClass::class.java)) { lexicalOwner ->
            PsiTreeUtil.getParentOfType(lexicalOwner, PsiClass::class.java, true)
        }.firstOrNull { it.name == qualifierName }
            ?.qualifiedName
            ?.let { return it }
        if (declarationOwner?.name == qualifierName) return declarationOwner.qualifiedName

        val javaFile = containingFile as? PsiJavaFile ?: return null
        javaFile.importList
            ?.importStatements
            ?.firstOrNull { !it.isOnDemand && it.qualifiedName?.substringAfterLast('.') == qualifierName }
            ?.qualifiedName
            ?.let { return it }
        return javaFile.packageName.takeIf(String::isNotEmpty)?.let { "$it.$qualifierName" }
    }

    private fun PsiMethod.rawParameterTypeQualifiedNames(): List<String> = parameterList.parameters.map { parameter ->
        parameter.type.erasureText()
    }

    private fun PsiType.erasureText(): String {
        val arrayType = (this as? PsiEllipsisType)?.toArrayType() ?: this
        return TypeConversionUtil.erasure(arrayType).canonicalText
    }
}

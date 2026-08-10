package io.github.archunitlens.rules.evaluator

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.JavaPsiConstructorUtil
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
 * A resolved member normally proves name and signature identity. An implicit
 * default constructor instead requires an accessible resolved class with no
 * declared constructors. The symbolic owner remains the qualifier's
 * JVM-erased static type, matching ArchUnit's access target owner; inherited,
 * overridden, and differently bounded generic accesses therefore differ from
 * the exact owner named by the rule.
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
        val constructor = expression.resolveConstructor()
        if (constructor != null) {
            val ownerQualifiedName = expression.type?.erasureText() ?: return null
            return ResolvedConstructorCall(ownerQualifiedName, constructor.rawParameterTypeQualifiedNames())
        }
        return expression.resolveImplicitDefaultConstructor()
    }

    fun resolveExplicitConstructorCall(call: PsiMethodCallExpression): ResolvedConstructorCall? {
        val referenceName = call.methodExpression.referenceName
        if (referenceName != "this" && referenceName != "super") return null
        val constructor = call.resolveMethod()?.takeIf(PsiMethod::isConstructor)
        if (constructor == null) return call.resolveImplicitDefaultSuperConstructor(referenceName)
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

    private fun PsiNewExpression.resolveImplicitDefaultConstructor(): ResolvedConstructorCall? {
        if (!hasEmptyCompleteArgumentList()) return null
        val classResult = classReference?.advancedResolve(false) ?: return null
        if (!classResult.isValidResult || !classResult.isAccessible) return null
        val targetClass = classResult.element as? PsiClass ?: return null
        if (!targetClass.hasImplicitDefaultConstructor() || !targetClass.canInstantiateAt(this)) return null
        return ResolvedConstructorCall(targetClass.qualifiedName ?: return null, emptyList())
    }

    private fun PsiMethodCallExpression.resolveImplicitDefaultSuperConstructor(
        referenceName: String,
    ): ResolvedConstructorCall? {
        if (referenceName != "super" || !hasEmptyCompleteArgumentList()) return null
        val enclosingConstructor = PsiTreeUtil.getParentOfType(this, PsiMethod::class.java) ?: return null
        if (!enclosingConstructor.isConstructor ||
            JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(enclosingConstructor) != this
        ) {
            return null
        }
        val sourceClass = enclosingConstructor.containingClass?.takeUnless { it.isEnum || it.isRecord } ?: return null
        val targetClass = sourceClass.accessibleDirectSuperClass() ?: return null
        if (!targetClass.hasImplicitDefaultConstructor() || !targetClass.hasValidSuperEnclosingInstance(this)) return null
        return ResolvedConstructorCall(targetClass.qualifiedName ?: return null, emptyList())
    }

    private fun com.intellij.psi.PsiCall.hasEmptyCompleteArgumentList(): Boolean {
        if (PsiTreeUtil.findChildOfType(this, PsiErrorElement::class.java) != null) return false
        return argumentList?.expressions?.isEmpty() == true
    }

    private fun PsiClass.hasImplicitDefaultConstructor(): Boolean = constructors.isEmpty() &&
        this !is PsiTypeParameter &&
        !isInterface &&
        !isAnnotationType &&
        !isEnum &&
        !isRecord

    private fun PsiClass.canInstantiateAt(expression: PsiNewExpression): Boolean {
        if (hasModifierProperty(PsiModifier.ABSTRACT)) return false
        if (!PsiUtil.isInnerClass(this)) return true
        val enclosingClass = containingClass ?: return false
        return expression.qualifier != null ||
            InheritanceUtil.hasEnclosingInstanceInScope(enclosingClass, expression, false, false)
    }

    private fun PsiClass.hasValidSuperEnclosingInstance(call: PsiMethodCallExpression): Boolean {
        if (!PsiUtil.isInnerClass(this)) return true
        val enclosingClass = containingClass ?: return false
        val qualifierType = call.methodExpression.qualifierExpression?.type
            ?: return InheritanceUtil.hasEnclosingInstanceInScope(enclosingClass, call, false, false)
        val qualifierClass = PsiUtil.resolveClassInType(TypeConversionUtil.erasure(qualifierType)) ?: return false
        return InheritanceUtil.isInheritorOrSelf(qualifierClass, enclosingClass, true)
    }

    private fun PsiClass.accessibleDirectSuperClass(): PsiClass? {
        val explicitSuperReferences = extendsList?.referenceElements.orEmpty()
        if (explicitSuperReferences.isEmpty()) {
            return superClass?.takeIf { it.qualifiedName == "java.lang.Object" }
        }
        val result = explicitSuperReferences.singleOrNull()?.advancedResolve(false) ?: return null
        if (!result.isValidResult || !result.isAccessible) return null
        return result.element as? PsiClass
    }

    private fun PsiMethod.rawParameterTypeQualifiedNames(): List<String> = parameterList.parameters.map { parameter ->
        parameter.type.erasureText()
    }

    private fun PsiType.erasureText(): String {
        val arrayType = (this as? PsiEllipsisType)?.toArrayType() ?: this
        return TypeConversionUtil.erasure(arrayType).canonicalText
    }
}

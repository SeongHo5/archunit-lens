package io.github.archunitlens.rules

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiArrayAccessExpression
import com.intellij.psi.PsiArrayInitializerExpression
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil

private const val ARCHUNIT_JAVA_MODIFIER_FQN = "com.tngtech.archunit.core.domain.JavaModifier"

/** Result of a short-lived, parser-only static argument proof. */
internal sealed interface StaticArgumentResult<out T> {
    data class Resolved<T>(val value: T) : StaticArgumentResult<T>

    data class Unsupported(val detail: String) : StaticArgumentResult<Nothing>

    data class Unresolved(val symbol: String) : StaticArgumentResult<Nothing>
}

/**
 * Resolves the narrow class-fact arguments admitted by the live class-rule
 * subset. It is created only while a rule source is parsed and never escapes
 * into cached descriptors or live rules.
 */
internal class StaticClassFactResolver(
    source: ArchRuleSource,
    callsWithSource: List<Pair<RawCall, PsiMethodCallExpression>>,
) {
    private val sourceClass = source.fieldPointer.element?.containingClass
    private val sourceCalls = callsWithSource.map { it.second }

    fun packagePatterns(callIndex: Int): StaticArgumentResult<List<String>> {
        if (DumbService.isDumb(sourceCalls[callIndex].project)) {
            return StaticArgumentResult.Unsupported("indexing mode")
        }
        val call = sourceCalls[callIndex]
        val arguments = call.argumentList.expressions
        if (arguments.size != 1) {
            return StaticArgumentResult.Unsupported("package list must be the only argument")
        }
        val reference = arguments.single().unwrapped() as? PsiReferenceExpression
            ?: return StaticArgumentResult.Unsupported("package list is not a field reference")
        val field = reference.resolve() as? PsiField
            ?: return StaticArgumentResult.Unresolved(reference.referenceName ?: reference.text)
        val owner = sourceClass ?: return StaticArgumentResult.Unsupported("rule class is unavailable")
        if (field.containingClass != owner) {
            return StaticArgumentResult.Unsupported("package list '${field.name}' is declared outside the rule class")
        }
        if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
            return StaticArgumentResult.Unsupported("package list '${field.name}' is not private")
        }
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) {
            return StaticArgumentResult.Unsupported("package list '${field.name}' is not static final")
        }
        val componentType = (field.type as? PsiArrayType)?.componentType
        if (componentType != PsiType.getJavaLangString(field.manager, field.resolveScope)) {
            return StaticArgumentResult.Unsupported("package list '${field.name}' is not a String[]")
        }
        val values = field.initializer.literalStringArrayValues()
            ?: return StaticArgumentResult.Unsupported("package list '${field.name}' is not literal-only")
        owner.enclosingNest().arrayFieldSafetyFailure(field, reference)?.let { return StaticArgumentResult.Unsupported(it) }
        return StaticArgumentResult.Resolved(values)
    }

    fun modifier(callIndex: Int): StaticArgumentResult<ClassModifier> {
        if (DumbService.isDumb(sourceCalls[callIndex].project)) {
            return StaticArgumentResult.Unsupported("indexing mode")
        }
        val expression = sourceCalls[callIndex].argumentList.expressions.singleOrNull()?.unwrapped()
            ?: return StaticArgumentResult.Unsupported("modifier must be one enum constant")
        val reference = expression as? PsiReferenceExpression
            ?: return StaticArgumentResult.Unsupported("modifier is not an enum constant")
        val field = reference.resolve() as? PsiField
            ?: return StaticArgumentResult.Unresolved(reference.referenceName ?: reference.text)
        if (field.containingClass?.qualifiedName != ARCHUNIT_JAVA_MODIFIER_FQN) {
            return StaticArgumentResult.Unsupported("modifier '${reference.text}' is not ArchUnit JavaModifier")
        }
        return when (field.name) {
            ClassModifier.FINAL.name -> StaticArgumentResult.Resolved(ClassModifier.FINAL)
            else -> StaticArgumentResult.Unsupported("ArchUnit JavaModifier.${field.name} is not supported for classes")
        }
    }

    private fun PsiExpression?.literalStringArrayValues(): List<String>? {
        val initializer = this?.unwrapped()
        val arrayInitializer = when (initializer) {
            is PsiArrayInitializerExpression -> initializer
            is PsiNewExpression -> initializer.arrayInitializer
            else -> null
        } ?: return null
        return arrayInitializer.initializers.map { element ->
            (element.unwrapped() as? PsiLiteralExpression)?.value as? String ?: return null
        }
    }

    private fun PsiClass.arrayFieldSafetyFailure(
        field: PsiField,
        allowedReference: PsiReferenceExpression,
    ): String? {
        PsiTreeUtil.findChildrenOfType(this, PsiReferenceExpression::class.java).forEach { reference ->
            if (
                reference.resolve() != field ||
                reference == allowedReference
            ) {
                return@forEach
            }
            val assignment = PsiTreeUtil.getParentOfType(reference, PsiAssignmentExpression::class.java)
            if (assignment?.lExpression is PsiArrayAccessExpression) {
                return "package list '${field.name}' has a mutable array element write"
            }
            return "package list '${field.name}' escapes the supported DSL use"
        }
        return null
    }

    private fun PsiClass.enclosingNest(): PsiClass {
        var nestHost = this
        while (true) {
            nestHost = nestHost.containingClass ?: return nestHost
        }
    }

    private fun PsiExpression.unwrapped(): PsiExpression = when (this) {
        is PsiParenthesizedExpression -> expression?.unwrapped() ?: this
        else -> this
    }
}

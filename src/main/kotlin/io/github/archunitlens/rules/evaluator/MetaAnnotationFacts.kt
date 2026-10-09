package io.github.archunitlens.rules.evaluator

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiReferenceExpression

private const val RETENTION_ANNOTATION = "java.lang.annotation.Retention"
private const val RETENTION_POLICY = "java.lang.annotation.RetentionPolicy"

/** Matches only annotation paths that the compiler retains in class files, including direct matches. */
internal object MetaAnnotationFacts {
    fun matches(annotation: PsiAnnotation, qualifiedName: String): Boolean? = matches(annotation, qualifiedName, mutableSetOf())

    private fun matches(
        annotation: PsiAnnotation,
        qualifiedName: String,
        visited: MutableSet<String>,
    ): Boolean? {
        val annotationClass = annotation.resolveAnnotationType()
            ?: annotation.qualifiedName?.let { JavaPsiFacade.getInstance(annotation.project).findClass(it, annotation.resolveScope) }
            ?: return null
        when (annotationClass.isRetainedInClassFile()) {
            false -> return false
            null -> return null
            true -> Unit
        }
        val current = annotationClass.qualifiedName ?: return null
        if (current == qualifiedName) return true
        if (!visited.add(current)) return false
        var unresolved = false
        annotationClass.modifierList?.annotations.orEmpty().forEach { metaAnnotation ->
            when (matches(metaAnnotation, qualifiedName, visited)) {
                true -> return true
                null -> unresolved = true
                false -> Unit
            }
        }
        return if (unresolved) null else false
    }

    private fun PsiClass.isRetainedInClassFile(): Boolean? {
        val annotations = modifierList?.annotations.orEmpty()
        val retention = annotations.filter { it.qualifiedName == RETENTION_ANNOTATION }
        if (retention.size > 1) return null
        if (retention.isEmpty()) {
            // An unresolved Retention declaration cannot establish the default CLASS policy.
            if (annotations.any { it.nameReferenceElement?.referenceName == "Retention" && it.resolveAnnotationType() == null }) return null
            return true
        }
        val value = retention.single().findDeclaredAttributeValue("value") as? PsiReferenceExpression ?: return null
        val policy = value.resolve() as? PsiEnumConstant ?: return null
        if (policy.containingClass?.qualifiedName != RETENTION_POLICY) return null
        return when (policy.name) {
            "SOURCE" -> false
            "CLASS", "RUNTIME" -> true
            else -> null
        }
    }
}

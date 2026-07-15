package io.github.archunitlens.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import io.github.archunitlens.ArchUnitLensBundle

/**
 * Removes an annotation that violates a supported forbidden-annotation rule.
 */
class RemoveAnnotationQuickFix(private val annotationName: String) : LocalQuickFix {
    override fun getFamilyName() = ArchUnitLensBundle.message("quickfix.removeAnnotation.family")

    override fun getName() = ArchUnitLensBundle.message(
        "quickfix.removeAnnotation.name",
        annotationName.substringAfterLast('.'),
    )

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val annotation = descriptor.psiElement as? PsiAnnotation ?: return
        annotation.delete()
    }
}

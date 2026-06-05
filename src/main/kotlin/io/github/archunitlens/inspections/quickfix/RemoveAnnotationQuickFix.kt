package io.github.archunitlens.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation

/**
 * Removes an annotation that violates a supported forbidden-annotation rule.
 */
class RemoveAnnotationQuickFix(private val annotationName: String) : LocalQuickFix {
    override fun getFamilyName() = "Remove forbidden annotation"

    override fun getName() = "Remove annotation forbidden by ArchUnit rule '${annotationName.substringAfterLast('.')}'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val annotation = descriptor.psiElement as? PsiAnnotation ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            annotation.delete()
        }
    }
}

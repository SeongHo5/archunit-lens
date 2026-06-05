package io.github.archunitlens.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * Navigates from an inspection warning to the ArchUnit field that produced it.
 */
class GoToArchRuleQuickFix(
    private val ruleName: String,
    private val sourcePointer: SmartPsiElementPointer<out PsiElement>,
) : LocalQuickFix {
    override fun getFamilyName() = "Go to ArchUnit rule"

    override fun getName() = "Go to ArchUnit rule '$ruleName'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = sourcePointer.element ?: return
        val virtualFile = element.containingFile?.virtualFile ?: return
        OpenFileDescriptor(project, virtualFile, element.textOffset).navigate(true)
    }
}

package io.github.archunitlens.inspections.quickfix

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import io.github.archunitlens.ArchUnitLensBundle

/**
 * Navigates from an inspection warning to the ArchUnit field that produced it.
 */
class GoToArchRuleQuickFix(
    private val ruleName: String,
    private val sourcePointer: SmartPsiElementPointer<out PsiElement>,
) : LocalQuickFix {
    override fun getFamilyName() = ArchUnitLensBundle.message("quickfix.goto.family")

    override fun getName() = ArchUnitLensBundle.message("quickfix.goto.name", ruleName)

    override fun generatePreview(project: Project, descriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? = null

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = sourcePointer.element ?: return
        val virtualFile = element.containingFile?.virtualFile ?: return
        OpenFileDescriptor(project, virtualFile, element.textOffset).navigate(true)
    }
}

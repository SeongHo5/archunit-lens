package io.github.archunitlens.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.refactoring.rename.RenameProcessor
import io.github.archunitlens.ArchUnitLensBundle

/**
 * Renames a class by appending the suffix required by a supported ArchUnit rule.
 */
class AppendClassSuffixQuickFix(private val requiredSuffix: String) : LocalQuickFix {
    override fun getFamilyName() = ArchUnitLensBundle.message("quickfix.appendSuffix.family")

    override fun getName() = ArchUnitLensBundle.message("quickfix.appendSuffix.name", requiredSuffix)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val psiClass = descriptor.psiElement.parent as? PsiClass ?: return
        val currentName = psiClass.name ?: return
        if (currentName.endsWith(requiredSuffix)) return

        RenameProcessor(project, psiClass, currentName + requiredSuffix, false, false).run()
    }
}

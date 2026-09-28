package io.github.archunitlens.inspections.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.inspections.ArchUnitLensInspection
import java.nio.file.Path

class AppendClassSuffixQuickFixTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ArchUnitLensInspection())
        myFixture.addFileToProject(
            "com/example/ArchitectureRules.java",
            testData("archrules/classSuffixQuickFix.java"),
        )
        myFixture.addFileToProject(
            "com/example/Caller.java",
            testData("javaSources/classSuffixQuickFix/Caller.java"),
        )
        val targetFile = myFixture.addFileToProject(
            "com/example/Target.java",
            testData("javaSources/classSuffixQuickFix/Target.java"),
        )
        myFixture.configureFromExistingVirtualFile(targetFile.virtualFile)
    }

    fun testPreviewDeclinesRefactoringAndLeavesOriginalPsiUnchanged() {
        val fix = myFixture.getAllQuickFixes().first { it.text.contains("Service") } as QuickFixWrapper
        val originalText = myFixture.file.text
        val originalCallerText = findClass("com.example.Caller")!!.containingFile.text

        val preview = ApplicationManager.getApplication().executeOnPooledThread<IntentionPreviewInfo?> {
            ApplicationManager.getApplication().runReadAction<IntentionPreviewInfo?> {
                fix.generatePreview(project, myFixture.editor, myFixture.file)
            }
        }.get()

        assertEquals(IntentionPreviewInfo.EMPTY, preview)
        assertNull(AppendClassSuffixQuickFix("Service").getFileModifierForPreview(myFixture.file))
        assertEquals(originalText, myFixture.file.text)
        assertEquals(originalCallerText, findClass("com.example.Caller")!!.containingFile.text)
        myFixture.launchAction(fix)
        assertNotNull(findClass("com.example.TargetService"))
    }

    fun testApplyingFixRenamesClassAndUsagesAndCanBeUndone() {
        val fix = myFixture.getAllQuickFixes().first { it.text.contains("Service") }

        myFixture.launchAction(fix)

        assertNotNull(findClass("com.example.TargetService"))
        assertTrue(findClass("com.example.Caller")!!.containingFile.text.contains("TargetService"))

        TestDialogManager.setTestDialog(TestDialog.OK, testRootDisposable)
        UndoManager.getInstance(project).undo(null)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertNotNull(findClass("com.example.Target"))
        val callerText = findClass("com.example.Caller")!!.containingFile.text
        assertTrue(callerText.contains("Target"))
        assertFalse(callerText.contains("TargetService"))
    }

    fun testCorrectiveActionRemainsAheadOfNavigationAction() {
        val fixes = myFixture.getAllQuickFixes()

        assertTrue(fixes.first().text.contains("Service"))
        assertTrue(fixes[1].text.contains("Go to ArchUnit rule"))
    }

    private fun testData(path: String): String = Path
        .of("src/test/testData", path)
        .toFile()
        .readText()

    private fun findClass(name: String): PsiClass? = JavaPsiFacade.getInstance(project)
        .findClass(name, GlobalSearchScope.allScope(project))
}

package io.github.archunitlens.inspections

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.SupportStatus
import io.github.archunitlens.rules.addArchUnitEntryPointDeclarations
import java.nio.file.Path

class ArchRuleEntryPointOwnershipInspectionTest : BasePlatformTestCase() {
    fun testRootDeclarationAppearingAndDisappearingInvalidatesCachedPackageSupport() {
        addResolvableRule()
        val service = project.service<ArchRuleProjectService>()
        assertNull(service.discoveriesForPackage("com.example").single().liveRule)
        assertTrue(service.rulesForPackage("com.example").isEmpty())

        myFixture.addArchUnitEntryPointDeclarations()

        assertNotNull(service.discoveriesForPackage("com.example").single().liveRule)
        assertEquals(1, service.rulesForPackage("com.example").size)
        val owner = JavaPsiFacade.getInstance(project).findClass(
            "com.tngtech.archunit.lang.syntax.ArchRuleDefinition",
            GlobalSearchScope.projectScope(project),
        ) ?: error("Expected ArchUnit entry-point declaration")
        WriteCommandAction.runWriteCommandAction(project) { owner.containingFile.delete() }

        assertNull(service.discoveriesForPackage("com.example").single().liveRule)
        assertTrue(service.rulesForPackage("com.example").isEmpty())
    }

    fun testCompetingImportedOwnerInvalidatesCachedLiveRuleWithoutEditingRuleSource() {
        myFixture.addArchUnitEntryPointDeclarations()
        addResolvableRule()
        val service = project.service<ArchRuleProjectService>()
        assertEquals(1, service.rulesForPackage("com.example").size)

        val competitor = myFixture.addFileToProject(
            "src/test/java/com/example/OtherRoots.java",
            testData("javaSources/entryPointOwnershipOtherRoots.java"),
        )

        assertTrue(service.rulesForPackage("com.example").isEmpty())
        assertNull(service.discoveriesForPackage("com.example").single().liveRule)
        WriteCommandAction.runWriteCommandAction(project) { competitor.delete() }
        assertEquals(1, service.rulesForPackage("com.example").size)
    }

    fun testHelperRootsStayMetadataOnlyInPackageLookupAndDoNotInvertOrPartiallyApplyRules() {
        myFixture.addArchUnitEntryPointDeclarations()
        myFixture.enableInspections(ArchUnitLensInspection())
        myFixture.addFileToProject(
            "src/test/java/com/example/EntryPointOwnershipHelpers.java",
            testData("archrules/entryPointOwnershipHelpers.java"),
        )
        myFixture.configureByText("Concrete.java", testData("javaSources/entryPointOwnershipConcrete.java"))

        val service = project.service<ArchRuleProjectService>()
        val discoveries = service.discoveriesForPackage("com.example")
        assertEquals(4, discoveries.size)
        assertTrue(discoveries.all { it.liveRule == null && it.descriptor.supportStatus is SupportStatus.Unsupported })
        assertTrue(service.rulesForPackage("com.example").isEmpty())
        val names = discoveries.map { it.ruleName }
        assertTrue(myFixture.doHighlighting().none { info -> names.any { info.description?.contains(it) == true } })
    }

    private fun testData(path: String): String = Path.of("src/test/testData/$path").toFile().readText()

    private fun addResolvableRule() {
        myFixture.addFileToProject(
            "src/test/java/com/example/EntryPointOwnershipResolved.java",
            testData("archrules/entryPointOwnershipResolved.java"),
        )
    }
}

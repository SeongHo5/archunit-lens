package io.github.archunitlens.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.ArchRuleProjectService
import java.nio.file.Path

class PackageDependencyBanInspectionTest : BasePlatformTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = dependencyProjectDescriptor

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ArchUnitLensInspection())
    }

    fun testExactPackagePatternMatchesImportsNestedClassesAndResolvedReferences() {
        addRuleFixture("dependencyPackageExact")
        addJavaFixture("com/forbidden/Api.java", "comForbiddenApi.java")
        addJavaFixture("com/forbidden/Outer.java", "comForbiddenOuter.java")
        addJavaFixture("com/forbidden/ReferenceApi.java", "comForbiddenReferenceApi.java")

        val listClass = JavaPsiFacade.getInstance(project).findClass("java.util.List", GlobalSearchScope.allScope(project))
        assertNotNull(listClass)
        assertEquals("java.util", (listClass!!.containingFile as PsiJavaFile).packageName)

        myFixture.configureByText("ExactTarget.java", testData("javaSources/dependencyPackageMatching/exactTarget.java"))

        val warnings = archUnitWarnings()
        assertEquals(3, warnings.size)
        assertTrue(warnings.any { it.contains("com.forbidden.Api") && it.contains("import") })
        assertTrue(warnings.any { it.contains("com.forbidden.Outer.Inner") && it.contains("import") })
        assertTrue(warnings.any { it.contains("com.forbidden.ReferenceApi") && it.contains("reference") })
        assertFalse(warnings.any { it.contains("java.util.List") })
        assertFalse(warnings.any { it.contains("com.forbidden.Missing") })
    }

    fun testSuffixPackagePatternDoesNotMatchClassNameOrSubpackage() {
        addRuleFixture("dependencyPackageSuffix")
        addJavaFixture("org/forbidden/Outer.java", "orgForbiddenOuter.java")
        addJavaFixture("org/forbidden/ReferenceApi.java", "orgForbiddenReferenceApi.java")
        addJavaFixture("org/forbidden/middle/MiddleApi.java", "orgForbiddenMiddleApi.java")
        addJavaFixture("com/allowed/forbidden.java", "allowedForbidden.java")

        myFixture.configureByText("SuffixTarget.java", testData("javaSources/dependencyPackageMatching/suffixTarget.java"))

        val warnings = archUnitWarnings()
        assertEquals(2, warnings.size)
        assertTrue(warnings.any { it.contains("org.forbidden.Outer.Inner") && it.contains("import") })
        assertTrue(warnings.any { it.contains("org.forbidden.ReferenceApi") && it.contains("reference") })
        assertFalse(warnings.any { it.contains("com.allowed.forbidden") })
        assertFalse(warnings.any { it.contains("org.forbidden.middle.MiddleApi") })
    }

    fun testExactPackagePatternMatchesCompiledClassImport() {
        addRuleFixture("dependencyPackageCompiled")
        myFixture.configureByText("CompiledTarget.java", testData("javaSources/dependencyPackageMatching/compiledTarget.java"))

        val warnings = archUnitWarnings()
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("java.util.List"))
        assertTrue(warnings.single().contains("import"))
    }

    fun testContainsPackagePatternMatchesOnlyPackageSegments() {
        addRuleFixture("dependencyPackageContains")
        addJavaFixture("org/forbidden/middle/MiddleApi.java", "orgForbiddenMiddleApi.java")
        addJavaFixture("com/allowed/forbidden.java", "allowedForbidden.java")

        myFixture.configureByText("ContainsTarget.java", testData("javaSources/dependencyPackageMatching/containsTarget.java"))

        val warnings = archUnitWarnings()
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("org.forbidden.middle.MiddleApi"))
        assertFalse(warnings.any { it.contains("com.allowed.forbidden") })
    }

    fun testDoesNotResolveImportsWhenNoDependencyRuleApplies() {
        val file = myFixture.configureByText(
            "NoDependencyRule.java",
            "package com.example; import java.util.List; class NoDependencyRule { List<String> values; }",
        ) as PsiJavaFile
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
        val importStatement = PsiTreeUtil.findChildOfType(file, PsiImportStatement::class.java) ?: error("Expected import statement")
        var resolveCount = 0
        val countingImport = object : PsiImportStatement by importStatement {
            override fun resolve(): PsiElement? {
                resolveCount += 1
                return importStatement.resolve()
            }
        }

        visitor.visitImportStatement(countingImport)

        assertEquals(0, resolveCount)
        assertTrue(holder.results.isEmpty())
        assertTrue(project.service<ArchRuleProjectService>().rulesForPackage(file.packageName).isEmpty())
    }

    fun testDependencyImportsAndReferencesStaySafeDuringDumbModeAfterWarmup() {
        addRuleFixture("dependencyPackageExact")
        addJavaFixture("com/forbidden/Api.java", "comForbiddenApi.java")
        myFixture.configureByText("DumbTarget.java", "package com.example; import com.forbidden.Api; class DumbTarget { Api api; }")
        assertTrue(archUnitWarnings().isNotEmpty())

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val file = myFixture.file as PsiJavaFile
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
            file.importList?.allImportStatements
                ?.filterIsInstance<PsiImportStatement>()
                ?.forEach(visitor::visitImportStatement)
            PsiTreeUtil.findChildrenOfType(file, PsiJavaCodeReferenceElement::class.java)
                .forEach(visitor::visitReferenceElement)

            assertTrue(holder.results.isEmpty())
        }
    }

    private fun addRuleFixture(name: String) {
        myFixture.addFileToProject("src/test/java/com/example/${name}Rules.java", testData("archrules/$name.java"))
    }

    private fun addJavaFixture(path: String, fixtureName: String) {
        myFixture.addFileToProject(
            "src/test/java/$path",
            testData("javaSources/dependencyPackageMatching/$fixtureName"),
        )
    }

    private fun archUnitWarnings(): List<String> {
        val prefix = ArchUnitLensBundle.message("inspection.problem.message", "")
        return myFixture.doHighlighting().orEmpty().mapNotNull { info ->
            info.description?.takeIf { it.startsWith(prefix) }
        }
    }

    private fun testData(path: String): String = Path
        .of("src/test/testData", path)
        .toFile()
        .readText()
}

private val dependencyProjectDescriptor = object : LightProjectDescriptor() {
    override fun getSdk(): Sdk = JavaSdk.getInstance().createJdk("Dependency inspection JDK", System.getProperty("java.home"), false)
}

package io.github.archunitlens.rules

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class ArchRuleEntryPointOwnershipTest : BasePlatformTestCase() {
    fun testSameClassAndExternalHelpersRemainMetadataOnlyWithoutReadingTheirBodies() {
        myFixture.addArchUnitEntryPointDeclarations()
        val sources = sources("Helpers")
        assertEquals(4, sources.size)
        sources.forEach { source ->
            val calls = RawCallExtractor.callsWithSource(source.initializer)
            assertNotNull(calls.first().second.resolveMethod())
            assertFalse(RawCallExtractor.isArchUnitEntryPoint(calls.first().second))
            assertEquals("classes", calls.first().first.name)
            assertTrue(calls.none { it.first.name == "noClasses" })
            assertMetadataOnly(source)
        }
    }

    fun testQualifiedAndStaticImportFormsResolveToTheRealOwner() {
        myFixture.addArchUnitEntryPointDeclarations()
        qualifiedAndImportedForms.forEach { form ->
            val source = sources(form).single()
            val rootCall = RawCallExtractor.callsWithSource(source.initializer).first().second
            assertTrue(RawCallExtractor.isArchUnitEntryPoint(rootCall))
            assertTrue(ArchRuleParser.discover(source)?.liveRule is ClassConventionRule)
        }
    }

    fun testInheritedArchUnitStaticEntryPointPreservesItsDeclarationOwner() {
        myFixture.addArchUnitEntryPointDeclarations()
        addProjectOwner("RealRoots", "RealRoots")
        val source = sources("InheritedArchUnit").single()
        assertTrue(ArchRuleParser.discover(source)?.liveRule is ClassConventionRule)
    }

    fun testSameNameClassInheritedHelperAndImportedHelperCannotEstablishArchUnitOwnership() {
        myFixture.addArchUnitEntryPointDeclarations()
        addProjectOwner("ArchRuleDefinition", "FakeDefinition")
        addProjectOwner("InheritedRoots", "InheritedRoots")
        addProjectOwner("OtherRoots", "OtherRoots")
        val sources = sources("ProjectOwners")
        assertEquals(3, sources.size)
        sources.forEach(::assertMetadataOnly)
    }

    fun testUnresolvedQualifiedExplicitAndWildcardRootsRemainMetadataOnly() {
        qualifiedAndImportedForms.forEach { form -> assertMetadataOnly(sources(form).single()) }
    }

    fun testAmbiguousExplicitAndWildcardStaticImportsRemainMetadataOnly() {
        myFixture.addArchUnitEntryPointDeclarations()
        addProjectOwner("OtherRoots", "OtherRoots")
        listOf("AmbiguousExplicit", "AmbiguousWildcard").forEach { form ->
            val source = sources(form).single()
            val candidates = RawCallExtractor.callsWithSource(source.initializer).first().second.methodExpression.multiResolve(false)
            assertTrue(candidates.mapNotNull { (it.element as? PsiMethod)?.containingClass?.qualifiedName }.distinct().size > 1)
            assertMetadataOnly(source)
        }
    }

    fun testAllSupportedSubjectFamiliesRequireGenuineEntryPointOwnership() {
        myFixture.addArchUnitEntryPointDeclarations()
        val sources = sources("SubjectHelpers")
        assertEquals(6, sources.size)
        sources.forEach(::assertMetadataOnly)
    }

    private fun sources(fixture: String): List<ArchRuleSource> {
        val file = myFixture.configureByText(
            "ArchitectureRules.java",
            testData("archrules/entryPointOwnership$fixture.java"),
        )
        return ArchRuleSourceFinder.findInFile(file)
    }

    private fun addProjectOwner(className: String, fixture: String) {
        myFixture.addFileToProject(
            "src/test/java/com/example/$className.java",
            testData("javaSources/entryPointOwnership$fixture.java"),
        )
    }

    private fun assertMetadataOnly(source: ArchRuleSource) {
        val discovery = ArchRuleParser.discover(source) ?: error("Expected retained metadata")
        assertNull(discovery.liveRule)
        assertEquals(
            SupportStatus.Unsupported(UnsupportedReason.UnsupportedEntryPoint(RawCallExtractor.from(source.initializer).first().name)),
            discovery.descriptor.supportStatus,
        )
    }

    private fun testData(path: String): String = Path.of("src/test/testData/$path").toFile().readText()

    private val qualifiedAndImportedForms = listOf("Qualified", "FullyQualified", "ExplicitImport", "WildcardImport")
}

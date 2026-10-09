package io.github.archunitlens.inspections

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.SupportStatus
import io.github.archunitlens.rules.addArchUnitEntryPointDeclarations
import java.nio.file.Path

class Issue85UnsupportedPackagePatternInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addArchUnitEntryPointDeclarations()
        myFixture.enableInspections(ArchUnitLensInspection())
    }

    fun testMixedUnsupportedTargetPackageListStaysMetadataOnlyInLookupAndInspection() {
        myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            Path.of("src/test/testData/archrules/issue85MixedPackagePatterns.java").toFile().readText(),
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/infrastructure/OrderRepository.java",
            "package com.example.infrastructure; public class OrderRepository {}",
        )
        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.domain;

                import com.example.infrastructure.OrderRepository;

                class OrderService {
                    private OrderRepository repository;
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        val discoveries = service.discoveriesForPackage("com.example.domain")
        assertEquals(1, discoveries.size)
        assertTrue(discoveries.single().liveRule == null)
        assertTrue(discoveries.single().descriptor.supportStatus is SupportStatus.Unsupported)
        assertTrue(service.rulesForPackage("com.example.domain").isEmpty())

        assertTrue(
            myFixture.doHighlighting().none {
                it.description?.contains("domain_should_not_depend_on_unsupported_package_list") == true
            },
        )
    }
}

package io.github.archunitlens.ui

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.DiscoveredArchRule

class ArchUnitLensRuleOverviewFormatterTest : BasePlatformTestCase() {
    fun testFormatsSupportedAndUnsupportedDiscoveriesWithScanMetrics() {
        myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.AnalyzeClasses;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                @AnalyzeClasses(packages = "com.example")
                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule services_should_end_with_service =
                            classes().that().resideInAPackage("..service..")
                                    .should().haveSimpleNameEndingWith("Service")
                                    .because("Services stay explicit.");

                    @ArchTest
                    static final ArchRule multi_package_dependency_shape =
                            noClasses().that().resideInAPackage("..domain..")
                                    .should().dependOnClassesThat()
                                    .resideInAnyPackage("..adapter..", "..infrastructure..");

                    @ArchTest
                    static final ArchRule custom_proxy_helper_is_unsupported =
                            classes().that().areInterfaces()
                                    .should().notBeMetaAnnotatedWith(proxyAnnotations());
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        val output = ArchUnitLensRuleOverviewFormatter.render(
            discoveries = service.discoveries().toOverviewItems("ArchitectureRules.java"),
            metrics = service.scanMetrics(),
        )

        assertTrue(output.contains(ArchUnitLensBundle.message("overview.title")))
        assertTrue(output.contains(scanLabelPrefix()))
        assertTrue(output.contains("services_should_end_with_service"))
        assertTrue(output.contains(ArchUnitLensBundle.message("overview.source", "ArchitectureRules.java")))
        assertTrue(output.contains(statusLine(ArchUnitLensBundle.message("overview.status.supported"))))
        assertTrue(output.contains(ArchUnitLensBundle.message("overview.reason", "Services stay explicit.")))
        assertTrue(output.contains("multi_package_dependency_shape"))
        assertTrue(output.contains("custom_proxy_helper_is_unsupported"))
        assertTrue(
            output.contains(
                statusLine(
                    ArchUnitLensBundle.message(
                        "overview.status.unsupported",
                        ArchUnitLensBundle.message("overview.unsupported.customOrMetaAnnotationPredicates"),
                    ),
                ),
            ),
        )
        assertTrue(
            output.contains(
                ArchUnitLensBundle.message(
                    "overview.scope",
                    ArchUnitLensBundle.message("overview.scope.packages", "com.example"),
                ),
            ),
        )
    }

    fun testFormatsSupportedMultiPackageClassConvention() {
        myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule services_should_have_suffix =
                            classes().that().resideInAnyPackage("..service..", "..application..")
                                    .should().haveSimpleNameEndingWith("Service");
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        val output = ArchUnitLensRuleOverviewFormatter.render(
            discoveries = service.discoveries().toOverviewItems("ArchitectureRules.java"),
            metrics = service.scanMetrics(),
        )

        assertTrue(output.contains("services_should_have_suffix"))
        assertTrue(output.contains(statusLine(ArchUnitLensBundle.message("overview.status.supported"))))
        assertFalse(output.contains(ArchUnitLensBundle.message("overview.unsupported.multiPackageRuleShape")))
    }

    fun testAppliesOverviewFiltersWithoutChangingDiscoverySource() {
        myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule services_should_end_with_service =
                            classes().that().resideInAPackage("..service..")
                                    .should().haveSimpleNameEndingWith("Service");

                    @ArchTest
                    static final ArchRule custom_proxy_helper_is_unsupported =
                            classes().that().areInterfaces()
                                    .should().notBeMetaAnnotatedWith(proxyAnnotations());
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        val discoveries = service.discoveries()
        val output = ArchUnitLensRuleOverviewFormatter.render(
            discoveries = discoveries.toOverviewItems("ArchitectureRules.java"),
            metrics = service.scanMetrics(),
            filter = RuleOverviewFilter(
                showSupported = false,
                showUnsupported = true,
                searchQuery = "proxy",
                showDiagnostics = true,
            ),
        )

        assertEquals(2, discoveries.size)
        assertFalse(output.contains("services_should_end_with_service"))
        assertTrue(output.contains("custom_proxy_helper_is_unsupported"))
        assertTrue(output.contains(ArchUnitLensBundle.message("overview.diagnostic.unsupported", "custom")))
    }

    private fun statusLine(status: String): String = ArchUnitLensBundle.message("overview.status", status)

    private fun List<DiscoveredArchRule>.toOverviewItems(
        sourceFileName: String?,
    ): List<RuleOverviewItem> = map { RuleOverviewItem(it, sourceFileName) }

    private fun scanLabelPrefix(): String = ArchUnitLensBundle
        .message("overview.scan", "", "", "", "", "", "", "", "", "", "", "")
        .substringBefore(" ")
}

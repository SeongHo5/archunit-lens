package io.github.archunitlens.ui

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.DiscoveredArchRule
import java.nio.file.Path

class ArchUnitLensRuleOverviewFormatterTest : BasePlatformTestCase() {
    fun testFormatsSupportedAndUnsupportedDiscoveriesWithScanMetrics() {
        myFixture.addFileToProject(
            "src/test/java/java/lang/System.java",
            "package java.lang; public final class System { public static Object out; public static Object err; }",
        )
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

                    @ArchTest
                    static final ArchRule no_system_streams = noClasses()
                            .should().accessField(java.lang.System.class, "out")
                            .orShould().accessField(java.lang.System.class, "err");
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
        assertTrue(output.contains("no_system_streams"))
        assertTrue(output.contains("(accessField(java.lang.System.out) OR accessField(java.lang.System.err))"))
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

    fun testFormatsOrderedMethodAndConstructorSignatures() {
        myFixture.addFileToProject(
            "src/test/java/java/lang/String.java",
            "package java.lang; public final class String {}",
        )
        myFixture.addFileToProject(
            "src/test/java/java/lang/Throwable.java",
            "package java.lang; public class Throwable {}",
        )
        myFixture.addFileToProject(
            "src/test/java/java/util/List.java",
            "package java.util; public interface List<E> {}",
        )
        myFixture.addFileToProject(
            "src/test/java/org/springframework/data/domain/PageImpl.java",
            "package org.springframework.data.domain; public class PageImpl<T> { public PageImpl(java.util.List<T> items) {} }",
        )
        myFixture.addFileToProject(
            "src/test/java/org/slf4j/Logger.java",
            "package org.slf4j; public interface Logger { " +
                "void error(java.lang.String message, java.lang.Throwable failure); }",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
                class ArchitectureRules {
                    @ArchTest static final ArchRule exact_signatures = noClasses().should()
                            .callMethod(org.slf4j.Logger.class, "error", java.lang.String.class, java.lang.Throwable.class)
                            .orShould().callConstructor(org.springframework.data.domain.PageImpl.class, java.util.List.class);
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        val output = ArchUnitLensRuleOverviewFormatter.render(
            discoveries = service.discoveries().toOverviewItems("ArchitectureRules.java"),
            metrics = service.scanMetrics(),
        )

        assertTrue(
            output.contains(
                "callMethod(org.slf4j.Logger.error(java.lang.String, java.lang.Throwable))",
            ),
        )
        assertTrue(
            output.contains(
                "callConstructor(org.springframework.data.domain.PageImpl(java.util.List))",
            ),
        )
    }

    fun testFormatsHelperBackedCustomConditionsWithoutTreatingBecauseAsCondition() {
        myFixture.addFileToProject(
            "src/test/java/com/example/HelperBackedCustomConditions.java",
            testData("archrules/helperBackedCustomConditions.java"),
        )

        val service = project.service<ArchRuleProjectService>()
        val output = ArchUnitLensRuleOverviewFormatter.render(
            discoveries = service.discoveries().toOverviewItems("HelperBackedCustomConditions.java"),
            metrics = service.scanMetrics(),
        )
        val unsupportedStatus = statusLine(
            ArchUnitLensBundle.message(
                "overview.status.unsupported",
                ArchUnitLensBundle.message("overview.unsupported.helperBackedCustomCondition"),
            ),
        )

        assertEquals(4, output.lineSequence().count { it.trim() == unsupportedStatus })
        val expected = mapOf(
            "class_helper_condition" to
                Triple(ArchUnitLensBundle.message("overview.subject.classes"), "customClassCondition()", "class invariant"),
            "method_helper_condition" to
                Triple(ArchUnitLensBundle.message("overview.subject.methods"), "customMethodCondition()", "method invariant"),
            "constructor_helper_condition" to Triple(
                ArchUnitLensBundle.message("overview.subject.constructors"),
                "customConstructorCondition()",
                "constructor invariant",
            ),
            "field_helper_condition" to
                Triple(ArchUnitLensBundle.message("overview.subject.fields"), "customFieldCondition()", "field invariant"),
        )
        expected.forEach { (ruleName, metadata) ->
            val ruleOutput = output.substringAfter(ruleName).substringBefore("\n\n")
            assertTrue(ruleOutput.lineSequence().any { it.trim() == unsupportedStatus })
            assertTrue(
                ruleOutput.lineSequence().any {
                    it.trim() == ArchUnitLensBundle.message("overview.subject", metadata.first)
                },
            )
            assertTrue(
                ruleOutput.lineSequence().any {
                    it.trim() == ArchUnitLensBundle.message("overview.condition", metadata.second)
                },
            )
            assertTrue(
                ruleOutput.lineSequence().any {
                    it.trim() == ArchUnitLensBundle.message("overview.reason", metadata.third)
                },
            )
        }
        assertFalse(
            output.lineSequence().any {
                it.trim() == ArchUnitLensBundle.message("overview.condition", "because")
            },
        )
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

    private fun testData(path: String): String = Path.of("src/test/testData", path).toFile().readText()
}

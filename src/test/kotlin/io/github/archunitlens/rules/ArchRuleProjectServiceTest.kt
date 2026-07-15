package io.github.archunitlens.rules

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.settings.ArchUnitLensSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ArchRuleProjectServiceTest : BasePlatformTestCase() {
    fun testDiscoveriesRequireReadAccess() {
        val service = project.service<ArchRuleProjectService>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val failure = executor.submit<Throwable?> {
                runCatching { service.discoveries() }.exceptionOrNull()
            }.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertTrue(failure is AssertionError)
        } finally {
            executor.shutdownNow()
        }
    }

    fun testConcurrentReadActionsPublishCompletePackageLookupSnapshot() {
        addArchitectureRules(
            "ArchitectureRules.java",
            classSuffixRule("Controller"),
        )

        val service = project.service<ArchRuleProjectService>()
        val readersReady = CountDownLatch(2)
        val startReaders = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val snapshots = (1..2).map {
                executor.submit<ArchRuleDiscoverySnapshot> {
                    ApplicationManager.getApplication().runReadAction<ArchRuleDiscoverySnapshot> {
                        readersReady.countDown()
                        check(startReaders.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        service.discoverySnapshot("com.example.controller")
                    }
                }
            }
            assertTrue(readersReady.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            startReaders.countDown()

            snapshots.map { it.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }.forEach { snapshot ->
                assertEquals(listOf("controller_classes_should_end_with_controller"), snapshot.discoveries.map { it.ruleName })
                assertEquals(1, snapshot.scanMetrics.packageLookupCacheEntries)
            }
            val metrics = service.scanMetrics()
            assertEquals(1, metrics.packageLookupCacheEntries)
            assertEquals(2, metrics.packageLookupCacheHits + metrics.packageLookupCacheMisses)
        } finally {
            startReaders.countDown()
            executor.shutdownNow()
        }
    }

    fun testDiscoverySkipsOrdinaryJavaFilesAndReusesUnchangedRuleFileCache() {
        addArchitectureRules(
            "ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule controller_classes_should_end_with_controller =
                            classes()
                                    .that()
                                    .resideInAPackage("..controller..")
                                    .should()
                                    .haveSimpleNameEndingWith("Controller");
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/domain/OrderService.java",
            """
                package com.example.domain;

                class OrderService {
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        assertEquals(1, service.discoveries().size)
        val firstScan = service.scanMetrics()
        assertEquals(1, firstScan.indexedJavaCandidateFiles)
        assertEquals(1, firstScan.archRuleCandidateFiles)
        assertEquals(1, firstScan.parsedRuleCandidateFiles)

        myFixture.addFileToProject(
            "src/test/java/com/example/domain/AnotherService.java",
            """
                package com.example.domain;

                class AnotherService {
                }
            """.trimIndent(),
        )

        assertEquals(1, service.discoveries().size)
        val secondScan = service.scanMetrics()
        assertEquals(1, secondScan.indexedJavaCandidateFiles)
        assertEquals(1, secondScan.archRuleCandidateFiles)
        assertEquals(0, secondScan.parsedRuleCandidateFiles)
    }

    fun testDiscoveryParsesOnlyNewRuleCandidateAfterCacheWarmup() {
        addArchitectureRules(
            "ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule controller_classes_should_end_with_controller =
                            classes().that().resideInAPackage("..controller..")
                                    .should().haveSimpleNameEndingWith("Controller");
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        assertEquals(1, service.discoveries().size)

        addArchitectureRules(
            "MoreArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class MoreArchitectureRules {
                    @ArchTest
                    static final ArchRule services_should_end_with_service =
                            classes().that().resideInAPackage("..service..")
                                    .should().haveSimpleNameEndingWith("Service");
                }
            """.trimIndent(),
        )

        assertEquals(2, service.discoveries().size)
        val secondScan = service.scanMetrics()
        assertEquals(2, secondScan.indexedJavaCandidateFiles)
        assertEquals(2, secondScan.archRuleCandidateFiles)
        assertEquals(1, secondScan.parsedRuleCandidateFiles)
    }

    fun testDiscoveryReparsesEditedRuleCandidateBeforeFileSave() {
        val ruleFile = addArchitectureRules(
            "ArchitectureRules.java",
            classSuffixRule("Controller"),
        )

        val service = project.service<ArchRuleProjectService>()
        val firstRule = service.discoveries().single().liveRule as ClassNameSuffixRule
        assertEquals("Controller", firstRule.requiredSuffix)

        val document = PsiDocumentManager.getInstance(project).getDocument(ruleFile)
            ?: error("Expected document for ArchUnit rule file")
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(classSuffixRule("Handler"))
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        val secondRule = service.discoveries().single().liveRule as ClassNameSuffixRule
        val secondScan = service.scanMetrics()
        assertEquals("Handler", secondRule.requiredSuffix)
        assertEquals(1, secondScan.indexedJavaCandidateFiles)
        assertEquals(1, secondScan.archRuleCandidateFiles)
        assertEquals(1, secondScan.parsedRuleCandidateFiles)
    }

    fun testRulesForPackageFiltersByAnalyzeScopeAndPackagePatternAndCachesLookups() {
        addArchitectureRules(
            "ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.AnalyzeClasses;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                @AnalyzeClasses(packages = "com.example")
                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule controllers_should_end_with_controller =
                            classes().that().resideInAPackage("..controller..")
                                    .should().haveSimpleNameEndingWith("Controller");

                    @ArchTest
                    static final ArchRule domain_should_not_depend_on_infrastructure =
                            noClasses().that().resideInAPackage("..domain..")
                                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        val controllerRules = service.rulesForPackage("com.example.controller")
        assertEquals(listOf("controllers_should_end_with_controller"), controllerRules.map { it.ruleName })
        assertEquals(1, service.scanMetrics().packageLookupCacheMisses)
        assertEquals(1, service.scanMetrics().packageLookupCacheEntries)

        val cachedControllerRules = service.rulesForPackage("com.example.controller")
        assertEquals(controllerRules, cachedControllerRules)
        assertEquals(1, service.scanMetrics().packageLookupCacheHits)

        val domainRules = service.rulesForPackage("com.example.domain")
        assertEquals(listOf("domain_should_not_depend_on_infrastructure"), domainRules.map { it.ruleName })
        assertEquals(2, service.scanMetrics().packageLookupCacheEntries)
        assertEquals(2, service.scanMetrics().packageLookupCacheMisses)

        assertTrue(service.rulesForPackage("com.other.controller").isEmpty())
    }

    fun testDiscoveriesForPackageRetainsUnsupportedMetadataByAnalyzeScope() {
        addArchitectureRules(
            "ArchitectureRules.java",
            """
                import com.tngtech.archunit.junit.AnalyzeClasses;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                @AnalyzeClasses(packages = "com.example")
                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule custom_proxy_helper_is_unsupported =
                            classes().that().areInterfaces()
                                    .should().notBeMetaAnnotatedWith(proxyAnnotations());
                }
            """.trimIndent(),
        )

        val service = project.service<ArchRuleProjectService>()
        val inScopeDiscoveries = service.discoveriesForPackage("com.example.api")
        assertEquals(listOf("custom_proxy_helper_is_unsupported"), inScopeDiscoveries.map { it.ruleName })
        assertTrue(inScopeDiscoveries.single().liveRule == null)

        assertTrue(service.discoveriesForPackage("com.other.api").isEmpty())
    }

    fun testDumbModeUsesCachedDiscoveriesAndMarksStaleFallback() {
        addArchitectureRules(
            "ArchitectureRules.java",
            classSuffixRule("Controller"),
        )

        val service = project.service<ArchRuleProjectService>()
        assertEquals(1, service.discoveries().size)

        addArchitectureRules(
            "MoreArchitectureRules.java",
            classSuffixRule("Service", className = "MoreArchitectureRules"),
        )

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertEquals(1, service.discoveries().size)
            val metrics = service.scanMetrics()
            assertEquals(ArchRuleIndexingStatus.INDEXING, metrics.indexingStatus)
            assertTrue(metrics.staleCacheFallback)
        }

        assertEquals(2, service.discoveries().size)
        assertEquals(ArchRuleIndexingStatus.SMART, service.scanMetrics().indexingStatus)
    }

    fun testCommentedClassLiteralReparsesWhenTargetAppearsAfterCacheWarmup() {
        addArchitectureRules("ArchitectureRules.java", commentedClassLiteralAssignabilityRule())
        val service = project.service<ArchRuleProjectService>()

        assertNull(service.discoveries().single().liveRule)
        myFixture.addFileToProject(
            "src/test/java/com/example/Base.java",
            "package com.example; public class Base {}",
        )

        assertTrue(service.discoveries().single().liveRule is ClassConventionRule)
        assertEquals(1, service.scanMetrics().parsedRuleCandidateFiles)
    }

    fun testSpacedAssignableCallReparsesWhenTargetDisappearsAfterCacheWarmup() {
        val target = myFixture.addFileToProject(
            "src/test/java/com/example/Base.java",
            "package com.example; public class Base {}",
        )
        addArchitectureRules("ArchitectureRules.java", spacedAssignabilityRule())
        val service = project.service<ArchRuleProjectService>()

        assertTrue(service.discoveries().single().liveRule is ClassConventionRule)
        WriteCommandAction.runWriteCommandAction(project) { target.delete() }

        assertNull(service.discoveries().single().liveRule)
        assertEquals(1, service.scanMetrics().parsedRuleCandidateFiles)
    }

    fun testSpacedAssignableCallReparsesWhenProjectRootsChange() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Base.java",
            "package com.example; public class Base {}",
        )
        addArchitectureRules("ArchitectureRules.java", spacedAssignabilityRule())
        val service = project.service<ArchRuleProjectService>()
        assertTrue(service.discoveries().single().liveRule is ClassConventionRule)

        val extraSourceRoot = myFixture.tempDirFixture.findOrCreateDir("alternate-source")
        PsiTestUtil.addSourceRoot(module, extraSourceRoot)
        try {
            assertTrue(service.discoveries().single().liveRule is ClassConventionRule)
            assertEquals(1, service.scanMetrics().parsedRuleCandidateFiles)
        } finally {
            PsiTestUtil.removeSourceRoot(module, extraSourceRoot)
        }
    }

    fun testSettingsExcludeGeneratedRuleSourcesFromDiscovery() {
        val state = service<ArchUnitLensSettings>().state
        val original = state.excludedPathFragments
        try {
            state.excludedPathFragments = "generated-test"
            myFixture.addFileToProject(
                "generated-test/java/com/example/GeneratedArchitectureRules.java",
                classSuffixRule("Controller", className = "GeneratedArchitectureRules"),
            )

            assertTrue(project.service<ArchRuleProjectService>().discoveries().isEmpty())
        } finally {
            state.excludedPathFragments = original
        }
    }

    fun testChangingScanExclusionsInvalidatesDiscoveryCache() {
        val state = service<ArchUnitLensSettings>().state
        val original = state.excludedPathFragments
        try {
            state.excludedPathFragments = ""
            myFixture.addFileToProject(
                "generated-test/java/com/example/GeneratedArchitectureRules.java",
                classSuffixRule("Controller", className = "GeneratedArchitectureRules"),
            )

            val service = project.service<ArchRuleProjectService>()
            assertEquals(1, service.discoveries().size)

            state.excludedPathFragments = "generated-test"

            assertTrue(service.discoveries().isEmpty())
        } finally {
            state.excludedPathFragments = original
        }
    }

    fun testChangingScanExclusionsInvalidatesRulesForPackageCache() {
        val state = service<ArchUnitLensSettings>().state
        val original = state.excludedPathFragments
        try {
            state.excludedPathFragments = ""
            myFixture.addFileToProject(
                "generated-test/java/com/example/GeneratedArchitectureRules.java",
                classSuffixRule("Controller", className = "GeneratedArchitectureRules"),
            )

            val service = project.service<ArchRuleProjectService>()
            assertEquals(1, service.rulesForPackage("com.example.controller").size)
            assertEquals(1, service.scanMetrics().packageLookupCacheEntries)

            state.excludedPathFragments = "generated-test"

            assertTrue(service.rulesForPackage("com.example.controller").isEmpty())
            assertEquals(1, service.scanMetrics().packageLookupCacheMisses)
        } finally {
            state.excludedPathFragments = original
        }
    }

    private fun addArchitectureRules(
        fileName: String,
        code: String,
    ): PsiFile = myFixture.addFileToProject("src/test/java/com/example/$fileName", code)

    private fun classSuffixRule(
        requiredSuffix: String,
        className: String = "ArchitectureRules",
    ): String =
        """
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;
            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            class $className {
                @ArchTest
                static final ArchRule controller_classes_should_end_with_controller =
                        classes()
                                .that()
                                .resideInAPackage("..controller..")
                                .should()
                                .haveSimpleNameEndingWith("$requiredSuffix");
            }
        """.trimIndent()

    private fun commentedClassLiteralAssignabilityRule(): String =
        """
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;
            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            class ArchitectureRules {
                @ArchTest
                static final ArchRule assignable_types = classes().that().areNotEnums()
                        .should().beAssignableTo(com.example.Base /* target */ . /* token */ class);
            }
        """.trimIndent()

    private fun spacedAssignabilityRule(): String =
        """
            import com.tngtech.archunit.junit.ArchTest;
            import com.tngtech.archunit.lang.ArchRule;
            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

            class ArchitectureRules {
                @ArchTest
                static final ArchRule assignable_types = classes().that().areNotEnums()
                        .should().beAssignableTo /* gap */ ( "com.example.Base" );
            }
        """.trimIndent()
}

private const val TEST_TIMEOUT_SECONDS = 10L

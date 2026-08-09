package io.github.archunitlens.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.AnalyzeScope
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.ClassNameSuffixRule
import io.github.archunitlens.rules.ForbiddenAnnotationRule
import io.github.archunitlens.rules.PackageDependencyBanRule
import io.github.archunitlens.rules.evaluator.ExactCodeAccessEvaluator
import io.github.archunitlens.settings.ArchUnitLensSettings
import java.nio.file.Path

class ArchUnitLensInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ArchUnitLensInspection())
    }

    fun testPackageDependencyBanHighlightsForbiddenImport() {
        addArchitectureRulesFixture("packageDependencyBan")

        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.domain.order;

                import com.example.infrastructure.persistence.OrderJpaRepository;
                import java.util.List;

                class OrderService {
                }
            """.trimIndent(),
        )

        val warnings = warningDescriptions()
        assertTrue(warnings.any { it.startsWith(problemMessage("domain_should_not_depend_on_infrastructure")) })
        assertTrue(
            myFixture.getAllQuickFixes().any {
                it.text.contains(goToRuleFixText("domain_should_not_depend_on_infrastructure"))
            },
        )
    }

    fun testSettingsCanDisableDependencyRuleWarnings() {
        addArchitectureRulesFixture("packageDependencyBan")
        val state = service<ArchUnitLensSettings>().state
        val original = state.dependencyRulesEnabled
        try {
            state.dependencyRulesEnabled = false
            myFixture.configureByText(
                "OrderService.java",
                """
                    package com.example.domain.order;

                    import com.example.infrastructure.persistence.OrderJpaRepository;

                    class OrderService {
                    }
                """.trimIndent(),
            )

            assertTrue(warningDescriptions().isEmpty())
        } finally {
            state.dependencyRulesEnabled = original
        }
    }

    fun testClassOnlyRulesDoNotResolveJavaReferences() {
        addControllerSuffixRule()
        val file = myFixture.configureByText(
            "OrderController.java",
            """
                package com.example.presentation.controller;

                import java.util.List;

                class OrderController {
                    private List<String> orders;
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val activeRules = project
            .service<ArchRuleProjectService>()
            .rulesForPackage(file.packageName)
        assertTrue(activeRules.any { it is ClassNameSuffixRule })
        assertFalse(activeRules.any { it is PackageDependencyBanRule })

        val references = PsiTreeUtil.findChildrenOfType(file, PsiJavaCodeReferenceElement::class.java)
        assertTrue(references.isNotEmpty())

        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
        var resolutionCount = 0
        references.forEach { reference ->
            val countingReference = object : PsiJavaCodeReferenceElement by reference {
                override fun resolve(): PsiElement? {
                    resolutionCount += 1
                    return reference.resolve()
                }
            }
            visitor.visitReferenceElement(countingReference)
        }

        assertEquals(0, resolutionCount)
    }

    fun testGoToArchUnitRuleQuickFixNavigatesToRuleFile() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule domain_should_not_depend_on_infrastructure =
                            noClasses().that().resideInAPackage("..domain..")
                                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
                }
            """.trimIndent(),
        )

        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.domain.order;

                import com.example.infrastructure.persistence.OrderJpaRepository;

                class OrderService {
                }
            """.trimIndent(),
        )

        val fix = myFixture.getAllQuickFixes().single {
            it.text.contains(goToRuleFixText("domain_should_not_depend_on_infrastructure"))
        }
        myFixture.launchAction(fix)
        UIUtil.dispatchAllInvocationEvents()

        assertEquals("ArchitectureRules.java", FileEditorManager.getInstance(project).selectedEditor?.file?.name)
    }

    fun testPackageDependencyBanIgnoresSegmentSubstring() {
        addPackageDependencyBanRule()

        myFixture.configureByText(
            "NotDomainService.java",
            """
                package com.example.notdomain.order;

                import com.example.infrastructure.persistence.OrderJpaRepository;

                class NotDomainService {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testPackageDependencyBanIgnoresAllowedImports() {
        addPackageDependencyBanRule()

        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.domain.order;

                import com.example.domain.shared.Money;
                import java.util.List;

                class OrderService {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testPackageDependencyBanIgnoresWildcardImportsForV01() {
        addPackageDependencyBanRule()

        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.domain.order;

                import com.example.infrastructure.persistence.*;

                class OrderService {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testPackageDependencyBanSupportsResideInAnyPackageSourceAndTarget() {
        addArchitectureRulesFixture("resideInAnyPackageDependencyBan")

        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.application.order;

                import com.example.adapter.http.OrderController;

                class OrderService {
                }
            """.trimIndent(),
        )

        val warnings = warningDescriptions()
        assertTrue(warnings.any { it.startsWith(problemMessage("application_should_not_depend_on_adapters")) })
        assertTrue(warnings.any { it.contains("com.example.adapter.http.OrderController") })
        assertTrue(warnings.any { it.contains("..adapter..") })
    }

    fun testPackageDependencyBanHighlightsResolvedReferenceKindsWithoutImports() {
        addArchitectureRulesFixture("resideInAnyPackageDependencyBan")
        addDependencyReferenceStubs()

        configureJavaFixture("OrderService.java", "javaSources/dependencyReferences/OrderService.java")

        val warnings = warningDescriptions()
        assertTrue(warnings.any { it.contains("com.example.infrastructure.persistence.BaseRepository") })
        assertTrue(warnings.any { it.contains("com.example.adapter.ExternalPort") })
        assertTrue(warnings.any { it.contains("com.example.infrastructure.persistence.OrderJpaRepository") })
        assertTrue(warnings.any { it.contains("com.example.infrastructure.persistence.OrderDto") })
        assertTrue(warnings.any { it.contains("com.example.adapter.ExternalRequest") })
    }

    fun testPackageDependencyBanDeduplicatesExplicitImportAndResolvedReference() {
        addPackageDependencyBanRule()
        addDependencyReferenceStubs()

        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.domain.order;

                import com.example.infrastructure.persistence.OrderJpaRepository;

                class OrderService {
                    private OrderJpaRepository repository;
                }
            """.trimIndent(),
        )

        val warnings = warningDescriptions()
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("import"))
        assertTrue(warnings.single().contains("com.example.infrastructure.persistence.OrderJpaRepository"))
    }

    fun testPackageDependencyBanIgnoresUnresolvedReferences() {
        addPackageDependencyBanRule()

        myFixture.configureByText(
            "OrderService.java",
            """
                package com.example.domain.order;

                class OrderService {
                    private MissingInfrastructureType repository;
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testClassNameSuffixHighlightsMissingSuffix() {
        addControllerSuffixRule()

        myFixture.configureByText(
            "UserApi.java",
            """
                package com.example.presentation.controller;

                class UserApi {
                }
            """.trimIndent(),
        )

        val warnings = warningDescriptions()
        assertTrue(warnings.contains(problemMessage("controller_classes_should_end_with_controller")))
        assertTrue(myFixture.getAllQuickFixes().any { it.text.contains(appendControllerSuffixFixText()) })
        assertCorrectiveFixAndNavigationAvailable(
            appendControllerSuffixFixText(),
            goToRuleFixText("controller_classes_should_end_with_controller"),
        )
    }

    fun testClassNameSuffixIgnoresCompliantAndOutsidePackageClasses() {
        addControllerSuffixRule()

        myFixture.configureByText(
            "UserController.java",
            """
                package com.example.presentation.controller;

                class UserController {
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().isEmpty())

        myFixture.configureByText(
            "UserApi.java",
            """
                package com.example.presentation.api;

                class UserApi {
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testClassNameSuffixQuickFixAppendsRequiredSuffix() {
        addControllerSuffixRule()

        myFixture.configureByText(
            "UserApi.java",
            """
                package com.example.presentation.controller;

                class UserApi {
                }
            """.trimIndent(),
        )

        val fix = myFixture.getAllQuickFixes().single { it.text.contains(appendControllerSuffixFixText()) }
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
                package com.example.presentation.controller;

                class UserApiController {
                }
            """.trimIndent(),
        )
    }

    fun testForbiddenAnnotationHighlightsAndOffersRemoval() {
        addForbiddenServiceRule()
        addSpringServiceAnnotationStub()

        myFixture.configureByText(
            "OrderPolicy.java",
            """
                package com.example.domain.order;

                import org.springframework.stereotype.Service;

                @Service
                class OrderPolicy {
                }
            """.trimIndent(),
        )

        val warnings = warningDescriptions()
        assertTrue(warnings.contains(problemMessage("domain_should_not_be_service")))
        assertTrue(myFixture.getAllQuickFixes().any { it.text.contains(removeAnnotationFixText("Service")) })
        assertCorrectiveFixAndNavigationAvailable(
            removeAnnotationFixText("Service"),
            goToRuleFixText("domain_should_not_be_service"),
        )
    }

    fun testCorrectiveQuickFixesPrecedeLowPriorityRuleNavigation() {
        val file = myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            """
                package com.example;

                class ArchitectureRules {
                }
            """.trimIndent(),
        )
        val sourcePointer = SmartPointerManager.createPointer<PsiElement>(file)
        val suffixRule = ClassNameSuffixRule(
            ruleName = "sample_rule",
            sourcePackagePattern = "..controller..",
            requiredSuffix = "Controller",
            sourcePointer = sourcePointer,
            analyzeScope = AnalyzeScope.All,
        )
        val annotationRule = ForbiddenAnnotationRule(
            ruleName = "sample_rule",
            sourcePackagePattern = "..domain..",
            forbiddenAnnotationQualifiedName = "org.springframework.stereotype.Service",
            sourcePointer = sourcePointer,
            analyzeScope = AnalyzeScope.All,
        )

        val suffixFixes = ArchUnitViolation.MissingClassNameSuffix(suffixRule, "Controller").quickFixes()
        assertTrue(suffixFixes.first().name.contains(appendControllerSuffixFixText()))
        assertTrue(suffixFixes.last() is LowPriorityAction)
        assertTrue(suffixFixes.last().name.contains(goToRuleFixText("sample_rule")))

        val annotationFixes = ArchUnitViolation.ForbiddenAnnotation(annotationRule, "Service").quickFixes()
        assertTrue(annotationFixes.first().name.contains(removeAnnotationFixText("Service")))
        assertTrue(annotationFixes.last() is LowPriorityAction)
        assertTrue(annotationFixes.last().name.contains(goToRuleFixText("sample_rule")))
    }

    fun testForbiddenAnnotationQuickFixRemovesOnlyForbiddenAnnotation() {
        addForbiddenServiceRule()
        addSpringServiceAnnotationStub()

        myFixture.configureByText(
            "OrderPolicy.java",
            """
                package com.example.domain.order;

                import org.springframework.stereotype.Service;

                @Deprecated
                @Service
                class OrderPolicy {
                }
            """.trimIndent(),
        )

        val fix = myFixture.getAllQuickFixes().single { it.text.contains(removeAnnotationFixText("Service")) }
        myFixture.launchAction(fix)

        myFixture.checkResult(
            """
                package com.example.domain.order;

                import org.springframework.stereotype.Service;

                @Deprecated
                class OrderPolicy {
                }
            """.trimIndent(),
        )
    }

    fun testForbiddenAnnotationIgnoresOutsidePackage() {
        addForbiddenServiceRule()
        addSpringServiceAnnotationStub()

        myFixture.configureByText(
            "InfrastructureService.java",
            """
                package com.example.infrastructure;

                import org.springframework.stereotype.Service;

                @Service
                class InfrastructureService {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testForbiddenAnnotationIgnoresDifferentAnnotationInMatchingPackage() {
        addForbiddenServiceRule()
        addSpringServiceAnnotationStub()
        myFixture.addFileToProject(
            "src/test/java/org/springframework/stereotype/Component.java",
            """
                package org.springframework.stereotype;

                public @interface Component {
                }
            """.trimIndent(),
        )

        myFixture.configureByText(
            "OrderPolicy.java",
            """
                package com.example.domain.order;

                import org.springframework.stereotype.Component;

                @Component
                class OrderPolicy {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testForbiddenAnnotationIgnoresMethodAndFieldAnnotations() {
        addForbiddenServiceRule()
        addSpringServiceAnnotationStub()

        myFixture.configureByText(
            "OrderPolicy.java",
            """
                package com.example.domain.order;

                import org.springframework.stereotype.Service;

                class OrderPolicy {
                    @Service
                    private String cachedPolicy;

                    @Service
                    void apply() {
                    }
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testAnalyzeClassesScopePreventsWarningOutsideScope() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.AnalyzeClasses;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import org.springframework.stereotype.Service;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                @AnalyzeClasses(packages = "com.example.domain")
                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule domain_should_not_be_service =
                            noClasses().that().resideInAPackage("..other..")
                                    .should().beAnnotatedWith(Service.class);
                }
            """.trimIndent(),
        )
        addSpringServiceAnnotationStub()

        myFixture.configureByText(
            "OtherPolicy.java",
            """
                package com.example.other;

                import org.springframework.stereotype.Service;

                @Service
                class OtherPolicy {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testAnnotationExclusivityHighlightsForbiddenAnnotationWithBecauseReason() {
        addArchitectureRulesFixture("annotationExclusivityBecause")
        addMapperAnnotationStubs()

        myFixture.configureByText(
            "IndoorMapper.java",
            """
                package io.indoorplus.persistence;

                import io.indoorplus.SecondaryMapper;
                import org.apache.ibatis.annotations.Mapper;

                @Mapper
                @SecondaryMapper
                interface IndoorMapper {
                }
            """.trimIndent(),
        )

        val warnings = warningDescriptions()
        assertTrue(
            warnings.any {
                it.contains("mapper_annotation_must_be_exclusive") &&
                    it.contains("Primary and secondary mapper annotations must be exclusive.")
            },
        )
        assertTrue(
            myFixture.getAllQuickFixes().first().text.contains(removeAnnotationFixText("SecondaryMapper")),
        )
        assertTrue(
            myFixture.getAllQuickFixes().any {
                it.text.contains(goToRuleFixText("mapper_annotation_must_be_exclusive"))
            },
        )
    }

    fun testAnnotationExclusivityQuickFixRemovesForbiddenAnnotation() {
        addMapperExclusivityRule()
        addMapperAnnotationStubs()

        myFixture.configureByText(
            "IndoorMapper.java",
            """
                package io.indoorplus.persistence;

                import io.indoorplus.SecondaryMapper;
                import org.apache.ibatis.annotations.Mapper;

                @Mapper
                @SecondaryMapper
                interface IndoorMapper {
                }
            """.trimIndent(),
        )

        val fix = myFixture.getAllQuickFixes().single {
            it.text.contains(removeAnnotationFixText("SecondaryMapper"))
        }
        myFixture.checkPreviewAndLaunchAction(fix)

        myFixture.checkResult(
            """
                package io.indoorplus.persistence;

                import io.indoorplus.SecondaryMapper;
                import org.apache.ibatis.annotations.Mapper;

                @Mapper
                interface IndoorMapper {
                }
            """.trimIndent(),
        )
    }

    fun testAnnotationExclusivityIgnoresClassOutsideAnalyzeScope() {
        addMapperExclusivityRule()
        addMapperAnnotationStubs()

        myFixture.configureByText(
            "OutdoorMapper.java",
            """
                package com.example.persistence;

                import io.indoorplus.SecondaryMapper;
                import org.apache.ibatis.annotations.Mapper;

                @Mapper
                @SecondaryMapper
                interface OutdoorMapper {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testQueryMapperRuleHighlightsClassesAndNonAssignableInterfaces() {
        addArchitectureRulesFixture("queryMapperInterface")
        addQueryMapperStub()

        myFixture.configureByText(
            "OrderQueryMapper.java",
            """
                package com.example.persistence;

                class OrderQueryMapper {
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().contains(problemMessage("query_mappers_should_be_interfaces")))

        myFixture.configureByText(
            "UserQueryMapper.java",
            """
                package com.example.persistence;

                interface UserQueryMapper {
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().contains(problemMessage("query_mappers_should_be_interfaces")))
    }

    fun testQueryMapperRuleAcceptsAssignableInterfaceAndScope() {
        addArchitectureRulesFixture("queryMapperInterface")
        addQueryMapperStub()

        myFixture.configureByText(
            "OrderQueryMapper.java",
            """
                package com.example.persistence;

                import com.example.QueryMapper;

                interface OrderQueryMapper extends QueryMapper {
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().isEmpty())

        myFixture.configureByText(
            "ExternalQueryMapper.java",
            """
                package com.other.persistence;

                class ExternalQueryMapper {
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testClassMetaAnnotationRuleMatchesDirectAndRecursiveAnnotations() {
        addArchitectureRulesFixture("literalClassMetaAnnotation")
        addProxyAnnotationStubs()

        val cases = listOf(
            "com.example.Proxy" to true,
            "com.example.Transactional" to true,
            "com.example.ComposedTransactional" to true,
            "com.example.DeepComposedTransactional" to true,
            "com.example.CyclicProxyA" to true,
            "com.example.CyclicUnrelatedA" to false,
            "com.example.Unrelated" to false,
            "com.example.missing.Unresolved" to false,
        )
        cases.forEach { (annotation, expectedWarning) ->
            myFixture.configureByText(
                "RemoteGateway.java",
                """
                    package com.example.api;

                    @$annotation
                    interface RemoteGateway {
                    }
                """.trimIndent(),
            )
            assertEquals(
                annotation,
                if (expectedWarning) listOf(problemMessage("proxy_annotations_belong_on_concrete_classes")) else emptyList(),
                warningDescriptions(),
            )
        }
    }

    fun testClassMetaAnnotationRuleHighlightsOnlyInterfaces() {
        addArchitectureRulesFixture("literalClassMetaAnnotation")
        addProxyAnnotationStubs()
        myFixture.configureByText(
            "RemoteGatewayImpl.java",
            """
                package com.example.api;

                import com.example.Transactional;

                @Transactional
                class RemoteGatewayImpl {
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testMethodMetaAnnotationRuleMatchesDirectAndRecursiveAnnotations() {
        addArchitectureRulesFixture("literalMethodMetaAnnotation")
        addProxyAnnotationStubs()

        val cases = listOf(
            "com.example.Proxy" to true,
            "com.example.Transactional" to true,
            "com.example.ComposedTransactional" to true,
            "com.example.DeepComposedTransactional" to true,
            "com.example.CyclicProxyA" to true,
            "com.example.CyclicUnrelatedA" to false,
            "com.example.Unrelated" to false,
            "com.example.missing.Unresolved" to false,
        )
        cases.forEach { (annotation, expectedWarning) ->
            myFixture.configureByText(
                "RemoteGateway.java",
                """
                    package com.example.api;

                    interface RemoteGateway {
                        @$annotation
                        void execute();
                    }
                """.trimIndent(),
            )
            assertEquals(
                annotation,
                if (expectedWarning) listOf(problemMessage("interface_methods_must_not_have_proxy_annotations")) else emptyList(),
                warningDescriptions(),
            )
        }
    }

    fun testMethodMetaAnnotationRuleHighlightsOnlyInterfaceMethods() {
        addArchitectureRulesFixture("literalMethodMetaAnnotation")
        addProxyAnnotationStubs()
        myFixture.configureByText(
            "RemoteGatewayImpl.java",
            """
                package com.example.api;

                import com.example.Transactional;

                class RemoteGatewayImpl {
                    @Transactional
                    void execute() {
                    }
                }
            """.trimIndent(),
        )
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testCustomMetaAnnotationHelperRemainsUnsupportedWithoutWarning() {
        addArchitectureRulesFixture("unsupportedCustomPredicate")
        addProxyAnnotationStubs()

        myFixture.configureByText(
            "RemoteGateway.java",
            """
                package com.example.api;

                import com.example.Transactional;

                @Transactional
                interface RemoteGateway {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testHelperBackedCustomConditionRemainsMetadataOnlyWithoutWarning() {
        addArchitectureRulesFixture("helperBackedCustomConditions")

        myFixture.configureByText(
            "BrokenMapper.java",
            """
                package com.example.mapper;

                class BrokenMapper {
                    Object state;

                    BrokenMapper() {
                    }

                    void map() {
                    }
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testClassConventionReportsIndependentAndShouldViolations() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Mapper.java",
            "package com.example; public @interface Mapper {}",
        )
        addArchitectureRulesFixture("classConventionMapper")
        configureJavaFixture("BrokenAdapter.java", "javaSources/classConventions/BrokenAdapter.java")

        val highlights = warningHighlights()
        val warnings = highlights.mapNotNull { it.description }
        assertEquals(3, warnings.size)
        assertTrue(highlights.all { myFixture.file.text.substring(it.startOffset, it.endOffset) == "BrokenAdapter" })
        assertTrue(warnings[0].contains(ArchUnitLensBundle.message("inspection.problem.class.mustBeInterface")))
        assertTrue(warnings[1].contains(ArchUnitLensBundle.message("inspection.problem.class.missingSuffix", "Mapper")))
        assertTrue(warnings[2].contains(ArchUnitLensBundle.message("inspection.problem.class.missingAnnotation", "com.example.Mapper")))
    }

    fun testClassConventionRequiresEveryLeafFamilySetting() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Mapper.java",
            "package com.example; public @interface Mapper {}",
        )
        addArchitectureRulesFixture("classConventionMapper")
        configureJavaFixture("BrokenAdapter.java", "javaSources/classConventions/BrokenAdapter.java")
        val state = service<ArchUnitLensSettings>().state
        val originalNaming = state.classNamingRulesEnabled
        val originalAnnotations = state.annotationRulesEnabled
        val originalInterfaces = state.interfaceRulesEnabled
        try {
            state.classNamingRulesEnabled = true
            state.annotationRulesEnabled = true
            state.interfaceRulesEnabled = true
            assertEquals(3, warningDescriptions().size)
            listOf<(Boolean) -> Unit>(
                { state.classNamingRulesEnabled = it },
                { state.annotationRulesEnabled = it },
                { state.interfaceRulesEnabled = it },
            ).forEach { setEnabled ->
                setEnabled(false)
                configureJavaFixture("BrokenAdapter.java", "javaSources/classConventions/BrokenAdapter.java")
                assertTrue(warningDescriptions().isEmpty())
                setEnabled(true)
                configureJavaFixture("BrokenAdapter.java", "javaSources/classConventions/BrokenAdapter.java")
            }
        } finally {
            state.classNamingRulesEnabled = originalNaming
            state.annotationRulesEnabled = originalAnnotations
            state.interfaceRulesEnabled = originalInterfaces
        }
    }

    fun testEveryClassConditionReportsOnlyViolatingDeclarationRange() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Required.java",
            "package com.example; public @interface Required {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Forbidden.java",
            "package com.example; public @interface Forbidden {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Base.java",
            "package com.example; public class Base {}",
        )
        addArchitectureRulesFixture("classConventionConditionMatrix")

        assertSingleClassWarning(
            """
                package com.example.case1;
                @com.example.Required class GoodCase1 {}
                class BadCase1 {}
            """.trimIndent(),
            "BadCase1",
            ArchUnitLensBundle.message("inspection.problem.class.missingAnnotation", "com.example.Required"),
        )
        assertSingleClassWarning(
            """
                package com.example.case2;
                class GoodCase2 {}
                @com.example.Forbidden class BadCase2 {}
            """.trimIndent(),
            "BadCase2",
            ArchUnitLensBundle.message("inspection.problem.class.forbiddenAnnotation", "com.example.Forbidden"),
        )
        assertNoClassWarning("package com.example.case3.required; class GoodCase3 {}")
        assertSingleClassWarning(
            "package com.example.case3; class BadCase3 {}",
            "BadCase3",
            ArchUnitLensBundle.message("inspection.problem.class.outsidePackages", "..required.."),
        )
        assertNoClassWarning("package com.example.case4.api; class GoodCase4 {}")
        assertSingleClassWarning(
            "package com.example.case4; class BadCase4 {}",
            "BadCase4",
            ArchUnitLensBundle.message("inspection.problem.class.outsidePackages", "..required.., ..api.."),
        )
        assertSingleClassWarning(
            "package com.example.case5; class GoodService {} class BadCase5 {}",
            "BadCase5",
            ArchUnitLensBundle.message("inspection.problem.class.missingSuffix", "Service"),
        )
        assertSingleClassWarning(
            "package com.example.case6; class GoodCase6 {} class BadCase6Impl {}",
            "BadCase6Impl",
            ArchUnitLensBundle.message("inspection.problem.class.forbiddenSuffix", "Impl"),
        )
        assertSingleClassWarning(
            "package com.example.case7; interface GoodCase7 {} class BadCase7 {}",
            "BadCase7",
            ArchUnitLensBundle.message("inspection.problem.class.mustBeInterface"),
        )
        assertSingleClassWarning(
            "package com.example.case8; class GoodCase8 {} interface BadCase8 {}",
            "BadCase8",
            ArchUnitLensBundle.message("inspection.problem.class.mustNotBeInterface"),
        )
        assertSingleClassWarning(
            "package com.example.case9; enum GoodCase9 { VALUE } class BadCase9 {}",
            "BadCase9",
            ArchUnitLensBundle.message("inspection.problem.class.mustBeEnum"),
        )
        assertSingleClassWarning(
            "package com.example.case10; class GoodCase10 {} enum BadCase10 { VALUE }",
            "BadCase10",
            ArchUnitLensBundle.message("inspection.problem.class.mustNotBeEnum"),
        )
        assertSingleClassWarning(
            """
                package com.example.case11;
                class GoodCase11 extends com.example.Base {}
                class BadCase11 {}
            """.trimIndent(),
            "BadCase11",
            ArchUnitLensBundle.message("inspection.problem.class.assignableTo", "com.example.Base"),
        )
    }

    fun testSpringMapStructAndMyBatisClassConventions() {
        addArchitectureRulesFixture("classConventionExamples")

        configureJavaFixture("BrokenEndpoint.java", "javaSources/classConventions/BrokenEndpoint.java")
        val springWarnings = warningDescriptions()
        assertEquals(2, springWarnings.size)
        assertTrue(springWarnings.any { it.contains(ArchUnitLensBundle.message("inspection.problem.class.missingSuffix", "Controller")) })
        assertTrue(
            springWarnings.any {
                it.contains(
                    ArchUnitLensBundle.message(
                        "inspection.problem.class.missingAnnotation",
                        "org.springframework.stereotype.Controller",
                    ),
                )
            },
        )

        configureJavaFixture("OrderConverter.java", "javaSources/classConventions/OrderConverter.java")
        val mapStructWarnings = warningDescriptions()
        assertEquals(2, mapStructWarnings.size)
        assertTrue(mapStructWarnings.any { it.contains(ArchUnitLensBundle.message("inspection.problem.class.mustBeInterface")) })
        assertTrue(
            mapStructWarnings.any {
                it.contains(ArchUnitLensBundle.message("inspection.problem.class.missingAnnotation", "org.mapstruct.Mapper"))
            },
        )

        configureJavaFixture("BrokenRepository.java", "javaSources/classConventions/BrokenRepository.java")
        val myBatisWarnings = warningDescriptions()
        assertEquals(3, myBatisWarnings.size)
        assertTrue(myBatisWarnings.any { it.contains(ArchUnitLensBundle.message("inspection.problem.class.mustBeInterface")) })
        assertTrue(myBatisWarnings.any { it.contains(ArchUnitLensBundle.message("inspection.problem.class.missingSuffix", "Mapper")) })
        assertTrue(
            myBatisWarnings.any {
                it.contains(
                    ArchUnitLensBundle.message(
                        "inspection.problem.class.missingAnnotation",
                        "org.apache.ibatis.annotations.Mapper",
                    ),
                )
            },
        )
    }

    fun testClassConventionPreservesAnalyzeScopeAndBecause() {
        addArchitectureRulesFixture("classConventionScopeBecause")

        configureJavaFixture("OrderServiceImpl.java", "javaSources/classConventions/OrderServiceImpl.java")
        val warning = warningDescriptions().single()
        assertTrue(warning.contains(ArchUnitLensBundle.message("inspection.problem.class.forbiddenSuffix", "Impl")))
        assertTrue(warning.contains(ArchUnitLensBundle.message("inspection.problem.reason", "Implementations stay behind ports.")))

        myFixture.configureByText(
            "OutsideServiceImpl.java",
            "package com.example.outside; class OutsideServiceImpl {}",
        )
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testUnsupportedClassConditionSiblingProducesNoWarning() {
        addArchitectureRulesFixture("classConventionUnsupportedSibling")
        configureJavaFixture("BrokenAdapter.java", "javaSources/classConventions/BrokenAdapter.java")

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testUnresolvedAssignableClassConditionProducesNoWarning() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule unresolved = classes().that().areNotEnums()
                            .should().beAssignableTo("com.example.Missing");
                }
            """.trimIndent(),
        )
        myFixture.configureByText("Candidate.java", "package com.example; class Candidate {}")

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testUnresolvedClassLiteralsInExactHandlersProduceNoWarning() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Custom.java",
            """
                package com.example;

                @com.example.missing.Proxy
                public @interface Custom {}
            """.trimIndent(),
        )
        addArchitectureRulesFixture("exactUnresolvedClassLiterals")
        myFixture.configureByText(
            "BrokenMapper.java",
            """
                package com.example.domain;

                @com.example.missing.Required
                @com.example.missing.Forbidden
                class BrokenMapper {}

                @com.example.Custom
                interface BrokenPort {
                    @com.example.Custom
                    void call();
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testClassConventionPredicateExclusionProducesNoWarning() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest static final ArchRule service_enums = classes().that()
                            .resideInAPackage("..service..")
                            .should().beEnums();
                }
            """.trimIndent(),
        )
        myFixture.configureByText("Outside.java", "package com.example.web; class Outside {}")

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testDynamicClassPredicateArgumentProducesNoWarning() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    static String dynamicPackage = "..service..";
                    @ArchTest static final ArchRule dynamic_predicate = classes().that()
                            .resideInAnyPackage("..api..", dynamicPackage)
                            .should().beEnums();
                }
            """.trimIndent(),
        )
        myFixture.configureByText("Candidate.java", "package com.example.api; class Candidate {}")

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testUnsupportedClassPackagePatternsProduceNoWarning() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest static final ArchRule invalid_middle_predicate = classes().that()
                            .resideInAPackage("com..service").should().beEnums();
                    @ArchTest static final ArchRule invalid_star_condition = classes().that().areNotEnums()
                            .should().resideInAPackage("com.*.service");
                    @ArchTest static final ArchRule invalid_middle_condition = classes().that().areNotEnums()
                            .should().resideInAPackage("com..service");
                    @ArchTest static final ArchRule invalid_any_condition = classes().that().areNotEnums()
                            .should().resideInAnyPackage("com.*.service", "..allowed..");
                }
            """.trimIndent(),
        )

        myFixture.configureByText("Candidate.java", "package com.service; class Candidate {}")
        assertTrue(warningDescriptions().isEmpty())
        myFixture.configureByText("Outside.java", "package com.other; class Outside {}")
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testMalformedClassPredicateGrammarProducesNoWarning() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest static final ArchRule consecutive = classes().that()
                            .resideInAPackage("..service..").haveSimpleNameNotEndingWith("Never")
                            .should().beEnums();
                    @ArchTest static final ArchRule dangling_that = classes().that().should().beEnums();
                    @ArchTest static final ArchRule dangling_and = classes().that()
                            .resideInAPackage("..service..").and().should().beEnums();
                    @ArchTest static final ArchRule dangling_or = classes().that()
                            .resideInAPackage("..service..").or().should().beEnums();
                }
            """.trimIndent(),
        )
        myFixture.configureByText("Candidate.java", "package com.example.service; class Candidate {}")

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testSelectorlessClassPredicateProducesNoWarning() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest static final ArchRule selectorless = classes()
                            .areNotEnums().should().beEnums();
                }
            """.trimIndent(),
        )
        myFixture.configureByText("Candidate.java", "package com.example; class Candidate {}")

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testExactCodeAccessRulesHighlightMemberNamesWithExactMessages() {
        addCodeAccessJdkStubs()
        addArchitectureRulesFixture("exactCodeAccess")
        myFixture.configureByText(
            "LegacyPrinter.java",
            """
                package com.example;

                class LegacyPrinter {
                    void print(java.lang.Throwable failure) {
                        java.lang.System.out.println("failed");
                        java.lang.System.err.println("failed");
                        failure.printStackTrace();
                    }
                }
            """.trimIndent(),
        )

        val warnings = warningHighlights()
        assertEquals(warnings.mapNotNull { it.description }.toString(), 3, warnings.size)
        assertEquals(listOf("out", "err", "printStackTrace"), warnings.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
        assertEquals(
            listOf(
                ArchUnitLensBundle.message("inspection.problem.message", "no_system_streams") + "\n" +
                    ArchUnitLensBundle.message("inspection.problem.fieldAccess", "java.lang.System", "out"),
                ArchUnitLensBundle.message("inspection.problem.message", "no_system_streams") + "\n" +
                    ArchUnitLensBundle.message("inspection.problem.fieldAccess", "java.lang.System", "err"),
                ArchUnitLensBundle.message("inspection.problem.message", "no_print_stack_trace") + "\n" +
                    ArchUnitLensBundle.message(
                        "inspection.problem.methodCall",
                        "java.lang.Throwable",
                        "printStackTrace",
                        "",
                    ),
            ),
            warnings.map { it.description },
        )
    }

    fun testExactCodeAccessUsesResolvedOwnerSignatureAndStaticImportUse() {
        addCodeAccessJdkStubs()
        addArchitectureRulesFixture("exactCodeAccess")
        myFixture.addFileToProject(
            "src/main/java/com/example/Other.java",
            "package com.example; class Other { static java.io.PrintStream out; }",
        )
        myFixture.configureByText(
            "ResolvedAccesses.java",
            """
                package com.example;

                import static java.lang.System.out;

                class ResolvedAccesses {
                    void inspect(java.lang.Throwable exact, java.lang.Exception inherited, CustomFailure overridden) {
                        out.println("exact static import use");
                        Other.out.println("different owner");
                        exact.printStackTrace();
                        inherited.printStackTrace();
                        overridden.printStackTrace();
                        overridden.printStackTrace("overload");
                    }
                }

                class CustomFailure extends java.lang.Exception {
                    @Override public void printStackTrace() {}
                    void printStackTrace(java.lang.String message) {}
                }
            """.trimIndent(),
        )

        val resolvedTargets = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiMethodCallExpression::class.java)
            .filter { it.methodExpression.referenceName == "printStackTrace" }
            .map(ExactCodeAccessEvaluator::resolveMethodCall)
        assertEquals(
            listOf(
                "java.lang.Throwable.printStackTrace/[]",
                "java.lang.Exception.printStackTrace/[]",
                "com.example.CustomFailure.printStackTrace/[]",
                "com.example.CustomFailure.printStackTrace/[java.lang.String]",
            ),
            resolvedTargets.map { target ->
                "${target?.ownerQualifiedName}.${target?.methodName}/${target?.parameterTypeQualifiedNames}"
            },
        )
        val warnings = warningHighlights()
        assertEquals(warnings.mapNotNull { it.description }.toString(), 2, warnings.size)
        assertEquals(listOf("out", "printStackTrace"), warnings.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    fun testExactCodeAccessIncludesAccessesInsideTargetClassLambdas() {
        addCodeAccessJdkStubs()
        addArchitectureRulesFixture("exactCodeAccess")
        myFixture.configureByText(
            "LambdaAccess.java",
            """
                package com.example;

                class LambdaAccess {
                    Runnable printer(java.lang.Throwable failure) {
                        return () -> failure.printStackTrace();
                    }
                }
            """.trimIndent(),
        )

        val warnings = warningHighlights()
        assertEquals(warnings.mapNotNull { it.description }.toString(), 1, warnings.size)
        assertEquals("printStackTrace", myFixture.file.text.substring(warnings.single().startOffset, warnings.single().endOffset))
    }

    fun testExactCodeAccessFailsClosedForUnresolvedAndDumbMode() {
        addCodeAccessJdkStubs()
        addArchitectureRulesFixture("exactCodeAccess")
        myFixture.configureByText(
            "UnresolvedAccess.java",
            "package com.example; class UnresolvedAccess { void run() { Missing.out.println(); missing.printStackTrace(); } }",
        )
        assertTrue(warningHighlights().isEmpty())

        myFixture.configureByText(
            "DumbAccess.java",
            "package com.example; class DumbAccess { void run(java.lang.Throwable failure) { java.lang.System.out.println(); failure.printStackTrace(); } }",
        )
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val file = myFixture.file as PsiJavaFile
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
            PsiTreeUtil.findChildrenOfType(file, PsiReferenceExpression::class.java).forEach(visitor::visitReferenceExpression)
            PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java).forEach(visitor::visitMethodCallExpression)
            assertTrue(holder.results.isEmpty())
        }
    }

    fun testCodeAccessNamePrefilterResolvesEachCandidateAtMostOnce() {
        addCodeAccessJdkStubs()
        addArchitectureRulesFixture("exactCodeAccess")
        val file = myFixture.configureByText(
            "ResolveCounts.java",
            """
                package com.example;
                class ResolveCounts {
                    void run(java.lang.Throwable failure) {
                        java.lang.System.out.println("candidate");
                        failure.printStackTrace();
                        failure.getMessage();
                        java.lang.System.lineSeparator();
                    }
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
        val activeRules = project.service<ArchRuleProjectService>().rulesForPackage(file.packageName)
        assertTrue(activeRules.toString(), activeRules.any { it is io.github.archunitlens.rules.NoClassesCodeAccessRule })
        var fieldResolves = 0
        var methodResolves = 0

        PsiTreeUtil.findChildrenOfType(file, PsiReferenceExpression::class.java).forEach { reference ->
            val counting = object : PsiReferenceExpression by reference {
                override fun resolve(): PsiElement? {
                    fieldResolves++
                    return reference.resolve()
                }
            }
            visitor.visitReferenceExpression(counting)
        }
        PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java).forEach { call ->
            val counting = object : PsiMethodCallExpression by call {
                override fun resolveMethod(): PsiMethod? {
                    methodResolves++
                    return call.resolveMethod()
                }
            }
            visitor.visitMethodCallExpression(counting)
        }

        val referenceNames = PsiTreeUtil.findChildrenOfType(file, PsiReferenceExpression::class.java).map { it.referenceName }
        val methodNames = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java).map { it.methodExpression.referenceName }
        assertEquals(referenceNames.toString(), 1, fieldResolves)
        assertEquals(methodNames.toString(), 1, methodResolves)
    }

    fun testCodeAccessPrefilterRepresentativeJavaBodyTiming() {
        addCodeAccessJdkStubs()
        addArchitectureRulesFixture("exactCodeAccess")
        val unrelatedCalls = (1..1_000).joinToString("\n") { "helper();" }
        val file = myFixture.configureByText(
            "RepresentativeBody.java",
            """
                package com.example;
                class RepresentativeBody {
                    void run(java.lang.Throwable failure) {
                        java.lang.System.out.println();
                        failure.printStackTrace();
                        $unrelatedCalls
                    }
                    void helper() {}
                }
            """.trimIndent(),
        ) as PsiJavaFile
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
        val references = PsiTreeUtil.findChildrenOfType(file, PsiReferenceExpression::class.java)
        val calls = PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression::class.java)
        var fieldResolves = 0
        var methodResolves = 0
        val countingReferences = references.map { reference ->
            object : PsiReferenceExpression by reference {
                override fun resolve(): PsiElement? {
                    fieldResolves++
                    return reference.resolve()
                }
            }
        }
        val countingCalls = calls.map { call ->
            object : PsiMethodCallExpression by call {
                override fun resolveMethod(): PsiMethod? {
                    methodResolves++
                    return call.resolveMethod()
                }
            }
        }
        val pass = {
            countingReferences.forEach(visitor::visitReferenceExpression)
            countingCalls.forEach(visitor::visitMethodCallExpression)
        }

        repeat(3) { pass() }
        val durations = List(10) { kotlin.system.measureNanoTime(pass) }
        val medianNanos = durations.sorted()[durations.size / 2]

        assertEquals(13, fieldResolves)
        assertEquals(13, methodResolves)
        println(
            "CODE_ACCESS_TIMING references=${references.size} calls=${calls.size} " +
                "candidateResolves=2 medianNanos=$medianNanos",
        )
    }

    private fun addPackageDependencyBanRule() {
        addArchitectureRulesFixture("packageDependencyBan")
    }

    private fun addControllerSuffixRule() {
        addArchitectureRules(
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
    }

    private fun addForbiddenServiceRule() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import org.springframework.stereotype.Service;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule domain_should_not_be_service =
                            noClasses().that().resideInAPackage("..domain..")
                                    .should().beAnnotatedWith(Service.class);
                }
            """.trimIndent(),
        )
    }

    private fun addMapperExclusivityRule() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.AnalyzeClasses;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                @AnalyzeClasses(packages = "io.indoorplus")
                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule mapper_annotation_must_be_exclusive =
                            classes().that().areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                                    .should().notBeAnnotatedWith("io.indoorplus.SecondaryMapper");
                }
            """.trimIndent(),
        )
    }

    private fun addArchitectureRules(code: String) {
        myFixture.addFileToProject("src/test/java/com/example/ArchitectureRules.java", code)
    }

    private fun addCodeAccessJdkStubs() {
        myFixture.addFileToProject(
            "src/test/java/java/lang/String.java",
            "package java.lang; public final class String {}",
        )
        myFixture.addFileToProject(
            "src/test/java/java/io/PrintStream.java",
            "package java.io; public class PrintStream { public void println() {} public void println(String value) {} }",
        )
        myFixture.addFileToProject(
            "src/test/java/java/lang/Throwable.java",
            "package java.lang; public class Throwable { public void printStackTrace() {} }",
        )
        myFixture.addFileToProject(
            "src/test/java/java/lang/Exception.java",
            "package java.lang; public class Exception extends java.lang.Throwable {}",
        )
        myFixture.addFileToProject(
            "src/test/java/java/lang/System.java",
            "package java.lang; public final class System { " +
                "public static java.io.PrintStream out; public static java.io.PrintStream err; " +
                "public static String lineSeparator() { return \"\"; } }",
        )
    }

    private fun addArchitectureRulesFixture(name: String) {
        addArchitectureRules(testData("archrules/$name.java"))
    }

    private fun configureJavaFixture(fileName: String, path: String) {
        myFixture.configureByText(fileName, testData(path))
    }

    private fun testData(path: String): String = Path
        .of("src/test/testData", path)
        .toFile()
        .readText()

    private fun addSpringServiceAnnotationStub() {
        myFixture.addFileToProject(
            "src/test/java/org/springframework/stereotype/Service.java",
            """
                package org.springframework.stereotype;

                public @interface Service {
                }
            """.trimIndent(),
        )
    }

    private fun addMapperAnnotationStubs() {
        myFixture.addFileToProject(
            "src/test/java/org/apache/ibatis/annotations/Mapper.java",
            """
                package org.apache.ibatis.annotations;

                public @interface Mapper {
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/test/java/io/indoorplus/SecondaryMapper.java",
            """
                package io.indoorplus;

                public @interface SecondaryMapper {
                }
            """.trimIndent(),
        )
    }

    private fun addQueryMapperStub() {
        myFixture.addFileToProject(
            "src/test/java/com/example/QueryMapper.java",
            """
                package com.example;

                public interface QueryMapper {
                }
            """.trimIndent(),
        )
    }

    private fun addProxyAnnotationStubs() {
        addAnnotationStub("Proxy")
        addAnnotationStub("Transactional", "@com.example.Proxy")
        addAnnotationStub("ComposedTransactional", "@com.example.Transactional")
        addAnnotationStub("DeepComposedTransactional", "@com.example.ComposedTransactional")
        addAnnotationStub("CyclicProxyA", "@com.example.CyclicProxyB")
        addAnnotationStub("CyclicProxyB", "@com.example.CyclicProxyA\n@com.example.Proxy")
        addAnnotationStub("CyclicUnrelatedA", "@com.example.CyclicUnrelatedB")
        addAnnotationStub("CyclicUnrelatedB", "@com.example.CyclicUnrelatedA")
        addAnnotationStub("Unrelated")
    }

    private fun addAnnotationStub(
        simpleName: String,
        annotations: String = "",
    ) {
        myFixture.addFileToProject(
            "src/test/java/com/example/$simpleName.java",
            """
                package com.example;

                $annotations
                public @interface $simpleName {
                }
            """.trimIndent(),
        )
    }

    private fun addDependencyReferenceStubs() {
        myFixture.addFileToProject(
            "src/test/java/com/example/infrastructure/persistence/BaseRepository.java",
            """
                package com.example.infrastructure.persistence;

                public class BaseRepository {
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/infrastructure/persistence/OrderJpaRepository.java",
            """
                package com.example.infrastructure.persistence;

                public class OrderJpaRepository {
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/infrastructure/persistence/OrderDto.java",
            """
                package com.example.infrastructure.persistence;

                public class OrderDto {
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/adapter/ExternalPort.java",
            """
                package com.example.adapter;

                public interface ExternalPort {
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/adapter/ExternalRequest.java",
            """
                package com.example.adapter;

                public class ExternalRequest {
                }
            """.trimIndent(),
        )
    }

    private fun warningHighlights(): List<HighlightInfo> = myFixture.doHighlighting()
        .filter { it.description?.startsWith(problemMessage("")) == true }

    private fun warningDescriptions(): List<String> = warningHighlights().mapNotNull { it.description }

    private fun assertSingleClassWarning(
        code: String,
        expectedIdentifier: String,
        expectedDetail: String,
    ) {
        myFixture.configureByText("$expectedIdentifier.java", code)
        val warnings = warningHighlights()
        assertEquals(warnings.mapNotNull { it.description }.toString(), 1, warnings.size)
        val warning = warnings.single()
        assertTrue(warning.description.orEmpty().contains(expectedDetail))
        assertEquals(expectedIdentifier, myFixture.file.text.substring(warning.startOffset, warning.endOffset))
    }

    private fun assertNoClassWarning(code: String) {
        myFixture.configureByText("Compliant.java", code)
        assertTrue(warningHighlights().isEmpty())
    }

    private fun problemMessage(ruleName: String): String = ArchUnitLensBundle.message("inspection.problem.message", ruleName)

    private fun goToRuleFixText(ruleName: String): String = ArchUnitLensBundle.message("quickfix.goto.name", ruleName)

    private fun appendControllerSuffixFixText(): String = ArchUnitLensBundle.message("quickfix.appendSuffix.name", "Controller")

    private fun removeAnnotationFixText(annotationName: String): String = ArchUnitLensBundle.message(
        "quickfix.removeAnnotation.name",
        annotationName,
    )

    private fun assertCorrectiveFixAndNavigationAvailable(correctiveText: String, navigationText: String) {
        val fixes = myFixture.getAllQuickFixes()
        assertTrue(fixes.any { it.text.contains(correctiveText) })
        assertTrue(fixes.any { it.text.contains(navigationText) })
    }
}

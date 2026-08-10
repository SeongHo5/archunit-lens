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
import io.github.archunitlens.rules.MemberConventionRule
import io.github.archunitlens.rules.PackageDependencyBanRule
import io.github.archunitlens.rules.SupportStatus
import io.github.archunitlens.rules.UnsupportedReason
import io.github.archunitlens.rules.evaluator.MemberSubjectEvaluator
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

    fun testLiteralMethodMetaAnnotationRuleKeepsAnnotationRangeAndRemovalQuickFix() {
        addArchitectureRulesFixture("literalMethodMetaAnnotation")
        addProxyAnnotationStubs()
        myFixture.configureByText(
            "RemoteGateway.java",
            """
                package com.example.api;

                interface RemoteGateway {
                    @com.example.Proxy
                    void execute();
                }
            """.trimIndent(),
        )

        val warning = warningHighlights().single()
        assertEquals("@com.example.Proxy", myFixture.file.text.substring(warning.startOffset, warning.endOffset))
        val fixes = myFixture.getAllQuickFixes()
        assertTrue(
            fixes.map { it.text }.toString(),
            fixes.any { it.text.contains(removeAnnotationFixText("Proxy")) },
        )
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

    fun testDeferredCodeAccessRulesProduceNoWarning() {
        addArchitectureRulesFixture("deferredCodeAccess")
        myFixture.configureByText(
            "LegacyPrinter.java",
            """
                package com.example;

                class LegacyPrinter {
                    void print(Throwable failure) {
                        System.out.println(failure.getMessage());
                        failure.printStackTrace();
                    }
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testClassFactsHighlightRecordsAndResolvedMetaModifiersWithBoundedPackageList() {
        myFixture.addFileToProject(
            "src/test/java/com/tngtech/archunit/core/domain/JavaModifier.java",
            "package com.tngtech.archunit.core.domain; public enum JavaModifier { FINAL }",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Transactional.java",
            "package com.example; public @interface Transactional {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/ComposedTransactional.java",
            "package com.example; @com.example.Transactional public @interface ComposedTransactional {}",
        )
        addArchitectureRules(
            """
                import com.tngtech.archunit.core.domain.JavaModifier;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                class ArchitectureRules {
                    private static final String[] RECORD_PACKAGES = {"..util.."};
                    private static final String[] META_PACKAGES = {"..util.."};
                    @ArchTest static final ArchRule record_types = classes().that()
                            .resideInAnyPackage(RECORD_PACKAGES)
                            .and().areMetaAnnotatedWith("com.example.Transactional")
                            .should().beRecords().andShould().haveModifier(JavaModifier.FINAL);
                    @ArchTest static final ArchRule meta_records_are_not_final = classes().that()
                            .resideInAnyPackage(META_PACKAGES)
                            .and().areMetaAnnotatedWith(com.example.Transactional.class)
                            .should().notHaveModifier(JavaModifier.FINAL);
                }
            """.trimIndent(),
        )
        assertEquals(2, project.service<ArchRuleProjectService>().discoveries().size)
        assertTrue(project.service<ArchRuleProjectService>().discoveries().all { it.liveRule != null })

        myFixture.configureByText(
            "BrokenUtility.java",
            "package com.example.util; @com.example.ComposedTransactional class BrokenUtility {}",
        )
        val brokenWarnings = warningDescriptions()
        assertEquals(2, brokenWarnings.size)
        assertTrue(brokenWarnings.any { it.contains(ArchUnitLensBundle.message("inspection.problem.class.mustBeRecord")) })
        assertTrue(
            brokenWarnings.any {
                it.contains(ArchUnitLensBundle.message("inspection.problem.class.missingModifier", "FINAL"))
            },
        )

        myFixture.configureByText(
            "UtilityRecord.java",
            "package com.example.util; @com.example.ComposedTransactional record UtilityRecord() {}",
        )
        val recordWarnings = warningDescriptions()
        assertEquals(1, recordWarnings.size)
        assertTrue(
            recordWarnings.single().contains(ArchUnitLensBundle.message("inspection.problem.class.forbiddenModifier", "FINAL")),
        )
    }

    fun testUnsafeStaticClassFactsProduceNoWarnings() {
        myFixture.addFileToProject(
            "src/test/java/com/example/JavaModifier.java",
            "package com.example; public enum JavaModifier { FINAL }",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Packages.java",
            "package com.example; public class Packages { public static final String[] VALUES = {\"..util..\"}; }",
        )
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                class ArchitectureRules {
                    private static final String[] MUTABLE = {"..util.."};
                    private static final String[] PACKAGES = {"..util.."};
                    static { MUTABLE[0] = "..changed.."; }
                    @ArchTest static final ArchRule mutable_array = classes().that()
                            .resideInAnyPackage(MUTABLE).should().beRecords();
                    @ArchTest static final ArchRule cross_file_array = classes().that()
                            .resideInAnyPackage(com.example.Packages.VALUES).should().beRecords();
                    @ArchTest static final ArchRule foreign_modifier = classes()
                            .should().haveModifier(com.example.JavaModifier.FINAL);
                    @ArchTest static final ArchRule unresolved_meta = classes()
                            .should().beMetaAnnotatedWith("com.example.missing.Transactional");
                    @ArchTest static final ArchRule helper_array = classes().that()
                            .resideInAnyPackage(packages()).should().beRecords();
                    @ArchTest static final ArchRule helper_escape = classes().that()
                            .resideInAnyPackage(PACKAGES).should().beRecords();
                    private static String[] packages() { return new String[] {"..util.."}; }
                    private static void resideInAnyPackage(String[] values) {}
                    static { resideInAnyPackage(PACKAGES); }
                }
            """.trimIndent(),
        )

        myFixture.configureByText("Candidate.java", "package com.example.util; public final class Candidate {}")

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testStaticClassFactsDoNotWarnDuringDumbModeWhileExistingRulesStillDo() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                class ArchitectureRules {
                    @ArchTest static final ArchRule existing_interface = classes().should().beInterfaces();
                    @ArchTest static final ArchRule new_record = classes().should().beRecords();
                }
            """.trimIndent(),
        )
        val candidateFile = myFixture.configureByText(
            "Candidate.java",
            "package com.example; class Candidate {}",
        ) as PsiJavaFile
        val service = project.service<ArchRuleProjectService>()
        assertEquals(2, service.rulesForPackage(candidateFile.packageName).size)

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val holder = ProblemsHolder(InspectionManager.getInstance(project), candidateFile, false)
            val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
            visitor.visitClass(candidateFile.classes.single())

            val problems = holder.results
            assertEquals(1, problems.size)
            assertTrue(problems.single().descriptionTemplate.contains(ArchUnitLensBundle.message("inspection.problem.class.mustBeInterface")))
        }
    }

    fun testMethodAndConstructorDeclarationConventionsHighlightExplicitAndImplicitUtilityDeclarations() {
        addMemberConventionStubs()
        addArchitectureRulesFixture("methodConstructorConventions")
        myFixture.configureByText(
            "UtilityClasses.java",
            """
                package com.example.util;

                class PrivateUtility {
                    private PrivateUtility() {}
                    static void good() {}
                }

                class ExplicitUtility {
                    ExplicitUtility() {}
                    void bad() {}
                }

                class ImplicitUtility {
                    static void good() {}
                }

                class Container {
                    private Container() {}

                    private static class HiddenUtility {
                        static void good() {}
                    }
                }
            """.trimIndent(),
        )

        val warnings = warningHighlights()
        assertEquals(3, warnings.size)
        assertEquals(
            listOf("ExplicitUtility", "bad", "ImplicitUtility"),
            warnings.map { myFixture.file.text.substring(it.startOffset, it.endOffset) },
        )
        assertTrue(warnings[0].description.orEmpty().contains(ArchUnitLensBundle.message("inspection.problem.member.mustBePrivate")))
        assertTrue(warnings[1].description.orEmpty().contains(ArchUnitLensBundle.message("inspection.problem.member.mustBeStatic")))
        assertTrue(warnings[2].description.orEmpty().contains(ArchUnitLensBundle.message("inspection.problem.member.mustBePrivate")))
    }

    fun testControllerMethodConventionUsesDirectAndTransitiveMetaAnnotationsAndRawReturnTypes() {
        addMemberConventionStubs()
        addArchitectureRulesFixture("methodConstructorConventions")
        myFixture.configureByText(
            "OrderController.java",
            """
                package com.example.api;

                @com.example.RestController
                class OrderController {
                    @com.example.RequestMapping com.example.ResponseEntity<String> direct() { return null; }
                    @com.example.GetMapping com.example.ResponseEntity<String> composed() { return null; }
                    @com.example.GetMapping com.example.WrongResponse mismatch() { return null; }
                    @com.example.GetMapping void noContent() {}
                    @com.example.GetMapping int status() { return 200; }
                    @com.example.GetMapping String[] array() { return null; }
                    @com.example.GetMapping com.other.ResponseEntity<String> sameSimpleName() { return null; }
                }
            """.trimIndent(),
        )

        val warnings = warningHighlights()
        assertEquals(5, warnings.size)
        assertEquals(
            listOf("mismatch", "noContent", "status", "array", "sameSimpleName"),
            warnings.map { myFixture.file.text.substring(it.startOffset, it.endOffset) },
        )
        assertTrue(
            warnings.all {
                it.description.orEmpty().contains(
                    ArchUnitLensBundle.message("inspection.problem.member.rawReturnType", "com.example.ResponseEntity"),
                )
            },
        )
    }

    fun testMemberConventionSkipsUnresolvedRuleTargetsAndCandidateAnnotationOrReturnTypes() {
        addMemberConventionStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

                class ArchitectureRules {
                    @ArchTest static final ArchRule unresolved_target = methods().that()
                            .areMetaAnnotatedWith(com.example.RequestMapping.class)
                            .should().haveRawReturnType(com.example.MissingResponse.class);
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UnresolvedController.java",
            """
                package com.example.api;

                @com.example.RestController
                class UnresolvedController {
                    @com.example.missing.RequestMapping com.example.missing.ResponseEntity unresolved() { return null; }
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testUnresolvedCandidateMetaAnnotationMakesOrPredicateFailClosed() {
        addMemberConventionStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import com.example.RequestMapping;
                import com.example.RestController;
                import com.example.ResponseEntity;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

                class ArchitectureRules {
                    @ArchTest static final ArchRule unresolved_candidate = methods().that()
                            .areMetaAnnotatedWith(RequestMapping.class)
                            .or().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                            .should().haveRawReturnType(ResponseEntity.class);
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "UnresolvedController.java",
            """
                package com.example.api;

                @com.example.RestController
                class UnresolvedController {
                    @com.example.missing.RequestMapping String unresolved() { return ""; }
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testMemberConventionDoesNotRegisterWarningsDuringDumbMode() {
        addMemberConventionStubs()
        addArchitectureRulesFixture("methodConstructorConventions")
        addControllerSuffixRule("SuffixRules.java")
        myFixture.configureByText(
            "Utility.java",
            "package com.example.util.controller; class Utility { void notStatic() {} }",
        )
        assertEquals(3, warningDescriptions().size)

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val file = myFixture.file as PsiJavaFile
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
            val utilityClass = file.classes.single()
            visitor.visitClass(utilityClass)
            utilityClass.methods.forEach(visitor::visitMethod)

            assertEquals(1, holder.results.size)
            assertTrue(holder.results.single().descriptionTemplate.contains("controller_classes_should_end_with_controller"))
        }
    }

    fun testUnsupportedMemberConventionSiblingProducesNoWarning() {
        addMemberConventionStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

                class ArchitectureRules {
                    static String dynamicPackage = "..util..";
                    @ArchTest static final ArchRule dynamic = methods().that()
                            .areDeclaredInClassesThat().resideInAPackage(dynamicPackage)
                            .should().beStatic();
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Utility.java",
            "package com.example.util; class Utility { void notStatic() {} }",
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testNegativeFieldAndMethodRulesReportOnlySelectedForbiddenDeclarations() {
        addNegativeMemberStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
                class ArchitectureRules {
                    @ArchTest static final ArchRule no_value_fields = noFields().should()
                            .beAnnotatedWith(com.example.Value.class);
                    @ArchTest static final ArchRule no_setters = noMethods().that()
                            .areDeclaredInClassesThat().implement(com.example.QueryModel.class)
                            .should().haveNameMatching("^set[A-Z].*");
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Query.java",
            """
                package com.example;
                class Query implements com.example.QueryModel {
                    @com.example.Value String secret;
                    @com.other.Value String sameName;
                    String plain;
                    public void setName() {}
                    public void getName() {}
                }
                class NotSelected { public void setIgnored() {} }
            """.trimIndent(),
        )

        assertEquals(
            2,
            project.service<ArchRuleProjectService>().rulesForPackage("com.example").filterIsInstance<io.github.archunitlens.rules.MemberConventionRule>().size,
        )
        val candidate = (myFixture.file as PsiJavaFile).classes.first()
        val rules = project.service<ArchRuleProjectService>().rulesForPackage("com.example")
            .filterIsInstance<io.github.archunitlens.rules.MemberConventionRule>()
        assertTrue(rules.any { MemberSubjectEvaluator.matches(it, candidate.fields.first(), "com.example") })
        assertTrue(
            "rules=$rules class=${candidate.qualifiedName} implements=${candidate.implementsListTypes.toList()} method=${candidate.methods.first().name}",
            rules.any { MemberSubjectEvaluator.matches(it, candidate.methods.first(), "com.example") },
        )
        assertEquals(listOf("secret", "setName"), warningHighlights().map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    fun testNegativeRootNegatesEntireConditionTreeExactlyOnce() {
        addNegativeMemberStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
                class ArchitectureRules {
                    @ArchTest static final ArchRule no_unannotated = noFields().should()
                            .notBeAnnotatedWith(com.example.Value.class);
                    @ArchTest static final ArchRule no_public_or_static = noMethods().should()
                            .bePublic().orShould().beStatic();
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Candidate.java",
            """
                package com.example;
                class Candidate {
                    @com.example.Value String annotated;
                    String plain;
                    public void exposed() {}
                    private static void utility() {}
                    private void safe() {}
                }
            """.trimIndent(),
        )

        assertEquals(
            listOf("plain", "exposed", "utility"),
            warningHighlights().map { myFixture.file.text.substring(it.startOffset, it.endOffset) },
        )
    }

    fun testConstructorEntryPointArgumentsProduceNoWarningsWhileOrdinaryConstructorRuleStillApplies() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

                class ArchitectureRules {
                    static String dynamicPackage = "..util..";
                    static String dynamicPackage() { return dynamicPackage; }

                    @ArchTest static final ArchRule ordinary = constructors().that()
                            .areDeclaredInClassesThat().resideInAPackage("..util..").should().bePrivate();
                    @ArchTest static final ArchRule dynamic = constructors(dynamicPackage).that()
                            .areDeclaredInClassesThat().resideInAPackage("..util..").should().bePrivate();
                    @ArchTest static final ArchRule helper = constructors(dynamicPackage()).that()
                            .areDeclaredInClassesThat().resideInAPackage("..util..").should().bePrivate();
                    @ArchTest static final ArchRule literal = constructors("..util..").that()
                            .areDeclaredInClassesThat().resideInAPackage("..util..").should().bePrivate();
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Utility.java",
            "package com.example.util; class Utility { Utility() {} }",
        )

        assertEquals(
            listOf("Utility"),
            warningHighlights().map { myFixture.file.text.substring(it.startOffset, it.endOffset) },
        )
    }

    fun testNegativeMemberRuleWithUnresolvedTargetIsMetadataOnlyAndProducesNoWarning() {
        addNegativeMemberStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
                class ArchitectureRules {
                    @ArchTest static final ArchRule unresolved = noFields().should()
                            .beAnnotatedWith(com.example.Missing.class);
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Candidate.java",
            "package com.example; class Candidate { @com.example.Value String field; }",
        )

        val discovery = project.service<ArchRuleProjectService>().discoveries().single { it.ruleName == "unresolved" }
        assertNull(discovery.liveRule)
        assertTrue((discovery.descriptor.supportStatus as SupportStatus.Unsupported).reason is UnsupportedReason.UnresolvedSymbol)
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testHelperBackedNegativeMemberRuleIsMetadataOnlyAsWholeRuleAndProducesNoWarning() {
        addNegativeMemberStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
                class ArchitectureRules {
                    @ArchTest static final ArchRule helper_backed = noFields().should(customCondition());
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Candidate.java",
            "package com.example; class Candidate { @com.example.Value String field; }",
        )

        val discovery = project.service<ArchRuleProjectService>().discoveries().single { it.ruleName == "helper_backed" }
        assertNull(discovery.liveRule)
        assertEquals(
            UnsupportedReason.HelperBackedCustomCondition,
            (discovery.descriptor.supportStatus as SupportStatus.Unsupported).reason,
        )
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testNegativeMemberRuleWithEmptySelectionProducesNoGlobalWarning() {
        addNegativeMemberStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
                class ArchitectureRules {
                    @ArchTest static final ArchRule no_setters = noMethods().that()
                            .areDeclaredInClassesThat().implement(com.example.QueryModel.class)
                            .should().haveNameMatching("^set[A-Z].*");
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "NotSelected.java",
            "package com.example; class NotSelected { public void setIgnored() {} }",
        )

        assertTrue(project.service<ArchRuleProjectService>().rulesForPackage("com.example").any { it is MemberConventionRule })
        assertTrue(warningDescriptions().isEmpty())
    }

    fun testCachedNegativeMemberRuleProducesNoWarningDuringDumbMode() {
        addNegativeMemberStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
                class ArchitectureRules {
                    @ArchTest static final ArchRule no_value_fields = noFields().should()
                            .beAnnotatedWith(com.example.Value.class);
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Candidate.java",
            "package com.example; class Candidate { @com.example.Value String field; }",
        )
        assertEquals(1, warningDescriptions().size)

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val file = myFixture.file as PsiJavaFile
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            val visitor = ArchUnitLensInspection().buildVisitor(holder, false) as JavaElementVisitor
            visitor.visitField(file.classes.single().fields.single())

            assertTrue(holder.results.isEmpty())
        }
    }

    fun testNegativeBeanFieldInjectionRuleSupportsDeclaringAndMemberMetaAnnotations() {
        addNegativeMemberStubs()
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
                class ArchitectureRules {
                    @ArchTest static final ArchRule no_field_injection = noFields().that()
                            .areDeclaredInClassesThat().areMetaAnnotatedWith(com.example.Component.class)
                            .should().beMetaAnnotatedWith(com.example.Autowired.class);
                }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Bean.java",
            """
                package com.example;
                @com.example.Service
                class Bean { @com.example.Inject Object dependency; }
            """.trimIndent(),
        )

        assertEquals(listOf("dependency"), warningHighlights().map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    private fun addPackageDependencyBanRule() {
        addArchitectureRulesFixture("packageDependencyBan")
    }

    private fun addControllerSuffixRule(fileName: String = "ArchitectureRules.java") {
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
            fileName,
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

    private fun addArchitectureRules(
        code: String,
        fileName: String = "ArchitectureRules.java",
    ) {
        myFixture.addFileToProject("src/test/java/com/example/$fileName", code)
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

    private fun addMemberConventionStubs() {
        addAnnotationStub("RequestMapping")
        addAnnotationStub("GetMapping", "@com.example.RequestMapping")
        addAnnotationStub("RestController")
        myFixture.addFileToProject(
            "src/test/java/com/example/ResponseEntity.java",
            "package com.example; public class ResponseEntity<T> {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/WrongResponse.java",
            "package com.example; public class WrongResponse {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/other/ResponseEntity.java",
            "package com.other; public class ResponseEntity<T> {}",
        )
    }

    private fun addNegativeMemberStubs() {
        addAnnotationStub("Value")
        addAnnotationStub("Component")
        addAnnotationStub("Service", "@com.example.Component")
        addAnnotationStub("Autowired")
        addAnnotationStub("Inject", "@com.example.Autowired")
        myFixture.addFileToProject(
            "src/test/java/com/example/QueryModel.java",
            "package com.example; public interface QueryModel {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/other/Value.java",
            "package com.other; public @interface Value {}",
        )
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

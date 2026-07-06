package io.github.archunitlens.inspections

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.AnalyzeScope
import io.github.archunitlens.rules.ClassNameSuffixRule
import io.github.archunitlens.rules.ForbiddenAnnotationRule
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
        myFixture.launchAction(fix)

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

    fun testClassMetaAnnotationRuleHighlightsOnlyInterfaces() {
        addArchitectureRulesFixture("literalClassMetaAnnotation")
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
        assertTrue(warningDescriptions().contains(problemMessage("proxy_annotations_belong_on_concrete_classes")))

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

    fun testMethodMetaAnnotationRuleHighlightsOnlyInterfaceMethods() {
        addArchitectureRulesFixture("literalMethodMetaAnnotation")
        addProxyAnnotationStubs()

        myFixture.configureByText(
            "RemoteGateway.java",
            """
                package com.example.api;

                import com.example.Transactional;

                interface RemoteGateway {
                    @Transactional
                    void execute();
                }
            """.trimIndent(),
        )
        assertTrue(
            warningDescriptions().contains(
                problemMessage("interface_methods_must_not_have_proxy_annotations"),
            ),
        )

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
        myFixture.addFileToProject(
            "src/test/java/com/example/Proxy.java",
            """
                package com.example;

                public @interface Proxy {
                }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Transactional.java",
            """
                package com.example;

                @com.example.Proxy
                public @interface Transactional {
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

    private fun warningDescriptions(): List<String> = myFixture.doHighlighting()
        .mapNotNull { it.description }
        .filter { it.startsWith(problemMessage("")) }

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

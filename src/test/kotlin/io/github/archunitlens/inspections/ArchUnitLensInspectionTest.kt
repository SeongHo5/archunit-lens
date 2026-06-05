package io.github.archunitlens.inspections

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

class ArchUnitLensInspectionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ArchUnitLensInspection())
    }

    fun testPackageDependencyBanHighlightsForbiddenImport() {
        addArchitectureRules(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule domain_should_not_depend_on_infrastructure =
                            noClasses()
                                    .that()
                                    .resideInAPackage("..domain..")
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAPackage("..infrastructure..");
                }
            """.trimIndent(),
        )

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
        assertTrue(warnings.contains("ArchUnit rule violation: domain_should_not_depend_on_infrastructure"))
        assertTrue(myFixture.getAllQuickFixes().any { it.text.contains("Go to ArchUnit rule") })
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

        val fix = myFixture.getAllQuickFixes().single { it.text.contains("Go to ArchUnit rule") }
        myFixture.launchAction(fix)
        UIUtil.dispatchAllInvocationEvents()

        assertEquals("ArchitectureRules.java", FileEditorManager.getInstance(project).selectedEditor?.file?.name)
    }

    fun testPackageDependencyBanIgnoresSegmentSubstring() {
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

                import com.example.domain.shared.Money;
                import java.util.List;

                class OrderService {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testPackageDependencyBanIgnoresWildcardImportsForV01() {
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

                import com.example.infrastructure.persistence.*;

                class OrderService {
                }
            """.trimIndent(),
        )

        assertTrue(warningDescriptions().isEmpty())
    }

    fun testClassNameSuffixHighlightsMissingSuffix() {
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

        myFixture.configureByText(
            "UserApi.java",
            """
                package com.example.presentation.controller;

                class UserApi {
                }
            """.trimIndent(),
        )

        val warnings = warningDescriptions()
        assertTrue(warnings.contains("ArchUnit rule violation: controller_classes_should_end_with_controller"))
        assertTrue(myFixture.getAllQuickFixes().any { it.text.contains("Rename class to end with") })
    }

    fun testClassNameSuffixIgnoresCompliantAndOutsidePackageClasses() {
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

        myFixture.configureByText(
            "UserApi.java",
            """
                package com.example.presentation.controller;

                class UserApi {
                }
            """.trimIndent(),
        )

        val fix = myFixture.getAllQuickFixes().single { it.text.contains("Rename class to end with") }
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
        assertTrue(warnings.contains("ArchUnit rule violation: domain_should_not_be_service"))
        assertTrue(myFixture.getAllQuickFixes().any { it.text.contains("Remove annotation forbidden by ArchUnit rule") })
    }

    fun testForbiddenAnnotationQuickFixRemovesOnlyForbiddenAnnotation() {
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

        val fix = myFixture.getAllQuickFixes().single { it.text.contains("Remove annotation forbidden by ArchUnit rule") }
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
                            classes()
                                    .that()
                                    .areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                                    .should()
                                    .notBeAnnotatedWith("io.indoorplus.SecondaryMapper")
                                    .because("Primary and secondary mapper annotations must be exclusive.");
                }
            """.trimIndent(),
        )
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
            myFixture.getAllQuickFixes().first().text.contains("Remove annotation forbidden by ArchUnit rule"),
        )
        assertTrue(myFixture.getAllQuickFixes().any { it.text.contains("Go to ArchUnit rule") })
    }

    fun testAnnotationExclusivityQuickFixRemovesForbiddenAnnotation() {
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
            it.text.contains("Remove annotation forbidden by ArchUnit rule")
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

    private fun addArchitectureRules(code: String) {
        myFixture.addFileToProject("src/test/java/com/example/ArchitectureRules.java", code)
    }

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

    private fun warningDescriptions(): List<String> = myFixture.doHighlighting()
        .mapNotNull { it.description }
        .filter { it.startsWith("ArchUnit rule violation:") }
}

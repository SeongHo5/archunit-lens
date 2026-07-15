package io.github.archunitlens.rules

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class ArchRuleParserTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "src/test/java/org/springframework/stereotype/Service.java",
            "package org.springframework.stereotype; public @interface Service {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Service.java",
            "package com.example; public @interface Service {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/QueryMapper.java",
            "package com.example; public interface QueryMapper {}",
        )
    }

    fun testParsesPackageDependencyBanRule() {
        val rule = parseSingleRule(
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

        assertTrue(rule is PackageDependencyBanRule)
        rule as PackageDependencyBanRule
        assertEquals("domain_should_not_depend_on_infrastructure", rule.ruleName)
        assertEquals(listOf("..domain.."), rule.sourcePackagePatterns)
        assertEquals(listOf("..infrastructure.."), rule.forbiddenPackagePatterns)
    }

    fun testParsesPackageDependencyBanRuleWithAnyPackageOnSourceAndTarget() {
        val rule = parseSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule application_should_not_depend_on_adapters =
                            noClasses()
                                    .that()
                                    .resideInAnyPackage("..application..", "..domain..")
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage("..adapter..", "..infrastructure..");
                }
            """.trimIndent(),
        )

        assertTrue(rule is PackageDependencyBanRule)
        rule as PackageDependencyBanRule
        assertEquals(listOf("..application..", "..domain.."), rule.sourcePackagePatterns)
        assertEquals(listOf("..adapter..", "..infrastructure.."), rule.forbiddenPackagePatterns)
    }

    fun testParsesClassNameSuffixRule() {
        val rule = parseSingleRule(
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

        assertTrue(rule is ClassNameSuffixRule)
        rule as ClassNameSuffixRule
        assertEquals("controller_classes_should_end_with_controller", rule.ruleName)
        assertEquals("..controller..", rule.sourcePackagePattern)
        assertEquals("Controller", rule.requiredSuffix)
    }

    fun testParsesForbiddenAnnotationRuleWithQualifiedImport() {
        val rule = parseSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import org.springframework.stereotype.Service;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule domain_should_not_be_service =
                            noClasses()
                                    .that()
                                    .resideInAPackage("..domain..")
                                    .should()
                                    .beAnnotatedWith(Service.class);
                }
            """.trimIndent(),
        )

        assertTrue(rule is ForbiddenAnnotationRule)
        rule as ForbiddenAnnotationRule
        assertEquals("domain_should_not_be_service", rule.ruleName)
        assertEquals("..domain..", rule.sourcePackagePattern)
        assertEquals("org.springframework.stereotype.Service", rule.forbiddenAnnotationQualifiedName)
    }

    fun testParsesAnalyzeClassesScopeAndBecauseReason() {
        val rule = parseSingleRule(
            """
                import com.tngtech.archunit.junit.AnalyzeClasses;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                @AnalyzeClasses(packages = {"io.indoorplus", "com.example"})
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

        assertTrue(rule is AnnotationExclusivityRule)
        assertEquals(AnalyzeScope.Packages(listOf("io.indoorplus", "com.example")), rule.analyzeScope)
        assertEquals("Primary and secondary mapper annotations must be exclusive.", rule.reason)
    }

    fun testParsesAnnotationExclusivityRuleWithStringArguments() {
        val rule = parseSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule mapper_annotation_must_be_exclusive =
                            classes()
                                    .that()
                                    .areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                                    .should()
                                    .notBeAnnotatedWith("io.indoorplus.SecondaryMapper");
                }
            """.trimIndent(),
        )

        assertTrue(rule is AnnotationExclusivityRule)
        rule as AnnotationExclusivityRule
        assertEquals("mapper_annotation_must_be_exclusive", rule.ruleName)
        assertEquals("org.apache.ibatis.annotations.Mapper", rule.requiredAnnotationQualifiedName)
        assertEquals("io.indoorplus.SecondaryMapper", rule.forbiddenAnnotationQualifiedName)
    }

    fun testDiscoversSupportedRuleDescriptorWithoutChangingLiveRuleParse() {
        val source = findSingleSource(
            """
                import com.tngtech.archunit.junit.AnalyzeClasses;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                @AnalyzeClasses(packages = "com.example")
                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule controller_classes_should_end_with_controller =
                            classes()
                                    .that()
                                    .resideInAPackage("..controller..")
                                    .should()
                                    .haveSimpleNameEndingWith("Controller")
                                    .because("Controllers stay visible at the edge.");
                }
            """.trimIndent(),
        )

        val discovered = ArchRuleParser.discover(source) ?: error("Expected discovered rule metadata")

        assertEquals("controller_classes_should_end_with_controller", discovered.ruleName)
        assertTrue(discovered.liveRule is ClassNameSuffixRule)
        assertEquals(discovered.liveRule, ArchRuleParser.discover(source)?.liveRule)
        assertEquals(SubjectKind.Classes, discovered.descriptor.subject)
        assertEquals(source.fieldPointer, discovered.descriptor.sourcePointer)
        assertEquals(AnalyzeScope.Packages(listOf("com.example")), discovered.descriptor.scope)
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
        assertEquals("Controllers stay visible at the edge.", discovered.descriptor.reason)
    }

    fun testDiscoversDescriptorsForExistingSupportedRuleFamilies() {
        val packageDependencyBan = discoverSingleRule(
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
        assertTrue(packageDependencyBan.liveRule is PackageDependencyBanRule)
        assertEquals(PredicateExpr.Leaf("resideInAPackage(..domain..)"), packageDependencyBan.descriptor.predicate)
        assertEquals(
            ConditionExpr.Leaf("dependOnClassesThat.resideInPackages(..infrastructure..)"),
            packageDependencyBan.descriptor.condition,
        )
        assertEquals(SupportStatus.Supported, packageDependencyBan.descriptor.supportStatus)

        val classNameSuffix = discoverSingleRule(
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
        assertTrue(classNameSuffix.liveRule is ClassNameSuffixRule)
        assertEquals(PredicateExpr.Leaf("resideInAPackage(..controller..)"), classNameSuffix.descriptor.predicate)
        assertEquals(ConditionExpr.Leaf("haveSimpleNameEndingWith(Controller)"), classNameSuffix.descriptor.condition)
        assertEquals(SupportStatus.Supported, classNameSuffix.descriptor.supportStatus)

        val forbiddenAnnotation = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import org.springframework.stereotype.Service;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule domain_should_not_be_service =
                            noClasses()
                                    .that()
                                    .resideInAPackage("..domain..")
                                    .should()
                                    .beAnnotatedWith(Service.class);
                }
            """.trimIndent(),
        )
        assertTrue(forbiddenAnnotation.liveRule is ForbiddenAnnotationRule)
        assertEquals(PredicateExpr.Leaf("resideInAPackage(..domain..)"), forbiddenAnnotation.descriptor.predicate)
        assertEquals(
            ConditionExpr.Leaf("beAnnotatedWith(org.springframework.stereotype.Service)"),
            forbiddenAnnotation.descriptor.condition,
        )
        assertEquals(SupportStatus.Supported, forbiddenAnnotation.descriptor.supportStatus)

        val annotationExclusivity = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule mapper_annotation_must_be_exclusive =
                            classes()
                                    .that()
                                    .areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                                    .should()
                                    .notBeAnnotatedWith("io.indoorplus.SecondaryMapper");
                }
            """.trimIndent(),
        )
        assertTrue(annotationExclusivity.liveRule is AnnotationExclusivityRule)
        assertEquals(
            PredicateExpr.Leaf("areAnnotatedWith(org.apache.ibatis.annotations.Mapper)"),
            annotationExclusivity.descriptor.predicate,
        )
        assertEquals(
            ConditionExpr.Leaf("notBeAnnotatedWith(io.indoorplus.SecondaryMapper)"),
            annotationExclusivity.descriptor.condition,
        )
        assertEquals(SupportStatus.Supported, annotationExclusivity.descriptor.supportStatus)
    }

    fun testDiscoversSupportedDescriptorForResideInAnyPackageDependencyRuleWithAnyPackageOnSourceAndTarget() {
        val discovered = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule application_should_not_depend_on_adapters =
                            noClasses()
                                    .that()
                                    .resideInAnyPackage("..application..", "..domain..")
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage("..adapter..", "..infrastructure..");
                }
            """.trimIndent(),
        )

        assertTrue(discovered.liveRule is PackageDependencyBanRule)
        discovered.liveRule as PackageDependencyBanRule
        assertEquals(listOf("..application..", "..domain.."), discovered.liveRule.sourcePackagePatterns)
        assertEquals(listOf("..adapter..", "..infrastructure.."), discovered.liveRule.forbiddenPackagePatterns)
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
        assertEquals(
            PredicateExpr.Leaf("resideInAnyPackage(..application.., ..domain..)"),
            discovered.descriptor.predicate,
        )
        assertEquals(
            ConditionExpr.Leaf("dependOnClassesThat.resideInPackages(..adapter.., ..infrastructure..)"),
            discovered.descriptor.condition,
        )
    }

    fun testDiscoversSupportedDescriptorForResideInAnyPackageDependencyRule() {
        val discovered = discoverSingleRule(
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
                                    .resideInAnyPackage("..infrastructure..", "..adapter..");
                }
            """.trimIndent(),
        )

        assertTrue(discovered.liveRule is PackageDependencyBanRule)
        discovered.liveRule as PackageDependencyBanRule
        assertEquals("domain_should_not_depend_on_infrastructure", discovered.ruleName)
        assertEquals(listOf("..domain.."), discovered.liveRule.sourcePackagePatterns)
        assertEquals(listOf("..infrastructure..", "..adapter.."), discovered.liveRule.forbiddenPackagePatterns)
        assertEquals(SubjectKind.Classes, discovered.descriptor.subject)
        assertEquals(AnalyzeScope.All, discovered.descriptor.scope)
        assertEquals(PredicateExpr.Leaf("resideInAPackage(..domain..)"), discovered.descriptor.predicate)
        assertEquals(
            ConditionExpr.Leaf("dependOnClassesThat.resideInPackages(..infrastructure.., ..adapter..)"),
            discovered.descriptor.condition,
        )
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
    }

    fun testDiscoversLeftAssociativeBooleanPredicateDescriptor() {
        val discovered = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule boolean_predicate_rule =
                            classes()
                                    .that()
                                    .haveSimpleNameEndingWith("Adapter")
                                    .and()
                                    .areInterfaces()
                                    .or()
                                    .areAnnotatedWith("com.example.Marker")
                                    .should()
                                    .beInterfaces();
                }
            """.trimIndent(),
        )

        assertTrue(discovered.liveRule is ClassConventionRule)
        assertEquals(
            PredicateExpr.Or(
                PredicateExpr.And(
                    PredicateExpr.HaveSimpleNameEndingWith("Adapter"),
                    PredicateExpr.AreInterfaces(expected = true),
                ),
                PredicateExpr.AreAnnotatedWith("com.example.Marker"),
            ),
            discovered.descriptor.predicate,
        )
        assertEquals(ConditionExpr.BeInterfaces(required = true), discovered.descriptor.condition)
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
    }

    fun testBooleanPredicateDoesNotPartiallyMatchLiveAnnotationRule() {
        val discovered = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule mapper_or_adapter_should_not_be_secondary =
                            classes()
                                    .that()
                                    .areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                                    .or()
                                    .haveSimpleNameEndingWith("Adapter")
                                    .should()
                                    .notBeAnnotatedWith("io.indoorplus.SecondaryMapper");
                }
            """.trimIndent(),
        )

        assertTrue(discovered.liveRule is ClassConventionRule)
        assertEquals(
            PredicateExpr.Or(
                PredicateExpr.AreAnnotatedWith("org.apache.ibatis.annotations.Mapper"),
                PredicateExpr.HaveSimpleNameEndingWith("Adapter"),
            ),
            discovered.descriptor.predicate,
        )
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
    }

    fun testExtraPredicateDoesNotPartiallyMatchLiveAnnotationRule() {
        val discovered = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule mapper_in_persistence_should_not_be_secondary =
                            classes()
                                    .that()
                                    .resideInAPackage("..persistence..")
                                    .areAnnotatedWith("org.apache.ibatis.annotations.Mapper")
                                    .should()
                                    .notBeAnnotatedWith("io.indoorplus.SecondaryMapper");
                }
            """.trimIndent(),
        )

        assertTrue(discovered.liveRule is ClassConventionRule)
        assertEquals(
            PredicateExpr.And(
                PredicateExpr.ResideInPackages(listOf("..persistence..")),
                PredicateExpr.AreAnnotatedWith("org.apache.ibatis.annotations.Mapper"),
            ),
            discovered.descriptor.predicate,
        )
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
    }

    fun testParsesQueryMapperInterfaceNamingSubset() {
        val discovered = discoverSingleRule(
            """
                package com.example.rules;

                import com.example.QueryMapper;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule query_mappers_should_be_interfaces =
                            classes()
                                    .that()
                                    .haveSimpleNameEndingWith("QueryMapper")
                                    .should()
                                    .beInterfaces()
                                    .andShould()
                                    .beAssignableTo(QueryMapper.class);
                }
            """.trimIndent(),
        )

        assertTrue(discovered.liveRule is InterfaceNamingRule)
        assertEquals(PredicateExpr.Leaf("haveSimpleNameEndingWith(QueryMapper)"), discovered.descriptor.predicate)
        assertEquals(
            ConditionExpr.And(
                ConditionExpr.Leaf("beInterfaces"),
                ConditionExpr.Leaf("beAssignableTo(com.example.QueryMapper)"),
            ),
            discovered.descriptor.condition,
        )
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
    }

    fun testParsesLiteralClassAndMethodMetaAnnotationSubsets() {
        val classRule = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule interface_proxy_annotations_are_forbidden =
                            classes()
                                    .that()
                                    .areInterfaces()
                                    .should()
                                    .notBeMetaAnnotatedWith("com.example.Proxy");
                }
            """.trimIndent(),
        )
        assertTrue(classRule.liveRule is ClassMetaAnnotationRule)
        assertEquals(SubjectKind.Classes, classRule.descriptor.subject)
        assertEquals(PredicateExpr.Leaf("areInterfaces"), classRule.descriptor.predicate)
        assertEquals(
            ConditionExpr.Leaf("notBeMetaAnnotatedWith(com.example.Proxy)"),
            classRule.descriptor.condition,
        )

        val methodRule = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule interface_method_proxy_annotations_are_forbidden =
                            methods()
                                    .that()
                                    .areDeclaredInClassesThat()
                                    .areInterfaces()
                                    .should()
                                    .notBeMetaAnnotatedWith("com.example.Proxy");
                }
            """.trimIndent(),
        )
        assertTrue(methodRule.liveRule is MethodMetaAnnotationRule)
        assertEquals(SubjectKind.Methods, methodRule.descriptor.subject)
        assertEquals(
            PredicateExpr.Leaf("areDeclaredInClassesThat.areInterfaces"),
            methodRule.descriptor.predicate,
        )
        assertEquals(
            ConditionExpr.Leaf("notBeMetaAnnotatedWith(com.example.Proxy)"),
            methodRule.descriptor.condition,
        )
    }

    fun testCustomPredicateHelperIsUnsupportedForFirstSlice() {
        val source = findSingleSource(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule proxy_annotation_rule =
                            classes()
                                    .that()
                                    .areInterfaces()
                                    .should()
                                    .notBeMetaAnnotatedWith(proxyAnnotations());
                }
            """.trimIndent(),
        )

        assertNull(ArchRuleParser.discover(source)?.liveRule)
        val discovered = ArchRuleParser.discover(source) ?: error("Expected unsupported rule metadata")
        assertNull(discovered.liveRule)
        assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
    }

    fun testUnqualifiedForbiddenAnnotationWithoutImportIsUnsupported() {
        val source = findSingleSource(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule domain_should_not_be_service =
                            noClasses()
                                    .that()
                                    .resideInAPackage("..domain..")
                                    .should()
                                    .beAnnotatedWith(Service.class);
                }
            """.trimIndent(),
        )

        assertNull(ArchRuleParser.discover(source)?.liveRule)
    }

    fun testUnsupportedMethodStyleArchTestIsIgnored() {
        val file = configureJava(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.core.domain.JavaClasses;

                class ArchitectureRules {
                    @ArchTest
                    void domain_rule(JavaClasses classes) {
                    }
                }
            """.trimIndent(),
        )

        assertTrue(ArchRuleSourceFinder.findInFile(file).isEmpty())
    }

    fun testAmbiguousSimpleArchTestAndArchRuleWithoutImportsAreIgnored() {
        val file = configureJava(
            """
                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule ambiguous = noClasses();
                }
            """.trimIndent(),
        )

        assertTrue(ArchRuleSourceFinder.findInFile(file).isEmpty())
    }

    fun testNonStaticOrNonFinalArchRuleFieldsAreIgnored() {
        val file = configureJava(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    final ArchRule non_static_rule =
                            noClasses().that().resideInAPackage("..domain..")
                                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

                    @ArchTest
                    static ArchRule non_final_rule =
                            noClasses().that().resideInAPackage("..domain..")
                                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
                }
            """.trimIndent(),
        )

        assertTrue(ArchRuleSourceFinder.findInFile(file).isEmpty())
    }

    fun testSupportsClassConventionWithResideInAnyPackage() {
        val source = findSingleSource(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule services_should_have_suffix =
                            classes()
                                    .that()
                                    .resideInAnyPackage("..service..", "..application..")
                                    .should()
                                    .haveSimpleNameEndingWith("Service");
                }
            """.trimIndent(),
        )

        val discovered = ArchRuleParser.discover(source) ?: error("Expected supported rule")
        assertTrue(discovered.liveRule is ClassConventionRule)
        assertEquals(
            PredicateExpr.ResideInPackages(listOf("..service..", "..application..")),
            discovered.descriptor.predicate,
        )
        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
    }

    fun testMalformedRuleDoesNotParse() {
        val source = findSingleSource(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule malformed = noClasses();
                }
            """.trimIndent(),
        )

        assertNull(ArchRuleParser.discover(source)?.liveRule)
    }

    fun testParsesEveryStaticClassPredicateLeaf() {
        val cases = listOf(
            "areAnnotatedWith(\"com.example.Required\")" to PredicateExpr.AreAnnotatedWith("com.example.Required"),
            "areNotAnnotatedWith(\"com.example.Forbidden\")" to PredicateExpr.AreNotAnnotatedWith("com.example.Forbidden"),
            "resideInAPackage(\"..service..\")" to PredicateExpr.ResideInPackages(listOf("..service..")),
            "resideInAnyPackage(\"..service..\", \"..api..\")" to
                PredicateExpr.ResideInPackages(listOf("..service..", "..api..")),
            "haveSimpleNameEndingWith(\"Service\")" to PredicateExpr.HaveSimpleNameEndingWith("Service"),
            "haveSimpleNameNotEndingWith(\"Impl\")" to PredicateExpr.HaveSimpleNameNotEndingWith("Impl"),
            "areInterfaces()" to PredicateExpr.AreInterfaces(expected = true),
            "areNotInterfaces()" to PredicateExpr.AreInterfaces(expected = false),
            "areEnums()" to PredicateExpr.AreEnums(expected = true),
            "areNotEnums()" to PredicateExpr.AreEnums(expected = false),
        )

        cases.forEach { (predicate, expected) ->
            val discovered = discoverSingleRule(classConventionRule(predicate, "beEnums()"))
            assertTrue("$predicate should be supported", discovered.liveRule is ClassConventionRule)
            assertEquals(expected, discovered.descriptor.predicate)
        }
    }

    fun testParsesEveryStaticClassConditionLeafAndLeftAssociativeAndShould() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Base.java",
            "package com.example; public class Base {}",
        )
        val cases = listOf(
            "beAnnotatedWith(\"com.example.Required\")" to ConditionExpr.BeAnnotatedWith("com.example.Required", true),
            "notBeAnnotatedWith(\"com.example.Forbidden\")" to ConditionExpr.BeAnnotatedWith("com.example.Forbidden", false),
            "resideInAPackage(\"..service..\")" to ConditionExpr.ResideInPackages(listOf("..service..")),
            "resideInAnyPackage(\"..service..\", \"..api..\")" to
                ConditionExpr.ResideInPackages(listOf("..service..", "..api..")),
            "haveSimpleNameEndingWith(\"Service\")" to ConditionExpr.HaveSimpleNameEndingWith("Service", true),
            "haveSimpleNameNotEndingWith(\"Impl\")" to ConditionExpr.HaveSimpleNameEndingWith("Impl", false),
            "beInterfaces()" to ConditionExpr.BeInterfaces(required = true),
            "notBeInterfaces()" to ConditionExpr.BeInterfaces(required = false),
            "beEnums()" to ConditionExpr.BeEnums(required = true),
            "notBeEnums()" to ConditionExpr.BeEnums(required = false),
            "beAssignableTo(\"com.example.Base\")" to ConditionExpr.BeAssignableTo("com.example.Base"),
        )

        cases.forEach { (condition, expected) ->
            val discovered = discoverSingleRule(classConventionRule("areEnums()", condition))
            assertTrue("$condition should be supported", discovered.liveRule is ClassConventionRule)
            assertEquals(expected, discovered.descriptor.condition)
        }

        val composite = discoverSingleRule(
            classConventionRule(
                "areNotEnums()",
                "beInterfaces().andShould().haveSimpleNameEndingWith(\"Mapper\").andShould().beAnnotatedWith(\"com.example.Mapper\")",
            ),
        )
        assertEquals(
            ConditionExpr.And(
                ConditionExpr.And(
                    ConditionExpr.BeInterfaces(required = true),
                    ConditionExpr.HaveSimpleNameEndingWith("Mapper", required = true),
                ),
                ConditionExpr.BeAnnotatedWith("com.example.Mapper", required = true),
            ),
            composite.descriptor.condition,
        )
    }

    fun testUnsupportedClassSiblingMakesWholeFallbackMetadataOnly() {
        val discovered = discoverSingleRule(
            classConventionRule(
                "areEnums()",
                "beInterfaces().andShould().beAnnotatedWith(annotationType())",
            ),
        )

        assertNull(discovered.liveRule)
        assertTrue((discovered.descriptor.supportStatus as SupportStatus.Unsupported).reason is UnsupportedReason.UnsupportedArgument)
    }

    fun testUnresolvedClassLiteralMakesWholeFallbackMetadataOnly() {
        val discovered = discoverSingleRule(
            classConventionRule(
                "areNotEnums()",
                "beAnnotatedWith(com.example.Missing.class)",
            ),
        )

        assertNull(discovered.liveRule)
        assertTrue((discovered.descriptor.supportStatus as SupportStatus.Unsupported).reason is UnsupportedReason.UnresolvedSymbol)
    }

    fun testUnresolvedAssignableTargetMakesWholeFallbackMetadataOnly() {
        val discovered = discoverSingleRule(
            classConventionRule(
                "areNotEnums()",
                "beAssignableTo(\"com.example.Missing\")",
            ),
        )

        assertNull(discovered.liveRule)
        assertTrue((discovered.descriptor.supportStatus as SupportStatus.Unsupported).reason is UnsupportedReason.UnresolvedSymbol)
    }

    fun testUnresolvedClassLiteralsKeepEveryTypeBearingExactHandlerMetadataOnly() {
        val file = configureJava(testData("archrules/exactUnresolvedClassLiterals.java"))
        val expectedReasons = mapOf(
            "unresolved_forbidden_annotation" to
                UnsupportedReason.UnresolvedSymbol("beAnnotatedWith", "com.example.missing.Forbidden"),
            "unresolved_annotation_exclusivity" to
                UnsupportedReason.UnresolvedSymbol("areAnnotatedWith", "com.example.missing.Required"),
            "unresolved_interface_assignability" to
                UnsupportedReason.UnresolvedSymbol("beAssignableTo", "com.example.missing.Base"),
            "unresolved_class_meta_annotation" to
                UnsupportedReason.UnresolvedSymbol("notBeMetaAnnotatedWith", "com.example.missing.Proxy"),
            "unresolved_method_meta_annotation" to
                UnsupportedReason.UnresolvedSymbol("notBeMetaAnnotatedWith", "com.example.missing.Proxy"),
        )

        val discoveries = ArchRuleSourceFinder.findInFile(file)
            .mapNotNull(ArchRuleParser::discover)
            .associateBy { it.ruleName }
        assertEquals(expectedReasons.keys, discoveries.keys)
        expectedReasons.forEach { (ruleName, expectedReason) ->
            val discovered = discoveries.getValue(ruleName)
            assertNull(discovered.liveRule)
            assertEquals(expectedReason, (discovered.descriptor.supportStatus as SupportStatus.Unsupported).reason)
        }
    }

    fun testDynamicClassPredicateArgumentMakesWholeFallbackMetadataOnly() {
        val discovered = discoverSingleRule(
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

        assertNull(discovered.liveRule)
        val status = discovered.descriptor.supportStatus as SupportStatus.Unsupported
        assertTrue(status.reason is UnsupportedReason.UnsupportedArgument)
    }

    fun testDeferredDeclarationAndCodeAccessRulesStayMetadataOnly() {
        val rules = listOf(
            exactRule(
                "classes().should().callMethod(java.lang.Throwable.class, \"printStackTrace\")",
                "classes",
            ),
            exactRule(
                "classes().should().accessField(java.lang.System.class, \"out\")",
                "classes",
            ),
            exactRule("methods().should().bePublic()", "methods"),
            exactRule("constructors().should().bePrivate()", "constructors"),
        )

        rules.forEach { ruleSource ->
            val discovered = discoverSingleRule(ruleSource)
            assertNull(discovered.liveRule)
            assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
        }
    }

    fun testEveryExactHandlerClassifiesOwnedForeignAndMalformedShapes() {
        val cases = exactHandlerCases()
        cases.forEachIndexed { index, (family, code) ->
            val source = findSingleSource(code)
            val calls = RawCallExtractor.from(source.initializer)

            assertTrue(
                "$family should match its valid shape",
                ArchRuleParser.classifyExactHandler(family, source, calls) is ExactHandlerDecision.Matched,
            )
            val foreignFamily = cases[(index + 1) % cases.size].first
            assertEquals(
                ExactHandlerDecision.NotApplicable,
                ArchRuleParser.classifyExactHandler(foreignFamily, source, calls),
            )
            val malformedCalls = calls.toMutableList().also { malformed ->
                malformed[0] = malformed[0].copy(arguments = listOf(RawArgument.Reference(0, "dynamic")))
            }
            assertTrue(
                "$family should own and reject malformed arity",
                ArchRuleParser.classifyExactHandler(family, source, malformedCalls) is ExactHandlerDecision.Unsupported,
            )
            val argumentCallIndex = calls.indexOfFirst { it.arguments.isNotEmpty() }
            assertTrue("$family should have an argument-bearing owned call", argumentCallIndex >= 0)
            val wrongKindCalls = calls.toMutableList().also { wrongKind ->
                wrongKind[argumentCallIndex] = wrongKind[argumentCallIndex].copy(
                    arguments = listOf(RawArgument.Lambda(0)),
                )
            }
            assertTrue(
                "$family should own and reject wrong argument kinds",
                ArchRuleParser.classifyExactHandler(family, source, wrongKindCalls) is ExactHandlerDecision.Unsupported,
            )
            val unresolvedCalls = calls.toMutableList().also { unresolved ->
                unresolved[argumentCallIndex] = unresolved[argumentCallIndex].copy(
                    arguments = listOf(RawArgument.Reference(0, "missingSymbol")),
                )
            }
            assertTrue(
                "$family should own and reject unresolved inputs",
                ArchRuleParser.classifyExactHandler(family, source, unresolvedCalls) is ExactHandlerDecision.Unsupported,
            )
        }
    }

    fun testAggregateRouteRunsFallbackOnlyAfterAllExactHandlersDecline() {
        val exactSource = findSingleSource(exactHandlerCases().first().second)
        val exactCalls = RawCallExtractor.from(exactSource.initializer)
        var fallbackCalls = 0

        assertTrue(
            ArchRuleParser.routeExactHandlers(exactSource, exactCalls) {
                fallbackCalls++
                ExactHandlerDecision.NotApplicable
            } is ExactHandlerDecision.Matched,
        )
        assertEquals(0, fallbackCalls)

        val malformedCalls = exactCalls.toMutableList().also { malformed ->
            val packageCallIndex = malformed.indexOfFirst { it.name == "resideInAPackage" }
            malformed[packageCallIndex] = malformed[packageCallIndex].copy(
                arguments = listOf(
                    RawArgument.StringLiteral(0, "..domain.."),
                    RawArgument.Reference(1, "dynamicPackage"),
                ),
            )
        }
        assertTrue(
            ArchRuleParser.routeExactHandlers(exactSource, malformedCalls) {
                fallbackCalls++
                ExactHandlerDecision.NotApplicable
            } is ExactHandlerDecision.Unsupported,
        )
        assertEquals(0, fallbackCalls)

        val fallbackSource = findSingleSource(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                class ArchitectureRules {
                    @ArchTest static final ArchRule rule = classes().should().beEnums();
                }
            """.trimIndent(),
        )
        assertEquals(
            ExactHandlerDecision.NotApplicable,
            ArchRuleParser.routeExactHandlers(fallbackSource, RawCallExtractor.from(fallbackSource.initializer)) {
                fallbackCalls++
                ExactHandlerDecision.NotApplicable
            },
        )
        assertEquals(1, fallbackCalls)
    }

    fun testMixedLiteralAndDynamicExactArgumentsStayMetadataOnly() {
        val discovered = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
                class ArchitectureRules {
                    static String dynamicPackage = "..adapter..";
                    @ArchTest static final ArchRule rule = noClasses().that()
                            .resideInAnyPackage("..domain..", dynamicPackage)
                            .should().dependOnClassesThat().resideInAPackage("..adapter..");
                }
            """.trimIndent(),
        )

        assertNull(discovered.liveRule)
        val status = discovered.descriptor.supportStatus as SupportStatus.Unsupported
        assertTrue(status.reason is UnsupportedReason.UnsupportedArgument)
    }

    private fun exactHandlerCases(): List<Pair<ExactHandlerFamily, String>> = listOf(
        ExactHandlerFamily.PACKAGE_DEPENDENCY_BAN to exactRule(
            "noClasses().that().resideInAPackage(\"..domain..\").should().dependOnClassesThat().resideInAPackage(\"..adapter..\")",
            "noClasses",
        ),
        ExactHandlerFamily.CLASS_NAME_SUFFIX to exactRule(
            "classes().that().resideInAPackage(\"..service..\").should().haveSimpleNameEndingWith(\"Service\")",
            "classes",
        ),
        ExactHandlerFamily.FORBIDDEN_ANNOTATION to exactRule(
            "noClasses().that().resideInAPackage(\"..domain..\").should().beAnnotatedWith(com.example.Service.class)",
            "noClasses",
        ),
        ExactHandlerFamily.ANNOTATION_EXCLUSIVITY to exactRule(
            "classes().that().areAnnotatedWith(\"com.example.Mapper\").should().notBeAnnotatedWith(\"com.example.Secondary\")",
            "classes",
        ),
        ExactHandlerFamily.INTERFACE_NAMING to exactRule(
            "classes().that().haveSimpleNameEndingWith(\"Mapper\").should().beInterfaces().andShould().beAssignableTo(\"com.example.Mapper\")",
            "classes",
        ),
        ExactHandlerFamily.CLASS_META_ANNOTATION to exactRule(
            "classes().that().areInterfaces().should().notBeMetaAnnotatedWith(\"com.example.Proxy\")",
            "classes",
        ),
        ExactHandlerFamily.METHOD_META_ANNOTATION to exactRule(
            "methods().that().areDeclaredInClassesThat().areInterfaces().should().notBeMetaAnnotatedWith(\"com.example.Proxy\")",
            "methods",
        ),
    )

    private fun exactRule(
        initializer: String,
        entryPoint: String,
    ): String = """
        import com.tngtech.archunit.junit.ArchTest;
        import com.tngtech.archunit.lang.ArchRule;
        import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.$entryPoint;
        class ArchitectureRules {
            @ArchTest static final ArchRule rule = $initializer;
        }
    """.trimIndent()

    private fun classConventionRule(
        predicate: String,
        condition: String,
    ): String = """
        import com.tngtech.archunit.junit.ArchTest;
        import com.tngtech.archunit.lang.ArchRule;
        import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
        class ArchitectureRules {
            @ArchTest static final ArchRule rule = classes().that().$predicate.should().$condition;
        }
    """.trimIndent()

    private fun parseSingleRule(code: String): LiveArchRule {
        val source = findSingleSource(code)
        return ArchRuleParser.discover(source)?.liveRule ?: error("Expected supported ArchUnit Lens rule")
    }

    private fun discoverSingleRule(code: String): DiscoveredArchRule {
        val source = findSingleSource(code)
        return ArchRuleParser.discover(source) ?: error("Expected discovered ArchUnit Lens rule")
    }

    private fun findSingleSource(code: String): ArchRuleSource {
        val file = configureJava(code)
        val sources = ArchRuleSourceFinder.findInFile(file)
        assertEquals(1, sources.size)
        return sources.single()
    }

    private fun configureJava(code: String): PsiFile = myFixture.configureByText("ArchitectureRules.java", code)

    private fun testData(path: String): String = Path.of("src/test/testData", path).toFile().readText()
}

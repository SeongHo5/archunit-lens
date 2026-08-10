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
        myFixture.addFileToProject(
            "src/test/java/com/tngtech/archunit/core/domain/JavaModifier.java",
            "package com.tngtech.archunit.core.domain; public enum JavaModifier { FINAL, STATIC }",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Value.java",
            "package com.example; public @interface Value {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/QueryModel.java",
            "package com.example; public interface QueryModel {}",
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

    fun testConsecutiveClassPredicatesStayMetadataOnly() {
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

        assertNull(discovered.liveRule)
        assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
    }

    fun testClassPredicatesRequireExplicitThatSelector() {
        val allClasses = discoverSingleRule(exactRule("classes().should().beEnums()", "classes"))
        assertTrue(allClasses.liveRule is ClassConventionRule)
        assertEquals(PredicateExpr.All, allClasses.descriptor.predicate)

        val explicitSelector = discoverSingleRule(classConventionRule("areNotEnums()", "beEnums()"))
        assertTrue(explicitSelector.liveRule is ClassConventionRule)

        val selectorless = discoverSingleRule(
            exactRule("classes().areNotEnums().should().beEnums()", "classes"),
        )
        assertNull(selectorless.liveRule)
        assertTrue(selectorless.descriptor.supportStatus is SupportStatus.Unsupported)
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

    fun testHelperBackedCustomConditionsHaveStableMetadataAcrossSubjects() {
        val file = configureJava(testData("archrules/helperBackedCustomConditions.java"))
        val discoveries = ArchRuleSourceFinder.findInFile(file)
            .mapNotNull(ArchRuleParser::discover)
            .associateBy { it.ruleName }
        val expected = mapOf(
            "class_helper_condition" to Triple(SubjectKind.Classes, "customClassCondition()", "class invariant"),
            "method_helper_condition" to Triple(SubjectKind.Methods, "customMethodCondition()", "method invariant"),
            "constructor_helper_condition" to
                Triple(SubjectKind.Constructors, "customConstructorCondition()", "constructor invariant"),
            "field_helper_condition" to Triple(SubjectKind.Fields, "customFieldCondition()", "field invariant"),
        )

        assertEquals(expected.keys, discoveries.keys)
        expected.forEach { (ruleName, metadata) ->
            val discovered = discoveries.getValue(ruleName)
            assertNull(discovered.liveRule)
            assertEquals(metadata.first, discovered.descriptor.subject)
            assertEquals(ConditionExpr.Leaf(metadata.second), discovered.descriptor.condition)
            assertEquals(metadata.third, discovered.descriptor.reason)
            assertEquals(
                SupportStatus.Unsupported(UnsupportedReason.HelperBackedCustomCondition),
                discovered.descriptor.supportStatus,
            )
        }
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

    fun testClassPackagePatternsAcceptOnlyTheProvenMatcherSubset() {
        listOf("com.example.service", "..service..", "..service", "com.example..").forEach { pattern ->
            val discovered = discoverSingleRule(
                classConventionRule("resideInAPackage(\"$pattern\")", "beEnums()"),
            )
            assertTrue("$pattern should be supported", discovered.liveRule is ClassConventionRule)
        }

        val unsupportedRules = listOf(
            classConventionRule("resideInAPackage(\"com.*.service\")", "beEnums()"),
            classConventionRule("resideInAnyPackage(\"..service..\", \"com..service\")", "beEnums()"),
            classConventionRule("areNotEnums()", "resideInAPackage(\"com.*.service\")"),
            classConventionRule("areNotEnums()", "resideInAnyPackage(\"..service..\", \"com..service\")"),
        )
        unsupportedRules.forEach { code ->
            val discovered = discoverSingleRule(code)
            assertNull(discovered.liveRule)
            assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
        }
    }

    fun testDanglingClassPredicateTokensStayMetadataOnly() {
        val malformedRules = listOf(
            "classes().that().should().beEnums()",
            "classes().that().resideInAPackage(\"..service..\").and().should().beEnums()",
            "classes().that().resideInAPackage(\"..service..\").or().should().beEnums()",
        )

        malformedRules.forEach { initializer ->
            val discovered = discoverSingleRule(exactRule(initializer, "classes"))
            assertNull(discovered.liveRule)
            assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
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
        val expectedCases = mapOf(
            "unresolved_forbidden_annotation" to Pair(
                ExactHandlerFamily.FORBIDDEN_ANNOTATION,
                UnsupportedReason.UnresolvedSymbol("beAnnotatedWith", "com.example.missing.Forbidden"),
            ),
            "unresolved_annotation_exclusivity" to Pair(
                ExactHandlerFamily.ANNOTATION_EXCLUSIVITY,
                UnsupportedReason.UnresolvedSymbol("areAnnotatedWith", "com.example.missing.Required"),
            ),
            "unresolved_interface_assignability" to Pair(
                ExactHandlerFamily.INTERFACE_NAMING,
                UnsupportedReason.UnresolvedSymbol("beAssignableTo", "com.example.missing.Base"),
            ),
            "unresolved_class_meta_annotation" to Pair(
                ExactHandlerFamily.CLASS_META_ANNOTATION,
                UnsupportedReason.UnresolvedSymbol("notBeMetaAnnotatedWith", "com.example.missing.Proxy"),
            ),
            "unresolved_method_meta_annotation" to Pair(
                ExactHandlerFamily.METHOD_META_ANNOTATION,
                UnsupportedReason.UnresolvedSymbol("notBeMetaAnnotatedWith", "com.example.missing.Proxy"),
            ),
        )

        val sources = ArchRuleSourceFinder.findInFile(file).associateBy { it.ruleName }
        assertEquals(expectedCases.keys, sources.keys)
        expectedCases.forEach { (ruleName, expectedCase) ->
            val (family, expectedReason) = expectedCase
            val source = sources.getValue(ruleName)
            assertEquals(
                ExactHandlerDecision.Unsupported(expectedReason),
                ArchRuleParser.classifyExactHandler(family, source, RawCallExtractor.from(source.initializer)),
            )
            val discovered = ArchRuleParser.discover(source) ?: error("Expected discovered rule")
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

    fun testParsesBoundedStaticClassFacts() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Transactional.java",
            "package com.example; public @interface Transactional {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Composed.java",
            "package com.example; @Transactional public @interface Composed {}",
        )

        val discovered = discoverSingleRule(
            """
                import com.tngtech.archunit.core.domain.JavaModifier;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                class ArchitectureRules {
                    private static final String[] UTIL_PACKAGES = {"..util..", "..support.."};
                    @ArchTest static final ArchRule rule = classes().that()
                            .resideInAnyPackage(UTIL_PACKAGES)
                            .and().areMetaAnnotatedWith(com.example.Transactional.class)
                            .should().beRecords().andShould().haveModifier(JavaModifier.FINAL);
                }
            """.trimIndent(),
        )

        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
        assertEquals(
            PredicateExpr.And(
                PredicateExpr.ResideInPackages(listOf("..util..", "..support..")),
                PredicateExpr.AreMetaAnnotatedWith("com.example.Transactional", expected = true),
            ),
            discovered.descriptor.predicate,
        )
        assertEquals(
            ConditionExpr.And(
                ConditionExpr.BeRecords(required = true),
                ConditionExpr.HaveModifier(ClassModifier.FINAL, required = true),
            ),
            discovered.descriptor.condition,
        )
    }

    fun testRejectsUnsafeStaticClassFactArgumentsPrecisely() {
        val cases = mapOf(
            "mutable" to """
                private static String[] PACKAGES = {"..util.."};
                @ArchTest static final ArchRule rule = classes().that().resideInAnyPackage(PACKAGES).should().beRecords();
            """,
            "crossFile" to """
                @ArchTest static final ArchRule rule = classes().that().resideInAnyPackage(com.example.Packages.VALUES).should().beRecords();
            """,
            "helper" to """
                private static final String[] PACKAGES = helper();
                @ArchTest static final ArchRule rule = classes().that().resideInAnyPackage(PACKAGES).should().beRecords();
            """,
            "sameNameHelperEscape" to """
                private static final String[] PACKAGES = {"..util.."};
                static { resideInAnyPackage(PACKAGES); }
                private static void resideInAnyPackage(String[] values) {}
                @ArchTest static final ArchRule rule = classes().that().resideInAnyPackage(PACKAGES).should().beRecords();
            """,
            "foreignModifier" to """
                @ArchTest static final ArchRule rule = classes().should().haveModifier(com.example.JavaModifier.FINAL);
            """,
            "unsupportedModifier" to """
                @ArchTest static final ArchRule rule = classes().should().haveModifier(com.tngtech.archunit.core.domain.JavaModifier.STATIC);
            """,
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/Packages.java",
            "package com.example; public class Packages { public static final String[] VALUES = {\"..util..\"}; }",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/JavaModifier.java",
            "package com.example; public enum JavaModifier { FINAL }",
        )

        cases.forEach { (name, body) ->
            val discovered = discoverSingleRule(
                """
                    import com.tngtech.archunit.junit.ArchTest;
                    import com.tngtech.archunit.lang.ArchRule;
                    import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                    class ArchitectureRules {
                        $body
                        private static String[] helper() { return null; }
                    }
                """.trimIndent(),
            )
            assertNull("$name must stay metadata-only", discovered.liveRule)
            assertTrue(
                "$name reason must identify the unsupported argument",
                (discovered.descriptor.supportStatus as SupportStatus.Unsupported).reason is UnsupportedReason.UnsupportedArgument,
            )
        }
    }

    fun testRejectsStaticPackageArraysMutatedElsewhereInTheirEnclosingNest() {
        val cases = mapOf(
            "enclosing" to """
                class ArchitectureRules {
                    static class NestedRules {
                        private static final String[] PACKAGES = {"..util.."};
                        @ArchTest static final ArchRule rule = classes().that()
                                .resideInAnyPackage(PACKAGES).should().beRecords();
                    }
                    static { NestedRules.PACKAGES[0] = "..changed.."; }
                }
            """,
            "sibling" to """
                class ArchitectureRules {
                    static class NestedRules {
                        private static final String[] PACKAGES = {"..util.."};
                        @ArchTest static final ArchRule rule = classes().that()
                                .resideInAnyPackage(PACKAGES).should().beRecords();
                    }
                    static class Mutator {
                        static { NestedRules.PACKAGES[0] = "..changed.."; }
                    }
                }
            """,
        )

        cases.forEach { (name, code) ->
            val discovered = discoverSingleRule(
                """
                    import com.tngtech.archunit.junit.ArchTest;
                    import com.tngtech.archunit.lang.ArchRule;
                    import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                    $code
                """.trimIndent(),
            )

            assertNull("$name nest mutation must stay metadata-only", discovered.liveRule)
            assertTrue(
                "$name nest mutation must identify the unsupported argument",
                (discovered.descriptor.supportStatus as SupportStatus.Unsupported).reason is UnsupportedReason.UnsupportedArgument,
            )
        }
    }

    fun testAllowsStaticPackageArrayWithSameNamedSiblingField() {
        val discovered = discoverSingleRule(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
                class ArchitectureRules {
                    static class NestedRules {
                        private static final String[] PACKAGES = {"..util.."};
                        @ArchTest static final ArchRule rule = classes().that()
                                .resideInAnyPackage(PACKAGES).should().beRecords();
                    }
                    static class Sibling {
                        private static final String[] PACKAGES = {"..other.."};
                        static { PACKAGES[0] = "..changed.."; }
                    }
                }
            """.trimIndent(),
        )

        assertEquals(SupportStatus.Supported, discovered.descriptor.supportStatus)
        assertNotNull(discovered.liveRule)
    }

    fun testUnresolvedMetaAnnotationAndUnsupportedSiblingStayMetadataOnly() {
        val unresolved = discoverSingleRule(
            classConventionRule(
                "areMetaAnnotatedWith(\"com.example.missing.Transactional\")",
                "beRecords()",
            ),
        )
        assertNull(unresolved.liveRule)
        assertEquals(
            UnsupportedReason.UnresolvedSymbol("areMetaAnnotatedWith", "com.example.missing.Transactional"),
            (unresolved.descriptor.supportStatus as SupportStatus.Unsupported).reason,
        )

        val sibling = discoverSingleRule(
            classConventionRule(
                "areRecords().and().haveSimpleNameMatching(\".*\")",
                "haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)",
            ),
        )
        assertNull(sibling.liveRule)
        assertEquals(
            UnsupportedReason.UnsupportedOrAmbiguousRuleChain,
            (sibling.descriptor.supportStatus as SupportStatus.Unsupported).reason,
        )
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
            exactRule("constructors().should().bePublic()", "constructors"),
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
            val invalidArity = UnsupportedReason.InvalidArity(calls[0].name, "0", 1)
            assertEquals(
                "$family should preserve its exact invalid-arity reason",
                ExactHandlerDecision.Unsupported(family.expectedExactReason(invalidArity)),
                ArchRuleParser.classifyExactHandler(family, source, malformedCalls),
            )
            val argumentCallIndex = calls.indexOfFirst { it.arguments.isNotEmpty() }
            assertTrue("$family should have an argument-bearing owned call", argumentCallIndex >= 0)
            val wrongKindCalls = calls.toMutableList().also { wrongKind ->
                wrongKind[argumentCallIndex] = wrongKind[argumentCallIndex].copy(
                    arguments = listOf(RawArgument.Lambda(0)),
                )
            }
            val unsupportedArgument = UnsupportedReason.UnsupportedArgument(calls[argumentCallIndex].name, 0, "lambda")
            assertEquals(
                "$family should preserve its exact unsupported-argument reason",
                ExactHandlerDecision.Unsupported(family.expectedExactReason(unsupportedArgument)),
                ArchRuleParser.classifyExactHandler(family, source, wrongKindCalls),
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

    fun testParsesPositiveMethodAndConstructorDeclarationConventions() {
        addMemberConventionStubs()
        val file = configureJava(testData("archrules/methodConstructorConventions.java"))
        val discoveries = ArchRuleSourceFinder.findInFile(file)
            .mapNotNull(ArchRuleParser::discover)
            .associateBy { it.ruleName }

        val constructorRule = discoveries.getValue("utility_constructors_are_private").liveRule as? MemberConventionRule
        assertEquals(MemberSubjectKind.Constructors, constructorRule?.subject)
        assertEquals(MemberConditionExpr.BePrivate, constructorRule?.condition)
        assertEquals(
            MemberPredicateExpr.DeclaredInClasses(PredicateExpr.ResideInPackages(listOf("..util.."))),
            constructorRule?.predicate,
        )

        val staticMethodRule = discoveries.getValue("utility_methods_are_static").liveRule as? MemberConventionRule
        assertEquals(MemberSubjectKind.Methods, staticMethodRule?.subject)
        assertEquals(MemberConditionExpr.BeStatic, staticMethodRule?.condition)
        assertEquals(RulePolarity.POSITIVE, staticMethodRule?.polarity)
        assertEquals(RulePolarity.POSITIVE, discoveries.getValue("utility_methods_are_static").descriptor.polarity)

        val returnTypeRule = discoveries.getValue("controller_mappings_return_response_entity").liveRule as? MemberConventionRule
        assertEquals(
            MemberConditionExpr.HaveRawReturnType("com.example.ResponseEntity"),
            returnTypeRule?.condition,
        )
        assertEquals(
            MemberPredicateExpr.And(
                MemberPredicateExpr.IsAnnotatedWith("com.example.RequestMapping", metaAnnotated = true),
                MemberPredicateExpr.DeclaredInClasses(PredicateExpr.AreAnnotatedWith("com.example.RestController")),
            ),
            returnTypeRule?.predicate,
        )
    }

    fun testConstructorEntryPointArgumentsStayMetadataOnly() {
        val file = configureJava(
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
        val discoveries = ArchRuleSourceFinder.findInFile(file)
            .mapNotNull(ArchRuleParser::discover)
            .associateBy { it.ruleName }

        assertTrue(discoveries.getValue("ordinary").liveRule is MemberConventionRule)
        assertEquals(SupportStatus.Supported, discoveries.getValue("ordinary").descriptor.supportStatus)

        listOf("dynamic", "helper", "literal").forEach { ruleName ->
            val discovered = discoveries.getValue(ruleName)
            assertNull(discovered.liveRule)
            assertEquals(
                SupportStatus.Unsupported(UnsupportedReason.InvalidArity("constructors", "0", 1)),
                discovered.descriptor.supportStatus,
            )
        }
    }

    fun testPositiveMemberConventionRejectsUnresolvedAndUnsupportedSiblingsAsWholeRule() {
        addMemberConventionStubs()
        val unsupportedRules = listOf(
            "methods().that().areMetaAnnotatedWith(com.example.Missing.class).should().beStatic()",
            "methods().that().areDeclaredInClassesThat().resideInAPackage(utilityPackage).should().beStatic()",
            "methods().that().areDeclaredInClassesThat().resideInAPackage(\"..util..\")" +
                ".and().areAnnotatedWith(com.example.RequestMapping.class).should().beStatic()",
            "methods().that().areDeclaredInClassesThat().resideInAPackage(\"..util..\")" +
                ".should().beStatic().orShould().haveRawReturnType(com.example.ResponseEntity.class)",
            "methods().that().should().beStatic()",
            "constructors().that().areAnnotatedWith(com.example.RequestMapping.class).should().bePrivate()",
            "constructors().that().areDeclaredInClassesThat().resideInAPackage(\"..util..\")" +
                ".should().bePrivate(helper())",
        )

        unsupportedRules.forEachIndexed { index, initializer ->
            val discovered = discoverSingleRule(exactRule(initializer, initializer.substringBefore('(')))
            assertNull("case $index", discovered.liveRule)
            assertTrue("case $index", discovered.descriptor.supportStatus is SupportStatus.Unsupported)
        }
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

    fun testParsesNegativeFieldRuleWithRootPolarity() {
        val rule = parseSingleRule(
            exactRule("noFields().should().beAnnotatedWith(com.example.Value.class)", "noFields"),
        ) as MemberConventionRule

        assertEquals(MemberSubjectKind.Fields, rule.subject)
        assertEquals(RulePolarity.NEGATIVE, rule.polarity)
        assertEquals(MemberConditionExpr.BeAnnotatedWith("com.example.Value", false, true), rule.condition)
    }

    fun testParsesNegativeMethodDeclaringClassAndOrConditionTree() {
        val rule = parseSingleRule(
            exactRule(
                "noMethods().that().areDeclaredInClassesThat().implement(com.example.QueryModel.class)" +
                    ".should().haveNameMatching(\"^set[A-Z].*\").orShould().beStatic()",
                "noMethods",
            ),
        ) as MemberConventionRule

        assertEquals(MemberSubjectKind.Methods, rule.subject)
        assertEquals(RulePolarity.NEGATIVE, rule.polarity)
        assertTrue(rule.predicate is MemberPredicateExpr.DeclaredInClasses)
        assertTrue(rule.condition is MemberConditionExpr.Or)
    }

    fun testMalformedNegativeMemberRuleIsMetadataOnly() {
        val discovery = discoverSingleRule(exactRule("noFields().should().haveNameMatching(\"[\")", "noFields"))

        assertNull(discovery.liveRule)
        assertEquals(RulePolarity.NEGATIVE, discovery.descriptor.polarity)
    }

    private fun ExactHandlerFamily.expectedExactReason(reason: UnsupportedReason): UnsupportedReason {
        val metaAnnotationFamily = this == ExactHandlerFamily.CLASS_META_ANNOTATION ||
            this == ExactHandlerFamily.METHOD_META_ANNOTATION
        return if (metaAnnotationFamily && reason !is UnsupportedReason.UnresolvedSymbol) {
            UnsupportedReason.CustomOrMetaAnnotationPredicates
        } else {
            reason
        }
    }

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

    private fun addMemberConventionStubs() {
        myFixture.addFileToProject(
            "src/test/java/com/example/RequestMapping.java",
            "package com.example; public @interface RequestMapping {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/RestController.java",
            "package com.example; public @interface RestController {}",
        )
        myFixture.addFileToProject(
            "src/test/java/com/example/ResponseEntity.java",
            "package com.example; public class ResponseEntity<T> {}",
        )
    }

    private fun testData(path: String): String = Path.of("src/test/testData", path).toFile().readText()
}

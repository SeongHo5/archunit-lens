package io.github.archunitlens.rules

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ArchRuleParserTest : BasePlatformTestCase() {
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

        assertNull(discovered.liveRule)
        assertEquals(
            PredicateExpr.Or(
                PredicateExpr.And(
                    PredicateExpr.Leaf("haveSimpleNameEndingWith(Adapter)"),
                    PredicateExpr.Leaf("areInterfaces"),
                ),
                PredicateExpr.Leaf("areAnnotatedWith(com.example.Marker)"),
            ),
            discovered.descriptor.predicate,
        )
        assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
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

        assertNull(discovered.liveRule)
        assertEquals(
            PredicateExpr.Or(
                PredicateExpr.Leaf("areAnnotatedWith(org.apache.ibatis.annotations.Mapper)"),
                PredicateExpr.Leaf("haveSimpleNameEndingWith(Adapter)"),
            ),
            discovered.descriptor.predicate,
        )
        assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
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

        assertNull(discovered.liveRule)
        assertEquals(
            PredicateExpr.And(
                PredicateExpr.Leaf("resideInAPackage(..persistence..)"),
                PredicateExpr.Leaf("areAnnotatedWith(org.apache.ibatis.annotations.Mapper)"),
            ),
            discovered.descriptor.predicate,
        )
        assertTrue(discovered.descriptor.supportStatus is SupportStatus.Unsupported)
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

    fun testUnsupportedResideInAnyPackageShapeStillStaysMetadataOnly() {
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

        assertNull(ArchRuleParser.discover(source)?.liveRule)
        val discovered = ArchRuleParser.discover(source) ?: error("Expected unsupported rule metadata")
        assertNull(discovered.liveRule)
        val supportStatus = discovered.descriptor.supportStatus
        assertTrue(supportStatus is SupportStatus.Unsupported)
        supportStatus as SupportStatus.Unsupported
        assertEquals(UnsupportedReason.UnsupportedMultiPackageRuleShape, supportStatus.reason)
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
}

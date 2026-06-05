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
        assertEquals("..domain..", rule.sourcePackagePattern)
        assertEquals(listOf("..infrastructure.."), rule.forbiddenPackagePatterns)
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

        assertNull(ArchRuleParser.parse(source))
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

        assertNull(ArchRuleParser.parse(source))
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

    fun testResideInAnyPackageRuleIsUnsupportedForV01() {
        val source = findSingleSource(
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

        assertNull(ArchRuleParser.parse(source))
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

        assertNull(ArchRuleParser.parse(source))
    }

    private fun parseSingleRule(code: String): LiveArchRule {
        val source = findSingleSource(code)
        return ArchRuleParser.parse(source) ?: error("Expected supported ArchUnit Lens rule")
    }

    private fun findSingleSource(code: String): ArchRuleSource {
        val file = configureJava(code)
        val sources = ArchRuleSourceFinder.findInFile(file)
        assertEquals(1, sources.size)
        return sources.single()
    }

    private fun configureJava(code: String): PsiFile = myFixture.configureByText("ArchitectureRules.java", code)
}

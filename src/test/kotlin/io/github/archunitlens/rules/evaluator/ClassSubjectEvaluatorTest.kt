package io.github.archunitlens.rules.evaluator

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.rules.ArchRuleParser
import io.github.archunitlens.rules.ArchRuleSource
import io.github.archunitlens.rules.ArchRuleSourceFinder
import io.github.archunitlens.rules.ClassMetaAnnotationRule
import io.github.archunitlens.rules.ClassNameSuffixRule
import io.github.archunitlens.rules.InterfaceNamingRule
import io.github.archunitlens.rules.PackageDependencyBanRule

class ClassSubjectEvaluatorTest : BasePlatformTestCase() {
    fun testEvaluatesPackageAndDependencyPatterns() {
        val rule = parseRule<PackageDependencyBanRule>(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule application_should_not_depend_on_adapters =
                            noClasses().that().resideInAnyPackage("..application..", "..domain..")
                                    .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..infrastructure..");
                }
            """.trimIndent(),
        )

        assertTrue(ClassSubjectEvaluator.appliesToPackage(rule, "com.example.application.order"))
        assertTrue(ClassSubjectEvaluator.appliesToPackage(rule, "com.example.domain.order"))
        assertFalse(ClassSubjectEvaluator.appliesToPackage(rule, "com.example.presentation"))
        assertEquals("..adapter..", ClassSubjectEvaluator.matchedForbiddenDependencyPattern(rule, "com.example.adapter.HttpClient"))
        assertEquals("..infrastructure..", ClassSubjectEvaluator.matchedForbiddenDependencyPattern(rule, "com.example.infrastructure.JpaRepository"))
        assertNull(ClassSubjectEvaluator.matchedForbiddenDependencyPattern(rule, "com.example.domain.Order"))
    }

    fun testEvaluatesClassNameAndAssignableConditionsWithoutExecutingRules() {
        myFixture.addFileToProject(
            "src/test/java/com/example/QueryMapper.java",
            """
                package com.example;

                public interface QueryMapper {
                }
            """.trimIndent(),
        )
        val suffixRule = parseRule<ClassNameSuffixRule>(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule services_should_end_with_service =
                            classes().that().resideInAPackage("..service..").should().haveSimpleNameEndingWith("Service");
                }
            """.trimIndent(),
        )
        val interfaceRule = parseRule<InterfaceNamingRule>(
            """
                import com.example.QueryMapper;
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule query_mappers_should_be_interfaces =
                            classes().that().haveSimpleNameEndingWith("QueryMapper")
                                    .should().beInterfaces().andShould().beAssignableTo(QueryMapper.class);
                }
            """.trimIndent(),
        )

        val badService = addJavaClass(
            "src/test/java/com/example/service/UserApi.java",
            "package com.example.service; class UserApi {}",
        )
        val goodMapper = addJavaClass(
            "src/test/java/com/example/UserQueryMapper.java",
            "package com.example; interface UserQueryMapper extends com.example.QueryMapper {}",
        )
        val badMapper = addJavaClass(
            "src/test/java/com/example/OtherQueryMapper.java",
            "package com.example; interface OtherQueryMapper {}",
        )

        assertTrue(ClassSubjectEvaluator.isMissingRequiredSuffix(badService, suffixRule))
        assertFalse(ClassSubjectEvaluator.isMissingInterface(goodMapper))
        assertFalse(ClassSubjectEvaluator.isMissingAssignableType(goodMapper, interfaceRule))
        assertTrue(ClassSubjectEvaluator.isMissingAssignableType(badMapper, interfaceRule))
    }

    fun testEvaluatesMetaAnnotationCondition() {
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
        val rule = parseRule<ClassMetaAnnotationRule>(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule interface_proxy_annotations_are_forbidden =
                            classes().that().areInterfaces().should().notBeMetaAnnotatedWith("com.example.Proxy");
                }
            """.trimIndent(),
        )
        val gateway = addJavaClass(
            "src/test/java/com/example/RemoteGateway.java",
            """
                package com.example;

                @com.example.Transactional
                interface RemoteGateway {
                }
            """.trimIndent(),
        )
        val annotation = gateway.modifierList!!.annotations.single()

        assertTrue(ClassSubjectEvaluator.isForbiddenMetaAnnotation(annotation, rule))
    }

    private inline fun <reified T> parseRule(code: String): T {
        val source = findSingleSource(code)
        return ArchRuleParser.discover(source)?.liveRule as? T ?: error("Expected ${T::class.simpleName}")
    }

    private fun findSingleSource(code: String): ArchRuleSource {
        val file = configureJava(code)
        val sources = ArchRuleSourceFinder.findInFile(file)
        assertEquals(1, sources.size)
        return sources.single()
    }

    private fun configureJava(code: String): PsiFile = myFixture.configureByText("ArchitectureRules.java", code)

    private fun addJavaClass(
        path: String,
        code: String,
    ) = (myFixture.addFileToProject(path, code) as PsiJavaFile).classes.single()
}

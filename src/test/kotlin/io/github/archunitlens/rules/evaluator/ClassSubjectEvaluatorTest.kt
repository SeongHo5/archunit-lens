package io.github.archunitlens.rules.evaluator

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.rules.ArchRuleParser
import io.github.archunitlens.rules.ArchRuleSource
import io.github.archunitlens.rules.ArchRuleSourceFinder
import io.github.archunitlens.rules.ClassConventionRule
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

    fun testEvaluatesStaticClassPredicateLeaves() {
        myFixture.addFileToProject(
            "src/test/java/com/example/Required.java",
            "package com.example; public @interface Required {}",
        )
        val annotated = addJavaClass(
            "src/test/java/com/example/service/AnnotatedService.java",
            "package com.example.service; @com.example.Required class AnnotatedService {}",
        )
        val plain = addJavaClass(
            "src/test/java/com/example/web/PlainController.java",
            "package com.example.web; class PlainController {}",
        )
        val implementation = addJavaClass(
            "src/test/java/com/example/web/RemoteAdapterImpl.java",
            "package com.example.web; class RemoteAdapterImpl {}",
        )
        val anInterface = addJavaClass(
            "src/test/java/com/example/Port.java",
            "package com.example; interface Port {}",
        )
        val anEnum = addJavaClass(
            "src/test/java/com/example/State.java",
            "package com.example; enum State { OPEN }",
        )

        val cases = listOf(
            PredicateCase("areAnnotatedWith(\"com.example.Required\")", annotated, "com.example.service", plain, "com.example.web"),
            PredicateCase("areNotAnnotatedWith(\"com.example.Required\")", plain, "com.example.web", annotated, "com.example.service"),
            PredicateCase("resideInAPackage(\"..service..\")", annotated, "com.example.service", plain, "com.example.web"),
            PredicateCase(
                "resideInAnyPackage(\"..service..\", \"..api..\")",
                annotated,
                "com.example.service",
                plain,
                "com.example.web",
            ),
            PredicateCase("haveSimpleNameEndingWith(\"Service\")", annotated, "com.example.service", plain, "com.example.web"),
            PredicateCase("haveSimpleNameNotEndingWith(\"Impl\")", plain, "com.example.web", implementation, "com.example.web"),
            PredicateCase("areInterfaces()", anInterface, "com.example", plain, "com.example.web"),
            PredicateCase("areNotInterfaces()", plain, "com.example.web", anInterface, "com.example"),
            PredicateCase("areEnums()", anEnum, "com.example", plain, "com.example.web"),
            PredicateCase("areNotEnums()", plain, "com.example.web", anEnum, "com.example"),
        )

        cases.forEach { case ->
            assertPredicate(case.expression, case.matchingClass, case.matchingPackage, expected = true)
            assertPredicate(case.expression, case.excludedClass, case.excludedPackage, expected = false)
        }
    }

    fun testEvaluatesAndShouldViolationsIndependentlyInSourceOrder() {
        val rule = parseRule<ClassConventionRule>(
            classConventionRule(
                "resideInAPackage(\"..mapper..\")",
                "beInterfaces().andShould().haveSimpleNameEndingWith(\"Mapper\").andShould().beAnnotatedWith(\"com.example.Mapper\")",
            ),
        )
        val broken = addJavaClass(
            "src/test/java/com/example/mapper/BrokenAdapter.java",
            "package com.example.mapper; class BrokenAdapter {}",
        )
        val partial = addJavaClass(
            "src/test/java/com/example/mapper/PartialAdapter.java",
            "package com.example.mapper; interface PartialAdapter {}",
        )

        assertEquals(
            listOf(
                ClassConditionViolation.MustBeInterface,
                ClassConditionViolation.MissingSuffix("Mapper"),
                ClassConditionViolation.MissingAnnotation("com.example.Mapper"),
            ),
            ClassSubjectEvaluator.violations(rule, broken, "com.example.mapper"),
        )
        assertEquals(
            listOf(
                ClassConditionViolation.MissingSuffix("Mapper"),
                ClassConditionViolation.MissingAnnotation("com.example.Mapper"),
            ),
            ClassSubjectEvaluator.violations(rule, partial, "com.example.mapper"),
        )
    }

    fun testEvaluatesEveryClassConditionWithCompliantAndViolatingTargets() {
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
        val annotatedRequired = addJavaClass(
            "src/test/java/com/example/AnnotatedRequired.java",
            "package com.example; @com.example.Required class AnnotatedRequired {}",
        )
        val plain = addJavaClass(
            "src/test/java/com/example/Plain.java",
            "package com.example; class Plain {}",
        )
        val annotatedForbidden = addJavaClass(
            "src/test/java/com/example/AnnotatedForbidden.java",
            "package com.example; @com.example.Forbidden class AnnotatedForbidden {}",
        )
        val service = addJavaClass(
            "src/test/java/com/example/OrderService.java",
            "package com.example; class OrderService {}",
        )
        val serviceImpl = addJavaClass(
            "src/test/java/com/example/OrderServiceImpl.java",
            "package com.example; class OrderServiceImpl {}",
        )
        val port = addJavaClass(
            "src/test/java/com/example/OrderPort.java",
            "package com.example; interface OrderPort {}",
        )
        val state = addJavaClass(
            "src/test/java/com/example/OrderState.java",
            "package com.example; enum OrderState { OPEN }",
        )
        val subtype = addJavaClass(
            "src/test/java/com/example/Subtype.java",
            "package com.example; class Subtype extends com.example.Base {}",
        )

        assertCondition(
            "beAnnotatedWith(\"com.example.Required\")",
            annotatedRequired,
            plain,
            ClassConditionViolation.MissingAnnotation("com.example.Required"),
        )
        assertCondition(
            "notBeAnnotatedWith(\"com.example.Forbidden\")",
            plain,
            annotatedForbidden,
            ClassConditionViolation.ForbiddenAnnotation("com.example.Forbidden"),
        )
        assertCondition(
            "resideInAPackage(\"..service..\")",
            plain,
            plain,
            ClassConditionViolation.OutsidePackages(listOf("..service..")),
            compliantPackage = "com.example.service",
            violatingPackage = "com.example.web",
        )
        assertCondition(
            "resideInAnyPackage(\"..service..\", \"..api..\")",
            plain,
            plain,
            ClassConditionViolation.OutsidePackages(listOf("..service..", "..api..")),
            compliantPackage = "com.example.api",
            violatingPackage = "com.example.web",
        )
        assertCondition(
            "haveSimpleNameEndingWith(\"Service\")",
            service,
            plain,
            ClassConditionViolation.MissingSuffix("Service"),
        )
        assertCondition(
            "haveSimpleNameNotEndingWith(\"Impl\")",
            service,
            serviceImpl,
            ClassConditionViolation.ForbiddenSuffix("Impl"),
        )
        assertCondition("beInterfaces()", port, plain, ClassConditionViolation.MustBeInterface)
        assertCondition("notBeInterfaces()", plain, port, ClassConditionViolation.MustNotBeInterface)
        assertCondition("beEnums()", state, plain, ClassConditionViolation.MustBeEnum)
        assertCondition("notBeEnums()", plain, state, ClassConditionViolation.MustNotBeEnum)
        assertCondition(
            "beAssignableTo(\"com.example.Base\")",
            subtype,
            plain,
            ClassConditionViolation.MissingAssignableType("com.example.Base"),
        )
    }

    private fun assertCondition(
        condition: String,
        compliant: com.intellij.psi.PsiClass,
        violating: com.intellij.psi.PsiClass,
        expectedViolation: ClassConditionViolation,
        compliantPackage: String = "com.example",
        violatingPackage: String = "com.example",
    ) {
        val rule = parseRule<ClassConventionRule>(classConventionRule("areNotEnums()", condition))
        val compliantViolations = ClassSubjectEvaluator.violations(rule, compliant, compliantPackage)
        assertTrue("$condition compliant violations: $compliantViolations", compliantViolations.isEmpty())
        assertEquals(
            listOf(expectedViolation),
            ClassSubjectEvaluator.violations(rule, violating, violatingPackage),
        )
    }

    private fun assertPredicate(
        predicate: String,
        aClass: com.intellij.psi.PsiClass,
        packageName: String,
        expected: Boolean,
    ) {
        val rule = parseRule<ClassConventionRule>(classConventionRule(predicate, "beEnums()"))
        assertEquals(expected, ClassSubjectEvaluator.matches(rule, aClass, packageName))
    }

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

    private data class PredicateCase(
        val expression: String,
        val matchingClass: PsiClass,
        val matchingPackage: String,
        val excludedClass: PsiClass,
        val excludedPackage: String,
    )
}

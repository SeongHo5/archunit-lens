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
import io.github.archunitlens.rules.MethodMetaAnnotationRule
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

    fun testEvaluatesClassMetaAnnotationConditionRecursively() {
        addMetaAnnotationGraph()
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

                @com.example.Proxy
                @com.example.Transactional
                @com.example.ComposedTransactional
                @com.example.DeepComposedTransactional
                @com.example.CyclicProxyA
                @com.example.CyclicUnrelatedA
                @com.example.Unrelated
                @com.example.missing.Unresolved
                interface RemoteGateway {
                }
            """.trimIndent(),
        )

        assertMetaAnnotationResults(gateway.modifierList!!.annotations) { annotation ->
            ClassSubjectEvaluator.isForbiddenMetaAnnotation(annotation, rule)
        }
    }

    fun testEvaluatesMethodMetaAnnotationConditionRecursively() {
        addMetaAnnotationGraph()
        val rule = parseRule<MethodMetaAnnotationRule>(
            """
                import com.tngtech.archunit.junit.ArchTest;
                import com.tngtech.archunit.lang.ArchRule;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

                class ArchitectureRules {
                    @ArchTest
                    static final ArchRule interface_method_proxy_annotations_are_forbidden =
                            methods().that().areDeclaredInClassesThat().areInterfaces()
                                    .should().notBeMetaAnnotatedWith("com.example.Proxy");
                }
            """.trimIndent(),
        )
        val gateway = addJavaClass(
            "src/test/java/com/example/RemoteGateway.java",
            """
                package com.example;

                interface RemoteGateway {
                    @com.example.Proxy
                    @com.example.Transactional
                    @com.example.ComposedTransactional
                    @com.example.DeepComposedTransactional
                    @com.example.CyclicProxyA
                    @com.example.CyclicUnrelatedA
                    @com.example.Unrelated
                    @com.example.missing.Unresolved
                    void execute();
                }
            """.trimIndent(),
        )

        assertMetaAnnotationResults(gateway.methods.single().modifierList.annotations) { annotation ->
            ClassSubjectEvaluator.isForbiddenMetaAnnotation(annotation, rule)
        }
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

    fun testGenericMetaClassFactsSkipUnresolvedCandidateAnnotations() {
        addAnnotation("Transactional")
        val unresolvedAnnotationClass = addJavaClass(
            "src/test/java/com/example/UnresolvedAnnotationCandidate.java",
            "package com.example; @com.example.missing.Unknown class UnresolvedAnnotationCandidate {}",
        )

        val negativePredicateRule = parseRule<ClassConventionRule>(
            classConventionRule(
                "areNotMetaAnnotatedWith(\"com.example.Transactional\")",
                "beRecords()",
            ),
        )
        val positiveConditionRule = parseRule<ClassConventionRule>(
            classConventionRule(
                "areNotEnums()",
                "beMetaAnnotatedWith(\"com.example.Transactional\")",
            ),
        )

        assertFalse(ClassSubjectEvaluator.matches(negativePredicateRule, unresolvedAnnotationClass, "com.example"))
        assertTrue(ClassSubjectEvaluator.violations(positiveConditionRule, unresolvedAnnotationClass, "com.example").isEmpty())
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

    fun testEvaluatesRecordModifierAndMetaAnnotationClassFacts() {
        addAnnotation("Transactional")
        addAnnotation("ComposedTransactional", "@com.example.Transactional")
        addAnnotation("DeepComposedTransactional", "@com.example.ComposedTransactional")
        myFixture.addFileToProject(
            "src/test/java/com/tngtech/archunit/core/domain/JavaModifier.java",
            "package com.tngtech.archunit.core.domain; public enum JavaModifier { FINAL }",
        )
        val directlyAnnotatedClass = addJavaClass(
            "src/test/java/com/example/DirectlyAnnotatedClass.java",
            "package com.example; @com.example.Transactional public class DirectlyAnnotatedClass {}",
        )
        val annotatedRecord = addJavaClass(
            "src/test/java/com/example/AnnotatedRecord.java",
            "package com.example; @com.example.DeepComposedTransactional public record AnnotatedRecord() {}",
        )
        val plainClass = addJavaClass(
            "src/test/java/com/example/PlainClass.java",
            "package com.example; public class PlainClass {}",
        )
        val finalClass = addJavaClass(
            "src/test/java/com/example/FinalClass.java",
            "package com.example; public final class FinalClass {}",
        )

        assertPredicate("areRecords()", annotatedRecord, "com.example", expected = true)
        assertPredicate("areRecords()", plainClass, "com.example", expected = false)
        assertPredicate(
            "areMetaAnnotatedWith(\"com.example.Transactional\")",
            directlyAnnotatedClass,
            "com.example",
            expected = true,
        )
        assertPredicate("areMetaAnnotatedWith(\"com.example.Transactional\")", annotatedRecord, "com.example", expected = true)
        assertPredicate("areMetaAnnotatedWith(\"com.example.Transactional\")", plainClass, "com.example", expected = false)
        assertPredicate(
            "areNotMetaAnnotatedWith(\"com.example.Transactional\")",
            directlyAnnotatedClass,
            "com.example",
            expected = false,
        )
        assertCondition("beRecords()", annotatedRecord, plainClass, ClassConditionViolation.MustBeRecord)
        assertCondition("notBeRecords()", plainClass, annotatedRecord, ClassConditionViolation.MustNotBeRecord)
        assertCondition(
            "haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)",
            finalClass,
            plainClass,
            ClassConditionViolation.MissingModifier(io.github.archunitlens.rules.ClassModifier.FINAL),
        )
        assertCondition(
            "notHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)",
            plainClass,
            finalClass,
            ClassConditionViolation.ForbiddenModifier(io.github.archunitlens.rules.ClassModifier.FINAL),
        )
        assertCondition(
            "beMetaAnnotatedWith(\"com.example.Transactional\")",
            annotatedRecord,
            plainClass,
            ClassConditionViolation.MissingMetaAnnotation("com.example.Transactional"),
        )
        assertCondition(
            "notBeMetaAnnotatedWith(\"com.example.Transactional\")",
            plainClass,
            directlyAnnotatedClass,
            ClassConditionViolation.ForbiddenMetaAnnotation("com.example.Transactional"),
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

    private fun addMetaAnnotationGraph() {
        addAnnotation("Proxy")
        addAnnotation("Transactional", "@com.example.Proxy")
        addAnnotation("ComposedTransactional", "@com.example.Transactional")
        addAnnotation("DeepComposedTransactional", "@com.example.ComposedTransactional")
        addAnnotation("CyclicProxyA", "@com.example.CyclicProxyB")
        addAnnotation("CyclicProxyB", "@com.example.CyclicProxyA\n@com.example.Proxy")
        addAnnotation("CyclicUnrelatedA", "@com.example.CyclicUnrelatedB")
        addAnnotation("CyclicUnrelatedB", "@com.example.CyclicUnrelatedA")
        addAnnotation("Unrelated")
    }

    private fun addAnnotation(
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

    private fun assertMetaAnnotationResults(
        annotations: Array<com.intellij.psi.PsiAnnotation>,
        matches: (com.intellij.psi.PsiAnnotation) -> Boolean,
    ) {
        val expected = mapOf(
            "Proxy" to true,
            "Transactional" to true,
            "ComposedTransactional" to true,
            "DeepComposedTransactional" to true,
            "CyclicProxyA" to true,
            "CyclicUnrelatedA" to false,
            "Unrelated" to false,
            "Unresolved" to false,
        )

        annotations.forEach { annotation ->
            val simpleName = annotation.nameReferenceElement?.referenceName ?: error("Expected annotation name")
            assertEquals(simpleName, expected.getValue(simpleName), matches(annotation))
        }
    }

    private data class PredicateCase(
        val expression: String,
        val matchingClass: PsiClass,
        val matchingPackage: String,
        val excludedClass: PsiClass,
        val excludedPackage: String,
    )
}

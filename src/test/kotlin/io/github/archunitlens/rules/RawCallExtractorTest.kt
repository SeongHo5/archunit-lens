package io.github.archunitlens.rules

import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RawCallExtractorTest : BasePlatformTestCase() {
    fun testExtractsCallsInSourceOrderWithLiteralArguments() {
        val calls = extractCalls(
            """
                import com.example.QueryMapper;
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    Object rule = classes()
                            .that()
                            .haveSimpleNameEndingWith("QueryMapper")
                            .should()
                            .beAssignableTo(QueryMapper.class)
                            .because("Query mappers expose query mapping only.");
                }
            """.trimIndent(),
        )

        assertEquals(
            listOf("classes", "that", "haveSimpleNameEndingWith", "should", "beAssignableTo", "because"),
            calls.map { it.name },
        )
        assertEquals(listOf("QueryMapper"), calls[2].stringArgs)
        assertEquals(listOf("QueryMapper"), calls[4].classLiteralArgs)
        assertEquals(listOf("Query mappers expose query mapping only."), calls[5].stringArgs)
    }

    fun testExtractsNestedMemberPredicateCallsInOrder() {
        val calls = extractCalls(
            """
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

                class ArchitectureRules {
                    Object rule = methods()
                            .that()
                            .areDeclaredInClassesThat()
                            .areInterfaces()
                            .should()
                            .notBeMetaAnnotatedWith("com.example.Proxy");
                }
            """.trimIndent(),
        )

        assertEquals(
            listOf("methods", "that", "areDeclaredInClassesThat", "areInterfaces", "should", "notBeMetaAnnotatedWith"),
            calls.map { it.name },
        )
        assertEquals(listOf("com.example.Proxy"), calls.last().stringArgs)
    }

    fun testPreservesOrderedDynamicArgumentsWithoutFlatteningNestedCalls() {
        val calls = extractCalls(
            """
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    String packagePattern = "..service..";
                    Object rule = classes()
                            .that()
                            .resideInAnyPackage("..api..", packagePattern, helperPackage())
                            .should()
                            .beAnnotatedWith(annotationType());
                }
            """.trimIndent(),
        )

        assertEquals(
            listOf("classes", "that", "resideInAnyPackage", "should", "beAnnotatedWith"),
            calls.map { it.name },
        )
        assertEquals(3, calls[2].arguments.size)
        assertEquals(RawArgument.StringLiteral(0, "..api.."), calls[2].arguments[0])
        assertEquals(RawArgument.Reference(1, "packagePattern"), calls[2].arguments[1])
        assertEquals(RawArgument.NestedCall(2, "helperPackage"), calls[2].arguments[2])
        assertEquals(listOf(RawArgument.NestedCall(0, "annotationType")), calls[4].arguments)
    }

    fun testClassifiesLambdaAndCustomConditionWithoutWalkingTheirBodies() {
        val calls = extractCalls(
            """
                import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

                class ArchitectureRules {
                    Object rule = classes().should(item -> item.getName().endsWith("Service"));
                }
            """.trimIndent(),
        )

        assertEquals(listOf("classes", "should"), calls.map { it.name })
        assertEquals(listOf(RawArgument.Lambda(0)), calls.last().arguments)
    }

    private fun extractCalls(code: String): List<RawCall> {
        val file = myFixture.configureByText("ArchitectureRules.java", code)
        return RawCallExtractor.from(file.assignmentInitializer())
    }

    private fun PsiFile.assignmentInitializer() = PsiTreeUtil.findChildrenOfType(this, PsiField::class.java)
        .firstOrNull { it.name == "rule" }
        ?.initializer
        ?: error("Expected field initializer")
}

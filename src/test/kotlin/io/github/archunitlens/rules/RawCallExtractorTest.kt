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

    private fun extractCalls(code: String): List<RawCall> {
        val file = myFixture.configureByText("ArchitectureRules.java", code)
        return RawCallExtractor.from(file.assignmentInitializer())
    }

    private fun PsiFile.assignmentInitializer() = text
        .let { PsiTreeUtil.findChildOfType(this, PsiField::class.java) }
        ?.initializer
        ?: error("Expected field initializer")
}

package io.github.archunitlens.rules.evaluator

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.archunitlens.rules.ClassConventionRule
import io.github.archunitlens.rules.ClassMetaAnnotationRule
import io.github.archunitlens.rules.ConditionExpr
import io.github.archunitlens.rules.MemberConditionExpr
import io.github.archunitlens.rules.MemberConventionRule
import io.github.archunitlens.rules.MemberPredicateExpr
import io.github.archunitlens.rules.MemberSubjectKind
import io.github.archunitlens.rules.MethodMetaAnnotationRule
import io.github.archunitlens.rules.PredicateExpr
import io.github.archunitlens.rules.RulePolarity
import java.nio.file.Path

class AnnotationRetentionEvaluatorTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = Path.of("src/test/testData").toAbsolutePath().toString()

    fun testMetaAnnotationRetentionAtAppliedAndIntermediateEdges() {
        val classes = annotationClasses()
        val policies = listOf("Source", "Class", "Runtime", "Default")
        policies.forEach { applied ->
            policies.forEach { intermediate ->
                val target = classes.getValue("${applied}Via${intermediate}Target")
                assertFacts(target, applied != "Source" && intermediate != "Source")
            }
        }
        assertFacts(classes.getValue("DirectTarget"), true)
        assertFacts(classes.getValue("SourceTarget"), false, "com.example.SourceMarker")
        assertFacts(classes.getValue("QualifiedSourceTarget"), false)
        assertFacts(classes.getValue("CustomRetentionTarget"), true)
    }

    fun testMetaAnnotationRetentionWithCyclesAndUnresolvedPaths() {
        val classes = annotationClasses()
        assertFacts(classes.getValue("CycleTarget"), true)
        assertFacts(classes.getValue("UnrelatedCycleTarget"), false)
        assertFacts(classes.getValue("SourceCycleTarget"), false)
        assertFacts(classes.getValue("UnresolvedTarget"), null)
        assertFacts(classes.getValue("SourceUnresolvedTarget"), false)
        assertFacts(classes.getValue("UnresolvedRetentionTarget"), null)
        assertFacts(classes.getValue("MissingRetentionValueTarget"), null)
        assertFacts(classes.getValue("RetainedBranchTarget"), true)
        assertFacts(classes.getValue("ResolvedBranchTarget"), true)
    }

    fun testUnresolvedRetentionDeclarationFailsClosed() {
        val classes = annotationClasses(withRetentionDeclarations = false)
        assertFacts(classes.getValue("DirectTarget"), null)
        assertFacts(classes.getValue("SourceViaRuntimeTarget"), null)
    }

    private fun annotationClasses(withRetentionDeclarations: Boolean = true): Map<String, PsiClass> {
        if (withRetentionDeclarations) {
            myFixture.copyFileToProject("javaSources/annotationRetention/Retention.java", "src/java/lang/annotation/Retention.java")
            myFixture.copyFileToProject("javaSources/annotationRetention/RetentionPolicy.java", "src/java/lang/annotation/RetentionPolicy.java")
        }
        val file = myFixture.copyFileToProject("javaSources/annotationRetention/Annotations.java", "src/com/example/Annotations.java")
        return (myFixture.psiManager.findFile(file) as PsiJavaFile).classes.associateBy { it.name!! }
    }

    private fun assertFacts(target: PsiClass, expected: Boolean?, qualifiedName: String = "com.example.Marker") {
        val pointer = SmartPointerManager.createPointer(target)
        for (required in listOf(true, false)) {
            val predicate = PredicateExpr.AreMetaAnnotatedWith(qualifiedName, required)
            assertEquals(target.name, expected?.let { it == required }, ClassSubjectEvaluator.matchesPredicate(target, "com.example", predicate))
            val rule = ClassConventionRule("retention", predicate, ConditionExpr.BeMetaAnnotatedWith(qualifiedName, required), pointer)
            assertEquals(target.name, expected != null && expected == required, ClassSubjectEvaluator.matches(rule, target, "com.example"))
            val violations = ClassSubjectEvaluator.violations(rule, target, "com.example")
            assertEquals(target.name, if (expected == null || expected == required) 0 else 1, violations.size)
        }
        val classRule = ClassMetaAnnotationRule("retention", qualifiedName, pointer)
        assertEquals(target.name, expected == true, ClassSubjectEvaluator.isForbiddenMetaAnnotation(target.modifierList!!.annotations.single(), classRule))
        val methodRule = MethodMetaAnnotationRule("retention", qualifiedName, pointer)
        val method = target.methods.single { !it.isConstructor }
        assertEquals(target.name, expected == true, ClassSubjectEvaluator.isForbiddenMetaAnnotation(method.modifierList.annotations.single(), methodRule))
        assertMemberFacts(target.fields.single(), MemberSubjectKind.Fields, expected, qualifiedName)
        assertMemberFacts(method, MemberSubjectKind.Methods, expected, qualifiedName)
        assertMemberFacts(target.constructors.single(), MemberSubjectKind.Constructors, expected, qualifiedName)
    }

    private fun assertMemberFacts(member: PsiMember, subject: MemberSubjectKind, expected: Boolean?, qualifiedName: String) {
        val pointer = SmartPointerManager.createPointer(member)
        val predicate = MemberPredicateExpr.IsAnnotatedWith(qualifiedName, metaAnnotated = true)
        val selected = MemberConventionRule("retention", subject, predicate, MemberConditionExpr.BePrivate, sourcePointer = pointer)
        assertEquals(member.name, expected == true, MemberSubjectEvaluator.matches(selected, member, "com.example"))
        val declaredIn = selected.copy(predicate = MemberPredicateExpr.DeclaredInClasses(PredicateExpr.AreMetaAnnotatedWith(qualifiedName, true)))
        assertEquals(member.name, expected == true, MemberSubjectEvaluator.matches(declaredIn, member, "com.example"))
        for (required in listOf(true, false)) {
            for (polarity in listOf(RulePolarity.POSITIVE, RulePolarity.NEGATIVE)) {
                val rule = selected.copy(
                    predicate = MemberPredicateExpr.All,
                    condition = MemberConditionExpr.BeAnnotatedWith(qualifiedName, metaAnnotated = true, required = required),
                    polarity = polarity,
                )
                val violated = expected != null && ((expected == required) == (polarity == RulePolarity.NEGATIVE))
                assertEquals(member.name, if (violated) 1 else 0, MemberSubjectEvaluator.violations(rule, member).size)
            }
        }
        val annotation = (member as PsiModifierListOwner).modifierList!!.annotations.single()
        assertEquals(member.name, expected, MetaAnnotationFacts.matches(annotation, qualifiedName))
    }
}

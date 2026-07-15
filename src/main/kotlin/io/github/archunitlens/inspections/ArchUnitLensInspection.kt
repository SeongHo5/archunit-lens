package io.github.archunitlens.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList
import com.intellij.psi.util.PsiTreeUtil
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.AnnotationExclusivityRule
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.ClassConventionRule
import io.github.archunitlens.rules.ClassMetaAnnotationRule
import io.github.archunitlens.rules.ClassNameSuffixRule
import io.github.archunitlens.rules.ConditionExpr
import io.github.archunitlens.rules.ForbiddenAnnotationRule
import io.github.archunitlens.rules.InterfaceNamingRule
import io.github.archunitlens.rules.MethodMetaAnnotationRule
import io.github.archunitlens.rules.PackageDependencyBanRule
import io.github.archunitlens.rules.PredicateExpr
import io.github.archunitlens.rules.evaluator.ClassSubjectEvaluator
import io.github.archunitlens.settings.ArchUnitLensSettings

/**
 * Reports supported ArchUnit rule violations directly in Java source files.
 *
 * This inspection intentionally handles only statically understood rule subsets.
 * ArchUnit tests remain the source of truth; warnings here are early feedback.
 */
class ArchUnitLensInspection : LocalInspectionTool() {
    override fun getShortName() = "ArchUnitLens"

    override fun getDisplayName() = ArchUnitLensBundle.message("inspection.display.name")

    override fun getGroupDisplayName() = ArchUnitLensBundle.message("inspection.group.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val javaFile = holder.file as? PsiJavaFile ?: return PsiElementVisitor.EMPTY_VISITOR
        val packageName = javaFile.packageName
        val settings = service<ArchUnitLensSettings>().state
        val rules = holder.project
            .service<ArchRuleProjectService>()
            .rulesForPackage(packageName)
            .filter { it.isEnabledBy(settings) }

        val dependencyRules = rules
            .filterIsInstance<PackageDependencyBanRule>()
            .filter { ClassSubjectEvaluator.appliesToPackage(it, packageName) }
        val forbiddenExplicitImports: Set<String> = javaFile.importList
            ?.allImportStatements
            ?.asSequence()
            ?.filterIsInstance<PsiImportStatement>()
            ?.filterNot { it.isOnDemand }
            ?.mapNotNull { it.qualifiedName }
            ?.filter { importedName -> dependencyRules.any { ClassSubjectEvaluator.matchedForbiddenDependencyPattern(it, importedName) != null } }
            ?.toSet()
            .orEmpty()
        val suffixRules = rules
            .filterIsInstance<ClassNameSuffixRule>()
            .filter { ClassSubjectEvaluator.appliesToPackage(it, packageName) }
        val annotationRules = rules
            .filterIsInstance<ForbiddenAnnotationRule>()
            .filter { ClassSubjectEvaluator.appliesToPackage(it, packageName) }
        val annotationExclusivityRules = rules
            .filterIsInstance<AnnotationExclusivityRule>()
            .filter { it.analyzeScope.includes(packageName) }
        val interfaceNamingRules = rules
            .filterIsInstance<InterfaceNamingRule>()
            .filter { it.analyzeScope.includes(packageName) }
        val classMetaAnnotationRules = rules
            .filterIsInstance<ClassMetaAnnotationRule>()
            .filter { it.analyzeScope.includes(packageName) }
        val methodMetaAnnotationRules = rules
            .filterIsInstance<MethodMetaAnnotationRule>()
            .filter { it.analyzeScope.includes(packageName) }
        val classConventionRules = rules
            .filterIsInstance<ClassConventionRule>()
            .filter { it.analyzeScope.includes(packageName) }

        return object : JavaElementVisitor() {
            override fun visitImportStatement(statement: PsiImportStatement) {
                if (statement.isOnDemand) return
                val importedName = statement.qualifiedName ?: return
                dependencyRules
                    .firstNotNullOfOrNull { rule ->
                        ClassSubjectEvaluator.matchedForbiddenDependencyPattern(rule, importedName)?.let { pattern ->
                            ForbiddenDependencyMatch(rule, importedName, pattern, "import")
                        }
                    }
                    ?.let { match ->
                        val violation = ArchUnitViolation.ForbiddenDependency(
                            rule = match.rule,
                            targetQualifiedName = match.targetQualifiedName,
                            forbiddenPackagePattern = match.forbiddenPackagePattern,
                            referenceKind = match.referenceKind,
                        )
                        holder.registerProblem(
                            statement,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
            }

            override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                if (PsiTreeUtil.getParentOfType(reference, PsiImportStatement::class.java) != null) return
                val targetClass = reference.resolve() as? PsiClass ?: return
                val targetQualifiedName = targetClass.qualifiedName ?: return
                if (targetQualifiedName in forbiddenExplicitImports) return

                dependencyRules
                    .firstNotNullOfOrNull { rule ->
                        ClassSubjectEvaluator.matchedForbiddenDependencyPattern(rule, targetQualifiedName)?.let { pattern ->
                            ForbiddenDependencyMatch(rule, targetQualifiedName, pattern, "reference")
                        }
                    }
                    ?.let { match ->
                        val violation = ArchUnitViolation.ForbiddenDependency(
                            rule = match.rule,
                            targetQualifiedName = match.targetQualifiedName,
                            forbiddenPackagePattern = match.forbiddenPackagePattern,
                            referenceKind = match.referenceKind,
                        )
                        holder.registerProblem(
                            reference,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
            }

            override fun visitClass(aClass: PsiClass) {
                val nameIdentifier = aClass.nameIdentifier ?: return
                val className = aClass.name ?: return
                classConventionRules
                    .filter { ClassSubjectEvaluator.matches(it, aClass, packageName) }
                    .forEach { rule ->
                        ClassSubjectEvaluator.violations(rule, aClass, packageName).forEach { detail ->
                            val violation = ArchUnitViolation.ClassConvention(rule, detail)
                            holder.registerProblem(
                                nameIdentifier,
                                violation.problemMessage(),
                                *violation.quickFixes(),
                            )
                        }
                    }
                suffixRules
                    .firstOrNull { ClassSubjectEvaluator.isMissingRequiredSuffix(aClass, it) }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.MissingClassNameSuffix(rule, rule.requiredSuffix)
                        holder.registerProblem(
                            nameIdentifier,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
                interfaceNamingRules
                    .filter { className.endsWith(it.requiredSuffix) }
                    .firstOrNull { ClassSubjectEvaluator.isMissingInterface(aClass) }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.MissingInterface(rule)
                        holder.registerProblem(
                            nameIdentifier,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
                interfaceNamingRules
                    .filter {
                        aClass.isInterface &&
                            className.endsWith(it.requiredSuffix)
                    }
                    .firstOrNull { rule -> ClassSubjectEvaluator.isMissingAssignableType(aClass, rule) }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.MissingAssignableType(rule, rule.assignableToQualifiedName)
                        holder.registerProblem(
                            nameIdentifier,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
            }

            override fun visitAnnotation(annotation: PsiAnnotation) {
                val annotationOwner = annotation.owner as? PsiModifierList ?: return
                val annotatedClass = annotationOwner.parent as? PsiClass ?: return
                val annotationName = annotation.qualifiedName ?: return
                annotationRules
                    .firstOrNull { ClassSubjectEvaluator.isForbiddenAnnotation(annotation, it) }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.ForbiddenAnnotation(rule, annotationName)
                        holder.registerProblem(
                            annotation,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
                annotationExclusivityRules
                    .firstOrNull { rule -> ClassSubjectEvaluator.isForbiddenAnnotation(annotation, rule, annotatedClass) }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.ForbiddenAnnotation(rule, annotationName)
                        holder.registerProblem(
                            annotation,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
                classMetaAnnotationRules
                    .filter { annotatedClass.isInterface }
                    .firstOrNull { rule -> ClassSubjectEvaluator.isForbiddenMetaAnnotation(annotation, rule) }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.ForbiddenAnnotation(rule, annotationName)
                        holder.registerProblem(
                            annotation,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
            }

            override fun visitMethod(method: PsiMethod) {
                if (method.containingClass?.isInterface != true) return
                method.modifierList.annotations.forEach { annotation ->
                    val annotationName = annotation.qualifiedName ?: return@forEach
                    methodMetaAnnotationRules
                        .firstOrNull { rule -> ClassSubjectEvaluator.isForbiddenMetaAnnotation(annotation, rule) }
                        ?.let { rule ->
                            val violation = ArchUnitViolation.ForbiddenAnnotation(rule, annotationName)
                            holder.registerProblem(
                                annotation,
                                violation.problemMessage(),
                                *violation.quickFixes(),
                            )
                        }
                }
            }
        }
    }
}

private data class ForbiddenDependencyMatch(
    val rule: PackageDependencyBanRule,
    val targetQualifiedName: String,
    val forbiddenPackagePattern: String,
    val referenceKind: String,
)

private fun io.github.archunitlens.rules.LiveArchRule.isEnabledBy(
    settings: io.github.archunitlens.settings.ArchUnitLensSettingsState,
): Boolean = when (this) {
    is ClassNameSuffixRule -> settings.classNamingRulesEnabled
    is ClassConventionRule -> predicate.isEnabledBy(settings) && condition.isEnabledBy(settings)
    is PackageDependencyBanRule -> settings.dependencyRulesEnabled
    is ForbiddenAnnotationRule,
    is AnnotationExclusivityRule,
    is ClassMetaAnnotationRule,
    is MethodMetaAnnotationRule,
    -> settings.annotationRulesEnabled
    is InterfaceNamingRule -> settings.interfaceRulesEnabled
}

private fun PredicateExpr.isEnabledBy(settings: io.github.archunitlens.settings.ArchUnitLensSettingsState): Boolean = when (this) {
    PredicateExpr.All -> true
    is PredicateExpr.Leaf -> false
    is PredicateExpr.AreAnnotatedWith,
    is PredicateExpr.AreNotAnnotatedWith,
    -> settings.annotationRulesEnabled
    is PredicateExpr.ResideInPackages,
    is PredicateExpr.HaveSimpleNameEndingWith,
    is PredicateExpr.HaveSimpleNameNotEndingWith,
    -> settings.classNamingRulesEnabled
    is PredicateExpr.AreInterfaces,
    is PredicateExpr.AreEnums,
    -> settings.interfaceRulesEnabled
    is PredicateExpr.And -> left.isEnabledBy(settings) && right.isEnabledBy(settings)
    is PredicateExpr.Or -> left.isEnabledBy(settings) && right.isEnabledBy(settings)
}

private fun ConditionExpr.isEnabledBy(settings: io.github.archunitlens.settings.ArchUnitLensSettingsState): Boolean = when (this) {
    is ConditionExpr.Leaf -> false
    is ConditionExpr.BeAnnotatedWith -> settings.annotationRulesEnabled
    is ConditionExpr.ResideInPackages,
    is ConditionExpr.HaveSimpleNameEndingWith,
    -> settings.classNamingRulesEnabled
    is ConditionExpr.BeInterfaces,
    is ConditionExpr.BeEnums,
    is ConditionExpr.BeAssignableTo,
    -> settings.interfaceRulesEnabled
    is ConditionExpr.And -> left.isEnabledBy(settings) && right.isEnabledBy(settings)
}

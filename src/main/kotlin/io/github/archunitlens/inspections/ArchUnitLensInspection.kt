package io.github.archunitlens.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifierList
import io.github.archunitlens.rules.AnnotationExclusivityRule
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.ClassNameSuffixRule
import io.github.archunitlens.rules.ForbiddenAnnotationRule
import io.github.archunitlens.rules.PackageDependencyBanRule
import io.github.archunitlens.rules.PackagePattern

/**
 * Reports supported ArchUnit rule violations directly in Java source files.
 *
 * This inspection intentionally handles only statically understood rule subsets.
 * ArchUnit tests remain the source of truth; warnings here are early feedback.
 */
class ArchUnitLensInspection : LocalInspectionTool() {
    override fun getShortName() = "ArchUnitLens"

    override fun getDisplayName() = "ArchUnit Lens"

    override fun getGroupDisplayName() = "Architecture"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val javaFile = holder.file as? PsiJavaFile ?: return PsiElementVisitor.EMPTY_VISITOR
        val packageName = javaFile.packageName
        val rules = holder.project.service<ArchRuleProjectService>().rules()

        val dependencyRules = rules.filterIsInstance<PackageDependencyBanRule>()
        val suffixRules = rules.filterIsInstance<ClassNameSuffixRule>()
        val annotationRules = rules.filterIsInstance<ForbiddenAnnotationRule>()
        val annotationExclusivityRules = rules.filterIsInstance<AnnotationExclusivityRule>()

        return object : JavaElementVisitor() {
            override fun visitImportStatement(statement: PsiImportStatement) {
                if (statement.isOnDemand) return
                val importedName = statement.qualifiedName ?: return
                dependencyRules
                    .filter { it.analyzeScope.includes(packageName) && PackagePattern.matches(it.sourcePackagePattern, packageName) }
                    .firstOrNull { rule ->
                        rule.forbiddenPackagePatterns.any { PackagePattern.matches(it, importedName) }
                    }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.ForbiddenDependency(rule)
                        holder.registerProblem(
                            statement,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
            }

            override fun visitClass(aClass: PsiClass) {
                val nameIdentifier = aClass.nameIdentifier ?: return
                val className = aClass.name ?: return
                suffixRules
                    .filter { it.analyzeScope.includes(packageName) && PackagePattern.matches(it.sourcePackagePattern, packageName) }
                    .firstOrNull { !className.endsWith(it.requiredSuffix) }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.MissingClassNameSuffix(rule, rule.requiredSuffix)
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
                    .filter { it.analyzeScope.includes(packageName) && PackagePattern.matches(it.sourcePackagePattern, packageName) }
                    .firstOrNull { annotationName == it.forbiddenAnnotationQualifiedName }
                    ?.let { rule ->
                        val violation = ArchUnitViolation.ForbiddenAnnotation(rule, annotationName)
                        holder.registerProblem(
                            annotation,
                            violation.problemMessage(),
                            *violation.quickFixes(),
                        )
                    }
                annotationExclusivityRules
                    .filter { it.analyzeScope.includes(packageName) }
                    .firstOrNull { rule ->
                        annotationName == rule.forbiddenAnnotationQualifiedName &&
                            annotatedClass.hasQualifiedAnnotation(rule.requiredAnnotationQualifiedName)
                    }
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

    private fun PsiClass.hasQualifiedAnnotation(qualifiedName: String): Boolean = modifierList
        ?.annotations
        ?.any { it.qualifiedName == qualifiedName } == true
}

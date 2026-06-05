package io.github.archunitlens.rules

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.SmartPsiElementPointer

/**
 * Source PSI for one candidate `@ArchTest static final ArchRule` field.
 */
data class ArchRuleSource(
    val ruleName: String,
    val fieldPointer: SmartPsiElementPointer<PsiField>,
    val initializer: PsiExpression,
    val analyzeScope: AnalyzeScope = AnalyzeScope.All,
)

/**
 * Package scope declared by `@AnalyzeClasses` on the containing ArchUnit rule class.
 */
sealed interface AnalyzeScope {
    fun includes(packageName: String): Boolean

    data object All : AnalyzeScope {
        override fun includes(packageName: String): Boolean = true
    }

    data class Packages(val packageNames: List<String>) : AnalyzeScope {
        override fun includes(packageName: String): Boolean = packageNames.any { configured ->
            packageName == configured || packageName.startsWith("$configured.")
        }
    }
}

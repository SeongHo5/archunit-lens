package io.github.archunitlens.rules

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker

/**
 * Project-level rule index backed by PSI modification counts.
 *
 * The service keeps inspection visitors cheap by caching parsed ArchUnit rules
 * until project PSI changes.
 */
@Service(Service.Level.PROJECT)
class ArchRuleProjectService(private val project: Project) {
    private var cachedModificationCount: Long = -1
    private var cachedRules: List<LiveArchRule> = emptyList()

    fun rules(): List<LiveArchRule> {
        val currentModificationCount = PsiModificationTracker.getInstance(project).modificationCount
        if (currentModificationCount == cachedModificationCount) return cachedRules

        cachedRules = collectRules()
        cachedModificationCount = currentModificationCount
        return cachedRules
    }

    private fun collectRules(): List<LiveArchRule> {
        val startedAt = System.nanoTime()
        val psiManager = PsiManager.getInstance(project)
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        var javaFileCount = 0
        var ruleSourceCount = 0
        val rules = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .asSequence()
            .filter { !projectFileIndex.isInLibrary(it) }
            .mapNotNull { psiManager.findFile(it) as? PsiJavaFile }
            .onEach { javaFileCount++ }
            .flatMap { file ->
                ArchRuleSourceFinder.findInFile(file)
                    .also { ruleSourceCount += it.size }
                    .asSequence()
            }
            .mapNotNull(ArchRuleParser::parse)
            .toList()
        val durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
        LOG.info(
            "ArchUnit Lens scan completed: javaFiles=$javaFileCount, " +
                "archRuleSources=$ruleSourceCount, supportedRules=${rules.size}, durationMs=$durationMs",
        )
        return rules
    }
}

private val LOG = Logger.getInstance(ArchRuleProjectService::class.java)

private const val NANOS_PER_MILLISECOND = 1_000_000

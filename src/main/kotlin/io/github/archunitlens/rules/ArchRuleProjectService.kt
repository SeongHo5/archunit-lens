package io.github.archunitlens.rules

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.Service
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
        val psiManager = PsiManager.getInstance(project)
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        return FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .asSequence()
            .filter { !projectFileIndex.isInLibrary(it) }
            .mapNotNull { psiManager.findFile(it) as? PsiJavaFile }
            .flatMap { ArchRuleSourceFinder.findInFile(it).asSequence() }
            .mapNotNull(ArchRuleParser::parse)
            .toList()
    }
}

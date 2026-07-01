package io.github.archunitlens.rules

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiModificationTracker
import io.github.archunitlens.settings.ArchUnitLensSettings

/**
 * Project-level rule index backed by indexed candidate lookup and per-rule-file text stamps.
 *
 * The service keeps inspection visitors cheap by caching parsed ArchUnit rules
 * until project PSI changes, then using IntelliJ's text index to avoid resolving
 * ordinary Java files to PSI during rule discovery. Only likely `@ArchTest`
 * files are inspected further, and unchanged rule candidates reuse cached
 * discoveries.
 */
@Service(Service.Level.PROJECT)
class ArchRuleProjectService(private val project: Project) {
    private var cachedModificationCount: Long = -1
    private var cachedDiscoverySettingsFingerprint: String = ""
    private var cachedDiscoveries: List<DiscoveredArchRule> = emptyList()
    private var cachedRuleFiles: Map<String, CachedRuleFileDiscoveries> = emptyMap()
    private var cachedPackageLookupModificationCount: Long = -1
    private var cachedPackageDiscoveries: Map<String, List<DiscoveredArchRule>> = emptyMap()
    private var packageLookupCacheHits = 0
    private var packageLookupCacheMisses = 0
    private var latestScanMetrics = ArchRuleScanMetrics()

    fun rulesForPackage(packageName: String): List<LiveArchRule> = discoveriesForPackage(packageName).mapNotNull { it.liveRule }

    fun discoveriesForPackage(packageName: String): List<DiscoveredArchRule> {
        discoveries()
        val currentModificationCount = PsiModificationTracker.getInstance(project).modificationCount
        clearPackageLookupCacheIfNeeded(currentModificationCount)

        cachedPackageDiscoveries[packageName]?.let { cached ->
            packageLookupCacheHits++
            updatePackageLookupMetrics()
            return cached
        }

        packageLookupCacheMisses++
        val filteredDiscoveries = cachedDiscoveries.filter { it.appliesToPackage(packageName) }
        cachedPackageDiscoveries = cachedPackageDiscoveries + (packageName to filteredDiscoveries)
        updatePackageLookupMetrics()
        return filteredDiscoveries
    }

    internal fun scanMetrics(): ArchRuleScanMetrics = latestScanMetrics

    fun discoveries(): List<DiscoveredArchRule> {
        val currentModificationCount = PsiModificationTracker.getInstance(project).modificationCount
        val settingsFingerprint = discoverySettingsFingerprint()
        if (
            currentModificationCount == cachedModificationCount &&
            settingsFingerprint == cachedDiscoverySettingsFingerprint
        ) {
            return cachedDiscoveries
        }

        clearPackageLookupCache(currentModificationCount)
        if (DumbService.isDumb(project)) {
            latestScanMetrics = latestScanMetrics.copy(
                indexingStatus = ArchRuleIndexingStatus.INDEXING,
                staleCacheFallback = cachedModificationCount >= 0,
                packageLookupCacheEntries = cachedPackageDiscoveries.size,
                packageLookupCacheHits = packageLookupCacheHits,
                packageLookupCacheMisses = packageLookupCacheMisses,
            )
            logMetrics {
                "ArchUnit Lens scan deferred during indexing: " +
                    "staleCacheFallback=${latestScanMetrics.staleCacheFallback}, " +
                    "cachedRules=${cachedDiscoveries.size}"
            }
            return cachedDiscoveries
        }

        cachedDiscoveries = collectDiscoveries()
        cachedModificationCount = currentModificationCount
        cachedDiscoverySettingsFingerprint = settingsFingerprint
        return cachedDiscoveries
    }

    private fun collectDiscoveries(): List<DiscoveredArchRule> {
        val startedAt = System.nanoTime()
        val psiManager = PsiManager.getInstance(project)
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        var indexedJavaCandidateFileCount = 0
        var candidateFileCount = 0
        var parsedCandidateFileCount = 0
        var ruleSourceCount = 0
        val nextRuleFiles = mutableMapOf<String, CachedRuleFileDiscoveries>()
        val rules = indexedArchTestJavaFiles(projectFileIndex)
            .asSequence()
            .mapNotNull { psiManager.findFile(it) as? PsiJavaFile }
            .onEach { indexedJavaCandidateFileCount++ }
            .filter { ArchRuleSourceFinder.mayContainArchRuleSources(it) }
            .flatMap { file ->
                candidateFileCount++
                val cacheKey = file.virtualFile?.path ?: file.name
                val modificationStamp = file.textHashStamp()
                val cachedFile = cachedRuleFiles[cacheKey]
                var currentRuleSourceCount = cachedFile?.ruleSourceCount ?: 0
                val ruleFileDiscoveries = if (cachedFile?.modificationStamp == modificationStamp) {
                    cachedFile.discoveries
                } else {
                    parsedCandidateFileCount++
                    val sources = ArchRuleSourceFinder.findInFile(file)
                    currentRuleSourceCount = sources.size
                    ruleSourceCount += currentRuleSourceCount
                    sources.mapNotNull(ArchRuleParser::discover)
                }
                if (cachedFile?.modificationStamp == modificationStamp) {
                    ruleSourceCount += currentRuleSourceCount
                }
                nextRuleFiles[cacheKey] = CachedRuleFileDiscoveries(
                    modificationStamp = modificationStamp,
                    ruleSourceCount = currentRuleSourceCount,
                    discoveries = ruleFileDiscoveries,
                )
                ruleFileDiscoveries.asSequence()
            }
            .toList()
        cachedRuleFiles = nextRuleFiles
        val durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
        latestScanMetrics = ArchRuleScanMetrics(
            indexedJavaCandidateFiles = indexedJavaCandidateFileCount,
            archRuleCandidateFiles = candidateFileCount,
            parsedRuleCandidateFiles = parsedCandidateFileCount,
            archRuleSources = ruleSourceCount,
            supportedRules = rules.count { it.liveRule != null },
            unsupportedRules = rules.count { it.liveRule == null },
            durationMs = durationMs,
            packageLookupCacheEntries = cachedPackageDiscoveries.size,
            packageLookupCacheHits = packageLookupCacheHits,
            packageLookupCacheMisses = packageLookupCacheMisses,
            indexingStatus = ArchRuleIndexingStatus.SMART,
            staleCacheFallback = false,
        )
        logMetrics {
            "ArchUnit Lens scan completed: indexedJavaCandidateFiles=${latestScanMetrics.indexedJavaCandidateFiles}, " +
                "archRuleCandidateFiles=${latestScanMetrics.archRuleCandidateFiles}, " +
                "parsedRuleCandidateFiles=${latestScanMetrics.parsedRuleCandidateFiles}, " +
                "archRuleSources=${latestScanMetrics.archRuleSources}, " +
                "supportedRules=${latestScanMetrics.supportedRules}, " +
                "unsupportedRules=${latestScanMetrics.unsupportedRules}, " +
                "packageLookupCacheEntries=${latestScanMetrics.packageLookupCacheEntries}, " +
                "packageLookupCacheHits=${latestScanMetrics.packageLookupCacheHits}, " +
                "packageLookupCacheMisses=${latestScanMetrics.packageLookupCacheMisses}, " +
                "indexingStatus=${latestScanMetrics.indexingStatus}, " +
                "staleCacheFallback=${latestScanMetrics.staleCacheFallback}, " +
                "durationMs=${latestScanMetrics.durationMs}"
        }
        return rules
    }

    private fun indexedArchTestJavaFiles(projectFileIndex: ProjectFileIndex): Set<VirtualFile> {
        val files = linkedSetOf<VirtualFile>()
        val excludedPathFragments = excludedPathFragments()
        PsiSearchHelper.getInstance(project).processCandidateFilesForText(
            GlobalSearchScope.projectScope(project),
            UsageSearchContext.IN_CODE,
            true,
            ARCH_TEST_WORD,
        ) { file ->
            if (
                file.fileType == JavaFileType.INSTANCE &&
                !projectFileIndex.isInLibrary(file) &&
                excludedPathFragments.none { file.path.contains(it) }
            ) {
                files += file
            }
            true
        }
        return files
    }

    private fun excludedPathFragments(): List<String> = service<ArchUnitLensSettings>()
        .state
        .excludedPathFragments
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private fun discoverySettingsFingerprint(): String = service<ArchUnitLensSettings>()
        .state
        .excludedPathFragments

    private fun logMetrics(message: () -> String) {
        if (service<ArchUnitLensSettings>().state.metricsLoggingEnabled) {
            LOG.info(message())
        }
    }

    private fun clearPackageLookupCacheIfNeeded(currentModificationCount: Long) {
        if (currentModificationCount != cachedPackageLookupModificationCount) {
            clearPackageLookupCache(currentModificationCount)
        }
    }

    private fun clearPackageLookupCache(currentModificationCount: Long) {
        cachedPackageDiscoveries = emptyMap()
        packageLookupCacheHits = 0
        packageLookupCacheMisses = 0
        cachedPackageLookupModificationCount = currentModificationCount
        updatePackageLookupMetrics()
    }

    private fun updatePackageLookupMetrics() {
        latestScanMetrics = latestScanMetrics.copy(
            packageLookupCacheEntries = cachedPackageDiscoveries.size,
            packageLookupCacheHits = packageLookupCacheHits,
            packageLookupCacheMisses = packageLookupCacheMisses,
        )
    }
}

internal data class ArchRuleScanMetrics(
    val indexedJavaCandidateFiles: Int = 0,
    val archRuleCandidateFiles: Int = 0,
    val parsedRuleCandidateFiles: Int = 0,
    val archRuleSources: Int = 0,
    val supportedRules: Int = 0,
    val unsupportedRules: Int = 0,
    val durationMs: Long = 0,
    val packageLookupCacheEntries: Int = 0,
    val packageLookupCacheHits: Int = 0,
    val packageLookupCacheMisses: Int = 0,
    val indexingStatus: ArchRuleIndexingStatus = ArchRuleIndexingStatus.SMART,
    val staleCacheFallback: Boolean = false,
)

internal enum class ArchRuleIndexingStatus {
    SMART,
    INDEXING,
}

private data class CachedRuleFileDiscoveries(
    val modificationStamp: Int,
    val ruleSourceCount: Int,
    val discoveries: List<DiscoveredArchRule>,
)

private val LOG = Logger.getInstance(ArchRuleProjectService::class.java)

private const val ARCH_TEST_WORD = "ArchTest"
private const val NANOS_PER_MILLISECOND = 1_000_000

private fun PsiJavaFile.textHashStamp(): Int = text.hashCode()

private fun DiscoveredArchRule.appliesToPackage(packageName: String): Boolean = liveRule?.appliesToPackage(packageName)
    ?: descriptor.scope.includes(packageName)

private fun LiveArchRule.appliesToPackage(packageName: String): Boolean = analyzeScope.includes(packageName) &&
    when (this) {
        is PackageDependencyBanRule -> sourcePackagePatterns.any { PackagePattern.matches(it, packageName) }
        is ClassNameSuffixRule -> PackagePattern.matches(sourcePackagePattern, packageName)
        is ForbiddenAnnotationRule -> PackagePattern.matches(sourcePackagePattern, packageName)
        is AnnotationExclusivityRule,
        is InterfaceNamingRule,
        is ClassMetaAnnotationRule,
        is MethodMetaAnnotationRule,
        -> true
    }

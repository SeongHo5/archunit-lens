package io.github.archunitlens.rules

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.util.concurrency.annotations.RequiresReadLock
import io.github.archunitlens.settings.ArchUnitLensSettings
import java.util.concurrent.atomic.AtomicReference

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
    private val cache = AtomicReference(ArchRuleCacheSnapshot())

    /**
     * Returns live rules that apply to [packageName]. The caller must hold an IntelliJ read action.
     */
    @RequiresReadLock
    fun rulesForPackage(packageName: String): List<LiveArchRule> = discoverySnapshot(packageName).discoveries.mapNotNull { it.liveRule }

    /**
     * Returns discovered rules that apply to [packageName]. The caller must hold an IntelliJ read action.
     */
    @RequiresReadLock
    fun discoveriesForPackage(packageName: String): List<DiscoveredArchRule> = discoverySnapshot(packageName).discoveries

    /**
     * Returns all discovered rules. The caller must hold an IntelliJ read action.
     */
    @RequiresReadLock
    fun discoveries(): List<DiscoveredArchRule> = discoverySnapshot().discoveries

    /**
     * Returns the most recently published immutable cache metrics without accessing PSI or indexes.
     */
    internal fun scanMetrics(): ArchRuleScanMetrics = cache.get().scanMetrics

    @RequiresReadLock
    internal fun discoverySnapshot(packageName: String? = null): ArchRuleDiscoverySnapshot {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        val snapshot = currentDiscoverySnapshot()
        return if (packageName == null) {
            snapshot.toDiscoverySnapshot()
        } else {
            packageDiscoverySnapshot(packageName)
        }
    }

    private fun currentDiscoverySnapshot(): ArchRuleCacheSnapshot {
        while (true) {
            val cachedSnapshot = cache.get()
            val currentModificationCount = PsiModificationTracker.getInstance(project).modificationCount
            val settingsFingerprint = discoverySettingsFingerprint()
            if (
                currentModificationCount == cachedSnapshot.modificationCount &&
                settingsFingerprint == cachedSnapshot.discoverySettingsFingerprint
            ) {
                return cachedSnapshot
            }

            val nextSnapshot = if (DumbService.isDumb(project)) {
                val clearedSnapshot = cachedSnapshot.clearPackageLookupCache(currentModificationCount)
                clearedSnapshot.copy(
                    scanMetrics = clearedSnapshot.scanMetrics.withPackageLookupMetrics(
                        indexingStatus = ArchRuleIndexingStatus.INDEXING,
                        staleCacheFallback = cachedSnapshot.modificationCount >= 0,
                    ),
                ).also { snapshot ->
                    logMetrics {
                        "ArchUnit Lens scan deferred during indexing: " +
                            "staleCacheFallback=${snapshot.scanMetrics.staleCacheFallback}, " +
                            "cachedRules=${snapshot.discoveries.size}"
                    }
                }
            } else {
                val discoveryResult = collectDiscoveries(
                    cachedRuleFiles = cachedSnapshot.ruleFiles,
                    excludedPathFragments = excludedPathFragments(settingsFingerprint),
                )
                ArchRuleCacheSnapshot(
                    modificationCount = currentModificationCount,
                    discoverySettingsFingerprint = settingsFingerprint,
                    discoveries = discoveryResult.discoveries,
                    ruleFiles = discoveryResult.ruleFiles,
                    packageLookupModificationCount = currentModificationCount,
                    scanMetrics = discoveryResult.scanMetrics,
                )
            }
            if (cache.compareAndSet(cachedSnapshot, nextSnapshot)) {
                return nextSnapshot
            }
        }
    }

    private fun packageDiscoverySnapshot(packageName: String): ArchRuleDiscoverySnapshot {
        while (true) {
            val cachedSnapshot = cache.get()
            val currentModificationCount = PsiModificationTracker.getInstance(project).modificationCount
            val packageCacheSnapshot = if (
                currentModificationCount == cachedSnapshot.packageLookupModificationCount
            ) {
                cachedSnapshot
            } else {
                cachedSnapshot.clearPackageLookupCache(currentModificationCount)
            }
            val cachedDiscoveries = packageCacheSnapshot.packageDiscoveries[packageName]
            val nextSnapshot = if (cachedDiscoveries != null) {
                packageCacheSnapshot.copy(
                    packageLookupCacheHits = packageCacheSnapshot.packageLookupCacheHits + 1,
                ).withUpdatedPackageLookupMetrics()
            } else {
                val filteredDiscoveries = packageCacheSnapshot.discoveries
                    .filter { it.appliesToPackage(packageName) }
                packageCacheSnapshot.copy(
                    packageDiscoveries = packageCacheSnapshot.packageDiscoveries + (packageName to filteredDiscoveries),
                    packageLookupCacheMisses = packageCacheSnapshot.packageLookupCacheMisses + 1,
                ).withUpdatedPackageLookupMetrics()
            }
            if (cache.compareAndSet(cachedSnapshot, nextSnapshot)) {
                return nextSnapshot.toDiscoverySnapshot(packageName)
            }
        }
    }

    private fun collectDiscoveries(
        cachedRuleFiles: Map<String, CachedRuleFileDiscoveries>,
        excludedPathFragments: List<String>,
    ): ArchRuleDiscoveryResult {
        val startedAt = System.nanoTime()
        val psiManager = PsiManager.getInstance(project)
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        var indexedJavaCandidateFileCount = 0
        var candidateFileCount = 0
        var parsedCandidateFileCount = 0
        var ruleSourceCount = 0
        val nextRuleFiles = mutableMapOf<String, CachedRuleFileDiscoveries>()
        val rules = indexedArchTestJavaFiles(projectFileIndex, excludedPathFragments)
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
        val durationMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND
        val scanMetrics = ArchRuleScanMetrics(
            indexedJavaCandidateFiles = indexedJavaCandidateFileCount,
            archRuleCandidateFiles = candidateFileCount,
            parsedRuleCandidateFiles = parsedCandidateFileCount,
            archRuleSources = ruleSourceCount,
            supportedRules = rules.count { it.liveRule != null },
            unsupportedRules = rules.count { it.liveRule == null },
            durationMs = durationMs,
            indexingStatus = ArchRuleIndexingStatus.SMART,
            staleCacheFallback = false,
        )
        logMetrics {
            "ArchUnit Lens scan completed: indexedJavaCandidateFiles=${scanMetrics.indexedJavaCandidateFiles}, " +
                "archRuleCandidateFiles=${scanMetrics.archRuleCandidateFiles}, " +
                "parsedRuleCandidateFiles=${scanMetrics.parsedRuleCandidateFiles}, " +
                "archRuleSources=${scanMetrics.archRuleSources}, " +
                "supportedRules=${scanMetrics.supportedRules}, " +
                "unsupportedRules=${scanMetrics.unsupportedRules}, " +
                "indexingStatus=${scanMetrics.indexingStatus}, " +
                "staleCacheFallback=${scanMetrics.staleCacheFallback}, " +
                "durationMs=${scanMetrics.durationMs}"
        }
        return ArchRuleDiscoveryResult(
            discoveries = rules,
            ruleFiles = nextRuleFiles.toMap(),
            scanMetrics = scanMetrics,
        )
    }

    private fun indexedArchTestJavaFiles(
        projectFileIndex: ProjectFileIndex,
        excludedPathFragments: List<String>,
    ): Set<VirtualFile> {
        val files = linkedSetOf<VirtualFile>()
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

    private fun excludedPathFragments(settingsFingerprint: String): List<String> = settingsFingerprint
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
}

private data class ArchRuleCacheSnapshot(
    val modificationCount: Long = -1,
    val discoverySettingsFingerprint: String = "",
    val discoveries: List<DiscoveredArchRule> = emptyList(),
    val ruleFiles: Map<String, CachedRuleFileDiscoveries> = emptyMap(),
    val packageLookupModificationCount: Long = -1,
    val packageDiscoveries: Map<String, List<DiscoveredArchRule>> = emptyMap(),
    val packageLookupCacheHits: Int = 0,
    val packageLookupCacheMisses: Int = 0,
    val scanMetrics: ArchRuleScanMetrics = ArchRuleScanMetrics(),
)

private data class ArchRuleDiscoveryResult(
    val discoveries: List<DiscoveredArchRule>,
    val ruleFiles: Map<String, CachedRuleFileDiscoveries>,
    val scanMetrics: ArchRuleScanMetrics,
)

internal data class ArchRuleDiscoverySnapshot(
    val discoveries: List<DiscoveredArchRule>,
    val scanMetrics: ArchRuleScanMetrics,
)

private fun ArchRuleCacheSnapshot.clearPackageLookupCache(currentModificationCount: Long): ArchRuleCacheSnapshot = copy(
    packageLookupModificationCount = currentModificationCount,
    packageDiscoveries = emptyMap(),
    packageLookupCacheHits = 0,
    packageLookupCacheMisses = 0,
).withUpdatedPackageLookupMetrics()

private fun ArchRuleCacheSnapshot.withUpdatedPackageLookupMetrics(): ArchRuleCacheSnapshot = copy(
    scanMetrics = scanMetrics.withPackageLookupMetrics(
        packageLookupCacheEntries = packageDiscoveries.size,
        packageLookupCacheHits = packageLookupCacheHits,
        packageLookupCacheMisses = packageLookupCacheMisses,
    ),
)

private fun ArchRuleScanMetrics.withPackageLookupMetrics(
    packageLookupCacheEntries: Int = this.packageLookupCacheEntries,
    packageLookupCacheHits: Int = this.packageLookupCacheHits,
    packageLookupCacheMisses: Int = this.packageLookupCacheMisses,
    indexingStatus: ArchRuleIndexingStatus = this.indexingStatus,
    staleCacheFallback: Boolean = this.staleCacheFallback,
): ArchRuleScanMetrics = copy(
    packageLookupCacheEntries = packageLookupCacheEntries,
    packageLookupCacheHits = packageLookupCacheHits,
    packageLookupCacheMisses = packageLookupCacheMisses,
    indexingStatus = indexingStatus,
    staleCacheFallback = staleCacheFallback,
)

private fun ArchRuleCacheSnapshot.toDiscoverySnapshot(
    packageName: String? = null,
): ArchRuleDiscoverySnapshot = ArchRuleDiscoverySnapshot(
    discoveries = packageName?.let { packageDiscoveries.getValue(it) } ?: discoveries,
    scanMetrics = scanMetrics,
)

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

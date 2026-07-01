package io.github.archunitlens.performance

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.ArchRuleScanMetrics
import java.lang.management.ManagementFactory

private const val SMOKE_METRIC_KEY = "ArchUnit Lens Performance Smoke | Total Time Execution"
private val performanceSmokeMetricsLog = Logger.getInstance(PerformanceSmokeMetrics::class.java)

/**
 * Emits the metric consumed by the IntelliJ Platform `testIdePerformance` Gradle task.
 *
 * The bundled smoke script invokes this class through `%runClassInPlugin`, keeping the metric
 * local to performance-test runs while still writing the TeamCity statistic line that the Gradle
 * task parses from `idea.log`.
 */
class PerformanceSmokeMetrics {
    /**
     * Reports elapsed IDE JVM time for the currently opened performance fixture project.
     */
    @Suppress("unused")
    fun report(project: Project) {
        val service = project.service<ArchRuleProjectService>()
        val metrics = ApplicationManager.getApplication().runReadAction<ArchRuleScanMetrics> {
            service.discoveries()
            service.scanMetrics()
        }
        val elapsedMillis = (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().startTime)
            .coerceAtLeast(0)
        val metricLine = buildTeamCityMetric(elapsedMillis)

        performanceSmokeMetricsLog.info(
            "Performance smoke scan metrics for ${project.name}: " +
                "indexedJavaCandidateFiles=${metrics.indexedJavaCandidateFiles}, " +
                "archRuleCandidateFiles=${metrics.archRuleCandidateFiles}, " +
                "parsedRuleCandidateFiles=${metrics.parsedRuleCandidateFiles}, " +
                "archRuleSources=${metrics.archRuleSources}, " +
                "packageLookupCacheEntries=${metrics.packageLookupCacheEntries}, " +
                "packageLookupCacheHits=${metrics.packageLookupCacheHits}, " +
                "packageLookupCacheMisses=${metrics.packageLookupCacheMisses}, " +
                "indexingStatus=${metrics.indexingStatus}, " +
                "staleCacheFallback=${metrics.staleCacheFallback}",
        )
        performanceSmokeMetricsLog.info("Performance smoke metric for ${project.name}: $elapsedMillis ms")
        performanceSmokeMetricsLog.info(metricLine)
        println(metricLine)
        System.out.flush()
    }

    private fun buildTeamCityMetric(elapsedMillis: Long): String = buildString {
        append("##teamcity[buildStatisticValue key='")
        append(SMOKE_METRIC_KEY)
        append("' value='")
        append(elapsedMillis)
        append("']")
    }
}

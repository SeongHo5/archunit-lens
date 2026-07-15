package io.github.archunitlens.ui

import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.AnalyzeScope
import io.github.archunitlens.rules.ArchRuleIndexingStatus
import io.github.archunitlens.rules.ArchRuleScanMetrics
import io.github.archunitlens.rules.DiscoveredArchRule
import io.github.archunitlens.rules.SubjectKind
import io.github.archunitlens.rules.SupportStatus
import io.github.archunitlens.rules.UnsupportedReason

/**
 * User-selected filters for the rule overview surface.
 */
internal data class RuleOverviewFilter(
    val showSupported: Boolean = true,
    val showUnsupported: Boolean = true,
    val searchQuery: String = "",
    val currentPackage: String? = null,
    val currentPackageOnly: Boolean = false,
    val showDiagnostics: Boolean = true,
)

/**
 * UI-ready rule overview row data resolved under the caller's read-action boundary.
 */
internal data class RuleOverviewItem(
    val discovery: DiscoveredArchRule,
    val sourceFileName: String?,
)

/**
 * Builds the textual rule overview shown in the ArchUnit Lens tool window.
 */
internal object ArchUnitLensRuleOverviewFormatter {
    fun render(
        discoveries: List<RuleOverviewItem>,
        metrics: ArchRuleScanMetrics,
        filter: RuleOverviewFilter = RuleOverviewFilter(),
    ): String = buildString {
        appendLine(ArchUnitLensBundle.message("overview.title"))
        appendLine()
        appendFilterSummary(filter)
        if (filter.showDiagnostics) {
            appendLine(
                ArchUnitLensBundle.message(
                    "overview.scan",
                    metrics.indexedJavaCandidateFiles,
                    metrics.archRuleCandidateFiles,
                    metrics.archRuleSources,
                    metrics.supportedRules,
                    metrics.unsupportedRules,
                    metrics.durationMs,
                    metrics.packageLookupCacheEntries,
                    metrics.packageLookupCacheHits,
                    metrics.packageLookupCacheMisses,
                    metrics.indexingStatus.label(),
                    metrics.staleCacheFallback.label(),
                ),
            )
        }
        appendLine()

        if (discoveries.isEmpty()) {
            appendLine(ArchUnitLensBundle.message("overview.empty.noRules"))
            appendLine(ArchUnitLensBundle.message("overview.empty.openSources"))
            return@buildString
        }

        val visibleDiscoveries = filteredDiscoveries(discoveries, filter)
        if (visibleDiscoveries.isEmpty()) {
            appendLine(ArchUnitLensBundle.message("overview.empty.filtered"))
            return@buildString
        }

        visibleDiscoveries
            .groupBy { it.discovery.groupLabel() }
            .forEach { (group, groupDiscoveries) ->
                appendLine(group)
                groupDiscoveries.forEachIndexed { index, item ->
                    append(renderRule(item, index + 1, filter.showDiagnostics))
                }
            }
    }

    fun filteredDiscoveries(
        discoveries: List<RuleOverviewItem>,
        filter: RuleOverviewFilter,
    ): List<RuleOverviewItem> = discoveries
        .filter { filter.showSupported || it.discovery.liveRule == null }
        .filter { filter.showUnsupported || it.discovery.liveRule != null }
        .filter { it.discovery.matchesSearch(filter.searchQuery) }
        .sortedWith(
            compareBy<RuleOverviewItem> { it.discovery.liveRule == null }
                .thenBy { it.discovery.descriptor.subject.label() }
                .thenBy { it.discovery.ruleName },
        )

    fun renderDetails(
        item: RuleOverviewItem,
        currentPackage: String?,
    ): String = buildString {
        append(renderRule(item, index = 1, includeDiagnostic = true))
        appendLine(
            currentPackage?.let { ArchUnitLensBundle.message("overview.currentPackage", it) }
                ?: ArchUnitLensBundle.message("overview.currentPackage.none"),
        )
    }

    private fun StringBuilder.appendFilterSummary(filter: RuleOverviewFilter) {
        appendLine(
            ArchUnitLensBundle.message(
                "overview.filter.summary",
                filter.searchQuery.ifBlank { ArchUnitLensBundle.message("overview.filter.search.empty") },
                filter.showSupported.label(),
                filter.showUnsupported.label(),
                filter.currentPackageOnly.label(),
            ),
        )
        appendLine(
            filter.currentPackage?.let { ArchUnitLensBundle.message("overview.currentPackage", it) }
                ?: ArchUnitLensBundle.message("overview.currentPackage.none"),
        )
    }

    private fun renderRule(
        item: RuleOverviewItem,
        index: Int,
        includeDiagnostic: Boolean,
    ): String = buildString {
        val discovery = item.discovery
        val descriptor = discovery.descriptor
        appendLine(ArchUnitLensBundle.message("overview.rule.index", index, discovery.ruleName))
        appendLine(indented("overview.status", descriptor.supportStatus.label()))
        appendLine(indented("overview.subject", descriptor.subject.label()))
        appendLine(indented("overview.scope", descriptor.scope.label()))
        appendLine(indented("overview.predicate", descriptor.predicate))
        appendLine(indented("overview.condition", descriptor.condition))
        descriptor.reason?.let { appendLine(indented("overview.reason", it)) }
        appendLine(indented("overview.source", item.sourceFileName ?: discovery.ruleName))
        if (includeDiagnostic) {
            appendLine(indented("overview.diagnostic", discovery.diagnostic()))
        }
        appendLine()
    }

    private fun DiscoveredArchRule.groupLabel(): String = if (liveRule != null) {
        ArchUnitLensBundle.message("overview.group.supported", descriptor.subject.label())
    } else {
        ArchUnitLensBundle.message("overview.group.unsupported", unsupportedReasonLabel().orEmpty())
    }

    private fun DiscoveredArchRule.diagnostic(): String = if (liveRule != null) {
        ArchUnitLensBundle.message("overview.diagnostic.supported")
    } else {
        ArchUnitLensBundle.message("overview.diagnostic.unsupported", unsupportedReasonLabel().orEmpty())
    }

    private fun DiscoveredArchRule.matchesSearch(searchQuery: String): Boolean {
        val query = searchQuery.trim()
        if (query.isEmpty()) return true
        val haystack = listOfNotNull(
            ruleName,
            descriptor.subject.label(),
            descriptor.scope.label(),
            descriptor.predicate.toString(),
            descriptor.condition.toString(),
            descriptor.reason,
            descriptor.supportStatus.label(),
            unsupportedReasonLabel(),
        )
        return haystack.any { it.contains(query, ignoreCase = true) }
    }

    private fun DiscoveredArchRule.unsupportedReasonLabel(): String? = when (val status = descriptor.supportStatus) {
        SupportStatus.Supported -> null
        is SupportStatus.Unsupported -> status.reason.label()
    }

    private fun SupportStatus.label(): String = when (this) {
        SupportStatus.Supported -> ArchUnitLensBundle.message("overview.status.supported")
        is SupportStatus.Unsupported -> ArchUnitLensBundle.message("overview.status.unsupported", reason.label())
    }

    private fun SubjectKind.label(): String = when (this) {
        SubjectKind.Classes -> ArchUnitLensBundle.message("overview.subject.classes")
        SubjectKind.SingleClass -> ArchUnitLensBundle.message("overview.subject.singleClass")
        SubjectKind.Members -> ArchUnitLensBundle.message("overview.subject.members")
        SubjectKind.Fields -> ArchUnitLensBundle.message("overview.subject.fields")
        SubjectKind.CodeUnits -> ArchUnitLensBundle.message("overview.subject.codeUnits")
        SubjectKind.Constructors -> ArchUnitLensBundle.message("overview.subject.constructors")
        SubjectKind.Methods -> ArchUnitLensBundle.message("overview.subject.methods")
        is SubjectKind.CustomTransformer -> ArchUnitLensBundle.message(
            "overview.subject.custom",
            description ?: ArchUnitLensBundle.message("overview.subject.unknown"),
        )
    }

    private fun AnalyzeScope.label(): String = when (this) {
        AnalyzeScope.All -> ArchUnitLensBundle.message("overview.scope.all")
        is AnalyzeScope.Packages -> ArchUnitLensBundle.message("overview.scope.packages", packageNames.joinToString(separator = ","))
    }

    private fun ArchRuleIndexingStatus.label(): String = when (this) {
        ArchRuleIndexingStatus.SMART -> ArchUnitLensBundle.message("overview.indexing.smart")
        ArchRuleIndexingStatus.INDEXING -> ArchUnitLensBundle.message("overview.indexing.indexing")
    }

    private fun Boolean.label(): String = if (this) {
        ArchUnitLensBundle.message("overview.boolean.yes")
    } else {
        ArchUnitLensBundle.message("overview.boolean.no")
    }

    private fun UnsupportedReason.label(): String = when (this) {
        UnsupportedReason.UnsupportedMultiPackageRuleShape -> ArchUnitLensBundle.message(
            "overview.unsupported.multiPackageRuleShape",
        )
        UnsupportedReason.CustomOrMetaAnnotationPredicates -> ArchUnitLensBundle.message(
            "overview.unsupported.customOrMetaAnnotationPredicates",
        )
        is UnsupportedReason.UnsupportedEntryPoint -> ArchUnitLensBundle.message("overview.unsupported.entryPoint", entryPoint)
        is UnsupportedReason.InvalidArity -> ArchUnitLensBundle.message(
            "overview.unsupported.invalidArity",
            methodName,
            expected,
            actual,
        )
        is UnsupportedReason.UnsupportedArgument -> ArchUnitLensBundle.message(
            "overview.unsupported.argument",
            methodName,
            position + 1,
            kind,
        )
        is UnsupportedReason.UnresolvedSymbol -> ArchUnitLensBundle.message(
            "overview.unsupported.unresolved",
            methodName,
            symbol,
        )
        UnsupportedReason.UnsupportedOrAmbiguousRuleChain -> ArchUnitLensBundle.message("overview.unsupported.ambiguous")
    }

    private fun indented(
        key: String,
        vararg params: Any,
    ): String = "   " + ArchUnitLensBundle.message(key, *params)
}

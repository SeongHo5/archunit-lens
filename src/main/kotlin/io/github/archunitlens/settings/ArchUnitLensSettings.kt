package io.github.archunitlens.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.messages.Topic

/**
 * Persistent user preferences for ArchUnit Lens inspections and rule overview.
 */
@Service(Service.Level.APP)
@State(
    name = "ArchUnitLensSettings",
    storages = [Storage(value = "archUnitLens.xml", roamingType = RoamingType.DISABLED)],
)
class ArchUnitLensSettings : PersistentStateComponent<ArchUnitLensSettingsState> {
    @Volatile
    private var currentState = ArchUnitLensSettingsState()

    override fun getState(): ArchUnitLensSettingsState = currentState

    override fun loadState(state: ArchUnitLensSettingsState) {
        currentState = state
    }

    internal fun update(change: (ArchUnitLensSettingsState) -> Unit) {
        val application = ApplicationManager.getApplication()
        application.assertIsDispatchThread()
        val previous = currentState
        val updated = previous.copy().also(change)
        val inspectionsChanged = previous.inspectionPreferences() != updated.inspectionPreferences()
        val discoveryChanged = previous.excludedPathFragments != updated.excludedPathFragments
        val overviewChanged = previous.overviewPreferences() != updated.overviewPreferences()
        currentState = updated
        if (inspectionsChanged || discoveryChanged) {
            ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }.forEach {
                // Keep the no-argument overload for IntelliJ 2025.2 compatibility; restart(reason) was added in 2025.3.
                // Switch to the reason overload when the minimum supported platform is raised to 253.
                DaemonCodeAnalyzer.getInstance(it).restart()
            }
        }
        if (inspectionsChanged || discoveryChanged || overviewChanged) {
            application.messageBus.syncPublisher(ARCH_UNIT_LENS_SETTINGS_CHANGED).settingsChanged()
        }
    }
}

/**
 * XML-serializable settings state. Defaults preserve the pre-settings behavior.
 */
class ArchUnitLensSettingsState {
    var classNamingRulesEnabled: Boolean = true
    var dependencyRulesEnabled: Boolean = true
    var annotationRulesEnabled: Boolean = true
    var interfaceRulesEnabled: Boolean = true
    var memberDeclarationRulesEnabled: Boolean = true
    var showSupportedRulesInOverview: Boolean = true
    var showUnsupportedRulesInOverview: Boolean = true
    var showDiagnosticsInOverview: Boolean = true
    var metricsLoggingEnabled: Boolean = true
    var excludedPathFragments: String = DEFAULT_EXCLUDED_PATH_FRAGMENTS
}

internal const val DEFAULT_EXCLUDED_PATH_FRAGMENTS = "build/generated,generated"

internal fun interface ArchUnitLensSettingsListener {
    fun settingsChanged()
}

@field:Topic.AppLevel
internal val ARCH_UNIT_LENS_SETTINGS_CHANGED = Topic.create(
    "ArchUnit Lens settings changed",
    ArchUnitLensSettingsListener::class.java,
)

private fun ArchUnitLensSettingsState.inspectionPreferences(): List<Boolean> = listOf(
    classNamingRulesEnabled,
    dependencyRulesEnabled,
    annotationRulesEnabled,
    interfaceRulesEnabled,
    memberDeclarationRulesEnabled,
)

private fun ArchUnitLensSettingsState.overviewPreferences(): List<Boolean> = listOf(
    showSupportedRulesInOverview,
    showUnsupportedRulesInOverview,
    showDiagnosticsInOverview,
)

private fun ArchUnitLensSettingsState.copy(): ArchUnitLensSettingsState = ArchUnitLensSettingsState().also {
    it.classNamingRulesEnabled = classNamingRulesEnabled
    it.dependencyRulesEnabled = dependencyRulesEnabled
    it.annotationRulesEnabled = annotationRulesEnabled
    it.interfaceRulesEnabled = interfaceRulesEnabled
    it.memberDeclarationRulesEnabled = memberDeclarationRulesEnabled
    it.showSupportedRulesInOverview = showSupportedRulesInOverview
    it.showUnsupportedRulesInOverview = showUnsupportedRulesInOverview
    it.showDiagnosticsInOverview = showDiagnosticsInOverview
    it.metricsLoggingEnabled = metricsLoggingEnabled
    it.excludedPathFragments = excludedPathFragments
}

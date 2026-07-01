package io.github.archunitlens.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent user preferences for ArchUnit Lens inspections and rule overview.
 */
@Service(Service.Level.APP)
@State(name = "ArchUnitLensSettings", storages = [Storage("archUnitLens.xml")])
class ArchUnitLensSettings : PersistentStateComponent<ArchUnitLensSettingsState> {
    private var currentState = ArchUnitLensSettingsState()

    override fun getState(): ArchUnitLensSettingsState = currentState

    override fun loadState(state: ArchUnitLensSettingsState) {
        currentState = state
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
    var showSupportedRulesInOverview: Boolean = true
    var showUnsupportedRulesInOverview: Boolean = true
    var showDiagnosticsInOverview: Boolean = true
    var metricsLoggingEnabled: Boolean = true
    var excludedPathFragments: String = DEFAULT_EXCLUDED_PATH_FRAGMENTS
}

internal const val DEFAULT_EXCLUDED_PATH_FRAGMENTS = "build/generated,generated"

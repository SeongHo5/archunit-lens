package io.github.archunitlens.settings

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ArchUnitLensSettingsTest : BasePlatformTestCase() {
    fun testDefaultsPreserveCurrentBehavior() {
        val settings = service<ArchUnitLensSettings>()
        settings.loadState(ArchUnitLensSettingsState())
        val state = settings.state

        assertTrue(state.classNamingRulesEnabled)
        assertTrue(state.dependencyRulesEnabled)
        assertTrue(state.annotationRulesEnabled)
        assertTrue(state.interfaceRulesEnabled)
        assertTrue(state.showSupportedRulesInOverview)
        assertTrue(state.showUnsupportedRulesInOverview)
        assertTrue(state.showDiagnosticsInOverview)
        assertTrue(state.metricsLoggingEnabled)
        assertEquals(DEFAULT_EXCLUDED_PATH_FRAGMENTS, state.excludedPathFragments)
    }
}

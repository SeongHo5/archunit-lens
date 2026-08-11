package io.github.archunitlens.settings

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ArchUnitLensSettingsTest : BasePlatformTestCase() {
    fun testApplicationSettingsStorageDisablesRoaming() {
        val state = ArchUnitLensSettings::class.java.getAnnotation(State::class.java)

        assertEquals(RoamingType.DISABLED, state.storages.single().roamingType)
    }

    fun testConfigurableDoesNotRetainApplicationSettingsService() {
        assertFalse(
            ArchUnitLensConfigurable::class.java.declaredFields.any {
                ArchUnitLensSettings::class.java.isAssignableFrom(it.type)
            },
        )
    }

    fun testConfigurableReadsSettingsWithoutRetainingTheService() {
        service<ArchUnitLensSettings>().loadState(ArchUnitLensSettingsState())
        val configurable = ArchUnitLensConfigurable()

        configurable.createComponent()

        assertFalse(configurable.isModified())
        configurable.disposeUIResources()
    }

    fun testDefaultsPreserveCurrentBehavior() {
        val settings = service<ArchUnitLensSettings>()
        settings.loadState(ArchUnitLensSettingsState())
        val state = settings.state

        assertTrue(state.classNamingRulesEnabled)
        assertTrue(state.dependencyRulesEnabled)
        assertTrue(state.annotationRulesEnabled)
        assertTrue(state.interfaceRulesEnabled)
        assertTrue(state.memberDeclarationRulesEnabled)
        assertTrue(state.showSupportedRulesInOverview)
        assertTrue(state.showUnsupportedRulesInOverview)
        assertTrue(state.showDiagnosticsInOverview)
        assertTrue(state.metricsLoggingEnabled)
        assertEquals(DEFAULT_EXCLUDED_PATH_FRAGMENTS, state.excludedPathFragments)
    }
}

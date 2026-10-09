package io.github.archunitlens.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.settings.ArchUnitLensConfigurable
import io.github.archunitlens.settings.ArchUnitLensSettings
import io.github.archunitlens.settings.ArchUnitLensSettingsState
import java.awt.Component
import java.awt.Container
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JTextField

class ArchUnitLensToolWindowFactoryTest : BasePlatformTestCase() {
    fun testSettingsApplyRefreshesExistingPanelsWithoutSourceEditAndSynchronizesFilters() {
        val settings = service<ArchUnitLensSettings>()
        val original = settings.state
        settings.loadState(ArchUnitLensSettingsState())
        addOverviewRule()
        val first = ArchUnitLensRuleOverviewPanel(project)
        val second = ArchUnitLensRuleOverviewPanel(project)
        val configurable = ArchUnitLensConfigurable()
        try {
            awaitRefresh()
            assertEquals(1, ruleCount(first))
            assertEquals(1, ruleCount(second))
            val component = configurable.createComponent() as JPanel
            component.components.filterIsInstance<JCheckBox>().single {
                it.text == ArchUnitLensBundle.message("settings.overview.showSupported")
            }.isSelected = false
            component.components.filterIsInstance<JCheckBox>().single {
                it.text == ArchUnitLensBundle.message("settings.overview.showDiagnostics")
            }.isSelected = false
            val metrics = project.service<ArchRuleProjectService>().scanMetrics()

            configurable.apply()
            awaitRefresh()

            assertEquals(0, ruleCount(first))
            assertEquals(0, ruleCount(second))
            assertFalse(checkbox(first, "supported").isSelected)
            assertFalse(checkbox(second, "diagnostics").isSelected)
            assertEquals(metrics, project.service<ArchRuleProjectService>().scanMetrics())

            // A stale local control must not restore unrelated preferences when another filter is clicked.
            checkbox(second, "supported").isSelected = true
            checkbox(second, "diagnostics").isSelected = true
            checkbox(second, "unsupported").doClick()
            awaitRefresh()

            assertFalse(settings.state.showSupportedRulesInOverview)
            assertFalse(settings.state.showDiagnosticsInOverview)
            assertFalse(settings.state.showUnsupportedRulesInOverview)
            assertFalse(checkbox(first, "unsupported").isSelected)

            checkbox(first, "supported").doClick()
            awaitRefresh()
            assertEquals(1, ruleCount(first))
            assertEquals(1, ruleCount(second))
            assertTrue(checkbox(second, "supported").isSelected)
        } finally {
            Disposer.dispose(first)
            Disposer.dispose(second)
            configurable.disposeUIResources()
            settings.loadState(original)
        }
    }

    fun testSettingsApplyRefreshesScanExclusionsAndDisposedPanelsStopListening() {
        val settings = service<ArchUnitLensSettings>()
        val original = settings.state
        settings.loadState(ArchUnitLensSettingsState())
        addOverviewRule()
        val panel = ArchUnitLensRuleOverviewPanel(project)
        val configurable = ArchUnitLensConfigurable()
        var panelDisposed = false
        try {
            awaitRefresh()
            assertEquals(1, ruleCount(panel))
            val component = configurable.createComponent() as JPanel
            component.components.filterIsInstance<JTextField>().single().text = "src/test/java"

            configurable.apply()
            awaitRefresh()

            assertEquals(0, ruleCount(panel))
            Disposer.dispose(panel)
            panelDisposed = true
            component.components.filterIsInstance<JCheckBox>().single {
                it.text == ArchUnitLensBundle.message("settings.overview.showSupported")
            }.isSelected = false
            configurable.apply()
            awaitRefresh()
            assertTrue(checkbox(panel, "supported").isSelected)
        } finally {
            if (!panelDisposed) Disposer.dispose(panel)
            configurable.disposeUIResources()
            settings.loadState(original)
        }
    }

    private fun addOverviewRule() {
        myFixture.addFileToProject(
            "src/test/java/com/tngtech/archunit/lang/syntax/ArchRuleDefinition.java",
            testData("stubs/settingsRefresh/ArchRuleDefinition.java"),
        )
        myFixture.addFileToProject(
            "src/test/java/ArchitectureRules.java",
            testData("archrules/classSuffixQuickFix.java"),
        )
    }

    private fun testData(path: String): String = Path.of("src/test/testData", path).toFile().readText()

    private fun awaitRefresh() {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        UIUtil.dispatchAllInvocationEvents()
    }

    private fun ruleCount(panel: JPanel): Int = descendants(panel).filterIsInstance<JBList<*>>().single().model.size

    private fun checkbox(panel: JPanel, name: String): JCheckBox = descendants(panel).filterIsInstance<JCheckBox>().single {
        it.text == ArchUnitLensBundle.message("toolwindow.filter.$name")
    }

    private fun descendants(component: Component): List<Component> = listOf(component) +
        if (component is Container) component.components.flatMap(::descendants) else emptyList()

    fun testFactoryInheritsPlatformDefaultsWithoutCompatibilityBridges() {
        assertEquals(
            setOf("createToolWindowContent"),
            ArchUnitLensToolWindowFactory::class.java.declaredMethods.map { it.name }.toSet(),
        )
    }

    fun testOverviewPanelDoesNotRetainApplicationSettingsService() {
        val panelClass = Class.forName("io.github.archunitlens.ui.ArchUnitLensRuleOverviewPanel")

        assertFalse(
            panelClass.declaredFields.any {
                ArchUnitLensSettings::class.java.isAssignableFrom(it.type)
            },
        )
    }

    fun testCurrentJavaPackageReadsPsiInsideReadAction() {
        val file = myFixture.addFileToProject(
            "src/test/java/com/example/ArchitectureRules.java",
            """
                package com.example;

                class ArchitectureRules {
                }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        val packageName = ApplicationManager.getApplication()
            .executeOnPooledThread<String> { currentJavaPackage(project) }
            .get(10, TimeUnit.SECONDS)

        assertEquals("com.example", packageName)
    }
}

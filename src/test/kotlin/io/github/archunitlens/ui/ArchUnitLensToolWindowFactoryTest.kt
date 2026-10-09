package io.github.archunitlens.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
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
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

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

    fun testCurrentFileOverviewFollowsSelectedJavaEditorWithoutManualRefresh() {
        withEditorSelectionOverview { panel, first, second ->
            checkbox(panel, "currentFile").doClick()
            awaitRefresh()
            assertEquals(listOf("package_a_rule"), ruleNames(panel))
            assertCurrentPackage(panel, "com.example.a")

            selectEditor(second)
            awaitRefresh()

            assertEquals(listOf("package_b_rule"), ruleNames(panel))
            assertCurrentPackage(panel, "com.example.b")
            assertEquals(second, FileEditorManager.getInstance(project).selectedFiles.single())
            assertTrue(first.isValid)
        }
    }

    fun testRapidJavaEditorSwitchesInvalidateEveryPreviousRefreshIncludingReturnToSameFile() {
        withEditorSelectionOverview { panel, first, second ->
            checkbox(panel, "currentFile").doClick()
            awaitRefresh()
            val generation = refreshGeneration(panel)

            // Keep all switches in the same EDT event so previous callbacks cannot run between them.
            selectEditor(second)
            selectEditor(first)
            assertEquals(generation + 2, refreshGeneration(panel))
            awaitRefresh()

            assertEquals(listOf("package_a_rule"), ruleNames(panel))
            assertCurrentPackage(panel, "com.example.a")
        }
    }

    fun testCurrentFileOverviewUsesAllRulesWithoutJavaEditorAndClearsPreviousPackage() {
        withEditorSelectionOverview { panel, first, _ ->
            val notes = myFixture.addFileToProject(
                "notes.txt",
                testData("javaSources/editorSelectionOverview/notes.txt"),
            ).virtualFile
            checkbox(panel, "currentFile").doClick()
            awaitRefresh()
            assertEquals(listOf("package_a_rule"), ruleNames(panel))

            selectEditor(notes)
            awaitRefresh()
            assertEquals(listOf("package_a_rule", "package_b_rule"), ruleNames(panel))
            assertCurrentPackage(panel, null)

            selectEditor(first)
            awaitRefresh()
            assertEquals(listOf("package_a_rule"), ruleNames(panel))
            FileEditorManager.getInstance(project).openFiles.forEach {
                FileEditorManager.getInstance(project).closeFile(it)
            }
            awaitRefresh()

            assertEmpty(FileEditorManager.getInstance(project).selectedFiles.toList())
            assertEquals(listOf("package_a_rule", "package_b_rule"), ruleNames(panel))
            assertCurrentPackage(panel, null)
        }
    }

    fun testEditorSwitchDoesNotRefreshWhenCurrentFileFilterIsOff() {
        withEditorSelectionOverview { panel, first, second ->
            val changes = observeRuleListChanges(panel)
            val generation = refreshGeneration(panel)
            val metrics = project.service<ArchRuleProjectService>().scanMetrics()

            selectEditor(second)
            selectEditor(first)
            awaitRefresh()

            assertEquals(generation, refreshGeneration(panel))
            assertEquals(0, changes())
            assertEquals(metrics, project.service<ArchRuleProjectService>().scanMetrics())
            assertEquals(listOf("package_a_rule", "package_b_rule"), ruleNames(panel))
        }
    }

    fun testDisposedCurrentFileOverviewStopsFollowingEditorSelection() {
        withEditorSelectionOverview { panel, _, second ->
            checkbox(panel, "currentFile").doClick()
            awaitRefresh()
            Disposer.dispose(panel)
            val generation = refreshGeneration(panel)
            val changes = observeRuleListChanges(panel)

            selectEditor(second)
            awaitRefresh()

            assertEquals(generation, refreshGeneration(panel))
            assertEquals(0, changes())
            assertEquals(listOf("package_a_rule"), ruleNames(panel))
        }
    }

    private fun withEditorSelectionOverview(action: (ArchUnitLensRuleOverviewPanel, VirtualFile, VirtualFile) -> Unit) {
        val settings = service<ArchUnitLensSettings>()
        val original = settings.state
        settings.loadState(ArchUnitLensSettingsState())
        myFixture.addFileToProject(
            "src/test/java/com/tngtech/archunit/lang/syntax/ArchRuleDefinition.java",
            testData("stubs/settingsRefresh/ArchRuleDefinition.java"),
        )
        myFixture.addFileToProject(
            "src/test/java/ArchitectureRules.java",
            testData("archrules/editorSelectionOverview.java"),
        )
        val first = myFixture.addFileToProject(
            "src/main/java/com/example/a/TargetA.java",
            testData("javaSources/editorSelectionOverview/TargetA.java"),
        ).virtualFile
        val second = myFixture.addFileToProject(
            "src/main/java/com/example/b/TargetB.java",
            testData("javaSources/editorSelectionOverview/TargetB.java"),
        ).virtualFile
        selectEditor(first)
        val panel = ArchUnitLensRuleOverviewPanel(project)
        try {
            awaitRefresh()
            action(panel, first, second)
        } finally {
            if (!Disposer.isDisposed(panel)) Disposer.dispose(panel)
            settings.loadState(original)
        }
    }

    private fun selectEditor(file: VirtualFile) {
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
    }

    private fun ruleNames(panel: JPanel): List<String> {
        val model = descendants(panel).filterIsInstance<JBList<*>>().single().model
        return (0 until model.size).map { model.getElementAt(it).toString() }
    }

    private fun assertCurrentPackage(panel: JPanel, packageName: String?) {
        val expected = packageName?.let { ArchUnitLensBundle.message("overview.currentPackage", it) }
            ?: ArchUnitLensBundle.message("overview.currentPackage.none")
        assertTrue(descendants(panel).filterIsInstance<JTextArea>().single().text.contains(expected))
    }

    private fun refreshGeneration(panel: JPanel): Int = panel.javaClass.getDeclaredField("refreshGeneration").let {
        it.isAccessible = true
        it.getInt(panel)
    }

    private fun observeRuleListChanges(panel: JPanel): () -> Int {
        var changes = 0
        descendants(panel).filterIsInstance<JBList<*>>().single().model.addListDataListener(object : ListDataListener {
            override fun intervalAdded(event: ListDataEvent) {
                changes++
            }

            override fun intervalRemoved(event: ListDataEvent) {
                changes++
            }

            override fun contentsChanged(event: ListDataEvent) {
                changes++
            }
        })
        return { changes }
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

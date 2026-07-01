package io.github.archunitlens.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import io.github.archunitlens.ArchUnitLensBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Settings page for the first ArchUnit Lens inspection and overview preferences.
 */
class ArchUnitLensConfigurable : Configurable {
    private val settings: ArchUnitLensSettings = service()
    private var panel: JPanel? = null
    private lateinit var classNamingRules: JCheckBox
    private lateinit var dependencyRules: JCheckBox
    private lateinit var annotationRules: JCheckBox
    private lateinit var interfaceRules: JCheckBox
    private lateinit var showSupported: JCheckBox
    private lateinit var showUnsupported: JCheckBox
    private lateinit var showDiagnostics: JCheckBox
    private lateinit var metricsLogging: JCheckBox
    private lateinit var excludedPaths: JTextField

    override fun getDisplayName(): String = ArchUnitLensBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        val root = JPanel(GridBagLayout())
        val constraints = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
        }

        classNamingRules = JCheckBox(ArchUnitLensBundle.message("settings.ruleFamily.classNaming"))
        dependencyRules = JCheckBox(ArchUnitLensBundle.message("settings.ruleFamily.dependencies"))
        annotationRules = JCheckBox(ArchUnitLensBundle.message("settings.ruleFamily.annotations"))
        interfaceRules = JCheckBox(ArchUnitLensBundle.message("settings.ruleFamily.interfaces"))
        showSupported = JCheckBox(ArchUnitLensBundle.message("settings.overview.showSupported"))
        showUnsupported = JCheckBox(ArchUnitLensBundle.message("settings.overview.showUnsupported"))
        showDiagnostics = JCheckBox(ArchUnitLensBundle.message("settings.overview.showDiagnostics"))
        metricsLogging = JCheckBox(ArchUnitLensBundle.message("settings.metrics.logging"))
        excludedPaths = JTextField()

        listOf(
            JLabel(ArchUnitLensBundle.message("settings.section.inspections")),
            classNamingRules,
            dependencyRules,
            annotationRules,
            interfaceRules,
            JLabel(ArchUnitLensBundle.message("settings.section.overview")),
            showSupported,
            showUnsupported,
            showDiagnostics,
            metricsLogging,
            JLabel(ArchUnitLensBundle.message("settings.excludedPaths")),
            excludedPaths,
        ).forEach { component ->
            root.add(component, constraints)
            constraints.gridy++
        }

        panel = root
        reset()
        return root
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return classNamingRules.isSelected != state.classNamingRulesEnabled ||
            dependencyRules.isSelected != state.dependencyRulesEnabled ||
            annotationRules.isSelected != state.annotationRulesEnabled ||
            interfaceRules.isSelected != state.interfaceRulesEnabled ||
            showSupported.isSelected != state.showSupportedRulesInOverview ||
            showUnsupported.isSelected != state.showUnsupportedRulesInOverview ||
            showDiagnostics.isSelected != state.showDiagnosticsInOverview ||
            metricsLogging.isSelected != state.metricsLoggingEnabled ||
            excludedPaths.text != state.excludedPathFragments
    }

    override fun apply() {
        val state = settings.state
        state.classNamingRulesEnabled = classNamingRules.isSelected
        state.dependencyRulesEnabled = dependencyRules.isSelected
        state.annotationRulesEnabled = annotationRules.isSelected
        state.interfaceRulesEnabled = interfaceRules.isSelected
        state.showSupportedRulesInOverview = showSupported.isSelected
        state.showUnsupportedRulesInOverview = showUnsupported.isSelected
        state.showDiagnosticsInOverview = showDiagnostics.isSelected
        state.metricsLoggingEnabled = metricsLogging.isSelected
        state.excludedPathFragments = excludedPaths.text.trim()
    }

    override fun reset() {
        val state = settings.state
        classNamingRules.isSelected = state.classNamingRulesEnabled
        dependencyRules.isSelected = state.dependencyRulesEnabled
        annotationRules.isSelected = state.annotationRulesEnabled
        interfaceRules.isSelected = state.interfaceRulesEnabled
        showSupported.isSelected = state.showSupportedRulesInOverview
        showUnsupported.isSelected = state.showUnsupportedRulesInOverview
        showDiagnostics.isSelected = state.showDiagnosticsInOverview
        metricsLogging.isSelected = state.metricsLoggingEnabled
        excludedPaths.text = state.excludedPathFragments
    }

    override fun disposeUIResources() {
        panel = null
    }
}

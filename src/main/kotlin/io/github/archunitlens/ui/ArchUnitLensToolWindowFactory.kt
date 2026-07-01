package io.github.archunitlens.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import io.github.archunitlens.ArchUnitLensBundle
import io.github.archunitlens.rules.ArchRuleProjectService
import io.github.archunitlens.rules.DiscoveredArchRule
import io.github.archunitlens.settings.ArchUnitLensSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Creates the ArchUnit Lens rule overview tool window.
 *
 * The factory is intentionally a class because IntelliJ Platform owns extension
 * instantiation for `plugin.xml` registrations.
 */
class ArchUnitLensToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val panel = ArchUnitLensRuleOverviewPanel(project)
        val content = toolWindow.contentManager.factory.createContent(
            panel,
            ArchUnitLensBundle.message("toolwindow.content.rules"),
            false,
        )
        content.preferredFocusableComponent = panel.preferredFocusComponent
        toolWindow.contentManager.addContent(content)
    }
}

private class ArchUnitLensRuleOverviewPanel(
    private val project: Project,
) : JPanel(BorderLayout()) {
    private val settings = service<ArchUnitLensSettings>()
    private val searchField = JBTextField().apply {
        emptyText.text = ArchUnitLensBundle.message("toolwindow.search.placeholder")
    }
    private val showSupported = JCheckBox(ArchUnitLensBundle.message("toolwindow.filter.supported"))
    private val showUnsupported = JCheckBox(ArchUnitLensBundle.message("toolwindow.filter.unsupported"))
    private val currentFileOnly = JCheckBox(ArchUnitLensBundle.message("toolwindow.filter.currentFile"))
    private val showDiagnostics = JCheckBox(ArchUnitLensBundle.message("toolwindow.filter.diagnostics"))
    private val refreshButton = JButton(ArchUnitLensBundle.message("toolwindow.refresh"))
    private val openSourceButton = JButton(ArchUnitLensBundle.message("toolwindow.openSource"))
    private val copyDiagnosticsButton = JButton(ArchUnitLensBundle.message("toolwindow.copyDiagnostics"))
    private val listModel = DefaultListModel<RuleOverviewRow>()
    private val ruleList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val details = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        text = ArchUnitLensBundle.message("toolwindow.loading")
    }
    private var latestOverviewText: String = ArchUnitLensBundle.message("toolwindow.loading")
    private var latestCurrentPackage: String? = null

    val preferredFocusComponent = searchField

    init {
        resetFiltersFromSettings()
        add(toolbar(), BorderLayout.NORTH)
        add(
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JBScrollPane(ruleList), JBScrollPane(details)).apply {
                resizeWeight = 0.35
            },
            BorderLayout.CENTER,
        )

        refreshButton.addActionListener { refresh() }
        openSourceButton.addActionListener { openSelectedRuleSource() }
        copyDiagnosticsButton.addActionListener { copyOverviewDiagnostics() }
        listOf(showSupported, showUnsupported, showDiagnostics).forEach { checkbox ->
            checkbox.addActionListener {
                persistOverviewSettings()
                refresh()
            }
        }
        currentFileOnly.addActionListener { refresh() }
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = refresh()
            override fun removeUpdate(event: DocumentEvent) = refresh()
            override fun changedUpdate(event: DocumentEvent) = refresh()
        })
        ruleList.addListSelectionListener { updateDetails() }
        refresh()
    }

    private fun toolbar(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(refreshButton)
        add(JLabel(ArchUnitLensBundle.message("toolwindow.search.label")))
        add(searchField)
        add(showSupported)
        add(showUnsupported)
        add(currentFileOnly)
        add(showDiagnostics)
        add(openSourceButton)
        add(copyDiagnosticsButton)
    }

    private fun refresh() {
        val service = project.service<ArchRuleProjectService>()
        latestCurrentPackage = currentJavaPackage()
        val discoveries = if (currentFileOnly.isSelected && latestCurrentPackage != null) {
            service.discoveriesForPackage(latestCurrentPackage.orEmpty())
        } else {
            service.discoveries()
        }
        val filter = currentFilter()
        val visibleDiscoveries = ArchUnitLensRuleOverviewFormatter.filteredDiscoveries(discoveries, filter)
        latestOverviewText = ArchUnitLensRuleOverviewFormatter.render(
            discoveries = discoveries,
            metrics = service.scanMetrics(),
            filter = filter,
        )
        listModel.clear()
        visibleDiscoveries.forEach { listModel.addElement(RuleOverviewRow(it)) }
        if (listModel.isEmpty) {
            details.text = latestOverviewText
            openSourceButton.isEnabled = false
        } else if (ruleList.selectedIndex < 0) {
            ruleList.selectedIndex = 0
        } else {
            updateDetails()
        }
        details.caretPosition = 0
    }

    private fun updateDetails() {
        val row = ruleList.selectedValue
        openSourceButton.isEnabled = row != null
        details.text = row?.let {
            ArchUnitLensRuleOverviewFormatter.renderDetails(it.discovery, latestCurrentPackage)
        } ?: latestOverviewText
        details.caretPosition = 0
    }

    private fun currentFilter(): RuleOverviewFilter = RuleOverviewFilter(
        showSupported = showSupported.isSelected,
        showUnsupported = showUnsupported.isSelected,
        searchQuery = searchField.text,
        currentPackage = latestCurrentPackage,
        currentPackageOnly = currentFileOnly.isSelected,
        showDiagnostics = showDiagnostics.isSelected,
    )

    private fun resetFiltersFromSettings() {
        val state = settings.state
        showSupported.isSelected = state.showSupportedRulesInOverview
        showUnsupported.isSelected = state.showUnsupportedRulesInOverview
        showDiagnostics.isSelected = state.showDiagnosticsInOverview
    }

    private fun persistOverviewSettings() {
        val state = settings.state
        state.showSupportedRulesInOverview = showSupported.isSelected
        state.showUnsupportedRulesInOverview = showUnsupported.isSelected
        state.showDiagnosticsInOverview = showDiagnostics.isSelected
    }

    private fun currentJavaPackage(): String? {
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(selectedFile) as? PsiJavaFile ?: return null
        return psiFile.packageName
    }

    private fun openSelectedRuleSource() {
        val element = ruleList.selectedValue?.discovery?.descriptor?.sourcePointer?.element ?: return
        val virtualFile = element.containingFile?.virtualFile ?: return
        com.intellij.openapi.fileEditor.OpenFileDescriptor(project, virtualFile, element.textOffset).navigate(true)
    }

    private fun copyOverviewDiagnostics() {
        CopyPasteManager.getInstance().setContents(StringSelection(latestOverviewText))
    }
}

private data class RuleOverviewRow(
    val discovery: DiscoveredArchRule,
) {
    override fun toString(): String = discovery.ruleName
}

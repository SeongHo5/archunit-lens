package io.github.archunitlens.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.AppExecutorUtil
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
import javax.swing.Timer
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
    private val refreshCoalesceKey = Any()
    private val refreshDebounceTimer = Timer(REFRESH_DEBOUNCE_MILLIS) { runRefresh(refreshGeneration) }.apply {
        isRepeats = false
    }
    private var refreshGeneration = 0
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
            override fun insertUpdate(event: DocumentEvent) = scheduleRefresh()
            override fun removeUpdate(event: DocumentEvent) = scheduleRefresh()
            override fun changedUpdate(event: DocumentEvent) = scheduleRefresh()
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

    private fun refresh() = scheduleRefresh(immediate = true)

    private fun scheduleRefresh(immediate: Boolean = false) {
        refreshGeneration++
        if (immediate) {
            refreshDebounceTimer.stop()
            runRefresh(refreshGeneration)
        } else {
            refreshDebounceTimer.restart()
        }
    }

    private fun runRefresh(generation: Int) {
        val request = currentRefreshRequest()
        ReadAction.nonBlocking<RuleOverviewSnapshot> {
            readRuleOverviewSnapshot(request)
        }
            .inSmartMode(project)
            .expireWith(project)
            .coalesceBy(refreshCoalesceKey)
            .finishOnUiThread(ModalityState.defaultModalityState()) { snapshot ->
                if (generation == refreshGeneration) {
                    applySnapshot(snapshot)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun applySnapshot(snapshot: RuleOverviewSnapshot) {
        latestCurrentPackage = snapshot.currentPackage
        latestOverviewText = snapshot.overviewText
        listModel.clear()
        snapshot.visibleDiscoveries.forEach { listModel.addElement(RuleOverviewRow(it)) }
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
        details.text = readAction {
            row?.let {
                ArchUnitLensRuleOverviewFormatter.renderDetails(it.discovery, latestCurrentPackage)
            } ?: latestOverviewText
        }
        details.caretPosition = 0
    }

    private fun readRuleOverviewSnapshot(request: RuleOverviewRefreshRequest): RuleOverviewSnapshot {
        val service = project.service<ArchRuleProjectService>()
        val currentPackage = packageNameForJavaFile(project, request.selectedFile)
        val filter = request.toFilter(currentPackage)
        val discoveries = if (request.currentFileOnly && currentPackage != null) {
            service.discoveriesForPackage(currentPackage)
        } else {
            service.discoveries()
        }
        return RuleOverviewSnapshot(
            currentPackage = currentPackage,
            visibleDiscoveries = ArchUnitLensRuleOverviewFormatter.filteredDiscoveries(discoveries, filter),
            overviewText = ArchUnitLensRuleOverviewFormatter.render(
                discoveries = discoveries,
                metrics = service.scanMetrics(),
                filter = filter,
            ),
        )
    }

    private fun currentRefreshRequest(): RuleOverviewRefreshRequest = RuleOverviewRefreshRequest(
        showSupported = showSupported.isSelected,
        showUnsupported = showUnsupported.isSelected,
        searchQuery = searchField.text,
        currentFileOnly = currentFileOnly.isSelected,
        showDiagnostics = showDiagnostics.isSelected,
        selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull(),
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

    private fun openSelectedRuleSource() {
        val descriptor = readAction {
            val element = ruleList.selectedValue?.discovery?.descriptor?.sourcePointer?.element ?: return@readAction null
            val virtualFile = element.containingFile?.virtualFile ?: return@readAction null
            OpenFileDescriptor(project, virtualFile, element.textOffset)
        } ?: return
        descriptor.navigate(true)
    }

    private fun copyOverviewDiagnostics() {
        CopyPasteManager.getInstance().setContents(StringSelection(latestOverviewText))
    }
}

internal fun currentJavaPackage(project: Project): String? = readAction {
    packageNameForJavaFile(project, FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
}

private fun packageNameForJavaFile(
    project: Project,
    selectedFile: VirtualFile?,
): String? {
    if (project.isDisposed || selectedFile?.isValid != true) {
        return null
    }
    val psiFile = selectedFile.let { PsiManager.getInstance(project).findFile(it) } as? PsiJavaFile ?: return null
    return psiFile.packageName
}

private fun <T> readAction(action: () -> T): T = ApplicationManager.getApplication().runReadAction<T>(action)

private const val REFRESH_DEBOUNCE_MILLIS = 250

private data class RuleOverviewRefreshRequest(
    val showSupported: Boolean,
    val showUnsupported: Boolean,
    val searchQuery: String,
    val currentFileOnly: Boolean,
    val showDiagnostics: Boolean,
    val selectedFile: VirtualFile?,
) {
    fun toFilter(currentPackage: String?): RuleOverviewFilter = RuleOverviewFilter(
        showSupported = showSupported,
        showUnsupported = showUnsupported,
        searchQuery = searchQuery,
        currentPackage = currentPackage,
        currentPackageOnly = currentFileOnly,
        showDiagnostics = showDiagnostics,
    )
}

private data class RuleOverviewSnapshot(
    val currentPackage: String?,
    val visibleDiscoveries: List<DiscoveredArchRule>,
    val overviewText: String,
)

private data class RuleOverviewRow(
    val discovery: DiscoveredArchRule,
) {
    override fun toString(): String = discovery.ruleName
}

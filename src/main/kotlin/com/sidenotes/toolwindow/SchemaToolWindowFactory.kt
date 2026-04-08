package com.sidenotes.toolwindow

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.sidenotes.SidenotesBundle
import com.sidenotes.services.AnnotationService
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Creates the "Sidenotes" tool window panel that displays the full schema
 * for the currently active model file. Updates automatically when the user
 * switches between editor tabs.
 */
class SchemaToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SchemaToolWindowPanel(project)

        val content = ContentFactory.getInstance().createContent(
            panel,
            SidenotesBundle.message("toolwindow.title"),
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

/**
 * The main panel displayed inside the Sidenotes tool window.
 * Shows schema information in a monospaced text area.
 */
class SchemaToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val textArea = JTextArea().apply {
        isEditable = false
        font = Font("JetBrains Mono", Font.PLAIN, 13).takeIf { it.family == "JetBrains Mono" }
            ?: Font(Font.MONOSPACED, Font.PLAIN, 13)
        lineWrap = false
        tabSize = 2
    }

    private val emptyMessage = SidenotesBundle.message("toolwindow.empty")

    init {
        add(JBScrollPane(textArea), BorderLayout.CENTER)

        // Listen for editor tab changes
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    SwingUtilities.invokeLater { updateContent() }
                }
            }
        )

        // Show content for currently open file
        updateContent()
    }

    private fun updateContent() {
        val editor = FileEditorManager.getInstance(project)
        val currentFile = editor.selectedFiles.firstOrNull()

        if (currentFile == null) {
            textArea.text = emptyMessage
            return
        }

        // Check if this is a Ruby model file
        if (currentFile.extension != "rb") {
            textArea.text = emptyMessage
            return
        }

        val service = AnnotationService.getInstance(project)
        val annotation = service.getAnnotationForModelFile(currentFile)

        if (annotation != null) {
            textArea.text = annotation.toPlainText()
            textArea.caretPosition = 0
        } else {
            textArea.text = emptyMessage
        }
    }
}

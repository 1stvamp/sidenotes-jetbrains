package com.sidenotes.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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

class SchemaToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SchemaToolWindowPanel(project, toolWindow.disposable)

        val content = ContentFactory.getInstance().createContent(
            panel,
            SidenotesBundle.message("toolwindow.title"),
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

class SchemaToolWindowPanel(
    private val project: Project,
    parentDisposable: Disposable
) : JPanel(BorderLayout()) {

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

        val connection = project.messageBus.connect(parentDisposable)
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    SwingUtilities.invokeLater { updateContent() }
                }
            }
        )

        updateContent()
    }

    private fun updateContent() {
        val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

        if (currentFile == null || currentFile.extension != "rb") {
            textArea.text = emptyMessage
            return
        }

        val annotation = AnnotationService.getInstance(project).getAnnotationForModelFile(currentFile)

        if (annotation != null) {
            textArea.text = annotation.toPlainText()
            textArea.caretPosition = 0
        } else {
            textArea.text = emptyMessage
        }
    }
}

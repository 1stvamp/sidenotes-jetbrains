package com.sidenotes.providers

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.sidenotes.SidenotesBundle
import com.sidenotes.services.AnnotationService
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides inline type hints next to attribute references in ActiveRecord models.
 * For example, `self.email` will show an inlay hint like `: string, not null`.
 *
 * Toggleable in Settings → Editor → Inlay Hints → Ruby → Sidenotes Schema Hints.
 */
@Suppress("UnstableApiUsage")
class SchemaInlayHintsProvider : InlayHintsProvider<SchemaInlayHintsProvider.Settings> {

    data class Settings(
        var showColumnTypes: Boolean = true,
        var showNullability: Boolean = true,
        var showDefaults: Boolean = false
    )

    override val key: SettingsKey<Settings> =
        SettingsKey("sidenotes.schema.inlay.hints")

    override val name: String =
        SidenotesBundle.message("inlay.hints.name")

    override val previewText: String = """
        class User < ApplicationRecord
          def display_info
            self.email    # → string, not null
            self.name     # → string
            created_at    # → datetime, not null
          end
        end
    """.trimIndent()

    override fun createSettings(): Settings = Settings()

    override fun createConfigurable(settings: Settings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent {
                return JPanel() // Minimal settings panel
            }
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (!settings.showColumnTypes) return null

        return object : InlayHintsCollector {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                collectHints(element, editor, settings, sink)
                return true
            }
        }
    }

    private fun collectHints(
        element: PsiElement,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink
    ) {
        val file = element.containingFile ?: return
        val project = element.project
        val service = AnnotationService.getInstance(project)

        // Get the annotation for the current file
        val virtualFile = file.virtualFile ?: return
        val annotation = service.getAnnotationForModelFile(virtualFile) ?: return

        val document = editor.document
        val text = document.text

        // Pattern to match attribute access: self.column_name or just column_name in model context
        val selfDotPattern = Regex("""\bself\.(\w+)\b""")
        val factory = PresentationFactory(editor)

        // Scan through the document text for self.attribute patterns
        selfDotPattern.findAll(text).forEach { match ->
            val attrName = match.groupValues[1]
            val column = annotation.findColumn(attrName) ?: return@forEach

            val hintText = buildHintText(column.type, column.nullable, column.default, settings)
            val offset = match.range.last + 1

            // Ensure offset is within document bounds
            if (offset <= document.textLength) {
                val presentation = factory.roundWithBackground(
                    factory.smallText(hintText)
                )
                sink.addInlineElement(offset, true, presentation, false)
            }
        }
    }

    private fun buildHintText(
        type: String,
        nullable: Boolean,
        default: String?,
        settings: Settings
    ): String {
        val parts = mutableListOf(type)
        if (settings.showNullability && !nullable) {
            parts.add("not null")
        }
        if (settings.showDefaults && default != null) {
            parts.add("default: $default")
        }
        return parts.joinToString(", ")
    }
}

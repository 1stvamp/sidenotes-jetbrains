package com.sidenotes.providers

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.sidenotes.SidenotesBundle
import com.sidenotes.services.AnnotationService
import javax.swing.JComponent
import javax.swing.JPanel

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
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (!settings.showColumnTypes) return null
        if (file.virtualFile?.extension != "rb") return null

        val service = AnnotationService.getInstance(file.project)
        val annotation = service.getAnnotationForModelFile(file.virtualFile) ?: return null

        return object : InlayHintsCollector {
            private var collected = false

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (collected) return true
                collected = true

                val document = editor.document
                val text = document.text
                val selfDotPattern = Regex("""\bself\.(\w+)\b""")
                val factory = PresentationFactory(editor)

                selfDotPattern.findAll(text).forEach { match ->
                    val attrName = match.groupValues[1]
                    val column = annotation.findColumn(attrName) ?: return@forEach

                    val hintText = buildHintText(column.type, column.nullable, column.default, settings)
                    val offset = match.range.last + 1

                    if (offset <= document.textLength) {
                        val presentation = factory.roundWithBackground(
                            factory.smallText(hintText)
                        )
                        sink.addInlineElement(offset, true, presentation, false)
                    }
                }
                return true
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

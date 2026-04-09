package com.sidenotes.providers

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.sidenotes.SidenotesBundle
import com.sidenotes.services.AnnotationService
import com.sidenotes.services.SidenotesSettings
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class SchemaInlayHintsProvider : InlayHintsProvider<SchemaInlayHintsProvider.Settings> {

    data class Settings(
        var showColumnTypes: Boolean = true,
        var showNullability: Boolean = true,
        var showDefaults: Boolean = false
    )

    companion object {
        // validates :email, :name  OR  validates_presence_of :email, :name
        private val VALIDATES_PATTERN = Regex("""validates?(?:_\w+)?\s+((?::\w+[\s,]*)+)""")
        private val SYMBOL_PATTERN = Regex(""":(\w+)""")

        // attribute :col
        private val ATTRIBUTE_PATTERN = Regex("""\battribute\s+:(\w+)""")

        // scope :name, -> { where(col: ...) }
        private val SCOPE_PATTERN = Regex("""\bscope\s+:\w+.*?where\((\w+):""")

        // self.col
        private val SELF_DOT_PATTERN = Regex("""\bself\.(\w+)\b""")
    }

    override val key: SettingsKey<Settings> =
        SettingsKey("sidenotes.schema.inlay.hints")

    override val name: String =
        SidenotesBundle.message("inlay.hints.name")

    override val previewText: String = """
        class User < ApplicationRecord
          validates :email, :name
          validates_presence_of :age
          attribute :role
          scope :active, -> { where(status: 'active') }

          def display_info
            self.email
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

        val pluginSettings = SidenotesSettings.getInstance(file.project)
        if (!pluginSettings.showInlayHints) return null

        val service = AnnotationService.getInstance(file.project)
        val annotation = service.getAnnotationForModelFile(file.virtualFile) ?: return null

        return object : InlayHintsCollector {
            private var collected = false

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (collected) return true
                collected = true

                val document = editor.document
                val text = document.text
                val factory = PresentationFactory(editor)

                // Process each line for patterns
                text.lines().forEachIndexed { lineIndex, line ->
                    val lineOffset = document.getLineStartOffset(lineIndex)

                    // validates :col1, :col2
                    VALIDATES_PATTERN.find(line)?.let { match ->
                        SYMBOL_PATTERN.findAll(match.groupValues[1]).forEach { sym ->
                            val colName = sym.groupValues[1]
                            addHintForColumn(annotation, colName, factory, sink, settings,
                                lineOffset + match.range.first + match.value.indexOf(":$colName") + colName.length + 1)
                        }
                    }

                    // attribute :col
                    ATTRIBUTE_PATTERN.find(line)?.let { match ->
                        val colName = match.groupValues[1]
                        addHintForColumn(annotation, colName, factory, sink, settings,
                            lineOffset + match.range.last + 1)
                    }

                    // scope :name, -> { where(col: ...) }
                    SCOPE_PATTERN.find(line)?.let { match ->
                        val colName = match.groupValues[1]
                        addHintForColumn(annotation, colName, factory, sink, settings,
                            lineOffset + match.range.first + match.value.indexOf("$colName:") + colName.length)
                    }

                    // self.col
                    SELF_DOT_PATTERN.findAll(line).forEach { match ->
                        val colName = match.groupValues[1]
                        addHintForColumn(annotation, colName, factory, sink, settings,
                            lineOffset + match.range.last + 1)
                    }
                }
                return true
            }
        }
    }

    private fun addHintForColumn(
        annotation: com.sidenotes.models.AnnotationData,
        colName: String,
        factory: PresentationFactory,
        sink: InlayHintsSink,
        settings: Settings,
        offset: Int
    ) {
        val column = annotation.findColumn(colName) ?: return
        val hintText = buildHintText(column.type, column.nullable, column.default, settings)
        val presentation = factory.roundWithBackground(factory.smallText(hintText))
        sink.addInlineElement(offset, true, presentation, false)
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

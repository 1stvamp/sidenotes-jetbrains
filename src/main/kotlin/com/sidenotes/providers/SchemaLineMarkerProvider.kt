package com.sidenotes.providers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.sidenotes.SidenotesBundle
import com.sidenotes.services.AnnotationService
import java.awt.event.MouseEvent
import javax.swing.Icon

class SchemaLineMarkerProvider : LineMarkerProvider {

    companion object {
        private val CLASS_PATTERN = Regex(
            """^\s*class\s+(\w+(?:::\w+)*)\s*<\s*(?:ApplicationRecord|ActiveRecord::Base)\s*$"""
        )
        private val ICON: Icon = AllIcons.Providers.Mysql
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.containingFile?.virtualFile?.extension != "rb") return null

        val document = element.containingFile?.viewProvider?.document ?: return null
        val lineNumber = document.getLineNumber(element.textRange.startOffset)
        val lineStart = document.getLineStartOffset(lineNumber)

        // Only process the first non-whitespace element on each line to avoid duplicates
        if (element.textRange.startOffset != lineStart) {
            val textBefore = document.getText(TextRange(lineStart, element.textRange.startOffset))
            if (textBefore.isNotBlank()) return null
        }

        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEnd))

        val match = CLASS_PATTERN.find(lineText) ?: return null
        val className = match.groupValues[1]

        val service = AnnotationService.getInstance(element.project)
        val annotation = service.getAnnotationForClassName(className) ?: return null

        return LineMarkerInfo(
            element,
            element.textRange,
            ICON,
            { _ -> "${SidenotesBundle.message("gutter.tooltip")}: ${annotation.toSummaryText()}" },
            { mouseEvent: MouseEvent, _ ->
                showSchemaPopup(mouseEvent, annotation.toSummaryText(), className)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { SidenotesBundle.message("gutter.tooltip") }
        )
    }

    private fun showSchemaPopup(event: MouseEvent, summary: String, className: String) {
        val content = buildString {
            appendLine("── $className ──")
            appendLine(summary)
        }

        JBPopupFactory.getInstance()
            .createMessage(content)
            .show(RelativePoint(event))
    }
}

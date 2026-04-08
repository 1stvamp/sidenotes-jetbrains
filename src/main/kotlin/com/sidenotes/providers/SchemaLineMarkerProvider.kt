package com.sidenotes.providers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.sidenotes.SidenotesBundle
import com.sidenotes.services.AnnotationService
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Provides gutter icons next to ActiveRecord model class declarations.
 * Detects lines matching `class Foo < ApplicationRecord` (or any AR base class)
 * and shows a database icon that, when clicked, displays a schema summary popup.
 */
class SchemaLineMarkerProvider : LineMarkerProvider {

    companion object {
        private val CLASS_PATTERN = Regex(
            """^\s*class\s+(\w+(?:::\w+)*)\s*<\s*(?:ApplicationRecord|ActiveRecord::Base)\s*$"""
        )
        private val ICON: Icon = AllIcons.Providers.Mysql
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process leaf elements to avoid duplicates — check the first token on the line
        val text = element.text ?: return null

        // We look for class declarations; PsiElement here is a leaf token.
        // We match against the full line text from the document.
        val document = element.containingFile?.viewProvider?.document ?: return null
        val lineNumber = document.getLineNumber(element.textRange.startOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        // Only trigger on the first element of the line
        if (element.textRange.startOffset != lineStart && element.text.isBlank()) return null
        // Check if this element is at the start of the interesting part
        if (!lineText.trimStart().startsWith("class")) return null
        if (element.textRange.startOffset > lineStart + lineText.indexOf("class") + 5) return null

        val match = CLASS_PATTERN.find(lineText) ?: return null
        val className = match.groupValues[1]

        val project = element.project
        val service = AnnotationService.getInstance(project)
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

package com.sidenotes.providers

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.sidenotes.services.AnnotationService

/**
 * Provides Quick Documentation (Ctrl+Q) for ActiveRecord model classes.
 * When the cursor is on a class name that has a corresponding .annotations/ file,
 * this provider renders the full schema as formatted HTML.
 */
class SchemaDocumentationProvider : AbstractDocumentationProvider() {

    companion object {
        private val CLASS_DEF_PATTERN = Regex(
            """class\s+(\w+(?:::\w+)*)\s*<\s*(?:ApplicationRecord|ActiveRecord::Base)"""
        )
        private val CLASS_REF_PATTERN = Regex("""^[A-Z]\w*(?:::\w+)*$""")
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element ?: originalElement ?: return null
        val project = target.project

        val className = resolveClassName(target) ?: return null
        val service = AnnotationService.getInstance(project)
        val annotation = service.getAnnotationForClassName(className) ?: return null

        return annotation.toHtmlDocumentation()
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return generateDoc(element, originalElement)
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        val element = contextElement ?: return null
        val text = element.text ?: return null

        // Check if hovering over a class name reference
        if (CLASS_REF_PATTERN.matches(text)) {
            val service = AnnotationService.getInstance(file.project)
            if (service.getAnnotationForClassName(text) != null) {
                return element
            }
        }

        return null
    }

    private fun resolveClassName(element: PsiElement): String? {
        val text = element.text ?: return null

        // Direct class name reference (e.g., "User", "Admin::User")
        if (CLASS_REF_PATTERN.matches(text)) {
            return text
        }

        // Check if within a class declaration line
        val containingFile = element.containingFile ?: return null
        val document = containingFile.viewProvider.document ?: return null
        val offset = element.textRange.startOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        val match = CLASS_DEF_PATTERN.find(lineText)
        if (match != null) {
            return match.groupValues[1]
        }

        return null
    }
}

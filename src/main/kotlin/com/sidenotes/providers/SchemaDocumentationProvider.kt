package com.sidenotes.providers

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.sidenotes.services.AnnotationService

class SchemaDocumentationProvider : AbstractDocumentationProvider() {

    companion object {
        private val CLASS_DEF_PATTERN = Regex(
            """class\s+(\w+(?:::\w+)*)\s*<\s*(?:ApplicationRecord|ActiveRecord::Base)"""
        )
        private val CLASS_REF_PATTERN = Regex("""^[A-Z]\w*(?:::\w+)*$""")
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element ?: originalElement ?: return null
        if (target.containingFile?.virtualFile?.extension != "rb") return null

        val className = resolveClassName(target) ?: return null
        val service = AnnotationService.getInstance(target.project)
        return service.getAnnotationForClassName(className)?.toHtmlDocumentation()
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
        if (file.virtualFile?.extension != "rb") return null
        val element = contextElement ?: return null
        val text = element.text ?: return null

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

        if (CLASS_REF_PATTERN.matches(text)) return text

        val containingFile = element.containingFile ?: return null
        val document = containingFile.viewProvider.document ?: return null
        val offset = element.textRange.startOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        return CLASS_DEF_PATTERN.find(lineText)?.groupValues?.get(1)
    }
}

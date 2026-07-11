package com.jiuwenswarm.plugin.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Collects IDE context (active file path, language, selection, diagnostics)
 * and formats it as a structured text block to prepend to outgoing chat messages.
 */
object ContextCollector {

    /** Returns a formatted context block, or null if there is nothing useful to inject. */
    fun collect(project: Project): String? = ReadAction.compute<String?, Throwable> {
        val editor: Editor =
            FileEditorManager.getInstance(project).selectedTextEditor ?: return@compute null

        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
        val filePath = virtualFile?.path

        val selection = editor.selectionModel.let { sel ->
            if (sel.hasSelection()) sel.selectedText else null
        }

        val diagnostics = collectDiagnostics(project, editor)

        // Nothing meaningful to send if we have no file info and no selection/diagnostics
        if (filePath == null && selection == null && diagnostics.isEmpty()) return@compute null

        buildString {
            appendLine("<!-- IDE Context -->")
            if (filePath != null) {
                val lang = virtualFile.fileType.name
                appendLine("Active file: $filePath  (${lang})")
                appendLine("Cursor line: ${editor.caretModel.logicalPosition.line + 1}")
            }
            if (!selection.isNullOrBlank()) {
                appendLine()
                appendLine("Selected code:")
                appendLine("```")
                appendLine(selection.trimEnd())
                appendLine("```")
            }
            if (diagnostics.isNotEmpty()) {
                appendLine()
                appendLine("Diagnostics (${diagnostics.size}):")
                diagnostics.forEach { appendLine("  • $it") }
            }
            appendLine("<!-- End IDE Context -->")
        }.trim()
    }

    // ──────────────────────────────────────────────────────────────────────────
    private fun collectDiagnostics(project: Project, editor: Editor): List<String> {
        val markupModel =
            DocumentMarkupModel.forDocument(editor.document, project, false) ?: return emptyList()

        // errorStripeTooltip is non-null only for highlighters that show in the error stripe,
        // which in practice means warnings and errors — no severity API needed.
        return markupModel.allHighlighters
            .filter { it.errorStripeTooltip != null }
            .take(10)
            .mapNotNull { h ->
                val tooltip = h.errorStripeTooltip?.toString() ?: return@mapNotNull null
                // Strip embedded HTML tags for a clean plain-text message
                tooltip.replace(Regex("<[^>]+>"), "").trim().ifBlank { null }
            }
            .distinct()
    }
}

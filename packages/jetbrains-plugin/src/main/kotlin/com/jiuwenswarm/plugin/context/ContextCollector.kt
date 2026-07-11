package com.jiuwenswarm.plugin.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Collects IDE context (active file, language, selection, diagnostics, open tabs, git)
 * and formats it as a structured text block to prepend to outgoing chat messages.
 *
 * IDE API calls run inside a [ReadAction]; git collection runs outside (subprocess) so
 * it must not hold the read lock.
 */
object ContextCollector {

    /** Returns a formatted context block, or null if there is nothing useful to inject. */
    fun collect(project: Project): String? {
        // ── Phase 1: gather all IntelliJ-API data under ReadAction ──────────
        val ideData = ReadAction.compute<IdeData?, Throwable> { readIdeData(project) }
        // ── Phase 2: git (subprocess — must run outside ReadAction) ─────────
        val gitInfo = GitContextProvider.collect(project)

        // If we have nothing at all, return null (no context to inject)
        if (ideData == null && gitInfo == null) return null

        return buildString {
            appendLine("<!-- IDE Context -->")

            // Active file + cursor
            ideData?.filePath?.let { path ->
                append("Active file: $path")
                ideData.language?.let { append("  ($it)") }
                appendLine()
                appendLine("Cursor line: ${ideData.cursorLine}")
            }

            // Selected code block
            ideData?.selection?.takeIf { it.isNotBlank() }?.let { sel ->
                appendLine()
                appendLine("Selected code:")
                appendLine("```")
                appendLine(sel.trimEnd())
                appendLine("```")
            }

            // Diagnostics
            ideData?.diagnostics?.takeIf { it.isNotEmpty() }?.let { diags ->
                appendLine()
                appendLine("Diagnostics (${diags.size}):")
                diags.forEach { appendLine("  • $it") }
            }

            // Other open tabs (multi-file context)
            ideData?.otherOpenFiles?.takeIf { it.isNotEmpty() }?.let { files ->
                appendLine()
                appendLine("Other open files (${files.size}):")
                files.forEach { appendLine("  $it") }
            }

            // Git
            gitInfo?.let { git ->
                appendLine()
                appendLine(git)
            }

            appendLine("<!-- End IDE Context -->")
        }.trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Must be called inside a ReadAction
    // ─────────────────────────────────────────────────────────────────────────
    private fun readIdeData(project: Project): IdeData? {
        val editor: Editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: return null  // No active text editor — still might want git, but IdeData is null

        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
        val filePath    = virtualFile?.path
        val language    = virtualFile?.fileType?.name

        val selection = editor.selectionModel.let { sel ->
            if (sel.hasSelection()) sel.selectedText else null
        }

        val diagnostics = collectDiagnostics(project, editor)

        // All other open files except the active one
        val activeFilePath = filePath
        val otherOpenFiles = FileEditorManager.getInstance(project)
            .openFiles
            .mapNotNull { it.path.takeIf { p -> p != activeFilePath } }
            .take(10)

        return IdeData(
            filePath    = filePath,
            language    = language,
            cursorLine  = editor.caretModel.logicalPosition.line + 1,
            selection   = selection,
            diagnostics = diagnostics,
            otherOpenFiles = otherOpenFiles,
        )
    }

    private fun collectDiagnostics(project: Project, editor: Editor): List<String> {
        val markupModel =
            DocumentMarkupModel.forDocument(editor.document, project, false) ?: return emptyList()
        return markupModel.allHighlighters
            .filter { it.errorStripeTooltip != null }
            .take(10)
            .mapNotNull { h ->
                val tooltip = h.errorStripeTooltip?.toString() ?: return@mapNotNull null
                tooltip.replace(Regex("<[^>]+>"), "").trim().ifBlank { null }
            }
            .distinct()
    }

    // ─────────────────────────────────────────────────────────────────────────
    private data class IdeData(
        val filePath: String?,
        val language: String?,
        val cursorLine: Int,
        val selection: String?,
        val diagnostics: List<String>,
        val otherOpenFiles: List<String>,
    )
}

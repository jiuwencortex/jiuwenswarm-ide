import * as vscode from 'vscode';

/**
 * Collects IDE context (active file path, language, selection, diagnostics)
 * and formats it as a structured text block to prepend to outgoing chat messages.
 */
export function collectContext(): string | undefined {
  const editor = vscode.window.activeTextEditor;
  if (!editor) return undefined;

  const doc = editor.document;
  const filePath = doc.fileName;
  const lang = doc.languageId;

  const selection = editor.selection.isEmpty
    ? null
    : editor.document.getText(editor.selection);

  const diagnostics = collectDiagnostics(doc);

  // Nothing meaningful to send if we have no file info and no selection/diagnostics
  if (!filePath && !selection && diagnostics.length === 0) return undefined;

  const lines: string[] = [];
  lines.push('<!-- IDE Context -->');
  if (filePath) {
    lines.push(`Active file: ${filePath}  (${lang})`);
    lines.push(`Cursor line: ${editor.selection.active.line + 1}`);
  }
  if (selection && selection.trim()) {
    lines.push('');
    lines.push('Selected code:');
    lines.push('```');
    lines.push(selection.trimEnd());
    lines.push('```');
  }
  if (diagnostics.length > 0) {
    lines.push('');
    lines.push(`Diagnostics (${diagnostics.length}):`);
    diagnostics.forEach((d) => lines.push(`  \u2022 ${d}`));
  }
  lines.push('<!-- End IDE Context -->');
  return lines.join('\n');
}

function collectDiagnostics(doc: vscode.TextDocument): string[] {
  const diags = vscode.languages.getDiagnostics(doc.uri);
  const result: string[] = [];
  for (const d of diags) {
    // Only warnings and errors (not info/hint)
    if (d.severity === vscode.DiagnosticSeverity.Error || d.severity === vscode.DiagnosticSeverity.Warning) {
      const line = d.range.start.line + 1;
      const msg = d.message.replace(/\s+/g, ' ').trim();
      result.push(`Line ${line}: ${msg}`);
    }
    if (result.length >= 10) break;
  }
  return result;
}

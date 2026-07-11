import * as vscode from 'vscode';
import { execSync } from 'child_process';
import * as path from 'path';

/**
 * Collects IDE context (active file path, language, selection, diagnostics,
 * other open files, and git status) and formats it as a structured text block
 * to prepend to outgoing chat messages.
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

  // Other open files (excluding the active one)
  const otherOpenFiles = collectOtherOpenFiles(filePath);

  // Git context
  const gitInfo = collectGitContext(filePath);

  // Nothing meaningful if everything is empty
  if (!filePath && !selection && diagnostics.length === 0 && otherOpenFiles.length === 0 && !gitInfo) {
    return undefined;
  }

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
  if (otherOpenFiles.length > 0) {
    lines.push('');
    lines.push(`Other open files (${otherOpenFiles.length}):`);
    otherOpenFiles.forEach((f) => lines.push(`  ${f}`));
  }
  if (gitInfo) {
    lines.push('');
    lines.push(gitInfo);
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

function collectOtherOpenFiles(activePath: string): string[] {
  const files: string[] = [];
  for (const tab of vscode.window.tabGroups.all.flatMap((g) => g.tabs)) {
    if (tab.input instanceof vscode.TabInputText) {
      const uri = tab.input.uri.fsPath;
      if (uri && uri !== activePath) {
        files.push(uri);
      }
    }
  }
  return files.slice(0, 10);
}

function collectGitContext(filePath: string): string | undefined {
  try {
    const workDir = path.dirname(filePath);
    const branch = execSync('git rev-parse --abbrev-ref HEAD', {
      cwd: workDir,
      encoding: 'utf-8',
      timeout: 5000,
    }).trim();
    if (!branch) return undefined;

    const status = execSync('git status --porcelain', {
      cwd: workDir,
      encoding: 'utf-8',
      timeout: 5000,
    }).trim();
    const statusLines = status.split('\n').filter((l) => l.trim().length > 0);

    let result = `Git: branch=${branch}`;
    if (statusLines.length === 1) {
      result += ', 1 uncommitted change';
    } else if (statusLines.length > 1) {
      result += `, ${statusLines.length} uncommitted changes`;
    } else {
      result += ', clean';
    }
    return result;
  } catch {
    // Not a git repo, git not on PATH, or timed out — suppress silently
    return undefined;
  }
}

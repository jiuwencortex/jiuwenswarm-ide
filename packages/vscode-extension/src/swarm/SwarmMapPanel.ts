import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { SwarmSnapshot } from './SwarmState';

/**
 * Manages the VS Code WebviewPanel that renders the Live Swarm Map.
 * Mirrors SwarmMapPanel.kt for JetBrains.
 *
 * The webview signals readiness via { type: "swarm_ready" }; snapshots
 * posted before that are buffered and flushed on ready.
 */
export class SwarmMapPanel {
  private panel?: vscode.WebviewPanel;
  private pendingSnapshot?: SwarmSnapshot;
  private webviewReady = false;

  constructor(private readonly context: vscode.ExtensionContext) {}

  /** Open the panel (or bring it to front if already open). */
  show(): void {
    if (this.panel) {
      this.panel.reveal(vscode.ViewColumn.Beside, true);
      return;
    }

    this.webviewReady = false;
    this.panel = vscode.window.createWebviewPanel(
      'jiuwenswarm.swarmMap',
      'Swarm Map',
      { viewColumn: vscode.ViewColumn.Beside, preserveFocus: true },
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [vscode.Uri.joinPath(this.context.extensionUri, 'resources')],
      },
    );
    this.panel.iconPath = vscode.Uri.joinPath(this.context.extensionUri, 'resources', 'icon.svg');
    this.panel.webview.html = this.getHtml(this.panel.webview);

    this.panel.webview.onDidReceiveMessage((msg: Record<string, unknown>) => {
      if (msg['type'] === 'swarm_ready') {
        this.webviewReady = true;
        if (this.pendingSnapshot) {
          void this.panel?.webview.postMessage({ type: 'swarm_snapshot', snapshot: this.pendingSnapshot });
          this.pendingSnapshot = undefined;
        }
      }
      // Phase 3: handle open_lane, redirect, pause messages from the webview
    });

    this.panel.onDidDispose(() => {
      this.panel = undefined;
      this.webviewReady = false;
    });
  }

  /** Thread-safe: buffers snapshot if the panel is not yet ready. */
  postSnapshot(snapshot: SwarmSnapshot): void {
    if (!this.panel) return;
    if (!this.webviewReady) {
      this.pendingSnapshot = snapshot;
      return;
    }
    void this.panel.webview.postMessage({ type: 'swarm_snapshot', snapshot });
  }

  dispose(): void {
    this.panel?.dispose();
    this.panel = undefined;
  }

  private getHtml(webview: vscode.Webview): string {
    const htmlPath = path.join(this.context.extensionPath, 'resources', 'swarm_map.html');
    try {
      let html = fs.readFileSync(htmlPath, 'utf-8');
      html = html.replace(
        /<meta http-equiv="Content-Security-Policy"[^>]*>/,
        `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline' ${webview.cspSource}; script-src 'unsafe-inline' ${webview.cspSource}; img-src data: blob: ${webview.cspSource};">`,
      );
      return html;
    } catch {
      return this.getFallbackHtml();
    }
  }

  private getFallbackHtml(): string {
    return [
      '<!DOCTYPE html><html><head><meta charset="UTF-8">',
      '<style>body{background:#1e1e1e;color:#ccc;font-family:sans-serif;padding:12px}</style>',
      '</head><body><b>Swarm Map</b><br><br>swarm_map.html not found in extension resources.</body></html>',
    ].join('');
  }
}

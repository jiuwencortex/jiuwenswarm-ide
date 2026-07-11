import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { WsClient, WsStatus } from '../client/WsClient';
import { SessionManager } from '../client/SessionManager';
import { JiuwenMessage, ExtToWebviewMsg, WebviewToExtMsg } from '../client/protocol';

export class ChatPanel implements vscode.WebviewViewProvider {
  public static readonly viewId = 'jiuwenswarm.chatView';

  private view?: vscode.WebviewView;
  private readonly webviewHtmlPath: string;
  private messageListener?: (msg: JiuwenMessage) => void;
  private statusListener?: (s: WsStatus) => void;

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly ws: WsClient,
    private readonly session: SessionManager,
  ) {
    this.webviewHtmlPath = path.join(context.extensionPath, 'resources', 'chat.html');
  }

  resolveWebviewView(
    webviewView: vscode.WebviewView,
    _ctx: vscode.WebviewViewResolveContext,
    _token: vscode.CancellationToken,
  ): void {
    this.view = webviewView;
    webviewView.webview.options = {
      enableScripts: true,
      localResourceRoots: [
        vscode.Uri.joinPath(this.context.extensionUri, 'resources'),
      ],
    };
    webviewView.webview.html = this.getHtml(webviewView.webview);

    // Webview → extension
    webviewView.webview.onDidReceiveMessage((raw: WebviewToExtMsg) => {
      this.handleWebviewMessage(raw);
    });

    // WS events → webview
    this.messageListener = (msg) => this.onJiuwenMessage(msg);
    this.statusListener = (s) => this.onStatusChange(s);
    this.ws.on('message', this.messageListener);
    this.ws.on('status', this.statusListener);

    // Clean up when view is disposed
    webviewView.onDidDispose(() => {
      if (this.messageListener) this.ws.off('message', this.messageListener);
      if (this.statusListener) this.ws.off('status', this.statusListener);
    });

    // Send current status immediately if already connected
    if (this.ws.isConnected() && this.session.sessionId) {
      this.postToWebview({
        type: 'connected',
        sessionId: this.session.sessionId,
        sessionTitle: this.session.sessionTitle,
      });
    }
  }

  // Called from command: prepend selection as context
  appendSelectionContext(selection: string, filePath: string): void {
    if (!this.view) return;
    const contextPrefix = `[File: ${filePath}]\n\`\`\`\n${selection}\n\`\`\`\n\n`;
    this.postToWebview({ type: 'error', message: '' }); // just focus
    // Focus the input via a custom msg type
    this.view.webview.postMessage({ type: 'prepend_context', text: contextPrefix });
    this.view.show(true);
  }

  postToWebview(msg: ExtToWebviewMsg): void {
    this.view?.webview.postMessage(msg);
  }

  // ──────────────────────────────────────────
  // Webview → Extension
  // ──────────────────────────────────────────
  private handleWebviewMessage(msg: WebviewToExtMsg): void {
    switch (msg.type) {
      case 'ready':
        this.sendCurrentStatus();
        break;

      case 'send':
        if (!this.ws.isConnected() || !this.session.sessionId) {
          this.postToWebview({ type: 'error', message: 'Not connected to JiuwenSwarm' });
          return;
        }
        this.session.sendChat(msg.content, msg.mode, msg.requestId);
        break;

      case 'new_session':
        this.createSession();
        break;

      case 'switch_session':
        this.switchSession(msg.sessionId);
        break;

      case 'list_sessions':
        this.listSessions();
        break;
    }
  }

  // ──────────────────────────────────────────
  // JiuwenSwarm events → webview
  // ──────────────────────────────────────────
  private onJiuwenMessage(msg: JiuwenMessage): void {
    // Forward streaming events to the webview
    if (msg.type === 'event') {
      this.postToWebview({ type: 'jiuwen_event', event: msg });
    }
  }

  private onStatusChange(s: WsStatus): void {
    if (s === 'connected') {
      // Session may have been created before the view was resolved
      if (this.session.sessionId) {
        this.postToWebview({
          type: 'connected',
          sessionId: this.session.sessionId,
          sessionTitle: this.session.sessionTitle,
        });
      }
    } else if (s === 'reconnecting') {
      this.postToWebview({ type: 'reconnecting' });
    } else if (s === 'disconnected') {
      this.postToWebview({ type: 'disconnected' });
    }
  }

  // ──────────────────────────────────────────
  // Session helpers
  // ──────────────────────────────────────────
  private async createSession(): Promise<void> {
    try {
      const sid = await this.session.createSession();
      this.postToWebview({
        type: 'connected',
        sessionId: sid,
        sessionTitle: this.session.sessionTitle,
      });
    } catch (e) {
      this.postToWebview({ type: 'error', message: String(e) });
    }
  }

  private async switchSession(sessionId: string): Promise<void> {
    try {
      await this.session.switchSession(sessionId);
      this.postToWebview({
        type: 'connected',
        sessionId: this.session.sessionId!,
        sessionTitle: this.session.sessionTitle,
      });
    } catch (e) {
      this.postToWebview({ type: 'error', message: String(e) });
    }
  }

  private async listSessions(): Promise<void> {
    try {
      const sessions = await this.session.listSessions();
      this.postToWebview({ type: 'sessions', sessions });
    } catch (e) {
      this.postToWebview({ type: 'error', message: String(e) });
    }
  }

  private sendCurrentStatus(): void {
    const s = this.ws.getStatus();
    if (s === 'connected' && this.session.sessionId) {
      this.postToWebview({
        type: 'connected',
        sessionId: this.session.sessionId,
        sessionTitle: this.session.sessionTitle,
      });
    } else if (s === 'reconnecting') {
      this.postToWebview({ type: 'reconnecting' });
    } else {
      this.postToWebview({ type: 'disconnected' });
    }
  }

  // ──────────────────────────────────────────
  // HTML
  // ──────────────────────────────────────────
  private getHtml(webview: vscode.Webview): string {
    // Read the shared HTML from resources/chat.html
    try {
      let html = fs.readFileSync(this.webviewHtmlPath, 'utf-8');
      const nonce = getNonce();
      // Replace the entire CSP meta tag with a nonce-based one
      html = html.replace(
        /<meta http-equiv="Content-Security-Policy"[^>]*>/,
        `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline' ${webview.cspSource}; script-src 'nonce-${nonce}';">`,
      );
      // Add nonce to the single inline <script> block
      html = html.replace('<script>', `<script nonce="${nonce}">`);
      return html;
    } catch {
      // Fallback: minimal inline HTML if file not found
      return this.getFallbackHtml();
    }
  }

  private getFallbackHtml(): string {
    return `<!DOCTYPE html>
<html><head><meta charset="UTF-8">
<style>
body { background: #1e1e1e; color: #d4d4d4; font-family: sans-serif; padding: 16px; }
</style></head>
<body>
<p>⚠ Could not load chat UI.<br>
Make sure <code>resources/chat.html</code> exists in the extension folder.</p>
</body></html>`;
  }
}

function getNonce(): string {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  for (let i = 0; i < 32; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  return text;
}

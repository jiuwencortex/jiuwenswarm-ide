import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { WsClient, WsStatus } from '../client/WsClient';
import { SessionManager } from '../client/SessionManager';
import { JiuwenMessage, ExtToWebviewMsg } from '../client/protocol';
import { collectContext } from '../context/ContextCollector';

export class ChatPanel implements vscode.WebviewViewProvider {
  public static readonly viewId = 'jiuwenswarm.chatView';

  private view?: vscode.WebviewView;
  private readonly webviewHtmlPath: string;
  private disposables: vscode.Disposable[] = [];
  private debugEnabled = false;
  private lastRequestId = '';

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
    const d1 = webviewView.webview.onDidReceiveMessage((raw) => this.handleWebviewMessage(raw));
    this.disposables.push(d1);

    // WS events → webview
    const statusListener = (s: WsStatus) => this.onStatusChange(s);
    const msgListener = (m: JiuwenMessage) => this.onJiuwenMessage(m);
    const sessionListener = (sid: string | null) => this.onSessionChange(sid);
    this.ws.on('status', statusListener);
    this.ws.on('message', msgListener);
    this.session.addSessionListener(sessionListener);

    this.disposables.push(
      new vscode.Disposable(() => this.ws.off('status', statusListener)),
      new vscode.Disposable(() => this.ws.off('message', msgListener)),
      new vscode.Disposable(() => this.session.removeSessionListener(sessionListener)),
    );

    webviewView.onDidDispose(() => {
      this.disposables.forEach((d) => d.dispose());
      this.disposables = [];
    });

    // Send current status immediately if already connected
    if (this.ws.isConnected()) {
      this.sendCurrentStatus();
    }
  }

  postToWebview(msg: ExtToWebviewMsg): void {
    this.view?.webview.postMessage(msg);
  }

  // ──────────────────────────────────────────
  // Webview → Extension
  // ──────────────────────────────────────────
  private handleWebviewMessage(msg: Record<string, unknown>): void {
    const type = msg.type as string;
    switch (type) {
      case 'ready':
        this.sendCurrentStatus();
        break;

      case 'send': {
        const content = msg.content as string;
        const mode = (msg.mode as string) || 'agent.plan';
        const rid = msg.requestId as string;
        const mediaItems = msg.media_items as unknown[] | undefined;
        if (!rid) return;
        this.lastRequestId = rid;
        if (!this.ws.isConnected() || !this.session.sessionId) {
          this.postToWebview({ type: 'error', message: 'Not connected to JiuwenSwarm' });
          return;
        }
        this.debug(`SEND\u2192 requestId=${rid} mode=${mode} content=${content.substring(0, 60)}`);
        const ideContext = collectContext();
        if (!this.session.sendChat(content, mode, rid, ideContext || undefined, mediaItems)) {
          this.debug('SEND\u2192 FAILED (no session or disconnected)');
          this.postToWebview({ type: 'error', message: 'Not connected or no active session', requestId: rid });
        } else {
          this.debug('SEND\u2192 OK');
        }
        break;
      }

      case 'new_session': {
        if (!this.ws.isConnected()) {
          this.postToWebview({ type: 'error', message: 'Not connected' });
          return;
        }
        this.debug('ACTION\u2192 new_session (reconnecting for fresh session)');
        this.ws.reconnect();
        break;
      }

      case 'toggle_debug': {
        this.debugEnabled = (msg.enabled as boolean) || false;
        this.debug(`Debug mode toggled: ${this.debugEnabled}`);
        break;
      }

      case 'list_sessions': {
        this.listSessions();
        break;
      }

      case 'switch_session': {
        const sid = msg.sessionId as string;
        if (sid) this.switchSession(sid);
        break;
      }

      case 'set_mode': {
        // Mode is handled purely in the webview; no backend action needed
        break;
      }
    }
  }

  // ──────────────────────────────────────────
  // JiuwenSwarm events → webview
  // ──────────────────────────────────────────
  private onJiuwenMessage(msg: JiuwenMessage): void {
    const msgType = msg.type;
    // Forward streaming events to the webview
    if (msgType === 'event') {
      // Remap gateway "event" field → "event_type" so webview handlers work
      const legacy = this.convertToLegacyEvent(msg);
      if (legacy) {
        this.postToWebview({ type: 'jiuwen_event', event: legacy });
      }
    }
  }

  private convertToLegacyEvent(msg: JiuwenMessage): Record<string, unknown> | null {
    const eventName = msg.event;
    if (!eventName) return null;
    const payload = (msg.payload as Record<string, unknown>) || {};
    const rid = (msg.id as string)
      || (payload.request_id as string)
      || this.lastRequestId;
    return {
      event_type: eventName,
      request_id: rid,
      payload,
    };
  }

  private onStatusChange(s: WsStatus): void {
    this.sendCurrentStatus();
  }

  private onSessionChange(_sid: string | null): void {
    // Session changed (from connection.ack) — refresh status
    if (this.ws.isConnected()) {
      this.sendCurrentStatus();
    }
  }

  private sendCurrentStatus(): void {
    const s = this.ws.getStatus();
    const sid = this.session.sessionId;
    this.debug(`STATUS\u2192 ws=${s} session=${sid}`);
    if (s === 'connected' && sid) {
      // Fetch models in background
      this.session.listModels()
        .then(({ models, activeModel }) => {
          const modelList = models.map((m) => ({
            model_name: (m.model_name as string) || '',
            alias: (m.alias as string) || '',
            model_provider: (m.model_provider as string) || '',
          }));
          this.postToWebview({
            type: 'connected',
            sessionId: sid,
            sessionTitle: this.session.sessionTitle,
            models: modelList,
            activeModel: activeModel || undefined,
          });
        })
        .catch(() => {
          this.postToWebview({
            type: 'connected',
            sessionId: sid,
            sessionTitle: this.session.sessionTitle,
          });
        });
    } else if (s === 'connected') {
      this.postToWebview({
        type: 'connected',
        sessionId: null,
        sessionTitle: 'JiuwenSwarm',
        needsSession: true,
      });
    } else if (s === 'reconnecting') {
      this.postToWebview({ type: 'reconnecting' });
    } else {
      this.postToWebview({ type: 'disconnected' });
    }
  }

  // ──────────────────────────────────────────
  // Session helpers
  // ──────────────────────────────────────────
  private async listSessions(): Promise<void> {
    try {
      const sessions = await this.session.listSessions();
      this.postToWebview({ type: 'sessions', sessions });
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

  private debug(line: string): void {
    if (this.debugEnabled) {
      console.log(`[JiuwenSwarm] ${line}`);
      this.postToWebview({ type: 'debug_log', line });
    }
  }

  // ──────────────────────────────────────────
  // HTML
  // ──────────────────────────────────────────
  private getHtml(webview: vscode.Webview): string {
    try {
      let html = fs.readFileSync(this.webviewHtmlPath, 'utf-8');
      // Use 'unsafe-inline' for scripts so inline onclick handlers work.
      // img-src data: blob: allows base64 image previews from attachments.
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

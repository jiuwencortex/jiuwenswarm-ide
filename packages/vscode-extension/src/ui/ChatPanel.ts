import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { WsClient, WsStatus } from '../client/WsClient';
import { SessionManager } from '../client/SessionManager';
import { JiuwenMessage, ExtToWebviewMsg } from '../client/protocol';
import { collectContext } from '../context/ContextCollector';
import { StatusBar } from './StatusBar';
import * as DiffApplier from '../editor/DiffApplier';

export class ChatPanel implements vscode.WebviewViewProvider {
  public static readonly viewId = 'jiuwenswarm.chatView';

  private view?: vscode.WebviewView;
  private readonly webviewHtmlPath: string;
  private disposables: vscode.Disposable[] = [];
  private debugEnabled = false;
  private lastRequestId = '';
  private lastTokenCount = 0;

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly ws: WsClient,
    private readonly session: SessionManager,
    private readonly statusBar: StatusBar,
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
        // Clear snapshots from previous turn; rewind is no longer valid
        DiffApplier.clearSnapshots();
        this.postToWebview({ type: 'rewindable', enabled: false });
        if (!this.ws.isConnected() || !this.session.sessionId) {
          this.postToWebview({ type: 'error', message: 'Not connected to JiuwenSwarm' });
          return;
        }
        this.debug(`SEND→ requestId=${rid} mode=${mode} content=${content.substring(0, 60)}`);
        const ideContext = collectContext();
        if (!this.session.sendChat(content, mode, rid, ideContext || undefined, mediaItems)) {
          this.debug('SEND→ FAILED (no session or disconnected)');
          this.postToWebview({ type: 'error', message: 'Not connected or no active session', requestId: rid });
        } else {
          this.debug('SEND→ OK');
        }
        break;
      }

      case 'new_session': {
        if (!this.ws.isConnected()) {
          this.postToWebview({ type: 'error', message: 'Not connected' });
          return;
        }
        this.debug('ACTION→ new_session (reconnecting for fresh session)');
        DiffApplier.clearSnapshots();
        this.postToWebview({ type: 'rewindable', enabled: false });
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
        if (sid) {
          // session.switch is a team-mode server operation
          this.switchSession(sid, 'team');
        }
        break;
      }

      case 'delete_session': {
        const sid = msg.sessionId as string;
        if (sid) this.deleteSession(sid);
        break;
      }

      case 'list_skills': {
        this.listSkills();
        break;
      }

      case 'toggle_skill': {
        const skillId = msg.skillId as string;
        const enabled = msg.enabled as boolean;
        if (skillId !== undefined && enabled !== undefined) {
          this.toggleSkill(skillId, enabled);
        }
        break;
      }

      case 'set_mode': {
        // Mode is handled purely in the webview; no backend action needed
        break;
      }

      case 'open_file': {
        const filePath = msg.path as string;
        const line = (msg.line as number) || 0;
        if (filePath) this.openFile(filePath, line);
        break;
      }

      case 'rewind': {
        this.handleRewind();
        break;
      }
    }
  }

  // ──────────────────────────────────────────
  // JiuwenSwarm events → webview
  // ──────────────────────────────────────────
  private onJiuwenMessage(msg: JiuwenMessage): void {
    const msgType = msg.type;
    // Skip request-response protocol messages — SessionManager handles those
    if (msgType === 'res') return;

    this.debug(`RAW ← ${JSON.stringify(msg)}`);

    const converted = this.convertServerMessageToLegacyEvent(msg);
    if (!converted) {
      this.debug('CONV  → dropped (not a recognised chat event)');
      return;
    }

    const et = converted.event_type as string;
    const payload = (converted.payload as Record<string, unknown>) || {};

    // ── Snapshot & apply file-edit tool calls ──
    if (et === 'chat.tool_call') {
      const toolName = payload.tool_name as string | undefined;
      if (toolName === 'str_replace_editor' || toolName === 'write_file' || toolName === 'create_file') {
        const args = (payload.tool_call as Record<string, unknown>)?.arguments as Record<string, unknown>
          || (payload.tool_input as Record<string, unknown>)
          || (payload.input as Record<string, unknown>)
          || {};
        const filePath = args.path as string | undefined;
        if (filePath) {
          DiffApplier.ensureSnapshot(filePath);
          this.debug(`SNAP  → snapshotted ${filePath}`);
        }
      }
      // Apply the edit (async — snapshot already saved above)
      DiffApplier.handleToolCall(converted);
    }

    // ── On turn end, promote snapshots and show rewind bar ──
    if (et === 'chat.final') {
      if (DiffApplier.promoteSnapshots()) {
        this.postToWebview({ type: 'rewindable', enabled: true });
        this.debug('SNAP  → turn complete, rewindable');
      }
    }

    this.debug(`CONV  → event_type=${et} request_id=${converted.request_id}`);
    this.postToWebview({ type: 'jiuwen_event', event: converted });
    this.trackTokenUsage(converted);
  }

  /** Convert server messages (E2A or old format) to the legacy event format the webview expects.
   *  Webview expects: { event_type, request_id, payload }
   */
  private convertServerMessageToLegacyEvent(msg: JiuwenMessage): Record<string, unknown> | null {
    const responseKind = msg.response_kind
      || (msg.payload?.response_kind as string | undefined);

    // ── E2A format ──
    if (responseKind) {
      const requestId = msg.request_id
        || (msg.payload?.request_id as string)
        || '';
      const body = (msg.body as Record<string, unknown>)
        || (msg.payload?.body as Record<string, unknown>)
        || {};

      if (responseKind === 'e2a.chunk') {
        const eventType = (body.event_type as string) || '';
        const delta = body.delta;
        const payloadOut: Record<string, unknown> = {};
        if (eventType === 'chat.delta') {
          payloadOut.text = (delta as string) || '';
        } else if (eventType === 'chat.reasoning') {
          payloadOut.text = (delta as string) || '';
        } else if (delta && typeof delta === 'object') {
          Object.assign(payloadOut, delta);
        }
        return { event_type: eventType, request_id: requestId, payload: payloadOut };
      }

      if (responseKind === 'e2a.complete') {
        const result = (body.result as Record<string, unknown>) || body;
        const eventType = (result.event_type as string) || 'chat.final';
        return { event_type: eventType, request_id: requestId, payload: result };
      }

      if (responseKind === 'e2a.error') {
        const details = body.details as Record<string, unknown> | undefined;
        const errorMsg = (body.message as string) || 'Unknown error';
        const payloadOut: Record<string, unknown> = { error: errorMsg };
        if (details) payloadOut.details = details;
        return { event_type: 'chat.error', request_id: requestId, payload: payloadOut };
      }

      return null;
    }

    // ── Old format ──
    if (msg.type === 'event' && msg.event) {
      const payload = { ...(msg.payload || {}) };
      // Webview expects "text" for delta events, gateway sends "content"
      if (msg.event === 'chat.delta' && payload.content !== undefined && payload.text === undefined) {
        payload.text = payload.content;
      }
      const rid = (msg.id as string)
        || (payload.request_id as string)
        || this.lastRequestId
        || '';
      return {
        event_type: msg.event,
        request_id: rid,
        payload,
      };
    }

    return null;
  }

  /** Extract token counts from chat.usage_metadata / chat.usage_summary events. */
  private trackTokenUsage(event: Record<string, unknown>): void {
    const et = event.event_type as string;
    if (et !== 'chat.usage_metadata' && et !== 'chat.usage_summary') return;
    const payload = (event.payload as Record<string, unknown>) || {};
    const input = (payload.input_tokens as number)
      || (payload.cache_read_input_tokens as number)
      || 0;
    const output = (payload.output_tokens as number) || 0;
    this.lastTokenCount += input + output;
    this.statusBar.setTokenCount(this.lastTokenCount);
  }

  private onStatusChange(s: WsStatus): void {
    this.sendCurrentStatus();
  }

  private onSessionChange(sid: string | null): void {
    this.statusBar.setSessionId(sid);
    if (this.ws.isConnected()) {
      this.sendCurrentStatus();
    }
  }

  private sendCurrentStatus(): void {
    const s = this.ws.getStatus();
    const sid = this.session.sessionId;
    this.debug(`STATUS→ ws=${s} session=${sid}`);
    if (s === 'connected' && sid) {
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
      this.debug('ACTION→ list_sessions');
      const sessions = await this.session.listSessions();
      this.debug(`ACTION→ list_sessions returned ${sessions.length} sessions`);
      this.postToWebview({ type: 'sessions', sessions });
    } catch (e) {
      this.debug(`list_sessions failed: ${e}`);
      this.postToWebview({ type: 'error', message: String(e) });
    }
  }

  private async switchSession(sessionId: string, mode?: string): Promise<void> {
    try {
      this.debug(`ACTION→ switch_session ${sessionId} mode=${mode || 'default'}`);
      await this.session.switchSession(sessionId, mode);
      this.postToWebview({
        type: 'connected',
        sessionId: this.session.sessionId!,
        sessionTitle: this.session.sessionTitle,
      });
    } catch (e) {
      this.postToWebview({ type: 'error', message: String(e) });
    }
  }

  private async deleteSession(sessionId: string): Promise<void> {
    try {
      this.debug(`ACTION→ delete_session ${sessionId}`);
      await this.session.deleteSession(sessionId);
      this.postToWebview({ type: 'session_deleted', sessionId });
    } catch (e) {
      this.debug(`delete_session failed: ${e}`);
      this.postToWebview({ type: 'error', message: String(e) });
    }
  }

  // ──────────────────────────────────────────
  // Skills helpers
  // ──────────────────────────────────────────
  private async listSkills(): Promise<void> {
    try {
      this.debug('ACTION→ list_skills');
      const skills = await this.session.listSkills();
      this.debug(`ACTION→ list_skills returned ${skills.length} skills`);
      const skillMaps = skills.map((obj) => ({
        skill_id: (obj.skill_id as string) || '',
        name: (obj.name as string) || (obj.skill_id as string) || '',
        description: (obj.description as string) || '',
        enabled: (obj.enabled as boolean) ?? true,
        trigger: (obj.trigger as string) || '',
      }));
      this.postToWebview({ type: 'skills', skills: skillMaps });
    } catch (e) {
      this.debug(`list_skills failed: ${e}`);
      this.postToWebview({ type: 'skills_error', message: String(e) });
    }
  }

  private async toggleSkill(skillId: string, enabled: boolean): Promise<void> {
    try {
      this.debug(`ACTION→ toggle_skill ${skillId} enabled=${enabled}`);
      await this.session.toggleSkill(skillId, enabled);
      this.postToWebview({ type: 'skill_toggled', skillId, enabled });
    } catch (e) {
      this.debug(`toggle_skill failed: ${e}`);
      this.postToWebview({ type: 'skills_error', message: String(e) });
    }
  }

  // ──────────────────────────────────────────
  // File open
  // ──────────────────────────────────────────
  private async openFile(filePath: string, line: number): Promise<void> {
    try {
      const uri = vscode.Uri.file(filePath);
      const doc = await vscode.workspace.openTextDocument(uri);
      const editor = await vscode.window.showTextDocument(doc);
      if (line > 0) {
        const pos = new vscode.Position(Math.max(0, line - 1), 0);
        editor.selection = new vscode.Selection(pos, pos);
        editor.revealRange(new vscode.Range(pos, pos), vscode.TextEditorRevealType.InCenter);
      }
    } catch (e) {
      this.debug(`open_file failed: ${e}`);
    }
  }

  // ──────────────────────────────────────────
  // Rewind
  // ──────────────────────────────────────────
  private async handleRewind(): Promise<void> {
    const snapshots = DiffApplier.getLastTurnSnapshots();
    if (snapshots.size === 0) return;
    this.debug(`ACTION→ rewind ${snapshots.size} file(s)`);
    const { restored, failed } = await DiffApplier.performRewind(snapshots);
    DiffApplier.clearLastTurnSnapshots();
    const message = failed === 0
      ? `Rewound ${restored} file(s)`
      : `Rewound ${restored} file(s), ${failed} failed`;
    this.postToWebview({ type: 'rewind_done', message, restored, failed });
    this.debug(`REWIND→ ${message}`);
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

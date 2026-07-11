import { WsClient } from './WsClient';
import { JiuwenMessage, SessionInfo, makeRequest } from './protocol';

type PendingRequest = {
  resolve: (payload: Record<string, unknown>) => void;
  reject: (err: Error) => void;
  timer: NodeJS.Timeout;
};

const REQUEST_TIMEOUT_MS = 15_000;

export class SessionManager {
  private currentSessionId: string | null = null;
  private currentTitle = 'JiuwenSwarm';
  private pending = new Map<string, PendingRequest>();
  private channelId: string;

  constructor(private readonly ws: WsClient, channelId = 'ide') {
    this.channelId = channelId;
    this.ws.on('message', (msg) => this.handleMessage(msg));
  }

  get sessionId(): string | null { return this.currentSessionId; }
  get sessionTitle(): string { return this.currentTitle; }

  /** Create a new session and return its ID */
  async createSession(): Promise<string> {
    const msg = makeRequest('session.create', {}, undefined, this.channelId);
    const payload = await this.request(msg);
    const sid = payload.session_id as string;
    this.currentSessionId = sid;
    this.currentTitle = (payload.title as string) || 'New Session';
    return sid;
  }

  /** List recent sessions */
  async listSessions(limit = 20): Promise<SessionInfo[]> {
    const msg = makeRequest('session.list', { limit }, undefined, this.channelId);
    const payload = await this.request(msg);
    return (payload.sessions as SessionInfo[]) || [];
  }

  /** Switch to an existing session */
  async switchSession(sessionId: string): Promise<void> {
    const msg = makeRequest('session.switch', { session_id: sessionId }, sessionId, this.channelId);
    const payload = await this.request(msg);
    this.currentSessionId = sessionId;
    this.currentTitle = (payload.title as string) || sessionId;
  }

  /** Send a chat message — streaming events arrive via WsClient 'message' events */
  sendChat(content: string, mode: string, requestId: string): boolean {
    if (!this.currentSessionId) return false;
    const msg: JiuwenMessage = {
      id: requestId,
      type: 'req',
      channel_id: this.channelId,
      session_id: this.currentSessionId,
      req_method: 'chat.send',
      params: { content, mode },
      timestamp: Date.now() / 1000,
    };
    return this.ws.send(msg);
  }

  // ──────────────────────────────────────────
  // Internal
  // ──────────────────────────────────────────

  private request(msg: JiuwenMessage): Promise<Record<string, unknown>> {
    return new Promise((resolve, reject) => {
      const id = msg.id!;
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Request ${msg.req_method} timed out`));
      }, REQUEST_TIMEOUT_MS);

      this.pending.set(id, { resolve, reject, timer });
      if (!this.ws.send(msg)) {
        clearTimeout(timer);
        this.pending.delete(id);
        reject(new Error('WebSocket not connected'));
      }
    });
  }

  private handleMessage(msg: JiuwenMessage): void {
    // Match response to pending request
    if (msg.type === 'res' && msg.request_id) {
      const p = this.pending.get(msg.request_id);
      if (p) {
        clearTimeout(p.timer);
        this.pending.delete(msg.request_id);
        if (msg.ok) {
          p.resolve((msg.payload as Record<string, unknown>) || {});
        } else {
          p.reject(new Error((msg.payload as any)?.error || 'Request failed'));
        }
      }
    }
  }
}

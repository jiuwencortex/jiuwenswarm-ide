// JiuwenSwarm WebSocket protocol types

export interface JiuwenMessage {
  id?: string;
  type: 'req' | 'res' | 'event';
  channel_id?: string;
  session_id?: string;
  req_method?: string;
  event_type?: string;
  request_id?: string;
  params?: Record<string, unknown>;
  payload?: Record<string, unknown>;
  ok?: boolean;
  timestamp?: number;
}

export interface SessionInfo {
  session_id: string;
  title?: string;
  last_message_at?: number;
  created_at?: number;
  message_count?: number;
  mode?: string;
}

// Messages from extension host → webview
export type ExtToWebviewMsg =
  | { type: 'connected'; sessionId: string; sessionTitle: string }
  | { type: 'disconnected' }
  | { type: 'reconnecting' }
  | { type: 'sessions'; sessions: SessionInfo[] }
  | { type: 'jiuwen_event'; event: JiuwenMessage }
  | { type: 'error'; message: string };

// Messages from webview → extension host
export type WebviewToExtMsg =
  | { type: 'ready' }
  | { type: 'send'; content: string; mode: string; requestId: string }
  | { type: 'new_session' }
  | { type: 'switch_session'; sessionId: string }
  | { type: 'list_sessions' };

export function makeRequest(
  method: string,
  params: Record<string, unknown> = {},
  sessionId?: string,
  channelId = 'ide',
): JiuwenMessage {
  return {
    id: crypto.randomUUID ? crypto.randomUUID() : randomId(),
    type: 'req',
    channel_id: channelId,
    session_id: sessionId,
    req_method: method,
    params,
    timestamp: Date.now() / 1000,
  };
}

function randomId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

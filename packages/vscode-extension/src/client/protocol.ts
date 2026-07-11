// JiuwenSwarm WebSocket protocol types

export interface JiuwenMessage {
  id?: string;
  type: 'req' | 'res' | 'event';
  channel_id?: string;
  method?: string;           // gateway uses "method" not "req_method"
  event?: string;            // gateway event name inside "event" field
  ok?: boolean;              // response status
  params?: Record<string, unknown>;
  payload?: Record<string, unknown>;
  timestamp?: number;
}

export interface SessionInfo {
  session_id: string;
  title?: string;
  last_message_at?: number;
  created_at?: number;
  message_count?: number;
}

// Messages from extension host → webview
export type ExtToWebviewMsg =
  | { type: 'connected'; sessionId: string | null; sessionTitle: string; needsSession?: boolean; models?: ModelEntry[]; activeModel?: string }
  | { type: 'disconnected' }
  | { type: 'reconnecting' }
  | { type: 'sessions'; sessions: SessionInfo[] }
  | { type: 'jiuwen_event'; event: JiuwenMessage }
  | { type: 'error'; message?: string; requestId?: string }
  | { type: 'debug_log'; line: string };

export interface ModelEntry {
  model_name: string;
  alias?: string;
  model_provider?: string;
}

// Messages from webview → extension host
export type WebviewToExtMsg =
  | { type: 'ready' }
  | { type: 'send'; content: string; mode: string; requestId: string; media_items?: unknown[] }
  | { type: 'new_session' }
  | { type: 'switch_session'; sessionId: string }
  | { type: 'list_sessions' }
  | { type: 'toggle_debug'; enabled: boolean }
  | { type: 'set_mode'; mode: string };

function randomId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function makeRequest(
  method: string,
  params: Record<string, unknown> = {},
  channelId = 'ide',
): JiuwenMessage {
  return {
    id: randomId(),
    type: 'req',
    channel_id: channelId,
    method,
    params,
    timestamp: Date.now() / 1000,
  };
}

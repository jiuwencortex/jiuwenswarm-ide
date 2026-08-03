# Live Swarm Map — Development Plan

Visual real-time panel showing active sub-agents, their current task, and live tool
activity during a JiuwenSwarm `code.team` session inside the IDE.

---

## 1. Wire format — what the server already sends

Team events travel as regular streaming text deltas. The gateway sends them as
`chat.delta` events whose `delta` (JetBrains) / `text` (VS Code webview) field is a
JSON string prefixed with `team.event:`.

**Example raw WebSocket frame (E2A `e2a.chunk`):**
```json
{
  "response_kind": "e2a.chunk",
  "request_id": "req-abc123",
  "channel_id": "chan-xyz",
  "body": {
    "event_type": "chat.delta",
    "delta": "team.event:{\"event\":{\"type\":\"team.member.spawned\",\"member_name\":\"alice\",\"display_name\":\"Alice — Researcher\",\"role\":\"TEAMMATE\",\"timestamp\":1722694000}}"
  }
}
```

After `convertServerMessageToLegacyEvent` this becomes:
```json
{
  "event_type": "chat.delta",
  "request_id": "req-abc123",
  "payload": { "text": "team.event:{\"event\":{...}}" }
}
```

The chat webview currently renders this as raw text. The swarm map feature intercepts
these deltas in the plugin layer and suppresses their forwarding to the chat webview.

**Known event types inside the `event.type` field:**

| `event.type` value | Key fields |
|---|---|
| `team.member.spawned` | `member_name`, `display_name`, `role` |
| `team.member.status_changed` | `member_name`, `status` (READY/BUSY/PAUSED/SHUTDOWN) |
| `team.member.execution_changed` | `member_name`, `execution_status` (IDLE/RUNNING/COMPLETING) |
| `team.member.shutdown` | `member_name` |
| `team.task.created` | `task_id`, `title`, `content`, `status` |
| `team.task.claimed` | `task_id`, `assignee` |
| `team.task.started` | `task_id`, `assignee` |
| `team.task.completed` | `task_id`, `assignee` |
| `team.task.cancelled` | `task_id` |
| `team.message.p2p` | `from_member`, `to_member`, `content`, `message_id` |
| `team.message.broadcast` | `from_member`, `content`, `message_id` |

> **Verify before coding:** run a real `code.team` session, set `debugEnabled = true`
> in the plugin, and grep the IDEA log for `"RAW ←"` entries that contain `"team.event:"`.
> Confirm the wrapper key (`event` vs `payload.event`) and the exact field names.

---

## 2. Data model

Owned by the plugin layer, not the webview. Serialised to JSON and pushed to the
Swarm Map panel as a full snapshot after every mutation.

### Kotlin (JetBrains)

```kotlin
// openjiuwen/harness/swarm/SwarmState.kt  (new file, shared model)
data class AgentLane(
    val memberName: String,
    val displayName: String,
    val role: String,                     // "LEADER" | "TEAMMATE" | "WORKER"
    var status: String = "READY",         // "READY" | "BUSY" | "PAUSED" | "SHUTDOWN"
    var executionStatus: String = "IDLE", // "IDLE" | "RUNNING" | "COMPLETING"
    var currentTaskId: String? = null,
    var currentTaskTitle: String? = null,
    var currentActivity: String? = null,  // human-readable: "write_file · auth.service.ts:44"
    var lastToolName: String? = null,
    var lastActivePath: String? = null,   // full file path for jump-to-file navigation
    var lastActiveAt: Long = System.currentTimeMillis(),
    var messageCount: Int = 0,
    var tasksDone: Int = 0,
)

data class TeamTask(
    val taskId: String,
    var title: String,
    var status: String,   // "pending" | "in_progress" | "completed" | "cancelled"
    var assignee: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One inter-agent message captured from team.message.* events. Ring buffer max 50. */
data class TeamMessage(
    val from: String,
    val to: String?,      // null means broadcast
    val content: String,
    val timestamp: Long,
)

data class SwarmSnapshot(
    val sessionId: String,
    val teamName: String,
    val lanes: List<AgentLane>,      // sorted: LEADER, BUSY, READY, PAUSED, SHUTDOWN
    val tasks: List<TeamTask>,       // sorted: in_progress, pending, completed, cancelled
    val messages: List<TeamMessage>, // last 50 inter-agent messages, chronological
    val lastEventAt: Long,
)
```

### TypeScript (VS Code) — mirrors the Kotlin model exactly

```typescript
// packages/vscode-extension/src/swarm/SwarmState.ts  (new file)
export interface AgentLane {
  memberName: string;
  displayName: string;
  role: 'LEADER' | 'TEAMMATE' | 'WORKER';
  status: 'READY' | 'BUSY' | 'PAUSED' | 'SHUTDOWN';
  executionStatus: 'IDLE' | 'RUNNING' | 'COMPLETING';
  currentTaskId: string | null;
  currentTaskTitle: string | null;
  currentActivity: string | null;
  lastToolName: string | null;
  lastActivePath: string | null;  // full file path for jump-to-file navigation
  lastActiveAt: number;           // epoch ms
  messageCount: number;
  tasksDone: number;
}

export interface TeamTask {
  taskId: string;
  title: string;
  status: 'pending' | 'in_progress' | 'completed' | 'cancelled';
  assignee: string | null;
  createdAt: number;
}

/** One inter-agent message captured from team.message.* events. Ring buffer max 50. */
export interface TeamMessage {
  from: string;
  to: string | null;   // null means broadcast
  content: string;
  timestamp: number;
}

export interface SwarmSnapshot {
  sessionId: string;
  teamName: string;
  lanes: AgentLane[];
  tasks: TeamTask[];
  messages: TeamMessage[];  // last 50 inter-agent messages, chronological
  lastEventAt: number;
}
```

---

## 3. SwarmStateManager

One instance per open project (JetBrains) or per extension activation (VS Code).
Mutates in place; the only output is `snapshot()`.

### Interface (same contract in both languages)

```kotlin
class SwarmStateManager {
    // Called by ChatPanel.onJiuwenMessage when a chat.delta text starts with "team.event:"
    fun applyTeamEvent(rawJson: String)

    // Called from the chat.tool_call branch of onJiuwenMessage
    // memberName may be null if not present in the payload (see open question §7)
    fun applyToolCall(toolName: String, filePath: String?, memberName: String?)

    // Returns serialised JSON ready to post to the Swarm Map panel
    fun snapshot(): SwarmSnapshot

    // Called from onSessionChange / switchSession — wipes all state
    fun reset(newSessionId: String)
}
```

### State mutation table

| Event type | Fields mutated |
|---|---|
| `team.member.spawned` | Create `AgentLane(memberName, displayName, role)` in lanes map |
| `team.member.status_changed` | `lane.status = event.status`; `lane.lastActiveAt = now` |
| `team.member.execution_changed` | `lane.executionStatus = event.execution_status`; `lane.lastActiveAt = now` |
| `team.member.shutdown` | `lane.status = "SHUTDOWN"`; `lane.executionStatus = "IDLE"` |
| `team.task.created` | Add `TeamTask(taskId, title, "pending")` to tasks list |
| `team.task.claimed` | `task.assignee = event.assignee`; `task.status = "pending"` (still pending until started) |
| `team.task.started` | `task.status = "in_progress"`; `lane.currentTaskId = task.taskId`; `lane.currentTaskTitle = task.title` |
| `team.task.completed` | `task.status = "completed"`; clear `lane.currentTaskId/Title`; increment `lane.tasksDone` |
| `team.task.cancelled` | `task.status = "cancelled"`; clear `lane.currentTaskId/Title` if it matched |
| `team.message.p2p` | `lane.messageCount++` on `from_member` lane; `lane.lastActiveAt = now`; if `content` present, append `TeamMessage(from, to, content, ts)` to ring buffer (max 50) |
| `team.message.broadcast` | same as p2p but `to = null` |
| `applyToolCall(...)` | `lane.lastToolName = toolName`; `lane.currentActivity = format(toolName, filePath)`; `lane.lastActivePath = filePath`; `lane.lastActiveAt = now` |

**`format(toolName, filePath)` rules:**
- `write_file`, `str_replace_editor` → `"writing · {basename(filePath)}"` (or `"editing · {basename}"` for str_replace)
- `read_file` → `"reading · {basename(filePath)}"`
- `bash`, `run_command` → `"running · {first 40 chars of command}"` (filePath carries the command string here)
- `search_files`, `grep` → `"searching · {filePath}"`
- anything else → `"{toolName}"`

**Sorting for `snapshot()`:**
```
lanes: LEADER first, then by status priority: BUSY > RUNNING > READY > PAUSED > SHUTDOWN
       within same status: sort by lastActiveAt descending
tasks: in_progress > pending > completed > cancelled
       within same status: sort by createdAt ascending
```

**Guard:** if `applyTeamEvent` is called before any `team.member.spawned` for that
`member_name` (race condition), create a stub lane with `memberName` as `displayName`
and role `"TEAMMATE"` and apply the event to it.

---

## 4. Integration into existing plugin files

### JetBrains — `ChatToolWindow.kt`

**New field** (add alongside `lastRequestId`):
```kotlin
private val swarmStateManager = SwarmStateManager()
```

**In `onJiuwenMessage`**, insert before the `convertServerMessageToLegacyEvent` call:

```kotlin
// ── Intercept team.event: deltas for the Swarm Map panel ──
val rawTeamEvent = extractTeamEventDelta(msg)
if (rawTeamEvent != null) {
    swarmStateManager.applyTeamEvent(rawTeamEvent)
    swarmMapPanel?.postSnapshot(swarmStateManager.snapshot())
    // Suppress: do NOT forward team event text to the chat webview
    return
}
```

**`extractTeamEventDelta(msg: JsonObject): String?`** — new private function:
```kotlin
private fun extractTeamEventDelta(msg: JsonObject): String? {
    // E2A chunk path: msg.body.delta starts with "team.event:"
    val body = msg.getAsJsonObject("body")
    val delta = body?.get("delta")?.asString
    if (delta?.startsWith("team.event:") == true) return delta.removePrefix("team.event:")

    // Old-format path: msg.type == "event" && msg.event starts with "team."
    if (msg.get("type")?.asString == "event") {
        val evtName = msg.get("event")?.asString ?: ""
        if (evtName.startsWith("team.")) {
            // Re-wrap in the team.event: envelope format SwarmStateManager expects
            val payload = msg.getAsJsonObject("payload") ?: JsonObject()
            payload.addProperty("type", evtName)
            return gson.toJson(JsonObject().apply { add("event", payload) })
        }
    }
    return null
}
```

**In the `chat.tool_call` branch** (already exists around line 525–554), add after the
existing tool-name extraction:
```kotlin
val memberName = payload.get("member_name")?.asString  // may be null
swarmStateManager.applyToolCall(toolName, path, memberName)
swarmMapPanel?.postSnapshot(swarmStateManager.snapshot())
```

**In `onSessionChange`**:
```kotlin
swarmStateManager.reset(sid ?: "")
swarmMapPanel?.postSnapshot(swarmStateManager.snapshot())
```

**Auto-open the Swarm Map panel** — in `swarmStateManager.applyTeamEvent` post-hook:
```kotlin
if (rawTeamEvent != null) {
    val isFirstSpawn = swarmStateManager.snapshot().lanes.size == 1 &&
        swarmStateManager.snapshot().lanes[0].status != "SHUTDOWN"
    if (isFirstSpawn) {
        ApplicationManager.getApplication().invokeLater {
            SwarmMapToolWindowFactory.openOrReveal(project)
        }
    }
    ...
}
```

**New field** (nullable, set by `SwarmMapToolWindowFactory`):
```kotlin
var swarmMapPanel: SwarmMapPanel? = null
```

### VS Code — `ChatPanel.ts`

**New field**:
```typescript
private readonly swarmState: SwarmStateManager;
// initialised in constructor: this.swarmState = new SwarmStateManager();
```

**In `onJiuwenMessage`**, before `convertServerMessageToLegacyEvent`:
```typescript
const teamEventJson = extractTeamEventDelta(msg);
if (teamEventJson !== null) {
    this.swarmState.applyTeamEvent(teamEventJson);
    this.swarmMapPanel?.postSnapshot(this.swarmState.snapshot());
    return;   // suppress from chat webview
}
```

**`extractTeamEventDelta(msg: JiuwenMessage): string | null`**:
```typescript
function extractTeamEventDelta(msg: JiuwenMessage): string | null {
    const body = (msg as any).body as Record<string, unknown> | undefined;
    const delta = body?.delta as string | undefined;
    if (delta?.startsWith('team.event:')) return delta.slice('team.event:'.length);

    if (msg.type === 'event' && typeof (msg as any).event === 'string') {
        const evtName = (msg as any).event as string;
        if (evtName.startsWith('team.')) {
            const payload = { ...((msg as any).payload ?? {}), type: evtName };
            return JSON.stringify({ event: payload });
        }
    }
    return null;
}
```

**In `onSessionChange`**:
```typescript
this.swarmState.reset(sid ?? '');
this.swarmMapPanel?.postSnapshot(this.swarmState.snapshot());
```

**New field**:
```typescript
private swarmMapPanel: SwarmMapPanel | null = null;
```

---

## 5. SwarmMapToolWindow — JetBrains

### New file: `SwarmMapToolWindowFactory.kt`

```kotlin
package com.jiuwenswarm.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class SwarmMapToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SwarmMapPanel(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)

        // Wire the panel back to ChatPanel so ChatPanel can push snapshots
        val chatPanel = findChatPanel(project)
        chatPanel?.swarmMapPanel = panel
    }

    override fun shouldBeAvailable(project: Project) = true

    companion object {
        fun openOrReveal(project: Project) {
            val tw = ToolWindowManager.getInstance(project).getToolWindow("JiuwenSwarm Swarm") ?: return
            if (!tw.isVisible) tw.show()
        }

        private fun findChatPanel(project: Project): ChatPanel? {
            val chatTw = ToolWindowManager.getInstance(project).getToolWindow("JiuwenSwarm") ?: return null
            return chatTw.contentManager.contents
                .mapNotNull { it.component.getClientProperty("jiuwenswarm.panel") as? ChatPanel }
                .firstOrNull()
        }
    }
}
```

### New file: `SwarmMapPanel.kt`

```kotlin
package com.jiuwenswarm.plugin.ui

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.jiuwenswarm.plugin.swarm.SwarmSnapshot
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent

class SwarmMapPanel(
    private val project: Project,
    toolWindow: ToolWindow,
) : Disposable {

    private val gson = Gson()
    private val browser = JBCefBrowser()
    private val jsQuery: JBCefJSQuery
    @Volatile private var ready = false
    private val pendingSnapshot = java.util.concurrent.atomic.AtomicReference<SwarmSnapshot?>()

    val component: JComponent get() = browser.component

    init {
        jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        jsQuery.addHandler { msg -> handleWebviewMessage(msg); JBCefJSQuery.Response("ok") }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                injectBridge()
                ready = true
                pendingSnapshot.getAndSet(null)?.let { postSnapshot(it) }
            }
        }, browser.cefBrowser)

        val htmlPath = javaClass.getResource("/webview/swarm_map.html")
            ?: error("swarm_map.html not found in plugin resources")
        browser.loadURL(htmlPath.toExternalForm())

        Disposer.register(toolWindow.disposable, this)
    }

    /** Push a full state snapshot to the HTML panel. Thread-safe. */
    fun postSnapshot(snapshot: SwarmSnapshot) {
        if (!ready) { pendingSnapshot.set(snapshot); return }
        val json = gson.toJson(snapshot)
        browser.cefBrowser.executeJavaScript(
            "window.__swarmReceive && window.__swarmReceive(${json});", "", 0
        )
    }

    private fun injectBridge() {
        val inject = jsQuery.inject(
            "payload",
            "function(r){}", "function(c,m){}"
        )
        browser.cefBrowser.executeJavaScript("""
            window.__swarmSend = function(payload) { $inject };
        """.trimIndent(), "", 0)
    }

    private fun handleWebviewMessage(raw: String) {
        // Currently: { "type": "open_lane", "memberName": "alice" }
        // Delegate to ChatPanel via shared service lookup (Phase 3)
    }

    override fun dispose() {
        browser.dispose()
    }
}
```

### `plugin.xml` addition (inside `<extensions defaultExtensionNs="com.intellij">`)

```xml
<!-- Swarm Map tool window (opens automatically when a team session starts) -->
<toolWindow
    id="JiuwenSwarm Swarm"
    secondary="true"
    icon="/icons/jiuwenswarm.svg"
    anchor="right"
    factoryClass="com.jiuwenswarm.plugin.ui.SwarmMapToolWindowFactory"/>
```

`secondary="true"` means it appears as a second tab next to the main chat panel
rather than pushing it out. The `anchor="right"` keeps it in the same sidebar.

---

## 6. SwarmMapPanel — VS Code

### New file: `packages/vscode-extension/src/swarm/SwarmMapPanel.ts`

```typescript
import * as vscode from 'vscode';
import * as path from 'path';
import { SwarmSnapshot } from './SwarmState';

export class SwarmMapPanel implements vscode.Disposable {
    private static _instance: SwarmMapPanel | undefined;
    private readonly _panel: vscode.WebviewPanel;
    private _ready = false;
    private _pendingSnapshot: SwarmSnapshot | null = null;
    private readonly _disposables: vscode.Disposable[] = [];

    private constructor(extensionPath: string) {
        this._panel = vscode.window.createWebviewPanel(
            'jiuwenswarmSwarmMap',
            'JiuwenSwarm · Swarm',
            { viewColumn: vscode.ViewColumn.Beside, preserveFocus: true },
            {
                enableScripts: true,
                localResourceRoots: [
                    vscode.Uri.file(path.join(extensionPath, 'resources'))
                ],
            }
        );

        const htmlPath = vscode.Uri.file(
            path.join(extensionPath, 'resources', 'swarm_map.html')
        );
        this._panel.webview.html = require('fs').readFileSync(htmlPath.fsPath, 'utf8');

        this._panel.webview.onDidReceiveMessage(
            (msg) => this._handleMessage(msg),
            undefined,
            this._disposables
        );
        this._panel.onDidDispose(() => this._onDispose(), undefined, this._disposables);

        // Panel ready is signalled by the webview posting { type: 'swarm_ready' }
    }

    static getOrCreate(extensionPath: string): SwarmMapPanel {
        if (!SwarmMapPanel._instance) {
            SwarmMapPanel._instance = new SwarmMapPanel(extensionPath);
        }
        return SwarmMapPanel._instance;
    }

    postSnapshot(snapshot: SwarmSnapshot): void {
        if (!this._ready) { this._pendingSnapshot = snapshot; return; }
        this._panel.webview.postMessage({ type: 'swarm_snapshot', state: snapshot });
    }

    reveal(): void {
        this._panel.reveal(vscode.ViewColumn.Beside, true);
    }

    private _handleMessage(msg: { type: string; [k: string]: unknown }): void {
        if (msg.type === 'swarm_ready') {
            this._ready = true;
            if (this._pendingSnapshot) {
                this.postSnapshot(this._pendingSnapshot);
                this._pendingSnapshot = null;
            }
        }
        // open_lane: Phase 3
    }

    private _onDispose(): void {
        SwarmMapPanel._instance = undefined;
        this._disposables.forEach(d => d.dispose());
    }

    dispose(): void { this._panel.dispose(); }
}
```

**No `package.json` / `contributes` change needed** — `createWebviewPanel` requires no
manifest registration. The panel is created on demand.

---

## 7. `swarm_map.html` — complete skeleton

Single file, no build step, no framework. Mirrors the patterns in `chat.html`.

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta http-equiv="Content-Security-Policy"
      content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';"/>
<style>
/* ── Reset ── */
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: var(--vscode-font-family, system-ui, sans-serif);
  font-size: 12px;
  background: var(--vscode-sideBar-background, #1e1e1e);
  color: var(--vscode-foreground, #ccc);
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ── Header ── */
#header {
  padding: 6px 10px;
  border-bottom: 1px solid var(--vscode-sideBarSectionHeader-border, #333);
  font-weight: 600;
  font-size: 11px;
  letter-spacing: .05em;
  text-transform: uppercase;
  color: var(--vscode-sideBarSectionHeader-foreground, #aaa);
  flex-shrink: 0;
}

/* ── Task board ── */
#task-board {
  padding: 6px 10px;
  border-bottom: 1px solid var(--vscode-sideBarSectionHeader-border, #333);
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
  min-height: 30px;
}
.task-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: 10px;
  font-size: 11px;
  max-width: 220px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.task-pill.in_progress { background: #1e3a1e; border: 1px solid #3a7a3a; color: #7ec87e; }
.task-pill.pending     { background: #2a2a1e; border: 1px solid #6a6a3a; color: #c8c87e; }
.task-pill.completed   { background: #1a1a1a; border: 1px solid #444; color: #666; }
.task-pill.cancelled   { background: #1a1a1a; border: 1px solid #444; color: #666; text-decoration: line-through; }
.task-assignee { font-size: 10px; opacity: .7; }

/* ── Lane list ── */
#lanes {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

/* ── Lane card ── */
.lane-card {
  border: 1px solid var(--vscode-widget-border, #333);
  border-radius: 5px;
  padding: 8px 10px;
  cursor: pointer;
  transition: border-color .15s;
  position: relative;
}
.lane-card:hover { border-color: var(--vscode-focusBorder, #007acc); }
.lane-card.status-SHUTDOWN { opacity: .45; }
.lane-card.status-BUSY   { border-left: 3px solid #3a7a3a; }
.lane-card.status-PAUSED { border-left: 3px solid #7a7a3a; }
.lane-card.status-READY  { border-left: 3px solid #444; }

.lane-name {
  font-weight: 600;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.lane-role-badge {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 8px;
  text-transform: uppercase;
  letter-spacing: .04em;
  background: #333;
  color: #aaa;
}
.lane-role-badge.LEADER { background: #2a2045; color: #a07ae8; }

.status-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}
.status-dot.BUSY     { background: #4ec94e; animation: pulse 1.4s ease-in-out infinite; }
.status-dot.PAUSED   { background: #c9c94e; }
.status-dot.READY    { background: #666; }
.status-dot.SHUTDOWN { background: #444; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: .35; }
}

.lane-activity {
  margin-top: 4px;
  font-size: 11px;
  color: var(--vscode-descriptionForeground, #888);
  font-family: var(--vscode-editor-font-family, monospace);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lane-task {
  margin-top: 2px;
  font-size: 11px;
  color: var(--vscode-descriptionForeground, #888);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.lane-idle-warning {
  position: absolute;
  top: 8px; right: 8px;
  font-size: 10px;
  color: #c9c94e;
}

/* ── Timeline bar ── */
#timeline {
  height: 28px;
  flex-shrink: 0;
  padding: 4px 10px;
  border-top: 1px solid var(--vscode-sideBarSectionHeader-border, #333);
  position: relative;
  overflow: hidden;
}
.timeline-track {
  position: absolute;
  top: 8px; height: 4px;
  border-radius: 2px;
  opacity: .75;
}

/* ── Empty state ── */
#empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--vscode-descriptionForeground, #666);
  font-size: 12px;
  text-align: center;
  padding: 20px;
}
</style>
</head>
<body>

<div id="header">JiuwenSwarm · Swarm Map</div>

<div id="task-board"></div>

<div id="lanes">
  <div id="empty">No active team session.<br>Start a conversation in <code>code.team</code> mode.</div>
</div>

<div id="timeline"></div>

<script>
// ── Constants ──────────────────────────────────────────
var IDLE_WARN_MS  = 30_000;   // show amber warning after 30s of BUSY with no activity
var TIMELINE_WINDOW_MS = 90_000;  // last 90 seconds shown in timeline bar

// Colour per member (assigned round-robin from this palette)
var LANE_COLOURS = ['#4ec94e','#4ea8e8','#e8a84e','#c94ee8','#e84e4e','#4ee8c9'];
var _laneColourMap = {};  // memberName → colour

// ── State ──────────────────────────────────────────────
var _state = null;        // SwarmSnapshot | null
var _activeSince = {};    // memberName → epoch ms when member became BUSY

// ── Entry point ────────────────────────────────────────
// Called by JetBrains (via executeJavaScript) or VS Code (via window.addEventListener)
window.__swarmReceive = function(snapshot) {
  _state = snapshot;
  render();
};

// VS Code host → webview
window.addEventListener('message', function(e) {
  var msg = e.data;
  if (msg && msg.type === 'swarm_snapshot') {
    window.__swarmReceive(msg.state);
  }
});

// Signal ready to VS Code host
if (typeof acquireVsCodeApi !== 'undefined') {
  var _vscode = acquireVsCodeApi();
  _vscode.postMessage({ type: 'swarm_ready' });
}

// ── Rendering ──────────────────────────────────────────
function render() {
  if (!_state || _state.lanes.length === 0) {
    showEmpty(true);
    return;
  }
  showEmpty(false);
  renderTaskBoard(_state.tasks);
  renderLanes(_state.lanes);
  renderTimeline(_state.lanes);
}

function showEmpty(visible) {
  document.getElementById('empty').style.display = visible ? 'flex' : 'none';
}

function renderTaskBoard(tasks) {
  var board = document.getElementById('task-board');
  board.innerHTML = '';
  tasks.forEach(function(task) {
    var pill = document.createElement('div');
    pill.className = 'task-pill ' + task.status;
    var icon = task.status === 'completed' ? '✓'
             : task.status === 'cancelled' ? '✗'
             : '⚙';
    var assigneePart = task.assignee ? ' <span class="task-assignee">→ ' + esc(task.assignee) + '</span>' : '';
    pill.innerHTML = icon + ' ' + esc(truncate(task.title, 28)) + assigneePart;
    pill.title = task.title + (task.assignee ? ' → ' + task.assignee : '');
    board.appendChild(pill);
  });
}

function renderLanes(lanes) {
  var container = document.getElementById('lanes');
  // Remove empty placeholder; keep existing cards or rebuild
  var placeholder = document.getElementById('empty');
  if (placeholder) placeholder.style.display = 'none';

  // Build a map of existing cards by memberName for in-place update
  var existing = {};
  container.querySelectorAll('.lane-card').forEach(function(el) {
    existing[el.dataset.member] = el;
  });

  // Render in sorted order; append missing, update existing, remove stale
  var rendered = {};
  lanes.forEach(function(lane, i) {
    rendered[lane.memberName] = true;
    assignColour(lane.memberName);

    var card = existing[lane.memberName] || createLaneCard(lane.memberName);
    updateLaneCard(card, lane, i);
    if (!existing[lane.memberName]) container.appendChild(card);
  });

  // Remove cards for members no longer in state
  Object.keys(existing).forEach(function(name) {
    if (!rendered[name]) existing[name].remove();
  });
}

function createLaneCard(memberName) {
  var card = document.createElement('div');
  card.className = 'lane-card';
  card.dataset.member = memberName;
  card.innerHTML = [
    '<div class="lane-name">',
    '  <span class="status-dot"></span>',
    '  <span class="lane-display-name"></span>',
    '  <span class="lane-role-badge"></span>',
    '</div>',
    '<div class="lane-activity"></div>',
    '<div class="lane-task"></div>',
    '<span class="lane-idle-warning"></span>',
  ].join('');
  card.addEventListener('click', function() { sendMessage({ type: 'open_lane', memberName: memberName }); });
  return card;
}

function updateLaneCard(card, lane, sortIndex) {
  card.className = 'lane-card status-' + lane.status;
  card.style.order = String(sortIndex);

  var dot  = card.querySelector('.status-dot');
  dot.className = 'status-dot ' + lane.status;

  card.querySelector('.lane-display-name').textContent = lane.displayName;

  var badge = card.querySelector('.lane-role-badge');
  badge.textContent = lane.role;
  badge.className   = 'lane-role-badge ' + lane.role;

  var actEl = card.querySelector('.lane-activity');
  actEl.textContent = lane.currentActivity || (lane.status === 'SHUTDOWN' ? 'Finished' : '—');

  var taskEl = card.querySelector('.lane-task');
  taskEl.textContent = lane.currentTaskTitle ? 'Task: ' + lane.currentTaskTitle : '';

  // Idle warning: BUSY but no activity for > IDLE_WARN_MS
  var warnEl = card.querySelector('.lane-idle-warning');
  var idleMs = Date.now() - lane.lastActiveAt;
  if (lane.status === 'BUSY' && idleMs > IDLE_WARN_MS) {
    warnEl.textContent = '⚠ idle ' + Math.floor(idleMs / 1000) + 's';
  } else {
    warnEl.textContent = '';
  }
}

function renderTimeline(lanes) {
  var bar   = document.getElementById('timeline');
  var width = bar.clientWidth - 20;   // 10px padding each side
  var now   = Date.now();
  var start = now - TIMELINE_WINDOW_MS;

  bar.innerHTML = '';
  lanes.forEach(function(lane) {
    if (lane.status === 'SHUTDOWN' && lane.lastActiveAt < start) return;
    var colour = _laneColourMap[lane.memberName] || '#666';
    var since  = _activeSince[lane.memberName] || lane.lastActiveAt;
    var segStart = Math.max(since, start);
    var segEnd   = lane.status === 'BUSY' ? now : lane.lastActiveAt;
    if (segEnd <= segStart) return;

    var left  = ((segStart - start) / TIMELINE_WINDOW_MS) * width + 10;
    var segW  = ((segEnd  - segStart) / TIMELINE_WINDOW_MS) * width;
    var track = document.createElement('div');
    track.className = 'timeline-track';
    track.style.cssText = 'left:' + left + 'px;width:' + segW + 'px;background:' + colour + ';top:12px;';
    track.title = lane.displayName;
    bar.appendChild(track);
  });
}

// ── Helpers ────────────────────────────────────────────
function assignColour(memberName) {
  if (!_laneColourMap[memberName]) {
    var idx = Object.keys(_laneColourMap).length % LANE_COLOURS.length;
    _laneColourMap[memberName] = LANE_COLOURS[idx];
  }
}

function sendMessage(obj) {
  if (typeof window.__swarmSend === 'function') window.__swarmSend(JSON.stringify(obj));  // JetBrains
  if (typeof _vscode !== 'undefined') _vscode.postMessage(obj);                            // VS Code
}

function esc(s) {
  return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function truncate(s, n) {
  return s && s.length > n ? s.slice(0, n) + '…' : (s || '');
}

// Refresh idle timers every 5s without a full server push
setInterval(function() { if (_state) render(); }, 5000);
</script>
</body>
</html>
```

---

## 8. New source files — full list

| Path | Description |
|------|-------------|
| `packages/jetbrains-plugin/src/main/kotlin/com/jiuwenswarm/plugin/swarm/SwarmState.kt` | Data classes: `AgentLane`, `TeamTask`, `SwarmSnapshot` |
| `packages/jetbrains-plugin/src/main/kotlin/com/jiuwenswarm/plugin/swarm/SwarmStateManager.kt` | State machine: `applyTeamEvent`, `applyToolCall`, `snapshot`, `reset` |
| `packages/jetbrains-plugin/src/main/kotlin/com/jiuwenswarm/plugin/ui/SwarmMapToolWindowFactory.kt` | `ToolWindowFactory` + `openOrReveal` companion |
| `packages/jetbrains-plugin/src/main/kotlin/com/jiuwenswarm/plugin/ui/SwarmMapPanel.kt` | JCEF browser wrapper, `postSnapshot` |
| `packages/vscode-extension/src/swarm/SwarmState.ts` | TypeScript interfaces |
| `packages/vscode-extension/src/swarm/SwarmStateManager.ts` | Same state machine in TypeScript |
| `packages/vscode-extension/src/swarm/SwarmMapPanel.ts` | `vscode.WebviewPanel` wrapper |
| `packages/shared-webview/swarm_map.html` | Source of truth for the HTML panel |
| `packages/jetbrains-plugin/src/main/resources/webview/swarm_map.html` | Copy (built from shared-webview) |
| `packages/vscode-extension/resources/swarm_map.html` | Copy (built from shared-webview) |

**Existing files with targeted changes:**

| File | What changes |
|------|-------------|
| `ChatToolWindow.kt` | +`swarmStateManager` field; +`swarmMapPanel` field; +`extractTeamEventDelta()`; +3 call sites in `onJiuwenMessage`; +1 call in `onSessionChange` |
| `ChatPanel.ts` | Same 5 additions in TypeScript |
| `META-INF/plugin.xml` | +1 `<toolWindow>` entry for `JiuwenSwarm Swarm` |

---

## 9. Edge cases

| Scenario | Handling |
|----------|----------|
| Panel not yet open when first team event arrives | `postSnapshot` stores the snapshot in `pendingSnapshot`; panel sends it on `onLoadEnd` / `swarm_ready` |
| `team.event:` arrives before `team.member.spawned` for that member | `SwarmStateManager` creates a stub lane; later `spawned` event overwrites `displayName` and `role` |
| Session switches mid-team-session | `onSessionChange` calls `reset()` → blank panel |
| All lanes reach SHUTDOWN | Live lane cards are replaced by a summary card showing agent count, tasks completed, and messages exchanged. The panel stays open; user closes manually. |
| Duplicate events (network retry) | `SwarmStateManager` is idempotent: `applyTeamEvent` for `spawned` overwrites the lane rather than duplicating it; task events check `task.status` before mutating |
| `member_name` absent from `chat.tool_call` payload | `applyToolCall` skips lane update; activity line stays at last known value |
| Panel disposed while team session still running | `swarmMapPanel` field set to null; `postSnapshot` calls are no-ops |

---

## 10. Open questions — resolved

1. **`member_name` in `chat.tool_call`** — ✅ Field is present. `applyToolCall` guards
   against `null` and skips the lane update if absent.
2. **Exact team event wrapper key** — ✅ Both wire formats handled: E2A chunk
   (`body.delta` prefixed with `"team.event:"`) and old-format (`type:"event"`,
   `event` starts with `"team."`). `extractTeamEventDelta` normalises both to the
   `{ "event": { "type": ..., ... } }` shape.
3. **Suppress or keep team events in chat webview?** — ✅ Suppressed. Team events are
   meta-events consumed entirely by the plugin; the user sees swarm activity in the
   Swarm Map panel, not as raw text in the chat transcript.
4. **`SwarmStateManager` thread safety (JetBrains)** — ✅ All public methods annotated
   with `@Synchronized`. The data is small so this is sufficient without a dedicated
   executor.

---

## 11. Demo screenshot

### Layout

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  IntelliJ IDEA · Darcula                                          [dark theme]│
├──────────────────────────┬───────────────────────────────────────────────────┤
│  Editor  (65% width)     │  JiuwenSwarm Swarm  (35% width)                   │
│                          │                                                    │
│  auth.service.ts         │  ┌─ JIUWENSWARM · AUTH_TEAM · 3 agents ─────────┐ │
│                          │  │                                                │ │
│  12  refreshToken() {    │  │  TASKS                                         │ │
│  13 ┌─────────────────┐  │  │  [⚙ Implement auth endpoints → Backend Dev  ] │ │
│  14 │ // written by   │  │  │  [⚙ Write auth unit tests  → Test Engineer  ] │ │
│  15 │ // Backend Dev  │  │  │  [✓ Design auth schema     → Architect      ] │ │
│  16 │ ...             │  │  │                                                │ │
│  24 └─────────────────┘  │  │  ┌─────────────────────────────────────────┐  │ │
│  25                       │  │  │ 👑 Architect                  READY     │  │ │
│  44 ██validateJwt() {██  │  │  │    —                                     │  │ │
│  45   // ...              │  │  │    Last active 18s ago                   │  │ │
│  46 }                     │  │  └─────────────────────────────────────────┘  │ │
│  47                       │  │  ┌─────────────────────────────────────────┐  │ │
│   [BD] gutter badge ──►   │  │  │ ● Backend Dev              ● BUSY  ●●● │  │ │
│                          │  │  │   writing · auth.service.ts:44          │  │ │
│                          │  │  │   Task: Implement auth endpoints · 2m   │  │ │
│                          │  │  └─────────────────────────────────────────┘  │ │
│                          │  │  ┌─────────────────────────────────────────┐  │ │
│                          │  │  │ ● Test Engineer            ● BUSY  ●●● │  │ │
│                          │  │  │   bash · pytest tests/auth/ --tb=short  │  │ │
│                          │  │  │   Task: Write auth unit tests · 45s     │  │ │
│                          │  │  └─────────────────────────────────────────┘  │ │
│                          │  │                                                │ │
│                          │  │  ████░░░░ ████████░ ██░░░░░░  ← timeline 90s  │ │
│                          │  └────────────────────────────────────────────────┘ │
├──────────────────────────┴───────────────────────────────────────────────────┤
│  JiuwenSwarm [tab]                                                            │
│  You: "Implement a complete JWT authentication system with refresh token..."  │
│  Architect: "Dispatched to 2 agents. Running in parallel."                   │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Key visual choices

- **65/35 split** — editor is primary; swarm map is a companion, not a takeover.
- **Green left border on BUSY cards** — first thing eye goes to; immediately communicates "active work".
- **Pulsing green dots** — conveys "live", not static.
- **Timeline bar** — 3 overlapping coloured tracks prove parallelism in a glance.
  Architect's track starts and ends early (design phase). Backend Dev and Test Engineer
  tracks overlap in the right 60% (parallel execution).
- **Chat panel collapsed to a tab** — the user typed once; the swarm map is where the
  action is now. The chat tab stays visible so they know they can return.
- **Gutter badge `[BD]`** — stretch goal (Phase 2.5); shows that the file open in the
  editor is being actively modified by an agent.

### Marketing caption

> "One prompt. Three agents. Parallel. In your IDE."

---

## 12. Phase 2 enhancements — implemented

Four self-contained improvements shipped after the initial Phase 1 release.
All require zero server-side changes.

### 12.1 Progress chip

A small chip in the top-right of the Swarm Map header (`N/M tasks · K agents`)
computed live from the snapshot:

- `N` = tasks with `status === 'completed'`
- `M` = total tasks created
- `K` = lanes with `status === 'BUSY' || 'READY'`

Disappears when no session is active. Renders via `renderProgress(state)` in
`swarm_map.html`.

### 12.2 Lane click → jump to active file

`AgentLane` gained a `lastActivePath: String?` field populated by `applyToolCall`.
`SwarmStateManager.applyToolCall` now also sets `lane.lastActivePath = filePath`.

When the HTML panel is clicked, it sends `{ type: "open_lane", memberName }` to the
plugin via the existing JS bridge. The plugin resolves `lastActivePath` from the
current snapshot and navigates the editor to that file.

**JetBrains** (`ChatPanel.handleSwarmMessage`):
Uses `LocalFileSystem.getInstance().findFileByPath(filePath)` and
`OpenFileDescriptor(project, vf).navigate(true)` on the EDT.

**VS Code** (`ChatPanel.handleSwarmMessage`):
Uses `vscode.workspace.openTextDocument(uri)` + `vscode.window.showTextDocument(doc)`.

Lane cards with a non-null `lastActivePath` gain the `.has-path` CSS class, which
renders an `↗ open file` hint via `::after` on hover.

### 12.3 Inter-agent message log

`SwarmStateManager` gained a `messages: ArrayDeque<TeamMessage>` ring buffer (max 50
entries). Every `team.message.*` event with a non-empty `content` field appends a
`TeamMessage(from, to, content, timestamp)` to the buffer.

`SwarmSnapshot` now carries `messages: List<TeamMessage>`.

In `swarm_map.html`, a collapsible `#msg-section` appears below the timeline once any
messages exist. The toggle header shows `▶ Messages (N)`. When expanded, `renderMessages`
renders rows colour-coded by sender's lane colour. The log auto-scrolls to the most
recent entry. The buffer holds the last 50 messages; older ones are dropped.

### 12.4 Summary card

When `_state.lanes.every(l => l.status === 'SHUTDOWN')` is true, `renderSummaryCard`
replaces the live lane list with a single card:

```
✓ {teamName} · Session complete
Agents            N
Tasks completed   N (M cancelled)
Messages          N
```

All counters are computed from the snapshot — no additional server events required.
The live lane cards are hidden (not destroyed) so the panel can return to normal if a
new session starts and lanes become active again.

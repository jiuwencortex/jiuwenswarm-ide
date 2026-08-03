# JiuwenSwarm IDE Plugins — Architecture

Architecture reference for the JetBrains plugin and VS Code extension. Both plugins share the same WebSocket protocol and the same shared webview UI (`chat.html`). Only the host-side language, IDE APIs, and bridge mechanism differ.

---

## 1. System Overview

```
┌──────────────────────────────────────┐     WebSocket      ┌─────────────────────────────────┐
│         IDE Plugin                   │ ◄─────────────────► │   JiuwenSwarm Gateway           │
│   (JetBrains / VS Code)              │  ws://host:19000/ws │   ws://host:19000/ws            │
│                                      │                     │                                 │
│  ┌────────────────────────────────┐  │                     │  ┌───────────────────────────┐  │
│  │  Shared Webview (chat.html)    │  │                     │  │  Web Channel Handler      │  │
│  │  - Streaming markdown          │  │                     │  └───────────┬───────────────┘  │
│  │  - Tool call cards             │  │                     │             │                   │
│  │  - Mode / model selectors      │  │                     │  ┌──────────▼───────────────┐  │
│  │  - @ / # / ! input pickers     │  │                     │  │  AgentServer             │  │
│  │  - Session / skills overlays   │  │                     │  └──────────────────────────┘  │
│  │  - Stats bar + mini charts     │  │                     └─────────────────────────────────┘
│  │  - Checkpoint rewind bar       │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │  Context Collector             │  │
│  │  - Active file + cursor + sel  │  │
│  │  - Diagnostics + open tabs     │  │
│  │  - Project tree (2-level)      │  │
│  │  - Git status (subprocess)     │  │
│  │  - Project rules file          │  │
│  │  - @-mentioned file contents   │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │  Edit Applier                  │  │
│  │  - Intercept tool_call events  │  │
│  │  - Diff window or auto-apply   │  │
│  │  - File snapshot (rewind)      │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │  Terminal Manager              │  │
│  │  - Route bash to IDE terminal  │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │  WS Client + Session Manager   │  │
│  │  - OkHttp / ws npm package     │  │
│  │  - Exponential backoff reconnect│ │
│  │  - Session CRUD                │  │
│  │  - Request/response matching   │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

The plugin connects to `ws://host:port/ws` as a standard WebSocket client — the same endpoint used by the web frontend. The `channel_id` field is set to `"ide"` so IDE connections are identifiable in server logs.

---

## 2. Protocol

### Sending a chat message

```json
{
  "id": "<uuid>",
  "type": "req",
  "channel_id": "ide",
  "session_id": "<session-id>",
  "method": "chat.send",
  "params": {
    "content": "Refactor this function to use async/await",
    "mode": "code.plan"
  },
  "timestamp": 1720000000.0
}
```

IDE context (active file, selection, diagnostics, git, project rules, @-mentioned files) is prepended to `content` as a plain-text block. The backend is unaware of this and requires no schema changes.

### Streaming response events

| Event | Action |
|-------|--------|
| `chat.delta` | Append text to the active turn |
| `chat.reasoning` | Append to the collapsible "Thinking…" block |
| `chat.tool_call` | Show tool call card with spinner |
| `chat.tool_result` | Update tool card with result |
| `chat.final` | Mark turn complete; replace accumulated text with canonical content |
| `chat.usage_metadata` | Update per-turn token counter |
| `chat.usage_summary` | Update session-level token and cost totals |
| `context.usage` | Update context bar occupancy percentage |
| `context.compression_state` | Show compaction progress indicator |
| `chat.interrupt_result` | Mark streaming stopped on user interrupt |
| `chat.human_turn_pending` | Show clarifying question UI |

### Session methods

```
req: session.list    → list sessions
req: session.create  → start new session
req: session.switch  → activate a session
req: session.delete  → delete a session
req: skills.list     → list registered skills
req: skills.toggle   → enable / disable a skill
req: models.list     → list available models
req: memory.compute  → compute server memory stats (JetBrains only)
req: history.get     → fetch past messages for a session
req: chat.interrupt  → interrupt an in-progress agent turn
req: chat.answer     → respond to a human-turn clarifying question
```

### Request/response matching

Every `req` gets a UUID `id`. The plugin keeps a map of in-flight `id → CompletableFuture` (JetBrains, 5 s timeout) or `id → Promise` (VS Code, 15 s timeout). Responses carry the matching `id` (legacy format) or `request_id` (E2A format). Unmatched responses are logged and discarded.

### E2A streaming format

The gateway supports an envelope format for streaming responses:

```
e2a.chunk   → { body: { event_type, delta } }
e2a.complete → { body: { result: { event_type, ... } } }
e2a.error   → { body: { message, details } }
```

Both plugins convert E2A messages to the legacy `{ event_type, request_id, payload }` shape before dispatching to the webview.

---

## 3. Context Injection

On every chat send, the plugin prepends a structured block to the message content. Assembly order:

1. Active file path, language, cursor line
2. Selected code (if any)
3. Editor diagnostics (up to 10)
4. Other open tabs (up to 10)
5. Project tree (2-level directory listing, configurable)
6. Git branch and uncommitted change count
7. Project rules (content of first matching file: `.jiuwenswarm/instructions.md`, `.jiuwenswarm/rules.md`, `AGENTS.md`)
8. @-mentioned files (full content for each `@path` typed in the message)

### Fields by source

| Field | JetBrains API | VS Code API |
|-------|--------------|-------------|
| Active file | `FileEditorManager.selectedFiles` | `vscode.window.activeTextEditor` |
| Cursor line | `Editor.caretModel` | `editor.selection.active.line` |
| Selection | `Editor.selectionModel` | `editor.document.getText(selection)` |
| Diagnostics | Document markup model (error-stripe highlighters) | `vscode.languages.getDiagnostics(uri)` |
| Open tabs | `FileEditorManager.openFiles` | `vscode.window.tabGroups.all` |
| Project tree | `LocalFileSystem` VirtualFile traversal | Workspace folder `fs` traversal |
| Git | `ProcessBuilder` → `git` subprocess | `ProcessBuilder` → `git` subprocess |

### ReadAction boundary (JetBrains only)

IDE APIs (`FileEditorManager`, `selectionModel`, markup model) must be called inside a `ReadAction`. Git subprocess blocks and must run outside. `ContextCollector.collect()` uses `ReadAction.compute {}` for IDE reads, then calls `GitContextProvider.collect()` outside.

### Example block

````
<!-- IDE Context -->
Active file: /project/src/api/handler.py  (Python)
Cursor line: 87

Selected code:
```python
def handle_request(req):
    result = blocking_call(req)
    return result
```

Diagnostics (2):
  • Variable 'result' is not used before return (line 87)
  • blocking_call is deprecated

Other open files (2):
  /project/src/api/router.py
  /project/tests/test_handler.py

Project structure:
src/
  api/
  models/
tests/
pyproject.toml

Git: branch=feature/async-refactor, 3 uncommitted changes

Project rules:
Always use async/await. No blocking calls. Follow PEP 8.

@src/api/models.py:
```python
class Request:
    ...
```
<!-- End IDE Context -->
````

---

## 4. File Edit Handling

When the agent invokes a file-editing tool (`str_replace_editor`, `write_file`, `create_file`), the plugin intercepts the `chat.tool_call` event and handles it natively.

### Supported tools

| Tool | Operation |
|------|-----------|
| `str_replace_editor` command=`str_replace` | Replace a specific block of text in an existing file |
| `str_replace_editor` command=`create` | Create a new file |
| `write_file` | Overwrite or create a file |
| `create_file` | Create a new file; parent directories created automatically |

### JetBrains

- **Default**: `DiffManager.getInstance().showDiff()` opens a side-by-side diff window. Closing the window applies the change via `WriteCommandAction`.
- **Auto-apply**: `WriteCommandAction.runWriteCommandAction()` + `Document.replaceString()`, undoable with `Ctrl+Z`.
- **Approve**: a confirmation dialog appears before the diff or auto-apply runs.
- **Snapshot**: before the first edit to a file in a turn, the current content is captured in `currentTurnSnapshots`. Promoted to `lastTurnSnapshots` on `chat.final`.

### VS Code

- **Default**: edit applied via Node.js `fs.writeFileSync`.
- **Approve**: `vscode.window.showInformationMessage()` with Approve/Reject buttons.

---

## 5. VS Code Extension

### Tech stack

| Concern | Choice |
|---------|--------|
| Language | TypeScript |
| Bundler | esbuild |
| WebSocket | `ws` npm package |
| UI | Webview (`vscode.WebviewPanel`) + shared `chat.html` |
| JSON | Built-in |

### Source layout

```
packages/vscode-extension/src/
├── extension.ts                       # activate() / deactivate()
├── client/
│   ├── WsClient.ts                    # WebSocket + exponential backoff reconnect
│   ├── SessionManager.ts              # Session CRUD; request/response matching (15 s timeout)
│   └── protocol.ts                    # Type definitions
├── context/
│   └── ContextCollector.ts            # Active file, selection, diagnostics, project rules, @-mentions
├── editor/
│   ├── DiffApplier.ts                 # File-edit interception + workspace apply + snapshot
│   └── DiffViewer.ts                  # Optional diff dialog for proposed edits
├── terminal/
│   └── TerminalManager.ts             # Run agent shell commands in VS Code terminal
├── codeActions/
│   └── FixWithAiCodeActionProvider.ts # Lightbulb "Fix with JiuwenSwarm"
└── ui/
    ├── ChatPanel.ts                   # WebviewPanel wrapper + message bridge
    └── StatusBar.ts                   # Connection status indicator
```

### Key VS Code APIs

| Feature | API |
|---------|-----|
| Chat panel | `vscode.window.createWebviewPanel()` |
| Active file | `vscode.window.activeTextEditor` |
| Selection | `editor.selection`, `editor.document.getText(selection)` |
| Apply edit | Node.js `fs` (or `vscode.workspace.applyEdit` for WorkspaceEdit) |
| Diagnostics | `vscode.languages.getDiagnostics()` |
| Quick action | `vscode.languages.registerCodeActionsProvider()` |
| Status bar | `vscode.window.createStatusBarItem()` |
| Terminal | `vscode.window.createTerminal()` |
| Settings | `vscode.workspace.getConfiguration('jiuwenswarm')` |

### Webview bridge

```
Extension host ──postMessage──► Webview (chat.html)
Extension host ◄──postMessage── Webview (chat.html)
```

---

## 6. JetBrains Plugin

### Tech stack

| Concern | Choice |
|---------|--------|
| Language | Kotlin |
| Build | Gradle 8.7 + `org.jetbrains.intellij.platform` |
| WebSocket | OkHttp |
| UI | JCEF (`JBCefBrowser`) — embedded Chromium running `chat.html` |
| JSON | Gson |

### Source layout

```
packages/jetbrains-plugin/src/main/kotlin/com/jiuwenswarm/plugin/
├── JiuwenSwarmService.kt              # Application-level singleton
├── client/
│   ├── WsClient.kt                    # OkHttp WebSocket + exponential backoff
│   └── SessionManager.kt              # Session CRUD; request/response matching (5 s timeout)
├── context/
│   ├── ContextCollector.kt            # ReadAction: file, selection, diagnostics, open tabs, rules, @-mentions
│   └── GitContextProvider.kt          # ProcessBuilder → git subprocess (outside ReadAction)
├── editor/
│   └── DiffApplier.kt                 # File-edit interception + diff window + apply + snapshot
├── terminal/
│   └── TerminalManager.kt             # Reflection-based routing to Terminal plugin
├── ui/
│   ├── ChatToolWindow.kt              # ToolWindowFactory + JCEF panel + message bridge
│   ├── Actions.kt                     # NewSessionAction, SendSelectionAction
│   ├── FixWithAiIntention.kt          # IntentionAction — Alt+Enter quick-fix
│   └── StatusBarWidgetFactory.kt      # Connection state + token count tooltip
└── settings/
    ├── JiuwenSwarmSettings.kt         # PersistentStateComponent
    └── SettingsConfigurable.kt        # Settings UI panel
```

### Key JetBrains APIs

| Feature | API |
|---------|-----|
| Chat panel | `ToolWindowFactory` + `JBCefBrowser` (JCEF) |
| Active file | `FileEditorManager.getInstance(project).selectedFiles` |
| Selection | `Editor.selectionModel` |
| Apply edit | `WriteCommandAction.runWriteCommandAction()` + `Document.replaceString()` |
| Diagnostics | Document markup model (error-stripe highlighters) |
| Quick fix | `IntentionAction` (Alt+Enter menu) |
| Git context | `ProcessBuilder` → `git rev-parse` + `git status --porcelain` |
| Settings | `PersistentStateComponent<State>` → `jiuwenswarm.xml` |
| Status bar | `StatusBarWidgetFactory` |
| Diff view | `DiffManager.getInstance().showDiff()` |
| Terminal | `TerminalView` (reflection-based; graceful fallback if Terminal plugin absent) |
| Symbol nav | `PsiSearchHelper.findFilesWithPlainTextWords()` |

### JCEF bridge

```kotlin
// Kotlin → JS
browser.cefBrowser.executeJavaScript(
    "if(window.__jb_dispatch) window.__jb_dispatch('$escapedJson');", "", 0
)

// JS → Kotlin (registered via JBCefJSQuery)
window.__jb_send = function(jsonStr) { /* routes to handleWebviewMessage() */ }
```

---

## 7. Shared Webview (chat.html)

Both plugins load the same self-contained `chat.html` — vanilla HTML + JavaScript, no build step. The file lives at `packages/shared-webview/chat.html` and is copied to each plugin's resource directory at build time.

### Bridge detection

```javascript
function send(msg) {
  if (typeof acquireVsCodeApi !== 'undefined') {
    vscodeApi.postMessage(msg);          // VS Code
  } else if (window.__jb_send) {
    window.__jb_send(JSON.stringify(msg)); // JetBrains
  }
}

window.__jb_dispatch = function(jsonStr) {
  handleHostMessage(JSON.parse(jsonStr));
};
window.addEventListener('message', function(e) { // VS Code
  handleHostMessage(e.data);
});
```

### Host → Webview messages

| Type | Key fields | Effect |
|------|-----------|--------|
| `connected` | `sessionId`, `sessionTitle`, `models`, `activeModel`, `defaultMode` | Update header, enable input, apply default mode if user hasn't customized it |
| `disconnected` | — | Show disconnected state, disable input |
| `reconnecting` | — | Show reconnecting indicator |
| `jiuwen_event` | `event` (object) | Route to streaming / tool card handlers |
| `prefill` | `content` | Pre-fill the chat textarea |
| `sessions` | `sessions[]` | Render session list overlay |
| `sessions_error` | `message` | Show error in sessions overlay |
| `session_deleted` | `sessionId` | Remove session from the overlay list |
| `skills` | `skills[]` | Populate skills overlay and `#` picker cache |
| `skills_error` | `message` | Show error in skills overlay |
| `skill_toggled` | `skillId`, `enabled` | Update skill toggle button state |
| `files` | `files[]` | Populate `@` file mention picker |
| `git_status` | `branch`, `changedCount` | Update git branch chip and changed files count |
| `git_committed` | `hash` | Show commit confirmation in git bar |
| `git_pushed` | — | Show push confirmation in git bar |
| `git_error` | `message` | Show git error message |
| `rewindable` | `enabled` | Show or hide the checkpoint rewind bar |
| `rewind_done` | `message`, `restored`, `failed` | Display rewind result |
| `history_loading` | `loading` | Show or hide the "Loading history…" indicator |
| `memory` | `rssMb`, `totalMb`, `availableMb` | Update server memory chip |
| `metrics` | `metrics` | Update host metrics display |
| `debug_log` | `line` | Append line to debug panel |
| `error` | `message`, `requestId` | Show error inline in the active turn |

### Webview → Host messages

| Type | Sent when |
|------|-----------|
| `ready` | Page load complete — host sends current status in response |
| `send` | User submits a message (`content`, `mode`, `requestId`, `media_items`, `mentionedPaths`) |
| `answer` | User answers a `chat.human_turn_pending` prompt |
| `stop` | User clicks the Stop button during streaming |
| `new_session` | User clicks New or confirms mode switch |
| `list_sessions` | Sessions overlay opens |
| `switch_session` | User clicks a session row |
| `delete_session` | User confirms session deletion |
| `list_skills` | Skills overlay opens |
| `toggle_skill` | User clicks ON/OFF on a skill |
| `files_request` | User types `@` for the first time (triggers workspace file scan) |
| `open_file` | User clicks a file link in the chat |
| `navigate_symbol` | User clicks a symbol link |
| `rewind` | User clicks the Undo Changes button |
| `git_status_request` | Git status chip is refreshed |
| `git_commit_request` | User clicks the Commit button |
| `git_push_request` | User clicks the Push button |
| `input_changed` | Textarea content changes (used by host for typing indicators) |
| `toggle_debug` | User toggles the debug log |

### State object (key fields)

```javascript
let state = {
  connected: false,
  sessionId: null,
  sessionTitle: 'JiuwenSwarm',
  mode: 'code.plan',
  modeCustomized: false,      // true once user manually picks a mode
  models: [],
  activeModel: null,
  streaming: false,
  turns: [],
  pendingTurns: {},
  skills: [],
  mentionFiles: [],
  pendingMentions: [],        // @-mentioned paths in the current input
  mentionQuery: '',           // current @ token being typed
  skillQuery: '',             // current # token being typed
  promptQuery: '',            // current ! token being typed
  gitBranch: null,
  gitChangedCount: 0,
  rewindable: false,
  contextUsagePercent: null,
  sessionStats: { ... },
};
```

---

## 8. Connection Management

- Reconnect with exponential backoff: 1 s → 2 s → 4 s → 8 s → … → 30 s cap
- On reconnect: restore last active session via `session.switch`
- Keep-alive: configurable ping interval (default 30 s, range 5–300 s) to prevent server-side timeout
- Status states: `DISCONNECTED` → `CONNECTING` → `CONNECTED` / `RECONNECTING`
- The 15-second reconnect retry in VS Code runs at constant interval independent of the WS backoff

---

## 9. Memory Polling (JetBrains)

After connecting, `ChatPanel` starts a `Timer` that fires every 10 seconds. It calls `session.getMemoryUsage()` (a synchronous `memory.compute` request to the server) and dispatches the result to the webview as a `memory` message, which updates the server RAM chip in the stats bar. The timer is cancelled on panel dispose.

---

## 10. Security

- The plugin connects to `127.0.0.1` (JetBrains) or `localhost` (VS Code) by default. Enterprise deployments can change the host/port in settings.
- JiuwenSwarm enforces tool permissions server-side. The plugin does not sandbox file operations beyond optionally requiring user approval.
- The approval workflow (`approveEdits`) gives the user a chance to reject individual file edits before they are written to disk.
- Secrets and credentials must never be committed to the project rules files injected into context.

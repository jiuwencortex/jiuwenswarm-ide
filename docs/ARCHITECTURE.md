# JiuwenSwarm IDE Plugins — Architecture

Architecture reference for the JetBrains plugin and VS Code extension. Both plugins share the same protocol, the same webview UI, and the same overall component model — only the host-side language and IDE APIs differ.

---

## 1. System Overview

```
┌─────────────────────────────────┐     WebSocket      ┌─────────────────────────────────┐
│       IDE Plugin                │ ◄─────────────────► │    jiuwenswarm Gateway          │
│  (JetBrains / VS Code)          │   ws://localhost:   │    ws://localhost:19000/ws       │
│                                 │   19000/ws          │                                 │
│  ┌───────────────────────────┐  │                     │  ┌───────────────────────────┐  │
│  │  Chat Panel (UI)          │  │                     │  │  Web Channel Handler      │  │
│  │  - Streaming markdown     │  │                     │  │  (reused as-is)           │  │
│  │  - Tool call cards        │  │                     │  └───────────┬───────────────┘  │
│  │  - Diff viewer            │  │                     │             │                   │
│  └───────────────────────────┘  │                     │  ┌──────────▼───────────────┐  │
│  ┌───────────────────────────┐  │                     │  │  AgentServer             │  │
│  │  Context Collector        │  │                     │  │  (unchanged)             │  │
│  │  - Open files             │  │                     │  └──────────────────────────┘  │
│  │  - Selection / cursor     │  │                     └─────────────────────────────────┘
│  │  - Git status             │  │
│  │  - Diagnostics/errors     │  │
│  └───────────────────────────┘  │
│  ┌───────────────────────────┐  │
│  │  Edit Applier             │  │
│  │  - Parse tool_call events │  │
│  │  - Show diff in IDE       │  │
│  │  - Apply/reject hunks     │  │
│  └───────────────────────────┘  │
│  ┌───────────────────────────┐  │
│  │  Terminal Manager         │  │
│  │  - Run bash in IDE term   │  │
│  └───────────────────────────┘  │
│  ┌───────────────────────────┐  │
│  │  WS Client + Session Mgr  │  │
│  │  - Reconnect logic        │  │
│  │  - Session CRUD           │  │
│  │  - Message queue          │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

The IDE plugin connects as a standard WebSocket client to `ws://localhost:19000/ws` — the same endpoint used by the web frontend. No backend changes are required. The `channel_id` field is set to `"ide"` so plugin connections are identifiable in server logs and TraceHound traces.

---

## 2. Protocol

The plugin uses the same JSON message protocol as the web frontend.

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

IDE context is prepended to `content` as a plain-text block — the backend is unaware of this and does not need any schema changes.

### Streaming response events

| Event | Action |
|-------|--------|
| `chat.delta` | Append text to the active turn |
| `chat.reasoning` | Show/append to collapsible "Thinking…" block |
| `chat.tool_call` | Show tool card with spinner |
| `chat.tool_result` | Update tool card with result |
| `chat.final` | Mark turn complete; replace streamed text with canonical content |
| `chat.usage_metadata` | Update token counter |

### Session methods

```
req: session.list    → list sessions
req: session.create  → start new session
req: session.switch  → activate a session
req: session.delete  → delete a session
req: skills.list     → list registered skills
req: skills.toggle   → enable / disable a skill
req: models.list     → list available models
```

### Message ID tracking

Every `req` gets a UUID `id`. The plugin keeps a map of in-flight `id → Future`. Responses carry the matching `id` (legacy format) or `request_id` (E2A format). The future is resolved or rejected when the response arrives. Unmatched responses are logged and discarded. Default timeout: 5 s (JetBrains), 15 s (VS Code).

### Streaming text assembly

```
chat.delta  { request_id, payload: { text: "Hello" } }
chat.delta  { request_id, payload: { text: " world" } }
chat.final  { request_id, payload: { ... full content ... } }
```

The webview accumulates `delta.text` per `request_id`. On `chat.final` the canonical content replaces the accumulated text, handling any dropped deltas.

---

## 3. Context Injection

On every chat send, the plugin prepends a structured context block to the message content. The agent sees it as part of the user message.

### Fields collected

| Field | Source |
|-------|--------|
| Active file path + language | `FileEditorManager` / `vscode.window.activeTextEditor` |
| Cursor line | `Editor.caretModel` / `editor.selection.active` |
| Selected code | `Editor.selectionModel` / `editor.document.getText(selection)` |
| Editor diagnostics (up to 10) | Markup model / `vscode.languages.getDiagnostics()` |
| Other open files (up to 10) | `FileEditorManager.openFiles` / `vscode.window.tabGroups` |
| Git branch + change count | `git` subprocess (`rev-parse`, `status --porcelain`) |

### Example block

````
<!-- IDE Context -->
Active file: /Users/mishka/project/src/api/handler.py  (Python)
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

Other open files (3):
  /Users/mishka/project/src/api/router.py
  /Users/mishka/project/src/models/request.py
  /Users/mishka/project/tests/test_handler.py

Git: branch=feature/async-refactor, 3 uncommitted changes
<!-- End IDE Context -->
````

If there is nothing useful to inject (no open editor, no git repo, no selection) the block is omitted and the message is sent as-is.

---

## 4. File Edit Handling

When the agent calls a file-editing tool (`str_replace_editor`, `write_file`, `create_file`), the plugin intercepts the tool call event and handles it natively.

### Supported tools

| Tool | Operation |
|------|-----------|
| `str_replace_editor` command=`str_replace` | Replace a specific block in an existing file |
| `str_replace_editor` command=`create` | Create a new file |
| `write_file` | Overwrite or create a file |
| `create_file` | Create a new file; parent dirs created automatically |

### Default: diff review

A side-by-side diff window opens showing **Current** vs **Proposed**. The user reviews and closes the window to apply.

- **JetBrains**: `DiffManager.getInstance().showDiff()` + `WriteCommandAction` + `Document.replaceString()`
- **VS Code**: `WorkspaceEdit` API + `window.showTextDocument()`

### Auto-apply mode

A plugin setting skips the diff dialog. Changes are applied immediately and are undoable (`Ctrl+Z`). A notification confirms each applied edit.

Tool call params are parsed identically for both plugins: extract `path`, `old_str`, `new_str` (or `content`).

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
├── extension.ts              # activate() / deactivate() entry point
├── client/
│   ├── WsClient.ts           # WebSocket + exponential backoff reconnect
│   ├── SessionManager.ts     # session.list / switch; request/response matching
│   └── protocol.ts           # Shared type definitions
├── context/
│   └── ContextCollector.ts   # Active file, selection, diagnostics injection
├── editor/
│   ├── DiffApplier.ts        # File-edit interception + workspace apply
│   └── DiffViewer.ts         # Native VS Code diff dialog for proposed edits
├── terminal/
│   └── TerminalManager.ts    # Run bash commands in IDE terminal
├── codeActions/
│   └── FixWithAiCodeActionProvider.ts   # Lightbulb "Fix with JiuwenSwarm"
└── ui/
    ├── ChatPanel.ts           # WebviewPanel wrapper + message bridge
    └── StatusBar.ts           # Connection status indicator
```

### Key VS Code APIs

| Feature | API |
|---------|-----|
| Chat panel | `vscode.window.createWebviewPanel()` |
| Active file | `vscode.window.activeTextEditor` |
| Selection | `editor.selection`, `editor.document.getText(selection)` |
| Apply edit | `vscode.workspace.applyEdit(WorkspaceEdit)` |
| Diagnostics | `vscode.languages.getDiagnostics()` |
| Quick action | `vscode.languages.registerCodeActionsProvider()` |
| Keybinding | `contributes.keybindings` in `package.json` |
| Status bar | `vscode.window.createStatusBarItem()` |
| Terminal | `vscode.window.createTerminal()` |
| Settings | `vscode.workspace.getConfiguration('jiuwenswarm')` |

### Webview bridge

VS Code webviews are sandboxed iframes. Communication is via `postMessage`:

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
| Build | Gradle + `org.jetbrains.intellij.platform` plugin |
| WebSocket | OkHttp (bundled with IntelliJ) |
| UI | JCEF (`JBCefBrowser`) — embedded Chromium running `chat.html` |
| JSON | Gson (bundled) |

### Source layout

```
packages/jetbrains-plugin/src/main/kotlin/com/jiuwenswarm/plugin/
├── JiuwenSwarmService.kt          # Application-level singleton
├── client/
│   ├── WsClient.kt                # OkHttp WebSocket + exponential backoff
│   └── SessionManager.kt          # Session CRUD; request/response matching (5 s timeout)
├── context/
│   ├── ContextCollector.kt        # ReadAction: active file, selection, diagnostics, open tabs
│   └── GitContextProvider.kt      # ProcessBuilder → git subprocess (outside ReadAction)
├── editor/
│   └── DiffApplier.kt             # File-edit interception + diff window + apply
├── terminal/
│   └── TerminalManager.kt         # Reflection-based routing to Terminal plugin
├── ui/
│   ├── ChatToolWindow.kt          # ToolWindowFactory + JCEF panel + message bridge
│   ├── Actions.kt                 # NewSessionAction, SendSelectionAction
│   ├── FixWithAiIntention.kt      # IntentionAction — Alt+Enter quick-fix
│   └── StatusBarWidgetFactory.kt  # Connection state + token count tooltip
└── settings/
    ├── JiuwenSwarmSettings.kt     # PersistentStateComponent
    └── SettingsConfigurable.kt    # Settings UI panel
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
| Settings | `PersistentStateComponent<State>` |
| Status bar | `StatusBarWidgetFactory` |
| Diff view | `DiffManager.getInstance().showDiff()` |
| Terminal | Reflection on `org.jetbrains.plugins.terminal.TerminalView` |
| Symbol nav | `PsiSearchHelper.findFilesWithPlainTextWords()` |
| Keyboard | `<action>` + `<keyboard-shortcut>` in `plugin.xml` |

### JCEF bridge

```kotlin
// Kotlin → JS
browser.cefBrowser.executeJavaScript(
    "if(window.__jb_dispatch) window.__jb_dispatch('$escapedJson');", "", 0)

// JS → Kotlin (CefMessageRouter / __jb_send)
window.__jb_send = function(jsonStr) { /* routed to handleWebviewMessage() */ }
```

### ReadAction boundary

IDE APIs (`FileEditorManager`, `selectionModel`, markup model) must run inside a `ReadAction`. Git subprocess (`ProcessBuilder`) blocks and must run **outside** `ReadAction`. `ContextCollector.collect()` calls `ReadAction.compute {}` for IDE reads, then calls `GitContextProvider.collect()` outside.

---

## 7. Shared Webview (`chat.html`)

Both plugins load the same self-contained `chat.html` file — vanilla HTML/JS, no build step. The file detects the host environment at startup and attaches the appropriate bridge.

### Bridge detection

```javascript
// JetBrains host exposes window.__jb_send and calls window.__jb_dispatch
// VS Code host exposes acquireVsCodeApi()
if (typeof acquireVsCodeApi !== 'undefined') {
    vscodeApi = acquireVsCodeApi();
    // send via vscodeApi.postMessage
} else if (window.__jb_send) {
    // send via window.__jb_send(jsonStr)
}
```

### Host messages dispatched to webview

| Type | Payload | Effect |
|------|---------|--------|
| `connected` | `sessionId`, `sessionTitle`, `models`, `activeModel` | Update header, enable input |
| `disconnected` | — | Show disconnected state |
| `jiuwen_event` | Raw gateway event | Route to streaming/tool card handlers |
| `prefill` | `content` | Pre-fill chat input |
| `sessions` | `sessions[]` | Render session list overlay |
| `skills` | `skills[]` | Render skills overlay |
| `skill_toggled` | `skillId`, `enabled` | Update skill toggle button |
| `rewindable` | `enabled` | Show/hide the checkpoint rewind bar |
| `rewind_done` | `message` | Display rewind result in chat |
| `debug_log` | `line` | Append line to debug panel |

### Webview messages sent to host

| Type | Sent when |
|------|-----------|
| `send` | User submits a message |
| `new_session` | User clicks New or confirms mode switch |
| `list_sessions` | Sessions overlay opens |
| `switch_session` | User clicks a session item |
| `list_skills` | Skills overlay opens |
| `toggle_skill` | User clicks ON/OFF button |
| `open_file` | User clicks a file link in the chat |
| `navigate_symbol` | User clicks a symbol link in the chat |
| `rewind` | User clicks the rewind button |
| `debug` | Debug log entry |

---

## 8. Optional: IDE-Specific Backend Channel

The current implementation reuses the standard web channel. A dedicated `ide` channel type would enable advanced features that the generic web channel cannot support:

- **Per-file permission scoping** — agent only touches files within the open project
- **Bidirectional cursor sync** — agent requests "jump to line 42 in foo.py"; IDE executes it
- **IDE-native tool routing** — edits go back to IDE APIs instead of being written by the agent
- **Inline completion fast path** — sub-50 ms latency for ghost text

```python
# jiuwenswarm/common/schema/message.py (future)
class ReqMethod(str, Enum):
    IDE_CONTEXT_PUSH = "ide.context_push"
    IDE_APPLY_EDIT   = "ide.apply_edit"
    IDE_OPEN_FILE    = "ide.open_file"
    IDE_RUN_TERMINAL = "ide.run_terminal"
    IDE_COMPLETION   = "ide.completion"
```

Not required for the current feature set.

---

## 9. Connection Management

- Reconnect with exponential backoff: 1 s → 2 s → 4 s → 8 s → … → 30 s max
- On reconnect: restore last active session via `session.switch`
- Heartbeat: ping frame every 15–20 s to prevent server-side timeout
- Status states: `DISCONNECTED` → `CONNECTING` → `CONNECTED` / `RECONNECTING`

---

## 10. Security

- The plugin connects only to `localhost` by default (configurable for enterprise)
- jiuwenswarm itself enforces tool permissions — the plugin does not sandbox anything
- The approval workflow feature (Phase 4) will use `permissions.approval_overrides.*` to configure which tools require user confirmation before execution

---

## 11. Open Questions

1. **Auth for enterprise**: The plugin currently connects without credentials. Enterprise deployments may want `Authorization: Bearer <token>` on the WS handshake — would require a minor gateway change.

2. **Multiple projects**: Each VS Code workspace folder should map to its own jiuwenswarm session (keyed on workspace path hash, stored in extension global state).

3. **No server running**: The plugin should show a helpful setup prompt rather than a raw "connection refused". Could bundle a launcher command.

4. **Inline completions latency**: `code.plan` / `code.normal` have 1–3 s latency — too slow for ghost text. A dedicated fast model path (`code.complete` mode) would need to be added to `ReqMethod`.

5. **Project-wide indexing**: Proactively sending all project file contents is expensive for large repos. Start with active file + selection; add opt-in "send project index" for smaller repos.

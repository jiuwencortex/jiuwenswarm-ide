# System Investigation — JiuwenSwarm IDE Plugins

**Related document:** RAT.md — product requirements and business background.
This document covers architecture, decomposition, technical constraints, system impact,
and external dependencies for the same feature.

---

## Feature Scope

The `jiuwenswarm-ide` repository adds two IDE client packages to the JiuwenSwarm platform:

1. **JetBrains plugin** (`packages/jetbrains-plugin/`) — a Kotlin plugin for all
   IntelliJ-platform IDEs (IntelliJ IDEA, PyCharm, GoLand, WebStorm, Rider, 2023.1+).
2. **VS Code extension** (`packages/vscode-extension/`) — a TypeScript extension for
   VS Code and all VS Code-compatible editors.

Both packages share a single HTML/JavaScript chat UI (`packages/shared-webview/chat.html`)
and connect to the same JiuwenSwarm gateway over WebSocket using the existing E2A
streaming protocol.

---

## Architecture

```
Developer's IDE
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│   ┌─────────────────────────────────────┐                           │
│   │  Shared webview  (chat.html / JS)   │  ← same file in both IDEs │
│   │  All UI rendering, state, popups,   │                           │
│   │  session display, stats bar         │                           │
│   └───────────┬────────────────────┬────┘                           │
│               │ postMessage (host→webview)                           │
│               │ window.sendToHost (webview→host)                    │
│               ▼                    ▼                                 │
│   ┌───────────────────────┐  ┌────────────────────────────────────┐ │
│   │  JetBrains host       │  │  VS Code host                      │ │
│   │  ChatToolWindow.kt    │  │  extension.ts                      │ │
│   │  JiuwenSwarmService.kt│  │  WebviewProvider                   │ │
│   │  ContextCollector.kt  │  │  ContextCollector.ts               │ │
│   │  StatusBarWidget.kt   │  │  StatusBarItem                     │ │
│   │  QuickFixAction.kt    │  │  CodeActionProvider                 │ │
│   │  TerminalRunner.kt    │  │  TerminalRunner.ts                 │ │
│   └───────────┬───────────┘  └────────────────┬───────────────────┘ │
│               │                               │                      │
└───────────────│───────────────────────────────│──────────────────────┘
                │ WebSocket + E2A               │ WebSocket + E2A
                └─────────────┬─────────────────┘
                              ▼
                  ┌──────────────────────────┐
                  │  JiuwenSwarm Gateway      │
                  │  (existing REST/WS/SSE)   │
                  └──────────────────────────┘
```

### Design principles

**Shared webview — single source of truth.**
`packages/shared-webview/chat.html` contains the complete chat UI in a single
self-contained file (no build step, no external dependencies). Both IDE host packages
copy this file into their resource directories at sync time. All UI changes land in one
place and propagate to both IDEs.

**Host/webview message boundary.**
The webview (JavaScript) and the host plugin (Kotlin/TypeScript) communicate exclusively
via a typed message-passing protocol. The webview has no direct access to IDE APIs;
the host has no access to webview DOM. This keeps the UI portable across both IDEs.

**Host owns all IDE APIs.**
Only the host plugin calls IDE-specific APIs: editor state, file system, terminal,
status bar, quick-fix actions, diagnostics. The webview receives the results as
structured JSON messages.

**Gateway connection in host only.**
The WebSocket connection to the JiuwenSwarm gateway is opened and managed entirely by
the host service. The webview sends outbound messages to the host via `window.sendToHost`;
the host forwards them to the gateway. Inbound E2A events arrive at the host first, get
translated if needed, and are forwarded to the webview as typed messages.

---

### Module layout

```
packages/
│
├── shared-webview/
│   └── chat.html                   ← single-file chat UI (HTML + inline CSS + inline JS)
│                                     Copied into both IDE packages at sync time
│
├── jetbrains-plugin/
│   ├── build.gradle.kts            ← Gradle build, plugin config, Marketplace publishing
│   ├── src/main/
│   │   ├── kotlin/com/jiuwenswarm/plugin/
│   │   │   ├── JiuwenSwarmPlugin.kt         ← plugin entry point
│   │   │   ├── service/
│   │   │   │   └── JiuwenSwarmService.kt    ← app-level service; WebSocket, session, model list
│   │   │   ├── settings/
│   │   │   │   ├── JiuwenSwarmSettings.kt   ← persistent settings (host, port, mode, …)
│   │   │   │   └── JiuwenSwarmSettingsUI.kt ← settings panel (IDE settings dialog)
│   │   │   ├── ui/
│   │   │   │   ├── ChatToolWindow.kt        ← tool window; JCEF webview; host↔webview bridge
│   │   │   │   └── StatusBarWidget.kt       ← status bar item (connection state, token count)
│   │   │   ├── context/
│   │   │   │   └── ContextCollector.kt      ← assembles IDE context before each message
│   │   │   ├── actions/
│   │   │   │   ├── SendSelectionAction.kt   ← ⌘⇧E action
│   │   │   │   ├── NewSessionAction.kt      ← ⌘⇧J action
│   │   │   │   └── FixWithJiuwenSwarmAction.kt ← Alt+Enter quick-fix action
│   │   │   └── terminal/
│   │   │       └── TerminalRunner.kt        ← runs agent shell commands in IDE terminal tab
│   │   └── resources/
│   │       ├── META-INF/plugin.xml          ← plugin descriptor
│   │       └── webview/chat.html            ← copy of packages/shared-webview/chat.html
│   └── src/test/                            ← plugin tests
│
└── vscode-extension/
    ├── package.json                ← extension manifest; activation events; commands; settings
    ├── webpack.config.js
    ├── src/
    │   ├── extension.ts            ← entry point; activate(); registers all providers/commands
    │   ├── webviewProvider.ts      ← ChatViewProvider; webview panel lifecycle; bridge
    │   ├── contextCollector.ts     ← assembles VS Code context before each message
    │   ├── settingsManager.ts      ← reads/writes jiuwenswarm.* settings from VS Code config
    │   ├── statusBarItem.ts        ← status bar item (connection state, token count)
    │   ├── terminalRunner.ts       ← runs agent shell commands in VS Code terminal
    │   ├── codeActionProvider.ts   ← lightbulb "Fix with JiuwenSwarm" action on diagnostics
    │   └── gatewayClient.ts        ← WebSocket client; E2A event parsing; reconnect loop
    └── resources/
        └── chat.html              ← copy of packages/shared-webview/chat.html
```

---

## Host ↔ Webview Message Protocol

All messages are JSON objects with a required `type` field.

### Host → Webview

| `type` | Payload fields | Purpose |
|---|---|---|
| `connected` | `sessionId`, `sessionTitle`, `defaultMode`, `models?`, `activeModel?`, `needsSession?` | Gateway connected; set initial state |
| `disconnected` | — | Gateway connection lost |
| `stream.chunk` | `requestId`, `text` | Incremental text token |
| `stream.thinking` | `requestId`, `text` | Incremental thinking/reasoning token |
| `stream.toolStart` | `requestId`, `toolName`, `toolInput` | Tool call began |
| `stream.toolResult` | `requestId`, `toolName`, `toolOutput` | Tool call completed |
| `stream.complete` | `requestId` | Full response received |
| `stream.error` | `requestId`, `message` | Stream error; response aborted |
| `stream.interrupted` | `requestId` | User-requested stop confirmed |
| `sessionList` | `sessions[]` | List of available sessions |
| `sessionSwitched` | `sessionId`, `sessionTitle`, `history[]` | Session switch complete; history payload |
| `skillList` | `skills[]` | Available skills with id, name, enabled |
| `modelList` | `models[]`, `activeModel` | Available models |
| `fileEditProposal` | `requestId`, `path`, `diff`, `newContent` | Agent wants to edit a file |
| `editApplied` | `requestId`, `path` | File edit applied in IDE |
| `editRejected` | `requestId`, `path` | File edit rejected by user |
| `checkpointReady` | `snapshotId` | Rewind snapshot saved |
| `rewindComplete` | `snapshotId`, `filesRestored` | Rewind applied |
| `humanQuestion` | `requestId`, `question`, `options?` | Agent asks a clarifying question |
| `sessionStats` | `turns`, `errors`, `tokens`, `llmCalls`, `avgLatencyMs`, `ttftMs`, `costUsd`, `perTurn[]` | Session statistics update |
| `contextWindow` | `used`, `total` | Context window occupancy |
| `compactionProgress` | `phase`, `pct` | Context compaction progress |
| `memoryUsage` | `rssBytes`, `availableBytes` | Gateway server memory |
| `gitStatus` | `branch`, `changedFiles` | Current git state |
| `workspaceFiles` | `files[]` | Workspace file list for `@` picker |
| `commandOutput` | `commandId`, `stdout`, `exitCode` | Result of a shell command run in terminal |

### Webview → Host

| `type` | Payload fields | Purpose |
|---|---|---|
| `sendMessage` | `text`, `mode`, `model`, `attachments?`, `mentions?` | User sends a chat message |
| `interrupt` | `requestId` | Stop current streaming response |
| `createSession` | — | Create a new session |
| `switchSession` | `sessionId` | Switch to a named session |
| `deleteSession` | `sessionId` | Delete a session |
| `applyEdit` | `requestId`, `path` | User approves a file edit proposal |
| `rejectEdit` | `requestId`, `path` | User rejects a file edit proposal |
| `rewind` | `snapshotId` | Restore files from checkpoint |
| `openFile` | `path`, `line?` | Open a file in the IDE editor |
| `navigateToSymbol` | `symbol` | Search for and navigate to a symbol in the IDE |
| `executeShellCommand` | `commandId`, `command` | Run a shell command in the IDE terminal |
| `requestStatus` | — | Request a fresh `connected` / `disconnected` message |
| `toggleSkill` | `skillId`, `enabled` | Enable or disable a skill |
| `answerQuestion` | `requestId`, `answer` | Answer a `humanQuestion` from the agent |
| `gitCommit` | — | Run git commit in the IDE terminal |
| `gitPush` | — | Run git push in the IDE terminal |
| `requestMemory` | — | Request a fresh `memoryUsage` message |
| `listWorkspaceFiles` | — | Request workspace file list |
| `updateSetting` | `key`, `value` | Persist a setting change to IDE settings store |

---

## Key Sequence Diagrams

### 1. Plugin startup and gateway connection

```
IDE startup            Host service              Gateway
     │                      │                      │
     │  IDE initialises      │                      │
     │─────────────────────►│                      │
     │                      │  read settings        │
     │                      │  (host, port, token)  │
     │                      │                      │
     │                      │  ws://host:port/ws    │
     │                      │─────────────────────►│
     │                      │◄── handshake OK ──────│
     │                      │                      │
     │                      │  GET /models          │
     │                      │─────────────────────►│
     │                      │◄── [{id, name}, …] ──│
     │                      │                      │
     │                      │  GET /sessions/current│
     │                      │─────────────────────►│
     │                      │◄── {sessionId, title}─│
     │                      │                      │
     │                      │  → webview: connected │
     │                      │    {sessionId, models,│
     │                      │     defaultMode}      │
     │◄─────────────────────│                      │
     │  status bar: ✅       │                      │
     │  mode pill applied    │                      │
```

On disconnect, the host enters exponential backoff: retries at 1 s, 2 s, 4 s … 30 s.
On reconnect, it re-attaches to the existing session and replays `connected`.

---

### 2. Developer sends a message with context injection

```
Developer        Webview (JS)         Host plugin         Gateway
     │               │                    │                  │
     │  types message │                    │                  │
     │  presses Enter │                    │                  │
     │──────────────►│                    │                  │
     │               │  sendMessage       │                  │
     │               │  {text, mode,      │                  │
     │               │   model, mentions} │                  │
     │               │───────────────────►│                  │
     │               │                    │                  │
     │               │                    │  collectContext()│
     │               │                    │  ┌─────────────┐│
     │               │                    │  │active file   ││
     │               │                    │  │cursor line   ││
     │               │                    │  │selection     ││
     │               │                    │  │diagnostics   ││
     │               │                    │  │open tabs     ││
     │               │                    │  │project tree  ││
     │               │                    │  │git state     ││
     │               │                    │  │project rules ││
     │               │                    │  │@file contents││
     │               │                    │  └─────────────┘│
     │               │                    │                  │
     │               │                    │  WS: send        │
     │               │                    │  {message, ctx,  │
     │               │                    │   mode, model}   │
     │               │                    │─────────────────►│
     │               │                    │                  │
     │               │  stream.chunk …    │  e2a.chunk …     │
     │               │◄───────────────────│◄─────────────────│
     │◄──────────────│  (incremental)     │  (incremental)   │
     │  tokens appear│                    │                  │
     │               │                    │                  │
     │               │  stream.complete   │  e2a.complete    │
     │               │◄───────────────────│◄─────────────────│
     │◄──────────────│  full response     │                  │
     │  response done│                    │                  │
     │               │  sessionStats      │                  │
     │               │◄───────────────────│                  │
     │◄──────────────│  (chips updated)   │                  │
```

---

### 3. Agent proposes a file edit (JetBrains path with diff dialog)

```
Developer        Webview (JS)         Host plugin          IDE / Gateway
     │               │                    │                     │
     │               │  fileEditProposal  │  e2a.chunk/complete │
     │               │◄───────────────────│◄────────────────────│
     │◄──────────────│  (diff card shown) │                     │
     │               │                    │                     │
     │  clicks Apply  │                    │                     │
     │──────────────►│                    │                     │
     │               │  applyEdit         │                     │
     │               │  {requestId, path} │                     │
     │               │───────────────────►│                     │
     │               │                    │  open diff dialog   │
     │               │                    │─────────────────────►│
     │               │                    │  (preview only;      │
     │               │                    │   no file written)   │
     │               │                    │                     │
     │  confirms OK   │                    │                     │
     │──────────────────────────────────────────────────────────►│
     │               │                    │  IDE writes file     │
     │               │                    │◄─────────────────────│
     │               │                    │                     │
     │               │  editApplied       │  snapshot for rewind │
     │               │◄───────────────────│                     │
     │◄──────────────│  (applied badge)   │                     │
```

In auto-apply mode the diff dialog is skipped; the host writes the file directly and
snapshots it. The edit is undoable via the IDE undo stack (Ctrl+Z) or via the
checkpoint / rewind bar.

---

### 4. `@` file mention — workspace file picker flow

```
Developer        Webview (JS)         Host plugin       File system
     │               │                    │                  │
     │  types "@"     │                    │                  │
     │──────────────►│                    │                  │
     │               │  listWorkspaceFiles│                  │
     │               │───────────────────►│                  │
     │               │                    │  walk project    │
     │               │                    │  root (≤500 files│
     │               │                    │  skip .git etc.) │
     │               │                    │─────────────────►│
     │               │                    │◄── file paths ───│
     │               │  workspaceFiles    │                  │
     │               │◄───────────────────│                  │
     │◄──────────────│  (picker appears)  │                  │
     │               │                    │                  │
     │  selects file  │                    │                  │
     │──────────────►│                    │                  │
     │               │  (JS: read file    │                  │
     │               │   via gateway or   │                  │
     │               │   host read API)   │                  │
     │               │───────────────────►│                  │
     │               │                    │  read file       │
     │               │                    │─────────────────►│
     │               │                    │◄── content ──────│
     │               │                    │                  │
     │               │  @mention resolved │                  │
     │               │◄───────────────────│                  │
     │◄──────────────│  (tag in input)    │                  │
     │  [file content injected into       │                  │
     │   next message context]            │                  │
```

---

### 5. Agent shell command routed to IDE terminal

```
Developer        Webview (JS)         Host plugin          IDE terminal
     │               │                    │                     │
     │               │  (stream shows     │                     │
     │               │   shell command    │                     │
     │               │   in tool card)    │                     │
     │               │                    │                     │
     │               │ executeShellCommand│                     │
     │               │ {commandId, cmd}   │                     │
     │               │───────────────────►│                     │
     │               │                    │  find/create        │
     │               │                    │  "JiuwenSwarm" tab  │
     │               │                    │─────────────────────►│
     │               │                    │  run command in tab │
     │               │                    │─────────────────────►│
     │               │                    │◄── exit code ────────│
     │               │  commandOutput     │                     │
     │               │◄───────────────────│                     │
     │◄──────────────│  (output in card)  │                     │
```

---

## Component Breakdown

### `packages/shared-webview/chat.html`

Single self-contained HTML file. Runs inside the IDE-managed webview sandbox.
Has no network access of its own — all data flows through the host message bridge.

| Sub-component | Description |
|---|---|
| State object | `state` — connected, streaming, mode, modeCustomized, sessions, skills, pendingMentions, etc. |
| Message renderer | Converts stream chunks into Markdown HTML; handles thinking blocks and tool call cards |
| Popup system | `@` mention picker, `#` skill picker, `!` preset prompt picker; all use `onmousedown` + `e.preventDefault()` to avoid JCEF focus-loss bug |
| Session overlay | List/create/switch/delete sessions |
| Skills overlay | Browse skills, toggle ON/OFF |
| Stats bar | Session chips (turns, errors, tokens, cost, latency, TTFT, LLM calls); mini bar charts; context bar; server memory chip; git chip; git quick action buttons |
| `handleHostMessage(msg)` | Dispatches all inbound messages from host to the correct UI update function |
| `window.sendToHost(msg)` | Single function used by all webview→host sends (calls `window.chrome.webview.postMessage` in JetBrains; `vscode.postMessage` in VS Code) |
| `applyMode(mode)` | Updates mode pill and `state.mode`; guarded by `state.modeCustomized` to respect host `defaultMode` |
| `selectMention / selectSkill / selectPrompt` | Full-value regex replacement (`inputEl.value.replace(/pattern$/, ...)`) — cursor-position-independent |

---

### JetBrains plugin — key classes

| Class | Package | Responsibility |
|---|---|---|
| `JiuwenSwarmService` | `service/` | App-level service; holds the WebSocket connection, session ID, model list; dispatches E2A frames to the tool window |
| `ChatToolWindow` | `ui/` | Tool window factory; creates the JCEF browser; bridges `window.sendToHost` → service → gateway; implements all host→webview `dispatchToWebview()` calls; contains `sendCurrentStatus()` |
| `ContextCollector` | `context/` | Reads `FileEditorManager`, `PsiFile`, `Editor`, `VcsUtil`, `ProjectFileIndex` under `ReadAction`; assembles JSON context; reads project rules; resolves @-mentioned file contents |
| `JiuwenSwarmSettings` | `settings/` | `@Service(Level.APP)` persistent state: host, port, channelId, defaultMode, autoConnect, autoApplyEdits, approveEdits, runCommandsInTerminal, keepAliveEnabled, keepAliveInterval, projectTreeEnabled, projectTreeMaxFiles, loadHistoryOnSwitch, rewindEnabled, gitEnabled |
| `StatusBarWidget` | `ui/` | `StatusBarWidget` implementing `StatusBarWidget.Multiframe`; shows connection state + token count; click triggers reconnect |
| `SendSelectionAction` | `actions/` | `AnAction`; copies active editor selection + file context to chat input; registered on `⌘⇧E` |
| `NewSessionAction` | `actions/` | `AnAction`; sends `createSession` to webview; registered on `⌘⇧J` |
| `FixWithJiuwenSwarmAction` | `actions/` | `IntentionAction`; surfaces on any `ProblemDescriptor`; sends diagnostic context + "Fix this error" to chat |
| `TerminalRunner` | `terminal/` | Finds or creates a `TerminalView` tab named "JiuwenSwarm"; runs commands via `ShellTerminalWidget` |

**`ReadAction` boundary:**
All calls to IntelliJ PSI, `VirtualFile`, `FileEditorManager`, and `VcsUtil` inside
`ContextCollector` are wrapped in `ApplicationManager.getApplication().runReadAction { }`.
These APIs may only be called from a read-safe context.

---

### VS Code extension — key modules

| Module | Responsibility |
|---|---|
| `extension.ts` | `activate()`: registers `ChatViewProvider`, status bar item, all commands, code action provider; manages global WebSocket client lifecycle |
| `webviewProvider.ts` | `WebviewViewProvider`; loads `chat.html` into a VS Code webview panel; handles `onDidReceiveMessage` (webview→host) and `postMessage` (host→webview) |
| `gatewayClient.ts` | WebSocket connection to JiuwenSwarm gateway; E2A event parsing; exponential backoff reconnect; emits typed events to the extension |
| `contextCollector.ts` | Reads `vscode.window.activeTextEditor`, `vscode.languages.getDiagnostics`, `vscode.workspace.workspaceFolders`, `vscode.window.tabGroups`; assembles context JSON |
| `settingsManager.ts` | Reads/writes `vscode.workspace.getConfiguration('jiuwenswarm')`; provides typed getters for all settings |
| `statusBarItem.ts` | `vscode.StatusBarItem`; updates connection state text and token count; click command triggers reconnect |
| `terminalRunner.ts` | `vscode.window.createTerminal` with name "JiuwenSwarm"; `sendText()` to run commands |
| `codeActionProvider.ts` | `vscode.CodeActionProvider`; returns a "Fix with JiuwenSwarm" `CodeAction` for any diagnostic |

---

### Settings reference

**JetBrains** (`JiuwenSwarmSettings.kt`):

| Setting | Default | Description |
|---|---|---|
| `host` | `127.0.0.1` | Gateway hostname |
| `port` | `19000` | Gateway WebSocket port |
| `channelId` | `ide` | Channel identifier sent in handshake |
| `defaultMode` | `code.plan` | Default agent mode applied on connect |
| `autoConnect` | `true` | Connect automatically on IDE start |
| `autoApplyEdits` | `false` | Apply file edits without diff dialog |
| `approveEdits` | `true` | Show approval prompt before applying edits |
| `runCommandsInTerminal` | `true` | Route agent shell commands to IDE terminal |
| `keepAliveEnabled` | `true` | Send WebSocket ping frames |
| `keepAliveInterval` | `30` | Ping interval in seconds |
| `projectTreeEnabled` | `true` | Include project tree in context |
| `projectTreeMaxFiles` | `200` | Max files listed in project tree |
| `loadHistoryOnSwitch` | `true` | Load conversation history when switching sessions |
| `rewindEnabled` | `true` | Enable checkpoint / rewind feature |
| `gitEnabled` | `false` | Show git commit + push buttons in status bar |

**VS Code** (`package.json` / `settingsManager.ts`):

| Setting key (`jiuwenswarm.*`) | Default | Description |
|---|---|---|
| `host` | `127.0.0.1` | Gateway hostname |
| `port` | `19000` | Gateway WebSocket port |
| `channelId` | `ide` | Channel identifier sent in handshake |
| `defaultMode` | `code.plan` | Default agent mode on connect |
| `autoConnect` | `true` | Connect automatically on VS Code start |
| `autoApplyEdits` | `false` | Apply file edits without confirmation |
| `approveEdits` | `true` | Show confirmation prompt before applying edits |
| `runCommandsInTerminal` | `true` | Route agent shell commands to VS Code terminal |
| `useDiffViewer` | `false` | Open VS Code diff editor for file edit proposals |
| `keepAlive.enabled` | `true` | Send WebSocket ping frames |
| `keepAlive.interval` | `30` | Ping interval in seconds |
| `loadHistoryOnSwitch` | `true` | Load conversation history when switching sessions |
| `rewindEnabled` | `true` | Enable checkpoint / rewind |
| `gitEnabled` | `false` | Show git commit + push buttons |

---

## Technical Constraints

**Shared webview sync is manual.**
`packages/shared-webview/chat.html` must be copied to both
`packages/jetbrains-plugin/src/main/resources/webview/chat.html` and
`packages/vscode-extension/resources/chat.html` after every change.
If the copy step is skipped, the two IDE packages diverge silently.
There is no automated check that enforces this.

**JCEF `onmousedown` requirement.**
In JCEF, `onclick` on popup list items fires after the textarea loses focus, which resets
`selectionStart` to 0. All three popup item handlers (`@` mention, `#` skill, `!` preset)
use `onmousedown` + `e.preventDefault()` to prevent focus loss before the handler fires.
This is a permanent constraint of the JCEF rendering model.

**`ReadAction` boundary in JetBrains.**
All IntelliJ PSI and `VirtualFile` access in `ContextCollector` runs inside
`ApplicationManager.getApplication().runReadAction { }`. Calls outside this boundary
cause `AlreadyDisposedException` or PSI exceptions on background threads.

**Context injection size.**
Context JSON sent per message is bounded by `projectTreeMaxFiles` (default 200) and
a max of 10 open tabs and 10 diagnostics. @-mentioned file contents are not bounded by
file count (one file read per mention) but are bounded by what the gateway accepts.
Very large files mentioned with `@` may cause gateway rejections if the total payload
exceeds the model's context window.

**Checkpoint granularity.**
The rewind / checkpoint feature snapshots files at the end of each agent turn
(after all `editApplied` events are received). Only the most recent turn's snapshots
are stored. Rewinding past more than one turn is not possible.

**Diff dialog is preview-only (JetBrains).**
The `DiffManager.showDiff()` call opens a read-only preview. The file is written by
the host plugin after the user clicks Accept in the dialog, not by the diff viewer itself.

**No per-hunk accept/reject.**
The diff workflow is all-or-nothing per file. Accepting a file edit applies the entire
proposed content. Rejecting discards it entirely. Inline per-hunk selection is not
implemented.

**One active task at a time.**
Both plugins disable the send button while `state.streaming` is true. There is no queue
for multiple concurrent agent tasks.

**`navigateToSymbol` uses plain text search.**
Symbol navigation from agent responses searches the project for the identifier string.
It does not use PSI references, LSP go-to-definition, or any semantic index.

---

## Impact on Existing Systems

### JiuwenSwarm gateway

No changes required. The plugin uses only existing endpoints:

| Interface | Used for |
|---|---|
| `ws://host:port/ws` | WebSocket connection for streaming; E2A envelope transport |
| `GET /models` | Fetch available model list |
| `GET /sessions/` | List sessions |
| `POST /sessions/` | Create a new session |
| `GET /sessions/{id}/history` | Load session conversation history |
| `DELETE /sessions/{id}` | Delete a session |
| `GET /skills` | Fetch skill list |
| `GET /memory` | Fetch server memory stats |

All requests use existing Bearer token authentication. No new endpoints, no schema
changes, no database changes.

### Existing web UI

No impact. The IDE plugin and the web UI are independent clients to the same gateway.
Both may be connected simultaneously. Session state is shared through the gateway —
a session opened in the IDE is visible in the web UI and vice versa.

### Performance

Each connected IDE instance holds one persistent WebSocket connection to the gateway.
Context injection adds approximately 1–5 KB of JSON to each outbound message (varies
with project tree depth and number of open tabs). Load scales linearly with active
IDE connections. No gateway changes are needed to support this load.

### Security

| Surface | Risk | Mitigation |
|---|---|---|
| WebSocket connection | Plugin connects to `ws://` (not `wss://`) by default | For remote/public gateways, set the host to an HTTPS-proxied URL and use `wss://`; local loopback connections (`127.0.0.1`) are acceptable on localhost |
| Bearer token in settings | Stored in IDE settings store (plaintext on disk) | The IDE settings store is readable by the local OS user only; same risk profile as browser cookie or SSH key |
| File write via agent edits | Agent can propose edits to any file in the project | Every edit requires explicit user approval (diff dialog or approval prompt) before being written; auto-apply mode disables this approval |
| Shell command execution | Agent can request terminal commands | Commands run in a visible IDE terminal tab; the developer sees and can interrupt them; no silent background execution |

---

## End-to-End Scenarios

### Scenario A — Developer asks the agent to fix a compiler error

**Context:** The developer has a Python file open with a type error highlighted by the
IDE. They want the agent to fix it.

```
1. DEVELOPER presses Alt+Enter on the error squiggle
   IDE shows a lightbulb menu
   "Fix with JiuwenSwarm" appears as a quick-fix action

2. FixWithJiuwenSwarmAction fires
   HOST reads:
     - active file path + content
     - cursor line (at the error)
     - diagnostic text ("Expected str, got int at line 42")
   HOST sends to webview: focus chat, pre-fill message
     "Fix this error: Expected str, got int at line 42"
     + full IDE context JSON attached

3. DEVELOPER presses Enter in the chat panel (or the action auto-sends)
   WEBVIEW → HOST: sendMessage {text, context}
   HOST → GATEWAY: WebSocket message with full payload

4. GATEWAY: agent reasons about the error and proposes a file edit
   Returns e2a.chunk stream with reasoning + tool call (write_file)
   HOST → WEBVIEW: stream.chunk (tokens appear), stream.toolStart (tool card appears)

5. GATEWAY: file edit proposed
   HOST receives fileEditProposal {path, diff, newContent}
   HOST → WEBVIEW: fileEditProposal
   WEBVIEW: shows "Apply / Reject" card

6. DEVELOPER clicks Apply
   WEBVIEW → HOST: applyEdit {requestId, path}
   HOST (JetBrains): opens diff dialog (preview)
   HOST (VS Code): shows approval notification or applies directly

7. DEVELOPER confirms (JetBrains: clicks Accept in diff dialog)
   HOST: writes file, saves snapshot for rewind
   HOST → WEBVIEW: editApplied {path}
   WEBVIEW: marks card as applied

8. GATEWAY: e2a.complete
   HOST → WEBVIEW: stream.complete
   HOST → WEBVIEW: sessionStats (updated chips)
   IDE: compiler re-evaluates file; error squiggle disappears
```

**Error paths:**
- Developer clicks Reject at step 7 → HOST sends editRejected → webview marks card rejected → file unchanged
- Developer clicks Undo (Ctrl+Z) after step 7 → IDE undo stack reverses the file write
- Developer clicks Rewind bar → HOST restores snapshot from checkpoint

---

### Scenario B — Developer asks about code using `@` file mention

**Context:** The developer is working in file `api/routes.py` and wants to ask the
agent how it relates to `models/user.py`.

```
1. DEVELOPER opens chat panel (⌘⇧J or sidebar)
   DEVELOPER types "@model" in the input

2. WEBVIEW: detects "@" trigger, sends listWorkspaceFiles to HOST
   HOST: walks project root (ContextCollector.getWorkspaceFiles())
   Skips: .git, .gradle, .idea, build, dist, node_modules, target,
          __pycache__, .venv, venv, .tox, coverage, .cache
   Returns ≤500 file paths

3. WEBVIEW: shows file picker, filters by "model"
   Shows: models/user.py, models/product.py, models/order.py
   DEVELOPER selects models/user.py

4. WEBVIEW: token "#models/user.py" inserted in input
   DEVELOPER types question: "@models/user.py How does api/routes.py use this?"
   Presses Enter

5. WEBVIEW → HOST: sendMessage
   HOST: collectContext()
     - active file: api/routes.py (full content, or first 200 lines)
     - mentions: ["models/user.py"] → read file → attach content
     - + standard fields (cursor, diagnostics, open tabs, project tree, git, rules)
   HOST → GATEWAY: message + context JSON

6. GATEWAY: agent reads both files in context, responds with explanation
   e2a.chunk stream → tokens appear in webview
   e2a.complete

7. DEVELOPER reads response; no file writes proposed
```

---

## External Dependencies

### Build toolchain

| Dependency | Version | Used by |
|---|---|---|
| JDK | 17 | JetBrains plugin build |
| Gradle | 8.7 | JetBrains plugin build |
| `org.jetbrains.intellij.platform` Gradle plugin | 2.x | JetBrains plugin: build, verify, sign, publish |
| `marketplace-zip-signer` | Current | JetBrains plugin signing (CI) |
| Node.js | 18+ | VS Code extension build |
| webpack | 5 | VS Code extension bundler |
| TypeScript | 5+ | VS Code extension language |
| `vsce` | Current | VS Code extension packaging and publishing |

### Runtime dependencies

| Dependency | Used by | Notes |
|---|---|---|
| IntelliJ Platform SDK (`ideaIC`) | JetBrains plugin | Tool window, JCEF, PSI, VCS APIs; version `sinceBuild = "231"` (2023.1) |
| VS Code Extension API (`@types/vscode`) | VS Code extension | Webview, terminal, status bar, code actions |
| JiuwenSwarm gateway | Both | WebSocket endpoint; E2A streaming protocol |

### Distribution platforms

| Platform | Package | Notes |
|---|---|---|
| JetBrains Plugin Marketplace | JetBrains plugin | First release reviewed in 2–5 business days; subsequent updates faster |
| VS Code Marketplace | VS Code extension | `vsce publish` on `vscode-vX.Y.Z` tag |
| Open VSX Registry | VS Code extension | Secondary; for open-source VS Code forks (Codium, etc.) |

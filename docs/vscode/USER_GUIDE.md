# JiuwenSwarm VS Code Extension — Usage Guide

Complete reference for every setting, panel element, and workflow. For installation see [README.md](README.md).

---

## Configuration

Open **Settings → Extensions → JiuwenSwarm** (or search `jiuwenswarm` in the Settings editor):

| Setting | Default | Description |
|---------|---------|-------------|
| `jiuwenswarm.host` | `localhost` | Hostname or IP of the JiuwenSwarm WebSocket server |
| `jiuwenswarm.port` | `19000` | Port — connects to `ws://host:port/ws` |
| `jiuwenswarm.channelId` | `ide` | Client identifier shown in server logs and traces |
| `jiuwenswarm.autoConnect` | `true` | Open the WebSocket when VS Code starts |
| **`jiuwenswarm.defaultMode`** | `code.plan` | Mode applied when a new session is created (`code.plan` / `code.normal` / `code.team`). Applied to the UI on every connection and can be overridden per-session by the mode selector. |
| `jiuwenswarm.approveEdits` | `false` | Require explicit approval before applying any agent file edit |
| `jiuwenswarm.loadHistoryOnSwitch` | `true` | Fetch and display past messages when switching to an existing session |
| `jiuwenswarm.rewindEnabled` | `true` | Snapshot files before agent edits; show the rewind bar after each turn |
| `jiuwenswarm.projectTree.enabled` | `true` | Prepend a 2-level directory listing of the workspace root to every message |
| `jiuwenswarm.projectTree.maxFiles` | `200` | Max entries in the project tree listing (10–2000) |

Settings take effect immediately. Reload the VS Code window if prompted after a settings change.

---

## Opening the Panel

Click the **JiuwenSwarm** icon in the Activity Bar, or press **Ctrl+Shift+J** / **⌘⇧J**. The panel opens as a sidebar webview. It can be moved to the right sidebar or any other view container via drag-and-drop.

A session is created automatically on first connect. The header shows the session title and live connection state.

---

## Header Bar

```
● Session title                    [New] [⚙]
```

| Element | Description |
|---------|-------------|
| Status dot | Green = connected; spinning = connecting; orange = reconnecting; red = disconnected. Click to reconnect when disconnected. |
| Session title | Name of the active session |
| **New** button | Start a fresh session: reconnects the WebSocket and clears the message list |
| **⚙** menu | Sessions, Skills, Theme (Auto/Dark/Light), Debug log |

---

## Mode Selector

The mode pill in the bottom input bar controls how the agent works:

| Mode | Key | Description |
|------|-----|-------------|
| **Plan & Execute** | `code.plan` | Agent reads files and designs a plan, then waits for you to approve before making any edits. Best for non-trivial or risky changes. |
| **Execute** | `code.normal` | Agent edits files and runs commands without a planning phase. Best for clear, contained tasks. |
| **Team Coding** | `code.team` | A leader agent breaks the task into parallel sub-tasks and assigns them to specialist agents simultaneously. Best for large decomposable work. |

Click the mode pill to open the dropdown. If the current session already has messages, switching mode asks for confirmation and starts a new session. The default mode at startup comes from `jiuwenswarm.defaultMode`.

---

## Chat Input

```
[+]  [mode ▾]  @ files · # skills · ! prompts — Enter to send · Shift+Enter for new line     [↑]
```

| Element | Description |
|---------|-------------|
| **+** | Opens a file picker to attach images (PNG, JPEG, WebP, GIF; up to 10 MB each). Previews appear above the input; click **✕** to remove. Images are base64-encoded and sent with the message. |
| Mode pill | Quick mode switcher |
| Textarea | Grows vertically as you type. **Enter** sends; **Shift+Enter** inserts a newline. |
| Send / Stop button | Submits the message while idle. Becomes a Stop button while the agent is streaming — click to interrupt. |

### Inline pickers

Three characters trigger autocomplete dropdowns that appear above the input:

**`@` — file mention**

Type `@` followed by part of a filename to search workspace files. Selecting a file inserts `@relative/path/to/file` into the message. When sent, the extension reads the file and includes its full contents in the context under a fenced code block.

**`#` — skill picker**

Type `#` to see all registered skills. Continue typing to filter by name. Selecting a skill inserts `#skill-name` into the message.

**`!` — preset prompts**

Type `!` to see eight built-in prompt templates:

| Label | Template |
|-------|---------|
| Explain | Explain what this code does and how it works. |
| Fix bug | Find and fix the bug in this code. Explain what caused it. |
| Write tests | Write unit tests for this code. Cover edge cases. |
| Refactor | Refactor this code to be cleaner and more maintainable. |
| Optimize | Optimize this code for performance. Explain the changes. |
| Document | Add clear documentation and comments to this code. |
| Review | Review this code for bugs, security issues, and improvements. |
| Implement | Implement the following feature: |

Continue typing to filter the list. Selecting a template replaces `!query` with the full prompt text.

For all three pickers: **Arrow keys** navigate, **Enter** or **Tab** selects, **Escape** dismisses.

---

## Message List

Each completed turn consists of:

- **Your message** — right-aligned bubble.
- **Thinking block** — when the model uses extended reasoning, a collapsible **Thinking…** section appears before the response. Click the arrow to expand or collapse.
- **Agent response** — full Markdown rendering: headings, bold/italic, syntax-highlighted code blocks, tables, lists.
- **Tool call cards** — every tool the agent invokes appears as an inline card with:
  - Tool icon and name (`📝 str_replace_editor`, `💻 bash`, `🔍 web_search`, `🔧 mcp_tool`, etc.)
  - Live spinner → checkmark or ✕ on completion
  - Collapsible **Inputs** section (parameters sent to the tool)
  - Collapsible **Output** section (result returned by the tool)

---

## Stats Bar and Metrics

Below the message list, a stats bar displays session-level metrics that update after each turn:

- **Turns** — total completed turns in the session
- **Tokens** — cumulative token count (input + output)
- **Cost** — estimated USD cost (shown when the server reports pricing)
- **Tool calls** — total tool invocations; click the chip to see a per-tool breakdown
- **Avg latency** — mean response time across turns
- **TTFT** — mean time-to-first-token

Click the bar chart icon on the right to toggle **mini charts** — bar graphs showing tokens and duration per turn.

A **context bar** below the input shows how full the active model's context window is (0–100%). The bar turns orange above 80%, red above 95%.

---

## IDE Context Injection

Every message has a structured context block prepended. The agent sees it as part of your message.

### What is injected

| Field | Source |
|-------|--------|
| Active file path and language | `vscode.window.activeTextEditor` + `document.languageId` |
| Cursor line | `editor.selection.active.line` |
| Selected code | `editor.document.getText(editor.selection)` (if non-empty) |
| Diagnostics (up to 10) | `vscode.languages.getDiagnostics(doc.uri)` |
| Other open tabs (up to 10) | `vscode.window.tabGroups.all` |
| Project tree (2-level) | Workspace folder traversal; skips `.git`, `build`, `node_modules`, `dist`, `target`, etc. |
| Git branch + change count | `git rev-parse` + `git status --porcelain` subprocess |
| Project rules | First non-empty file found: `.jiuwenswarm/instructions.md`, `.jiuwenswarm/rules.md`, `AGENTS.md` |
| @-mentioned files | Full file content for each `@path` typed in the message |

### Project rules

Create a file at the workspace root to inject standing instructions into every message:

```
.jiuwenswarm/instructions.md   ← checked first
.jiuwenswarm/rules.md          ← checked second
AGENTS.md                      ← checked third
```

Use it to define coding style, forbidden patterns, preferred libraries, or any project-specific context the agent should always know.

### Controlling what is injected

| Setting | Effect |
|---------|--------|
| `jiuwenswarm.projectTree.enabled` | Toggle the directory listing on or off |
| `jiuwenswarm.projectTree.maxFiles` | Limit entries (10–2000) for large mono-repos |

### Example context block

````
<!-- IDE Context -->
Active file: /Users/mishka/project/src/api/handler.py  (python)
Cursor line: 87

Selected code:
```python
def handle_request(req):
    result = blocking_call(req)
    return result
```

Diagnostics (2):
  • Line 87: Variable 'result' is not used before return
  • Line 88: blocking_call is deprecated

Other open files (2):
  /Users/mishka/project/src/api/router.py
  /Users/mishka/project/tests/test_handler.py

Project structure:
  src/
    api/
    models/
  tests/
  pyproject.toml

Git: branch=feature/async-refactor, 3 uncommitted changes

Project rules:
Always use async/await. No blocking calls. Follow PEP 8.
<!-- End IDE Context -->
````

---

## Clickable File Links

File paths in agent responses become clickable links that open the file at the referenced line.

| Pattern | Example | Effect |
|---------|---------|--------|
| Backtick path with directory | `` `src/api/handler.py` `` | Opens file at line 1 |
| Backtick path with line | `` `src/api/handler.py:42` `` | Opens file at line 42 |
| Bare `path/to/file.ext:N` | `src/auth/router.py:87` | Opens file at line 87 |

Plain identifiers in backticks (no `/` and no `:N`) are not linkified as files. Paths inside fenced code blocks are rendered verbatim.

---

## Symbol Navigation

PascalCase (`` `MyClass` ``) and SCREAMING_SNAKE_CASE (`` `MAX_RETRIES` ``) identifiers in backticks appear as purple links. Clicking uses `vscode.executeWorkspaceSymbolProvider` to find the symbol and opens the first result. Common acronyms (API, HTTP, JSON, TODO, etc.) are excluded.

---

## Actions and Keyboard Shortcuts

| Action | Win / Linux | Mac |
|--------|-------------|-----|
| Open / focus chat panel | `Ctrl+Shift+J` | `⌘⇧J` |
| Send selection | `Ctrl+Shift+E` | `⌘⇧E` |
| New session (command palette) | — | — |
| Fix with JiuwenSwarm (lightbulb) | `Ctrl+.` | `⌘.` |

**Open / focus** — opens the JiuwenSwarm sidebar. If already open, focuses the input field.

**Send selection** (`Ctrl+Shift+E` / `⌘⇧E` or right-click → **Send Selection to JiuwenSwarm**) — opens the panel and pre-fills the input with the selected code:

```
[File: handler.py]
```python
def handle_request(req):
    ...
```
```

Add your question and press Enter.

**New session** (command palette: `JiuwenSwarm: New Session`) — reconnects the WebSocket to start a fresh session.

---

## Code Action Quick Fix

VS Code shows a lightbulb 💡 next to any line that has an error or warning. JiuwenSwarm registers a **Fix with JiuwenSwarm** code action:

1. Place the cursor on a line with an error (red squiggly).
2. Click the lightbulb or press `Ctrl+.` / `⌘.`.
3. Select **Fix with JiuwenSwarm**.
4. The chat panel opens with the error message and ±7 lines of surrounding code pre-filled:

```
Fix this error in handler.py:

Error:
Variable 'result' is not used before return

```python
def handle_request(req):
    result = blocking_call(req)
    return result
```
```

5. Press Enter to send.

Works for any language VS Code has diagnostics for — TypeScript, Python, Java, Go, Rust, C#, and more.

---

## File Edit Workflow

When the agent calls `str_replace_editor`, `write_file`, or `create_file`, the extension applies the edit to the workspace using Node.js `fs` operations. A notification toast confirms each applied change.

### With approval

Enable `jiuwenswarm.approveEdits` in settings to see an **Approve / Reject** prompt before every file change. Clicking **Reject** discards the edit; clicking **Approve** writes it to disk.

---

## Terminal Integration

Agent shell commands (`bash`, `run_command`) run in a **JiuwenSwarm** terminal created by `vscode.window.createTerminal()`. The terminal is created on the first command and reused. If you close it, a new one is created on the next command.

---

## Checkpoint / Rewind

After any agent turn that edits files, the rewind bar appears below the message list:

```
⟲ Agent edited files this turn    [⟲ Undo changes]
```

### How it works

Before the agent's first edit to a file in a given turn, the extension snapshots that file's current content (read via the VS Code filesystem API). At the end of the turn (`chat.final`) the snapshots are locked in.

### Using rewind

Click **⟲ Undo changes**. The extension restores every snapshotted file. Files that did not exist before the turn are deleted. A notification toast confirms each restored file.

A status line confirms the result:

```
⟲ Rewound 3 file(s)
```

### Limits

| Scenario | Behaviour |
|----------|-----------|
| Agent created a file | File is deleted on rewind |
| Agent edited a file | File is restored to pre-turn state |
| You send another message | Bar disappears; snapshots are discarded |
| New session | Bar cleared |

Disable via `jiuwenswarm.rewindEnabled` in settings.

---

## Sessions

### Opening the overlay

Click **⚙ → Sessions** in the header.

### What the list shows

Each row shows session title, time of last message (relative), and message count.

### Switching

Click a row to switch. With `jiuwenswarm.loadHistoryOnSwitch` on, past messages stream in automatically.

### Creating

Click **New** in the header or run `JiuwenSwarm: New Session` from the command palette.

### Deleting

Click **✕** on a non-active session row. Click once (turns red) then again within 2 seconds to confirm. The active session cannot be deleted — start a new session first.

### Refreshing

Click **↺** in the overlay header. Up to 20 sessions are shown.

---

## Skills

### Opening the overlay

Click **⚙ → Skills** in the header.

### What the list shows

Each skill row shows name, description, trigger, and ON/OFF toggle. Click ON or OFF to enable or disable. The change is sent to the server via `skills.toggle`.

### Picking a skill from the input

Type `#` in the textarea. A popup lists all loaded skills. Filter by continuing to type. Select with Enter or Tab.

---

## Connection Status Bar

The status bar (bottom-right) shows live WebSocket state:

| Icon | Meaning |
|------|---------|
| `$(check) JiuwenSwarm` | Connected |
| `$(loading~spin) JiuwenSwarm` | Connecting |
| `$(sync~spin) JiuwenSwarm` (yellow) | Reconnecting — exponential backoff: 1 s → 30 s max |
| `$(circle-slash) JiuwenSwarm` (red) | Disconnected — click to reconnect |

Token total appears next to the label: `$(check) JiuwenSwarm · 42.3k`.

---

## Theme

| Option | Description |
|--------|-------------|
| **⚙ → ◐ Auto** | Follows VS Code's light or dark theme (default) |
| **⚙ → 🌙 Dark** | Forces dark regardless of VS Code theme |
| **⚙ → ☀ Light** | Forces light regardless of VS Code theme |

Stored in webview local storage; survives panel restarts.

---

## Model Selector

When connected to a server that has multiple models configured, a model dropdown appears in the input bar. Click to open it and switch models. The active model is shown in the mini model chip.

---

## Debug Log

Click **⚙ → Debug log** to open a scrollable log panel below the message list. It records:

- Every WebSocket frame received (raw JSON with timestamp)
- Every message sent (content, context size, media item count)
- Session switches, reconnects, connection state transitions
- Action dispatches (list_sessions, list_skills, toggle_skill, etc.)
- File edit tool calls (tool name and parameters)

The panel keeps the most recent 500 lines. Toggle off to hide; the log clears on the next enable.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Panel is blank | `chat.html` missing or CSP issue | Reinstall from the latest VSIX |
| Status bar shows `$(circle-slash)` | Server not running or wrong host/port | Start JiuwenSwarm; verify settings; click widget to reconnect |
| Messages send, no response | Server unreachable after handshake | Enable Debug log; check for error frames; open Webview Developer Tools from the command palette |
| Send Selection does nothing | No text selected | Ensure text is selected in the editor before pressing the shortcut |
| File links don't open | File path not in workspace | Check that the referenced file exists |
| Rewind bar missing | `jiuwenswarm.rewindEnabled` is false, or edit was rejected | Enable rewind in settings |
| Rewind restores 0 files | Snapshots cleared by a subsequent message | Click Undo immediately after the turn ends |
| "Loading history…" never disappears | Server did not send `history.done` | Reconnect via the status bar; check server logs |
| Session list stays on "Loading…" | Server timeout or `session.list` not supported | Click ↺ Retry; check server logs |
| Skills list shows error | Server does not support `skills.list` | Expected on older server versions; upgrade the server |

### Reading extension logs

1. Open **View → Output** (`Ctrl+Shift+U` / `⌘⇧U`).
2. Select **JiuwenSwarm** from the dropdown.

For webview JavaScript errors:

1. Run **Developer: Open Webview Developer Tools** from the command palette.
2. Check the **Console** tab.

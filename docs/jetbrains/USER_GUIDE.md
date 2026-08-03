# JiuwenSwarm JetBrains Plugin — Usage Guide

Complete reference for every setting, panel element, and workflow. For installation see [README.md](README.md).

---

## Configuration

Open **Settings → Tools → JiuwenSwarm**.

| Setting | Default | Description |
|---------|---------|-------------|
| Server host | `127.0.0.1` | Hostname or IP of the JiuwenSwarm WebSocket server |
| Server port | `19000` | Port — connects to `ws://host:port/ws` |
| Channel ID | `ide` | Client identifier shown in server logs and traces |
| Connect on startup | on | Open the WebSocket when the IDE starts |
| **Default mode** | `code.plan` | Mode applied when a new session is created (Plan & Execute / Execute / Team Coding). Applied to the UI on every connection and can be overridden per-session by the mode selector. |
| Auto-apply file edits | off | Apply agent file edits immediately without opening the diff review window |
| Require approval before edits | off | Show a confirmation prompt before applying any agent file edit |
| Run commands in IDE terminal | on | Route agent shell commands to a dedicated JiuwenSwarm terminal tab |
| Keep-alive ping | on | Send periodic WebSocket ping frames to prevent server-side timeout |
| Keep-alive interval | 30 s | Seconds between pings (5–300) |
| Include project tree | on | Prepend a 2-level directory listing of the project root to every message |
| Project tree max files | 200 | Maximum file entries in the tree listing (10–2000) |
| Load history on session switch | on | Fetch and display past messages after switching to an existing session |
| **Enable checkpoint / rewind** | on | Snapshot files before agent edits; show the rewind bar at the end of each turn |
| **Git quick actions** | off | Show Commit and Push buttons below the message list |

Settings are stored in `jiuwenswarm.xml` in the IDE config directory and persist across restarts.

---

## Opening the Panel

Click **JiuwenSwarm** in the right sidebar tool window. The panel opens as a docked JCEF (embedded Chromium) tool window and can be resized, moved, or floated like any other tool window.

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

Click the mode pill to open the dropdown. If the current session already has messages, switching mode asks for confirmation and starts a new session. The default mode applied at startup comes from **Settings → Default mode**.

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

Type `@` followed by part of a filename to search workspace files. Selecting a file inserts `@relative/path/to/file` into the message. When sent, the plugin reads the file and includes its full contents in the context under a fenced code block.

**`#` — skill picker**

Type `#` to see all registered skills. Continue typing to filter by name. Selecting a skill inserts `#skill-name` into the message. Skills listed here are the same ones shown in the Skills overlay.

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

Click the bar chart icon on the right to toggle **mini charts** — bar graphs showing tokens and duration per turn. Click any bar to view that turn's details.

Below the stats bar, a **context bar** shows how full the active model's context window is (0–100%). When the model reports context occupancy above 80%, the bar turns orange; above 95%, red.

The **server memory** chip shows live JiuwenSwarm server RAM usage (RSS / available), polled every 10 seconds.

---

## IDE Context Injection

Every message has a structured context block prepended before being sent. The agent sees it as part of your message — no manual copy-pasting required.

### What is injected

| Field | Source |
|-------|--------|
| Active file path and language | `FileEditorManager` + `FileType` |
| Cursor line | `Editor.caretModel` |
| Selected code | `Editor.selectionModel` (if non-empty) |
| Diagnostics (up to 10) | Document markup model (error-stripe highlighters) |
| Other open tabs (up to 10) | `FileEditorManager.openFiles` |
| Project tree (2-level) | `LocalFileSystem` traversal; skips `.git`, `build`, `node_modules`, `target`, `.venv`, etc. |
| Git branch + change count | `git rev-parse` + `git status --porcelain` subprocess |
| Project rules | First non-empty file found: `.jiuwenswarm/instructions.md`, `.jiuwenswarm/rules.md`, `AGENTS.md` |
| @-mentioned files | Full file content for each `@path` typed in the message |

### Project rules

Create a file at the project root to inject standing instructions into every message:

```
.jiuwenswarm/instructions.md   ← checked first
.jiuwenswarm/rules.md          ← checked second
AGENTS.md                      ← checked third
```

Use it to define coding style, forbidden patterns, preferred libraries, or any project-specific context the agent should always know.

### Controlling what is injected

| Setting | Effect |
|---------|--------|
| **Include project tree** | Toggle the directory listing on or off |
| **Project tree max files** | Limit entries (10–2000) for large mono-repos |

### Example context block

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

File paths in agent responses become clickable links. Clicking opens the file in the editor at the referenced line.

| Pattern | Example | Effect |
|---------|---------|--------|
| Backtick path with directory | `` `src/api/handler.py` `` | Opens file at line 1 |
| Backtick path with line | `` `src/api/handler.py:42` `` | Opens file at line 42 |
| Bare `path/to/file.ext:N` | `src/auth/router.py:87` | Opens file at line 87 |

Plain identifiers in backticks (no `/` and no `:N`) are not linkified as files. Paths inside fenced code blocks are rendered verbatim.

---

## Symbol Navigation

PascalCase (`` `MyClass` ``) and SCREAMING_SNAKE_CASE (`` `MAX_RETRIES` ``) identifiers in backticks appear as purple links. Clicking uses `PsiSearchHelper` to find the symbol in the project and opens the first match. Common acronyms (API, HTTP, JSON, TODO, etc.) are excluded.

---

## Actions and Keyboard Shortcuts

| Action | Win / Linux | Mac |
|--------|-------------|-----|
| New session | `Ctrl+Shift+J` | `⌘⇧J` |
| Send selection | `Ctrl+Shift+E` | `⌘⇧E` |
| Fix with JiuwenSwarm | `Alt+Enter` | `⌥Enter` |

**New session** — opens the panel if not already visible, then reconnects the WebSocket to start a fresh session. The message list clears.

**Send selection** — opens the panel and pre-fills the input with the selected code, labelled with the file name:

```
[File: handler.py]
```python
def handle_request(req):
    ...
```
```

Add your question after the code and press Enter.

**Right-click → Send Selection to JiuwenSwarm** — identical to `Ctrl+Shift+E`.

---

## Alt+Enter Quick Fix

Place the cursor on any error or warning gutter marker and press **Alt+Enter**. **Fix with JiuwenSwarm** appears in the lightbulb menu.

What happens:

1. The error tooltip text is read (HTML stripped).
2. ±7 lines of surrounding code are captured.
3. The panel opens and the input is pre-filled:

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

Works for any language PyCharm supports — Python, Kotlin, Java, TypeScript, Go, Rust, and so on.

---

## File Edit Workflow

When the agent calls `str_replace_editor`, `write_file`, or `create_file`, the plugin intercepts it:

### Default: diff review

A side-by-side diff window opens — **Current** (left) vs **Proposed** (right). Review the diff using IntelliJ's standard diff tools. Close the window to apply the change to the in-memory document. Save with `Ctrl+S` / `⌘S` or let the IDE auto-save.

If the target text for a `str_replace` cannot be located, a balloon notification explains the problem.

### Auto-apply

Enable **Auto-apply file edits** to skip the diff dialog. Changes are applied via `WriteCommandAction` and are undoable with `Ctrl+Z`. A balloon notification confirms each edit.

### Require approval

Enable **Require approval before edits** to show a confirmation prompt before any edit is applied or the diff dialog opens. Useful when you want to see every proposed change before committing.

---

## Terminal Integration

Agent shell commands (`bash`, `run_command`) are routed to a **JiuwenSwarm** terminal tab in the Terminal tool window. The tab is created on the first command and reused for all subsequent ones. The Terminal tool window comes to the front automatically.

Disable **Run commands in IDE terminal** in settings to run commands silently in a background process instead.

---

## Checkpoint / Rewind

After any agent turn that edits files, the rewind bar appears below the message list:

```
⟲ Agent edited files this turn    [⟲ Undo changes]
```

### How it works

Before the agent's first edit to a file in a given turn, the plugin snapshots that file's current content. At the end of the turn (`chat.final`) the snapshots are locked in. If no edits were made, the bar does not appear.

### Using rewind

Click **⟲ Undo changes**. The plugin restores every snapshotted file using `WriteCommandAction`. Files that did not exist before the turn are deleted. The restore is itself undoable with `Ctrl+Z`.

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

Disable rewind entirely via **Enable checkpoint / rewind** in settings (reduces memory usage during long agent runs).

---

## Git Quick Actions

Enable **Git quick actions** in settings to show a toolbar below the message list with two buttons:

**Commit** — opens a dialog pre-filled with your last sent message as the commit message (prefixed with "AI: "). Clicking OK runs `git add -u && git commit -m <message>`. The git status chip updates after commit.

**Push** — runs `git push` in the background. Status updates on completion.

The git status chip in the stats bar shows the current branch and the number of uncommitted files. It updates automatically after each agent turn.

---

## Sessions

### Opening the overlay

Click **⚙ → Sessions** in the header. The overlay slides over the message list.

### What the list shows

Each row shows:
- Session title (or raw session ID if untitled)
- Time of last message (relative: "just now", "3m ago", "2h ago", "5d ago")
- Message count

### Switching

Click a row to switch. With **Load history on session switch** on, past messages stream in automatically. A **"Loading history…"** indicator appears until the stream is complete.

### Creating

Click **New** in the header or press `Ctrl+Shift+J` / `⌘⇧J`.

### Deleting

Click **✕** on a non-active session row. The button turns red and the tooltip changes to "Click again to confirm." Click a second time within 2 seconds to confirm deletion. The active session cannot be deleted — switch to another session first.

### Refreshing

Click **↺** in the overlay header to reload the list. Up to 20 sessions are shown.

---

## Skills

### Opening the overlay

Click **⚙ → Skills** in the header.

### What the list shows

Each skill row shows:
- Name and description
- Trigger (the slash command used in-session, e.g. `/commit`)
- ON/OFF toggle

### Toggling

Click ON or OFF. The change is sent immediately to the server via `skills.toggle`. Teal = enabled; muted grey = disabled.

### Picking a skill from the input

Type `#` in the chat textarea. A popup lists all loaded skills. Continue typing to filter. Select with Enter or Tab — the skill name is inserted at the cursor. Skills are loaded the first time `#` is typed; the list is cached for the session.

---

## Connection Status Bar Widget

| Widget | Meaning |
|--------|---------|
| `⬤ JiuwenSwarm` (teal) | Connected |
| `◌ JiuwenSwarm` (teal) | Connecting |
| `↻ JiuwenSwarm` (yellow) | Reconnecting — exponential backoff: 1 s → 2 s → 4 s → … → 30 s max |
| `○ JiuwenSwarm` (grey) | Disconnected — click to reconnect |

Token total appears next to the label once tokens are consumed:

```
⬤ JiuwenSwarm · 42.3k
```

Hover for a tooltip with session ID and full token count.

---

## Theme

| Option | Description |
|--------|-------------|
| **⚙ → ◐ Auto** | Follows the IDE's light or dark theme (default) |
| **⚙ → 🌙 Dark** | Forces dark regardless of IDE theme |
| **⚙ → ☀ Light** | Forces light regardless of IDE theme |

Stored in browser local storage inside JCEF; survives panel restarts.

---

## Model Selector

When connected to a server that has multiple models configured, a model dropdown appears in the input bar. Click to open it and switch models. The active model is shown in the mini model chip. Model availability depends on the server configuration.

---

## Debug Log

Click **⚙ → Debug log** to open a scrollable log panel below the message list. It records:

- Every WebSocket frame received (raw JSON with timestamp)
- Every message sent (content, context size, media item count)
- Session switches, reconnects, connection state transitions
- Action dispatches (list_sessions, list_skills, toggle_skill, etc.)
- File edit tool calls (tool name and parameters)
- Snapshot events (`SNAP →`) and rewind operations

The panel keeps the most recent 500 lines. Toggle off to hide; the log clears on the next enable.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Panel shows blank | JCEF not enabled | Enable `ide.browser.jcef.enabled` in **Help → Find Action → Registry**; restart |
| Status bar shows `○` | Server not running or wrong host/port | Start JiuwenSwarm; verify settings; click status widget to reconnect |
| Messages send, no response | Server unreachable after handshake | Enable Debug log; look for error frames; check server logs |
| Diff window opens but file unchanged | Close the window to apply | The change applies when the diff window is closed, not immediately on open |
| Rewind bar missing | Rewind disabled, or turn ended without file edits | Check **Enable checkpoint / rewind** in settings; enable Debug log and look for `SNAP →` lines |
| Rewind restores 0 files | Snapshots cleared by a subsequent message | Click Undo immediately after the turn; snapshots survive until the next send |
| Alt+Enter does not show the option | No error at cursor | Move cursor onto a line with a red or yellow gutter marker |
| Skills popup shows nothing | Server has no skills, or `list_skills` failed | Check server logs; the popup is empty if the server returns an empty list |
| Session history does not load | **Load history on session switch** is off, or server does not support history | Enable the setting; check server logs for `history.get` errors |
| "Loading history…" never disappears | Server did not send `history.done` | Reconnect via the status bar; check server logs |
| Session ✕ button missing | Session is active | Switch to another session before deleting |
| Project structure not in context | No project root detected | Open a project folder; `project.basePath` must be non-null |
| Git quick actions not visible | **Git quick actions** is off | Enable in Settings → Tools → JiuwenSwarm |

### Reading the IDE log

**Help → Show Log in Explorer** (Windows/Linux) or **Help → Show Log in Finder** (macOS). Search for `JiuwenSwarm`.

# JiuwenSwarm JetBrains Plugin — Usage Guide

This is the complete usage reference for the JiuwenSwarm JetBrains plugin. It covers every panel, setting, action, and workflow in detail. For installation instructions see the [README](README.md).

---

## Configuration

Open **Settings → Tools → JiuwenSwarm**:

| Setting | Default | Description |
|---------|---------|-------------|
| Server host | `localhost` | Hostname or IP of the JiuwenSwarm WebSocket server |
| Server port | `19000` | Port the server is listening on — connects to `ws://host:port/ws` |
| Channel ID | `ide` | Identifies this client in server logs and TraceHound traces |
| Connect automatically on startup | on | Opens the WebSocket connection when the IDE starts |
| Auto-apply file edits (skip diff dialog) | off | When on, agent file edits are written immediately without a review window |

Settings are stored per-IDE-installation in `jiuwenswarm.xml` inside the IDE config directory and persist across restarts.

---

## Opening the Panel

Click the **JiuwenSwarm** tool window button in the right sidebar. The panel opens as a docked tool window and can be moved, resized, or floated like any other IDE tool window.

On first connect a session is created automatically. The panel header shows the session title and the live connection state.

---

## The Chat Panel

The chat panel runs inside JCEF (an embedded Chromium browser). Here is a complete tour of every element.

### Header bar

```
● JiuwenSwarm  [New] [⚙]
```

| Element | Description |
|---------|-------------|
| Status dot | Coloured indicator — green = connected, spinning = connecting, orange = reconnecting, red = disconnected |
| Session title | Name of the active session; updates when a session is loaded or switched |
| **New** button | Starts a fresh session: disconnects, reconnects, and clears the message list |
| **⚙** menu | Opens the settings dropdown: Sessions, Skills, Theme, Debug log |

### Mode selector

Located in the input bar at the bottom:

| Mode | Internal key | Description |
|------|-------------|-------------|
| **Planning Mode** | `agent.plan` | Full planning mode. The agent reasons step-by-step, reads code, and acts autonomously. Best for non-trivial tasks. |
| **Performance Mode** | `agent.fast` | Faster, lighter mode. Good for quick questions and small changes. |
| **Cluster Mode** | `team` | Multi-agent team mode. Spawns specialised sub-agents to collaborate on larger tasks. |

Click the mode button to open the dropdown and switch. If the active session already has messages you will be asked to confirm because switching mode creates a new session.

### Message list

Responses stream token-by-token. Each turn shows:

- **Your message** — right-aligned, darker background.
- **Agent response** — full markdown: headers, bold, italic, code blocks with syntax highlighting, tables, lists.
- **Reasoning block** — when the model uses extended thinking, a collapsible "Thinking…" section appears above the response text. Click the arrow to expand or collapse.
- **Tool call cards** — every tool the agent invokes is shown inline:
  - Icon and tool name (e.g. `📝 str_replace_editor`, `💻 bash`, `🔍 web_search`, `🔧 mcp_tool`)
  - Live status indicator (running spinner → done checkmark or error)
  - Collapsible inputs (the parameters sent to the tool)
  - Collapsible output (the result returned by the tool)
- **Token usage** — after each completed turn, a small counter in the bottom-right of the input bar shows the token count for that exchange (e.g. `1,234 tok`). The running total is also shown directly in the status bar widget.

### Input bar

```
[+] [mode ▾]  [write your message here…]  [→ Send]
```

| Element | Description |
|---------|-------------|
| **+** (attach) | Opens a file picker to attach one or more images (PNG, JPEG, WebP, GIF; up to 10 MB each). Thumbnail previews appear above the input. Click ✕ on a thumbnail to remove it. Images are base64-encoded and sent with the message. |
| Mode pill | Quick-access mode switcher (same choices as described above) |
| Textarea | Type your message. Grows vertically as you type. **Enter** sends; **Shift+Enter** inserts a newline. |
| Send button | Submits the message. Disabled while a response is streaming. |

---

## IDE Context Injection

Every message you send automatically has a structured context block prepended. The agent sees it as part of your message and uses it to understand what you are looking at — no copy-pasting required.

### What is injected

| Field | How it is collected | Example |
|-------|---------------------|---------|
| Active file path and language | `FileEditorManager` + `FileType` | `Active file: /src/api/handler.py  (Python)` |
| Cursor line number | `Editor.caretModel` | `Cursor line: 87` |
| Selected code | `Editor.selectionModel` | Fenced code block of the current selection |
| Editor diagnostics | Document markup model (error-stripe highlighters) | Up to 10 current errors and warnings from the editor gutter |
| Other open tabs | `FileEditorManager.openFiles` | Paths of up to 10 other files open in the editor |
| Project tree structure | `LocalFileSystem` + `VirtualFile` traversal | 2-level directory listing of the project root; skips `node_modules`, `build`, `.git`, `target`, etc. |
| Git status | `git` subprocess | `Git: branch=feature/auth, 3 uncommitted changes` |

The block is assembled at send time. If there is nothing useful (no editor open, no git repo, no selection), the message is sent without a context block.

### Example injected block

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

Project structure:
src/
  api/
  models/
tests/
pyproject.toml
README.md

Git: branch=feature/async-refactor, 3 uncommitted changes
<!-- End IDE Context -->
````

---

## Clickable File Links

When the agent mentions a file path in its response, the plugin turns it into a clickable link. Clicking it opens the file in the editor and jumps to the referenced line.

### What gets linkified

| Pattern | Example | Behaviour |
|---------|---------|-----------|
| Backtick path with directory | `` `src/api/handler.py` `` | Opens file; goes to line 1 |
| Backtick path with line number | `` `src/api/handler.py:42` `` | Opens file; goes to line 42 |
| Bare `path/to/file.ext:N` | `src/auth/router.py:87` | Opens file; goes to line 87 |

### What does NOT get linkified

- Plain variable names in backticks (`` `someVar` ``, `` `myFunc` ``) — no path separator and no `:N` suffix.
- Paths inside fenced code blocks (``` ``` ``` sections) — these are rendered verbatim.
- URLs — the colon in `http://` is not followed by a plain integer.

### How it works

The plugin intercepts the rendered agent text and runs two regex passes before rendering:

1. Backtick-wrapped paths that contain a `/` or end with `:N` are wrapped in a clickable `<a>` element around the existing `<code>` span.
2. Bare `path/to/file:N` references outside backticks and HTML tags are wrapped in a clickable `<a>` element.

Clicking either type sends an `open_file` message from the webview to the IDE, which uses `OpenFileDescriptor` to navigate to the file and line.

---

## Actions & Keyboard Shortcuts

### Keyboard shortcuts

| Action | Win / Linux | Mac | Notes |
|--------|-------------|-----|-------|
| **New session** | `Ctrl+Shift+J` | `⌘⇧J` | Always available |
| **Send selection** | `Ctrl+Shift+E` | `⌘⇧E` | Available when text is selected |

**New session** (`Ctrl+Shift+J` / `⌘⇧J`):
Opens the panel (creating it lazily if this is the first open), then reconnects the WebSocket to start a fresh session. The message list clears and the previous history is left on the server.

**Send selection** (`Ctrl+Shift+E` / `⌘⇧E`):
Opens the panel and pre-fills the input with the selected code labelled with the file name, for example:

> `[File: handler.py]`
> ` ```python`
> `def handle_request(req):`
> `    ...`
> ` ``` `

Add your question after the code block and press Enter to send.

### Editor right-click menu

Right-clicking anywhere in an editor appends **Send Selection to JiuwenSwarm** to the context menu. This is identical to `Ctrl+Shift+E`.

### Alt+Enter quick-fix — "Fix with JiuwenSwarm"

When the cursor is on or adjacent to an error or warning highlight, pressing **Alt+Enter** (the lightbulb menu) includes **Fix with JiuwenSwarm** in the list of available quick-fixes.

What happens when you select it:

1. The plugin reads the error tooltip text at the cursor (HTML formatting stripped).
2. It captures the 15 lines of code surrounding the error (7 lines before, 7 after).
3. The JiuwenSwarm panel opens.
4. The input is pre-filled with a structured prompt containing the error message and the surrounding code:

       Fix this error in handler.py:

       Error:
       Variable 'result' is not used before return

       ```python
       def handle_request(req):
           result = blocking_call(req)
           return result
       ```

5. Press Enter. The agent analyses the error and proposes a fix.

The quick-fix works for any language — Python, Kotlin, Java, TypeScript, Go, Rust, etc.

---

## File Edit Workflow

When the agent calls a file-editing tool (`str_replace_editor`, `write_file`, or `create_file`), the plugin intercepts the call and handles it based on your settings.

### Supported tools

| Tool | Operation |
|------|-----------|
| `str_replace_editor` command=`str_replace` | Replaces a specific block of text in an existing file |
| `str_replace_editor` command=`create` | Creates a new file with given content |
| `write_file` | Overwrites an existing file or creates it if missing |
| `create_file` | Creates a new file; parent directories are created automatically |

### Default: diff review window

A **side-by-side diff window** opens titled **"JiuwenSwarm — Proposed Edit: filename"**:

- **Left** — current file content (labelled "Current")
- **Right** — proposed content (labelled "Proposed")

Use IntelliJ's standard diff tools to review line-by-line. Close the diff window to apply the change to the in-memory document. Save normally (`Ctrl+S` / `⌘S`), or let the IDE auto-save.

If the text targeted by a `str_replace` cannot be located in the file, or the file is missing, a balloon notification in the top-right corner explains the problem.

### Auto-apply mode

Enable **Auto-apply file edits** in **Settings → Tools → JiuwenSwarm** to skip the diff dialog entirely.

- Changes are applied immediately via `WriteCommandAction` and are **undoable** with `Ctrl+Z` / `⌘Z`.
- A balloon notification confirms each applied edit (e.g. "Applied edit to handler.py").
- New files are written to disk with any missing parent directories created automatically.

---

## Checkpoint / Rewind

After every agent turn that edits one or more files, a rewind bar appears between the message list and the debug/input area:

```
⟲ Agent edited files this turn    [⟲ Undo changes]
```

### How it works

Before the agent's first edit to a file in a given turn the plugin snapshots the current file content. At the end of the turn (`chat.final`) the snapshots are locked in and the rewind bar becomes active.

### Using the rewind bar

Click **⟲ Undo changes**. The plugin restores every snapshotted file to its pre-turn state. The restore runs as a `WriteCommandAction`, meaning it is itself **undoable** with `Ctrl+Z` / `⌘Z` if you change your mind.

After a successful rewind the bar disappears and a status line appears in the message list, for example:

```
⟲ Rewound 3 file(s)
```

### Scope and limits

| Scenario | Behaviour |
|----------|-----------|
| Agent created a new file | The file is deleted on rewind |
| Agent edited an existing file | The file is restored to the content before the first edit in the turn |
| You send another message | The bar disappears; snapshots are discarded — only the most recent completed turn can be rewound |
| New session | Rewind bar is cleared |
| Rewind partially fails | Bar disappears; status line reports how many files were restored and how many failed |

---

## Sessions

Sessions maintain separate conversation histories. You can run several parallel conversations (one per project, feature, or topic) and resume any of them at any time.

### Opening the Sessions panel

Click **⚙ → Sessions** in the header dropdown. The Sessions overlay slides into the main panel area, replacing the message list.

### What the list shows

Each session item displays:
- **Session title** (or the raw session ID if no title has been set by the server)
- **Time of last message** (relative: "just now", "3m ago", "2h ago", "5d ago")
- **Message count**

### Switching sessions

Click a session item to switch. The overlay closes, the header title updates, and new messages are routed to the chosen session.

### Creating a new session

Click the **New** button in the header (or press `Ctrl+Shift+J` / `⌘⇧J`). This reconnects the WebSocket, which triggers automatic session creation on the server side.

### Deleting a session

Each non-active session row has a **✕** button on the right side of the title. Click it once — the button turns red and the tooltip changes to "Click again to confirm". Click it a second time within 2 seconds to permanently delete the session from the server. The row is removed from the list immediately.

The currently active session cannot be deleted from the overlay. To delete it, click **New** to start a fresh session first, then delete the old one.

### Refreshing the list

Click the **↺** button in the Sessions overlay header to reload the list from the server. The list shows up to 20 recent sessions.

### Closing the overlay

Click **✕** in the overlay header. The message list for the current session becomes visible again.

---

## Skills Panel

Skills are named slash-command shortcuts registered with your JiuwenSwarm instance. Examples: `/commit` to generate a commit message, `/review` to code-review a file, `/init` to bootstrap a new project.

### Opening the Skills panel

Click **⚙ → Skills** in the header dropdown. The Skills overlay slides into the main panel area.

### What the list shows

Each skill item displays:
- **Skill name** — the human-readable name
- **Description** — a short explanation of what the skill does
- **Trigger** — the slash command used to invoke it (e.g. `/commit`)
- **ON / OFF toggle** — the current enabled state

### Toggling a skill

Click the **ON** or **OFF** button on a skill item. The button changes immediately (teal = ON, muted grey = OFF) and a `skills.toggle` request is sent to the server to persist the change.

### Refreshing and errors

Click **↺** to reload. If the server does not support the `skills.list` method an error message is shown with a Retry button — this is expected on older server versions.

---

## Connection Status Bar Widget

A coloured text widget in the IDE status bar (bottom of the window) shows the live WebSocket state:

| Widget text | Colour | Meaning |
|-------------|--------|---------|
| `⬤ JiuwenSwarm` | Teal | Connected and active |
| `◌ JiuwenSwarm` | Teal | Connecting — waiting for server handshake |
| `↻ JiuwenSwarm` | Yellow | Reconnecting — automatic exponential backoff: 1 s → 2 s → 4 s → 8 s → … → 30 s max |
| `○ JiuwenSwarm` | Grey | Disconnected — **click the widget to reconnect** |

Once tokens are used, the running total appears directly in the widget:

```
⬤ JiuwenSwarm · 42.3k
```

Hovering shows a detailed tooltip:

```
JiuwenSwarm: Connected — session a1b2c3d4 | 42.3k tokens used
```

The count accumulates across all turns in the current IDE session and resets on reconnect or new session.

---

## Theme

The chat panel follows the IDE theme automatically by default. Override it at any time:

| Option | Description |
|--------|-------------|
| **⚙ → ◐ Auto** | Matches the IDE's current Light or Dark theme (default) |
| **⚙ → 🌙 Dark** | Forces dark background regardless of IDE theme |
| **⚙ → ☀ Light** | Forces light background regardless of IDE theme |

The theme preference is stored in browser local storage and survives panel restarts.

---

## Debug Log

The debug log panel is hidden by default. Toggle it with **⚙ → Debug log**.

When enabled, a scrollable panel appears below the message list and records:

- Every WebSocket message received from the server (raw JSON, with timestamp)
- Every chat message sent (including context and media item counts)
- Session switches, reconnects, and connection status changes
- Action dispatches (list_sessions, list_skills, toggle_skill, etc.)

The panel keeps the most recent 500 lines. Toggle it off to hide it; the log clears on the next enable.

This is most useful when something is not working as expected and you need to see what messages are being exchanged with the server.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Panel shows a blank white or grey box | JCEF not enabled | Enable `ide.browser.jcef.enabled` in **Help → Find Action → Registry**, then restart the IDE |
| "Could not load JiuwenSwarm chat UI" error message | `chat.html` not bundled | Reinstall from the latest ZIP; rebuild from source if developing locally |
| Status bar shows `○` (disconnected) | Server not running or host/port wrong | Start JiuwenSwarm; verify **Settings → Tools → JiuwenSwarm**; click the status widget to reconnect |
| Messages send but no response streams in | Server unreachable after handshake | Enable the Debug log and look for error frames; check server logs |
| Image preview shows a broken icon | JCEF content-security-policy issue (pre-0.2.0 bug) | Update the plugin to the latest version |
| Alt+Enter does not show "Fix with JiuwenSwarm" | No error or warning highlight at cursor | Move the cursor onto a line with a red or yellow gutter marker |
| Diff window opens but file does not change | Close the diff window to apply | The change is applied when the diff window is closed, not immediately on open |
| Session list stays on "Loading…" | Server timeout (5 s) or method not supported | Click **↺ Retry**; check server logs for `session.list` errors |
| Skills list shows an error | Server does not support `skills.list` | Expected on older server versions; upgrade the server to enable the skills panel |
| IDE log filled with `[JiuwenSwarmDebug]` lines | Debug mode was left on | Open the panel and click **⚙ → Debug log** to toggle it off |
| Rewind bar does not appear after agent edits a file | Auto-apply may be off so edits were not applied, or the server did not send `chat.final` | Enable the Debug log to check for `SNAP →` lines; verify the turn completed normally |
| Rewind restores 0 files | Snapshots were cleared by a subsequent message before rewind was clicked | The rewind window only lasts for the most recently completed turn |
| Session ✕ button is not visible | The session is the currently active one | Active sessions cannot be deleted; switch to another session first |
| "Project structure" not appearing in context | No project root detected (`project.basePath` is null) | This can happen in the default project or when no project folder is open |

### Reading the IDE log

The plugin writes all warnings and errors to the standard IDE log:

- **Windows / Linux**: **Help → Show Log in Explorer**
- **macOS**: **Help → Show Log in Finder**

Search the log for `JiuwenSwarm` to filter plugin-related entries.

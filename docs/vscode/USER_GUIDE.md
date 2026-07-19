# JiuwenSwarm VS Code Extension — Usage Guide

This is the complete usage reference for the JiuwenSwarm VS Code extension. It covers every panel, setting, action, and workflow in detail. For installation instructions see the [README](README.md).

---

## Configuration

Open **Settings → Extensions → JiuwenSwarm**:

| Setting | Default | Description |
|---------|---------|-------------|
| `jiuwenswarm.host` | `localhost` | Hostname or IP of the JiuwenSwarm WebSocket server |
| `jiuwenswarm.port` | `19000` | Port the server is listening on — connects to `ws://host:port/ws` |
| `jiuwenswarm.channelId` | `ide` | Identifies this client in server logs and TraceHound traces |
| `jiuwenswarm.autoConnect` | `true` | Opens the WebSocket connection when VS Code starts |
| `jiuwenswarm.defaultMode` | `code.plan` | Default agent mode (code.plan / code.normal / code.team) for new sessions |
| `jiuwenswarm.loadHistoryOnSwitch` | `true` | Automatically fetches and displays past messages when you switch to an existing session |
| `jiuwenswarm.rewindEnabled` | `true` | Enables file snapshots after each agent turn and shows the rewind bar; disable to reduce memory usage |
| `jiuwenswarm.projectTree.enabled` | `true` | Appends a 2-level directory listing of the workspace root to every outgoing message |
| `jiuwenswarm.projectTree.maxFiles` | `200` | Maximum number of file entries included in the project tree (range: 10–2000) |

Settings are stored in VS Code's standard settings (user or workspace scope) and persist across restarts. Changes require a window reload to take effect — the extension prompts you when you save a change.

---

## Opening the Panel

Click the **JiuwenSwarm** icon in the left **Activity Bar**, or press **Ctrl+Shift+J** / **⌘⇧J**. The panel opens as a sidebar webview and can be moved to the right sidebar or any other view location via drag-and-drop.

On first connect a session is created automatically. The panel header shows the session title and the live connection state.

---

## The Chat Panel

The chat panel runs inside a VS Code webview (an embedded Chromium browser). Here is a complete tour of every element.

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
| **Plan & Explore** | `code.plan` | Explore files, design a plan, wait for your approval — no edits yet. Best for non-trivial tasks. |
| **Execute** | `code.normal` | Edit files, run commands, verify and deliver results directly. Good for quick changes and clear tasks. |
| **Team Coding** | `code.team` | Multi-agent collaboration: a leader assigns specialists in parallel. Best for large, decomposable tasks. |

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
| Active file path and language | `vscode.window.activeTextEditor` + `document.languageId` | `Active file: /src/api/handler.py  (Python)` |
| Cursor line number | `editor.selection.active.line` | `Cursor line: 87` |
| Selected code | `editor.document.getText(editor.selection)` | Fenced code block of the current selection |
| Editor diagnostics | `vscode.languages.getDiagnostics(doc.uri)` | Up to 10 current errors and warnings from the Problems panel |
| Other open tabs | `vscode.window.tabGroups.all` | Paths of up to 10 other files open in the editor |
| Project tree | `workspace.workspaceFolders` | 2-level directory listing of the workspace root; skips `node_modules`, `build`, `.git`, `target`, etc. |
| Git status | `git` subprocess | `Git: branch=feature/auth, 3 uncommitted changes` |

The block is assembled at send time. If there is nothing useful (no editor open, no git repo, no selection), the message is sent without a context block.

### Controlling what is injected

Open **Settings → Extensions → JiuwenSwarm** to adjust context injection:

| Setting | Effect |
|---------|--------|
| `jiuwenswarm.projectTree.enabled` | Toggle the directory listing on or off |
| `jiuwenswarm.projectTree.maxFiles` | Limit the number of entries listed (10–2000); useful for large mono-repos where the full listing would be too verbose |

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
  • Line 87: Variable 'result' is not used before return
  • Line 88: blocking_call is deprecated

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

The webview intercepts the rendered agent text and runs two regex passes before rendering:

1. Backtick-wrapped paths that contain a `/` or end with `:N` are wrapped in a clickable `<a>` element around the existing `<code>` span.
2. Bare `path/to/file:N` references outside backticks and HTML tags are wrapped in a clickable `<a>` element.

Clicking either type sends an `open_file` message from the webview to the extension host, which uses `vscode.workspace.openTextDocument` and `showTextDocument` to navigate to the file and line.

---

## Symbol Navigation

When the agent mentions a code symbol (class name, constant, enum value, or other identifier) in its response, the plugin turns it into a clickable purple link. Clicking it searches the workspace for the symbol definition and jumps to it.

### What gets linkified

| Pattern | Example | Behaviour |
|---------|---------|-----------|
| PascalCase identifier in backticks | `` `MyClass` `` | Searches workspace for `MyClass`; jumps to first definition |
| SCREAMING_SNAKE_CASE identifier | `` `MAX_SIZE` `` | Searches workspace for `MAX_SIZE`; jumps to first definition |

### What does NOT get linkified

- File paths (handled by file links above)
- Common keywords like `TODO`, `FIXME`, `HTTP`, `JSON`, `API`, etc.
- Identifiers shorter than 3 characters
- camelCase or snake_case that starts with a lowercase letter

### How it works

The markdown renderer checks remaining backtick content after file-link extraction. If it looks like an uppercase identifier (≥3 chars, not in the keyword blocklist), it is wrapped in a `<a class="symbol-link">` element. Clicking sends a `navigate_symbol` message to the extension, which calls VS Code's `vscode.executeWorkspaceSymbolProvider` to find the definition and open it.

---

## Actions & Keyboard Shortcuts

### Keyboard shortcuts

| Action | Win / Linux | Mac | Notes |
|--------|-------------|-----|-------|
| **Open chat panel** | `Ctrl+Shift+J` | `⌘⇧J` | Always available |
| **New session** | `Ctrl+Shift+J` | `⌘⇧J` | Same shortcut; opens panel then starts fresh session if already open |
| **Send selection** | `Ctrl+Shift+E` | `⌘⇧E` | Available when text is selected |

**Open chat panel** (`Ctrl+Shift+J` / `⌘⇧J`):
Opens the JiuwenSwarm sidebar webview. If the panel is already open, it focuses the input field.

**New session** (command palette: `JiuwenSwarm: New Session`):
Disconnects and reconnects the WebSocket to start a fresh session. The message list clears and the previous history is left on the server. The panel is focused automatically.

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

---

## Code Action Quick Fix

VS Code shows a lightbulb 💡 next to any line that has an error or warning. JiuwenSwarm registers a **"Fix with JiuwenSwarm"** quick-fix action that appears in this menu.

### How to use it

1. Write or open code that has a compiler or linter error (red squiggly underline).
2. Place the cursor **on or inside** the highlighted error.
3. Click the 💡 lightbulb that appears in the left margin (or press `Ctrl+.` / `⌘.`).
4. Select **"Fix with JiuwenSwarm"** from the menu.
5. The chat panel opens and the input is pre-filled with:
   - The exact error message
   - ±7 lines of surrounding code context

6. Press **Enter** to send. The agent analyses the error and proposes a fix.

### What is included in the prefill

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

### Scope

- Works for any language that VS Code has diagnostics for (TypeScript, Python, Java, Go, Rust, C#, etc.)
- If multiple diagnostics exist on the same line, the first one (usually the most severe) is used.
- No text selection is required — just place the cursor on the error.

---

## File Edit Workflow

When the agent calls a file-editing tool (`str_replace_editor`, `write_file`, or `create_file`), the extension intercepts the call and handles it.

### Supported tools

| Tool | Operation |
|------|-----------|
| `str_replace_editor` command=`str_replace` | Replaces a specific block of text in an existing file |
| `str_replace_editor` command=`create` | Creates a new file with given content |
| `write_file` | Overwrites an existing file or creates it if missing |
| `create_file` | Creates a new file; parent directories are created automatically |

### VS Code behaviour (default)

By default, file edits are applied directly to the workspace via Node.js `fs` operations:

- File edit tool calls are logged to the Debug log panel with the tool name and parameters.
- Changes are written to disk immediately. VS Code's built-in **Source Control** panel or file explorer shows the modifications.
- Each applied edit triggers a notification toast confirming the change.

### Diff viewer (optional)

Enable **Use diff viewer** in **Settings → Extensions → JiuwenSwarm** to review every proposed file edit before it is applied.

When enabled:

1. A side-by-side diff editor opens showing **Current** (left) vs **Proposed** (right).
2. A notification appears with **Accept** and **Reject** buttons.
3. If you click **Accept**, the change is written to disk.
4. If you click **Reject**, the change is discarded and a toast confirms the rejection.
5. The diff editor closes automatically after you choose.

> **Tip:** Combine the diff viewer with **Approve edits** for maximum control — you'll see the diff first and then get an explicit accept/reject prompt.

### Approval workflow

Enable **Approve edits** in **Settings → Extensions → JiuwenSwarm** to require your confirmation before every file change. When enabled, a prompt appears with **Approve** and **Reject** buttons for each proposed edit.

### Terminal integration

When the agent runs a shell command (`bash` or `run_command`), the command is automatically sent to an IDE terminal named **"JiuwenSwarm"** so you can see live output, scroll back, and copy text.

- The terminal is created on the first command and reused for all subsequent ones.
- If you close the terminal, a new one is created on the next command.
- Disable this in **Settings → Extensions → JiuwenSwarm → Run commands in terminal** if you prefer commands to run silently.

---

## Checkpoint / Rewind

After every agent turn that edits one or more files, a rewind bar appears at the bottom of the chat panel:

```
⟲ Agent edited files this turn    [⟲ Undo changes]
```

> **Note:** Rewind can be disabled via **Settings → Extensions → JiuwenSwarm → Enable checkpoint / rewind** (`jiuwenswarm.rewindEnabled`). When disabled, no file snapshots are taken and the rewind bar never appears. This is useful if you want to reduce memory usage during long agent runs.

### How it works

Before the agent's first edit to a file in a given turn the extension snapshots the current file content. At the end of the turn (`chat.final`) the snapshots are locked in and the rewind bar becomes active.

### Using the rewind bar

Click **⟲ Undo changes**. The extension restores every snapshotted file to its pre-turn state. Files that did not exist before the turn are deleted on rewind. Each restore triggers a notification toast.

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

#### Session history

When `jiuwenswarm.loadHistoryOnSwitch` is `true` (the default), the extension automatically fetches the session's past messages after switching. A **"Loading history…"** indicator appears in the message list while the history streams in over the WebSocket. Once loading is complete the indicator disappears and the full conversation is visible.

To disable this behaviour, set `jiuwenswarm.loadHistoryOnSwitch` to `false` in **Settings → Extensions → JiuwenSwarm**.

### Creating a new session

Click the **New** button in the header (or run `JiuwenSwarm: New Session` from the command palette, or press `Ctrl+Shift+J` / `⌘⇧J`). This reconnects the WebSocket, which triggers automatic session creation on the server side.

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

A coloured text widget in the VS Code status bar (bottom-right of the window) shows the live WebSocket state:

| Widget text | Colour | Meaning |
|-------------|--------|---------|
| `$(check) JiuwenSwarm` | Default | Connected and active |
| `$(loading~spin) JiuwenSwarm` | Default | Connecting — waiting for server handshake |
| `$(sync~spin) JiuwenSwarm` | Warning (yellow background) | Reconnecting — automatic exponential backoff: 1 s → 2 s → 4 s → 8 s → … → 30 s max |
| `$(circle-slash) JiuwenSwarm` | Error (red background) | Disconnected — **click the widget to reconnect** |

Once tokens are used, the running total appears directly in the widget:

```
$(check) JiuwenSwarm · 42.3k
```

Hovering shows a detailed tooltip:

```
JiuwenSwarm: Connected — session a1b2c3d4 | 42.3k tokens used
```

The count accumulates across all turns in the current VS Code session and resets on reconnect or new session.

---

## Theme

The chat panel follows the VS Code theme automatically by default. Override it at any time:

| Option | Description |
|--------|-------------|
| **⚙ → ◐ Auto** | Matches VS Code's current Light or Dark theme (default) |
| **⚙ → 🌙 Dark** | Forces dark background regardless of VS Code theme |
| **⚙ → ☀ Light** | Forces light background regardless of VS Code theme |

The theme preference is stored in browser local storage and survives panel restarts.

---

## Debug Log

The debug log panel is hidden by default. Toggle it with **⚙ → Debug log**.

When enabled, a scrollable panel appears below the message list and records:

- Every WebSocket message received from the server (raw JSON, with timestamp)
- Every chat message sent (including context and media item counts)
- Session switches, reconnects, and connection status changes
- Action dispatches (list_sessions, list_skills, toggle_skill, etc.)
- File edit tool calls (with tool name and parameters)

The panel keeps the most recent 500 lines. Toggle it off to hide it; the log clears on the next enable.

This is most useful when something is not working as expected and you need to see what messages are being exchanged with the server.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Panel shows a blank white or grey box | `chat.html` not found or CSP issue | Check that `resources/chat.html` exists in the extension folder; reinstall from the latest VSIX if missing |
| "Could not load JiuwenSwarm chat UI" error message | `chat.html` not bundled | Reinstall from the latest VSIX; rebuild from source if developing locally |
| Status bar shows `$(circle-slash)` (disconnected) | Server not running or host/port wrong | Start JiuwenSwarm; verify **Settings → Extensions → JiuwenSwarm**; click the status widget to reconnect |
| Messages send but no response streams in | Server unreachable after handshake | Enable the Debug log and look for error frames; check server logs |
| Image preview shows a broken icon | Webview CSP or base64 encoding issue | Update the extension to the latest version |
| Send Selection does nothing | No text selected or editor not focused | Make sure text is actually selected in the editor; check that the editor has focus before pressing the shortcut |
| Rewind bar does not appear after agent edits a file | `jiuwenswarm.rewindEnabled` is `false`, approval rejected the edit, or the server did not send `chat.final` | Check **Enable checkpoint / rewind** in settings; enable the Debug log to check for `SNAP →` lines; verify the turn completed normally |
| Rewind restores 0 files | Snapshots were cleared by a subsequent message before rewind was clicked | The rewind window only lasts for the most recently completed turn; click Undo immediately after the turn ends |
| Session history does not appear after switching sessions | `jiuwenswarm.loadHistoryOnSwitch` is `false`, or the server does not support `history.get` | Verify the setting is `true` in **Settings → Extensions → JiuwenSwarm**; enable the Debug log and check for `history.get` errors; upgrade the server if needed |
| "Loading history…" indicator never disappears | Server began streaming history but did not send `history.done` | Reconnect via the status bar widget; check server logs for the affected session |
| Session list stays on "Loading…" | Server timeout (15 s) or method not supported | Click **↺ Retry**; check server logs for `session.list` errors |
| Skills list shows an error | Server does not support `skills.list` | Expected on older server versions; upgrade the server to enable the skills panel |
| IDE log filled with `[JiuwenSwarm]` lines | Debug mode was left on | Open the panel and click **⚙ → Debug log** to toggle it off |
| Settings change did not take effect | VS Code requires a window reload | The extension prompts you to reload when settings change; click **Reload** |
| "Project structure" not appearing in context | No workspace folder open | Open a folder in VS Code; context collection requires an active workspace |

### Reading the extension logs

The extension writes debug output to the VS Code **Output** panel:

1. Open **View → Output** (or press `Ctrl+Shift+U` / `⌘⇧U`).
2. Select **JiuwenSwarm** from the dropdown in the top-right of the Output panel.
3. Look for `[JiuwenSwarm]` prefixed lines.

For webview-level debugging (JavaScript errors in the chat UI):

1. Run **Developer: Open Webview Developer Tools** from the command palette (`Ctrl+Shift+P` / `⌘⇧P`).
2. Check the **Console** tab for JavaScript errors.

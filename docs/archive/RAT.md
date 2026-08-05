# Requirements Analysis — JiuwenSwarm IDE Plugins

---

## Source of Demand

- **Proactive Planning** — New Features / Developer Experience
- **Product Requirements** — JiuwenSwarm Platform / IDE Reach & Integration

---

## Demand Background

### WHY

JiuwenSwarm provides a powerful AI agent platform for coding assistance, workflow automation,
and interactive agent sessions. All interaction today requires opening a browser tab,
navigating to the JiuwenSwarm web UI, and switching away from the editor. This friction
breaks the development flow every time a developer wants help: they must leave their
IDE, context-switch, then come back.

Developers live in their IDE. The tools, keybindings, file tree, diagnostics, git status,
and active code are all there. Any AI assistant that cannot see this context — or that
requires the developer to copy-paste code into a browser — is operating at a disadvantage
relative to what it could provide with full IDE access.

Two problems need solving:

**1. Access friction** — Developers must switch to a browser to interact with JiuwenSwarm.
There is no way to ask the agent about the currently open file, selected code, compiler
errors, or git state without manually copying that information.

**2. Context gap** — When a developer does switch to the browser, the agent has no
knowledge of what the IDE knows: which file is open, what line the cursor is on, what
errors the compiler is reporting, what the project structure looks like. The developer
must supply all of this manually.

The goal is to eliminate both problems: put JiuwenSwarm inside the IDE the developer
already uses, and give the agent automatic access to full workspace context without any
manual copy-paste.

### WHEN

New feature, delivered as two independent packages targeting the two dominant IDE families:
- **JetBrains plugin** — IntelliJ IDEA, PyCharm, GoLand, WebStorm, Rider, and all other
  IntelliJ-platform IDEs (2023.1+)
- **VS Code extension** — VS Code and any VS Code-compatible editor (Cursor, Windsurf,
  Codium, etc.)

### WHAT

Each IDE package provides three layers:

---

**Layer 1 — Streaming chat panel**

A full-featured chat UI embedded as an IDE tool window. The developer can send messages,
receive streaming responses, and interact with the agent without leaving the IDE.

| Capability | Description |
|---|---|
| Streaming markdown rendering | Agent responses render incrementally as tokens arrive; code blocks syntax-highlighted |
| Thinking / reasoning blocks | Collapsible blocks for model chain-of-thought; collapsed by default |
| Tool call cards | Each tool call shows name, collapsible input/output, execution status |
| Stop / interrupt streaming | Cancel mid-generation; agent stream terminates cleanly |
| `@` file mention picker | Type `@` to autocomplete any workspace file; file contents injected into context |
| `#` skill picker | Type `#` to pick and activate a named skill for the turn |
| `!` preset prompt templates | Type `!` to select from 8 built-in prompt shortcuts |
| Mode selector | Plan & Execute / Execute / Team Coding; default mode applied from settings on connect |
| Model selector | Dropdown showing live model list from server; model switchable per turn |
| Image attachments | Attach PNG, JPEG, WebP, GIF inline in the message |
| Session management | Create, switch, resume, delete sessions; full history loaded on switch |
| Skills overlay | Browse available skills with ON/OFF toggle |
| Checkpoint / rewind | Undo all file edits made by the agent in the last turn |
| Human-turn clarifying questions | Agent can ask clarifying questions; answer workflow renders inline |
| Dark / light / auto theme | Follows IDE theme or set explicitly |
| Debug log panel | Live WebSocket/SSE event log for diagnostics |
| Context compaction indicator | Progress bar when context window is being compacted |

---

**Layer 2 — Context injection**

Before every message, the plugin automatically collects and attaches workspace context
so the agent always knows what the developer is looking at.

| Context field | Description |
|---|---|
| Active file path + language | The currently open file and its detected language |
| Cursor line | Line number where the cursor is positioned |
| Selected code | Any code selection in the active editor |
| Editor diagnostics | Up to 10 compiler/linter errors and warnings from the open file |
| Other open tabs | Up to 10 other open files (paths only) |
| Project tree | 2-level directory listing of the workspace root |
| Git branch + change count | Current branch name and number of uncommitted files |
| Project rules | Contents of `.jiuwenswarm/instructions.md`, `.jiuwenswarm/rules.md`, or `AGENTS.md` if present |
| @-mentioned file contents | Full contents of any file mentioned with `@` in the message |

---

**Layer 3 — IDE integration**

Deep integration with each IDE's native extension points.

| Feature | Description |
|---|---|
| Connection status bar widget | Live indicator showing connected / disconnected; click to reconnect |
| Token count in status bar | Current session token consumption |
| Send Selection (⌘⇧E / Ctrl+Shift+E) | Send the selected editor text to the chat panel |
| Right-click → Send Selection | Context menu action on any editor selection |
| New Session shortcut (⌘⇧J / Ctrl+Shift+J) | Start a new session from anywhere |
| Fix with JiuwenSwarm (Alt+Enter / lightbulb) | Quick-fix action on any diagnostic to ask the agent to fix it |
| File edit diff window | Preview agent-proposed file edits before applying (JetBrains full diff dialog) |
| Auto-apply file edits | Skip the diff dialog and apply immediately; undoable with Ctrl+Z |
| Approval prompt before file edits | Optional confirmation prompt before any agent edit is applied |
| Agent shell commands in IDE terminal | Agent-requested commands run in a dedicated IDE terminal tab |
| E2A streaming protocol | Plugin speaks the E2A envelope format (`e2a.chunk` / `e2a.complete` / `e2a.error`) |
| Exponential backoff reconnect | On disconnect, retries at 1 s → 2 s → 4 s … → 30 s intervals |
| Session restore on reconnect | Active session and conversation state automatically re-attached on reconnect |
| Keep-alive ping frames | Configurable heartbeat to prevent gateway timeout on idle connections |
| Per-project settings | Settings stored per IDE installation; project-specific overrides |

---

**Session statistics (status bar)**

| Chip | Content |
|---|---|
| Turns / Errors | Turn counter and error count for the session |
| Tokens | Total input + output tokens consumed |
| LLM calls | Number of individual model invocations |
| Avg latency / TTFT | Mean end-to-end latency and time-to-first-token across turns |
| Cost | Estimated USD cost for the session |
| Mini bar charts | Per-turn token counts and durations as a sparkline bar chart |
| Context bar | Model context window occupancy (orange > 60 %, red > 80 %) |
| Server memory | Live RSS / available memory on the JiuwenSwarm gateway host |
| Git status chip | Current branch and changed file count |
| Git quick actions | Commit and Push buttons in the status bar (optional; disabled by default) |

---

### Requirement Type

☑ **Functionality** (excluding Trust)
☑ **Operation and Maintenance Methods** (multi-IDE deployment and settings)

---

## Needs Assessment

### Requirement Decomposition

The feature is split into three independently buildable packages:

| Package | Language | Build artefact | Distribution |
|---|---|---|---|
| `packages/shared-webview` | HTML + JavaScript | `chat.html` (copied into each IDE package) | Bundled; not distributed separately |
| `packages/jetbrains-plugin` | Kotlin + Gradle | `jiuwenswarm-plugin-*.zip` | JetBrains Plugin Marketplace |
| `packages/vscode-extension` | TypeScript + webpack | `jiuwenswarm-*.vsix` | VS Code Marketplace + Open VSX |

### Constraints

**Shared webview — single source of truth:**
`packages/shared-webview/chat.html` is the canonical chat UI. Both the JetBrains plugin
and the VS Code extension load this file directly from their resource directories. Changes
to the UI must be synced to both IDE packages before building. There is no build step
that does this automatically; it is a manual copy step (`cp`).

**JCEF `onclick` vs `onmousedown`:**
The JetBrains plugin renders the webview via JCEF (Java Chromium Embedded Framework).
In JCEF, `onclick` handlers on popup list items fire *after* the textarea loses focus,
causing `selectionStart` to reset to 0. All popup item handlers (`@`, `#`, `!`) must use
`onmousedown` + `e.preventDefault()` to prevent focus loss before the handler fires.

**File edit diff — JetBrains only:**
The full side-by-side diff dialog before applying agent edits is implemented in JetBrains
only. The VS Code extension applies edits directly (with an optional approval prompt) but
does not open a diff window.

**One active agent task at a time:**
Both plugins support a single concurrent agent task per session. Multiple simultaneous
tasks, background task queuing, and badge counters are not implemented.

**Checkpoint stores last turn only:**
The rewind / checkpoint feature stores snapshots of files modified in the most recent
agent turn only. Rolling multi-turn snapshot history is not implemented.

**`navigateToSymbol` uses plain-text search:**
Clickable symbol links in agent responses (PascalCase / SCREAMING_SNAKE names) navigate
to the symbol by searching for the identifier as a plain string. True LSP go-to-definition
is not used.

**Semantic / codebase search not available:**
The agent does not automatically search the codebase for relevant files before composing
a response. Only files explicitly `@`-mentioned or already in the IDE context fields are
included.

### Impact on Requirement Implementation on Existing Systems

**JiuwenSwarm gateway:** No changes required. The plugin connects to the existing
WebSocket / SSE streaming endpoint using existing E2A protocol envelopes and Bearer token
authentication.

**Existing web UI users:** No impact. The IDE plugin is additive. Users who continue
using the browser are unaffected.

**New inbound traffic:** Each connected IDE instance opens a persistent WebSocket
connection to the gateway. Context injection adds ~1–5 KB of JSON to each outbound
message. Volume scales linearly with the number of active IDE connections.

**Project files:** The plugin may read project files for `@`-mentions and project rules.
It never writes to project files except when applying agent-proposed edits, which always
requires explicit approval (either via the diff dialog or approval prompt).

### External Dependencies

| Dependency | Used by | Purpose |
|---|---|---|
| JetBrains IntelliJ Platform SDK | JetBrains plugin | Tool window, editor APIs, JCEF, status bar widget, terminal |
| Gradle + `org.jetbrains.intellij.platform` Gradle plugin | JetBrains plugin | Build, verification, signing, publishing |
| JDK 17 | JetBrains plugin | Build requirement |
| JetBrains Plugin Marketplace | JetBrains plugin | Distribution |
| VS Code Extension API (`vscode`) | VS Code extension | Webview, status bar, editor context, commands, terminal |
| Node.js + webpack | VS Code extension | Build toolchain |
| `vsce` CLI | VS Code extension | Packaging and publishing |
| VS Code Marketplace | VS Code extension | Primary distribution |
| Open VSX Registry | VS Code extension | Secondary distribution (open-source IDEs) |
| JiuwenSwarm gateway | Both | WebSocket/SSE connection, E2A streaming, session and model APIs |

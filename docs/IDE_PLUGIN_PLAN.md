# JiuwenSwarm IDE Plugins — Development Plan

Development status and roadmap for the JetBrains plugin and VS Code extension. For architecture and technical details see [ARCHITECTURE.md](ARCHITECTURE.md).

Legend: ✅ done · 🔶 partial · ❌ not started · 🚫 platform limitation

---

## Feature Status

### Core (v1)

| Feature | Description | JetBrains | VS Code |
|---------|-------------|-----------|---------|
| Chat panel with streaming markdown rendering | Real-time conversational UI that renders agent responses word-by-word with full markdown support | ✅ | ✅ |
| Session list (create / switch / delete) | Browse, switch between, create new, and delete named conversation sessions | ✅ | ✅ |
| Automatic context injection (active file, selection, language) | Every message automatically includes the active file path, cursor position, selected code, and language | ✅ | ✅ |
| Tool call cards (file read/write, bash, etc.) | Inline collapsible cards showing every tool the agent invokes, with live status and parameters | ✅ | ✅ (via shared webview) |
| Inline diff for file edits (accept / reject) | Side-by-side diff window reviewing proposed file changes before applying | ✅ | 🚫 |
| Connection status indicator (connected / reconnecting / disconnected) | Visual indicator in the IDE showing WebSocket state | ✅ | ✅ |
| Token usage display | Running total of input and output tokens shown in the IDE status bar | ✅ (status bar) | ✅ (status bar) |
| E2A streaming format support (`e2a.chunk` / `e2a.complete` / `e2a.error`) | Handles the newer event-to-agent streaming protocol | ✅ | ✅ |
| Image/media attachments (base64 in `media_items`) | Attach images (PNG, JPEG, WebP, GIF) via the chat input, base64-encoded | ✅ | ✅ |
| Debug logging toggle from webview | Toggle verbose WebSocket message logging directly from the chat panel settings menu | ✅ | ✅ |
| Click-to-reconnect when disconnected | Click the status indicator to trigger an immediate reconnection attempt | ✅ | ✅ |
| Approval workflow before applying file edits | Require explicit user confirmation before the agent applies any file edit | ✅ | ✅ |
| Notification toast on applied edit | Brief popup confirming each successfully applied file change | ✅ | ✅ |

### Enhanced (v2)

| Feature | Description | JetBrains | VS Code |
|---------|-------------|-----------|---------|
| Diagnostics context: IDE errors/warnings injected into every message | Include up to 10 current compiler/linter errors and warnings in every message context | ✅ | ✅ |
| Git context: current branch + uncommitted change count | Include current git branch name and number of uncommitted changes | ✅ | ✅ |
| Multi-file context: all open tabs sent with each message | Include paths of up to 10 other open editor tabs for broader project awareness | ✅ | ✅ |
| Project tree context: 2-level directory listing of workspace | Include a 2-level directory tree of the project root to orient the agent | ✅ | ✅ |
| "Fix with JiuwenSwarm" quick action on errors (Alt+Enter) | Alt+Enter intention action that prefills the chat with the error text and surrounding code | ✅ | 🚫 |
| Right-click → "Send Selection to JiuwenSwarm" | Context-menu item that sends selected code to the chat panel | ✅ | ✅ |
| Inline ghost text suggestions (like Copilot) | Predictive inline code completions while typing | ❌ | ❌ |
| Skills panel: browse / toggle skills from within IDE | View and enable/disable registered slash-command skills from the chat panel | ✅ | ✅ |
| Clickable file links: agent-mentioned paths open file at line in editor | File paths in agent responses become clickable hyperlinks that open the file | ✅ | ✅ |
| Replay / TraceHound panel: session trajectory viewer inside IDE | Browse the full turn-by-turn history of a session with tool call details | ❌ | ❌ |
| E2A field resilience (checks top-level + payload for `response_kind`) | Gracefully handles E2A messages whether `response_kind` is at top-level or nested in `payload` | ✅ | ✅ |
| Checkpoint / rewind: undo all file changes from last turn | After the agent edits files, one-click restore to pre-turn state | ✅ | ✅ |

### Power features (v3)

| Feature | Description | JetBrains | VS Code |
|---------|-------------|-----------|---------|
| Terminal integration: agent runs commands in IDE terminal | Execute shell commands in an IDE-integrated terminal so output is visible | ❌ | ❌ |
| Symbol navigation: agent references symbols for jump-to-definition | Agent mentions symbols that become clickable for Go to Definition | ❌ | ❌ |
| Pair programming mode: agent narrates thought process in real time | Continuous real-time commentary on the agent's reasoning and actions | ❌ | ❌ |

---

## Implementation Phases

### Phase 1 — Working Prototype ✅

| # | Feature | Description | JetBrains | VS Code |
|---|---------|-------------|-----------|---------|
| 1 | WebSocket client connecting to `ws://localhost:19000/ws` | Establish and maintain a WebSocket connection with automatic reconnect | ✅ | ✅ |
| 2 | Session creation on startup (auto from `connection.ack`) | Automatically create a new session when the server acknowledges connection | ✅ | ✅ |
| 3 | Basic chat panel (text in, streaming text out) | Send messages and receive token-by-token streaming responses | ✅ | ✅ |
| 4 | Tool call cards (read-only display) | Show tool invocations as inline status cards without applying edits | ✅ | ✅ |
| 5 | Connection status indicator | Show coloured dot / text in the UI indicating connection health | ✅ | ✅ |
| 6 | VS Code: Webview panel · JetBrains: JCEF panel | Host the chat UI in VS Code webview or JetBrains JCEF browser | ✅ | ✅ |
| 7 | Status bar with token count | Display cumulative token usage alongside connection state | ✅ | ✅ |
| 8 | Image attachments via `media_items` | Send images as base64 media items in chat messages | ✅ | ✅ |

**Deliverable**: Developer can type a question, get a streaming answer from JiuwenSwarm, see what tools ran, and see token usage.

---

### Phase 2 — Context & Edits ✅

| # | Feature | Description | JetBrains | VS Code |
|---|---------|-------------|-----------|---------|
| 1 | Context injection (active file, selection, diagnostics) | Gather and prepend IDE context to every outgoing message | ✅ | ✅ |
| 2 | File edit interception from `chat.tool_call` events | Detect and handle file-editing tool calls before they reach the agent | ✅ | ✅ |
| 3 | Diff viewer (propose → accept / reject) | Open a side-by-side diff for proposed file changes with accept/reject controls | ✅ | 🚫 |
| 4 | Session management (list, create, switch, delete) | Full CRUD for sessions with an in-panel overlay UI | ✅ | ✅ |
| 5 | Settings panel (host, port, mode, auto-apply, approval) | Configure plugin settings in the IDE preferences | ✅ | ✅ |
| 6 | Git context (branch, uncommitted changes) | Include git branch and change count in message context | ✅ | ✅ |
| 7 | Other open files context | Include other open editor tabs in message context | ✅ | ✅ |
| 8 | Project tree context (2-level directory listing) | Include project directory tree in message context | ✅ | ✅ |

**Deliverable**: Developer can ask "fix this function" with selection, get edits applied (with optional diff or approval), and the agent knows the git state, open files, and project structure.

---

### Phase 3 — Deep IDE Integration ✅

| # | Feature | Description | JetBrains | VS Code |
|---|---------|-------------|-----------|---------|
| 1 | Skills browser panel (`skills.list` / `skills.toggle`) | View available skills and toggle them on/off from the chat panel | ✅ | ✅ |
| 2 | Token usage in status bar | Show running token count in the IDE status bar | ✅ | ✅ |
| 3 | Right-click context menu → "Send Selection to JiuwenSwarm" | Editor context-menu shortcut for sending selected code | ✅ | ✅ |
| 4 | E2A streaming format support (gateway v2 protocol) | Support the newer event-to-agent streaming format | ✅ | ✅ |
| 5 | "Fix with JiuwenSwarm" quick action on errors (Alt+Enter) | JetBrains intention action triggered via Alt+Enter | ✅ | 🚫 |
| 6 | Multi-file context (all open tabs) | Send all open editor tabs as context | ✅ | ✅ |
| 7 | Project tree context | Send project directory tree as context | ✅ | ✅ |
| 8 | Session delete from sessions overlay | Delete named sessions from the sessions list UI | ✅ | ✅ |
| 9 | Checkpoint / rewind: snapshot files before agent edits; one-click restore | Save file snapshots before edits and allow restoring them | ✅ | ✅ |
| 10 | Debug logging toggle from webview | Turn on/off verbose WebSocket debug logging from the chat panel | ✅ | ✅ |
| 11 | Clickable file links in agent responses | Make file paths mentioned by the agent clickable | ✅ | ✅ |
| 12 | Approval workflow for file edits | Require user approval before applying file edits | ✅ | ✅ |
| 13 | Notification toast on applied edits | Show confirmation toast when edits are applied | ✅ | ✅ |

**Deliverable**: First-class AI assistant experience on par with Copilot Chat / Cursor.

---

### Phase 4 — Advanced ❌

| # | Feature | Description | JetBrains | VS Code |
|---|---------|-------------|-----------|---------|
| 1 | Inline ghost text completions | Real-time inline code suggestions while typing | ❌ | ❌ |
| 2 | Terminal integration (agent runs commands in IDE terminal) | Run shell commands in an IDE terminal panel | ❌ | ❌ |
| 3 | Replay / TraceHound viewer inside IDE | Display full session history and reasoning traces | ❌ | ❌ |
| 4 | Symbol navigation (agent references symbols for jump-to-definition) | Click agent-mentioned symbols to jump to definition | ❌ | ❌ |
| 5 | Shared webview code published as npm package | Extract chat.html as a reusable npm module | ❌ | ❌ |
| 6 | VS Code: native diff dialog for file edits | Use VS Code's built-in diff/compare functionality | 🚫 | 🚫 |
| 7 | VS Code: Code Action quick-fix on diagnostics | Register a Code Action provider for diagnostic quick-fixes | 🚫 | 🚫 |

---

## Build & Distribution

### VS Code Extension
- **Packaging**: `vsce package` → `.vsix`
- **Install locally**: `code --install-extension jiuwenswarm-*.vsix`
- **Registry**: [VS Code Marketplace](https://marketplace.visualstudio.com/)
- **CI**: `vsce publish` on tag push

### JetBrains Plugin
- **Packaging**: `./gradlew buildPlugin` → `build/distributions/jiuwenswarm-*.zip`
- **Install locally**: Settings → Plugins → Install from disk
- **Registry**: [JetBrains Marketplace](https://plugins.jetbrains.com/)
- **Compatibility**: `sinceBuild = "231"` (2023.1+)
- **CI**: `./gradlew publishPlugin` on tag push

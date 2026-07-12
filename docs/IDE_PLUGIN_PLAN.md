# JiuwenSwarm IDE Plugins — Development Plan

Development status and roadmap for the JetBrains plugin and VS Code extension. For architecture and technical details see [ARCHITECTURE.md](ARCHITECTURE.md).

Legend: ✅ done · 🔶 partial · ❌ not started · 🚫 platform limitation

---

## Feature Status

### Core (v1)

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Chat panel with streaming markdown rendering | ✅ | ✅ |
| Session list (create / switch / delete) | ✅ | ✅ |
| Automatic context injection (active file, selection, language) | ✅ | ✅ |
| Tool call cards (file read/write, bash, etc.) | ✅ | ✅ (via shared webview) |
| Inline diff for file edits (accept / reject) | ✅ | 🚫 |
| Connection status indicator (connected / reconnecting / disconnected) | ✅ | ✅ |
| Token usage display | ✅ (status bar) | ✅ (status bar) |
| E2A streaming format support (`e2a.chunk` / `e2a.complete` / `e2a.error`) | ✅ | ✅ |
| Image/media attachments (base64 in `media_items`) | ✅ | ✅ |
| Debug logging toggle from webview | ✅ | ✅ |
| Click-to-reconnect when disconnected | ✅ | ✅ |
| Approval workflow before applying file edits | ✅ | ✅ |
| Notification toast on applied edit | ✅ | ✅ |

### Enhanced (v2)

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Diagnostics context: IDE errors/warnings injected into every message | ✅ | ✅ |
| Git context: current branch + uncommitted change count | ✅ | ✅ |
| Multi-file context: all open tabs sent with each message | ✅ | ✅ |
| Project tree context: 2-level directory listing of workspace | ✅ | ✅ |
| "Fix with JiuwenSwarm" quick action on errors (Alt+Enter) | ✅ | 🚫 |
| Right-click → "Send Selection to JiuwenSwarm" | ✅ | ✅ |
| Inline ghost text suggestions (like Copilot) | ❌ | ❌ |
| Skills panel: browse / toggle skills from within IDE | ✅ | ✅ |
| Clickable file links: agent-mentioned paths open file at line in editor | ✅ | ✅ |
| Replay / TraceHound panel: session trajectory viewer inside IDE | ❌ | ❌ |
| E2A field resilience (checks top-level + payload for `response_kind`) | ✅ | ✅ |
| Checkpoint / rewind: undo all file changes from last turn | ✅ | ✅ |

### Power features (v3)

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Terminal integration: agent runs commands in IDE terminal | ❌ | ❌ |
| Symbol navigation: agent references symbols for jump-to-definition | ❌ | ❌ |
| Pair programming mode: agent narrates thought process in real time | ❌ | ❌ |

---

## Implementation Phases

### Phase 1 — Working Prototype ✅

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | WebSocket client connecting to `ws://localhost:19000/ws` | ✅ | ✅ |
| 2 | Session creation on startup (auto from `connection.ack`) | ✅ | ✅ |
| 3 | Basic chat panel (text in, streaming text out) | ✅ | ✅ |
| 4 | Tool call cards (read-only display) | ✅ | ✅ |
| 5 | Connection status indicator | ✅ | ✅ |
| 6 | VS Code: Webview panel · JetBrains: JCEF panel | ✅ | ✅ |
| 7 | Status bar with token count | ✅ | ✅ |
| 8 | Image attachments via `media_items` | ✅ | ✅ |

**Deliverable**: Developer can type a question, get a streaming answer from JiuwenSwarm, see what tools ran, and see token usage.

---

### Phase 2 — Context & Edits ✅

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | Context injection (active file, selection, diagnostics) | ✅ | ✅ |
| 2 | File edit interception from `chat.tool_call` events | ✅ | ✅ |
| 3 | Diff viewer (propose → accept / reject) | ✅ | 🚫 |
| 4 | Session management (list, create, switch, delete) | ✅ | ✅ |
| 5 | Settings panel (host, port, mode, auto-apply, approval) | ✅ | ✅ |
| 6 | Git context (branch, uncommitted changes) | ✅ | ✅ |
| 7 | Other open files context | ✅ | ✅ |
| 8 | Project tree context (2-level directory listing) | ✅ | ✅ |

**Deliverable**: Developer can ask "fix this function" with selection, get edits applied (with optional diff or approval), and the agent knows the git state, open files, and project structure.

---

### Phase 3 — Deep IDE Integration ✅

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | Skills browser panel (`skills.list` / `skills.toggle`) | ✅ | ✅ |
| 2 | Token usage in status bar | ✅ | ✅ |
| 3 | Right-click context menu → "Send Selection to JiuwenSwarm" | ✅ | ✅ |
| 4 | E2A streaming format support (gateway v2 protocol) | ✅ | ✅ |
| 5 | "Fix with JiuwenSwarm" quick action on errors (Alt+Enter) | ✅ | 🚫 |
| 6 | Multi-file context (all open tabs) | ✅ | ✅ |
| 7 | Project tree context | ✅ | ✅ |
| 8 | Session delete from sessions overlay | ✅ | ✅ |
| 9 | Checkpoint / rewind: snapshot files before agent edits; one-click restore | ✅ | ✅ |
| 10 | Debug logging toggle from webview | ✅ | ✅ |
| 11 | Clickable file links in agent responses | ✅ | ✅ |
| 12 | Approval workflow for file edits | ✅ | ✅ |
| 13 | Notification toast on applied edits | ✅ | ✅ |

**Deliverable**: First-class AI assistant experience on par with Copilot Chat / Cursor.

---

### Phase 4 — Advanced ❌

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | Inline ghost text completions | ❌ | ❌ |
| 2 | Terminal integration (agent runs commands in IDE terminal) | ❌ | ❌ |
| 3 | Replay / TraceHound viewer inside IDE | ❌ | ❌ |
| 4 | Symbol navigation (agent references symbols for jump-to-definition) | ❌ | ❌ |
| 5 | Shared webview code published as npm package | ❌ | ❌ |
| 6 | VS Code: native diff dialog for file edits | 🚫 | 🚫 |
| 7 | VS Code: Code Action quick-fix on diagnostics | 🚫 | 🚫 |

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

# JiuwenSwarm IDE Plugins — Development Plan

Development status and roadmap for the JetBrains plugin and VS Code extension. For architecture and technical details see [ARCHITECTURE.md](ARCHITECTURE.md).

Legend: ✅ done · 🔶 partial · ❌ not started

---

## Feature Status

### Core (v1)

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Chat panel with streaming markdown rendering | ✅ | ✅ |
| Session list (create / switch / delete) | ✅ | ✅ (no delete) |
| Automatic context injection (active file, selection, language) | ✅ | ✅ |
| Tool call cards (file read/write, bash, etc.) | ✅ | ✅ (via shared webview) |
| Inline diff for file edits (accept / reject) | ✅ | ❌ |
| Connection status indicator (connected / reconnecting) | ✅ | ✅ |
| Token usage display | ✅ (status bar tooltip) | ❌ |

### Enhanced (v2)

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Diagnostics context: IDE errors/warnings injected into every message | ✅ | ✅ |
| Git context: current branch + uncommitted change count | ✅ | ❌ |
| Multi-file context: all open tabs sent with each message | ✅ | ❌ |
| Project tree context: directory listing of workspace | ✅ | ❌ |
| "Fix with JiuwenSwarm" quick action on errors (Alt+Enter) | ✅ | ❌ |
| Right-click → "Send Selection to JiuwenSwarm" | ✅ | ✅ |
| Inline ghost text suggestions (like Copilot) | ❌ | ❌ |
| Skills panel: browse / toggle skills from within IDE | ✅ | ❌ |
| Replay / TraceHound panel: session trajectory viewer inside IDE | ❌ | ❌ |

### Power features (v3)

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Approval workflow: tool calls require user confirmation | ❌ | ❌ |
| Terminal integration: agent runs commands in IDE terminal | ❌ | ❌ |
| Symbol navigation: agent references symbols for jump-to-definition | ❌ | ❌ |
| Checkpoint / rewind: undo all file changes from last turn | ✅ | ❌ |
| Pair programming mode: agent narrates thought process in real time | ❌ | ❌ |

---

## Implementation Phases

### Phase 1 — Working Prototype ✅

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | WebSocket client connecting to `ws://localhost:19000/ws` | ✅ | ✅ |
| 2 | Session creation on startup | ✅ | ✅ |
| 3 | Basic chat panel (text in, streaming text out) | ✅ | ✅ |
| 4 | Tool call cards (read-only display) | ✅ | ✅ |
| 5 | Connection status indicator | ✅ | ✅ |
| 6 | VS Code: Webview panel · JetBrains: JCEF panel | ✅ | ✅ |

**Deliverable**: Developer can type a question, get a streaming answer from jiuwenswarm, see what tools ran.

---

### Phase 2 — Context & Edits 🔶

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | Context injection (active file, selection, diagnostics) | ✅ | ✅ |
| 2 | File edit interception from `chat.tool_call` events | ✅ | ❌ |
| 3 | Diff viewer (propose → accept / reject) | ✅ | ❌ |
| 4 | Session management (list, create, switch) | ✅ | ✅ |
| 5 | Settings panel (host, port, mode, auto-apply) | ✅ | ✅ (no auto-apply) |

**Deliverable**: Developer can ask "fix this function" with selection, get a diff, click Accept.

---

### Phase 3 — Deep IDE Integration 🔶

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | Git context (branch, uncommitted changes count) | ✅ | ❌ |
| 2 | "Fix with JiuwenSwarm" quick action on errors (Alt+Enter) | ✅ | ❌ |
| 3 | Right-click context menu → "Send Selection to JiuwenSwarm" | ✅ | ✅ |
| 4 | Multi-file context (all open tabs) | ✅ | ❌ |
| 5 | Skills browser panel | ✅ | ❌ |
| 6 | Token usage in status bar | ✅ | ❌ |
| 7 | Project tree context (2-level directory listing injected per message) | ✅ | ❌ |
| 8 | Session delete from sessions overlay (two-click confirmation) | ✅ | ❌ |
| 9 | Checkpoint / rewind: snapshot files before agent edits; one-click restore | ✅ | ❌ |

**Deliverable**: First-class AI assistant experience on par with Copilot Chat / Cursor.

---

### Phase 4 — Advanced ❌

| # | Feature | JetBrains | VS Code |
|---|---------|-----------|---------|
| 1 | Inline ghost text completions | ❌ | ❌ |
| 2 | Approval workflow for tool calls | ❌ | ❌ |
| 3 | Replay / TraceHound viewer inside IDE | ❌ | ❌ |
| 4 | Terminal integration (agent runs commands in IDE terminal) | ❌ | ❌ |
| 5 | Shared webview code published as npm package | ❌ | ❌ |

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

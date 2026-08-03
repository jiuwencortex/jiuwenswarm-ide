# JiuwenSwarm IDE Plugins — Feature Status

Current feature inventory and roadmap for the JetBrains plugin and VS Code extension. For architecture see [ARCHITECTURE.md](ARCHITECTURE.md).

Legend: ✅ done · 🔶 partial · ❌ not started · 🚫 platform limitation

---

## Implemented Features

### Chat UI (shared webview)

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Streaming markdown rendering | ✅ | ✅ |
| Collapsible thinking / reasoning blocks | ✅ | ✅ |
| Tool call cards (inputs + outputs, collapsible) | ✅ | ✅ |
| Mode selector (Plan & Execute / Execute / Team Coding) | ✅ | ✅ |
| Default mode applied from settings on connect | ✅ | ✅ |
| Model selector dropdown | ✅ | ✅ |
| Image / media attachments (PNG, JPEG, WebP, GIF) | ✅ | ✅ |
| `@` file mention picker (workspace file autocomplete) | ✅ | ✅ |
| `#` skill picker (inline skill selection from input) | ✅ | ✅ |
| `!` preset prompt templates (8 built-in templates) | ✅ | ✅ |
| Clickable file links in agent responses | ✅ | ✅ |
| Symbol navigation links (PascalCase / SCREAMING_SNAKE) | ✅ | ✅ |
| Session overlay (list / switch / create / delete) | ✅ | ✅ |
| Session history loading after switch | ✅ | ✅ |
| Skills overlay (list + ON/OFF toggle) | ✅ | ✅ |
| Checkpoint / rewind bar (undo last-turn file edits) | ✅ | ✅ |
| Context bar (model context window occupancy) | ✅ | ✅ |
| Per-turn token counter | ✅ | ✅ |
| Session stats chips (turns, tokens, cost, tool calls, latency, TTFT) | ✅ | ✅ |
| Mini bar charts (tokens/turn + duration/turn) | ✅ | ✅ |
| Git status chip (branch + changed file count) | ✅ | ✅ |
| Git quick actions (Commit + Push, toggled in settings) | ✅ | ✅ |
| Server memory usage chip | ✅ | ✅ |
| Dark / light / auto theme | ✅ | ✅ |
| Debug log panel | ✅ | ✅ |
| Context compaction progress indicator | ✅ | ✅ |
| Human-turn clarifying question (answer workflow) | ✅ | ✅ |
| Stop / interrupt streaming | ✅ | ✅ |

### Context Injection

| Field | JetBrains | VS Code |
|-------|-----------|---------|
| Active file path + language | ✅ | ✅ |
| Cursor line | ✅ | ✅ |
| Selected code | ✅ | ✅ |
| Editor diagnostics (up to 10) | ✅ | ✅ |
| Other open tabs (up to 10) | ✅ | ✅ |
| Project tree (2-level directory listing) | ✅ | ✅ |
| Git branch + uncommitted change count | ✅ | ✅ |
| Project rules (`.jiuwenswarm/instructions.md` / `.jiuwenswarm/rules.md` / `AGENTS.md`) | ✅ | ✅ |
| @-mentioned file contents | ✅ | ✅ |

### IDE Integration

| Feature | JetBrains | VS Code |
|---------|-----------|---------|
| Connection status bar widget | ✅ | ✅ |
| Click widget to reconnect | ✅ | ✅ |
| Token count in status bar | ✅ | ✅ |
| Send Selection (Ctrl+Shift+E / ⌘⇧E) | ✅ | ✅ |
| Right-click → Send Selection to JiuwenSwarm | ✅ | ✅ |
| New Session shortcut (Ctrl+Shift+J / ⌘⇧J) | ✅ | ✅ |
| Fix with JiuwenSwarm (Alt+Enter / lightbulb) | ✅ | ✅ |
| File edit diff window | ✅ | 🚫 |
| Auto-apply file edits (skip diff, undoable) | ✅ | ✅ |
| Approval prompt before file edits | ✅ | ✅ |
| Agent shell commands in IDE terminal | ✅ | ✅ |
| E2A streaming format (e2a.chunk / e2a.complete / e2a.error) | ✅ | ✅ |
| Exponential backoff reconnect | ✅ | ✅ |
| Session restore on reconnect | ✅ | ✅ |
| Keep-alive ping frames | ✅ | ✅ |
| Per-project settings stored per-IDE-install | ✅ | ✅ |

---

## Not Yet Implemented

| Feature | Notes |
|---------|-------|
| Inline per-hunk accept/reject | Editor inline ghost text diff (Cursor-style). Current diff is a full popup window (JetBrains) or direct apply (VS Code). |
| Background / async task queue | Multiple simultaneous agent tasks; badge counter while tasks run. Currently one active task at a time. |
| Full checkpoint timeline | Rewind only stores the most recent turn's snapshots. A multi-turn history requires keeping a rolling snapshot buffer. |
| Test loop integration | Automatically run tests after agent edits and feed failures back to the agent. |
| LSP / symbol-level context | True go-to-definition / find-references for symbols mentioned in chat. `navigateToSymbol` currently uses plain-text word search. |
| Semantic / codebase search | Automatically find relevant files/symbols before sending a message (ripgrep / AST-based, no vector DB needed). |
| Project-scoped custom slash commands | Read `.jiuwenswarm/commands/*.md` as user-defined command templates. |
| Cost budget limits | Pause / warn when session cost exceeds a configured threshold. Token/cost tracking exists; enforcement does not. |
| Conversation export | Export session as Markdown or JSON. |
| OS notifications for long tasks | Desktop notification (JetBrains `Notifications.Bus` / VS Code `showInformationMessage`) when a task completes while the IDE is unfocused. |
| Voice input | Web Speech API microphone button in the chat textarea. |
| Inline ghost text completions | Sub-50 ms latency code completions while typing in the editor. |
| Conversation fork / branch | "Fork from this message" — create a new session that starts from a specific point in the current conversation history. |

---

## Build & Distribution

### JetBrains Plugin

```bash
# Build
cd packages/jetbrains-plugin
./gradlew buildPlugin
# → build/distributions/jiuwenswarm-plugin-0.1.0.zip

# Install locally
# Settings → Plugins → ⚙ → Install Plugin from Disk → select ZIP
```

- **Compatibility**: `sinceBuild = "231"` (2023.1+)
- **Marketplace**: [plugins.jetbrains.com](https://plugins.jetbrains.com)
- **CI**: `./gradlew signPlugin publishPlugin` on `jetbrains-vX.Y.Z` tag push

### VS Code Extension

```bash
# Build
cd packages/vscode-extension
npm install
npm run build
npx vsce package --no-dependencies
# → jiuwenswarm-0.1.0.vsix

# Install locally
code --install-extension jiuwenswarm-0.1.0.vsix
```

- **Marketplace**: [marketplace.visualstudio.com](https://marketplace.visualstudio.com)
- **OpenVSX**: [open-vsx.org](https://open-vsx.org)
- **CI**: `vsce publish` on `vscode-vX.Y.Z` tag push

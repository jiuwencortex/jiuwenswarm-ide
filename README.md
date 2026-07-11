# JiuwenSwarm IDE Plugins

AI coding assistant plugins for VS Code and JetBrains IDEs, backed by a locally running [JiuwenSwarm](../jiuwenswarm) instance.

JiuwenSwarm is an autonomous multi-agent AI engine that coordinates specialised agents to handle complex software engineering tasks end-to-end: reading and editing files, running shell commands, searching codebases, calling external tools, and reasoning across multiple steps — all without leaving your IDE.

## Prerequisites

JiuwenSwarm must be running before the plugin connects:

```bash
cd jiuwenswarm && jiuwenswarm-start
# WebSocket server starts at ws://localhost:19000/ws
```

## Packages

```
packages/
├── shared-webview/      # Self-contained chat UI (vanilla HTML/JS, no build step)
├── vscode-extension/    # VS Code extension (TypeScript + esbuild)
└── jetbrains-plugin/    # JetBrains plugin (Kotlin + Gradle)
```

---

## VS Code Extension

### Development

```bash
cd packages/vscode-extension
npm install
npm run build      # one-shot build → out/extension.js
npm run watch      # watch mode
```

Then in VS Code:
1. Open `packages/vscode-extension` as the workspace
2. Press **F5** to open an Extension Development Host window
3. Open the JiuwenSwarm panel in the Activity Bar (`Ctrl+Shift+J` / `Cmd+Shift+J`)

### Packaging

```bash
npm install -g @vscode/vsce
vsce package                            # → jiuwenswarm-0.1.0.vsix
code --install-extension jiuwenswarm-0.1.0.vsix
```

### Configuration

`Settings → Extensions → JiuwenSwarm`:

| Setting | Default | Description |
|---------|---------|-------------|
| `jiuwenswarm.host` | `localhost` | JiuwenSwarm server hostname |
| `jiuwenswarm.port` | `19000` | WebSocket port |
| `jiuwenswarm.defaultMode` | `code.normal` | Default agent mode |
| `jiuwenswarm.channelId` | `ide` | Channel ID sent to the server |
| `jiuwenswarm.autoConnect` | `true` | Connect on startup |

### Keybindings

| Key (Win/Linux) | Key (Mac) | Command |
|-----------------|-----------|---------|
| `Ctrl+Shift+J` | `Cmd+Shift+J` | Open chat panel |
| `Ctrl+Shift+E` | `Cmd+Shift+E` | Send selection to chat |

---

## JetBrains Plugin

Works with **PyCharm**, **IntelliJ IDEA**, **WebStorm**, and all JetBrains IDEs (2023.1+).

### Development

```bash
cd packages/jetbrains-plugin
./gradlew runIde      # launch a sandboxed IDE instance with the plugin pre-installed
./gradlew buildPlugin # → build/distributions/jiuwenswarm-plugin-0.1.0.zip
```

### Installation

**From build output:**
1. `./gradlew buildPlugin`
2. **Settings → Plugins → ⚙ → Install Plugin from Disk** → select `build/distributions/jiuwenswarm-plugin-0.1.0.zip`
3. Restart the IDE

**From JetBrains Marketplace:** search "JiuwenSwarm" in **Settings → Plugins → Marketplace**.

> JCEF (Chromium Embedded Framework) must be enabled. If you see a message about it, go to
> **Help → Find Action → Registry** → enable `ide.browser.jcef.enabled` → restart.

### Configuration

**Settings → Tools → JiuwenSwarm:**

| Setting | Default | Description |
|---------|---------|-------------|
| Server host | `localhost` | JiuwenSwarm server hostname |
| Server port | `19000` | WebSocket port |
| Channel ID | `ide` | Channel ID sent to the server |
| Connect automatically on IDE startup | on | Open the connection when the IDE starts |
| Auto-apply file edits (skip diff dialog) | off | Apply agent file edits immediately without showing a diff |

### Keybindings

| Key (Win/Linux) | Key (Mac) | Command |
|-----------------|-----------|---------|
| `Ctrl+Shift+J` | `Cmd+Shift+J` | New session |
| `Ctrl+Shift+E` | `Cmd+Shift+E` | Send selection to chat |

---

## Features

### Chat panel

The chat panel is embedded directly in the IDE sidebar using JCEF (Chromium Embedded Framework). Responses stream token-by-token so you see output in real time. The panel supports:

- Markdown rendering with syntax-highlighted code blocks
- Collapsible tool call cards showing every agent action (file read/write, bash commands, web search, MCP tools) with live status, inputs, and outputs
- Reasoning blocks (when the model exposes its thinking)
- Dark and light themes with a persistent toggle (`◑` button in the header)
- Agent mode selector: `code·normal`, `code·plan`, `agent·plan`, `agent·fast`

### Session management

Sessions preserve their full conversation history and agent context so you can pick up exactly where you left off. Use the session dropdown in the panel header to list, create, or switch sessions. A fresh session is created automatically on the first connection.

### IDE context injection

Every message sent from the plugin automatically includes a structured context block with:

- The active file path and language
- The cursor line number
- Any selected code (as a fenced code block)
- Editor diagnostics (warnings and errors visible in the error stripe)

This means the agent always has up-to-date information about what you are looking at — no copy-pasting required.

### Send selection

Select any code in the editor and press `Ctrl+Shift+E` (`Cmd+Shift+E` on Mac). The JiuwenSwarm panel opens and the selection is prefilled in the chat input, formatted with the filename and a code fence, ready for you to add a question.

### File edit interception and diff viewer

When the agent proposes a file edit (`str_replace_editor`, `write_file`, or `create_file` tool calls), the plugin intercepts it before it reaches the filesystem and opens a **JetBrains side-by-side diff window** showing Current vs Proposed. You review and close the window when satisfied.

Enable **Auto-apply file edits** in settings to skip the diff dialog and apply edits immediately via `WriteCommandAction` (with a notification confirming what changed).

### Connection status widget

A status indicator in the IDE status bar shows the live WebSocket connection state:

| Symbol | Meaning |
|--------|---------|
| `⬤` | Connected |
| `◌` | Connecting |
| `↻` | Reconnecting (exponential back-off: 1 s → 2 s → 4 s → … → 30 s) |
| `○` | Disconnected — click to reconnect |

---

## Protocol

Both plugins connect to `ws://localhost:19000/ws` using the same JSON protocol as the JiuwenSwarm web frontend, with `channel_id: "ide"`. The plugin supports both the E2A streaming format (`response_kind: "e2a.chunk" | "e2a.complete" | "e2a.error"`) and the legacy event format (`type: "event"`).

---

## Publishing

For step-by-step instructions on submitting to the JetBrains Marketplace, VS Code Marketplace, and OpenVSX, see [docs/publishing.md](docs/publishing.md).

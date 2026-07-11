# JiuwenSwarm IDE Plugins

Official IDE plugins for [JiuwenSwarm](https://github.com/openjiuwen) — an autonomous multi-agent AI orchestration engine that coordinates specialized agents to handle complex software engineering tasks end-to-end inside your IDE.

## What is JiuwenSwarm?

JiuwenSwarm is a locally-running AI engine. A swarm of specialized agents reads and edits files, runs shell commands, searches codebases, calls external tools via MCP, and reasons across multiple steps — all without leaving your IDE. The IDE plugins embed a native chat panel that streams responses token-by-token and gives full visibility into every action the swarm performs.

## Core Features

Both the VS Code extension and the JetBrains plugin share the same web-based chat UI and the same real-time protocol.

- **Streaming Chat Panel** — conversational interface with real-time token streaming, markdown rendering, and syntax-highlighted code blocks. Responses arrive word-by-word.
- **Tool Call Cards** — every agent action (file read/write, bash command, web search, MCP tool call) is displayed inline as a collapsible card with live status, inputs, and outputs. Full transparency into what the swarm is doing.
- **Session Management** — create, switch between, and resume named sessions. Each session preserves its full conversation history and agent context.
- **Send Selection** — select any code in the editor and send it to JiuwenSwarm with **Ctrl+Shift+E** (Win/Linux) / **⌘⇧E** (Mac) for instant review, explanation, or refactoring.
- **New Session Shortcut** — open a fresh chat at any time with **Ctrl+Shift+J** / **⌘⇧J**.
- **Connection Status** — a status indicator shows the live WebSocket state (connected / reconnecting / disconnected). Click to reconnect.
- **Multi-model Support** — JiuwenSwarm supports Claude (Anthropic), GPT-4o (OpenAI), Gemini, and other providers. Switch models in the JiuwenSwarm settings without changing the plugin configuration.
- **Skills & MCP** — invoke built-in slash-command skills (`/commit`, `/review`, `/init`, …) and any MCP server tools registered with your JiuwenSwarm instance.
- **IDE Context Injection** — every message automatically includes the active file path, cursor position, selected code, and editor diagnostics (warnings/errors). The agent always knows what you are looking at.
- **File Edit Diff Viewer** (JetBrains) — when the agent proposes a file edit, the plugin opens a side-by-side diff window showing Current vs Proposed before applying changes. Enable Auto-apply in settings to skip the dialog.

## Prerequisites

JiuwenSwarm must be running before any plugin connects:

```bash
cd jiuwenswarm && jiuwenswarm-start
# WebSocket server at ws://localhost:19000/ws
```

## Installation

### VS Code

1. Download `jiuwenswarm-0.1.0.vsix` from the [releases page](https://github.com/openjiuwen/jiuwenswarm-ide/releases) or build it locally (see Development).
2. In VS Code, go to **Extensions → ⋯ → Install from VSIX** and select the file.
3. The JiuwenSwarm icon appears in the Activity Bar.

### JetBrains (PyCharm, IntelliJ IDEA, WebStorm, etc.)

1. Download `jiuwenswarm-1.0-SNAPSHOT.zip` from releases or build locally.
2. Go to **Settings → Plugins → ⚙ → Install Plugin from Disk** and select the ZIP file.
3. Restart the IDE.
4. If you see a JCEF (Chromium Embedded Framework) warning, enable it via **Help → Find Action → Registry** → `ide.browser.jcef.enabled` → restart.

## Configuration

### VS Code

Open **Settings → Extensions → JiuwenSwarm**:

| Setting | Default | Description |
|---------|---------|-------------|
| `jiuwenswarm.host` | `localhost` | JiuwenSwarm server hostname |
| `jiuwenswarm.port` | `19000` | WebSocket port |
| `jiuwenswarm.defaultMode` | `agent.plan` | Default agent mode: `agent.plan` / `agent.fast` / `team` |
| `jiuwenswarm.channelId` | `ide` | Channel ID reported to the server |
| `jiuwenswarm.autoConnect` | `true` | Connect on startup |

### JetBrains

Open **Settings → Tools → JiuwenSwarm**:

| Setting | Default | Description |
|---------|---------|-------------|
| Server host | `localhost` | JiuwenSwarm server hostname |
| Server port | `19000` | WebSocket port |
| Channel ID | `ide` | Channel ID reported to the server |
| Connect automatically on IDE startup | on | Open connection on startup |
| Auto-apply file edits (skip diff dialog) | off | Apply agent file edits immediately |

## Usage

| Action | Win/Linux | Mac |
|--------|-----------|-----|
| Open chat panel / new session | `Ctrl+Shift+J` | `⌘⇧J` |
| Send selection to chat | `Ctrl+Shift+E` | `⌘⇧E` |
| Reconnect (VS Code command) | — | — |

1. Start JiuwenSwarm locally.
2. Open the JiuwenSwarm panel from the sidebar (VS Code Activity Bar / JetBrains right sidebar).
3. A session is created automatically on first connect.
4. Type a message or select code and press **Ctrl+Shift+E** to begin.

## Architecture

```
packages/
├── shared-webview/      # Self-contained chat UI (vanilla HTML/JS, no build)
├── vscode-extension/    # VS Code extension (TypeScript + esbuild)
└── jetbrains-plugin/    # JetBrains plugin (Kotlin + Gradle)
```

Both plugins connect to `ws://localhost:19000/ws` using the same JSON protocol as the JiuwenSwarm web frontend, with `channel_id: "ide"`. The plugin supports both the E2A streaming format and the legacy event format.

The chat UI in `shared-webview/` is embedded as a webview in VS Code and via JCEF in JetBrains, so both IDEs render an identical interface with the same behavior.

## Publishing

See [docs/publishing.md](publishing.md) for complete instructions on submitting to the JetBrains Plugin Marketplace, the VS Code Marketplace, and the OpenVSX registry.

## Development

### VS Code Extension

```bash
cd packages/vscode-extension
npm install
npm run build      # one-shot → out/extension.js
npm run watch      # watch mode
```

Open `packages/vscode-extension` in VS Code and press **F5** to launch an Extension Development Host.

Package for distribution:

```bash
npm install -g @vscode/vsce
vsce package       # → jiuwenswarm-0.1.0.vsix
```

### JetBrains Plugin

```bash
cd packages/jetbrains-plugin
./gradlew runIde      # sandboxed IDE with the plugin
./gradlew buildPlugin # → build/distributions/*.zip
```

Requires JDK 17 and Gradle 8.7.

## Feedback & Source

JiuwenSwarm is open-source. Report issues and contribute at [github.com/openjiuwen](https://github.com/openjiuwen).

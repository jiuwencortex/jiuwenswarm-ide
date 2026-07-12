# JiuwenSwarm IDE Plugins

Official IDE plugins for [JiuwenSwarm](https://github.com/openjiuwen) — an autonomous multi-agent AI orchestration engine that coordinates specialized agents to handle complex software engineering tasks end-to-end inside your IDE.

## What is JiuwenSwarm?

JiuwenSwarm is a locally-running AI engine. A swarm of specialized agents reads and edits files, runs shell commands, searches codebases, calls external tools via MCP, and reasons across multiple steps — all without leaving your IDE. The IDE plugins embed a native chat panel that streams responses token-by-token and gives full visibility into every action the swarm performs.

## Core Features

Both the VS Code extension and the JetBrains plugin share the same web-based chat UI and the same real-time protocol.

- **Streaming Chat Panel** — conversational interface with real-time token streaming, markdown rendering, and syntax-highlighted code blocks. Responses arrive word-by-word.
- **Tool Call Cards** — every agent action (file read/write, bash command, web search, MCP tool call) is displayed inline as a collapsible card with live status, inputs, and outputs. Full transparency into what the swarm is doing.
- **Session Management** — create, switch between, delete, and resume named sessions. Each session preserves its full conversation history and agent context.
- **Send Selection** — select any code in the editor and send it to JiuwenSwarm with **Ctrl+Shift+E** (Win/Linux) / **⌘⇧E** (Mac) for instant review, explanation, or refactoring.
- **New Session Shortcut** — open a fresh chat at any time with **Ctrl+Shift+J** / **⌘⇧J**.
- **Connection Status** — a status indicator shows the live WebSocket state (connected / reconnecting / disconnected). Click to reconnect. Token usage is displayed when available.
- **Multi-model Support** — JiuwenSwarm supports Claude (Anthropic), GPT-4o (OpenAI), Gemini, and other providers. Switch models in the JiuwenSwarm settings without changing the plugin configuration.
- **Skills & MCP** — invoke built-in slash-command skills (`/commit`, `/review`, `/init`, …) and any MCP server tools registered with your JiuwenSwarm instance.
- **IDE Context Injection** — every message automatically includes the active file path, cursor position, selected code, editor diagnostics (warnings/errors), other open files, project directory tree, and git branch/status. The agent always knows what you are looking at.
- **File Edit Diff Viewer** (JetBrains) — when the agent proposes a file edit, the plugin opens a side-by-side diff window showing Current vs Proposed before applying changes.
- **File Edit Application** (VS Code) — file edits are applied directly to the workspace with notification toasts confirming each change.
- **Approval Workflow** — optionally require your confirmation before applying any agent file edit on either platform.
- **Checkpoint / Rewind** — after each turn that modifies files, click a button to undo all changes from that turn and restore files to their previous state.
- **Clickable File Links** — file paths mentioned by the agent are clickable and open the file at the referenced line.
- **Alt+Enter Quick Fix** (JetBrains) — place the cursor on any error or warning and press **Alt+Enter** to see "Fix with JiuwenSwarm", which prefills the chat with the error text and surrounding code.

## Prerequisites

JiuwenSwarm must be running before any plugin connects:

```bash
cd jiuwenswarm && jiuwenswarm-start
# WebSocket server at ws://localhost:19000/ws
```

## Installation & Usage

Quick-start for each platform — for detailed walkthroughs of every panel, setting, and workflow see the User Guides below.

- [VS Code Extension](docs/vscode/README.md) — install from VSIX or marketplace, configure, and use
- [JetBrains Plugin](docs/jetbrains/README.md) — install from ZIP or marketplace, configure, and use

## Documentation

| Document | What you'll find |
|----------|------------------|
| [VS Code User Guide](docs/vscode/USER_GUIDE.md) | Complete walkthrough of every panel, setting, action, and workflow |
| [JetBrains User Guide](docs/jetbrains/USER_GUIDE.md) | Complete walkthrough of every panel, setting, action, and workflow |
| [Architecture Reference](docs/ARCHITECTURE.md) | Protocol, component model, context injection, file-edit handling, and shared webview design |
| [Development Plan](docs/IDE_PLUGIN_PLAN.md) | Feature roadmap with descriptions, implementation phases, and build/distribution notes |
| [VS Code Publishing](docs/vscode/PUBLISHING.md) | Build, package, and publish the VS Code extension |
| [JetBrains Publishing](docs/jetbrains/PUBLISHING.md) | Build, sign, and publish the JetBrains plugin |

## Development

```
packages/
├── shared-webview/      # Self-contained chat UI (vanilla HTML/JS, no build)
├── vscode-extension/    # VS Code extension (TypeScript + esbuild)
└── jetbrains-plugin/    # JetBrains plugin (Kotlin + Gradle)
```

Both plugins connect to `ws://localhost:19000/ws` using the same JSON protocol as the JiuwenSwarm web frontend, with `channel_id: "ide"`. The chat UI in `shared-webview/` is embedded as a webview in VS Code and via JCEF in JetBrains, so both IDEs render an identical interface with the same behavior.

## Feedback & Source

JiuwenSwarm is open-source. Report issues and contribute at [github.com/openjiuwen](https://github.com/openjiuwen).

# JiuwenSwarm IDE Plugins

AI coding assistant plugins for VS Code and JetBrains IDEs, backed by a locally running [JiuwenSwarm](../jiuwenswarm) instance.

## Prerequisites

JiuwenSwarm must be running:
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
2. Press **F5** → "Run Extension" launch config opens a new Extension Development Host window
3. Open the JiuwenSwarm panel in the Activity Bar (robot icon, or `Ctrl+Shift+J`)

### Packaging

```bash
npm install -g @vscode/vsce
vsce package       # → jiuwenswarm-0.1.0.vsix
code --install-extension jiuwenswarm-0.1.0.vsix
```

### Configuration

`Settings → Extensions → JiuwenSwarm`:

| Setting | Default | Description |
|---------|---------|-------------|
| `jiuwenswarm.host` | `localhost` | JiuwenSwarm server hostname |
| `jiuwenswarm.port` | `19000` | WebSocket port |
| `jiuwenswarm.defaultMode` | `code.normal` | Default agent mode |
| `jiuwenswarm.autoConnect` | `true` | Connect on startup |

### Keybindings

| Key | Command |
|-----|---------|
| `Ctrl+Shift+J` | Open chat panel |
| `Ctrl+Shift+E` | Send selection to chat |

---

## JetBrains Plugin

Works with **PyCharm**, **IntelliJ IDEA**, **WebStorm**, and all JetBrains IDEs (2023.1+).

### Development

```bash
cd packages/jetbrains-plugin
./gradlew runIde      # launch a sandboxed IDE instance with the plugin installed
./gradlew buildPlugin # → build/distributions/jiuwenswarm-0.1.0.zip
```

### Installation

1. **From build**: Settings → Plugins → ⚙ → Install Plugin from Disk → select the `.zip`
2. **From Marketplace**: (future) search "JiuwenSwarm"

### Configuration

Settings → Tools → JiuwenSwarm:
- **Server host** (default: `localhost`)
- **Server port** (default: `19000`)
- **Channel ID** (default: `ide`)
- **Auto-connect on startup**

### Keybindings

| Key | Command |
|-----|---------|
| `Ctrl+Shift+J` | New session |
| `Ctrl+Shift+E` | Send selection to chat |

---

## Phase 1 Feature Set

- [x] Chat panel embedded in IDE (streaming responses)
- [x] Session management (create / switch / list)
- [x] Tool call cards (show what the agent is doing)
- [x] Connection status (status bar + panel indicator)
- [x] Automatic reconnect with exponential backoff
- [x] Shared webview HTML between both IDEs

## Protocol

Both plugins connect to `ws://localhost:19000/ws` using the exact same protocol as the JiuwenSwarm web frontend, with `channel_id: "ide"`.

See [IDE_PLUGIN_PLAN.md](../docs-michael/IDE_PLUGIN_PLAN.md) for the full architecture.

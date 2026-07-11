# JiuwenSwarm for JetBrains

The JiuwenSwarm plugin brings an autonomous multi-agent AI chat panel directly into PyCharm, IntelliJ IDEA, WebStorm, and all JetBrains IDEs (2023.1+).

## Prerequisites

JiuwenSwarm must be running locally before the plugin connects:

```bash
cd jiuwenswarm && jiuwenswarm-start
# WebSocket server at ws://localhost:19000/ws
```

JCEF (Chromium Embedded Framework) must be enabled in your IDE. If you see a warning on first open, enable it via **Help → Find Action → Registry** → `ide.browser.jcef.enabled`, then restart.

## Installation

### From ZIP

1. Download `jiuwenswarm-1.0-SNAPSHOT.zip` from the [releases page](https://github.com/openjiuwen/jiuwenswarm-ide/releases).
2. Go to **Settings → Plugins → ⚙ → Install Plugin from Disk** and select the ZIP file.
3. Restart the IDE.

### From Marketplace

Search "JiuwenSwarm" in **Settings → Plugins → Marketplace** and click Install.

## Configuration

Open **Settings → Tools → JiuwenSwarm**:

| Setting | Default | Description |
|---------|---------|-------------|
| Server host | `localhost` | JiuwenSwarm server hostname |
| Server port | `19000` | WebSocket port |
| Channel ID | `ide` | Channel ID reported to the server |
| Connect automatically on IDE startup | on | Open connection on startup |
| Auto-apply file edits (skip diff dialog) | off | Apply agent file edits immediately without review |

## Usage

| Action | Win/Linux | Mac | Description |
|--------|-----------|-----|-------------|
| New session | `Ctrl+Shift+J` | `⌘⇧J` | Opens a fresh chat session |
| Send selection | `Ctrl+Shift+E` | `⌘⇧E` | Sends selected code to the chat |

1. Start JiuwenSwarm locally.
2. Open the JiuwenSwarm tool window from the right sidebar.
3. A session is created automatically on first connect.
4. Type a message, or select code and press **Ctrl+Shift+E**, then add your question and send.

## What You See

The chat panel renders inside the IDE via JCEF. It supports:

- Markdown rendering with syntax-highlighted code blocks
- Collapsible tool call cards showing every agent action with live status, inputs, and outputs
- A mode selector dropdown (`agent.plan`, `agent.fast`, `team`)
- A session dropdown to create or switch conversations
- An attach button to include the current file as context
- A dark/light theme toggle

## File Edit Review

When the agent proposes a file edit, the plugin opens a **side-by-side diff window** showing Current vs Proposed. Review the changes and close the window to apply. Enable **Auto-apply file edits** in settings to skip the diff dialog and apply edits immediately (with a notification confirming what changed).

## Connection Status

A widget in the status bar shows the live WebSocket state:

| Symbol | Meaning |
|--------|---------|
| `⬤` | Connected |
| `◌` | Connecting |
| `↻` | Reconnecting (exponential back-off: 1 s → 2 s → 4 s → … → 30 s) |
| `○` | Disconnected — click to reconnect |

## Troubleshooting

- **Panel shows blank**: Enable JCEF via **Help → Find Action → Registry** → `ide.browser.jcef.enabled`, then restart.
- **No connection**: Check that Jiuwenswarm is running on the configured host and port. Click the status bar widget to reconnect.
- **No responses appear**: Verify the WebSocket endpoint (`ws://host:port/ws`) is reachable. Check **Help → Show Log in Explorer** for plugin logs.

# JiuwenSwarm for VS Code

The JiuwenSwarm extension brings an autonomous multi-agent AI chat panel directly into Visual Studio Code.

## Prerequisites

JiuwenSwarm must be running locally before the extension connects:

```bash
cd jiuwenswarm && jiuwenswarm-start
# WebSocket server at ws://localhost:19000/ws
```

## Installation

### From VSIX

1. Download `jiuwenswarm-0.1.0.vsix` from the [releases page](https://github.com/openjiuwen/jiuwenswarm-ide/releases).
2. In VS Code, go to **Extensions → ⋯ → Install from VSIX** and select the file.
3. The JiuwenSwarm icon appears in the Activity Bar.

### From Marketplace

Search "JiuwenSwarm" in **Extensions → Marketplace** and click Install.

## Configuration

Open **Settings → Extensions → JiuwenSwarm**:

| Setting | Default | Description |
|---------|---------|-------------|
| `jiuwenswarm.host` | `localhost` | JiuwenSwarm server hostname |
| `jiuwenswarm.port` | `19000` | WebSocket port |
| `jiuwenswarm.defaultMode` | `code.plan` | Default agent mode: `code.plan` / `code.normal` / `code.team` |
| `jiuwenswarm.channelId` | `ide` | Channel ID reported to the server |
| `jiuwenswarm.autoConnect` | `true` | Connect on startup |
| `jiuwenswarm.approveEdits` | `false` | Show approval prompt before applying agent file edits |

## Usage

| Action | Win/Linux | Mac | Description |
|--------|-----------|-----|-------------|
| Open chat panel | `Ctrl+Shift+J` | `⌘⇧J` | Opens the JiuwenSwarm sidebar |
| Send selection | `Ctrl+Shift+E` | `⌘⇧E` | Sends selected code to the chat input |
| New session | — | — | Use the command palette: `JiuwenSwarm: New Session` |
| Reconnect | — | — | Use the command palette: `JiuwenSwarm: Reconnect` |

1. Start JiuwenSwarm locally.
2. Click the JiuwenSwarm icon in the Activity Bar or press **Ctrl+Shift+J**.
3. A session is created automatically on first connect.
4. Type a message, or select code and press **Ctrl+Shift+E**, then add your question and send.

## What You See

The chat panel renders in the sidebar using a VS Code webview. It supports:

- Markdown rendering with syntax-highlighted code blocks
- Collapsible tool call cards showing every agent action with live status, inputs, and outputs
- A mode selector dropdown (`code.plan`, `code.normal`, `code.team`)
- A session dropdown to create, switch, or delete conversations
- A skills panel to view and toggle registered skills
- Clickable file links in agent responses — click to open the file at the referenced line
- A rewind bar that appears after the agent edits files — click to undo all changes from that turn
- An attach button to include the current file as context
- A dark/light theme toggle

## File Edits

When the agent proposes a file edit, the extension applies it directly to your workspace. A notification toast confirms each applied change.

Enable **Approve edits** in settings to require your confirmation before every file change. When enabled, a prompt appears with **Approve** and **Reject** buttons for each proposed edit.

## Checkpoint / Rewind

After each agent turn that modifies files, a rewind bar appears at the bottom of the chat panel. Click **Undo Changes** to restore all files to their state before that turn. Files that did not exist before the turn are deleted on rewind.

Rewind is cleared when you send a new message or start a new session.

## Status Bar

The status bar widget in the bottom-right shows:

| State | Icon | Behaviour |
|-------|------|-----------|
| Connected | `$(check)` | Click to open chat |
| Connecting | `$(loading~spin)` | — |
| Reconnecting | `$(sync~spin)` | Auto-retry with exponential back-off |
| Disconnected | `$(circle-slash)` | Click to force reconnect |

When token usage metadata is received from the server, the token count is displayed next to the status label.

## Troubleshooting

- **Panel shows blank / no connection**: Check that JiuwenSwarm is running on the configured host and port. The status bar shows connection state; click it to reconnect.
- **No responses appear**: Verify the WebSocket endpoint (`ws://host:port/ws`) is reachable. Open the webview developer tools (**Developer: Open Webview Developer Tools** from the command palette) and check the console for debug messages.
- **Send Selection does nothing**: Make sure text is actually selected in the editor.
- **Clickable file links don't open**: Ensure the file path exists in your workspace.

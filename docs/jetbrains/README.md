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
| Require approval before applying agent file edits | off | Show confirmation dialog before every file change |
| Auto-apply file edits (skip diff dialog) | off | Apply agent file edits immediately without review |

## Usage

| Action | Win/Linux | Mac | Description |
|--------|-----------|-----|-------------|
| New session | `Ctrl+Shift+J` | `⌘⇧J` | Opens a fresh chat session |
| Send selection | `Ctrl+Shift+E` | `⌘⇧E` | Sends selected code to the chat |
| Fix with AI | `Alt+Enter` | `⌥Enter` | Appears on errors/warnings in quick-fix menu |

1. Start JiuwenSwarm locally.
2. Open the JiuwenSwarm tool window from the right sidebar.
3. A session is created automatically on first connect.
4. Type a message, or select code and press **Ctrl+Shift+E**, then add your question and send.

## What You See

The chat panel renders inside the IDE via JCEF. It supports:

- Markdown rendering with syntax-highlighted code blocks
- Collapsible tool call cards showing every agent action with live status, inputs, and outputs
- A mode selector dropdown (`code.plan`, `code.normal`, `code.team`)
- A session dropdown to create, switch, or delete conversations
- A skills panel to view and toggle registered skills
- Clickable file links in agent responses — click to open the file at the referenced line
- A rewind bar that appears after the agent edits files — click to undo all changes from that turn
- An attach button to include the current file as context
- A dark/light theme toggle

## File Edit Review

When the agent proposes a file edit, the plugin opens a **side-by-side diff window** showing Current vs Proposed. Review the changes and close the window to apply.

Enable **Auto-apply file edits** in settings to skip the diff dialog and apply edits immediately (with a notification confirming what changed).

Enable **Require approval** in settings to see a confirmation dialog before the diff window opens or before auto-applying. This gives you a chance to reject unwanted edits.

## Checkpoint / Rewind

After each agent turn that modifies files, a rewind bar appears at the bottom of the chat panel. Click **Undo Changes** to restore all files to their state before that turn. Files that did not exist before the turn are deleted on rewind.

Rewind is cleared when you send a new message or start a new session.

## Connection Status

A widget in the status bar shows the live WebSocket state:

| Symbol | Meaning |
|--------|---------|
| `⬤` | Connected |
| `◌` | Connecting |
| `↻` | Reconnecting (exponential back-off: 1 s → 2 s → 4 s → … → 30 s) |
| `○` | Disconnected — click to reconnect |

The status bar also shows the current session ID and cumulative token usage when available.

## Alt+Enter Quick Fix

Place the cursor on any highlighted error or warning and press **Alt+Enter**. The quick-fix menu includes **Fix with JiuwenSwarm**, which opens the chat panel and prefills the input with the error message and surrounding code context.

## Troubleshooting

- **Panel shows blank**: Enable JCEF via **Help → Find Action → Registry** → `ide.browser.jcef.enabled`, then restart.
- **No connection**: Check that JiuwenSwarm is running on the configured host and port. Click the status bar widget to reconnect.
- **No responses appear**: Verify the WebSocket endpoint (`ws://host:port/ws`) is reachable. Check **Help → Show Log in Explorer** for plugin logs.

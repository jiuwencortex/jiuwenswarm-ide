# JiuwenSwarm VS Code User Guide

This guide walks you through using the JiuwenSwarm extension in Visual Studio Code — from first install to everyday workflows.

## What You Get

A chat panel in the Activity Bar that connects to a locally-running JiuwenSwarm instance. Ask questions in natural language, send code selections, and watch the agent reason in real time. Every tool call the agent makes is displayed as a collapsible card so you see exactly what is happening.

---

## First-time Setup

1. **Start JiuwenSwarm** on your machine:
   ```bash
   cd jiuwenswarm && jiuwenswarm-start
   ```
   The WebSocket server runs at `ws://localhost:19000/ws`.

2. **Install the extension** from the [releases page](https://github.com/openjiuwen/jiuwenswarm-ide/releases). Download `jiuwenswarm-0.1.0.vsix`, then in VS Code go to **Extensions → ⋯ → Install from VSIX** and select it.

3. **Open the panel**: Click the JiuwenSwarm icon in the left Activity Bar, or press **Ctrl+Shift+J** / **⌘⇧J**.

4. **Connect**: If `jiuwenswarm.autoConnect` is enabled (default), the extension connects automatically. Otherwise press **Ctrl+Shift+J** or run `JiuwenSwarm: Open Chat` from the command palette.

A session is created automatically on first connection.

---

## The Chat Panel

The panel is divided into three areas:

### Header row
- **Model label** — shows the active LLM (e.g. Claude, GPT-4o)
- **Session dropdown** — switch between past conversations or create a new one
- **Attach button** — include the current file as context for the next message
- **Theme toggle** — switch between dark and light mode
- **Debug button** — turns on verbose logging in the Output panel

### Conversation area
Messages appear here in real time:
- **User messages** — what you typed or sent
- **Assistant responses** — stream word-by-word with markdown and code block highlighting
- **Tool call cards** — every file read, bash command, web search, or MCP tool is shown as a collapsible card with status, inputs, and outputs
- **Reasoning blocks** — when the model exposes its thinking process

### Input row
- **Mode selector** — choose the agent mode before sending:
  - `agent.plan` — reasoning-heavy, step-by-step planning (default)
  - `agent.fast` — quicker responses with less deliberation
  - `team` — multi-agent cluster mode
- **Text input** — type your message and press Enter to send
- **Attach button** — same as the header attach; includes the current file path in context

---

## Working with Sessions

### Automatic session creation
On first connect the server assigns a session automatically. You do not need to do anything.

### Starting a new session
- Click the **+** button next to the session dropdown, or
- Run `JiuwenSwarm: New Session` from the command palette, or
- Press **Ctrl+Shift+J** / **⌘⇧J**

This drops the current WebSocket connection and reconnects, giving you a fresh session with no history.

### Switching sessions
Open the session dropdown to see recent conversations. Click one to resume it. The full conversation history and agent context are restored.

---

## Sending Code to the Agent

### Send Selection
Select any code in the editor and press **Ctrl+Shift+E** / **⌘⇧E**. The chat panel opens and the selection is prefilled in the input area, wrapped in a code fence with the filename. Add your question and press Enter.

Example:
```
[File: main.py]
```
def calculate_total(items):
    return sum(item.price for item in items)
```

Add a 10% discount to this function.
```

### Attach current file
Click the **paperclip icon** in the header or input row. This sends the active file path as context with your next message, even if no text is selected.

### What the agent sees
Every message you send automatically includes a structured context block with:
- The active file path and language
- Your cursor position
- The selected code (if any)
- Warnings and errors visible in the Problems panel (up to 10)
- Other open file paths (up to 10)
- Git branch name and whether there are uncommitted changes

You never need to copy-paste file paths or error messages.

---

## Understanding the Response

### Streaming text
Responses arrive token-by-token. You see words appear in real time, not all at once after a long wait.

### Tool call cards
When the agent decides to read a file, run a command, or call a tool, a card appears inline:
- **File read** — shows the path and a snippet of what was read
- **Bash command** — shows the command and its stdout/stderr
- **Web search** — shows the query and top results
- **MCP tool** — shows the tool name, inputs, and returned output

Each card is collapsible so you can hide it once you have read it.

### Reasoning blocks
Some models expose their internal reasoning. These appear as greyed-out collapsible sections labeled "Thinking…".

---

## Skills & MCP

Open the **Skills** panel from the chat header (the **sliders icon**). You see a list of registered skills such as:
- `/commit` — generate a commit message from staged changes
- `/review` — review the current file for issues
- `/init` — initialise a new project scaffold

Each skill shows whether it is enabled. Toggle the switch to enable or disable it. Disabled skills are ignored by the agent.

Skills are registered with your JiuwenSwarm instance, not the plugin. The plugin only shows what is available and lets you toggle them.

---

## Status Bar

A JiuwenSwarm widget sits in the bottom-right status bar:

| State | Appearance | Action |
|-------|-----------|--------|
| Connected | `$(check) JiuwenSwarm` | Click to open chat |
| Connected (with tokens) | `$(check) JiuwenSwarm · 1.2k` | Shows cumulative token usage |
| Connecting | `$(loading~spin) JiuwenSwarm` | Wait or check server |
| Reconnecting | `$(sync~spin) JiuwenSwarm` | Auto-retrying with back-off |
| Disconnected | `$(circle-slash) JiuwenSwarm` | Click to force reconnect |

The tooltip shows the session ID and token count when available.

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+J` / `⌘⇧J` | Open chat panel |
| `Ctrl+Shift+E` / `⌘⇧E` | Send selection to chat |

---

## Settings

Open **Settings → Extensions → JiuwenSwarm** to change:

| Setting | Default | When to change |
|---------|---------|----------------|
| `jiuwenswarm.host` | `localhost` | If JiuwenSwarm runs on another machine |
| `jiuwenswarm.port` | `19000` | If you changed the server port |
| `jiuwenswarm.defaultMode` | `agent.plan` | Prefer `agent.fast` for quicker responses |
| `jiuwenswarm.channelId` | `ide` | Only if your server uses a different channel |
| `jiuwenswarm.autoConnect` | `true` | Disable if you prefer manual connection |

Settings changes require a window reload to take effect. The extension prompts you when you save a change.

---

## Troubleshooting

**Panel shows blank or "Loading JiuwenSwarm…"**
- Check that JiuwenSwarm is running on the configured host and port
- The status bar shows the connection state; click it to reconnect

**No responses appear after sending a message**
- Verify `ws://host:port/ws` is reachable (use a WebSocket test client)
- Turn on debug logging via the debug button in the chat header, then check **Output → JiuwenSwarm** for raw traffic
- Open the webview developer tools: run **Developer: Open Webview Developer Tools** from the command palette and look for JavaScript errors

**Send Selection does nothing**
- Make sure text is actually selected in the editor
- Check that the editor has focus before pressing the shortcut

**Status bar shows disconnected**
- The server may have stopped — restart it
- Network changed — click the status bar widget to reconnect
- Wrong host/port — check Settings

---

## Feedback

Report issues and contribute at [github.com/openjiuwen](https://github.com/openjiuwen).

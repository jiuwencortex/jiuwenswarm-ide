# JiuwenSwarm IDE Plugins — Presentation Notes

Source material for two slides. Keep the language simple and direct.

---

## Slide 1 — What is the IDE plugin and what does it do today

### What it is

Two IDE plugins — one for **JetBrains** (PyCharm, IntelliJ, WebStorm, etc.) and one for **VS Code** — that embed JiuwenSwarm directly inside the editor. No browser tab. No switching context. The agent lives where the code lives.

Both plugins share the same feature set. Everything described below works in both.

### Who it is for

Developers who already use JiuwenSwarm and want the agent to work inside their IDE instead of in a separate window. The plugin gives the agent full awareness of what the developer is doing — open file, cursor position, selected code, errors, git state — so they don't have to copy-paste context manually.

### What it does today

**Chat panel**
- Streaming chat with the agent, rendered inside the IDE
- Three modes: Plan & Execute, Execute, Team Coding (multi-agent swarm)
- File mentions (`@filename`), skill picker (`#skill`), preset prompt templates (`!`)
- Image attachments (PNG, JPEG, WebP, GIF)

**IDE context — sent automatically with every message**
- Active file, cursor line, selected code
- Editor errors and warnings (up to 10)
- Other open tabs
- Project directory tree (2-level)
- Git branch and change count
- Project rules file (`.jiuwenswarm/instructions.md` or `AGENTS.md`)

**File editing**
- Agent edits land in a diff viewer for review before applying
- Or auto-apply mode (edits applied immediately, undoable with Ctrl+Z)
- Checkpoint / rewind: one click restores all files from before the agent's last turn

**Code navigation**
- File paths in agent responses are clickable links — opens the file at the exact line
- Alt+Enter (JetBrains) / lightbulb (VS Code): "Fix with JiuwenSwarm" on any error

**Terminal**
- Agent shell commands run in a dedicated terminal tab inside the IDE

**Git**
- Commit and Push buttons with auto-suggested message (optional, off by default)

**Sessions and skills**
- Multiple named sessions, switch between them with history reload
- Skill library with per-skill on/off toggle
- **Conversation export**: one-click "↓ Export session" from the ☰ menu — converts the session to Markdown and opens it in the editor

**Live Swarm Map — unique to JiuwenSwarm**
Opens automatically when a `code.team` session starts. Shows the entire swarm at work in real time:
- **Map view**: interactive canvas with agent nodes, animated pipeline flow between them, pan/zoom, click any agent to inspect
- **List view**: per-agent lane cards with status chip, live elapsed timer, activity feed (what tool the agent just called), task title
- **Board view**: three-column kanban — Backlog / In Progress / Done — one card per task; cards show the assigned agent (colour-coded dot), Blocked badge when stalled, strikethrough when cancelled
- **Task pills** (List view): pill per task, colour shifts as tasks move from pending → active → done
- **Progress**: header chip shows `tasks done / total · agents working`, with a completion bar
- **Jump to file**: click any agent lane → IDE opens the file that agent last touched
- **Message log**: collapsible log of inter-agent messages, colour-coded by sender
- **Session summary**: when all agents finish, shows total agents, tasks completed, messages sent, and time per agent
- **Debug console**: toggleable raw event log for diagnosing sessions

---

## Slide 2 — Next steps

Ordered roughly by how close each item is to being done.

### Month 1 — Polish and quick wins

- **Inline accept/reject per code block** — agent edits show up as highlighted diff lines directly in the editor; accept or reject each hunk without opening a separate dialog (like Cursor). Infrastructure is in place; this is a UI change.
- **"Fix all errors" action** — one button collects every error and warning in the current file and sends them to the agent at once, instead of using Alt+Enter one at a time.
- **Right-click quick actions** — select any function, right-click → Explain / Write tests / Add docs. Sends the selection with the right prompt pre-filled.
- **Terminal output → chat** — one-click button copies whatever is in the terminal (stack traces, test output) into the chat. No more manual highlight-and-paste.
- **OS desktop notification** — notify the developer when a long agent run finishes while the IDE is in the background.
- **Smart commit message** — instead of using the last chat message as the commit message, the agent reads the actual diff and writes an accurate one.
- **Swarm Map: steer agents** — pause or redirect an individual agent from the Swarm Map panel (requires a small server API addition, in progress).

### Month 2 — Productivity features

- **Background / async agents** — send a task and keep coding. The agent works in the background; the IDE shows a notification when it finishes. The UI does not block.
- **Test loop** — after the agent edits files, automatically run the project's tests and feed failures back to the agent so it can fix them without the developer doing anything.
- **Custom slash commands** — define reusable prompt templates in `.jiuwenswarm/commands/` (e.g. `/code-review`, `/write-changelog`). They appear as autocomplete options in the chat input.
- **Pinned context files** — mark files (schema, main config, README) that the agent should always see, regardless of what file is currently open.
- **PR / diff review** — load a branch diff or paste a pull request URL; the agent reviews it for bugs, security issues, and improvements.
- **Full multi-turn rewind** — undo not just the last agent turn but any previous turn from a scrollable history, not just the most recent one.
- **Conversation fork** — "branch from this message": start a new session from any point in the conversation history without losing the current thread.

### Month 3 — Intelligence and reach

- **Semantic codebase search** — before answering, the agent automatically searches the whole workspace for relevant files and symbols using ripgrep / AST matching and adds them to context. No vector database needed.
- **Persistent memory** — the agent remembers project conventions, past decisions, and team rules between sessions. Stored in a plain text file the developer can edit or clear.
- **Web search and docs lookup** — `@web` searches the internet; paste a docs URL and ask questions about it directly in chat.
- **Morning briefing** — when the IDE opens, a one-time card summarises what changed since last time: teammate commits, PRs to review, CI failures, in plain English. No dashboard, no browser.
- **Blast radius preview** — before saving, a side pane shows everything in the codebase that calls or imports what just changed. Zero-impact edits show green; large-impact edits show a warning. Pure static analysis, no build step.
- **AI git archaeology** — right-click any line → "Why does this exist?". The agent traces git blame, commit message, PR, and linked tickets and produces a plain-English explanation of why that line is written the way it is.

### Further out — genuinely new capabilities

- **Sketch → implement**: describe a feature, get an editable architecture diagram first, then click "Implement this" and the swarm uses the diagram as its spec.
- **Proactive watchdog**: agent silently monitors edits and flags semantic issues (SQL injection, race conditions, duplicate code) as soft badges, without blocking the developer.

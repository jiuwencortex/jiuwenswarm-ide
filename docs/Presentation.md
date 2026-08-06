# JiuwenSwarm IDE Plugins — Presentation Notes

Source material for up to four slides. Keep the language simple and direct.

---

## Slide 1 — Plugin today (general capabilities)

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
- Commit and Push buttons; commit message pre-filled from your last message (optional, off by default)

**Sessions and skills**
- Multiple named sessions, switch between them with history reload
- Skill library with per-skill on/off toggle
- **Conversation export**: one-click "↓ Export session" from the ☰ menu — converts the session to Markdown and opens it in the editor

---

## Slide 2 — Swarm today (multi-agent capabilities)

When a `code.team` session starts, the **Live Swarm Map** panel opens automatically. It shows the entire swarm at work in real time across three views:

**Map view**
- Interactive canvas with all active agent nodes
- Animated pipeline flow arrows between agents
- Pan / zoom; click any agent node to inspect its current state

**List view**
- Per-agent lane card: status chip (idle / working / done), live elapsed timer, activity feed showing what tool the agent just called, current task title
- Task pill strip: one pill per task, colour shifts as tasks move pending → active → done
- Collapsible inter-agent message log, colour-coded by sender
- When all agents finish: session summary — agents, tasks completed, messages sent, time per agent

**Board view (kanban)**
- Three columns: Backlog / In Progress / Done
- One card per task; each card shows task title, assigned agent name with colour dot
- Status badges: Blocked (stalled) / strikethrough (cancelled)
- Updates live on every snapshot

**Cross-view features**
- Header chip: `tasks done / total · N agents working` + completion percentage bar
- Click any agent lane → IDE opens the file that agent last touched
- ☰ debug console: toggleable raw event log for diagnosing sessions

---

## Slide 3 — Plugin roadmap (general next steps)

Ordered roughly by how close each item is to being done.

### Month 1 — Polish and quick wins

- **Inline accept/reject per code block** — agent edits show up as highlighted diff lines directly in the editor; accept or reject each hunk without opening a separate dialog (like Cursor). Most of the diff plumbing is in place; this is a UI change.
- **"Fix all errors" action** — one button collects every error and warning in the current file and sends them to the agent at once, instead of using Alt+Enter one at a time.
- **Right-click quick actions** — select any function, right-click → Explain / Write tests / Add docs. Sends the selection with the right prompt pre-filled.
- **Terminal output → chat** — one-click button copies whatever is in the terminal (stack traces, test output) into the chat. No more manual highlight-and-paste.
- **OS desktop notification** — notify the developer when a long agent run finishes while the IDE is in the background.
- **Smart commit message** — instead of using the last chat message as the commit message, the agent reads the actual diff and writes an accurate one.

### Month 2 — Productivity features

- **Background / async agents** — send a task and keep coding. The agent works in the background; the IDE shows a notification when it finishes. The UI does not block.
- **Test loop** — after the agent edits files, automatically run the project's tests and feed failures back to the agent so it can fix them without the developer doing anything.
- **Custom slash commands** — define reusable prompt templates in `.jiuwenswarm/commands/` (e.g. `/code-review`, `/write-changelog`). They appear as autocomplete options in the chat input.
- **Custom system prompt** — a free-text field in plugin settings that prepends personal instructions to every request (preferred style, language, persona). Applies across all projects, independent of the project rules file.
- **Pinned context files** — mark files (schema, main config, README) that the agent should always see, regardless of what file is currently open.
- **PR / diff review** — load a branch diff or paste a pull request URL; the agent reviews it for bugs, security issues, and improvements.
- **Full multi-turn rewind** — undo not just the last agent turn but any previous turn from a scrollable history.
- **Conversation fork** — "branch from this message": start a new session from any point in the conversation history without losing the current thread.

### Month 3 — Intelligence and reach

- **Semantic codebase search** — before answering, the agent automatically searches the whole workspace for relevant files and symbols using ripgrep / AST matching and adds them to context. No vector database needed.
- **Persistent memory** — the agent remembers project conventions, past decisions, and team rules between sessions. Stored in a plain text file the developer can edit or clear.
- **Web search and docs lookup** — `@web` searches the internet; paste a docs URL and ask questions about it directly in chat.
- **Morning briefing** — when the IDE opens, a one-time card summarises what changed since last time: teammate commits, PRs to review, CI failures, in plain English. No dashboard, no browser.
- **Blast radius preview** — before saving, a side pane shows everything in the codebase that calls or imports what just changed. Zero-impact edits show green; large-impact edits show a warning. Pure static analysis, no build step.
- **AI git archaeology** — right-click any line → "Why does this exist?". The agent traces git blame, commit message, PR, and linked tickets and produces a plain-English explanation of why that line is written the way it is.

### Further out

- **Proactive watchdog**: agent silently monitors edits and flags semantic issues (SQL injection, race conditions, duplicate code) as soft badges, without blocking the developer.

---

## Slide 4 — Swarm roadmap (multi-agent next steps)

### Month 1 — Steering the swarm

- **Steer individual agents** — pause or redirect any running agent from the Swarm Map panel without stopping the rest of the swarm. Requires a small server + plugin API addition (`team.agent.pause`, `team.agent.redirect`); the Swarm Map panel currently only supports opening an agent's file.

### Month 2 — Swarm control

- **Spawn agent mid-session** — click "+ Agent" in the Swarm Map to add a new worker to a running session and assign it a task. Useful when a specialist is needed after work has started (e.g. add a security reviewer once coding is done).
- **Conflict detection** — when two agents edit the same file simultaneously, the Swarm Map shows a collision badge on both lanes. The orchestrator is notified and can serialise writes or reassign one agent.
- **Quality gate hooks** — define shell commands in `.jiuwenswarm/hooks.json` that run automatically after every agent turn (`pytest`, `eslint`, `mypy`). Non-zero exit feeds the output back to the agent for a fix attempt.
- **Per-agent cost breakdown** — extend the existing stats bar with a per-agent token / cost breakdown in the session summary: which worker spent the most and why.

### Month 3 — Visibility and collaboration

- **Swarm replay** — after a session ends, a timeline scrubber lets you step through the session event-by-event: watch agents spawn, tasks progress, and files change in the order they happened. Useful for debugging or showing teammates what the swarm did.
- **Team session templates** — save a named swarm configuration (roles, system prompts, task split strategy) as a template in `.jiuwenswarm/teams/`. Pick a template from the chat input to start a pre-configured multi-agent session in one click.
- **Read-only session stream** — generate a shareable URL so anyone can watch a live session (chat, Swarm Map, file diffs) in a browser without IDE access. Observer-only; no message sending.
- **Auto-session context** — when the agent references a file, that file is automatically pinned for the rest of the session so follow-up messages don't lose track of it.
- **`@swarm` context mention** — type `@swarm` in chat to attach a snapshot of the current Swarm Map state (lanes, tasks, progress) as structured context to the next message.

### Further out

- **Sketch → implement**: describe a feature in plain English, get an editable architecture diagram (Mermaid / Excalidraw) showing which components it touches and what is new, annotate or adjust it, then click "Implement this" — the swarm uses the diagram as its spec.

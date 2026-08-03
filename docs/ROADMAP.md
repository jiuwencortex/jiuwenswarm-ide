# JiuwenSwarm IDE Plugins — Backlog

Future development items for the JetBrains plugin and VS Code extension.

---

## Incomplete — foundational work exists

These features are partially built. The infrastructure is in place; specific gaps remain.

| Feature | What exists | What is missing | Seen in |
|---------|-------------|-----------------|---------|
| **Git PR & conflict help** | Commit with auto-suggested message + Push buttons (controlled by the "Git enabled" setting) | PR description generation from commit history; merge conflict resolution assistance | GitHub Copilot, Cursor, GitLens AI |
| **Conversation fork** | Session switching and creating new sessions from any point | "Branch from this message" — start a parallel session from a specific turn without discarding the current one | Claude.ai (branch conversation) |
| **Full checkpoint timeline** | Rewind restores files from the most recent agent turn only | Rolling snapshot buffer across multiple turns — currently you can only undo the last set of changes | Windsurf (multi-step Cascade undo) |
| **LSP / symbol context** | `navigateToSymbol` does a plain-text word search in the workspace | True IDE language-server integration for go-to-definition and find-references, so symbol links in agent responses resolve precisely | Continue.dev, Cursor (tree-sitter / LSP indexing) |

---

## Not started

| Feature | Description | Seen in |
|---------|-------------|---------|
| **Inline per-hunk accept/reject** | Agent edits appear as highlighted lines directly inside the editor (Cursor-style), with per-block Accept / Reject inline rather than a separate popup dialog | Cursor, Windsurf, GitHub Copilot (recent), Zed |
| **Background / async agents** | Send a task and keep coding while the agent works; get notified when it finishes rather than waiting for the response before the UI unblocks | Windsurf (background Cascade), OpenCode |
| **Test loop integration** | After agent edits, automatically run the project's tests and feed any failures back to the agent so it can fix them without manual copy-paste of error output | Claude Code, Cursor (agent mode), Windsurf |
| **Semantic codebase search** | Before answering, automatically search the workspace for relevant files and symbols and include them in the agent's context — similar to Cursor's `@codebase`, using ripgrep / AST matching, no vector DB | Cursor (`@codebase`), Continue.dev (`@codebase`), GitHub Copilot (workspace agent), Windsurf |
| **Project-scoped custom commands** | Define prompt templates in `.jiuwenswarm/commands/*.md`; they appear as `/command-name` shortcuts in the chat input, letting teams build repeatable workflows (code review, changelog, etc.) | Claude Code (skills / slash commands), Continue.dev (custom slash commands), Cursor |
| **Cost budget limits** | Set a per-session cost ceiling; the plugin warns and pauses before the agent exceeds it — cost tracking already exists in the stats bar, enforcement does not | — |
| **Conversation export** | Export the full chat session as Markdown or JSON with a single action — for sharing, filing a bug report, or keeping an audit trail of what the agent did | Claude.ai (export), some Continue.dev community extensions |
| **OS notifications for long tasks** | Desktop notification when a long agent run completes while the IDE is not focused — same pattern as build-finished notifications | Claude Code (terminal bell), native IDE build notifications |
| **Voice input** | Microphone button in the chat input; browser Speech API transcribes speech to text in the input field | GitHub Copilot Voice (separate extension) |
| **Inline ghost text completions** | Sub-50 ms code completions appearing as ghost text while typing in the editor, separate from the chat panel | GitHub Copilot, Continue.dev (tab autocomplete), Cursor, Windsurf (Supercomplete), Tabnine, Codeium |
| **Web search in context** | Type `@web` in the chat input to search the internet and pull the results into what the agent sees — useful for error messages you've never encountered, library versions, or recent API changes the model may not know about | Continue.dev (`@web`), Cursor (`@web`), Perplexity integrations |
| **Documentation lookup** | Paste a URL to any documentation site and ask questions about it; the plugin fetches and indexes the page so you can say "how do I configure authentication in this library?" without copy-pasting from the docs yourself | Continue.dev (`@docs`), Cursor (`@docs`) |
| **Terminal output in chat** | A button that copies whatever is currently in your terminal into the chat with one click — instead of manually highlighting and pasting a stack trace or command output, the agent sees exactly what happened | Continue.dev (`@terminal`), Claude Code (reads terminal context), Cursor |
| **Smart commit message from diff** | Instead of using your last chat message as the commit message, the agent reads the actual code changes and writes a proper commit message describing what changed and why — more accurate than the current "prefix last message with AI:" approach | GitHub Copilot (commit message suggestion), Cursor, Continue.dev, GitLens AI |
| **Explain / Generate tests / Add docstrings right-click actions** | Select any function or block of code, right-click, and choose what you want: "Explain this", "Write tests for this", or "Add documentation comments" — sends the selection to the agent with the right prompt pre-filled, without opening the chat manually | GitHub Copilot (`/explain`, `/tests`, `/doc`), Continue.dev, Cursor (editor actions), Amazon CodeWhisperer |
| **Pinned context files** | Mark files you always want the agent to see regardless of what you are currently editing — for example, pin your database schema or main config file so the agent always knows the data model even when you are working in a completely different part of the codebase | Continue.dev (always-on context), Cursor (pinned files), Windsurf (Notepads) |
| **Persistent memory across sessions** | The agent remembers facts about your project between conversations — things like coding conventions, architecture decisions, or team rules — so you do not have to repeat them every session; facts are stored in a project-level file you can edit or clear | Claude Code (`/memory`), Cursor (`.cursorrules` / Rules for AI), Continue.dev (system message) |
| **Per-turn model selection** | Switch between a fast cheap model for quick questions and a powerful model for complex tasks within the same session, per message rather than per session | Continue.dev, OpenCode, Zed |
| **PR / diff review mode** | Load the diff between two branches or paste a pull request and ask the agent to review it: find bugs, suggest improvements, check for security issues, or summarise what changed — produces a structured review rather than a chat answer | GitHub Copilot (code review), GitLens AI, Amazon CodeWhisperer (security review) |
| **Batch fix all errors in file** | A single action that collects every error and warning in the current file and asks the agent to fix them all at once, instead of using Alt+Enter one error at a time | Cursor (agent mode), Windsurf (Cascade) |

---

## WOW — genuinely novel, no direct competitor equivalent

These do not exist in any tool today. Each would be a demo-stopping moment.

| Feature | Description | Why it is different |
|---------|-------------|---------------------|
| **Live swarm map** | A sidebar panel that shows all active sub-agents as named "lanes" running in parallel — which file each agent is currently editing, which tool call it just made, whether it is waiting or running. You can click any lane to see its conversation, pause it, redirect it with a message, or merge two lanes. Unique because the server already has team-mode multi-agent sessions; this just makes the swarm visible and steerable in real time. | Competitors run at most one background agent. Nobody shows you the swarm working — you get a spinner. This turns the product name into a literal UI metaphor. |
| **Blast radius preview** | Before you press Save, a side pane appears (can be opt-in) that shows everything in the codebase that imports or calls what you just changed — a live dependency graph with "X callers, Y tests, Z downstream modules". No code is run; it is pure static analysis on every keystroke. If the blast radius is zero, you see green. If it is large, you see a flame icon. Clicking any node navigates to that file. | Linters catch syntax errors. Type checkers catch type errors. Nothing tells you the *impact* of a change before you save it. This is the first "consequence preview" for live editing. |
| **AI git archaeology — "why does this line exist?"** | Right-click any line → "Why was this written this way?". The agent traces git blame → commit message → PR description → linked ticket → any recorded conversation history that touched this line, and produces a one-paragraph human explanation: "This null check was added after a production incident on March 3rd where `user.profile` was null during OAuth token refresh. The team chose Optional over throwing because…" | Git blame tells you *who* and *when*. This tells you *why*. For large codebases with high turnover this is the single most useful thing you can ask about any line of code. Nothing else does this. |
| **Sketch → architecture, then implement** | Before a single line of code is written, describe a feature in plain English and the agent generates an editable architecture diagram (Mermaid or Excalidraw) showing which existing components it touches, what new components are needed, and the data flow. You drag, drop, or annotate the diagram to express your intent, then click "Implement this" and the agent uses the diagram as its spec. | Every tool starts coding immediately. This puts a thinking step first — visible, shareable, correctable. The diagram becomes the single source of truth the swarm uses as a plan. |
| **Morning briefing** | When you open the IDE, a one-time card appears summarising everything that changed since you last had it open: commits merged by teammates, PRs waiting for your review, CI failures, and (if connected) production error spikes — all in plain English, in under ten seconds. No dashboard to open, no browser tabs. | Every tool is reactive — you have to ask. This is the first proactive "here is what you missed" without leaving the IDE. The server already has session history and git access; the briefing is a synthesis task on open. |
| **Proactive watchdog** | The agent silently observes your edits in the background. When it detects something it is confident about — a SQL injection, a race condition, you duplicating a utility that already exists elsewhere in the repo — it places a subtle badge on the affected line and a soft notification in the chat panel. Sensitivity is a slider: "security issues only" at one end, "anything suspicious" at the other. You are never blocked; you can dismiss it. | Linters and static analysis tools catch rule violations. This catches *semantic* problems: "you just re-implemented `utils/debounce.ts:42`" or "this lock is acquired but never released in the error path". It acts only when confident and never interrupts your flow. |

# Live Swarm Map — Development Plan

Visual real-time panel showing all active sub-agents, their current task, and tool activity
while a JiuwenSwarm team session runs inside the IDE.

---

## What already exists (no new server work needed)

The backend already emits a rich event stream over the same WebSocket the IDE plugin already
listens on. Every event arrives as a regular WebSocket message with content starting with
`team.event:` followed by a JSON envelope.

| Already in backend | Wire event name | What it carries |
|--------------------|-----------------|-----------------|
| Member joins | `team.member.spawned` | `member_name`, `display_name`, `role` |
| Member status changes | `team.member.status_changed` | `status` (READY / BUSY / PAUSED / SHUTDOWN) |
| Execution state changes | `team.member.execution_changed` | `execution_status` (IDLE / RUNNING / COMPLETING) |
| Task claimed by member | `team.task.claimed` | `task_id`, `title`, `assignee` |
| Task started | `team.task.started` | `task_id`, `assignee` |
| Task done | `team.task.completed` | `task_id`, `assignee` |
| Member-to-member message | `team.message.p2p` | `from_member`, `to_member`, `content` |
| Broadcast message | `team.message.broadcast` | `from_member`, `content` |
| Member leaves | `team.member.shutdown` | `member_name` |

All of these flow through the existing `onJiuwenMessage` / `convertServerMessageToLegacyEvent`
pipeline in both plugins. They are currently forwarded to the webview as generic
`jiuwen_event` messages and rendered as flat chat text. No IDE-level processing is done.

---

## Target experience (one paragraph)

When the user sends a message in `code.team` mode, a panel slides open on the right side
(JetBrains: a new Tool Window; VS Code: a Webview sidebar). Each active sub-agent appears
as a vertical "lane" with its name, a live status badge, and a one-line description of
what it is doing right now ("writing auth.service.ts · line 47", "running pytest",
"reading README.md"). A compact task board sits at the top showing all team tasks and who
owns each. When an agent finishes, its lane fades and shows a checkmark. The user can click
any lane to jump to that agent's conversation sub-thread. Nothing is blocked — the main
chat panel continues to work normally alongside the swarm map.

---

## Architecture

```
Server  →  WebSocket  →  plugin (Kotlin / TypeScript)
                              │
                   ┌──────────┴──────────┐
                   │                     │
             Chat webview          Swarm Map panel
           (unchanged)          (new, receives swarm
                                 state from plugin)
```

The plugin layer (not the webview) owns the swarm state. It maintains a small in-memory
map of agent lanes, updates it on every `team.event:` message, and pushes a full
snapshot to the Swarm Map panel after each update. The panel is a thin renderer — it
never talks to the server directly.

---

## Data model (plugin layer, shared between JetBrains and VS Code)

```
SwarmState
  sessionId: String
  teamName: String
  lanes: Map<memberName, AgentLane>
  tasks: List<TeamTask>
  lastEventAt: Instant

AgentLane
  memberName: String        // "alice"
  displayName: String       // "Alice — Researcher"
  role: String              // LEADER | TEAMMATE | WORKER
  status: String            // READY | BUSY | PAUSED | SHUTDOWN
  executionStatus: String   // IDLE | RUNNING | COMPLETING
  currentTaskId: String?
  currentTaskTitle: String?
  currentActivity: String?  // "writing auth.service.ts", "running pytest", etc.
  lastToolCall: String?     // tool name from last chat.tool_call event
  lastActiveAt: Instant
  messageCount: Int
  tasksDone: Int

TeamTask
  taskId: String
  title: String
  status: String            // pending | in_progress | completed | cancelled
  assignee: String?
```

---

## Implementation plan

### Phase 1 — Event parsing and state in the plugin (no UI yet)

**Files to change:**

`ChatToolWindow.kt` (JetBrains) / `ChatPanel.ts` (VS Code)

1. Add a `SwarmStateManager` class (one per plugin type) that holds a `SwarmState` in memory.
2. In `onJiuwenMessage`, before the existing conversion pipeline, intercept any message
   whose raw content starts with `team.event:`. Parse the JSON and call
   `swarmStateManager.apply(event)` which updates the appropriate lane or task.
3. After each `apply()` call, post the full `SwarmState` snapshot to the Swarm Map panel
   (even if the panel is not yet open — messages are buffered or dropped silently if no
   panel is listening).

`SwarmStateManager` methods:
- `apply(event)` — fan-out to `onMemberSpawned`, `onMemberStatusChanged`,
  `onTaskClaimed`, `onTaskCompleted`, etc.
- `onToolCall(memberName, toolName, filePath?)` — called from `chat.tool_call` events
  that already flow through the webview pipeline; the member name is in the event payload.
- `snapshot(): SwarmState` — returns current state for the panel.
- `reset()` — called when the session changes.

**Verification:** Add debug logging of SwarmState after each event. Run a team session,
confirm lanes are created and updated correctly before building any UI.

---

### Phase 2 — Swarm Map panel (JetBrains)

**New files:**
- `SwarmMapToolWindow.kt` — registers a JetBrains Tool Window with `ToolWindowFactory`.
- `SwarmMapPanel.kt` — a JCEF browser that loads `swarm_map.html` from plugin resources.
- `swarm_map.html` — self-contained HTML/CSS/JS, no framework.

**Tool Window registration (plugin.xml):**
```xml
<toolWindow id="JiuwenSwarm Map"
            anchor="right"
            factoryClass="com.jiuwenswarm.plugin.ui.SwarmMapToolWindowFactory"
            icon="/icons/swarm.svg"
            canCloseContents="false"/>
```

The tool window opens automatically when `SwarmStateManager` receives its first
`team.member.spawned` event (i.e. the swarm starts). It closes or shows an empty state
card when all lanes reach SHUTDOWN status.

**Message protocol between `SwarmMapPanel.kt` and `swarm_map.html`:**

Kotlin → JS (via JCEF `executeJavaScript`):
```json
{ "type": "swarm_snapshot", "state": { ...SwarmState... } }
```

JS → Kotlin (via the existing `jsQuery` mechanism):
```json
{ "type": "open_lane", "memberName": "alice" }
```
Opening a lane triggers `SwarmMapPanel` to tell `ChatToolWindow` to filter the chat to
that agent's sub-thread (future work; Phase 3).

**`swarm_map.html` rendering rules:**
- One card per AgentLane, sorted: LEADER first, then BUSY lanes, then READY, then SHUTDOWN.
- Status badge colours: BUSY = green pulse, READY = grey, PAUSED = amber, SHUTDOWN = dimmed.
- Current activity line: `lastToolCall + " · " + currentActivity` (e.g. "write_file · auth.service.ts").
- Task board at top: small pill per task, colour by status. Assignee initials in the pill.
- Idle timer: if `(now - lastActiveAt) > 30s` AND status is BUSY, show amber warning.
- Clicking a card emits `open_lane` message (no-op until Phase 3).
- Full re-render on every `swarm_snapshot` (simple; state is small).

---

### Phase 2b — Swarm Map panel (VS Code)

**New files:**
- `SwarmMapPanel.ts` — a `vscode.WebviewPanel` (separate from `ChatPanel`).
- Reuses the same `swarm_map.html` (copy to `resources/swarm_map.html` during build).
- Messages sent via `panel.webview.postMessage(snapshot)`.

**Panel lifecycle:**
- Created on first `swarm_snapshot` if the panel does not exist.
- Revealed (brought to front) on first agent BUSY event.
- Closed automatically when `SwarmStateManager.reset()` is called.

**VS Code message handler** (JS → extension host):
```typescript
window.addEventListener('message', (e) => {
    if (e.data.type === 'open_lane') {
        vscode.postMessage({ type: 'swarm_open_lane', memberName: e.data.memberName });
    }
});
```

---

### Phase 3 — Interactive controls (after Phase 2 is stable)

**Redirect an agent:**
- "Redirect" button on each lane card → small text input overlay → user types a message →
  plugin calls a new `session.send_to_member(memberName, message)` RPC (server already
  supports member-targeted messages via the `MESSAGE` event type).

**Pause / resume a lane:**
- Button triggers `session.pause_member(memberName)` / `session.resume_member(memberName)`.
- Server already has `PAUSED` / `STOPPED` status transitions.

**Merge lanes:**
- "Merge" selects two lanes → plugin calls `session.merge_members(a, b)` → server
  consolidates their task queues (server work required — flag this as out of scope for
  Phase 3, mark as future server feature).

---

## File change summary

| File | Change |
|------|--------|
| `ChatToolWindow.kt` | Add `swarmStateManager`; intercept `team.event:` in `onJiuwenMessage`; auto-open tool window on first swarm event |
| `ChatPanel.ts` | Same as above for VS Code |
| `SwarmStateManager.kt` | New: in-memory state, event fan-out, snapshot serialisation |
| `SwarmStateManager.ts` | New: same for VS Code (or share logic via a common JSON model) |
| `SwarmMapToolWindow.kt` | New: JetBrains Tool Window factory + JCEF panel |
| `SwarmMapPanel.ts` | New: VS Code WebviewPanel wrapper |
| `resources/swarm_map.html` | New: shared HTML panel renderer |
| `plugin.xml` | Register the new Tool Window |
| `package.json` (VS Code) | Register the new webview container if needed |

No server changes required for Phase 1 and Phase 2.

---

## Demo screenshot plan

### Scene

**JetBrains IntelliJ IDEA, dark Darcula theme.**
The screen is split: left two-thirds shows the editor with `auth.service.ts` open,
right one-third shows the Swarm Map tool window.

### Left panel — editor

File: `auth.service.ts`. Three blocks of code are highlighted in different colours:
- Lines 12–24: **green** tint (just written by "Backend Dev" agent) — a `refreshToken()` method.
- Lines 44–51: **amber** tint (currently being edited by "Backend Dev") — a `validateJwt()` stub with a blinking cursor ghost.
- A small inline gutter badge on line 44: avatar initials `BD` in green, tooltip "Backend Dev — writing".

### Right panel — Swarm Map tool window

Title bar: "JiuwenSwarm · research_team · 3 agents · 2 tasks active"

**Task board (top strip, 3 pills):**
```
[ ⚙ Implement auth endpoints  →  Backend Dev  IN PROGRESS ]
[ ⚙ Write auth unit tests     →  Test Engineer  IN PROGRESS ]
[ ✓ Design auth schema        →  Architect  DONE ]
```

**Agent lanes (3 cards, vertically stacked):**

```
┌─────────────────────────────────────────────────────────┐
│  👑  Architect                              READY  ████░ │
│      Monitoring · 0 active tasks                        │
│      Last active 18s ago                                │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  ●  Backend Dev                          ● BUSY  ██████ │  ← green pulse
│      write_file · auth.service.ts:44                    │
│      Task: Implement auth endpoints · 2 min             │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  ●  Test Engineer                        ● BUSY  ██████ │  ← green pulse
│      bash · pytest tests/auth/ --tb=short               │
│      Task: Write auth unit tests · 45s                  │
└─────────────────────────────────────────────────────────┘
```

**Bottom strip:** mini timeline bar, last 90 seconds, showing overlapping coloured
segments per agent — visual proof that all three ran in parallel, not sequentially.

### Chat panel (collapsed to a tab at the bottom)

Just visible enough to see the last user message:
> "Implement a complete JWT authentication system with refresh token support"

and below it one team leader reply:
> "Dispatched to 2 agents. Architect approved the schema. Backend Dev and Test Engineer are now running in parallel."

### What this screenshot communicates instantly

1. **You asked one thing.** Two agents are working *simultaneously* on different parts of it.
2. **You can see exactly what each is doing** — which file, which line, which tool call.
3. **Tests are being written at the same time as implementation.** This has never been visible in an IDE before.
4. **It is inside the IDE.** No browser tab, no separate dashboard.

### Caption for marketing

> "Ask once. The swarm handles the rest — in parallel, in your IDE, in real time."

---

## Open questions before starting

1. Does the server send `member_name` inside `chat.tool_call` events so we can attribute
   tool calls to lanes? (If not, we need a small server addition: add `member_name` field
   to tool call events in team mode.)
2. Does `code.team` mode always produce `team.event:` messages, or only when the swarm
   has more than one member? Need to confirm the wire format with a live test.
3. For the gutter badges in the editor (lines highlighted per agent) — this requires IDE-
   level editor integration (JetBrains `RangeHighlighter`, VS Code `TextEditorDecorationType`).
   Scope this as a Phase 2.5 stretch goal, not part of Phase 2.

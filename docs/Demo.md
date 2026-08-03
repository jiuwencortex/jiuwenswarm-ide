## JiuwenSwarm IDE Plugin — Demo Scenario

---

### 0. Prerequisites (already done)

- JiuwenSwarm server is running (WebSocket endpoint reachable, e.g. `ws://localhost:8765`)
- PyCharm is open with a project that has some Python files
- The JiuwenSwarm plugin is installed (visible in *Settings → Plugins*)

---

### 1. Open the Chat panel

1. In the right sidebar click **JiuwenSwarm** (or *View → Tool Windows → JiuwenSwarm*).
2. The chat panel loads. The status bar at the bottom of PyCharm shows the connection state.
3. If the server is reachable the status indicator turns green / shows "Connected".

---

### 2. Start a team session

In the chat input box type a request that invokes multi-agent mode. Example:

```
/code.team Refactor the payment module: split billing logic from invoice
generation, add unit tests for both, and update the README.
```

Press **Enter** (or the send button).

> The `/code.team` prefix (or equivalent mode flag your server recognises) tells the gateway to spawn a swarm instead of a single agent.

---

### 3. Watch the Swarm Map appear

Within a second or two the first `team.member.spawned` event arrives.

- The **Swarm Map** tool window pops open automatically at the bottom of PyCharm.
- A lane card appears for the first agent (e.g. **Planner**) with a green pulsing dot and role badge.
- The **header chip** (`0/3 tasks · 1 agent`) updates immediately.

---

### 4. Observe agents spawning

As more members join you see additional lane cards:

| Lane | Role | Status | Activity |
|------|------|--------|----------|
| planner | LEADER | BUSY | — |
| coder-1 | TEAMMATE | READY | — |
| tester | TEAMMATE | READY | — |

The leader's dot pulses green. Teammates show grey dots until they pick up tasks.

---

### 5. Watch tasks flow through the board

Once the planner emits `team.task.created` events:

- **Task pills** appear at the top of the Swarm Map: yellow for *pending*, green for *in_progress*, grey for *completed*.
- The progress chip updates: `1/3 tasks · 3 agents`.

When `team.task.started` fires for **coder-1**:
- Its lane card border turns bright green (BUSY).
- `Task: Split billing logic from invoice` appears under its name.

---

### 6. Watch file-level activity

As **coder-1** calls tools (`read_file`, `str_replace_editor`, `write_file`):

- The **activity line** under its name updates live: `editing · billing.py`, `writing · invoice.py`, etc.
- The lane card gains the `.has-path` class — hovering over it shows the **↗ open file** hint.

**Click the lane card** → PyCharm opens `billing.py` at the top of the editor and moves focus there.

---

### 7. Watch inter-agent messages

When the planner sends a subtask description to coder-1 via `team.message.*`:

- At the bottom of the Swarm Map a **Messages (3)** toggle appears.
- Click the **▶ Messages** toggle to expand the log.
- Rows show: `planner → coder-1 · implement split_billing(order) keeping…`
- The sender name is colour-coded to match their lane dot colour.

---

### 8. Observe the tester lane

While coder-1 is writing, **tester** claims its task:

- Second pill turns green: `2/3 tasks`.
- tester's activity shows `running · pytest tests/test_billing.py`.

---

### 9. Session completes — summary card

When all agents send `team.member.shutdown`:

- All lane dots turn grey and fade (opacity 45%).
- Live lane cards are replaced by the **summary card**:

```
✓ PaymentRefactor Team · Session complete
Agents            3
Tasks completed   3
Messages          12
```

The progress chip shows `3/3 tasks`.

---

### 10. What to highlight during the demo

| What to show | Where to look |
|---|---|
| Automatic panel open | Swarm Map appears without manual click |
| Real-time status dots | Pulsing green vs. grey vs. amber idle |
| File navigation | Click lane card → editor jumps to file |
| Progress chip | Header: `done/total tasks · N agents` |
| Task pills | Colour changes: yellow → green → grey |
| Inter-agent messages | Collapsible log at bottom of Swarm Map |
| Summary card | Replaces lanes when session ends |
| Idle warning | Amber `⚠ idle 35s` if BUSY agent goes quiet |

---

### Common issues during demo

| Symptom | Fix |
|---|---|
| Status stays "Disconnected" | Check server URL in *Settings → Tools → JiuwenSwarm* |
| Swarm Map never opens | Confirm server sends `team.member.spawned` events; check plugin log (*Help → Show Log*) |
| Lane cards appear but no file activity | `member_name` field may be missing from server's `chat.tool_call` payload |
| Messages toggle never appears | Confirm server sends `team.message.*` events with `content` field |
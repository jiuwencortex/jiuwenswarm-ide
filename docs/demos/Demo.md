# JiuwenSwarm IDE Plugin — Demo Scenario

A self-contained walkthrough. The only assumption is that the JiuwenSwarm
server process is already running. No code, no project, no files are needed
in advance — the swarm builds everything from zero.

---

## 0. Before you start

| What | Required state |
|------|---------------|
| JiuwenSwarm server | Running (note the WebSocket URL, e.g. `ws://localhost:8765`) |
| PyCharm | Installed, JiuwenSwarm plugin installed |
| Demo project | **Empty directory** — create a new folder anywhere, e.g. `~/demo-swarm` |

---

## 1. Open the empty project in PyCharm

1. *File → Open* → select `~/demo-swarm`.
2. Confirm it is empty in the *Project* sidebar (left panel). No files, no `src/`, nothing.

> This is the starting point. The audience sees a blank canvas.

---

## 2. Configure the server connection (first run only)

1. Open *Settings (⌘,) → Tools → JiuwenSwarm*.
2. Set **Server URL** to the WebSocket address the server is listening on.
3. Click **OK**.

Skip this step on subsequent runs — the setting is persisted.

---

## 3. Open the Chat panel

1. Click **JiuwenSwarm** in the right sidebar strip, or go to
   *View → Tool Windows → JiuwenSwarm*.
2. The chat panel slides open on the right side of the IDE.
3. The status bar at the very bottom of PyCharm shows the connection state.
   Wait until it reads **Connected** (green dot or similar indicator).

> If it stays "Disconnected": check the server URL in Settings and confirm
> the server process is up.

---

## 4. Send the demo prompt

Click the chat input box and paste this prompt exactly:

```
Build a Python CLI task manager from scratch. The project should have:
1. tasks.py — a module with add_task, list_tasks, complete_task, delete_task
   functions, persisting tasks to tasks.json
2. cli.py — a thin CLI wrapper using argparse that exposes add / list /
   complete / delete subcommands
3. tests/test_tasks.py — unit tests covering all four operations, using
   only stdlib (no pytest required)
4. README.md — usage examples for every subcommand

Start with a plan, then write each file, run the tests, and confirm they pass.
```

Press **Enter** (or click the send button).

---

## 5. Swarm Map appears automatically

Within 1–2 seconds the first `team.member.spawned` event arrives from the server.

- The **JiuwenSwarm Swarm** tool window pops open at the **bottom** of PyCharm
  without any manual action. It opens in **Map view** by default.
- A glowing **node** appears for the first agent — typically the **Planner** — with:
  - A **pulsing ring** (actively working)
  - A friendly status line under it: *Planning · 0:0X*
- The **header chip** in the top-right of the Swarm Map shows: `0/4 tasks · 1 agent`

> Point out: nobody clicked anything. The panel opened on its own when the
> first agent joined the session. Toggle **Map / List** in the header to flip
> between the interactive map and the technical per-agent lane cards.

---

## 6. More agents spawn

Two or three more agents join within seconds. New **nodes** appear on the map, and the
**List view** shows their lane cards:

| Lane | Status | What it will do |
|------|--------|-----------------|
| planner | WORKING (pulse) | Decompose the request into tasks |
| coder | IDLE | Write tasks.py and cli.py |
| tester | IDLE | Write and run tests/test_tasks.py |
| writer | IDLE | Write README.md |

The progress chip updates to `0/4 tasks · 4 agents`.

---

## 7. Tasks appear as pills

The planner emits `team.task.created` events for each work item. Four pills
appear across the top of the Swarm Map:

- **yellow** pills: *pending* tasks waiting to be claimed
- As agents claim and start them, the pill turns **green** (in_progress)
- When finished, the pill turns **grey** (completed)

The progress chip counts up: `1/4 tasks`, `2/4 tasks`, …

---

## 8. Live file activity on each lane card

As **coder** starts writing:

- Its node/ring pulses green and the status word changes to **Writing**.
- The **activity feed** on the card (List view) updates in real time:
  - `reading · tasks.json` when it inspects the schema
  - `writing · tasks.py` when it creates the file
  - `editing · tasks.py` when it refines a function

**At this point, hover over the coder lane card.** Because it has an active file,
the hint **↗ open file** appears in the top-right corner of the card.

**Click the lane card** → PyCharm opens `tasks.py` directly in the editor and
moves focus to it. The audience can read the code the agent just wrote.

Go back to the Swarm Map and do the same for the tester lane once it is active
— clicking it jumps to `tests/test_tasks.py`.

---

## 9. Inter-agent messages

When the planner sends a task description to coder:

- A **Messages (N)** toggle appears at the bottom edge of the Swarm Map panel.
- Click **▶ Messages** to expand the log.
- Rows appear in the format:

  ```
  planner  →  coder    implement add_task(title) writing to tasks.json…
  planner  →  tester   write unit tests for all four task operations…
  coder    →  tester   tasks.py is ready, file is at /…/tasks.py
  ```

- Each sender name is colour-coded to match the dot on their lane card.
- The log scrolls automatically to the most recent message.

Collapse the log again by clicking **▼ Messages**.

---

## 10. Tests run

The tester lane shows `running · tests/test_tasks.py` in its activity line,
indicating the agent has called a shell command to execute the test suite.

Once the tests pass, the tester's task pill turns grey (completed) and its
tasksDone counter increments internally.

---

## 11. Session completes — summary card

When every agent has finished and sends `team.member.shutdown`:

- All node/lane statuses turn to **Done** (✓ / grey).
- The live lanes are **replaced** by the summary card:

  ```
  ✓ TaskManager Team · Session complete
  Agents              4
  Tasks completed     4
  Messages exchanged  9
  planner  · 0:12
  coder    · 0:58
  tester   · 0:41
  writer   · 0:09
  ```

- The progress chip shows **4/4 tasks** and the progress bar under the header fills.

Now look at the *Project* sidebar — files that didn't exist five minutes ago:

```
demo-swarm/
├── tasks.py
├── cli.py
├── tasks.json          ← created on first run by the agent
├── README.md
└── tests/
    └── test_tasks.py
```

Open any of them in the editor to show the audience real, working code.

---

## 12. Optional: try the CLI

Open the PyCharm **Terminal** tab and run the app the swarm built:

```bash
python cli.py add "Buy milk"
python cli.py add "Write demo docs"
python cli.py list
python cli.py complete 1
python cli.py list
python cli.py delete 2
```

Then run the tests:

```bash
python -m unittest tests/test_tasks.py -v
```

---

## What to highlight at each stage

| Moment | What to point at |
|--------|-----------------|
| Server sends first event | Swarm Map opens — no button click needed |
| Map view nodes | Agents as a living map, not rows — pulse = working |
| Map / List toggle | One click to flip between the map and the technical lanes |
| Status word + timer | Friendly status (Planning / Writing / Done) with a live elapsed counter |
| Activity feed updates | Sub-second granularity on what the agent is doing |
| Hover → ↗ open file | One click to jump into the file the agent last touched |
| Click lane card | PyCharm focus moves to that exact file instantly |
| Messages toggle | Agents coordinating with each other, not just user ↔ agent |
| Message colour coding | Matches lane colour — easy to trace who said what |
| Progress chip / bar | Always visible: `done/total tasks · N agents` + completion bar |
| Task pills | Colour shift from yellow to green to grey tells the story |
| Debug log (☰ menu) | Opt-in raw event feed for troubleshooting |
| Summary card | Clean end state — results visible, per-agent durations |
| Project sidebar | Empty dir → 5 files with working code |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Status bar stays "Disconnected" | Server not running or wrong URL | Check *Settings → Tools → JiuwenSwarm* |
| Chat panel loads but no response | Server up but agent pool not started | Check server logs |
| Swarm Map never opens | Server not sending `team.member.spawned` | Verify server emits team events; check *Help → Show Log* in PyCharm |
| Lane cards show but no file activity | `member_name` missing from `chat.tool_call` payload | Check server event schema |
| Messages toggle never appears | `team.message.*` events not emitted, or `content` field missing | Check server event schema |
| ↗ open file hint absent | Agent ran no file-touching tools yet | Wait for first read/write tool call |
| File navigation does nothing | File path in event doesn't match local filesystem mount | Check server sends absolute paths matching the local project root |

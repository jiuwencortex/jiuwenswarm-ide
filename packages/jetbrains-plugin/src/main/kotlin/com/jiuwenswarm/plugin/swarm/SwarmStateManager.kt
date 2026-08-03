package com.jiuwenswarm.plugin.swarm

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import java.io.File

private val LOG = logger<SwarmStateManager>()
private val gson = Gson()

/**
 * Maintains in-memory swarm state built from team event deltas that arrive
 * over the WebSocket as "team.event:{json}" prefixed chat.delta messages.
 *
 * All public methods are @Synchronized — onJiuwenMessage runs on a background
 * thread while postSnapshot reads may happen on any thread.
 */
class SwarmStateManager {

    private var sessionId: String = ""
    private var teamName: String = ""
    private val lanes = LinkedHashMap<String, AgentLane>()   // insertion order = join order
    private val tasks = LinkedHashMap<String, TeamTask>()
    private val messages = ArrayDeque<TeamMessage>()          // ring buffer, max 50
    @Volatile private var lastEventAt: Long = 0L

    // ──────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────

    /**
     * Parse the JSON string that follows "team.event:" and apply it to state.
     * Expected shape: { "event": { "type": "team.member.spawned", ... } }
     */
    @Synchronized
    fun applyTeamEvent(json: String) {
        runCatching {
            val root = gson.fromJson(json, JsonObject::class.java)
            val event = normalizeEvent(root.getAsJsonObject("event") ?: root)  // tolerate flat shape
            val type = event.get("type")?.asString ?: return
            lastEventAt = event.get("timestamp")?.asLong ?: System.currentTimeMillis()

            // Capture team name from the first event that carries it
            event.get("team_name")?.asString?.let { if (teamName.isEmpty()) teamName = it }

            when {
                type == "team.member.spawned"          -> onMemberSpawned(event)
                type == "team.member.status_changed"   -> onMemberStatusChanged(event)
                type == "team.member.execution_changed"-> onMemberExecutionChanged(event)
                type == "team.member.shutdown"         -> onMemberShutdown(event)
                type == "team.task.created"            -> onTaskCreated(event)
                type == "team.task.claimed"            -> onTaskClaimed(event)
                type == "team.task.started"            -> onTaskStarted(event)
                type == "team.task.completed"          -> onTaskCompleted(event)
                type == "team.task.cancelled"          -> onTaskCancelled(event)
                type == "team.task.status_snapshot"    -> onTaskStatusSnapshot(event)
                type.startsWith("team.message.")       -> onMessage(event)
                else -> LOG.debug("SWARM → unhandled event type: $type")
            }
        }.onFailure { LOG.warn("SWARM → failed to apply team event: ${it.message}") }
    }

    /**
     * Map the gateway's team-event field names to the shape this manager consumes.
     * The gateway sends member_id/name/mode/team_id; the lanes here use
     * member_name/display_name/role/team_name. Runs before dispatch so every
     * handler below reads one consistent vocabulary.
     */
    private fun normalizeEvent(raw: JsonObject): JsonObject {
        val e = raw.deepCopy()
        if (e.has("member_id") && !e.has("member_name")) {
            e.get("member_id")?.takeIf { it.isJsonPrimitive }?.asString?.let { e.addProperty("member_name", it) }
        }
        if (e.has("name") && !e.has("display_name")) {
            e.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.let { e.addProperty("display_name", it) }
        }
        if (e.has("mode") && !e.has("role")) {
            val mode = e.get("mode")
            if (mode != null && mode.isJsonPrimitive) {
                e.addProperty("role", mode.asString.uppercase())
            } else if (mode != null) {
                e.add("role", mode)
            }
        }
        if (e.has("team_id") && !e.has("team_name")) {
            e.get("team_id")?.takeIf { it.isJsonPrimitive }?.asString?.let { e.addProperty("team_name", it) }
        }
        return e
    }

    /** Uppercase a lane status into the map's vocabulary (READY/BUSY/PAUSED/SHUTDOWN). */
    private fun normalizeLaneStatus(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        val up = s.uppercase()
        return if (up == "IDLE") "READY" else up
    }

    /**
     * Called from the chat.tool_call branch of onJiuwenMessage.
     * memberName may be null if the server does not include it in the payload.
     */
    @Synchronized
    fun applyToolCall(toolName: String, filePath: String?, memberName: String?) {
        val name = memberName ?: return   // cannot attribute without a member name
        val lane = lanes.getOrPut(name) { stubLane(name) }
        lane.lastToolName = toolName
        lane.currentActivity = formatActivity(toolName, filePath)
        lane.lastActivePath = filePath
        lane.lastActiveAt = System.currentTimeMillis()
        lastEventAt = lane.lastActiveAt
    }

    @Synchronized
    fun snapshot(): SwarmSnapshot {
        val sortedLanes = lanes.values.sortedWith(
            compareBy(
                { statusPriority(it.status) },
                { -it.lastActiveAt }
            )
        )
        val sortedTasks = tasks.values.sortedWith(
            compareBy(
                { taskStatusPriority(it.status) },
                { it.createdAt }
            )
        )
        return SwarmSnapshot(
            sessionId = sessionId,
            teamName = teamName,
            lanes = sortedLanes,
            tasks = sortedTasks,
            messages = messages.toList(),
            lastEventAt = lastEventAt,
        )
    }

    @Synchronized
    fun reset(newSessionId: String) {
        sessionId = newSessionId
        teamName = ""
        lanes.clear()
        tasks.clear()
        messages.clear()
        lastEventAt = 0L
    }

    fun isEmpty(): Boolean = lanes.isEmpty()

    // ──────────────────────────────────────────
    // Event handlers
    // ──────────────────────────────────────────

    private fun onMemberSpawned(e: JsonObject) {
        val name = e.get("member_name")?.asString ?: return
        val existing = lanes[name]
        if (existing != null) {
            // Update display info in case of restart
            existing.displayName = e.get("display_name")?.asString ?: existing.displayName
            existing.role = e.get("role")?.asString ?: existing.role
            existing.status = "READY"
            existing.lastActiveAt = lastEventAt
        } else {
            lanes[name] = AgentLane(
                memberName = name,
                displayName = e.get("display_name")?.asString ?: name,
                role = e.get("role")?.asString ?: "TEAMMATE",
                lastActiveAt = lastEventAt,
            )
        }
    }

    private fun onMemberStatusChanged(e: JsonObject) {
        val name = e.get("member_name")?.asString ?: return
        val lane = lanes.getOrPut(name) { stubLane(name) }
        val newStatus = e.get("new_status")?.takeIf { it.isJsonPrimitive }?.asString
            ?: e.get("status")?.takeIf { it.isJsonPrimitive }?.asString
        normalizeLaneStatus(newStatus)?.let { lane.status = it }
        lane.lastActiveAt = lastEventAt
    }

    private fun onMemberExecutionChanged(e: JsonObject) {
        val name = e.get("member_name")?.asString ?: return
        val lane = lanes.getOrPut(name) { stubLane(name) }
        val execution = e.get("new_status")?.takeIf { it.isJsonPrimitive }?.asString
            ?: e.get("execution_status")?.takeIf { it.isJsonPrimitive }?.asString
        if (!execution.isNullOrEmpty()) lane.executionStatus = execution.uppercase()
        lane.lastActiveAt = lastEventAt
    }

    private fun onMemberShutdown(e: JsonObject) {
        val name = e.get("member_name")?.asString ?: return
        val lane = lanes[name] ?: return
        lane.status = "SHUTDOWN"
        lane.executionStatus = "IDLE"
        lane.currentActivity = null
        lane.lastActiveAt = lastEventAt
    }

    private fun onTaskCreated(e: JsonObject) {
        val id = e.get("task_id")?.asString ?: return
        tasks[id] = TeamTask(
            taskId = id,
            title = e.get("title")?.asString ?: id,
            status = "pending",
            createdAt = lastEventAt,
        )
    }

    private fun onTaskClaimed(e: JsonObject) {
        val id = e.get("task_id")?.asString ?: return
        val task = tasks.getOrPut(id) { TeamTask(id, id, "pending") }
        // Gateway names the claimer member_id; normalizeEvent maps it to member_name.
        task.assignee = e.get("assignee")?.asString ?: e.get("member_name")?.asString
        // stays "pending" until started
    }

    /** Convergence event: apply a task's authoritative status/assignee snapshot. */
    private fun onTaskStatusSnapshot(e: JsonObject) {
        val id = e.get("task_id")?.asString ?: return
        val status = e.get("status")?.asString
        val assignee = e.get("assignee")?.asString ?: e.get("member_name")?.asString
        val task = tasks.getOrPut(id) { TeamTask(id, e.get("title")?.asString ?: id, "pending") }
        if (!status.isNullOrEmpty()) task.status = status
        if (!assignee.isNullOrEmpty()) task.assignee = assignee
        task.assignee?.let { memberName ->
            lanes[memberName]?.let { lane ->
                if (task.status == "completed" && lane.currentTaskId == id) {
                    lane.currentTaskId = null
                    lane.currentTaskTitle = null
                    lane.tasksDone++
                    lane.lastActiveAt = lastEventAt
                }
            }
        }
    }

    private fun onTaskStarted(e: JsonObject) {
        val id = e.get("task_id")?.asString ?: return
        val task = tasks.getOrPut(id) { TeamTask(id, id, "pending") }
        task.status = "in_progress"
        task.assignee = e.get("assignee")?.asString ?: task.assignee
        // Link task into the lane
        task.assignee?.let { memberName ->
            lanes[memberName]?.let { lane ->
                lane.currentTaskId = id
                lane.currentTaskTitle = task.title
                lane.lastActiveAt = lastEventAt
            }
        }
    }

    private fun onTaskCompleted(e: JsonObject) {
        val id = e.get("task_id")?.asString ?: return
        val task = tasks[id] ?: return
        task.status = "completed"
        // Clear task from lane
        task.assignee?.let { memberName ->
            lanes[memberName]?.let { lane ->
                if (lane.currentTaskId == id) {
                    lane.currentTaskId = null
                    lane.currentTaskTitle = null
                    lane.tasksDone++
                    lane.lastActiveAt = lastEventAt
                }
            }
        }
    }

    private fun onTaskCancelled(e: JsonObject) {
        val id = e.get("task_id")?.asString ?: return
        val task = tasks[id] ?: return
        task.status = "cancelled"
        task.assignee?.let { memberName ->
            lanes[memberName]?.let { lane ->
                if (lane.currentTaskId == id) {
                    lane.currentTaskId = null
                    lane.currentTaskTitle = null
                    lane.lastActiveAt = lastEventAt
                }
            }
        }
    }

    private fun onMessage(e: JsonObject) {
        val from = e.get("from_member")?.asString ?: return
        val lane = lanes.getOrPut(from) { stubLane(from) }
        lane.messageCount++
        lane.lastActiveAt = lastEventAt
        // Capture message content for the inter-agent message log
        val content = e.get("content")?.asString ?: return
        val to = e.get("to_member")?.asString
        messages.addLast(TeamMessage(from = from, to = to, content = content, timestamp = lastEventAt))
        if (messages.size > 50) messages.removeFirst()
    }

    // ──────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────

    private fun stubLane(memberName: String) = AgentLane(
        memberName = memberName,
        displayName = memberName,
        role = "TEAMMATE",
        lastActiveAt = lastEventAt,
    )

    private fun formatActivity(toolName: String, filePath: String?): String {
        val base = filePath?.let { File(it).name } ?: filePath
        return when (toolName) {
            "write_file", "create_file" -> if (base != null) "writing · $base" else "writing"
            "str_replace_editor"        -> if (base != null) "editing · $base" else "editing"
            "read_file"                 -> if (base != null) "reading · $base" else "reading"
            "bash", "run_command"       -> if (filePath != null) "running · ${filePath.take(40)}" else "running"
            "search_files", "grep"      -> if (base != null) "searching · $base" else "searching"
            else                        -> toolName
        }
    }

    private fun statusPriority(status: String) = when (status) {
        "BUSY"     -> 0
        "RUNNING"  -> 1
        "READY"    -> 2
        "PAUSED"   -> 3
        "SHUTDOWN" -> 4
        else       -> 5
    }

    private fun taskStatusPriority(status: String) = when (status) {
        "in_progress" -> 0
        "pending"     -> 1
        "completed"   -> 2
        "cancelled"   -> 3
        else          -> 4
    }
}

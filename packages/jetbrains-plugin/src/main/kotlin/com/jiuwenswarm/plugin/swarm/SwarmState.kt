package com.jiuwenswarm.plugin.swarm

data class AgentLane(
    val memberName: String,
    var displayName: String,
    var role: String,                         // "LEADER" | "TEAMMATE" | "WORKER"
    var status: String = "READY",             // "READY" | "BUSY" | "PAUSED" | "SHUTDOWN"
    var executionStatus: String = "IDLE",     // "IDLE" | "RUNNING" | "COMPLETING"
    var currentTaskId: String? = null,
    var currentTaskTitle: String? = null,
    var currentActivity: String? = null,      // e.g. "writing · auth.service.ts"
    var lastToolName: String? = null,
    var lastActiveAt: Long = System.currentTimeMillis(),
    var messageCount: Int = 0,
    var tasksDone: Int = 0,
)

data class TeamTask(
    val taskId: String,
    var title: String,
    var status: String,   // "pending" | "in_progress" | "completed" | "cancelled"
    var assignee: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class SwarmSnapshot(
    val sessionId: String,
    val teamName: String,
    val lanes: List<AgentLane>,
    val tasks: List<TeamTask>,
    val lastEventAt: Long,
)

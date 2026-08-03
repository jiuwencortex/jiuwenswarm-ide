import * as path from 'path';
import { AgentLane, TeamTask, TeamMessage, SwarmSnapshot } from './SwarmState';

/**
 * Mirrors SwarmStateManager.kt — maintains in-memory swarm state built from
 * team event deltas that arrive over the WebSocket as "team.event:{json}"
 * prefixed chat.delta messages.
 */
export class SwarmStateManager {
  private sessionId = '';
  private teamName = '';
  // Map preserves insertion order so join order is maintained
  private lanes = new Map<string, AgentLane>();
  private tasks = new Map<string, TeamTask>();
  private messages: TeamMessage[] = [];  // ring buffer, max 50
  private lastEventAt = 0;

  // ──────────────────────────────────────────
  // Public API
  // ──────────────────────────────────────────

  /**
   * Parse the JSON string that follows "team.event:" and apply it to state.
   * Expected shape: { "event": { "type": "team.member.spawned", ... } }
   */
  applyTeamEvent(json: string): void {
    try {
      const root = JSON.parse(json) as Record<string, unknown>;
      const event = (root['event'] as Record<string, unknown>) ?? root;
      const type = event['type'] as string | undefined;
      if (!type) return;
      this.lastEventAt = (event['timestamp'] as number | undefined) ?? Date.now();
      const teamName = event['team_name'] as string | undefined;
      if (teamName && !this.teamName) this.teamName = teamName;

      switch (type) {
        case 'team.member.spawned':           this.onMemberSpawned(event); break;
        case 'team.member.status_changed':    this.onMemberStatusChanged(event); break;
        case 'team.member.execution_changed': this.onMemberExecutionChanged(event); break;
        case 'team.member.shutdown':          this.onMemberShutdown(event); break;
        case 'team.task.created':             this.onTaskCreated(event); break;
        case 'team.task.claimed':             this.onTaskClaimed(event); break;
        case 'team.task.started':             this.onTaskStarted(event); break;
        case 'team.task.completed':           this.onTaskCompleted(event); break;
        case 'team.task.cancelled':           this.onTaskCancelled(event); break;
        default:
          if (type.startsWith('team.message.')) this.onTeamMessage(event);
      }
    } catch {
      // malformed JSON — ignore silently
    }
  }

  /**
   * Called from the chat.tool_call branch of onJiuwenMessage.
   * memberName may be undefined if the server does not include it in the payload.
   */
  applyToolCall(toolName: string, filePath: string | undefined, memberName: string | undefined): void {
    if (!memberName) return;
    const lane = this.getOrStubLane(memberName);
    lane.lastToolName = toolName;
    lane.currentActivity = this.formatActivity(toolName, filePath);
    lane.lastActivePath = filePath ?? null;
    lane.lastActiveAt = Date.now();
    this.lastEventAt = lane.lastActiveAt;
  }

  snapshot(): SwarmSnapshot {
    const sortedLanes = [...this.lanes.values()].sort((a, b) => {
      const pd = this.statusPriority(a.status) - this.statusPriority(b.status);
      return pd !== 0 ? pd : b.lastActiveAt - a.lastActiveAt;
    });
    const sortedTasks = [...this.tasks.values()].sort((a, b) => {
      const pd = this.taskStatusPriority(a.status) - this.taskStatusPriority(b.status);
      return pd !== 0 ? pd : a.createdAt - b.createdAt;
    });
    return {
      sessionId: this.sessionId,
      teamName: this.teamName,
      lanes: sortedLanes,
      tasks: sortedTasks,
      messages: [...this.messages],
      lastEventAt: this.lastEventAt,
    };
  }

  reset(newSessionId: string): void {
    this.sessionId = newSessionId;
    this.teamName = '';
    this.lanes.clear();
    this.tasks.clear();
    this.messages = [];
    this.lastEventAt = 0;
  }

  isEmpty(): boolean {
    return this.lanes.size === 0;
  }

  // ──────────────────────────────────────────
  // Event handlers
  // ──────────────────────────────────────────

  private onMemberSpawned(e: Record<string, unknown>): void {
    const name = e['member_name'] as string | undefined;
    if (!name) return;
    const existing = this.lanes.get(name);
    if (existing) {
      existing.displayName = (e['display_name'] as string) ?? existing.displayName;
      existing.role = (e['role'] as string) ?? existing.role;
      existing.status = 'READY';
      existing.lastActiveAt = this.lastEventAt;
    } else {
      this.lanes.set(name, {
        memberName: name,
        displayName: (e['display_name'] as string) ?? name,
        role: (e['role'] as string) ?? 'TEAMMATE',
        status: 'READY',
        executionStatus: 'IDLE',
        currentTaskId: null,
        currentTaskTitle: null,
        currentActivity: null,
        lastToolName: null,
        lastActivePath: null,
        lastActiveAt: this.lastEventAt,
        messageCount: 0,
        tasksDone: 0,
      });
    }
  }

  private onMemberStatusChanged(e: Record<string, unknown>): void {
    const name = e['member_name'] as string | undefined;
    if (!name) return;
    const lane = this.getOrStubLane(name);
    lane.status = (e['status'] as string) ?? lane.status;
    lane.lastActiveAt = this.lastEventAt;
  }

  private onMemberExecutionChanged(e: Record<string, unknown>): void {
    const name = e['member_name'] as string | undefined;
    if (!name) return;
    const lane = this.getOrStubLane(name);
    lane.executionStatus = (e['execution_status'] as string) ?? lane.executionStatus;
    lane.lastActiveAt = this.lastEventAt;
  }

  private onMemberShutdown(e: Record<string, unknown>): void {
    const name = e['member_name'] as string | undefined;
    if (!name) return;
    const lane = this.lanes.get(name);
    if (!lane) return;
    lane.status = 'SHUTDOWN';
    lane.executionStatus = 'IDLE';
    lane.currentActivity = null;
    lane.lastActiveAt = this.lastEventAt;
  }

  private onTaskCreated(e: Record<string, unknown>): void {
    const id = e['task_id'] as string | undefined;
    if (!id) return;
    this.tasks.set(id, {
      taskId: id,
      title: (e['title'] as string) ?? id,
      status: 'pending',
      assignee: null,
      createdAt: this.lastEventAt,
    });
  }

  private onTaskClaimed(e: Record<string, unknown>): void {
    const id = e['task_id'] as string | undefined;
    if (!id) return;
    const task = this.tasks.get(id) ?? { taskId: id, title: id, status: 'pending', assignee: null, createdAt: this.lastEventAt };
    this.tasks.set(id, task);
    task.assignee = (e['assignee'] as string) ?? null;
  }

  private onTaskStarted(e: Record<string, unknown>): void {
    const id = e['task_id'] as string | undefined;
    if (!id) return;
    const task = this.tasks.get(id) ?? { taskId: id, title: id, status: 'pending', assignee: null, createdAt: this.lastEventAt };
    this.tasks.set(id, task);
    task.status = 'in_progress';
    task.assignee = (e['assignee'] as string) ?? task.assignee;
    if (task.assignee) {
      const lane = this.lanes.get(task.assignee);
      if (lane) {
        lane.currentTaskId = id;
        lane.currentTaskTitle = task.title;
        lane.lastActiveAt = this.lastEventAt;
      }
    }
  }

  private onTaskCompleted(e: Record<string, unknown>): void {
    const id = e['task_id'] as string | undefined;
    if (!id) return;
    const task = this.tasks.get(id);
    if (!task) return;
    task.status = 'completed';
    if (task.assignee) {
      const lane = this.lanes.get(task.assignee);
      if (lane && lane.currentTaskId === id) {
        lane.currentTaskId = null;
        lane.currentTaskTitle = null;
        lane.tasksDone++;
        lane.lastActiveAt = this.lastEventAt;
      }
    }
  }

  private onTaskCancelled(e: Record<string, unknown>): void {
    const id = e['task_id'] as string | undefined;
    if (!id) return;
    const task = this.tasks.get(id);
    if (!task) return;
    task.status = 'cancelled';
    if (task.assignee) {
      const lane = this.lanes.get(task.assignee);
      if (lane && lane.currentTaskId === id) {
        lane.currentTaskId = null;
        lane.currentTaskTitle = null;
        lane.lastActiveAt = this.lastEventAt;
      }
    }
  }

  private onTeamMessage(e: Record<string, unknown>): void {
    const from = e['from_member'] as string | undefined;
    if (!from) return;
    const lane = this.getOrStubLane(from);
    lane.messageCount++;
    lane.lastActiveAt = this.lastEventAt;
    // Capture message content for the inter-agent message log
    const content = e['content'] as string | undefined;
    if (content) {
      const to = (e['to_member'] as string | undefined) ?? null;
      this.messages.push({ from, to, content, timestamp: this.lastEventAt });
      if (this.messages.length > 50) this.messages.shift();
    }
  }

  // ──────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────

  private getOrStubLane(memberName: string): AgentLane {
    const existing = this.lanes.get(memberName);
    if (existing) return existing;
    const stub: AgentLane = {
      memberName,
      displayName: memberName,
      role: 'TEAMMATE',
      status: 'READY',
      executionStatus: 'IDLE',
      currentTaskId: null,
      currentTaskTitle: null,
      currentActivity: null,
      lastToolName: null,
      lastActivePath: null,
      lastActiveAt: this.lastEventAt,
      messageCount: 0,
      tasksDone: 0,
    };
    this.lanes.set(memberName, stub);
    return stub;
  }

  private formatActivity(toolName: string, filePath: string | undefined): string {
    const base = filePath ? path.basename(filePath) : undefined;
    switch (toolName) {
      case 'write_file':
      case 'create_file':        return base ? `writing · ${base}` : 'writing';
      case 'str_replace_editor': return base ? `editing · ${base}` : 'editing';
      case 'read_file':          return base ? `reading · ${base}` : 'reading';
      case 'bash':
      case 'run_command':        return filePath ? `running · ${filePath.substring(0, 40)}` : 'running';
      case 'search_files':
      case 'grep':               return base ? `searching · ${base}` : 'searching';
      default:                   return toolName;
    }
  }

  private statusPriority(status: string): number {
    return ({ BUSY: 0, RUNNING: 1, READY: 2, PAUSED: 3, SHUTDOWN: 4 } as Record<string, number>)[status] ?? 5;
  }

  private taskStatusPriority(status: string): number {
    return ({ in_progress: 0, pending: 1, completed: 2, cancelled: 3 } as Record<string, number>)[status] ?? 4;
  }
}

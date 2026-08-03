export interface AgentLane {
  memberName: string;
  displayName: string;
  role: string;            // "LEADER" | "TEAMMATE" | "WORKER"
  status: string;          // "READY" | "BUSY" | "PAUSED" | "SHUTDOWN"
  executionStatus: string; // "IDLE" | "RUNNING" | "COMPLETING"
  currentTaskId: string | null;
  currentTaskTitle: string | null;
  currentActivity: string | null; // e.g. "writing · auth.service.ts"
  lastToolName: string | null;
  lastActiveAt: number;
  messageCount: number;
  tasksDone: number;
}

export interface TeamTask {
  taskId: string;
  title: string;
  status: string; // "pending" | "in_progress" | "completed" | "cancelled"
  assignee: string | null;
  createdAt: number;
}

export interface SwarmSnapshot {
  sessionId: string;
  teamName: string;
  lanes: AgentLane[];
  tasks: TeamTask[];
  lastEventAt: number;
}

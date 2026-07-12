package com.jiuwenswarm.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "JiuwenSwarmSettings",
    storages = [Storage("jiuwenswarm.xml")]
)
class JiuwenSwarmSettings : PersistentStateComponent<JiuwenSwarmSettings.State> {

    data class State(
        var host: String = "localhost",
        var port: Int = 19000,
        var channelId: String = "ide",
        var defaultMode: String = "agent.plan",
        var autoConnect: Boolean = true,
        /** When true, file edits from the agent are applied directly without a diff dialog. */
        var autoApplyEdits: Boolean = false,
        /** When true, show an approval dialog before applying any agent file edit. */
        var approveEdits: Boolean = false,
        /** When true, bash/run_command tool calls are shown in an IDE terminal. */
        var runCommandsInTerminal: Boolean = true,
        /** When true, send periodic WebSocket ping frames to keep the connection alive. */
        var keepAliveEnabled: Boolean = true,
        /** Seconds between keep-alive ping frames (5–300). */
        var keepAliveInterval: Int = 30,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var host: String
        get() = state.host
        set(v) { state = state.copy(host = v) }

    var port: Int
        get() = state.port
        set(v) { state = state.copy(port = v) }

    var channelId: String
        get() = state.channelId
        set(v) { state = state.copy(channelId = v) }

    var defaultMode: String
        get() = state.defaultMode
        set(v) { state = state.copy(defaultMode = v) }

    var autoConnect: Boolean
        get() = state.autoConnect
        set(v) { state = state.copy(autoConnect = v) }

    var autoApplyEdits: Boolean
        get() = state.autoApplyEdits
        set(v) { state = state.copy(autoApplyEdits = v) }

    var approveEdits: Boolean
        get() = state.approveEdits
        set(v) { state = state.copy(approveEdits = v) }

    var runCommandsInTerminal: Boolean
        get() = state.runCommandsInTerminal
        set(v) { state = state.copy(runCommandsInTerminal = v) }

    var keepAliveEnabled: Boolean
        get() = state.keepAliveEnabled
        set(v) { state = state.copy(keepAliveEnabled = v) }

    var keepAliveInterval: Int
        get() = state.keepAliveInterval
        set(v) { state = state.copy(keepAliveInterval = v.coerceIn(5, 300)) }

    val wsUrl: String get() = "ws://$host:$port/ws"

    companion object {
        fun instance(): JiuwenSwarmSettings =
            ApplicationManager.getApplication().getService(JiuwenSwarmSettings::class.java)
    }
}

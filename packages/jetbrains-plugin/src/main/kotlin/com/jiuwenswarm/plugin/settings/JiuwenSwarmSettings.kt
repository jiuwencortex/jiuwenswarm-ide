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
        var defaultMode: String = "code.normal",
        var autoConnect: Boolean = true,
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

    val wsUrl: String get() = "ws://$host:$port/ws"

    companion object {
        fun instance(): JiuwenSwarmSettings =
            ApplicationManager.getApplication().getService(JiuwenSwarmSettings::class.java)
    }
}

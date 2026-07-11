package com.jiuwenswarm.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.jiuwenswarm.plugin.client.SessionManager
import com.jiuwenswarm.plugin.client.WsClient
import com.jiuwenswarm.plugin.client.WsStatus
import com.jiuwenswarm.plugin.settings.JiuwenSwarmSettings

private val LOG = logger<JiuwenSwarmService>()

/**
 * Application-level singleton that owns the WebSocket connection and session manager.
 * Registered as applicationService in plugin.xml.
 */
class JiuwenSwarmService : Disposable {

    val settings = JiuwenSwarmSettings.instance()
    val ws = WsClient(settings.wsUrl)
    val session = SessionManager(ws, settings.channelId)

    init {
        if (settings.autoConnect) {
            // Create session once connection is established
            ws.addStatusListener { s ->
                if (s == WsStatus.CONNECTED && session.sessionId == null) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            session.createSession()
                            LOG.info("Session created: ${session.sessionId}")
                        } catch (e: Exception) {
                            LOG.warn("Could not create initial session", e)
                        }
                    }
                }
            }
            ws.connect()
        }
    }

    override fun dispose() {
        session.dispose()
        ws.dispose()
    }

    companion object {
        fun instance(): JiuwenSwarmService =
            ApplicationManager.getApplication().getService(JiuwenSwarmService::class.java)
    }
}

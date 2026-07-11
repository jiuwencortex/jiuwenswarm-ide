package com.jiuwenswarm.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.jiuwenswarm.plugin.JiuwenSwarmService
import com.jiuwenswarm.plugin.client.WsStatus
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class StatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "JiuwenSwarmStatusWidget"
    override fun getDisplayName() = "JiuwenSwarm"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = JiuwenStatusBarWidget()
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}

/** Direct implementation — does not extend EditorBasedWidget (removed from public API in 2024.1). */
class JiuwenStatusBarWidget : StatusBarWidget, StatusBarWidget.TextPresentation {

    private val service = JiuwenSwarmService.instance()
    private var bar: StatusBar? = null
    private val statusListener: (WsStatus) -> Unit = { update() }

    override fun ID() = "JiuwenSwarmStatusWidget"

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        bar = statusBar
        service.ws.addStatusListener(statusListener)
        update()
    }

    override fun dispose() {
        service.ws.removeStatusListener(statusListener)
        bar = null
    }

    // ── TextPresentation ──

    override fun getAlignment(): Float = 0f

    override fun getText(): String = when (service.ws.getStatus()) {
        WsStatus.CONNECTED    -> "⬤ JiuwenSwarm"
        WsStatus.CONNECTING   -> "◌ JiuwenSwarm"
        WsStatus.RECONNECTING -> "↻ JiuwenSwarm"
        WsStatus.DISCONNECTED -> "○ JiuwenSwarm"
    }

    override fun getTooltipText(): String = when (service.ws.getStatus()) {
        WsStatus.CONNECTED    -> "JiuwenSwarm: Connected — session ${service.session.sessionId?.take(8) ?: "none"}"
        WsStatus.CONNECTING   -> "JiuwenSwarm: Connecting…"
        WsStatus.RECONNECTING -> "JiuwenSwarm: Reconnecting…"
        WsStatus.DISCONNECTED -> "JiuwenSwarm: Disconnected — click to reconnect"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer<MouseEvent> { e ->
        if (SwingUtilities.isLeftMouseButton(e) && service.ws.getStatus() == WsStatus.DISCONNECTED) {
            service.ws.connect()
        }
    }

    private fun update() {
        SwingUtilities.invokeLater { bar?.updateWidget(ID()) }
    }
}

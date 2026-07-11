package com.jiuwenswarm.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.jiuwenswarm.plugin.JiuwenSwarmService
import com.jiuwenswarm.plugin.client.WsStatus
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

class StatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "JiuwenSwarmStatusWidget"
    override fun getDisplayName() = "JiuwenSwarm"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = JiuwenStatusBarWidget()
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}

/**
 * Custom status bar widget that renders with per-state colours.
 * Implements [StatusBarWidget.CustomStatusBarWidget] so we can embed
 * a [JLabel] directly and set its foreground colour — unlike
 * [StatusBarWidget.TextPresentation] which always uses the IDE's
 * default muted text colour.
 */
class JiuwenStatusBarWidget : StatusBarWidget, StatusBarWidget.CustomStatusBarWidget {

    private val service = JiuwenSwarmService.instance()
    private var bar: StatusBar? = null
    private val statusListener: (WsStatus) -> Unit = { update() }

    private val label = JLabel().also { lbl ->
        lbl.border = BorderFactory.createEmptyBorder(0, 6, 0, 4)
        lbl.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        lbl.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) &&
                    service.ws.getStatus() == WsStatus.DISCONNECTED
                ) {
                    service.ws.connect()
                }
            }
        })
    }

    override fun ID() = "JiuwenSwarmStatusWidget"

    // CustomStatusBarWidget — return null; our JLabel is the entire presentation
    override fun getPresentation(): StatusBarWidget.WidgetPresentation? = null

    override fun getComponent(): JComponent = label

    override fun install(statusBar: StatusBar) {
        bar = statusBar
        service.ws.addStatusListener(statusListener)
        update()
    }

    override fun dispose() {
        service.ws.removeStatusListener(statusListener)
        bar = null
    }

    private fun update() {
        SwingUtilities.invokeLater {
            val sid = service.session.sessionId?.take(8) ?: "none"
            val (symbol, color, tip) = when (service.ws.getStatus()) {
                WsStatus.CONNECTED    -> Triple(
                    "⬤ JiuwenSwarm",
                    Color(0x4e, 0xc9, 0xb0),  // teal — matches the in-panel connected dot
                    "JiuwenSwarm: Connected — session $sid",
                )
                WsStatus.CONNECTING   -> Triple(
                    "◌ JiuwenSwarm",
                    Color(0x4e, 0xc9, 0xb0),
                    "JiuwenSwarm: Connecting…",
                )
                WsStatus.RECONNECTING -> Triple(
                    "↻ JiuwenSwarm",
                    Color(0xdc, 0xdc, 0xaa),  // yellow — "caution"
                    "JiuwenSwarm: Reconnecting…",
                )
                WsStatus.DISCONNECTED -> Triple(
                    "○ JiuwenSwarm",
                    Color(0x6b, 0x6b, 0x6b),  // gray — inactive
                    "JiuwenSwarm: Disconnected — click to reconnect",
                )
            }
            label.text = symbol
            label.foreground = color
            label.toolTipText = tip
            bar?.updateWidget(ID())
        }
    }
}

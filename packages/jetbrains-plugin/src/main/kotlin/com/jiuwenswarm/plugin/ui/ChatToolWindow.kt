package com.jiuwenswarm.plugin.ui

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.jiuwenswarm.plugin.JiuwenSwarmService
import com.jiuwenswarm.plugin.client.SessionInfo
import com.jiuwenswarm.plugin.client.WsStatus
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel

private val LOG = logger<ChatToolWindowFactory>()
private val gson = Gson()

class ChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (!JBCefApp.isSupported()) {
            addFallback(toolWindow,
                "<html><body style='padding:8px'>" +
                "<b>JiuwenSwarm</b> requires JCEF (Chromium Embedded Framework).<br><br>" +
                "Enable it via:<br>" +
                "<code>Help → Find Action → Registry → ide.browser.jcef.enabled</code><br><br>" +
                "Then restart the IDE.</body></html>")
            return
        }
        try {
            val panel = ChatPanel(project, toolWindow)
            Disposer.register(toolWindow.disposable, panel)
            val content = ContentFactory.getInstance()
                .createContent(panel.component, "", false)
            toolWindow.contentManager.addContent(content)
        } catch (e: Exception) {
            LOG.error("Failed to initialise JiuwenSwarm chat panel", e)
            addFallback(toolWindow,
                "<html><body style='padding:8px'>" +
                "<b>JiuwenSwarm failed to load.</b><br>${e.message}</body></html>")
        }
    }

    override fun shouldBeAvailable(project: Project) = true

    private fun addFallback(toolWindow: ToolWindow, html: String) {
        val label = JLabel(html)
        label.border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        val content = ContentFactory.getInstance().createContent(label, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ChatPanel(
    private val project: Project,
    private val toolWindow: ToolWindow,
) : Disposable {

    private val service = JiuwenSwarmService.instance()
    private val browser = JBCefBrowser()
    private val jsQuery: JBCefJSQuery

    val component: JComponent get() = browser.component

    init {
        jsQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
        jsQuery.addHandler { request ->
            handleWebviewMessage(request)
            JBCefJSQuery.Response("ok")
        }

        // Inject bridge function once page loads
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    injectBridge()
                    sendCurrentStatus()
                }
            }
        }, browser.cefBrowser)

        // Listen for WS status and message events
        service.ws.addStatusListener(::onStatusChange)
        service.ws.addMessageListener(::onJiuwenMessage)

        // Load the chat HTML
        loadChatHtml()
    }

    // ──────────────────────────────────────────
    // Load HTML
    // ──────────────────────────────────────────
    private fun loadChatHtml() {
        val html = readChatHtml()
        // Use http://localhost as the base URL so WebSocket to ws://localhost:... is allowed by JCEF.
        browser.loadHTML(html, "http://localhost")
    }

    private fun readChatHtml(): String {
        // 1. Packaged resource (production)
        javaClass.classLoader.getResource("webview/chat.html")?.let {
            return it.readText()
        }
        // 2. Development (monorepo sibling next to the plugin source tree)
        try {
            val jarDir = File(javaClass.protectionDomain.codeSource.location.toURI())
            val devHtml = generateSequence(jarDir) { it.parentFile }
                .take(6)
                .map { File(it, "packages/shared-webview/chat.html") }
                .firstOrNull { it.exists() }
            if (devHtml != null) return devHtml.readText()
        } catch (_: Exception) {}
        return fallbackHtml()
    }

    // ──────────────────────────────────────────
    // Bridge injection (JS ↔ Kotlin)
    // ──────────────────────────────────────────
    private fun injectBridge() {
        val inject = """
            window.__jb_send = function(jsonStr) {
                ${jsQuery.inject("jsonStr")}
            };
            // Notify app that bridge is ready
            if (window.__jb_dispatch) window.__jb_dispatch('{"type":"bridge_ready"}');
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(inject, browser.cefBrowser.url, 0)
    }

    fun dispatchToWebview(msg: JsonObject) {
        val json = gson.toJson(msg).replace("'", "\\'")
        val js = "if(window.__jb_dispatch) window.__jb_dispatch('$json');"
        ApplicationManager.getApplication().invokeLater {
            browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
        }
    }

    fun dispatchToWebview(msg: Map<String, Any?>) {
        dispatchToWebview(gson.toJsonTree(msg).asJsonObject)
    }

    // ──────────────────────────────────────────
    // Webview → Plugin messages
    // ──────────────────────────────────────────
    private fun handleWebviewMessage(jsonStr: String) {
        try {
            val msg = gson.fromJson(jsonStr, JsonObject::class.java)
            when (msg.get("type")?.asString) {
                "ready" -> sendCurrentStatus()
                "send" -> {
                    val content = msg.get("content")?.asString ?: return
                    val mode = msg.get("mode")?.asString ?: "code.normal"
                    val rid = msg.get("requestId")?.asString ?: return
                    service.session.sendChat(content, mode, rid)
                }
                "new_session" -> ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        service.session.createSession()
                        sendCurrentStatus()
                    } catch (e: Exception) {
                        dispatchToWebview(mapOf("type" to "error", "message" to e.message))
                    }
                }
                "switch_session" -> {
                    val sid = msg.get("sessionId")?.asString ?: return
                    ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            service.session.switchSession(sid)
                            sendCurrentStatus()
                        } catch (e: Exception) {
                            dispatchToWebview(mapOf("type" to "error", "message" to e.message))
                        }
                    }
                }
                "list_sessions" -> ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        val sessions = service.session.listSessions()
                        dispatchToWebview(mapOf("type" to "sessions", "sessions" to sessions.map { it.toMap() }))
                    } catch (e: Exception) {
                        dispatchToWebview(mapOf("type" to "error", "message" to e.message))
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse webview message: $jsonStr", e)
        }
    }

    // ──────────────────────────────────────────
    // JiuwenSwarm events → webview
    // ──────────────────────────────────────────
    private fun onStatusChange(s: WsStatus) {
        sendCurrentStatus()
    }

    private fun onJiuwenMessage(msg: JsonObject) {
        if (msg.get("type")?.asString == "event") {
            dispatchToWebview(mapOf("type" to "jiuwen_event", "event" to msg))
        }
    }

    private fun sendCurrentStatus() {
        val s = service.ws.getStatus()
        val sid = service.session.sessionId
        when {
            s == WsStatus.CONNECTED && sid != null ->
                dispatchToWebview(mapOf(
                    "type" to "connected",
                    "sessionId" to sid,
                    "sessionTitle" to service.session.sessionTitle,
                ))
            s == WsStatus.RECONNECTING ->
                dispatchToWebview(mapOf("type" to "reconnecting"))
            else ->
                dispatchToWebview(mapOf("type" to "disconnected"))
        }
    }

    // ──────────────────────────────────────────
    override fun dispose() {
        service.ws.removeStatusListener(::onStatusChange)
        service.ws.removeMessageListener(::onJiuwenMessage)
        jsQuery.dispose()
        browser.dispose()
    }

    private fun fallbackHtml() = """
        <html><body style="background:#1e1e1e;color:#d4d4d4;font-family:sans-serif;padding:16px">
        <p>⚠ Could not load JiuwenSwarm chat UI.<br>
        Ensure <code>resources/webview/chat.html</code> is packaged with the plugin.</p>
        </body></html>
    """.trimIndent()
}

private fun SessionInfo.toMap() = mapOf(
    "session_id" to session_id,
    "title" to (title ?: session_id),
    "last_message_at" to last_message_at,
    "message_count" to message_count,
)

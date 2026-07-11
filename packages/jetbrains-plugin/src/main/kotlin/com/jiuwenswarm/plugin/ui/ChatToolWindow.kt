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
import java.awt.BorderLayout
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JToolBar

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

    // Tracks the last requestId sent to the server so we can match streaming
    // events that carry no request_id in their payload (gateway quirk).
    @Volatile private var lastRequestId: String? = null

    // Debug
    @Volatile private var debugEnabled = false
    private val debugLog = JTextArea(8, 0).apply {
        isEditable = false
        font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11)
    }
    private val debugScroll = JScrollPane(debugLog)
    private val debugToggle = JButton("Debug: OFF").apply {
        addActionListener {
            debugEnabled = !debugEnabled
            text = if (debugEnabled) "Debug: ON" else "Debug: OFF"
            debugScroll.isVisible = debugEnabled
            if (debugEnabled) {
                debugLog.append("[${time()}] DEBUG MODE ON\n")
                debugLog.append("[${time()}] Session: ${service.session.sessionId ?: "none"}\n")
                debugLog.append("[${time()}] WS Status: ${service.ws.getStatus()}\n")
                debugLog.append("---\n")
            }
        }
    }

    private val rootPanel = JPanel(BorderLayout()).apply {
        // Toolbar
        val toolbar = JToolBar().apply {
            isFloatable = false
            add(debugToggle)
            add(JLabel(" JiuwenSwarm plugin v2").apply {
                font = font.deriveFont(java.awt.Font.ITALIC, 10f)
            })
        }
        add(toolbar, BorderLayout.NORTH)
        // Browser
        add(browser.component, BorderLayout.CENTER)
        // Debug log (hidden by default)
        debugScroll.isVisible = false
        debugScroll.preferredSize = java.awt.Dimension(0, 140)
        add(debugScroll, BorderLayout.SOUTH)
    }

    val component: JComponent get() = rootPanel

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

        // Listen for WS status, session, and message events
        service.ws.addStatusListener(::onStatusChange)
        service.session.addSessionListener(::onSessionChange)
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
                    lastRequestId = rid
                    debug("SEND  → requestId=$rid mode=$mode content=${content.take(60)}")
                    if (!service.session.sendChat(content, mode, rid)) {
                        debug("SEND  → FAILED (no session or disconnected)")
                        dispatchToWebview(mapOf(
                            "type" to "error",
                            "message" to "Not connected or no active session",
                            "requestId" to rid
                        ))
                    } else {
                        debug("SEND  → OK")
                    }
                }
                "new_session" -> ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        debug("ACTION→ new_session")
                        // Server auto-creates session on connect via connection.ack.
                        // If none exists yet, just refresh status; session may arrive shortly.
                        sendCurrentStatus()
                    } catch (e: Exception) {
                        dispatchToWebview(mapOf("type" to "error", "message" to e.message))
                    }
                }
                "switch_session" -> {
                    val sid = msg.get("sessionId")?.asString ?: return
                    debug("ACTION→ switch_session $sid")
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
                        debug("ACTION→ list_sessions")
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
    private fun debug(line: String) {
        if (!debugEnabled) return
        ApplicationManager.getApplication().invokeLater {
            debugLog.append("[${time()}] $line\n")
            // Auto-scroll to bottom
            debugLog.caretPosition = debugLog.document.length
        }
    }

    private fun time(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))

    private fun onStatusChange(s: WsStatus) {
        debug("WS status → $s")
        sendCurrentStatus()
    }

    private fun onSessionChange(sid: String?) {
        debug("Session → $sid")
        sendCurrentStatus()
    }

    private fun onJiuwenMessage(msg: JsonObject) {
        debug("RAW ← ${gson.toJson(msg)}")
        val converted = convertServerMessageToLegacyEvent(msg, lastRequestId)
        if (converted != null) {
            debug("CONV  → event_type=${converted.get("event_type")?.asString} request_id=${converted.get("request_id")?.asString}")
            dispatchToWebview(mapOf("type" to "jiuwen_event", "event" to converted))
        } else {
            debug("CONV  → dropped (unrecognized format)")
        }
    }

    /** Convert server messages (E2A or old format) to the legacy event format the webview expects.
     *  Webview expects: { event_type, request_id, payload }
     */
    private fun convertServerMessageToLegacyEvent(msg: JsonObject, fallbackRequestId: String? = null): JsonObject? {
        val responseKind = msg.get("response_kind")?.asString

        // ── E2A format ──
        if (responseKind != null) {
            val requestId = msg.get("request_id")?.asString ?: ""
            val body = msg.getAsJsonObject("body") ?: return null

            return when (responseKind) {
                "e2a.chunk" -> {
                    val eventType = body.get("event_type")?.asString ?: ""
                    val delta = body.get("delta")
                    JsonObject().apply {
                        addProperty("event_type", eventType)
                        addProperty("request_id", requestId)
                        add("payload", when (eventType) {
                            "chat.delta" -> JsonObject().apply {
                                addProperty("text", delta?.asString ?: "")
                            }
                            "chat.reasoning" -> JsonObject().apply {
                                addProperty("text", delta?.asString ?: "")
                            }
                            else -> if (delta?.isJsonObject == true) delta.asJsonObject else JsonObject()
                        })
                    }
                }
                "e2a.complete" -> {
                    val result = body.getAsJsonObject("result")
                    val eventType = result?.get("event_type")?.asString ?: "chat.final"
                    JsonObject().apply {
                        addProperty("event_type", eventType)
                        addProperty("request_id", requestId)
                        add("payload", result ?: JsonObject())
                    }
                }
                "e2a.error" -> {
                    val details = body.getAsJsonObject("details")
                    val errorMsg = body.get("message")?.asString ?: "Unknown error"
                    JsonObject().apply {
                        addProperty("event_type", "chat.error")
                        addProperty("request_id", requestId)
                        add("payload", JsonObject().apply {
                            addProperty("error", errorMsg)
                            if (details != null) add("details", details)
                        })
                    }
                }
                else -> null
            }
        }

        // ── Old format (used for connection.ack and direct events) ──
        if (msg.get("type")?.asString == "event") {
            val eventName = msg.get("event")?.asString ?: ""
            val payload = msg.getAsJsonObject("payload") ?: JsonObject()
            val mappedPayload = payload.deepCopy()
            // Webview expects "text" for delta events, gateway sends "content"
            if (eventName == "chat.delta" && mappedPayload.has("content") && !mappedPayload.has("text")) {
                mappedPayload.addProperty("text", mappedPayload.get("content").asString)
            }
            val requestId = mappedPayload.get("request_id")?.asString
                ?: fallbackRequestId
                ?: ""
            return JsonObject().apply {
                addProperty("event_type", eventName)
                addProperty("request_id", requestId)
                add("payload", mappedPayload)
            }
        }

        return null
    }

    private fun sendCurrentStatus() {
        val s = service.ws.getStatus()
        val sid = service.session.sessionId
        debug("STATUS→ ws=$s session=$sid")
        when {
            s == WsStatus.CONNECTED && sid != null ->
                dispatchToWebview(mapOf(
                    "type" to "connected",
                    "sessionId" to sid,
                    "sessionTitle" to service.session.sessionTitle,
                ))
            s == WsStatus.CONNECTED ->
                // WS is up but no session yet — ask webview to show "connecting" state
                // so user knows they should click New Session or wait
                dispatchToWebview(mapOf(
                    "type" to "connected",
                    "sessionId" to null,
                    "sessionTitle" to "JiuwenSwarm",
                    "needsSession" to true,
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
        service.session.removeSessionListener(::onSessionChange)
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

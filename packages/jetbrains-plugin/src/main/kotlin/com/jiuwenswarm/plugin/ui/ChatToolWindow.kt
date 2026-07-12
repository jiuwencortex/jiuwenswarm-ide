package com.jiuwenswarm.plugin.ui

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.jiuwenswarm.plugin.JiuwenSwarmService
import com.jiuwenswarm.plugin.client.SessionInfo
import com.jiuwenswarm.plugin.client.WsStatus
import com.jiuwenswarm.plugin.context.ContextCollector
import com.jiuwenswarm.plugin.editor.DiffApplier
import com.jiuwenswarm.plugin.settings.JiuwenSwarmSettings
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
            // Store the panel on its own component so SendSelectionAction can retrieve it
            panel.component.putClientProperty("jiuwenswarm.panel", panel)
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

    // Debug logging is toggled from the webview; when true we log to IDEA log.
    @Volatile private var debugEnabled = false

    // Snapshot tracking for checkpoint/rewind feature.
    // currentTurnSnapshots: file path → content before first edit this turn (null = file didn't exist).
    // lastTurnSnapshots: promoted from currentTurnSnapshots on chat.final; used for rewind.
    private val currentTurnSnapshots = mutableMapOf<String, String?>()
    @Volatile private var lastTurnSnapshots = mapOf<String, String?>()

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
        val json = gson.toJson(msg)
            .replace("\\", "\\\\")  // must come first: escape backslashes before single-quotes
            .replace("'", "\\'")
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
                    val mode = msg.get("mode")?.asString ?: "agent.plan"
                    val rid = msg.get("requestId")?.asString ?: return
                    val mediaItems = msg.getAsJsonArray("media_items")
                    lastRequestId = rid
                    // Clear snapshots from previous turn; rewind is no longer valid once user sends a new message
                    currentTurnSnapshots.clear()
                    lastTurnSnapshots = emptyMap()
                    dispatchToWebview(mapOf("type" to "rewindable", "enabled" to false))
                    debug("SEND  → requestId=$rid mode=$mode content=${content.take(60)} media=${mediaItems?.size() ?: 0}")
                    val ideContext = ContextCollector.collect(project)
                    if (!service.session.sendChat(content, mode, rid, ideContext, mediaItems)) {
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
                "toggle_debug" -> {
                    debugEnabled = msg.get("enabled")?.asBoolean ?: false
                    debug("Debug mode toggled: $debugEnabled")
                }
                "new_session" -> ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        debug("ACTION→ new_session (reconnecting for fresh session)")
                        currentTurnSnapshots.clear()
                        lastTurnSnapshots = emptyMap()
                        dispatchToWebview(mapOf("type" to "rewindable", "enabled" to false))
                        service.ws.reconnect()
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
                        debug("ACTION→ list_sessions returned ${sessions.size} sessions")
                        dispatchToWebview(mapOf("type" to "sessions", "sessions" to sessions.map { it.toMap() }))
                    } catch (e: Exception) {
                        LOG.warn("list_sessions failed", e)
                        dispatchToWebview(mapOf("type" to "sessions_error", "message" to (e.message ?: "Failed to load sessions")))
                    }
                }
                "list_skills" -> ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        debug("ACTION→ list_skills")
                        val skills = service.session.listSkills()
                        debug("ACTION→ list_skills returned ${skills.size} skills")
                        val skillMaps = skills.map { obj ->
                            mapOf(
                                "skill_id"    to (obj.get("skill_id")?.asString    ?: ""),
                                "name"        to (obj.get("name")?.asString         ?: obj.get("skill_id")?.asString ?: ""),
                                "description" to (obj.get("description")?.asString  ?: ""),
                                "enabled"     to (obj.get("enabled")?.asBoolean     ?: true),
                                "trigger"     to (obj.get("trigger")?.asString      ?: ""),
                            )
                        }
                        dispatchToWebview(mapOf("type" to "skills", "skills" to skillMaps))
                    } catch (e: Exception) {
                        LOG.warn("list_skills failed", e)
                        dispatchToWebview(mapOf("type" to "skills_error", "message" to (e.message ?: "Failed to load skills")))
                    }
                }
                "toggle_skill" -> {
                    val skillId = msg.get("skillId")?.asString ?: return
                    val enabled = msg.get("enabled")?.asBoolean ?: return
                    ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            debug("ACTION→ toggle_skill $skillId enabled=$enabled")
                            service.session.toggleSkill(skillId, enabled)
                            dispatchToWebview(mapOf("type" to "skill_toggled", "skillId" to skillId, "enabled" to enabled))
                        } catch (e: Exception) {
                            LOG.warn("toggle_skill failed", e)
                            dispatchToWebview(mapOf("type" to "skills_error", "message" to (e.message ?: "Failed to toggle skill")))
                        }
                    }
                }
                "delete_session" -> {
                    val sid = msg.get("sessionId")?.asString ?: return
                    ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            debug("ACTION→ delete_session $sid")
                            service.session.deleteSession(sid)
                            dispatchToWebview(mapOf("type" to "session_deleted", "sessionId" to sid))
                        } catch (e: Exception) {
                            LOG.warn("delete_session failed", e)
                            dispatchToWebview(mapOf("type" to "sessions_error",
                                "message" to (e.message ?: "Failed to delete session")))
                        }
                    }
                }
                "rewind" -> {
                    val snapshots = lastTurnSnapshots
                    if (snapshots.isEmpty()) return
                    ApplicationManager.getApplication().executeOnPooledThread {
                        var restored = 0
                        var failed = 0
                        for ((path, originalContent) in snapshots) {
                            try {
                                WriteCommandAction.runWriteCommandAction(project, "Rewind agent changes", null, Runnable {
                                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
                                    if (originalContent == null) {
                                        vf?.delete(this)
                                    } else if (vf != null) {
                                        vf.setBinaryContent(originalContent.toByteArray(vf.charset))
                                    }
                                })
                                restored++
                            } catch (e: Exception) {
                                debug("Rewind failed for $path: ${e.message}")
                                failed++
                            }
                        }
                        lastTurnSnapshots = emptyMap()
                        val resultMsg = if (failed == 0) "Rewound $restored file(s)"
                                        else "Rewound $restored file(s), $failed failed"
                        dispatchToWebview(mapOf("type" to "rewind_done", "message" to resultMsg,
                            "restored" to restored, "failed" to failed))
                    }
                }
                "open_file" -> {
                    val path = msg.get("path")?.asString ?: return
                    val line = msg.get("line")?.asInt ?: 0
                    ApplicationManager.getApplication().invokeLater {
                        val vf = LocalFileSystem.getInstance().findFileByPath(path)
                            ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
                        if (vf != null) {
                            OpenFileDescriptor(project, vf, maxOf(0, line - 1), 0).navigate(true)
                        } else {
                            debug("open_file: not found: $path")
                        }
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
        LOG.info("[JiuwenSwarmDebug] $line")
        dispatchToWebview(mapOf("type" to "debug_log", "line" to line))
    }

    private fun onStatusChange(@Suppress("UNUSED_PARAMETER") s: WsStatus) {
        debug("WS status → $s")
        sendCurrentStatus()
    }

    private fun onSessionChange(sid: String?) {
        debug("Session → $sid")
        sendCurrentStatus()
    }

    private fun onJiuwenMessage(msg: JsonObject) {
        // Skip request-response protocol messages (type:"res") — SessionManager handles
        // those synchronously via its CompletableFuture.  They are never chat events.
        if (msg.get("type")?.asString == "res") return

        debug("RAW ← ${gson.toJson(msg)}")
        // Route file-edit tool calls to DiffApplier (show diff or auto-apply).
        // Only applies to old-format events where tool_name sits at the message root.
        if (msg.get("type")?.asString == "event" &&
            msg.get("event_type")?.asString == "chat.tool_call") {
            ApplicationManager.getApplication().executeOnPooledThread {
                DiffApplier.handle(project, msg)
            }
        }
        val converted = convertServerMessageToLegacyEvent(msg, lastRequestId)
        if (converted != null) {
            val et = converted.get("event_type")?.asString
            // ── Snapshot files before they are edited so rewind can restore them ──
            if (et == "chat.tool_call") {
                val payload = converted.getAsJsonObject("payload") ?: JsonObject()
                val toolName = payload.get("tool_name")?.asString ?: ""
                if (toolName in setOf("str_replace_editor", "write_file", "create_file")) {
                    val args = payload.getAsJsonObject("tool_call")?.getAsJsonObject("arguments")
                        ?: payload.getAsJsonObject("tool_input")
                        ?: payload.getAsJsonObject("input")
                    val path = args?.get("path")?.asString
                    if (path != null && !currentTurnSnapshots.containsKey(path)) {
                        val vf = LocalFileSystem.getInstance().findFileByPath(path)
                        currentTurnSnapshots[path] = vf?.let {
                            try {
                                ReadAction.compute<String, Throwable> {
                                    String(it.contentsToByteArray(), it.charset)
                                }
                            } catch (_: Exception) { null }
                        }
                        debug("SNAP  → snapshotted $path (existed=${vf != null})")
                    }
                }
            }
            // ── On turn end, promote snapshots and show rewind bar ──
            if (et == "chat.final") {
                if (currentTurnSnapshots.isNotEmpty()) {
                    lastTurnSnapshots = currentTurnSnapshots.toMap()
                    dispatchToWebview(mapOf("type" to "rewindable", "enabled" to true))
                    debug("SNAP  → turn complete, ${lastTurnSnapshots.size} file(s) snapshotted")
                }
                currentTurnSnapshots.clear()
            }
            debug("CONV  → event_type=$et request_id=${converted.get("request_id")?.asString}")
            dispatchToWebview(mapOf("type" to "jiuwen_event", "event" to converted))
            trackTokenUsage(converted)
        } else {
            debug("CONV  → dropped (not a recognised chat event)")
        }
    }

    /** Extract token counts from chat.usage_metadata and update the service for the status bar. */
    private fun trackTokenUsage(event: JsonObject) {
        val et = event.get("event_type")?.asString ?: return
        if (et != "chat.usage_metadata" && et != "chat.usage_summary") return
        val payload = event.getAsJsonObject("payload") ?: return
        val input   = payload.get("input_tokens")?.asInt  ?: payload.get("cache_read_input_tokens")?.asInt ?: 0
        val output  = payload.get("output_tokens")?.asInt ?: 0
        service.lastTokenCount += input + output
        // Refresh the status bar widget so the new count appears immediately
        ApplicationManager.getApplication().invokeLater {
            WindowManager.getInstance().getStatusBar(project)?.updateWidget("JiuwenSwarmStatusWidget")
        }
    }

    /** Convert server messages (E2A or old format) to the legacy event format the webview expects.
     *  Webview expects: { event_type, request_id, payload }
     *  Pure conversion — no side-effects (no dispatch, no DiffApplier calls).
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
            s == WsStatus.CONNECTED && sid != null -> {
                // Fetch models in background and include them
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        val (models, activeModel) = service.session.listModels()
                        val modelList = models.map { m ->
                            mapOf(
                                "model_name" to (m.get("model_name")?.asString ?: ""),
                                "alias" to (m.get("alias")?.asString ?: ""),
                                "model_provider" to (m.get("model_provider")?.asString ?: ""),
                            )
                        }
                        dispatchToWebview(mapOf(
                            "type" to "connected",
                            "sessionId" to sid,
                            "sessionTitle" to service.session.sessionTitle,
                            "models" to modelList,
                            "activeModel" to activeModel,
                        ))
                    } catch (e: Exception) {
                        debug("STATUS→ models.list failed: ${e.message}")
                        dispatchToWebview(mapOf(
                            "type" to "connected",
                            "sessionId" to sid,
                            "sessionTitle" to service.session.sessionTitle,
                        ))
                    }
                }
            }
            s == WsStatus.CONNECTED ->
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

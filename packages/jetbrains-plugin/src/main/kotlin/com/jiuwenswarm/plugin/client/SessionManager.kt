package com.jiuwenswarm.plugin.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.logger
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

private val LOG = logger<SessionManager>()
private val gson = Gson()
private const val REQUEST_TIMEOUT_SEC = 5L

typealias SessionChangeListener = (String?) -> Unit

data class SessionInfo(
    val session_id: String,
    val title: String?,
    val last_message_at: Long?,
    val created_at: Long?,
    val message_count: Int?,
)

class SessionManager(
    private val ws: WsClient,
    private val channelId: String = "ide",
) {
    var sessionId: String? = null
        private set
    var sessionTitle: String = "JiuwenSwarm"
        private set

    private val pending = ConcurrentHashMap<String, CompletableFuture<JsonObject>>()
    private val sessionListeners = CopyOnWriteArrayList<SessionChangeListener>()
    private val listener: MessageListener = { msg -> handleMessage(msg) }

    init {
        ws.addMessageListener(listener)
    }

    fun addSessionListener(l: SessionChangeListener) = sessionListeners.add(l)
    fun removeSessionListener(l: SessionChangeListener) = sessionListeners.remove(l)

    fun dispose() {
        ws.removeMessageListener(listener)
        pending.values.forEach { it.cancel(true) }
        pending.clear()
    }

    fun listSessions(limit: Int = 20): List<SessionInfo> {
        val payload = request("session.list", mapOf("limit" to limit))
        val arr = payload.getAsJsonArray("sessions") ?: return emptyList()
        return arr.map { el ->
            val obj = el.asJsonObject
            SessionInfo(
                session_id = obj.get("session_id")?.asString ?: "",
                title = obj.get("title")?.asString,
                last_message_at = obj.get("last_message_at")?.asLong,
                created_at = obj.get("created_at")?.asLong,
                message_count = obj.get("message_count")?.asInt,
            )
        }
    }

    /** Returns available models and the currently active model name. */
    fun listModels(): Pair<List<JsonObject>, String?> {
        val payload = request("models.list", emptyMap())
        val models = payload.getAsJsonArray("models")?.map { it.asJsonObject } ?: emptyList()
        val activeModel = payload.get("active_model")?.asString
        return models to activeModel
    }

    fun switchSession(sid: String) {
        val payload = request("session.switch", mapOf("session_id" to sid), sid)
        setSessionId(sid)
        sessionTitle = payload.get("title")?.asString ?: sid
    }

    /**
     * Fire-and-forget chat message. Streaming events arrive via WsClient message listener.
     * If [ideContext] is non-null it is prepended to [content] as a structured block so the
     * agent can see the current file, selection, and diagnostics without the user having to
     * copy-paste them manually.
     * If [mediaItems] is non-null, image attachments are included in base64 format.
     */
    fun sendChat(
        content: String,
        mode: String,
        requestId: String,
        ideContext: String? = null,
        mediaItems: com.google.gson.JsonArray? = null,
    ): Boolean {
        val sid = sessionId ?: return false
        val fullContent = if (!ideContext.isNullOrBlank()) {
            "$ideContext\n\n$content"
        } else {
            content
        }
        val msg = buildJsonObject {
            addProperty("id", requestId)
            addProperty("type", "req")
            addProperty("channel_id", channelId)
            addProperty("method", "chat.send")
            add("params", buildJsonObject {
                addProperty("content", fullContent)
                addProperty("mode", mode)
                addProperty("session_id", sid)
                if (mediaItems != null && mediaItems.size() > 0) {
                    add("media_items", mediaItems)
                }
            })
        }
        return ws.send(msg)
    }

    // ──────────────────────────────────────────
    private fun setSessionId(sid: String?) {
        if (sessionId == sid) return
        sessionId = sid
        sessionListeners.forEach { it(sid) }
    }

    private fun request(
        method: String,
        params: Map<String, Any>,
        sid: String? = null,
    ): JsonObject {
        val id = UUID.randomUUID().toString()
        val paramsWithSession = params.toMutableMap()
        if (sid != null) paramsWithSession["session_id"] = sid
        val msg = buildJsonObject {
            addProperty("id", id)
            addProperty("type", "req")
            addProperty("channel_id", channelId)
            addProperty("method", method)
            add("params", gson.toJsonTree(paramsWithSession).asJsonObject)
            addProperty("timestamp", System.currentTimeMillis() / 1000.0)
        }
        val future = CompletableFuture<JsonObject>()
        pending[id] = future
        if (!ws.send(msg)) {
            pending.remove(id)
            throw IllegalStateException("WebSocket not connected")
        }
        return try {
            future.get(REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: Exception) {
            pending.remove(id)
            throw RuntimeException("Request '$method' failed: ${e.javaClass.simpleName} - ${e.message}", e)
        }
    }

    private fun handleMessage(msg: JsonObject) {
        val msgType = msg.get("type")?.asString

        // ── E2A format responses (gateway may wrap session.list/switch in E2A) ──
        val responseKind = msg.get("response_kind")?.asString
        if (responseKind != null) {
            val rid = msg.get("request_id")?.asString ?: return
            val future = pending.remove(rid) ?: return   // not our request — ignore
            when (responseKind) {
                "e2a.complete" -> {
                    val body = msg.getAsJsonObject("body") ?: JsonObject()
                    val result = body.getAsJsonObject("result") ?: body
                    future.complete(result)
                }
                "e2a.error" -> {
                    val body = msg.getAsJsonObject("body") ?: JsonObject()
                    val err = body.get("message")?.asString ?: "E2A error"
                    future.completeExceptionally(RuntimeException(err))
                }
                else -> future.completeExceptionally(RuntimeException("Unexpected response_kind: $responseKind"))
            }
            return
        }

        // ── Gateway legacy format responses ──
        if (msgType == "res") {
            val rid = msg.get("id")?.asString
            if (rid == null) {
                LOG.warn("Response missing 'id' field: ${msg}")
                return
            }
            val future = pending.remove(rid)
            if (future == null) {
                LOG.warn("Received response for unknown id=$rid; pending=${pending.keys}")
                return
            }
            if (msg.get("ok")?.asBoolean == true) {
                val payload = msg.getAsJsonObject("payload") ?: JsonObject()
                future.complete(payload)
            } else {
                val err = msg.get("error")?.asString
                    ?: msg.getAsJsonObject("payload")?.get("error")?.asString
                    ?: "Request failed"
                future.completeExceptionally(RuntimeException(err))
            }
            return
        }

        // ── Events (connection.ack gives us the session) ──
        if (msgType == "event") {
            val eventName = msg.get("event")?.asString ?: return
            val payload = msg.getAsJsonObject("payload") ?: JsonObject()
            when (eventName) {
                "connection.ack" -> {
                    val sid = payload.get("session_id")?.asString
                    if (sid != null) {
                        LOG.info("Auto-session from connection.ack: $sid")
                        setSessionId(sid)
                    }
                }
            }
            return
        }
    }
}

// ──────────────────────────────────────────────────────────────────
private fun buildJsonObject(block: JsonObject.() -> Unit): JsonObject =
    JsonObject().also(block)

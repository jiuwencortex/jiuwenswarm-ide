package com.jiuwenswarm.plugin.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val LOG = logger<SessionManager>()
private val gson = Gson()
private const val REQUEST_TIMEOUT_SEC = 15L

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
    private val listener: MessageListener = { msg -> handleMessage(msg) }

    init {
        ws.addMessageListener(listener)
    }

    fun dispose() {
        ws.removeMessageListener(listener)
        pending.values.forEach { it.cancel(true) }
        pending.clear()
    }

    /** Create a new session, blocking up to [REQUEST_TIMEOUT_SEC] seconds */
    fun createSession(): String {
        val payload = request("session.create", emptyMap())
        // Server uses camelCase "sessionId" in the response payload
        val sid = payload.get("sessionId")?.asString
            ?: payload.get("session_id")?.asString
            ?: error("No sessionId in response")
        sessionId = sid
        sessionTitle = payload.get("title")?.asString ?: "New Session"
        return sid
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

    fun switchSession(sid: String) {
        val payload = request("session.switch", mapOf("session_id" to sid), sid)
        sessionId = sid
        sessionTitle = payload.get("title")?.asString ?: sid
    }

    /** Fire-and-forget chat message. Streaming events arrive via WsClient message listener. */
    fun sendChat(content: String, mode: String, requestId: String): Boolean {
        val sid = sessionId ?: return false
        val msg = buildJsonObject {
            addProperty("id", requestId)
            addProperty("type", "req")
            addProperty("channel_id", channelId)
            addProperty("session_id", sid)
            addProperty("req_method", "chat.send")
            add("params", buildJsonObject {
                addProperty("content", content)
                addProperty("mode", mode)
            })
            addProperty("timestamp", System.currentTimeMillis() / 1000.0)
        }
        return ws.send(msg)
    }

    // ──────────────────────────────────────────
    private fun request(
        method: String,
        params: Map<String, Any>,
        sid: String? = null,
    ): JsonObject {
        val id = UUID.randomUUID().toString()
        val msg = buildJsonObject {
            addProperty("request_id", id)
            addProperty("type", "req")
            addProperty("channel_id", channelId)
            if (sid != null) addProperty("session_id", sid)
            addProperty("req_method", method)
            add("params", gson.toJsonTree(params).asJsonObject)
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
            throw RuntimeException("Request '$method' failed: ${e.message}", e)
        }
    }

    private fun handleMessage(msg: JsonObject) {
        // ── E2A format responses (server always sends these) ──
        val requestId = msg.get("request_id")?.asString
        val status = msg.get("status")?.asString
        if (requestId != null && status != null) {
            val future = pending.remove(requestId) ?: return
            if (status == "succeeded") {
                val body = msg.getAsJsonObject("body")
                // The actual result is nested under body.result
                val result = body?.getAsJsonObject("result") ?: JsonObject()
                future.complete(result)
            } else {
                val body = msg.getAsJsonObject("body")
                val error = body?.get("message")?.asString ?: "Request failed"
                future.completeExceptionally(RuntimeException(error))
            }
            return
        }

        // ── Legacy format responses (fallback, unlikely with current server) ──
        if (msg.get("type")?.asString == "res") {
            val rid = msg.get("request_id")?.asString ?: return
            val future = pending.remove(rid) ?: return
            if (msg.get("ok")?.asBoolean == true) {
                val payload = msg.getAsJsonObject("payload") ?: JsonObject()
                future.complete(payload)
            } else {
                val err = msg.getAsJsonObject("payload")?.get("error")?.asString ?: "Request failed"
                future.completeExceptionally(RuntimeException(err))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// Tiny DSL to build JsonObject without Kotlin serialization overhead
// ──────────────────────────────────────────────────────────────────
private fun buildJsonObject(block: JsonObject.() -> Unit): JsonObject =
    JsonObject().also(block)

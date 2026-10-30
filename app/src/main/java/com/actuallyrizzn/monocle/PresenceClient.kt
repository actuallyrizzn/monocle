package com.actuallyrizzn.monocle

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class QueueMessage(
    val id: Int,
    val text: String,
    val sender: String,
    val subject: String,
)

data class HeartbeatResult(
    val ok: Boolean,
    val on: Boolean,
    val queueLen: Int? = null,
    val error: String? = null,
)

data class PullResult(
    val ok: Boolean,
    val on: Boolean,
    val messages: List<QueueMessage> = emptyList(),
    val error: String? = null,
)

data class AckResult(
    val ok: Boolean,
    val removed: Int = 0,
    val error: String? = null,
)

fun interface PresenceTransport {
    fun request(method: String, url: String, apiKey: String, jsonBody: String?): String
}

class HttpPresenceTransport(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
) : PresenceTransport {
    override fun request(method: String, url: String, apiKey: String, jsonBody: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.requestMethod = method
            conn.doInput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("X-API-Key", apiKey)
            if (jsonBody != null) {
                val bytes = jsonBody.toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Content-Length", bytes.size.toString())
                conn.outputStream.use { it.write(bytes) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val raw = (stream ?: conn.inputStream).bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (raw.isBlank()) {
                throw IOException("presence HTTP ${conn.responseCode} empty body")
            }
            return raw
        } finally {
            conn.disconnect()
        }
    }
}

class PresenceClient(
    @Volatile var baseUrl: String,
    @Volatile var apiKey: String,
    private val transport: PresenceTransport = HttpPresenceTransport(),
) {
    fun heartbeat(ble: Boolean, device: String?, battery: Int? = null): HeartbeatResult {
        if (apiKey.isBlank()) {
            return HeartbeatResult(ok = false, on = false, error = "missing_api_key")
        }
        val body = JSONObject().put("ble", ble)
        if (!device.isNullOrBlank()) body.put("device", device)
        if (battery != null) body.put("battery", battery)
        return try {
            parseHeartbeat(transport.request("POST", url("/v1/heartbeat"), apiKey, body.toString()))
        } catch (e: Exception) {
            HeartbeatResult(ok = false, on = false, error = e.message ?: "unreachable")
        }
    }

    fun pull(): PullResult {
        if (apiKey.isBlank()) {
            return PullResult(ok = false, on = false, error = "missing_api_key")
        }
        return try {
            parsePull(transport.request("GET", url("/v1/pull"), apiKey, null))
        } catch (e: Exception) {
            PullResult(ok = false, on = false, error = e.message ?: "unreachable")
        }
    }

    fun ack(ids: List<Int>): AckResult {
        if (ids.isEmpty()) return AckResult(ok = true, removed = 0)
        if (apiKey.isBlank()) {
            return AckResult(ok = false, error = "missing_api_key")
        }
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        val body = JSONObject().put("ids", arr)
        return try {
            parseAck(transport.request("POST", url("/v1/ack"), apiKey, body.toString()))
        } catch (e: Exception) {
            AckResult(ok = false, error = e.message ?: "unreachable")
        }
    }

    fun keySet(): Boolean = apiKey.isNotBlank()

    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    companion object {
        fun parseHeartbeat(raw: String): HeartbeatResult {
            val json = JSONObject(raw)
            val err = json.optString("error").ifBlank { null }
            val ok = json.optBoolean("ok", err == null)
            val queueLen = if (json.has("queue_len")) json.optInt("queue_len") else null
            return HeartbeatResult(
                ok = ok,
                on = json.optBoolean("on", false),
                queueLen = queueLen,
                error = if (ok) null else err ?: "error",
            )
        }

        fun parsePull(raw: String): PullResult {
            val json = JSONObject(raw)
            val err = json.optString("error").ifBlank { null }
            val ok = json.optBoolean("ok", err == null)
            val messages = mutableListOf<QueueMessage>()
            val arr = json.optJSONArray("messages")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val text = m.optString("text").trim()
                    if (text.isEmpty()) continue
                    messages += QueueMessage(
                        id = m.optInt("id"),
                        text = text,
                        sender = m.optString("sender").ifBlank { "agent" },
                        subject = m.optString("subject").trim(),
                    )
                }
            }
            return PullResult(
                ok = ok,
                on = json.optBoolean("on", false),
                messages = messages,
                error = if (ok) null else err ?: "error",
            )
        }

        fun parseAck(raw: String): AckResult {
            val json = JSONObject(raw)
            val err = json.optString("error").ifBlank { null }
            val ok = json.optBoolean("ok", err == null)
            return AckResult(
                ok = ok,
                removed = json.optInt("removed", 0),
                error = if (ok) null else err ?: "error",
            )
        }
    }
}

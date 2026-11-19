package app.tiinyremote

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** An answer from the Tiiny: HTTP status plus the raw bytes. */
class Reply(val code: Int, val bytes: ByteArray) {
    val ok get() = code in 200..299
    val text get() = String(bytes, Charsets.UTF_8)

    fun json(): JSONObject = try {
        val t = text.trim()
        if (t.startsWith("[")) JSONObject().put("data", JSONArray(t)) else JSONObject(t)
    } catch (e: Exception) {
        JSONObject()
    }

    /** The readable part of an error answer, like Tiiny Studio's err(). */
    fun error(): String {
        val o = json()
        for (k in listOf("error", "detail", "message", "msg")) {
            when (val v = o.opt(k)) {
                is String -> if (v.isNotBlank()) return v
                is JSONObject -> v.optString("message").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        val t = text.trim()
        return if (t.isEmpty()) "HTTP $code" else "HTTP $code: ${t.take(300)}"
    }
}

class MediaException(message: String) : Exception(message)

/**
 * HTTP to the Tiiny's port 80 for the media tools and chat (Bearer key), returning raw bytes so WAV, MP4 and GLB
 * answers come through untouched. Calls block: use them from Dispatchers.IO.
 */
class DeviceClient(url: String, private val key: String) {
    val base = TiinyApi.normalize(url)

    fun quote(s: String) = TiinyApi.quote(s)

    private fun open(method: String, path: String, timeoutSec: Int): HttpURLConnection {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = timeoutSec * 1000
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Accept", "*/*")
        return conn
    }

    private fun finish(conn: HttpURLConnection, method: String, path: String): Reply {
        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        val shown = if (bytes.size > 2000 || !looksLikeText(bytes)) "(${bytes.size} bytes)" else String(bytes, Charsets.UTF_8).take(400)
        ApiLog.add("$method $path -> HTTP $code: $shown")
        return Reply(code, bytes)
    }

    private fun looksLikeText(b: ByteArray) = b.take(64).all { it >= 9 || it < 0 }

    fun request(method: String, path: String, body: JSONObject? = null, timeoutSec: Int = 60): Reply {
        val conn = open(method, path, timeoutSec)
        try {
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            return finish(conn, method, path)
        } catch (e: MediaException) {
            throw e
        } catch (e: Exception) {
            ApiLog.add("$method $path -> ${e.javaClass.simpleName}: ${e.message}")
            throw MediaException("Lost the connection to the Tiiny (${e.message ?: e.javaClass.simpleName}).")
        } finally {
            conn.disconnect()
        }
    }

    class Part(val field: String, val filename: String, val mime: String, val data: ByteArray)

    fun multipart(path: String, fields: List<Pair<String, String>>, files: List<Part>, timeoutSec: Int): Reply {
        val boundary = UUID.randomUUID().toString().replace("-", "")
        val out = ByteArrayOutputStream()
        for ((k, v) in fields) {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n".toByteArray())
        }
        for (f in files) {
            out.write(
                ("--$boundary\r\nContent-Disposition: form-data; name=\"${f.field}\"; filename=\"${f.filename}\"\r\n" +
                    "Content-Type: ${f.mime}\r\n\r\n").toByteArray()
            )
            out.write(f.data)
            out.write("\r\n".toByteArray())
        }
        out.write("--$boundary--\r\n".toByteArray())
        val payload = out.toByteArray()
        val conn = open("POST", path, timeoutSec)
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }
            return finish(conn, "POST", path)
        } catch (e: Exception) {
            ApiLog.add("POST $path -> ${e.javaClass.simpleName}: ${e.message}")
            throw MediaException("Lost the connection to the Tiiny (${e.message ?: e.javaClass.simpleName}).")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Streams an OpenAI chat completion (server-sent events): calls onDelta(content, reasoning) for each piece.
     * Returns normally when the answer ends; `stop()` returning true aborts it.
     */
    fun streamChat(body: JSONObject, onDelta: (String, String) -> Unit, stop: () -> Boolean) {
        val conn = open("POST", "/v1/chat/completions", 600)
        try {
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.use { it.readBytes() } ?: ByteArray(0)
                val r = Reply(code, err)
                ApiLog.add("POST /v1/chat/completions -> HTTP $code: ${r.text.take(400)}")
                throw MediaException(r.error())
            }
            ApiLog.add("POST /v1/chat/completions -> HTTP $code (streaming)")
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    if (stop()) return
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val o = try {
                        JSONObject(data)
                    } catch (e: Exception) {
                        continue
                    }
                    val delta = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: continue
                    val content = delta.optString("content").takeIf { it != "null" } ?: ""
                    val reasoning = delta.optString("reasoning_content").takeIf { it != "null" }
                        ?: delta.optString("reasoning").takeIf { it != "null" } ?: ""
                    if (content.isNotEmpty() || reasoning.isNotEmpty()) onDelta(content, reasoning)
                }
            }
        } catch (e: MediaException) {
            throw e
        } catch (e: Exception) {
            if (stop()) return
            ApiLog.add("POST /v1/chat/completions -> ${e.javaClass.simpleName}: ${e.message}")
            throw MediaException("Lost the connection to the Tiiny (${e.message ?: e.javaClass.simpleName}).")
        } finally {
            conn.disconnect()
        }
    }
}

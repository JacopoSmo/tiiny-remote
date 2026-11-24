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
        if (key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Accept", "*/*")
        return conn
    }

    private fun finish(conn: HttpURLConnection, method: String, path: String, log: Boolean = true): Reply {
        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        // A successful answer is your content (chat replies, text read from documents, transcripts): only its size is
        // logged. Errors are short and say what went wrong, so their text is kept.
        val shown = if (code < 400 || bytes.size > 2000 || !looksLikeText(bytes)) "(${bytes.size} bytes)" else String(bytes, Charsets.UTF_8).take(400)
        if (log || code >= 400) ApiLog.add("$method $path -> HTTP $code: $shown")
        return Reply(code, bytes)
    }

    private fun looksLikeText(b: ByteArray) = b.take(64).all { it >= 9 || it < 0 }

    /**
     * The Tiiny answers "busy" for a while, not "broken", in three ways: error 150004 (it's already doing an inference,
     * for another app say; it can even come with HTTP 200), "xh2a backend fail" (a model that has just loaded, ~10 s),
     * and 502 "Upstream model server request failed" (~20 s after another model began loading). Such a request is
     * tried again after 5, 10 and 20 s (found by Story Lantern and Tiiny's API notes on TiinyVerse).
     */
    private fun busy(code: Int, bytes: ByteArray): Boolean {
        if (bytes.size > 4000) return false
        val t = String(bytes, Charsets.UTF_8)
        // 150004 as the error code, or anywhere in an answer that isn't a model's reply (that may mention the number).
        return BUSY_CODE.containsMatchIn(t) || ("150004" in t && "\"choices\"" !in t && "\"data\"" !in t) || "xh2a backend fail" in t || (code == 502 && "upstream model server request failed" in t.lowercase())
    }

    private val BUSY_CODE = Regex("\"(?:code|error_code|err_code)\"\\s*:\\s*\"?150004")

    private fun <T> retryBusy(path: String, call: () -> T, check: (T) -> Pair<Int, ByteArray>?): T {
        var wait = 5_000L
        repeat(3) {
            val r = call()
            val (code, bytes) = check(r) ?: return r
            if (!busy(code, bytes)) return r
            ApiLog.add("$path: the Tiiny is busy (HTTP $code); trying again in ${wait / 1000} s")
            Thread.sleep(wait)
            wait *= 2
        }
        return call()
    }

    /** log = false for frequent polls (device stats), so they don't push the useful entries out of the log. */
    fun request(method: String, path: String, body: JSONObject? = null, timeoutSec: Int = 60, log: Boolean = true): Reply =
        // A POST to /v1/… has a model at work: loads wait for it (NpuGate).
        if (method == "POST" && path.startsWith("/v1/")) NpuGate.work {
            retryBusy(path, { send(method, path, body, timeoutSec, log) }) { it.code to it.bytes }
        }
        else send(method, path, body, timeoutSec, log)

    private fun send(method: String, path: String, body: JSONObject?, timeoutSec: Int, log: Boolean): Reply {
        val conn = open(method, path, timeoutSec)
        try {
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            return finish(conn, method, path, log)
        } catch (e: MediaException) {
            throw e
        } catch (e: Exception) {
            if (log) ApiLog.add("$method $path -> ${e.javaClass.simpleName}: ${e.message}")
            throw MediaException("Lost the connection to the Tiiny (${e.message ?: e.javaClass.simpleName}).")
        } finally {
            conn.disconnect()
        }
    }

    class Part(val field: String, val filename: String, val mime: String, val data: ByteArray)

    fun multipart(path: String, fields: List<Pair<String, String>>, files: List<Part>, timeoutSec: Int): Reply =
        NpuGate.work { retryBusy(path, { sendParts(path, fields, files, timeoutSec) }) { it.code to it.bytes } }

    private fun sendParts(path: String, fields: List<Pair<String, String>>, files: List<Part>, timeoutSec: Int): Reply {
        val boundary = UUID.randomUUID().toString().replace("-", "")
        val out = ByteArrayOutputStream()
        for ((k, v) in fields) {
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n".toByteArray())
        }
        for (f in files) {
            // A quote or line break in a file's name would break the form: replace them.
            val fname = f.filename.replace(Regex("[\"\\\\\\r\\n]"), "_")
            out.write(
                ("--$boundary\r\nContent-Disposition: form-data; name=\"${f.field}\"; filename=\"$fname\"\r\n" +
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

    class ToolCall(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

    /**
     * A finished answer. tokens and seconds measure the writing (first piece of text to the last), from the Tiiny's
     * usage count if it sends one, else the pieces streamed (about one token each); alone: no other model of this app
     * was writing meanwhile.
     */
    class Completion(
        val content: String, val reasoning: String, val calls: List<ToolCall>, val finish: String,
        val tokens: Int = 0, val seconds: Double = 0.0, val alone: Boolean = false,
    )

    /**
     * Streams an OpenAI chat completion (server-sent events): onDelta(content, reasoning) for each piece of text;
     * tool calls are collected and returned at the end. `stop()` returning true ends it early.
     */
    fun streamChat(body: JSONObject, onDelta: (String, String) -> Unit, stop: () -> Boolean): Completion =
        NpuGate.work {
            // A "busy" answer comes before any text, so trying again can't repeat what was shown.
            var wait = 5_000L
            var tries = 0
            while (true) {
                try {
                    return@work stream(body, onDelta, stop)
                } catch (e: Busy) {
                    if (++tries > 3 || stop()) throw MediaException(e.message ?: "The Tiiny is busy.")
                    ApiLog.add("/v1/chat/completions: the Tiiny is busy; trying again in ${wait / 1000} s")
                    Thread.sleep(wait)
                    wait *= 2
                }
            }
            throw IllegalStateException()
        }

    private class Busy(msg: String) : Exception(msg)

    /**
     * A request answered with server-sent events (`event:` / `data:` blocks), e.g. image generation with previews.
     * onEvent(event, data) for each; the Reply is the error answer when it wasn't 2xx (then no events came), else
     * an empty 200.
     */
    fun sse(path: String, body: JSONObject, timeoutSec: Int, stop: () -> Boolean, onEvent: (String, String) -> Unit): Reply = NpuGate.work {
        val conn = open("POST", path, timeoutSec)
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
                ApiLog.add("POST $path -> HTTP $code: ${String(err).take(300)}")
                return@work Reply(code, err)
            }
            ApiLog.add("POST $path -> HTTP $code (streaming)")
            conn.inputStream.bufferedReader().use { reader ->
                var event = "message"
                val data = StringBuilder()
                while (!stop()) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) onEvent(event, data.toString())
                            event = "message"
                            data.setLength(0)
                        }
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trimStart())
                        }
                    }
                }
                if (data.isNotEmpty()) onEvent(event, data.toString())
            }
            Reply(200, ByteArray(0))
        } catch (e: MediaException) {
            throw e
        } catch (e: Exception) {
            ApiLog.add("POST $path -> ${e.javaClass.simpleName}: ${e.message}")
            throw MediaException("Lost the connection to the Tiiny (${e.message ?: e.javaClass.simpleName}).")
        } finally {
            conn.disconnect()
        }
    }

    private fun stream(body: JSONObject, onDelta: (String, String) -> Unit, stop: () -> Boolean): Completion {
        val conn = open("POST", "/v1/chat/completions", 900)
        var first = 0L
        var last = 0L
        var pieces = 0
        var usage = 0
        var alone = true
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val calls = java.util.TreeMap<Int, ToolCall>()
        var finish = ""
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
                if (busy(code, err)) throw Busy(r.error())
                throw MediaException(r.error())
            }
            ApiLog.add("POST /v1/chat/completions -> HTTP $code (streaming)")
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    if (stop()) break
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val o = try {
                        JSONObject(data)
                    } catch (e: Exception) {
                        continue
                    }
                    o.optJSONObject("usage")?.optInt("completion_tokens", 0)?.takeIf { it > 0 }?.let { usage = it }
                    val choice = o.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val fr = choice.optString("finish_reason")
                    if (fr.isNotBlank() && fr != "null") finish = fr
                    val delta = choice.optJSONObject("delta") ?: continue
                    val c = delta.optString("content").takeIf { it != "null" } ?: ""
                    val r = delta.optString("reasoning_content").takeIf { it != "null" }
                        ?: delta.optString("reasoning").takeIf { it != "null" } ?: ""
                    delta.optJSONArray("tool_calls")?.let { tcs ->
                        for (i in 0 until tcs.length()) {
                            val tc = tcs.optJSONObject(i) ?: continue
                            val slot = calls.getOrPut(tc.optInt("index", 0)) { ToolCall() }
                            val tid = tc.optString("id")
                            if (tid.isNotBlank() && tid != "null") slot.id = tid
                            val f = tc.optJSONObject("function")
                            if (f != null) {
                                val n = f.optString("name")
                                // Some servers repeat the whole name in every piece: add only what's new.
                                if (n.isNotEmpty() && n != "null" && slot.name != n) slot.name += n
                                val a = f.optString("arguments")
                                if (a != "null") slot.args.append(a)
                            }
                        }
                    }
                    content.append(c)
                    reasoning.append(r)
                    if (c.isNotEmpty() || r.isNotEmpty() || delta.has("tool_calls")) {
                        val now = System.nanoTime()
                        if (first == 0L) first = now
                        last = now
                        pieces++
                        if (NpuGate.count > 1) alone = false
                    }
                    if (c.isNotEmpty() || r.isNotEmpty()) onDelta(c, r)
                }
            }
        } catch (e: MediaException) {
            throw e
        } catch (e: Busy) {
            throw e
        } catch (e: Exception) {
            if (!stop()) {
                ApiLog.add("POST /v1/chat/completions -> ${e.javaClass.simpleName}: ${e.message}")
                throw MediaException("Lost the connection to the Tiiny (${e.message ?: e.javaClass.simpleName}).")
            }
        } finally {
            conn.disconnect()
        }
        // The first piece isn't timed (it's the wait before writing), so it's left out of the count too.
        val tokens = (if (usage > 0) usage else pieces) - 1
        return Completion(content.toString(), reasoning.toString(), calls.values.toList(), finish, tokens.coerceAtLeast(0), (last - first) / 1e9, alone)
    }
}

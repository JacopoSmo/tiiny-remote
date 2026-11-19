package app.tiinyremote

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TiinyException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { LOCKED, UNAUTHORIZED, UNREACHABLE, NOT_FOUND, HTTP }
}

data class ModelInfo(
    val id: String,
    val name: String,
    val type: String,
    val npuUsage: Double?,
    val loadTimeSec: Int?,
    /** "running", "loading" or "stopped", worked out the same way Tiiny Studio does. */
    val status: String,
    val busy: Boolean,
    /** e.g. "Text,Image" */
    val input: String = "",
    /** e.g. ["Image Editing", "Tool Use", "Voice Clone"] */
    val supported: List<String> = emptyList(),
    val desc: String = "",
) {
    val isChat get() = type == "Text Generation" || type == "Image-Text-to-Text"
    val seesImages get() = type == "Image-Text-to-Text" || input.contains("Image")
    val isImageGen get() = type == "Text-to-Image" || type == "Image-to-Image"
    /** Same test as Tiiny Studio: "Image Editing" listed, or images accepted as input. */
    val canEditImages get() = isImageGen && ("Image Editing" in supported || Regex("\\bImage\\b").containsMatchIn(input))
    val isOcr get() = type == "Image-to-Text"
    val isSpeech get() = type == "Text-to-Speech"
    val isAsr get() = type == "ASR"
    val isMusic get() = type == "Music Generation"
    val isSongModel get() = isMusic && Regex("song|lyric", RegexOption.IGNORE_CASE).containsMatchIn(id + desc.take(300))
    val isLoopModel get() = isMusic && !isSongModel
    val isVideo get() = type == "Text-to-Video"
    val is3d get() = type == "Image-to-3D"
    val running get() = status == "running"
}

data class Catalog(val models: List<ModelInfo>, val npuUsed: Double)

/** Answer of /api/v1/models/{id}/unload_candidate: does the model fit, and what to unload if not. */
data class Plan(
    val unloadRequired: Boolean,
    val npuRequired: Double?,
    val npuAvailable: Double?,
    val candidateId: String?,
    val candidateName: String?,
    val candidateNpu: Double?,
)

/** The last few requests and answers, shown under Settings to help debug a firmware that answers differently. */
object ApiLog {
    private val entries = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val secretFields = Regex("(\"(?:auth_key|attestation)\"\\s*:\\s*\")[^\"]*\"")

    /** Email addresses, login keys and device attestations never reach the log, so a shared log stays private. */
    fun redact(text: String): String =
        secretFields.replace(email.replace(text, "***@***"), "$1***\"")

    @Synchronized
    fun add(line: String) {
        entries.addFirst("${time.format(Date())}  ${redact(line)}")
        while (entries.size > 100) entries.removeLast()
    }

    @Synchronized
    fun all(): List<String> = entries.toList()
}

/**
 * The Tiiny's own HTTP API on port 80, called the same way Tiiny Studio calls it
 * (Bearer key; 502 on every route means the device is locked after a restart).
 */
class TiinyApi(url: String, private val key: String) {
    private val base = normalize(url)

    companion object {
        /** The full model list is written to the log once per app start, to help debugging. */
        @Volatile
        private var catalogLogged = false

        fun normalize(url: String): String {
            var u = url.trim().trimEnd('/')
            if (u.endsWith("/v1")) u = u.removeSuffix("/v1").trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            return u
        }

        /** Same encoding as Tiiny Studio: everything but letters, digits and _.-~ becomes %XX (per UTF-8 byte). */
        fun quote(id: String): String {
            val sb = StringBuilder()
            for (b in id.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt() and 0xFF
                val ch = c.toChar()
                if (c < 128 && (ch.isLetterOrDigit() || ch in "_.-~")) sb.append(ch)
                else sb.append('%').append(String.format(Locale.ROOT, "%02X", c))
            }
            return sb.toString()
        }
    }

    /** `routine` marks the status checks made every few seconds: those are logged only when they fail. */
    private fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        timeoutSec: Int = 15,
        routine: Boolean = false,
    ): Pair<Int, String> {
        val conn = try {
            URL(base + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw TiinyException(TiinyException.Kind.UNREACHABLE, "Invalid Tiiny address: $base")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = timeoutSec * 1000
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            } else if (method == "POST") {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
                conn.outputStream.close()
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (!routine || code !in 200..299) {
                val sent = if (body != null) "\n  sent: $body" else ""
                ApiLog.add("$method $base$path$sent\n  -> HTTP $code: ${text.take(1500)}")
            }
            return code to text
        } catch (e: Exception) {
            // Network errors (and anything unexpected) become one readable error instead of a crash.
            ApiLog.add("$method $base$path\n  -> ${e.javaClass.simpleName}: ${e.message}")
            throw TiinyException(
                TiinyException.Kind.UNREACHABLE,
                "Can't reach the Tiiny at $base (${e.message ?: e.javaClass.simpleName}). " +
                    "Check the address, and that you're on home wifi or WireGuard.",
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun json(res: Pair<Int, String>): JSONObject {
        val (code, text) = res
        when {
            code == 502 -> throw TiinyException(
                TiinyException.Kind.LOCKED,
                "Your Tiiny is locked after a restart.",
            )
            code == 401 || code == 403 -> throw TiinyException(
                TiinyException.Kind.UNAUTHORIZED,
                "The Tiiny rejected the API key. Copy it again from TiinyOS → Settings → API Key.",
            )
            code == 404 -> throw TiinyException(
                TiinyException.Kind.NOT_FOUND,
                errorText(text),
            )
            code !in 200..299 -> throw TiinyException(
                TiinyException.Kind.HTTP,
                "HTTP $code: ${errorText(text)}",
            )
        }
        val t = text.trim()
        if (t.isEmpty()) return JSONObject()
        return try {
            if (t.startsWith("[")) JSONObject().put("data", JSONArray(t)) else JSONObject(t)
        } catch (e: Exception) {
            throw TiinyException(TiinyException.Kind.HTTP, "Unexpected answer (not JSON): ${t.take(120)}")
        }
    }

    private fun errorText(text: String): String {
        val t = text.trim()
        return try {
            val o = JSONObject(t)
            o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: o.optString("detail").takeIf { it.isNotBlank() }
                ?: o.optString("message").takeIf { it.isNotBlank() }
                ?: t.take(200)
        } catch (e: Exception) {
            t.take(200)
        }
    }

    /** Installed models with live status: running, loading (pending) or stopped. */
    fun catalog(): Catalog {
        val inst = json(call("GET", "/api/v1/models/?including_downloading=false", routine = true))
        val run = json(call("GET", "/api/v1/models/running", routine = true))
        if (!catalogLogged) {
            catalogLogged = true
            ApiLog.add("Model list as the Tiiny reports it:\n${inst.toString().take(6000)}")
        }

        val instances = run.optJSONObject("instances") ?: JSONObject()
        val running = instances.optJSONArray("running") ?: JSONArray()
        val runningById = HashMap<String, JSONObject>()
        for (i in 0 until running.length()) {
            val r = running.optJSONObject(i) ?: continue
            r.optString("model_id").takeIf { it.isNotEmpty() }?.let { runningById[it] = r }
        }
        val pending = HashSet<String>()
        instances.optJSONArray("pending")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.opt(i)
                if (p is JSONObject) p.optString("model_id").takeIf { it.isNotEmpty() }?.let { pending.add(it) }
                else if (p is String) pending.add(p)
            }
        }
        run.optJSONArray("pending")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotEmpty() }?.let { pending.add(it) }
        }

        val data = inst.optJSONArray("data") ?: JSONArray()
        val models = ArrayList<ModelInfo>()
        for (i in 0 until data.length()) {
            val m = data.optJSONObject(i) ?: continue
            val id = m.optString("id")
            if (id.isEmpty()) continue
            val r = runningById[id]
            val status = when {
                r != null -> "running"
                id in pending -> "loading"
                else -> "stopped"
            }
            val display = m.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
                ?: id.substringAfterLast('/')
            models.add(
                ModelInfo(
                    id = id,
                    name = display,
                    type = m.optString("type").takeIf { it != "null" } ?: "",
                    npuUsage = m.optDouble("npu_usage").takeIf { !it.isNaN() },
                    loadTimeSec = m.optDouble("load_time").takeIf { !it.isNaN() }?.toInt(),
                    status = status,
                    busy = r != null && r.optInt("active_request_count", 0) > 0,
                    input = m.optString("input").takeIf { it != "null" } ?: "",
                    supported = m.optJSONArray("supported")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                    desc = m.optString("desc").takeIf { it != "null" } ?: "",
                )
            )
        }
        var used = 0.0
        for (r in runningById.values) used += r.optDouble("npu_usage").takeIf { !it.isNaN() } ?: 0.0
        return Catalog(models, used)
    }

    /** Calls /api/v1/models/{id}{suffix}, with the id encoded like Tiiny Studio does (Qwen/X becomes Qwen%2FX). */
    private fun modelCall(method: String, id: String, suffix: String, body: JSONObject? = null, timeoutSec: Int): JSONObject =
        json(call(method, "/api/v1/models/${quote(id)}$suffix", body, timeoutSec))

    fun plan(id: String): Plan {
        val p = try {
            modelCall("GET", id, "/unload_candidate", timeoutSec = 30)
        } catch (e: TiinyException) {
            // The room check is only a courtesy: if it fails for another reason than an unknown model, just try the load.
            if (e.kind == TiinyException.Kind.HTTP) {
                ApiLog.add("unload_candidate failed (${e.message}); loading without the room check")
                return Plan(false, null, null, null, null, null)
            }
            throw e
        }
        val cand = p.optJSONObject("candidate")
        var candId = cand?.optString("model_id")?.takeIf { it.isNotEmpty() }
        if (candId == null) {
            // Some answers list the models to unload as an array instead.
            val arr = p.optJSONArray("model_id")
            if (arr != null && arr.length() > 0) candId = arr.optString(0)
        }
        return Plan(
            unloadRequired = p.optBoolean("unload_required", false),
            npuRequired = p.optDouble("npu_required").takeIf { !it.isNaN() },
            npuAvailable = p.optDouble("npu_available").takeIf { !it.isNaN() },
            candidateId = candId,
            candidateName = (cand?.optString("fullname")?.takeIf { it.isNotEmpty() } ?: candId)
                ?.substringAfterLast('/'),
            candidateNpu = cand?.optDouble("npu_usage")?.takeIf { !it.isNaN() },
        )
    }

    /** Starts loading; the device answers once the load is accepted and finishes in the background. */
    fun launch(id: String) {
        modelCall("POST", id, "/launch", JSONObject().put("model_id", id), timeoutSec = 120)
    }

    fun stop(id: String) {
        modelCall("POST", id, "/stop", timeoutSec = 120)
    }
}

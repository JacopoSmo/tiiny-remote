package app.tiinyremote

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class TiinyException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { LOCKED, UNAUTHORIZED, UNREACHABLE, NOT_FOUND, HTTP }
}

private val moeName = Regex("(?i)-A(\\d+(?:\\.\\d+)?)B(?![a-z])")
private val moeDesc = Regex("(?i)mixture[- ]of[- ]experts|\\bMoE\\b")

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
    /** From the model's "thinking" object: does it reason at all, and can that be switched off? */
    val thinkingSupported: Boolean = false,
    val thinkingToggleable: Boolean = false,
    /** The name the Tiiny gives it; `name` may be your shorter one for this app. */
    val fullName: String = "",
    /** The store has a newer version of this model. */
    val needUpdate: Boolean = false,
    val versionLabel: String = "",
    val sizeBytes: Long = 0,
) {
    /** Can reason before answering ("Thinking"). */
    val thinks get() = isChat && (thinkingSupported || "Reasoning" in supported)
    /** Always reasons; only the effort changes (like gpt-oss). */
    val alwaysThinks get() = thinks && !thinkingToggleable
    /** Marked for tool use: can call the chat's tools ("Can make things"). */
    val makesThings get() = isChat && "Tool Use" in supported

    /** Short labels for what a chat model can do. */
    val abilities: List<String>
        get() = if (!isChat) emptyList() else listOfNotNull(
            when {
                alwaysThinks -> "Always thinks"
                thinks -> "Thinks"
                else -> null
            },
            if (makesThings) "Makes things" else null,
            if (seesImages) "Sees images" else null,
        )

    /**
     * A mixture of experts: holds many parameters but uses only a few for each word ("A3B" = about 3 billion active),
     * so it writes much faster than a dense model of the same size. Known from the name (…-A3B, gpt-oss) or the
     * description.
     */
    val isMoE: Boolean
        get() = moeName.containsMatchIn(id) || id.contains("gpt-oss", true) || moeDesc.containsMatchIn(desc)

    /** "3B" for a …-35B-A3B model, when the name says how many parameters are active per word. */
    val activeParams: String?
        get() = moeName.find(id)?.groupValues?.get(1)?.let { "${it}B" } ?: if (id.contains("gpt-oss", true)) "~4B" else null

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
    val isEmbedding get() = type == "Text Embedding"
    val isReranker get() = type == "Text Reranking"
    val isClassifier get() = type == "Text Classification"
    val running get() = status == "running"
}

data class Catalog(val models: List<ModelInfo>, val npuUsed: Double)

/** A model in TiinyOS's Model Store (GET /api/v1/models/online_models). */
data class StoreModel(
    val id: String,
    val name: String,
    val type: String,
    val params: String,
    val npuUsage: Double?,
    val sizeBytes: Long,
    val desc: String,
    val input: String,
    val output: String,
    val supported: List<String>,
    val thinks: Boolean,
    val versionLabel: String,
    val contextWindow: Int?,
)

/** A model the Tiiny is downloading (or has paused): status as the Tiiny says it, progress in %. */
data class Download(val id: String, val name: String, val status: String, val progress: Double?) {
    val paused get() = status.contains("pause", true)
}

/** Answer of /api/v1/models/{id}/unload_candidate: does the model fit, and what to unload if not. */
data class Plan(
    val unloadRequired: Boolean,
    val npuRequired: Double?,
    val npuAvailable: Double?,
    val candidateId: String?,
    val candidateName: String?,
    val candidateNpu: Double?,
)

/** One line of the unified log: when, which kind (see ApiLog.TYPES), the text, and whether it reports a problem. */
data class LogEntry(val ms: Long, val type: String, val text: String, val error: Boolean, val time: String)

/** The app's one log: device API calls, models, unlocking, VPN, chat, web search and the creation tools. */
object ApiLog {
    /** The kinds of entries, in the order the Activity tab shows its filter buttons. */
    val TYPES = listOf("Device", "Models", "Unlock", "VPN", "Chat", "Search", "Create")

    private val _entries = kotlinx.coroutines.flow.MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: kotlinx.coroutines.flow.StateFlow<List<LogEntry>> = _entries

    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    /**
     * Fields whose values never reach the log: login keys, tokens, passwords, the account's name and the device's
     * serial number. The closing quote is optional: callers cut long answers before logging them, and a value cut
     * in the middle must still be hidden.
     */
    private val secretFields = Regex(
        "(\"(?:auth_key|authKey|attestation|token|access_token|refresh_token|id_token|password|api_key|apiKey|secret|sn|serial|serial_number|user_name|username)\"\\s*:\\s*\")[^\"]*\"?",
    )
    private val bearer = Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}")
    /** The same in plain text, as a command line prints it ("token: …", "password=…"), and any JWT-shaped token. */
    private val plainSecret = Regex("(?i)\\b(auth[_ ]?key|token|password|passwd|api[_ ]?key|secret)(\\s*[:=]\\s*)[^\\s\",}]+")
    private val jwt = Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]*")
    /** A login written into an address (http://user:password@host). */
    private val urlLogin = Regex("(?i)(https?://)[^/\\s:@]+:[^/\\s@]+@")
    /** File paths from a computer (C:\\Users\\…, /C$/Users/…, /home/…, /Users/…): they name people and folders. */
    private val localPath = Regex("(?i)(?:[A-Z]:|/[A-Z]\\$)?[\\\\/](?:Users|home)[\\\\/][^\"\\s,}]*")
    private val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
    private val problem = Regex("-> HTTP [45]\\d\\d|Exception|failed|didn't|refused|not reachable|error", RegexOption.IGNORE_CASE)

    /** Email addresses, login keys and device attestations never reach the log, so a shared log stays private. */
    fun redact(text: String): String {
        var t = email.replace(text, "***@***")
        t = secretFields.replace(t, "$1***\"")
        t = bearer.replace(t, "$1***")
        t = plainSecret.replace(t, "$1$2***")
        t = jwt.replace(t, "***")
        t = urlLogin.replace(t, "$1***@")
        return localPath.replace(t, "[local file path]")
    }

    /** Which kind an entry is, from what it says (when the caller doesn't name it). */
    private fun classify(line: String): String = when {
        line.contains("chat/completions") -> "Chat"
        line.startsWith("web search") || line.startsWith("news via") -> "Search"
        line.contains("auth.api.tiiny") || line.startsWith("Logged in") || line.contains("unlock", true) -> "Unlock"
        line.contains("/api/v1/models") || line.contains("/v1/models ") || line.startsWith("Loading ") || line.startsWith("Unloading ") ||
            line.startsWith("Model list") || line.startsWith("unload_candidate") -> "Models"
        Regex("^(GET|POST|DELETE|PUT) /v1/").containsMatchIn(line) || line.contains(" failed: ") -> "Create"
        else -> "Device"
    }

    fun add(line: String, type: String? = null) {
        val text = redact(line)
        val e = LogEntry(System.currentTimeMillis(), type ?: classify(text), text, problem.containsMatchIn(text), clock.format(java.time.LocalTime.now()))
        synchronized(this) { _entries.value = (listOf(e) + _entries.value).take(400) }
    }

    fun clear() {
        synchronized(this) { _entries.value = emptyList() }
    }

    /** Newest first, as text: "08:55:07  [VPN] …". */
    fun all(): List<String> = _entries.value.map { "${it.time}  [${it.type}] ${it.text}" }
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

        /**
         * The last failure of the checks made every few seconds. The same failure again isn't logged: while the Tiiny
         * is offline or locked for hours it would push everything else out of the log.
         */
        @Volatile
        private var lastRoutineProblem: String? = null

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
            if (routine && code in 200..299) {
                lastRoutineProblem = null
            } else if (!routine || lastRoutineProblem != "$method $path $code") {
                if (routine) lastRoutineProblem = "$method $path $code"
                val sent = if (body != null) "\n  sent: $body" else ""
                ApiLog.add("$method $base$path$sent\n  -> HTTP $code: ${text.take(1500)}")
            }
            return code to text
        } catch (e: Exception) {
            // Network errors (and anything unexpected) become one readable error instead of a crash.
            val problem = "$method $path ${e.javaClass.simpleName}"
            if (!routine || lastRoutineProblem != problem) {
                if (routine) lastRoutineProblem = problem
                ApiLog.add("$method $base$path\n  -> ${e.javaClass.simpleName}: ${e.message}")
            }
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
            // One short line per model (id, type, state, NPU share): the store texts would fill the shared log.
            val arr = inst.optJSONArray("data") ?: JSONArray()
            val lines = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { m -> "${m.optString("id")} · ${m.optString("type")} · ${m.optString("status")} · ${m.optInt("npu_usage")}% NPU" }
            }
            ApiLog.add("Model list as the Tiiny reports it (${lines.size}):\n" + lines.joinToString("\n"))
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
                    thinkingSupported = m.optJSONObject("thinking")?.optBoolean("supported", false) ?: false,
                    thinkingToggleable = m.optJSONObject("thinking")?.optBoolean("toggleable", false) ?: false,
                    fullName = display,
                    needUpdate = m.optBoolean("need_update", false),
                    versionLabel = m.optString("version_label").takeIf { it != "null" } ?: "",
                    sizeBytes = m.optLong("total_size", m.optLong("size", 0)),
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

    // ---------- the Model Store and downloads (the Tiiny downloads over its own Wi-Fi) ----------

    /** Answers 2xx or throws the usual readable error. */
    private fun ok(res: Pair<Int, String>): String {
        if (res.first !in 200..299) json(res)
        return res.second
    }

    fun store(): List<StoreModel> {
        val t = ok(call("GET", "/api/v1/models/online_models", timeoutSec = 30, routine = true)).trim()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optString("model_id").ifBlank { m.optString("id") }
            if (id.isBlank()) return@mapNotNull null
            val tags = m.optJSONArray("tags")
            val tagSize = (0 until (tags?.length() ?: 0)).maxOfOrNull { tags!!.optJSONObject(it)?.optLong("size", 0) ?: 0 } ?: 0
            fun str(k: String) = m.optString(k).takeIf { it != "null" } ?: ""
            StoreModel(
                id = id,
                name = str("display_name").ifBlank { str("name") }.ifBlank { id.substringAfterLast('/') },
                type = str("type"),
                params = str("params"),
                npuUsage = m.optDouble("npu_usage").takeIf { !it.isNaN() },
                sizeBytes = maxOf(tagSize, m.optLong("total_size", 0), m.optLong("size", 0)),
                desc = str("desc"),
                input = str("input"),
                output = str("output"),
                supported = m.optJSONArray("supported")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                thinks = m.optJSONObject("thinking")?.optBoolean("supported", false) ?: false,
                versionLabel = str("version_label"),
                contextWindow = m.optInt("context_window", 0).takeIf { it > 0 },
            )
        }
    }

    /** Models being downloaded or paused, from the full list (including_downloading=true). */
    fun downloads(): List<Download> {
        val o = json(call("GET", "/api/v1/models/?including_downloading=true", timeoutSec = 15, routine = true))
        val data = o.optJSONArray("data") ?: JSONArray()
        return (0 until data.length()).mapNotNull { i ->
            val m = data.optJSONObject(i) ?: return@mapNotNull null
            val st = m.optString("status")
            if (st == "downloaded" || st.isBlank() || st == "null") return@mapNotNull null
            val id = m.optString("id").ifBlank { m.optString("model_id") }
            Download(
                id,
                m.optString("display_name").takeIf { it.isNotBlank() && it != "null" } ?: id.substringAfterLast('/'),
                st,
                m.optDouble("progress").takeIf { !it.isNaN() },
            )
        }
    }

    /** Starts (or resumes) downloading a model; for an installed one with an update, fetches the new version. */
    fun download(id: String) {
        ok(call("POST", "/api/v1/models/${quote(id)}/download", JSONObject(), 60)) // the device expects an empty JSON body
    }

    fun pauseDownload(id: String) {
        ok(call("POST", "/api/v1/models/${quote(id)}/pause_download", JSONObject(), 30))
    }

    /** Deletes a downloaded model (or a partly downloaded one) from the Tiiny. */
    fun delete(id: String) {
        ok(call("DELETE", "/api/v1/models/${quote(id)}", null, 60))
    }

    /** Starts loading; the device answers once the load is accepted and finishes in the background. */
    fun launch(id: String) {
        modelCall("POST", id, "/launch", JSONObject().put("model_id", id), timeoutSec = 120)
    }

    fun stop(id: String) {
        modelCall("POST", id, "/stop", timeoutSec = 120)
    }
}

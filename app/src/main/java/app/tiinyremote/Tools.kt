package app.tiinyremote

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/** How a running tool reports progress, and how it learns it was cancelled. */
class Progress(
    val update: (message: String, percent: Int?) -> Unit,
    val cancelled: () -> Boolean,
)

/** Wraps an id the device gave a long job (video or 3D task) so a cancel can reach it. */
class RemoteJob {
    @Volatile
    var cancelPath: Pair<String, String>? = null // method to path
}

/**
 * The media tools, sending the same requests Tiiny Studio sends (rust/studio-core/src/media in its repository).
 * Everything here blocks: run it on Dispatchers.IO.
 */
object Tools {

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.DEFAULT)

    private fun check(p: Progress) {
        if (p.cancelled()) throw MediaException("Cancelled")
    }

    private fun short(model: String) = model.substringAfterLast('/')

    fun fmtDuration(sec: Long): String = when {
        sec >= 3600 -> "${sec / 3600} h ${sec % 3600 / 60} min"
        sec >= 60 -> "${sec / 60} min" + (if (sec % 60 > 0) " ${sec % 60} s" else "")
        else -> "$sec s"
    }

    // ---------------- loading models for a job (Tiiny Studio's make_room / put_back) ----------------

    /** log = false for the checks repeated every few seconds while a model loads (failures are still logged). */
    fun runningIds(c: DeviceClient, log: Boolean = true): Set<String> {
        val run = c.request("GET", "/api/v1/models/running", null, 15, log = log).json()
        val list = run.optJSONObject("instances")?.optJSONArray("running") ?: return emptySet()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("model_id")?.takeIf { s -> s.isNotBlank() } }.toSet()
    }

    private fun waitLoaded(c: DeviceClient, model: String, p: Progress) {
        val until = System.currentTimeMillis() + 420_000 // the biggest models take about 2 minutes
        while (System.currentTimeMillis() < until) {
            check(p)
            if (model in runningIds(c, log = false)) return
            Thread.sleep(3_000)
        }
        throw MediaException("${short(model)} didn't finish loading.")
    }

    /** Loads `target`, unloading whatever the Tiiny says must go to make room. Returns what it unloaded. */
    fun makeRoom(c: DeviceClient, target: String, p: Progress): List<String> {
        if (target in runningIds(c)) return emptyList()
        val unloaded = ArrayList<String>()
        for (round in 0 until 4) {
            val plan = c.request("GET", "/api/v1/models/${c.quote(target)}/unload_candidate", null, 30)
            if (!plan.ok) throw MediaException(plan.error())
            val o = plan.json()
            if (!o.optBoolean("unload_required", false)) break
            val victims = o.optJSONArray("model_id")?.let { a -> (0 until a.length()).map { a.optString(it) } }
                ?: listOfNotNull(o.optJSONObject("candidate")?.optString("model_id"))
            for (v in victims.filter { it.isNotBlank() }) {
                p.update("Unloading ${short(v)} to make room", null)
                val r = c.request("POST", "/api/v1/models/${c.quote(v)}/stop", null, 120)
                if (!r.ok) throw MediaException(r.error())
                unloaded.add(v)
            }
        }
        p.update("Loading ${short(target)}", null)
        val r = c.request("POST", "/api/v1/models/${c.quote(target)}/launch", JSONObject().put("model_id", target), 120)
        if (!r.ok) throw MediaException(r.error())
        waitLoaded(c, target, p)
        return unloaded
    }

    /** After a job: if models had to make room, unload `target` and load them back. */
    fun putBack(c: DeviceClient, target: String, unloaded: List<String>, p: Progress) {
        if (unloaded.isEmpty()) return
        p.update("Unloading ${short(target)}", null)
        c.request("POST", "/api/v1/models/${c.quote(target)}/stop", null, 120)
        for (m in unloaded) {
            p.update("Loading ${short(m)} back", null)
            val r = c.request("POST", "/api/v1/models/${c.quote(m)}/launch", JSONObject().put("model_id", m), 120)
            if (!r.ok) throw MediaException("Couldn't load ${short(m)} back: ${r.error()}")
        }
        for (m in unloaded) waitLoaded(c, m, p)
    }

    /**
     * How a chat model thinks, from its /v1/models entry: "switch" (enable_thinking on or off), "always"
     * (reasons whatever it's sent, like gpt-oss: only the effort can change) or "none".
     */
    /** What thinkingMode found per model, so helpers called several times at once don't each ask again. */
    private val thinkingModes = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun thinkingMode(c: DeviceClient, model: String): String {
        thinkingModes[c.base + " " + model]?.let { return it }
        val data = try {
            c.request("GET", "/v1/models", null, 15).json().optJSONArray("data")
        } catch (e: Exception) {
            null
        }
        var t: JSONObject? = null
        if (data != null) for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            if (item.optString("id") == model) t = item.optJSONObject("thinking")
        }
        val mode = when {
            t == null -> if (model.contains("gpt-oss")) "always" else "switch"
            !t.optBoolean("supported", false) -> "none"
            t.optBoolean("toggleable", false) -> "switch"
            else -> "always"
        }
        if (t != null) thinkingModes[c.base + " " + model] = mode // only what the Tiiny actually said is kept
        return mode
    }

    // ---------------- images ----------------

    fun image(
        c: DeviceClient, store: ResultStore, model: String, prompt: String, negative: String,
        size: String, steps: Int?, seed: Long?, sources: List<String>, p: Progress,
        count: Int = 1, guidance: Double? = null,
    ): ResultEntry {
        val body = JSONObject().put("model", model).put("prompt", prompt).put("n", 1)
            .put("response_format", "b64_json")
        if (negative.isNotBlank()) body.put("negative_prompt", negative)
        if (size.isNotBlank()) body.put("size", size)
        steps?.let { body.put("steps", it) }
        guidance?.let { body.put("guidance_scale", it) }
        if (sources.size == 1) body.put("image", sources[0])
        else if (sources.size > 1) body.put("images", JSONArray(sources))
        val n = count.coerceIn(1, 4)
        val verb = if (sources.isEmpty()) "Generating" else "Editing"
        val id = store.newId()
        val files = ArrayList<String>()
        // The device makes one image per request, so several are separate requests with consecutive seeds.
        for (i in 0 until n) {
            seed?.let { body.put("seed", it + i) }
            var got: String? = null
            for (attempt in 0..6) {
                check(p)
                p.update(
                    if (attempt > 0) "Waiting for the model to warm up" else if (n == 1) "$verb the image" else "$verb image ${i + 1} of $n",
                    if (n > 1) i * 100 / n else null,
                )
                val r = c.request("POST", "/v1/images/generations", body, 3600)
                val data = r.json().optJSONArray("data")
                if (r.ok && data != null && data.length() > 0) {
                    got = data.getJSONObject(0).optString("b64_json").takeIf { it.isNotEmpty() }
                    if (got == null) throw MediaException("The Tiiny sent no picture back.")
                    break
                }
                // A freshly loaded image model answers "backend fail" for a while: wait and try again.
                if (!r.error().contains("backend fail") || attempt == 6) {
                    if (files.isNotEmpty()) break // keep the images that did come out
                    throw MediaException(r.error())
                }
                Thread.sleep(10_000)
            }
            if (got == null) break
            files.add(store.saveFile(id, if (n == 1) "image.png" else "image_${i + 1}.png", unb64(got)))
        }
        val mode = if (sources.isEmpty()) "Image" else "Edited image"
        val details = listOfNotNull(
            size.takeIf { it.isNotBlank() }?.replace("x", " × "),
            steps?.let { "$it steps" }, guidance?.let { "cfg $it" }, seed?.let { "seed $it" },
        ).joinToString(" · ")
        return ResultEntry(id, "image", "$mode: ${prompt.take(60)}", files, "$prompt\n$details".trim(), model, System.currentTimeMillis())
    }

    /** Reads the text in a picture with an OCR model (Image-to-Text). */
    fun ocr(c: DeviceClient, store: ResultStore, model: String, image: String, p: Progress): ResultEntry {
        p.update("Reading the text", null)
        val r = c.request(
            "POST", "/v1/ocr?model=${c.quote(model)}",
            JSONObject().put("input", image).put("response_format", "json"), 300,
        )
        var text: String? = null
        if (r.ok) {
            text = r.json().optJSONArray("data")?.optJSONObject(0)?.optString("text")
        } else if (r.error().contains("endpoint not found", ignoreCase = true)) {
            // GLM-OCR isn't routed by the gateway: ask its own container (Wi-Fi/LAN only), as Tiiny Studio does.
            text = ocrDirect(c, model, image)
        }
        if (text == null) throw MediaException(r.error())
        val id = store.newId()
        return ResultEntry(id, "ocr", "Text from image", emptyList(), text.trim(), model, System.currentTimeMillis())
    }

    /** Reads a PDF's rendered pages one by one into one text, with page markers. */
    fun ocrPages(c: DeviceClient, store: ResultStore, model: String, name: String, pages: List<String>, total: Int, p: Progress): ResultEntry {
        val out = StringBuilder()
        for ((i, page) in pages.withIndex()) {
            check(p)
            p.update("Reading page ${i + 1} of ${pages.size}", i * 100 / pages.size)
            val e = ocr(c, store, model, page, Progress({ _, _ -> }, p.cancelled))
            if (pages.size > 1) out.append("— Page ${i + 1} —\n")
            out.append(e.text.trim()).append("\n\n")
        }
        if (total > pages.size) out.append("(Only the first ${pages.size} of $total pages were read.)")
        val id = store.newId()
        return ResultEntry(id, "ocr", "Text from $name", emptyList(), out.toString().trim(), model, System.currentTimeMillis())
    }

    private fun ocrDirect(c: DeviceClient, model: String, image: String): String {
        val port = runningPort(c, model)
            ?: throw MediaException("${short(model)} only works over Wi-Fi/LAN. Use PP-OCRv6 on this connection.")
        val host = URL(c.base).host
        val direct = DeviceClient("http://$host:$port", "")
        val name = direct.request("GET", "/v1/models", null, 10).json()
            .optJSONArray("data")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() } ?: short(model)
        val content = JSONArray()
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image)))
            .put(JSONObject().put("type", "text").put("text", "Text Recognition:"))
        val body = JSONObject().put("model", name).put("temperature", 0).put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val r = direct.request("POST", "/v1/chat/completions", body, 300)
        if (!r.ok) throw MediaException(r.error())
        return r.json().optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: throw MediaException("The OCR model sent no text back.")
    }

    private fun runningPort(c: DeviceClient, model: String): Int? {
        val run = c.request("GET", "/api/v1/models/running", null, 15).json()
        val list = run.optJSONObject("instances")?.optJSONArray("running") ?: return null
        for (i in 0 until list.length()) {
            val r = list.optJSONObject(i) ?: continue
            if (r.optString("model_id") == model) return r.optInt("port").takeIf { it > 0 }
        }
        return null
    }

    /** Describes a picture (or answers a question about it) with a chat model that sees images. */
    fun describe(c: DeviceClient, store: ResultStore, model: String, image: String, question: String, p: Progress): ResultEntry {
        p.update("Looking at the picture", null)
        val q = question.ifBlank { "Describe this image in detail." }
        val content = JSONArray()
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image)))
            .put(JSONObject().put("type", "text").put("text", q))
        val body = JSONObject().put("model", model).put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val r = c.request("POST", "/v1/chat/completions", body, 900)
        if (!r.ok) throw MediaException(r.error())
        val text = r.json().optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.replace(Regex("(?s)<think>.*?</think>"), "")?.trim()
            ?: throw MediaException("The model sent no answer.")
        val id = store.newId()
        return ResultEntry(id, "description", "About the image: ${q.take(50)}", emptyList(), text, model, System.currentTimeMillis())
    }

    // ---------------- voice ----------------

    data class VoiceCaps(
        val clone: Boolean,
        val speakers: List<String>,
        val design: Boolean,
        val instruct: Boolean,
        val languages: List<String>,
    )

    /** What a loaded voice model offers (its /v1/capabilities). */
    fun voiceCaps(c: DeviceClient, model: String): VoiceCaps? {
        val r = c.request("GET", "/v1/capabilities?model=${c.quote(model)}", null, 15)
        if (!r.ok) return null
        val o = r.json().let { it.optJSONObject("data") ?: it }
        fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return VoiceCaps(
            clone = o.optBoolean("support_voice_clone", true),
            speakers = if (o.optBoolean("support_custom_voice", false)) list("supported_speakers") else emptyList(),
            design = o.optBoolean("support_voice_design", false),
            instruct = o.optBoolean("support_instruct", false),
            languages = list("supported_languages"),
        )
    }

    /** Splits long text at sentence ends into pieces the voice models take (Tiiny Studio: 320 characters). */
    private fun chunks(text: String, max: Int = 320): List<String> {
        val out = ArrayList<String>()
        val sentences = Regex("(?<=[.!?。！？])\\s+|\\n+").split(text.trim()).filter { it.isNotBlank() }
        val cur = StringBuilder()
        for (s in sentences) {
            if (cur.isNotEmpty() && cur.length + s.length + 1 > max) {
                out.add(cur.toString())
                cur.clear()
            }
            var rest = s
            while (rest.length > max) {
                val cut = rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
                out.add(rest.substring(0, cut))
                rest = rest.substring(cut).trim()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(rest)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    fun speech(
        c: DeviceClient, store: ResultStore, model: String, text: String,
        speaker: String?, design: String?, refAudio: ByteArray?, refText: String?,
        language: String, instruct: String, p: Progress,
    ): ResultEntry {
        val body = JSONObject().put("model", model).put("response_format", "wav")
        when {
            !speaker.isNullOrBlank() -> body.put("voice", speaker)
            !design.isNullOrBlank() -> body.put("instruct", design)
            else -> {
                if (refAudio == null) throw MediaException("This voice needs a reference clip: record or pick one.")
                if (refText.isNullOrBlank()) throw MediaException("Type what's said in the reference clip: cloning needs both.")
                body.put("ref_audio", b64(refAudio)).put("ref_text", refText.trim())
                if (instruct.isNotBlank()) body.put("instruct", instruct.trim())
            }
        }
        if (language.isNotBlank()) body.put("language", language)
        val parts = chunks(text)
        val wavs = ArrayList<ByteArray>()
        for ((i, part) in parts.withIndex()) {
            check(p)
            p.update(
                if (parts.size == 1) "Speaking" else "Speaking part ${i + 1} of ${parts.size}",
                if (parts.size > 1) i * 100 / parts.size else null,
            )
            body.put("input", part)
            val r = c.request("POST", "/v1/audio/speech", body, 3600)
            if (r.ok && r.bytes.size > 12 && String(r.bytes, 0, 4) == "RIFF") {
                wavs.add(r.bytes)
                continue
            }
            val e = r.error()
            if (e.contains("upstream TTS model failed")) {
                throw MediaException("${short(model)} failed on the device. Some voice models and modes don't work through TiinyOS's gateway yet. Try another voice model.")
            }
            throw MediaException(e)
        }
        val id = store.newId()
        val file = store.saveFile(id, "speech.wav", Wav.join(wavs))
        return ResultEntry(id, "speech", "Voice: ${text.take(60)}", listOf(file), text, model, System.currentTimeMillis())
    }

    fun transcribe(
        c: DeviceClient, store: ResultStore, model: String, audio: ByteArray, mime: String,
        filename: String, language: String, p: Progress,
    ): ResultEntry {
        p.update("Transcribing", null)
        val fields = mutableListOf("model" to model, "response_format" to "json")
        if (language.isNotBlank()) fields.add("language" to language)
        val r = c.multipart("/v1/audio/transcriptions", fields, listOf(DeviceClient.Part("file", filename, mime, audio)), 3600)
        val text = r.json().opt("text")
        if (!r.ok || text == null) throw MediaException(r.error())
        val id = store.newId()
        return ResultEntry(id, "transcript", "Transcript: ${filename}", emptyList(), text.toString().trim(), model, System.currentTimeMillis())
    }

    // ---------------- music ----------------

    fun loop(
        c: DeviceClient, store: ResultStore, model: String, prompt: String, seconds: Double,
        steps: Int, cfg: Double, seed: Long, p: Progress,
    ): ResultEntry {
        p.update("Generating the loop", null)
        val config = JSONObject().put("seconds_total", seconds).put("steps", steps).put("cfg_scale", cfg).put("seed", seed)
        val r = c.request("POST", "/v1/music/generate/wav", JSONObject().put("model", model).put("prompt", prompt).put("config", config), 3600)
        if (!r.ok || r.bytes.size < 12 || String(r.bytes, 0, 4) != "RIFF") throw MediaException(r.error())
        val id = store.newId()
        val file = store.saveFile(id, "loop.wav", r.bytes)
        return ResultEntry(id, "loop", "Loop: ${prompt.take(60)}", listOf(file), prompt, model, System.currentTimeMillis())
    }

    /** What the song model is, from /v1/music/info: SongGeneration (preview, resume) or a plain one (AceStep, YuE2). */
    data class SongInfo(
        val known: Boolean,
        val plain: Boolean,
        val ace: Boolean,
        val yue: Boolean,
        val steps: Double?,
        val temperature: Double?,
        val topK: Double?,
        val topP: Double?,
        val maxSteps: Int?,
        val maxSeconds: Int,
        val secsPerSec: Double?,
    )

    fun songInfo(c: DeviceClient, model: String): SongInfo {
        val r = try {
            c.request("GET", "/v1/music/info?model=${c.quote(model)}", null, 15)
        } catch (e: Exception) {
            null
        }
        val o = if (r?.ok == true) r.json() else JSONObject()
        val svc = o.optString("service_name")
        val d = o.optJSONObject("default_config") ?: JSONObject()
        val mi = o.optJSONObject("model_info")?.optJSONObject("model") ?: JSONObject()
        fun num(k: String) = if (d.has(k) && !d.isNull(k)) d.optDouble(k).takeIf { !it.isNaN() } else null
        val plain = if (svc.isNotEmpty()) !svc.startsWith("SongGeneration") else model.startsWith("ACE-Step/")
        val dur = mi.optInt("codec_llm_duration", 0)
        return SongInfo(
            known = svc.isNotEmpty(),
            plain = plain,
            ace = svc.startsWith("AceStep") || (svc.isEmpty() && plain),
            yue = if (svc.isNotEmpty()) svc.startsWith("YuE") else model.contains("YuE", true),
            steps = num("steps"), temperature = num("temperature"), topK = num("top_k"), topP = num("top_p"),
            maxSteps = mi.optInt("max_decoder_steps", 0).takeIf { it > 0 },
            maxSeconds = if (dur > 0) dur else 270,
            secsPerSec = when {
                svc.startsWith("YuE") -> 10.5
                svc.startsWith("AceStep") -> 1.5
                else -> null
            },
        )
    }

    /** A made song, and the device's session id when it was a preview that can be continued. */
    class SongOut(val entry: ResultEntry, val session: String?)

    /**
     * A song, as Tiiny Studio composes it over the gateway: plain models (AceStep, YuE2) through /v1/music/generate;
     * SongGeneration as an 8-second preview, a preview of the asked length, preview + resume for full length (4 min
     * and more), or the resume of an earlier preview. `config` holds caption, generation_type, lyrics, seed and any
     * of steps, temperature, top_k, top_p and ace_step.
     */
    fun song(
        c: DeviceClient, store: ResultStore, model: String, config: JSONObject, seconds: Int,
        preview: Boolean, resume: String?, p: Progress,
    ): SongOut {
        val q = c.quote(model)
        val plain = songInfo(c, model).plain
        val caption = config.optString("caption")
        val lyrics = config.optString("lyrics")

        // Show the device's own progress while the long request runs.
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val watcher = Thread {
            while (!done.get()) {
                try {
                    Thread.sleep(3_000)
                    if (done.get()) break
                    val r = c.request("GET", "/v1/music/sessions?model=$q", null, 5, log = false)
                    val list = r.json().optJSONArray("sessions") ?: continue
                    var best = 0.0
                    for (i in 0 until list.length()) {
                        val s = list.optJSONObject(i) ?: continue
                        if (s.optString("status") in setOf("completed", "failed", "cancelled")) continue
                        best = maxOf(best, s.optDouble("progress", 0.0))
                    }
                    if (best > 0) p.update("Composing", minOf(99, (best * 100).toInt()))
                } catch (e: Exception) {
                }
            }
        }
        watcher.isDaemon = true
        watcher.start()
        fun post(path: String, cfg: JSONObject, session: String? = null): Reply {
            val body = JSONObject().put("model", model).put("config", cfg)
            if (session != null) body.put("session_id", session)
            return c.request("POST", "/v1/music/generate$path", body, 3600)
        }
        var previewSession: String? = null
        val r: Reply = try {
            when {
                plain -> {
                    p.update("Composing", null)
                    post("", JSONObject(config.toString()).put("seconds_total", if (preview) 8 else seconds))
                }
                resume != null -> {
                    p.update("Composing the full song (this takes a while)", null)
                    post("/resume", config, resume)
                }
                preview -> {
                    p.update("Composing an 8-second preview", null)
                    post("/preview", JSONObject(config.toString()).put("preview_seconds", 8)).also { r1 ->
                        previewSession = r1.json().optString("session_id").takeIf { it.isNotBlank() && it != "null" }
                    }
                }
                seconds >= 240 -> {
                    p.update("Composing the opening", null)
                    val r1 = post("/preview", JSONObject(config.toString()).put("preview_seconds", 8))
                    val sid = r1.json().optString("session_id")
                    if (!r1.ok || sid.isBlank() || sid == "null") r1
                    else {
                        check(p)
                        p.update("Composing the full song (this takes a while)", null)
                        post("/resume", config, sid)
                    }
                }
                else -> {
                    p.update("Composing", null)
                    post("/preview", JSONObject(config.toString()).put("preview_seconds", seconds))
                }
            }
        } finally {
            done.set(true)
        }
        val res = r.json()
        val audio = res.optString("audio_data")
        val bytes: ByteArray = when {
            r.ok && audio.isNotEmpty() && audio != "null" -> unb64(audio)
            r.ok && res.optString("session_id").isNotBlank() -> {
                val dl = c.request("GET", "/v1/music/sessions/${c.quote(res.optString("session_id"))}/download?format=mp3", null, 120)
                if (!dl.ok || dl.bytes.size < 1000) throw MediaException("The song was made but couldn't be downloaded.")
                dl.bytes
            }
            else -> throw MediaException(r.error())
        }
        val ext = if (bytes.size > 4 && String(bytes, 0, 4) == "RIFF") "wav" else "mp3"
        val id = store.newId()
        val file = store.saveFile(id, "song.$ext", bytes)
        val kindLabel = when {
            preview && !plain -> "Song preview"
            resume != null -> "Song (full)"
            else -> "Song"
        }
        val text = buildString {
            append(caption)
            append(" · ")
            append(mapOf("bgm" to "instrumental", "vocal" to "vocals only").getOrDefault(config.optString("generation_type"), "with vocals"))
            if (lyrics.isNotBlank()) append("\n\n").append(lyrics)
        }
        val entry = ResultEntry(id, "song", "$kindLabel: ${caption.take(60)}", listOf(file), text, model, System.currentTimeMillis())
        return SongOut(entry, if (preview && !plain) previewSession else null)
    }

    // ---------------- helpers: a chat model writes tags or lyrics ----------------

    /** One short answer from a chat model, with thinking off where it can be switched off. */
    fun chatOnce(c: DeviceClient, model: String, system: String, user: String, maxTokens: Int): String {
        val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("stream", false)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        when (thinkingMode(c, model)) {
            "switch" -> body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            "always" -> body.put("reasoning_effort", "low")
        }
        val r = c.request("POST", "/v1/chat/completions", body, 600)
        if (!r.ok) throw MediaException(r.error())
        val text = r.json().optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
        val out = text.replace(Regex("(?s)<think>.*?</think>"), "").trim()
            .replace(Regex("^```\\w*\\n?|\\n?```$"), "").trim()
            .trim('"', '“', '”').trim()
        if (out.isEmpty()) throw MediaException("The chat model sent an empty answer. Try again.")
        return out
    }

    const val PROMPT_SYSTEM = "You improve prompts for a text-to-image model. Rewrite the user's idea as one vivid, concrete prompt: " +
        "subject, setting, composition, lighting, style, mood. Keep everything the user asked for and invent nothing " +
        "that contradicts it. Under 80 words. Reply with the prompt only: no preamble, no quotes, no lists."

    const val EDIT_SYSTEM = "You improve instructions for an image-editing model that changes an existing picture. Rewrite the user's " +
        "request as one clear, specific edit instruction: say exactly what to change and what must stay the same " +
        "(subject, pose, framing, style). Under 60 words. Reply with the instruction only: no preamble, no quotes."

    const val LYRICS_SYSTEM = "You write song lyrics for a singing model. Use only these section tags, each alone on its own line: " +
        "[verse], [chorus], [bridge]. Under each tag write 2-4 short singable lines, one per line, then a blank line " +
        "before the next tag. Repeat the chorus word for word. No title, no notes, no other markup, no blank lines " +
        "inside a section. Reply with the lyrics only."

    const val TAGS_SYSTEM = "You write prompts for Foundation-1, a model that makes instrument loops from comma-separated tags. Turn the " +
        "user's description into tags: first exactly one instrument, then 1-3 character tags, 1-2 phrase tags and 0-2 " +
        "effect tags. Prefer tags from the vocabulary given; never mention drums, tempo, bars or key. Reply with one " +
        "line of comma-separated tags only."

    /** Tidies a model's lyrics to [verse]/[chorus]/[bridge] sections (Tiiny Studio's clean_lyrics). */
    fun cleanLyrics(text: String): String {
        val tag = Regex("(?i)^\\[?\\s*(verse|chorus|bridge|intro|outro|pre-chorus|hook)(?:\\s*\\d+)?\\s*\\]?\\s*:?$")
        val out = ArrayList<String>()
        for (raw in text.lines()) {
            val line = raw.trim().trim('*', '_', '#').trim()
            val m = tag.find(line)
            if (m != null) {
                val t = when (val k = m.groupValues[1].lowercase()) {
                    "pre-chorus" -> "verse"
                    "hook" -> "chorus"
                    else -> k
                }
                if (out.isNotEmpty()) out.add("")
                out.add("[$t]")
            } else if (line.isNotEmpty() && out.isNotEmpty()) {
                out.add(line)
            }
        }
        val joined = out.joinToString("\n")
        if (!Regex("\\[(verse|chorus)\\]").containsMatchIn(joined)) {
            throw MediaException("The model didn't return lyrics with [verse]/[chorus] sections. Try again.")
        }
        return joined.replace(Regex("\\[(intro|outro)\\]\\n(?=\\n|$)"), "").trim()
    }

    // ---------------- text models ----------------

    /** Embeds several texts and shows how similar they are to each other (cosine similarity, 1 = same meaning). */
    fun embeddings(c: DeviceClient, store: ResultStore, model: String, texts: List<String>, p: Progress): ResultEntry {
        p.update("Embedding ${texts.size} texts", null)
        val r = c.request("POST", "/v1/embeddings", JSONObject().put("model", model).put("input", JSONArray(texts)), 600)
        val data = r.json().optJSONArray("data")
        if (!r.ok || data == null || data.length() != texts.size) throw MediaException(r.error())
        val vecs = (0 until data.length()).map { i ->
            val o = data.getJSONObject(i)
            val e = o.getJSONArray("embedding")
            o.optInt("index", i) to DoubleArray(e.length()) { e.getDouble(it) }
        }.sortedBy { it.first }.map { it.second }
        fun cos(a: DoubleArray, b: DoubleArray): Double {
            var dot = 0.0
            var na = 0.0
            var nb = 0.0
            for (k in a.indices) {
                dot += a[k] * b[k]
                na += a[k] * a[k]
                nb += b[k] * b[k]
            }
            return if (na == 0.0 || nb == 0.0) 0.0 else dot / Math.sqrt(na * nb)
        }
        val pairs = ArrayList<Triple<Int, Int, Double>>()
        for (i in vecs.indices) for (j in i + 1 until vecs.size) pairs.add(Triple(i, j, cos(vecs[i], vecs[j])))
        pairs.sortByDescending { it.third }
        val text = buildString {
            append("Vectors with ${vecs.firstOrNull()?.size ?: 0} dimensions.\n\n")
            texts.forEachIndexed { i, t -> append("${i + 1}. ${t.take(120)}\n") }
            append("\nSimilarity, most alike first (1.00 = same meaning):\n")
            for ((i, j, v) in pairs) append("${i + 1} ↔ ${j + 1}: ${"%.3f".format(v)}\n")
        }
        val id = store.newId()
        return ResultEntry(id, "embedding", "Compared ${texts.size} texts", emptyList(), text.trim(), model, System.currentTimeMillis())
    }

    /** Ranks documents by how well they answer a query. */
    fun rerank(c: DeviceClient, store: ResultStore, model: String, query: String, docs: List<String>, p: Progress): ResultEntry {
        p.update("Ranking ${docs.size} documents", null)
        val r = c.request(
            "POST", "/v1/rerank",
            JSONObject().put("model", model).put("query", query).put("documents", JSONArray(docs)), 600,
        )
        val o = r.json()
        val list = o.optJSONArray("results") ?: o.optJSONArray("data")
        if (!r.ok || list == null) throw MediaException(r.error())
        val ranked = (0 until list.length()).mapNotNull { i ->
            val e = list.optJSONObject(i) ?: return@mapNotNull null
            val idx = e.optInt("index", i)
            val score = when {
                e.has("relevance_score") -> e.optDouble("relevance_score")
                else -> e.optDouble("score", Double.NaN)
            }
            idx to score
        }.sortedByDescending { if (it.second.isNaN()) Double.NEGATIVE_INFINITY else it.second }
        val text = buildString {
            append("Query: $query\n\nMost relevant first:\n")
            ranked.forEachIndexed { rank, (idx, score) ->
                val doc = docs.getOrNull(idx) ?: "?"
                val sc = if (score.isNaN()) "" else " (score ${"%.3f".format(score)})"
                append("${rank + 1}. ${doc.take(160)}$sc\n")
            }
        }
        val id = store.newId()
        return ResultEntry(id, "rerank", "Ranked: ${query.take(50)}", emptyList(), text.trim(), model, System.currentTimeMillis())
    }

    /** One typed question for a Text Classification (Jev / System One) model. */
    data class Question(val type: String, val instructions: String, val options: List<String>)

    /**
     * Asks a classification model typed questions about a text (POST /v1/systemone): yes/no ("noul"),
     * pick one ("choice") or a score on a scale ("score"). The answers are probabilities, not generated text.
     */
    fun classify(c: DeviceClient, store: ResultStore, model: String, state: String, questions: List<Question>, p: Progress): ResultEntry {
        p.update("Classifying", null)
        val qs = JSONObject()
        questions.forEachIndexed { i, q ->
            val o = JSONObject().put("type", q.type).put("instructions", q.instructions)
            when (q.type) {
                "choice" -> {
                    val crit = JSONObject()
                    for (opt in q.options) {
                        val name = opt.substringBefore(':').trim()
                        if (name.isNotEmpty()) crit.put(name, opt.substringAfter(':', name).trim())
                    }
                    o.put("criteria", crit)
                }
                "score" -> o.put("criteria", JSONArray(q.options))
            }
            qs.put("q${i + 1}", o)
        }
        val r = c.request("POST", "/v1/systemone", JSONObject().put("model", model).put("state", state).put("questions", qs), 600)
        val answers = r.json().optJSONObject("answers")
        if (!r.ok || answers == null) throw MediaException(r.error())
        fun pct(v: Double) = "${Math.round(v * 100)}%"
        val text = buildString {
            questions.forEachIndexed { i, q ->
                val a = answers.optJSONObject("q${i + 1}") ?: JSONObject()
                append("${i + 1}. ${q.instructions}\n")
                when (q.type) {
                    "noul" -> {
                        val yes = a.optDouble("noul", Double.NaN)
                        append(if (yes.isNaN()) "   (no answer)\n" else "   ${if (yes >= 0.5) "Yes" else "No"} (yes: ${pct(yes)})\n")
                    }
                    "choice" -> {
                        append("   ${a.optString("choice")}")
                        if (a.has("confidence")) append(" (${pct(a.optDouble("confidence"))})")
                        append("\n")
                        a.optJSONObject("probabilities")?.let { pr ->
                            pr.keys().asSequence().map { it to pr.optDouble(it) }.sortedByDescending { it.second }
                                .forEach { (k, v) -> append("   · $k: ${pct(v)}\n") }
                        }
                    }
                    else -> {
                        val sc = a.optDouble("score", Double.NaN)
                        val levels = q.options
                        val nearest = if (sc.isNaN()) null else levels.getOrNull(Math.round(sc).toInt())
                        append("   ${if (sc.isNaN()) "(no answer)" else "%.2f".format(sc)}${nearest?.let { " ≈ $it" } ?: ""}\n")
                    }
                }
            }
        }
        val id = store.newId()
        return ResultEntry(id, "classification", "Classified: ${state.take(50)}", emptyList(), text.trim(), model, System.currentTimeMillis())
    }

    // ---------------- video ----------------

    data class VideoInfo(val canvases: List<String>, val steps: List<Int>, val defaultSteps: Int, val fps: Int, val audio: Boolean)

    fun videoInfo(c: DeviceClient, model: String): VideoInfo? {
        val r = c.request("GET", "/v1/video/info?model=${c.quote(model)}", null, 15)
        if (!r.ok) return null
        val o = r.json().let { it.optJSONObject("data") ?: it }
        val canv = o.optJSONArray("canvases")?.let { a ->
            (0 until a.length()).mapNotNull {
                when (val v = a.opt(it)) {
                    is String -> v
                    is JSONObject -> v.optString("size").takeIf { s -> s.isNotBlank() }
                        ?: "${v.optInt("width")}x${v.optInt("height")}"
                    else -> null
                }
            }
        } ?: emptyList()
        val steps = o.optJSONArray("step_options")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
        return VideoInfo(canv, steps, o.optInt("default_steps", 7), o.optInt("fps", 24), o.has("audio") || !o.has("fps"))
    }

    private fun explainVideo(e: String) = when {
        e.contains("Weight installation failed") ->
            "The Tiiny couldn't fit the video model next to the other loaded models. Unload the others, then try again. (Tiiny: $e)"
        e.contains("exceeded the gateway time limit") || e.contains("GENERATION_TIMEOUT") ->
            "The Tiiny stopped this video at its 1-hour limit. Try 480p, fewer steps or fewer seconds. (Tiiny: $e)"
        else -> e
    }

    /** Starts a clip and returns the device's id for it. */
    fun videoStart(
        c: DeviceClient, model: String, prompt: String, size: String, seconds: Int,
        steps: Int?, seed: Long?, image: String?,
    ): String {
        val body = JSONObject().put("model", model)
            .put("prompt", prompt.ifBlank { "Bring this picture to life" })
            .put("size", size).put("seconds", seconds.toString())
        steps?.let { body.put("steps", it) } // none: the model's own default
        seed?.let { body.put("seed", it) }
        if (image != null) body.put("image", image)
        val r = c.request("POST", "/v1/videos", body, 120)
        if (r.code >= 300) throw MediaException(explainVideo(r.error()))
        return r.json().optString("id").takeIf { it.isNotBlank() } ?: throw MediaException("The Tiiny didn't start the clip.")
    }

    /** Waits for a clip, downloads it and deletes it from the device. Also used to pick up a clip after a restart. */
    fun videoFinish(
        c: DeviceClient, store: ResultStore, model: String, vid: String, title: String, job: RemoteJob, p: Progress,
        details: String = "", startedAt: Long = 0,
    ): ResultEntry {
        val path = "/v1/videos/${c.quote(vid)}"
        job.cancelPath = "DELETE" to path
        var lost = 0
        var last = JSONObject()
        while (true) {
            Thread.sleep(3_000)
            check(p)
            val r = try {
                c.request("GET", path, null, 30, log = false) // every 3 s for up to an hour: only failures are logged
            } catch (e: MediaException) {
                null
            }
            val st = if (r != null && r.code < 500) r.json().optString("status") else ""
            if (st in setOf("completed", "failed", "cancelled")) {
                last = r!!.json()
                break
            }
            if (r != null && r.code == 404) throw MediaException("The Tiiny no longer has this clip (it keeps clips for an hour).")
            lost = if (st.isEmpty()) lost + 1 else 0
            if (lost >= 100) throw MediaException("Lost the Tiiny while it rendered this video.")
            if (lost > 0) {
                p.update("No answer from the Tiiny yet (it's usually just busy). Still waiting…", null)
                continue
            }
            val pct = r!!.json().optDouble("progress", 0.0).toInt()
            p.update(if (st == "in_progress") "Rendering" else "Waiting in the device queue", pct)
        }
        job.cancelPath = null
        if (last.optString("status") != "completed") {
            val e = last.opt("error")
            val msg = when (e) {
                is JSONObject -> e.optString("message").ifBlank { e.toString() }
                is String -> e
                else -> last.optString("status")
            }
            throw MediaException(explainVideo(msg))
        }
        p.update("Downloading", 100)
        var got: Reply? = null
        for (i in 0 until 5) {
            got = try {
                c.request("GET", "$path/content", null, 600)
            } catch (e: MediaException) {
                null
            }
            if (got != null && (got.ok || got.code in 1..499)) break
            Thread.sleep(3_000)
        }
        if (got == null || !got.ok || got.bytes.isEmpty()) throw MediaException("Couldn't fetch the video: ${got?.error() ?: "no answer"}")
        val id = store.newId()
        val file = store.saveFile(id, "video.mp4", got.bytes)
        try {
            c.request("DELETE", path, null, 30) // the device keeps it an hour otherwise
        } catch (e: Exception) {
        }
        val took = if (startedAt > 0) "Took ${fmtDuration((System.currentTimeMillis() - startedAt) / 1000)} to render" else "Saved from the Tiiny after it finished"
        return ResultEntry(id, "video", title, listOf(file), listOf(took, details).filter { it.isNotBlank() }.joinToString(" · "), model, System.currentTimeMillis())
    }

    // ---------------- 3D ----------------

    private fun stageInputs(stage: String) = when (stage) {
        "background", "shape" -> listOf("image")
        "simplify" -> listOf("mesh")
        "texture" -> listOf("image", "mesh")
        else -> listOf("material_artifact", "mesh_glb")
    }

    private fun stageField(input: String) = when (input) {
        "material_artifact" -> "material"
        "mesh_glb" -> "mesh"
        else -> input
    }

    private fun weight(stage: String) = when (stage) {
        "shape" -> 45.0
        "texture" -> 42.0
        "simplify" -> 3.0
        else -> 5.0
    }

    private class Artifact(val name: String, val data: ByteArray, val mime: String)

    /** What a loaded 3D model offers (its /v1/3d/capabilities); Hunyuan3D's options when it doesn't say. */
    data class MeshCaps(
        val family: String,
        val known: Boolean,
        val stages: List<String>,
        /** (value, label, hint) of the geometry details it accepts: draft / standard / high */
        val precisions: List<Triple<String, String, String>>,
        val qualities: List<String>,
        val assetQualities: List<String>,
    ) {
        val simplify get() = "simplify" in stages
        val texture get() = "texture" in stages && "export" in stages
    }

    fun meshCaps(c: DeviceClient, model: String): MeshCaps {
        val o = try {
            c.request("GET", "/v1/3d/capabilities?model=${c.quote(model)}", null, 30).json().optJSONObject("data")
        } catch (e: Exception) {
            null
        } ?: JSONObject()
        fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }
        val op = o.optJSONObject("output_precisions")
        val precisions = listOf("draft" to "Draft", "standard" to "Standard", "high" to "High").mapNotNull { (v, label) ->
            if (op == null) {
                Triple(v, label, mapOf("draft" to "192", "standard" to "288", "high" to "384").getValue(v))
            } else {
                val e = op.optJSONObject(v) ?: return@mapNotNull null
                val eff = e.optString("effective")
                if (eff.isNotBlank() && eff != "null" && eff != v) return@mapNotNull null // TRELLIS.2: its draft is standard
                val hint = e.opt("octree_resolution")?.toString() ?: e.opt("pipeline_type")?.toString() ?: ""
                Triple(v, label, hint)
            }
        }
        return MeshCaps(
            family = o.optString("model").takeIf { it.isNotBlank() && it != "null" } ?: "hunyuan3d",
            known = o.has("model"),
            stages = list("stages") ?: listOf("background", "shape", "simplify", "texture", "export"),
            precisions = precisions,
            qualities = list("inference_qualities")?.filter { it in setOf("fast", "balance", "quality") } ?: listOf("fast", "balance", "quality"),
            assetQualities = list("asset_qualities") ?: emptyList(),
        )
    }

    /** Tiiny Studio's time estimate for a 3D model, in seconds (measured on TiinyOS 0.1.38). */
    fun meshEstimate(caps: MeshCaps, precision: String, quality: String, faces: Int, textured: Boolean, cutoutSecs: Int): Double {
        val qm = mapOf("fast" to 0.75, "balance" to 1.0, "quality" to 1.5)[quality] ?: 1.0
        val pm = mapOf("draft" to 1.0, "standard" to 1.75, "high" to 3.35)[precision] ?: 1.0
        val simp = faces > 0 && caps.simplify
        val tex = textured && caps.texture
        return if (caps.family == "trellis2") {
            cutoutSecs + 200 * (if (precision == "high") 2 else 1) * qm + (if (simp) 20 else 0) + (if (tex) 100 * qm + 45 else 0.0)
        } else {
            cutoutSecs + 205 * pm * qm + (if (simp) 6 else 0) + (if (tex) 230 * qm + 60 else 0.0)
        }
    }

    data class MeshOptions(
        val precision: String,
        val quality: String,
        val assetQuality: String?,
        /** 0 = keep every polygon (no simplify stage) */
        val faces: Int,
        val textured: Boolean,
        val seed: Long?,
        /** An image model that puts the subject on a transparent background first, or null. */
        val cutoutModel: String?,
        val cutoutSize: String,
    )

    private const val CUTOUT_PROMPT = "Extract the main subject from this photo onto a transparent background. Keep the subject exactly the same: " +
        "shape, colours, details. Remove everything else. The image has an alpha channel and a transparent background."

    /** Makes nearly see-through pixels fully transparent white, as Tiiny Studio does before building from a cut-out. */
    private fun cleanAlpha(png: ByteArray): ByteArray {
        val bmp = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size) ?: return png
        if (!bmp.hasAlpha()) return png
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) if ((px[i] ushr 24) < 32) px[i] = 0x00FFFFFF
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        val bos = java.io.ByteArrayOutputStream()
        out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    /**
     * A 3D model from one picture, like Tiiny Studio's mesh tool: optional cut-out, then shape, optional simplify
     * (polygon budget), optional texture and export.
     */
    fun mesh(
        c: DeviceClient, store: ResultStore, model: String, image: ByteArray, imageMime: String,
        opts: MeshOptions, job: RemoteJob, p: Progress,
    ): ResultEntry {
        val started = System.currentTimeMillis()
        var source = image
        var sourceMime = imageMime
        if (opts.cutoutModel != null) {
            p.update("Cutting out the subject", null)
            val uri = "data:$imageMime;base64," + b64(image)
            val unloaded = makeRoom(c, opts.cutoutModel, p)
            val cut = try {
                this.image(c, store, opts.cutoutModel, CUTOUT_PROMPT, "", opts.cutoutSize, null, null, listOf(uri), p)
            } finally {
                putBack(c, opts.cutoutModel, unloaded, p)
            }
            val f = store.file(cut.mainFile!!)
            source = cleanAlpha(f.readBytes())
            sourceMime = "image/png"
            f.delete() // the cut-out is only a step on the way, not a result of its own
            makeRoom(c, model, p) // the 3D model may have made way for the image model
        }
        val q = "?model=${c.quote(model)}"
        val caps = c.request("GET", "/v1/3d/capabilities$q", null, 30).json().let { it.optJSONObject("data") ?: JSONObject() }
        val plan = mutableListOf("shape")
        if (opts.faces > 0) plan += "simplify"
        if (opts.textured) plan += listOf("texture", "export")
        caps.optJSONArray("stages")?.let { st ->
            val offered = (0 until st.length()).map { st.optString(it) }.toSet()
            if (offered.isNotEmpty()) plan.retainAll { it in offered }
        }
        if (plan.isEmpty()) throw MediaException("This 3D model offers no shape stage.")
        val useRefs = caps.optBoolean("input_refs", false)
        val refs = JSONObject()
        val have = HashMap<String, Artifact>()
        have["image"] = Artifact(if (sourceMime == "image/png") "input.png" else "input.jpg", source, sourceMime)
        val total = plan.sumOf { weight(it) }
        var done = 0.0
        val last = plan.last()

        for (stage in plan) {
            check(p)
            val params = JSONObject()
            when (stage) {
                "simplify" -> params.put("target_faces", opts.faces)
                "background" -> {}
                else -> {
                    params.put("output_precision", opts.precision).put("inference_quality", opts.quality)
                    opts.assetQuality?.let { params.put("asset_quality", it) }
                    opts.seed?.let { params.put("seed", it) }
                }
            }
            caps.optJSONObject("stage_specs")?.optJSONObject(stage)?.let { spec ->
                val listed = HashSet<String>()
                spec.optJSONObject("params")?.keys()?.forEach { listed.add(it) }
                spec.optJSONArray("controls")?.let { a -> for (i in 0 until a.length()) listed.add(a.optString(i)) }
                params.keys().asSequence().toList().forEach { if (it !in listed) params.remove(it) }
            }
            val names = caps.optJSONObject("inputs")?.optJSONArray(stage)
                ?.let { a -> (0 until a.length()).map { a.optString(it) } }
                ?.takeIf { it.isNotEmpty() } ?: stageInputs(stage)
            val files = ArrayList<DeviceClient.Part>()
            val inrefs = JSONObject()
            for (name in names) {
                val field = stageField(name)
                val ref = refs.optJSONObject(name)
                if (useRefs && ref != null) inrefs.put(name, ref)
                else have[name]?.let { a -> if (files.none { it.field == field }) files.add(DeviceClient.Part(field, a.name, a.mime, a.data)) }
            }
            val fields = mutableListOf("stage" to stage, "params" to params.toString())
            if (inrefs.length() > 0) fields.add("input_refs" to inrefs.toString())
            val r = c.multipart("/v1/3d/tasks$q", fields, files, 3600)
            if (r.code >= 300) throw MediaException("$stage: ${r.error()}")
            val tid = r.json().optJSONObject("data")?.opt("task_id")?.toString()
                ?: throw MediaException("$stage: the Tiiny didn't start the task.")
            val tq = "/v1/3d/tasks/${c.quote(tid)}"
            job.cancelPath = "POST" to "$tq/cancel$q"
            var silent = 0
            var d = JSONObject()
            while (true) {
                Thread.sleep(2_000)
                check(p)
                val st = try {
                    c.request("GET", "$tq$q", null, 30, log = false) // every 2 s: only failures are logged
                } catch (e: MediaException) {
                    null
                }
                if (st == null || st.code >= 500) {
                    if (++silent >= 150) throw MediaException("$stage: lost the Tiiny while it worked on this task")
                    p.update("No answer from the Tiiny yet (it's usually just busy). Still waiting…", null)
                    continue
                }
                d = st.json().optJSONObject("data") ?: JSONObject()
                val pct = d.optDouble("progress", 0.0)
                val status = d.optString("status")
                val shown = d.optString("message").takeIf { it.isNotBlank() && it != "null" } ?: status
                p.update(
                    "${stage.replaceFirstChar { it.uppercase() }}: $shown",
                    ((done + weight(stage) * pct / 100.0) / total * 100).toInt(),
                )
                if (status in setOf("completed", "failed", "cancelled", "error")) break
                silent = if (status.isNotBlank()) 0 else silent + 1
                if (silent >= 150) throw MediaException("$stage: the device stopped reporting on this task")
            }
            if (d.optString("status") != "completed") {
                val why = listOf("error", "message", "status").map { d.opt(it) }.firstOrNull { it != null && it.toString().isNotBlank() && it.toString() != "null" }
                throw MediaException("$stage: ${why ?: "failed"}")
            }
            val out = d.optJSONObject("result")?.optJSONObject("output") ?: JSONObject()
            val payloadKey = listOf("glb", "artifact", "image", "mesh").firstOrNull { out.opt(it) is String } ?: ""
            val arts = out.optJSONArray("artifacts")?.takeIf { it.length() > 0 }?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
                ?: listOf(
                    JSONObject().put("artifact_type", out.optString("artifact_type").ifBlank { "mesh_glb" })
                        .put("filename", out.opt("filename")).put("mime_type", out.opt("mime_type")).put("payload_key", payloadKey)
                )
            for (art in arts) {
                val kind = art.optString("artifact_type").takeIf { it != "null" } ?: ""
                if (kind.endsWith("_preview") && stage != last) continue
                val aid = art.optString("artifact_id").takeIf { it.isNotBlank() && it != "null" }
                if (aid != null && kind.isNotEmpty()) {
                    val ref = JSONObject().put("task_id", tid).put("artifact_id", aid)
                    refs.put(kind, ref)
                    refs.put("${kind}_artifact", ref)
                }
                val local = !useRefs || kind == "mesh_glb" || kind.endsWith("_preview")
                val inline = out.optString(art.optString("payload_key")).takeIf { it.isNotBlank() && it != "null" && art.optString("payload_key").isNotBlank() }
                val data: ByteArray = when {
                    inline != null -> unb64(inline)
                    aid != null && !local -> continue
                    aid != null -> {
                        p.update("${stage.replaceFirstChar { it.uppercase() }}: downloading the result", null)
                        var got: Reply? = null
                        for (i in 0 until 5) {
                            got = try {
                                c.request("GET", "$tq/artifacts/${c.quote(aid)}$q", null, 600)
                            } catch (e: MediaException) {
                                null
                            }
                            if (got != null && got.code in 1..499) break
                            Thread.sleep(3_000)
                        }
                        if (got == null || !got.ok) throw MediaException("$stage: couldn't fetch its $kind: ${got?.error() ?: "no answer"}")
                        got.bytes
                    }
                    else -> continue
                }
                val fname = art.optString("filename").takeIf { it.isNotBlank() && it != "null" } ?: "$kind.bin"
                val mime = art.optString("mime_type").takeIf { it.isNotBlank() && it != "null" } ?: "application/octet-stream"
                val item = Artifact(fname, data, mime)
                when {
                    kind == "mesh_glb" -> {
                        have["mesh"] = item
                        have["mesh_glb"] = item
                    }
                    kind.startsWith("material") -> {
                        have["material"] = item
                        have["material_artifact"] = item
                    }
                    kind.startsWith("image") || mime.startsWith("image/") -> have["image"] = item
                    kind.endsWith("_preview") -> have["preview"] = item
                    kind.isNotEmpty() -> {
                        have[kind] = item
                        have["${kind}_artifact"] = item
                    }
                }
            }
            done += weight(stage)
            job.cancelPath = null
        }
        val mesh = have["mesh"] ?: have["preview"] ?: throw MediaException("The Tiiny returned no 3D model.")
        val id = store.newId()
        val file = store.saveFile(id, "model.glb", mesh.data)
        val details = listOfNotNull(
            "Took ${fmtDuration((System.currentTimeMillis() - started) / 1000)}",
            if (opts.cutoutModel != null) "cut out by ${short(opts.cutoutModel)}" else null,
            "${opts.precision} detail",
            if (opts.faces > 0) "${opts.faces / 1000}k faces" else "full mesh",
            if (opts.textured) "PBR texture" else "no texture",
            opts.seed?.let { "seed $it" },
        ).joinToString(" · ")
        return ResultEntry(id, "mesh", if (opts.textured) "Textured 3D model" else "3D model", listOf(file), details, model, System.currentTimeMillis())
    }
}

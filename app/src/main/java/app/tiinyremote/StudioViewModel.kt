package app.tiinyremote

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** The one job that can run at a time (the Tiiny handles one request at a time anyway). */
data class JobUi(
    val tool: String,
    val running: Boolean,
    val message: String = "",
    val percent: Int? = null,
    val error: String? = null,
    val result: ResultEntry? = null,
)

data class SavedVoice(val id: String, val name: String, val text: String)

data class Source(val title: String, val url: String)

data class ChatMsg(
    val role: String,
    val text: String,
    val reasoning: String = "",
    /** A picture sent with the message, as a data URI. */
    val image: String? = null,
    /** What the model made with its tools during this reply. */
    val results: List<ResultEntry> = emptyList(),
    /** What a tool is doing right now (loading a model, making an image, …). */
    val status: String = "",
    /** Web pages found or read while answering. */
    val sources: List<Source> = emptyList(),
)

private const val SYSTEM_PROMPT = "You are the assistant in Tiiny Remote, an Android app for the user's own Tiiny AI device. " +
    "Be direct and helpful. Use Markdown when it helps."

private const val TOOLS_PROMPT = " When the user wants something made (an image, an edit of an image, speech, a music " +
    "loop, a song), call the matching tool instead of describing it; write rich, specific prompts for it. Nothing is " +
    "made unless you call a tool: never claim you made or changed something without calling the tool in this turn. " +
    "The result appears in the chat and in the app's results, so after the tool returns just tell the user briefly " +
    "what you made. Making things can take a minute or more because models may be swapped on the device."

private fun webPrompt() = " You can search the web with web_search and read a page with open_page. Today is " +
    java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date()) + ". For anything " +
    "current, recent or that you're unsure of, search first instead of answering from memory; open the most promising " +
    "pages when the snippets aren't enough. Base your answer on what you found and cite the sources as Markdown links."

/** The web tools: search through the user's SearXNG, and read a page. */
private fun webSpecs(): JSONArray {
    fun fn(name: String, desc: String, props: JSONObject, req: String) = JSONObject().put("type", "function")
        .put("function", JSONObject().put("name", name).put("description", desc).put("parameters",
            JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(listOf(req)))))
    return JSONArray()
        .put(fn("web_search", "Search the web. Returns titles, links and snippets of the top results.",
            JSONObject().put("query", JSONObject().put("type", "string").put("description", "Search terms, as you'd type them into a search engine.")), "query"))
        .put(fn("open_page", "Read the text of a web page (e.g. one from the search results).",
            JSONObject().put("url", JSONObject().put("type", "string").put("description", "The page's full http(s) address.")), "url"))
}

/** The tools the chat model may call, as in Tiiny Studio's "Can make things". */
private fun toolSpecs(): JSONArray {
    fun fn(name: String, desc: String, params: JSONObject) = JSONObject().put("type", "function")
        .put("function", JSONObject().put("name", name).put("description", desc).put("parameters", params))
    fun obj(props: JSONObject, vararg required: String) = JSONObject().put("type", "object")
        .put("properties", props).put("required", JSONArray(required.toList()))
    fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
    return JSONArray()
        .put(fn("create_image", "Create a new image from a detailed text-to-image prompt.", obj(
            JSONObject().put("prompt", str("Detailed visual description: subject, setting, style, lighting."))
                .put("shape", JSONObject().put("type", "string").put("enum", JSONArray(listOf("square", "portrait", "landscape", "wide"))).put("description", "Aspect ratio.")),
            "prompt")))
        .put(fn("edit_image", "Edit the most recent image in the conversation (attached by the user or made earlier) with a plain-language instruction.", obj(
            JSONObject().put("instruction", str("What to change, and what to keep the same.")), "instruction")))
        .put(fn("speak", "Read text aloud in the user's saved voice and return the audio.", obj(
            JSONObject().put("text", JSONObject().put("type", "string")), "text")))
        .put(fn("make_loop", "Make a short instrument loop for music production.", obj(
            JSONObject().put("tags", str("Comma-separated tags: one instrument first, then character, phrase and effect tags."))
                .put("bpm", JSONObject().put("type", "integer").put("minimum", 100).put("maximum", 150)),
            "tags")))
        .put(fn("make_song", "Make a short song (about 30 seconds).", obj(
            JSONObject().put("caption", str("Genre, mood, instruments, voice."))
                .put("lyrics", str("Lyrics with [verse] and [chorus] tags on their own lines. Empty for an instrumental.")),
            "caption")))
}

/** The media tools, the chat and the results library. */
class StudioViewModel(app: Application) : AndroidViewModel(app) {
    val store = ResultStore(app)
    private val settingsStore = Store(app)
    private val prefs = app.getSharedPreferences("tiiny_remote_studio", Context.MODE_PRIVATE)

    private val _job = MutableStateFlow<JobUi?>(null)
    val job: StateFlow<JobUi?> = _job.asStateFlow()

    private val _results = MutableStateFlow(store.all())
    val results: StateFlow<List<ResultEntry>> = _results.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMsg>>(emptyList())
    val chat: StateFlow<List<ChatMsg>> = _chat.asStateFlow()
    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()
    private val chatStop = AtomicBoolean(false)

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    private val cancelled = AtomicBoolean(false)
    @Volatile
    private var remote = RemoteJob()

    init {
        resumePendingVideo()
    }

    fun client(): DeviceClient = settingsStore.load().let { DeviceClient(it.tiinyUrl, it.apiKey) }

    fun noteShown() {
        _note.value = null
    }

    private fun note(msg: String) {
        _note.value = msg
    }

    /** Remembers the model picked per tool. */
    fun pick(tool: String): String? = prefs.getString("pick_$tool", null)
    fun setPick(tool: String, id: String) {
        prefs.edit().putString("pick_$tool", id).apply()
    }

    fun clearJob() {
        if (_job.value?.running != true) _job.value = null
    }

    /** Runs a tool in the background; its result lands in the library. */
    fun run(tool: String, block: (DeviceClient, ResultStore, Progress, RemoteJob) -> ResultEntry) {
        if (_job.value?.running == true) {
            note("Something is already running on the Tiiny. Wait for it, or cancel it.")
            return
        }
        cancelled.set(false)
        val rj = RemoteJob()
        remote = rj
        _job.value = JobUi(tool, true, "Starting…")
        viewModelScope.launch {
            val progress = Progress(
                update = { m, pct -> _job.update { it?.copy(message = m, percent = pct ?: it.percent) } },
                cancelled = { cancelled.get() },
            )
            try {
                // The result is stored right where it's made, so it's kept even if the screen goes away meanwhile.
                val entry = withContext(Dispatchers.IO) { block(client(), store, progress, rj).also { store.add(it) } }
                _results.value = store.all()
                _job.value = JobUi(tool, false, "Done", 100, null, entry)
            } catch (e: Exception) {
                ApiLog.add("$tool failed: ${e.message}")
                _job.value = JobUi(tool, false, error = e.message ?: e.toString())
            }
        }
    }

    fun cancel() {
        cancelled.set(true)
        _job.update { it?.copy(message = "Cancelling…") }
        val cp = remote.cancelPath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client().request(cp.first, cp.second, null, 30)
            } catch (e: Exception) {
            }
        }
        if (_job.value?.tool == "video") prefs.edit().remove("pending_video").apply()
    }

    // ---------- video: survives the app being closed ----------

    /** Small remembered settings (e.g. the video size), like Tiiny Studio keeps them. */
    fun setting(key: String, default: String): String = prefs.getString("set_$key", null) ?: default
    fun setSetting(key: String, value: String) {
        prefs.edit().putString("set_$key", value).apply()
    }

    fun video(model: String, prompt: String, size: String, seconds: Int, steps: Int?, seed: Long?, image: String?) {
        val title = "Video: " + prompt.ifBlank { "from a picture" }.take(60)
        val details = "$size · $seconds s · ${steps?.let { "$it steps" } ?: "default steps"}" + (seed?.let { " · seed $it" } ?: "")
        run("video") { c, store, p, rj ->
            p.update("Sending the clip to the Tiiny", null)
            val started = System.currentTimeMillis()
            val vid = Tools.videoStart(c, model, prompt, size, seconds, steps, seed, image)
            prefs.edit().putString(
                "pending_video",
                JSONObject().put("id", vid).put("model", model).put("title", title).put("details", details).put("started", started).toString(),
            ).apply()
            try {
                Tools.videoFinish(c, store, model, vid, title, rj, p, details, started)
            } finally {
                prefs.edit().remove("pending_video").apply()
            }
        }
    }

    /** A clip still rendering when the app was closed is picked up again (the Tiiny keeps it for an hour). */
    private fun resumePendingVideo() {
        val saved = prefs.getString("pending_video", null) ?: return
        val o = try {
            JSONObject(saved)
        } catch (e: Exception) {
            null
        } ?: return
        val vid = o.optString("id")
        if (vid.isBlank()) return
        run("video") { c, store, p, rj ->
            p.update("Picking up the video that was still rendering", null)
            try {
                Tools.videoFinish(c, store, o.optString("model"), vid, o.optString("title"), rj, p, o.optString("details"), o.optLong("started", 0))
            } finally {
                prefs.edit().remove("pending_video").apply()
            }
        }
    }

    // ---------- results ----------

    fun delete(e: ResultEntry) {
        store.delete(e)
        _results.value = store.all()
        if (_job.value?.result?.id == e.id) _job.value = null
    }

    fun saveToPhone(e: ResultEntry) {
        if (e.files.isEmpty()) {
            note("This result is text: use Share or Copy.")
            return
        }
        viewModelScope.launch {
            val where = withContext(Dispatchers.IO) { e.files.mapNotNull { store.saveToPhone(it) }.firstOrNull() }
            note(if (where != null) "Saved to $where" else "Saving needs Android 10 or newer: use Share instead.")
        }
    }

    fun share(e: ResultEntry) = store.share(e)

    fun open(name: String) {
        if (!store.open(name)) {
            note(if (name.endsWith(".glb")) "No app on this phone opens 3D models (.glb). Install a 3D viewer, or Share the file." else "No app on this phone opens this file.")
        }
    }

    // ---------- chat ----------

    fun clearChat() {
        if (!_chatBusy.value) {
            _chat.value = emptyList()
            lastChatImage = null
        }
    }

    fun stopChat() {
        chatStop.set(true)
    }

    /** The newest picture in the chat (attached or made), for edit_image. */
    @Volatile
    private var lastChatImage: String? = null

    // ---------- saved voices (cloned from a clip, kept in the app) ----------

    private val voicesDir = java.io.File(app.filesDir, "voices").apply { mkdirs() }

    private fun loadVoices(): List<SavedVoice> {
        val a = try {
            JSONArray(prefs.getString("voices", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            SavedVoice(o.optString("id"), o.optString("name"), o.optString("text"))
                .takeIf { voiceFile(it.id).exists() }
        }
    }

    private val _voices = MutableStateFlow(loadVoices())
    val voices: StateFlow<List<SavedVoice>> = _voices.asStateFlow()

    fun voiceFile(id: String) = java.io.File(voicesDir, "voice_$id.wav")

    private fun storeVoices(list: List<SavedVoice>) {
        val a = JSONArray()
        for (v in list) a.put(JSONObject().put("id", v.id).put("name", v.name).put("text", v.text))
        prefs.edit().putString("voices", a.toString()).apply()
        _voices.value = list
    }

    fun addVoice(name: String, clip: ByteArray, text: String): SavedVoice {
        val v = SavedVoice(System.currentTimeMillis().toString(36), name.trim().ifBlank { "Voice ${_voices.value.size + 1}" }, text.trim())
        voiceFile(v.id).writeBytes(clip)
        storeVoices(_voices.value + v)
        return v
    }

    fun deleteVoice(id: String) {
        voiceFile(id).delete()
        storeVoices(_voices.value.filter { it.id != id })
    }

    // ---------- helpers: a chat model suggests loop tags or writes lyrics, the ASR model transcribes a clip ----------

    private val _helper = MutableStateFlow<String?>(null) // what a helper is doing, while it runs
    val helper: StateFlow<String?> = _helper.asStateFlow()

    /**
     * Runs a helper with a model of the given kind: a loaded one if there is one, else the one picked for that tool,
     * loaded for the job (making room) with your models put back afterwards, like Tiiny Studio does.
     */
    private fun helperRun(label: String, tool: String, models: List<ModelInfo>, fits: (ModelInfo) -> Boolean, block: (DeviceClient, String) -> String, onDone: (String) -> Unit) {
        if (_helper.value != null) return
        val ok = models.filter(fits)
        val model = ok.firstOrNull { it.running && it.id == pick(tool) } ?: ok.firstOrNull { it.running }
            ?: ok.firstOrNull { it.id == pick(tool) } ?: ok.firstOrNull()
        if (model == null) {
            note(if (tool == "chat") "This helper needs a chat model on the Tiiny." else "Auto-transcribe needs a speech-to-text (ASR) model on the Tiiny.")
            return
        }
        if (_job.value?.running == true && !model.running) {
            note("Wait for the running job: the helper would have to swap models.")
            return
        }
        _helper.value = label
        if (!model.running) note("Loading ${model.name} for this, then your models go back.")
        viewModelScope.launch {
            try {
                val out = withContext(Dispatchers.IO) {
                    val c = client()
                    val p = Progress(update = { m, _ -> _helper.value = m }, cancelled = { false })
                    val unloaded = Tools.makeRoom(c, model.id, p)
                    try {
                        _helper.value = label
                        block(c, model.id)
                    } finally {
                        Tools.putBack(c, model.id, unloaded, p)
                    }
                }
                onDone(out)
            } catch (e: Exception) {
                ApiLog.add("helper failed: ${e.message}")
                note(e.message ?: e.toString())
            } finally {
                _helper.value = null
            }
        }
    }

    fun suggestTags(description: String, vocabulary: String, models: List<ModelInfo>, onDone: (String) -> Unit) =
        helperRun("Suggesting tags…", "chat", models, { it.isChat }, { c, m ->
            Tools.chatOnce(c, m, Tools.TAGS_SYSTEM, "$vocabulary\n\nDescription: ${description.trim()}", 120)
        }, onDone)

    fun writeLyrics(request: String, models: List<ModelInfo>, onDone: (String) -> Unit) =
        helperRun("Writing lyrics…", "chat", models, { it.isChat }, { c, m ->
            Tools.cleanLyrics(Tools.chatOnce(c, m, Tools.LYRICS_SYSTEM, request, 900))
        }, onDone)

    fun autoTranscribe(clip: PickedAudio, models: List<ModelInfo>, onDone: (String) -> Unit) =
        helperRun("Transcribing the clip…", "transcribe", models, { it.isAsr }, { c, m ->
            Tools.transcribe(c, store, m, clip.bytes, clip.mime, clip.name, "", Progress({ _, _ -> }, { false })).text
        }, onDone)

    fun improvePrompt(text: String, edit: Boolean, models: List<ModelInfo>, onDone: (String) -> Unit) =
        helperRun(if (edit) "Improving the instruction…" else "Improving the prompt…", "chat", models, { it.isChat }, { c, m ->
            Tools.chatOnce(c, m, if (edit) Tools.EDIT_SYSTEM else Tools.PROMPT_SYSTEM, text.trim(), if (edit) 160 else 200)
        }, onDone)

    // ---------- "Edit this": a made picture goes to the Edit tool ----------

    private val _openTool = MutableStateFlow<String?>(null)
    val openTool: StateFlow<String?> = _openTool.asStateFlow()
    private val _editSource = MutableStateFlow<String?>(null) // a file in the results store
    val editSource: StateFlow<String?> = _editSource.asStateFlow()

    fun editThis(file: String) {
        _editSource.value = file
        _openTool.value = "edit"
    }

    fun toolOpened() {
        _openTool.value = null
    }

    fun editSourceTaken() {
        _editSource.value = null
    }

    // ---------- songs: a preview can be continued to the full song ----------

    fun song(model: String, config: JSONObject, seconds: Int, preview: Boolean) {
        run("song") { c, store, p, _ ->
            val out = Tools.song(c, store, model, config, seconds, preview, null, p)
            out.session?.let { sid ->
                prefs.edit().putString("resume_${out.entry.id}", JSONObject().put("session", sid).put("model", model).put("config", config).toString()).apply()
            }
            out.entry
        }
    }

    fun canContinue(e: ResultEntry): Boolean = prefs.contains("resume_${e.id}")

    fun continueSong(e: ResultEntry) {
        val o = try {
            JSONObject(prefs.getString("resume_${e.id}", null) ?: return)
        } catch (x: Exception) {
            return
        }
        if (_job.value?.running == true) {
            note("Something is already running on the Tiiny. Wait for it, or cancel it.")
            return
        }
        prefs.edit().remove("resume_${e.id}").apply()
        note("Continuing to the full song: this takes about 15 minutes.")
        run("song") { c, store, p, _ ->
            val model = o.getString("model")
            Tools.makeRoom(c, model, p)
            Tools.song(c, store, model, o.getJSONObject("config"), 0, false, o.getString("session"), p).entry
        }
    }

    // ---------- the saved voice for "speak" (set by the Voice tool) ----------

    fun saveVoice(clip: ByteArray?, refText: String?, speaker: String?) {
        val e = prefs.edit()
        if (speaker != null) e.putString("voice_speaker", speaker)
        if (speaker == null && clip != null) e.remove("voice_speaker")
        if (clip != null && !refText.isNullOrBlank()) {
            java.io.File(getApplication<Application>().filesDir, "voice.wav").writeBytes(clip)
            e.putString("voice_text", refText.trim())
        }
        e.apply()
    }

    private fun savedClip(): Pair<ByteArray, String>? {
        val f = java.io.File(getApplication<Application>().filesDir, "voice.wav")
        val t = prefs.getString("voice_text", null)
        return if (f.exists() && !t.isNullOrBlank()) f.readBytes() to t else null
    }

    /** Which installed model a tool uses: the one picked in that tool, else a loaded one, else the first. */
    private fun modelFor(tool: String, models: List<ModelInfo>, fits: (ModelInfo) -> Boolean): String? {
        val ok = models.filter(fits)
        return ok.firstOrNull { it.id == pick(tool) }?.id ?: ok.firstOrNull { it.running }?.id ?: ok.firstOrNull()?.id
    }

    private fun setStatus(text: String) {
        _chat.update { list -> list.dropLast(1) + list.last().copy(status = text) }
    }

    /** Runs web_search or open_page and notes the sources on the reply. */
    private fun webTool(name: String, args: JSONObject, searx: String): String {
        if (name == "web_search") {
            val q = args.optString("query").trim().ifBlank { return "Give a query to search for." }
            setStatus("Searching the web: $q")
            val hits = WebSearch.search(searx, q)
            if (hits.isEmpty()) return "No results for \"$q\". Try other words."
            _chat.update { list ->
                list.dropLast(1) + list.last().let { m -> m.copy(sources = (m.sources + hits.map { Source(it.title, it.url) }).distinctBy { it.url }) }
            }
            return hits.mapIndexed { i, h -> "${i + 1}. ${h.title}\n${h.url}\n${h.snippet.take(400)}" }.joinToString("\n\n")
        }
        val url = args.optString("url").trim()
        setStatus("Reading ${url.substringAfter("://").substringBefore('/')}")
        val text = WebSearch.read(url)
        _chat.update { list ->
            list.dropLast(1) + list.last().let { m -> if (m.sources.any { it.url == url }) m else m.copy(sources = m.sources + Source(url.substringAfter("://").substringBefore('/'), url)) }
        }
        return "Page $url:\n\n$text"
    }

    /** Runs one tool call for the chat model; returns (result or null, text for the model). */
    private fun runTool(c: DeviceClient, name: String, args: JSONObject, models: List<ModelInfo>, p: Progress): Pair<ResultEntry?, String> {
        val (tool, fits) = when (name) {
            "create_image" -> "image" to { m: ModelInfo -> m.isImageGen }
            "edit_image" -> "edit" to { m: ModelInfo -> m.canEditImages }
            "speak" -> "speech" to { m: ModelInfo -> m.isSpeech }
            "make_loop" -> "loop" to { m: ModelInfo -> m.isLoopModel }
            "make_song" -> "song" to { m: ModelInfo -> m.isSongModel }
            else -> return null to "Unknown tool $name"
        }
        val model = modelFor(tool, models, fits) ?: return null to "No model for ${name.replace('_', ' ')} is installed on the device."
        if (name == "edit_image" && lastChatImage == null) {
            return null to "There's no image in the conversation to edit. Ask the user to attach one."
        }
        val unloaded = Tools.makeRoom(c, model, p)
        val result = try {
            when (name) {
                "create_image" -> {
                    val size = when (args.optString("shape")) {
                        "portrait" -> "768x1024"
                        "landscape" -> "1024x768"
                        "wide" -> "1024x576"
                        else -> "1024x1024"
                    }
                    Tools.image(c, store, model, args.optString("prompt"), "", size, null, null, emptyList(), p)
                }
                "edit_image" -> Tools.image(c, store, model, args.optString("instruction"), "", "", null, null, listOf(lastChatImage!!), p)
                "speak" -> {
                    val caps = Tools.voiceCaps(c, model)
                    val savedSpeaker = prefs.getString("voice_speaker", null)
                    val clip = savedClip()
                    val text = args.optString("text")
                    when {
                        caps != null && savedSpeaker != null && savedSpeaker in caps.speakers ->
                            Tools.speech(c, store, model, text, savedSpeaker, null, null, null, "", "", p)
                        clip != null && (caps == null || caps.clone) ->
                            Tools.speech(c, store, model, text, null, null, clip.first, clip.second, "", "", p)
                        caps != null && caps.speakers.isNotEmpty() ->
                            Tools.speech(c, store, model, text, caps.speakers.first(), null, null, null, "", "", p)
                        else -> null
                    }
                }
                "make_loop" -> {
                    val bpm = args.optInt("bpm", 120).let { if (it in 100..150) it else 120 }
                    Tools.loop(c, store, model, "${args.optString("tags")}, 8 bars, $bpm BPM", 8 * 4 * 60.0 / bpm, 64, 6.0, kotlin.random.Random.nextLong(0, 1L shl 31), p)
                }
                else -> {
                    val lyrics = args.optString("lyrics").trim()
                    val cfg = JSONObject().put("caption", args.optString("caption"))
                        .put("generation_type", if (lyrics.isEmpty()) "bgm" else "mixed")
                        .put("seed", kotlin.random.Random.nextLong(0, 1L shl 31))
                    if (lyrics.isNotEmpty()) cfg.put("lyrics", lyrics)
                    Tools.song(c, store, model, cfg, 30, false, null, p).entry
                }
            }
        } finally {
            Tools.putBack(c, model, unloaded, p)
        }
        if (result == null) {
            return null to "No saved voice to speak with. Tell the user to clone a voice (or pick a built-in one) once in the Voice tool first."
        }
        store.add(result)
        if (result.kind == "image") result.mainFile?.let { f ->
            lastChatImage = "data:image/png;base64," + android.util.Base64.encodeToString(store.file(f).readBytes(), android.util.Base64.NO_WRAP)
        }
        val what = mapOf("image" to "image", "speech" to "audio", "loop" to "loop", "song" to "song").getValue(result.kind)
        return result to "Done: the $what is shown to the user and saved in the app's results (made with ${model.substringAfterLast('/')})."
    }

    /**
     * Sends a message. With canMake on, the model may call tools (images, edits, speech, loops, songs); a tool's model
     * is loaded for it, unloading others if needed, and the chat model is loaded back afterwards.
     */
    fun send(model: String, text: String, image: String?, thinking: Boolean, canMake: Boolean, web: Boolean, models: List<ModelInfo>) {
        if (_chatBusy.value || (text.isBlank() && image == null)) return
        if (image != null) lastChatImage = image
        val history = _chat.value + ChatMsg("user", text.trim(), image = image)
        _chat.value = history + ChatMsg("assistant", "")
        _chatBusy.value = true
        chatStop.set(false)
        val convo = JSONArray()
        convo.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT + (if (canMake) TOOLS_PROMPT else "") + (if (web) webPrompt() else "")))
        for (m in history) {
            if (m.role == "assistant" && m.text.isBlank()) continue
            if (m.image != null) {
                val content = JSONArray()
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", m.image)))
                if (m.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", m.text))
                convo.put(JSONObject().put("role", m.role).put("content", content))
            } else {
                convo.put(JSONObject().put("role", m.role).put("content", m.text))
            }
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val c = client()
                    val mode = Tools.thinkingMode(c, model)
                    val tools = JSONArray()
                    if (canMake) toolSpecs().let { a -> for (i in 0 until a.length()) tools.put(a.get(i)) }
                    if (web) webSpecs().let { a -> for (i in 0 until a.length()) tools.put(a.get(i)) }
                    val searx = settingsStore.load().searxUrl
                    val maxRounds = if (web) 8 else 4
                    val p = Progress({ m, _ -> setStatus(m) }, { chatStop.get() })
                    for (round in 0..maxRounds) {
                        val body = JSONObject().put("model", model).put("messages", convo).put("stream", true).put("max_tokens", 4096)
                        when (mode) {
                            "always" -> body.put("reasoning_effort", if (thinking) "medium" else "low")
                            "switch" -> body.put("chat_template_kwargs", JSONObject().put("enable_thinking", thinking))
                        }
                        if (tools.length() > 0 && round < maxRounds) body.put("tools", tools)
                        setStatus("")
                        val done = c.streamChat(body, { content, reasoning ->
                            _chat.update { list ->
                                val last = list.last()
                                list.dropLast(1) + last.copy(text = last.text + content, reasoning = last.reasoning + reasoning)
                            }
                        }, { chatStop.get() })
                        if (chatStop.get() || done.calls.isEmpty()) {
                            if (done.calls.isEmpty() && done.finish == "length" && _chat.value.last().text.isBlank()) {
                                throw MediaException("The model reasoned until it ran out of room and gave no answer. Try again, or ask for less.")
                            }
                            break
                        }
                        val calls = JSONArray()
                        done.calls.forEachIndexed { i, call ->
                            if (call.id.isBlank()) call.id = "call_$i"
                            calls.put(JSONObject().put("id", call.id).put("type", "function").put("function",
                                JSONObject().put("name", call.name).put("arguments", call.args.toString().ifBlank { "{}" })))
                        }
                        convo.put(JSONObject().put("role", "assistant").put("content", done.content).put("tool_calls", calls))
                        for (call in done.calls) {
                            val args = try {
                                JSONObject(call.args.toString().ifBlank { "{}" })
                            } catch (e: Exception) {
                                JSONObject()
                            }
                            val (entry, answer) = try {
                                if (call.name == "web_search" || call.name == "open_page") null to webTool(call.name, args, searx)
                                else runTool(c, call.name, args, models, p)
                            } catch (e: Exception) {
                                if (chatStop.get()) throw e
                                null to "That didn't work: ${e.message}"
                            }
                            if (entry != null) {
                                _results.value = store.all()
                                _chat.update { list -> list.dropLast(1) + list.last().let { it.copy(results = it.results + entry) } }
                            }
                            convo.put(JSONObject().put("role", "tool").put("tool_call_id", call.id).put("content", answer))
                        }
                        // keep what was said before the tool call on screen while the reply goes on
                        _chat.update { list ->
                            val last = list.last()
                            list.dropLast(1) + last.copy(text = if (last.text.isBlank()) last.text else last.text + "\n\n")
                        }
                    }
                }
            } catch (e: Exception) {
                _chat.update { list ->
                    val last = list.last()
                    list.dropLast(1) + last.copy(text = last.text + (if (last.text.isEmpty()) "" else "\n\n") + "Error: ${e.message}")
                }
            } finally {
                setStatus("")
                _chatBusy.value = false
            }
        }
    }
}

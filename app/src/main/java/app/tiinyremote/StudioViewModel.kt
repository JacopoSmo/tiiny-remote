package app.tiinyremote

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

data class ChatInfo(val id: String, val title: String, val updated: Long)

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

private fun webPrompt() = " You can search the web with web_search, get current headlines with news, and read a page with open_page. Today is " +
    java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date()) + ". For anything " +
    "current, recent or that you're unsure of, search first instead of answering from memory; open the most promising " +
    "pages when the snippets aren't enough. Base your answer on what you found and cite the sources as Markdown links."

/** For the fast helper model: boil a page down to what the chat model needs. */
private const val PAGE_SYSTEM = "You condense a web page for another assistant who is answering the user's question. " +
    "From the page, keep only what helps answer it: facts, figures, dates, names, and short quotes with who said them, " +
    "in the page's own words for key facts. Note the page's date if it gives one. If nothing helps, reply exactly: " +
    "NOTHING RELEVANT. Under 250 words, no preamble."

private const val TITLE_SYSTEM = "Give this conversation a short title of 3 to 6 words in the user's language. " +
    "Reply with the title only: no quotes, no final period."

private const val CONDENSE_SYSTEM = "Summarise this earlier part of a conversation so the assistant can continue it: " +
    "what the user wants, facts and decisions so far, names, numbers and open questions. Keep it under 300 words, " +
    "as plain notes. Reply with the summary only."

private val WEB_TOOLS = setOf("web_search", "open_page", "news")

/** The web tools: search through the user's SearXNG, and read a page. */
private fun webSpecs(): JSONArray {
    fun fn(name: String, desc: String, props: JSONObject, req: String) = JSONObject().put("type", "function")
        .put("function", JSONObject().put("name", name).put("description", desc).put("parameters",
            JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(listOf(req)))))
    return JSONArray()
        .put(fn("web_search", "Search the web. Returns titles, links and snippets of the top results.",
            JSONObject().put("query", JSONObject().put("type", "string").put("description", "Search terms, as you'd type them into a search engine.")), "query"))
        .put(fn("news", "Current news headlines from the last day, with sources and times. Leave topic empty for the top stories.",
            JSONObject().put("topic", JSONObject().put("type", "string").put("description", "What the news should be about; empty for top stories."))
                .put("region", JSONObject().put("type", "string").put("description", "Country code for local news (ch, de, at, us, uk, fr, it) or \"world\" (default).")), "topic"))
        .put(fn("open_page", "Read the text of a web page from the search or news results, or one the user gave. Other addresses are refused.",
            JSONObject().put("url", JSONObject().put("type", "string").put("description", "The page's full http(s) address, exactly as listed in the results.")), "url"))
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

    // ---------- chats: several, each saved on the phone, able to run side by side ----------

    private val chatsDir = java.io.File(app.filesDir, "chats").apply { mkdirs() }
    /**
     * Chats with pictures are big files: they're written, renamed, deleted and listed on this one background thread,
     * in order, so the screen doesn't stutter and a late save can't bring back a chat deleted meanwhile.
     */
    private val chatDisk = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val _all = MutableStateFlow<Map<String, List<ChatMsg>>>(emptyMap())
    private val _chatList = MutableStateFlow<List<ChatInfo>>(emptyList())
    /** Titles you gave chats yourself (a save keeps them instead of using the first message). */
    private val customTitles = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** One page condensed by the helper at a time (the Tiiny takes one model's requests in turn). */
    private val helperTurn = java.util.concurrent.Semaphore(1, true)
    /** Titles the fast helper model wrote (yours win over them). */
    private val autoTitles = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** A long chat's older part, condensed by the helper: (how many messages it covers, the summary). */
    private val condensed = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, String>>()
    /** Saved chats, newest first. */
    val chatList: StateFlow<List<ChatInfo>> = _chatList.asStateFlow()
    private val _currentChat = MutableStateFlow(
        prefs.getString("chat_current", null)?.takeIf { java.io.File(chatsDir, "$it.json").exists() } ?: newChatId(),
    )
    val currentChat: StateFlow<String> = _currentChat.asStateFlow()
    val chat: StateFlow<List<ChatMsg>> = combine(_all, _currentChat) { all, id -> all[id] ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _busyChats = MutableStateFlow<Set<String>>(emptySet())
    /** Chats that are answering right now. */
    val busyChats: StateFlow<Set<String>> = _busyChats.asStateFlow()
    val chatBusy: StateFlow<Boolean> = combine(_busyChats, _currentChat) { b, id -> id in b }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val stops = java.util.concurrent.ConcurrentHashMap<String, AtomicBoolean>()
    /** The newest picture in each chat (attached or made), for edit_image. */
    private val lastImages = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    private val cancelled = AtomicBoolean(false)
    /**
     * Held while something may load or unload models on the Tiiny: a tool job, a chat's tool call, or a helper that
     * has to swap models. Only one at a time, so a chat can't unload the model a video is rendering with.
     */
    private val deviceBusy = AtomicBoolean(false)
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

    /** Runs a tool in the background; its result lands in the library. */
    fun run(tool: String, block: (DeviceClient, ResultStore, Progress, RemoteJob) -> ResultEntry) {
        if (_job.value?.running == true) {
            note("Something is already running on the Tiiny. Wait for it, or cancel it.")
            return
        }
        if (!deviceBusy.compareAndSet(false, true)) {
            note("A chat or a helper is using the Tiiny's models right now. Wait for it to finish.")
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
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Out of memory (a huge picture or file) ends this job, not the app.
                val msg = if (e is OutOfMemoryError) "Not enough memory on the phone for this. Try a smaller file or size." else e.message ?: e.toString()
                ApiLog.add("$tool failed: $msg", "Create")
                _job.value = JobUi(tool, false, error = msg)
            } finally {
                deviceBusy.set(false)
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
        Player.playing?.let { if (it in e.files) Player.stop() }
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

    init {
        loadMessages(_currentChat.value)
        chatDisk.execute { _chatList.value = loadChatList() }
    }

    override fun onCleared() {
        chatDisk.shutdown() // what's queued is still written
        super.onCleared()
    }

    private fun newChatId() = System.currentTimeMillis().toString(36) + kotlin.random.Random.nextInt(1296).toString(36)

    private fun chatFile(id: String) = java.io.File(chatsDir, "$id.json")

    private fun loadChatList(): List<ChatInfo> =
        (chatsDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).mapNotNull { f ->
            try {
                val o = JSONObject(f.readText())
                val title = o.optString("title", "Chat")
                if (o.optBoolean("custom")) customTitles[f.nameWithoutExtension] = title
                o.optString("auto_title").takeIf { it.isNotBlank() }?.let { autoTitles[f.nameWithoutExtension] = it }
                ChatInfo(f.nameWithoutExtension, title, o.optLong("updated", f.lastModified()))
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.updated }

    private fun loadMessages(id: String) {
        if (_all.value.containsKey(id)) return
        val f = chatFile(id)
        val msgs = if (!f.exists()) emptyList() else try {
            val byId = store.all().associateBy { it.id }
            val a = JSONObject(f.readText()).optJSONArray("messages") ?: JSONArray()
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val res = o.optJSONArray("results")
                val src = o.optJSONArray("sources")
                ChatMsg(
                    role = o.optString("role"),
                    text = o.optString("text"),
                    reasoning = o.optString("reasoning"),
                    image = o.optString("image").takeIf { it.isNotBlank() },
                    results = (0 until (res?.length() ?: 0)).mapNotNull { j -> byId[res!!.optString(j)] },
                    sources = (0 until (src?.length() ?: 0)).mapNotNull { j ->
                        src!!.optJSONObject(j)?.let { Source(it.optString("title"), it.optString("url")) }
                    },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        _all.update { it + (id to msgs) }
    }

    private fun saveChat(id: String) {
        val msgs = _all.value[id] ?: return
        chatDisk.execute { writeChat(id, msgs) }
    }

    /** On the chat thread. A chat deleted since the save was asked for is left deleted. */
    private fun writeChat(id: String, msgs: List<ChatMsg>) {
        if (!_all.value.containsKey(id)) return
        val f = chatFile(id)
        if (msgs.isEmpty()) {
            f.delete()
            _chatList.update { l -> l.filter { it.id != id } }
            return
        }
        val custom = customTitles[id]
        val auto = autoTitles[id]
        val title = custom ?: auto ?: msgs.firstOrNull { it.role == "user" && it.text.isNotBlank() }?.text?.lines()?.first()?.take(48)
            ?: if (msgs.any { it.image != null }) "Picture" else "Chat"
        val a = JSONArray()
        for (m in msgs) {
            val o = JSONObject().put("role", m.role).put("text", m.text)
            if (m.reasoning.isNotBlank()) o.put("reasoning", m.reasoning)
            m.image?.let { o.put("image", it) }
            if (m.results.isNotEmpty()) o.put("results", JSONArray(m.results.map { it.id }))
            if (m.sources.isNotEmpty()) o.put("sources", JSONArray().apply { m.sources.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) } })
            a.put(o)
        }
        val now = System.currentTimeMillis()
        // Written beside it first, then swapped in: an app killed mid-write can't leave a broken chat behind.
        val tmp = java.io.File(chatsDir, "$id.json.tmp")
        val doc = JSONObject().put("title", title).put("custom", custom != null).put("updated", now).put("messages", a)
        if (auto != null) doc.put("auto_title", auto)
        tmp.writeText(doc.toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
        _chatList.update { l -> (listOf(ChatInfo(id, title, now)) + l.filter { it.id != id }).sortedByDescending { it.updated } }
    }

    /** Changes a chat's messages; a chat deleted meanwhile (or never loaded) stays as it is, so a late answer can't bring it back. */
    private fun upd(id: String, f: (List<ChatMsg>) -> List<ChatMsg>) = _all.update { all ->
        val cur = all[id] ?: return@update all
        all + (id to f(cur))
    }

    fun selectChat(id: String) {
        loadMessages(id)
        _currentChat.value = id
        prefs.edit().putString("chat_current", id).apply()
    }

    /** Starts a new chat; the current one stays in the list. */
    fun newChat() {
        if ((_all.value[_currentChat.value] ?: emptyList()).isEmpty()) return
        val id = newChatId()
        _all.update { it + (id to emptyList()) }
        selectChat(id)
    }

    fun renameChat(id: String, title: String) {
        if (title.isBlank()) return
        val name = title.trim().take(60)
        chatDisk.execute {
            val f = chatFile(id)
            if (!f.exists()) return@execute
            try {
                customTitles[id] = name
                f.writeText(JSONObject(f.readText()).put("title", name).put("custom", true).toString())
                _chatList.update { l -> l.map { if (it.id == id) it.copy(title = name) else it } }
            } catch (e: Exception) {
            }
        }
    }

    fun deleteChat(id: String) {
        stops[id]?.set(true)
        _all.update { it - id }
        customTitles.remove(id)
        autoTitles.remove(id)
        condensed.remove(id)
        chatDisk.execute { chatFile(id).delete() }
        lastImages.remove(id)
        _chatList.update { l -> l.filter { it.id != id } }
        if (_currentChat.value == id) {
            val next = _chatList.value.firstOrNull()?.id
            if (next != null) selectChat(next) else {
                val n = newChatId()
                _all.update { it + (n to emptyList()) }
                selectChat(n)
            }
        }
    }

    fun stopChat() {
        stops[_currentChat.value]?.set(true)
    }

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
        if (Player.playing == voiceFile(id).name) Player.stop()
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
        // Writing helpers use the fast helper model when it's loaded.
        val fast = if (tool == "chat") settingsStore.load().helperModel.ifBlank { null } else null
        val model = ok.firstOrNull { it.running && it.id == fast } ?: ok.firstOrNull { it.running && it.id == pick(tool) } ?: ok.firstOrNull { it.running }
            ?: ok.firstOrNull { it.id == pick(tool) } ?: ok.firstOrNull()
        if (model == null) {
            note(if (tool == "chat") "This helper needs a chat model on the Tiiny." else "Auto-transcribe needs a speech-to-text (ASR) model on the Tiiny.")
            return
        }
        // A helper whose model is already loaded swaps nothing; one that must load its model waits for the device.
        val swaps = !model.running
        if (swaps && !deviceBusy.compareAndSet(false, true)) {
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
                if (swaps) deviceBusy.set(false)
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
        note("Continuing to the full song: this takes about 15 minutes.")
        run("song") { c, store, p, _ ->
            val model = o.getString("model")
            val unloaded = Tools.makeRoom(c, model, p)
            val entry = try {
                Tools.song(c, store, model, o.getJSONObject("config"), 0, false, o.getString("session"), p).entry
            } finally {
                Tools.putBack(c, model, unloaded, p)
            }
            // Forgotten only once the full song is made, so a failed attempt can be tried again.
            prefs.edit().remove("resume_${e.id}").apply()
            entry
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

    /**
     * The newest picture in a chat, for edit_image: the one remembered since the app started, else (in a chat opened
     * again after a restart) the newest one attached or made in its messages.
     */
    private fun lastImage(id: String): String? {
        lastImages[id]?.let { return it }
        for (m in (_all.value[id] ?: emptyList()).asReversed()) {
            m.results.lastOrNull { it.kind == "image" && it.mainFile != null }?.let { e ->
                val f = try {
                    store.file(e.mainFile!!)
                } catch (x: Exception) {
                    null
                }
                if (f != null && f.exists()) {
                    return ("data:image/png;base64," + android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                        .also { lastImages[id] = it }
                }
            }
            m.image?.let { return it.also { img -> lastImages[id] = img } }
        }
        return null
    }

    /** Which installed model a tool uses: the one picked in that tool, else a loaded one, else the first. */
    private fun modelFor(tool: String, models: List<ModelInfo>, fits: (ModelInfo) -> Boolean): String? {
        val ok = models.filter(fits)
        return ok.firstOrNull { it.id == pick(tool) }?.id ?: ok.firstOrNull { it.running }?.id ?: ok.firstOrNull()?.id
    }

    private fun setStatus(id: String, text: String) {
        upd(id) { list -> if (list.isEmpty()) list else list.dropLast(1) + list.last().copy(status = text) }
    }

    /** Runs web_search or open_page and notes the sources on the reply. */
    /**
     * Runs web_search, news or open_page and notes the sources on the reply. With a fast helper model loaded, an
     * opened page reaches the chat model boiled down to what answers `question` instead of as 8,000 raw characters.
     */
    private fun webTool(id: String, name: String, args: JSONObject, cfg: AppSettings, c: DeviceClient, helper: String?, question: String): String {
        if (name == "web_search") {
            val q = args.optString("query").trim().ifBlank { return "Give a query to search for." }
            setStatus(id, "Searching the web: $q")
            val found = WebSearch.search(cfg, q)
            val hits = found.hits
            ApiLog.add("web search via ${found.via}: ${hits.size} results" + if (found.skipped.isNotEmpty()) " (skipped: ${found.skipped.joinToString("; ")})" else "")
            if (hits.isEmpty()) return "No results for \"$q\". Try other words."
            upd(id) { list ->
                if (list.isEmpty()) list else list.dropLast(1) + list.last().let { m -> m.copy(sources = (m.sources + hits.map { Source(it.title, it.url) }).distinctBy { it.url }) }
            }
            return hits.mapIndexed { i, h -> "${i + 1}. ${h.title}\n${h.url}\n${h.snippet.take(400)}" }.joinToString("\n\n")
        }
        if (name == "news") {
            val topic = args.optString("topic").trim()
            val region = args.optString("region").trim()
            setStatus(id, if (topic.isEmpty()) "Getting today's headlines" else "Getting news: $topic")
            val found = WebSearch.news(cfg, topic, region)
            ApiLog.add("news via ${found.via}: ${found.hits.size} items" + if (found.skipped.isNotEmpty()) " (skipped: ${found.skipped.joinToString("; ")})" else "")
            upd(id) { list ->
                if (list.isEmpty()) list else list.dropLast(1) + list.last().let { m -> m.copy(sources = (m.sources + found.hits.map { Source(it.title, it.url) }).distinctBy { it.url }) }
            }
            return "News (via ${found.via}):\n\n" + found.hits.mapIndexed { i, h -> "${i + 1}. ${h.title}\n${h.url}\n${h.snippet.take(300)}" }.joinToString("\n\n")
        }
        val url = args.optString("url").trim()
        // A page could tell the model to "open" an address carrying parts of this chat to someone's server. So only
        // addresses that came from this chat's search or news results, or that you wrote yourself, can be opened.
        if (!pageAllowed(id, url)) {
            ApiLog.add("open_page refused an address that wasn't in the results or your messages", "Search")
            return "That address can't be opened: only pages from this chat's search or news results, or addresses " +
                "the user wrote, can be read. Search for it first if you need it."
        }
        val host = url.substringAfter("://").substringBefore('/')
        setStatus(id, "Reading $host")
        val text = WebSearch.read(url)
        upd(id) { list ->
            if (list.isEmpty()) list else list.dropLast(1) + list.last().let { m -> if (m.sources.any { it.url == url }) m else m.copy(sources = m.sources + Source(host, url)) }
        }
        if (helper != null && text.length > 1500) {
            // Pages are fetched at the same time, but condensed one after the other: the speed test showed the Tiiny
            // answers one model's requests in turn anyway, so queueing them here keeps the status honest.
            setStatus(id, "Waiting to condense $host")
            helperTurn.acquire()
            setStatus(id, "Condensing $host with ${helper.substringAfterLast('/')}")
            val started = System.currentTimeMillis()
            try {
                val summary = Tools.chatOnce(c, helper, PAGE_SYSTEM, "Question: ${question.take(1000)}\n\nPage ($url):\n$text", 450)
                ApiLog.add("Page summarised by the helper: ${text.length} → ${summary.length} characters in ${(System.currentTimeMillis() - started) / 1000} s", "Chat")
                return "Page $url (condensed by a helper model to what answers the question):\n\n$summary"
            } catch (e: Exception) {
                ApiLog.add("The helper couldn't summarise $host (${e.message}); the chat model gets the page itself", "Chat")
            } finally {
                helperTurn.release()
            }
        }
        return "Page $url:\n\n$text"
    }

    /** The same address, written slightly differently (no #part, no trailing /, any case in the host). */
    private fun sameUrl(u: String): String {
        val noFrag = u.trim().substringBefore('#').trimEnd('/')
        val scheme = noFrag.substringBefore("://", "")
        if (scheme.isEmpty()) return noFrag
        val rest = noFrag.substringAfter("://")
        val host = rest.substringBefore('/')
        return scheme.lowercase() + "://" + host.lowercase() + rest.removePrefix(host)
    }

    /** open_page only reads addresses this chat's results listed, or that the user wrote in a message. */
    private fun pageAllowed(id: String, url: String): Boolean {
        val want = sameUrl(url)
        val msgs = _all.value[id] ?: return false
        if (msgs.any { m -> m.sources.any { sameUrl(it.url) == want } }) return true
        val linkInText = Regex("""https?://[^\s<>()\[\]"']+""")
        return msgs.filter { it.role == "user" }.any { m ->
            linkInText.findAll(m.text).any { sameUrl(it.value.trimEnd('.', ',', ';', ':', '!', '?')) == want }
        }
    }

    /** Runs one tool call for the chat model; returns (result or null, text for the model). */
    private fun runTool(id: String, c: DeviceClient, name: String, args: JSONObject, models: List<ModelInfo>, p: Progress): Pair<ResultEntry?, String> {
        val (tool, fits) = when (name) {
            "create_image" -> "image" to { m: ModelInfo -> m.isImageGen }
            "edit_image" -> "edit" to { m: ModelInfo -> m.canEditImages }
            "speak" -> "speech" to { m: ModelInfo -> m.isSpeech }
            "make_loop" -> "loop" to { m: ModelInfo -> m.isLoopModel }
            "make_song" -> "song" to { m: ModelInfo -> m.isSongModel }
            else -> return null to "Unknown tool $name"
        }
        val model = modelFor(tool, models, fits) ?: return null to "No model for ${name.replace('_', ' ')} is installed on the device."
        if (name == "edit_image" && lastImage(id) == null) {
            return null to "There's no image in the conversation to edit. Ask the user to attach one."
        }
        if (!deviceBusy.compareAndSet(false, true)) {
            return null to "The Tiiny is busy with another job right now (a tool in the app or another chat). " +
                "Tell the user to try again when it's done."
        }
        val result = try {
            val unloaded = Tools.makeRoom(c, model, p)
            try { when (name) {
                "create_image" -> {
                    val size = when (args.optString("shape")) {
                        "portrait" -> "768x1024"
                        "landscape" -> "1024x768"
                        "wide" -> "1024x576"
                        else -> "1024x1024"
                    }
                    Tools.image(c, store, model, args.optString("prompt"), "", size, null, null, emptyList(), p)
                }
                "edit_image" -> Tools.image(c, store, model, args.optString("instruction"), "", "", null, null, listOf(lastImage(id)!!), p)
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
            } } finally {
                Tools.putBack(c, model, unloaded, p)
            }
        } finally {
            deviceBusy.set(false)
        }
        if (result == null) {
            return null to "No saved voice to speak with. Tell the user to clone a voice (or pick a built-in one) once in the Voice tool first."
        }
        store.add(result)
        if (result.kind == "image") result.mainFile?.let { f ->
            lastImages[id] = "data:image/png;base64," + android.util.Base64.encodeToString(store.file(f).readBytes(), android.util.Base64.NO_WRAP)
        }
        val what = mapOf("image" to "image", "speech" to "audio", "loop" to "loop", "song" to "song").getValue(result.kind)
        return result to "Done: the $what is shown to the user and saved in the app's results (made with ${model.substringAfterLast('/')})."
    }

    // ---------- speed test: one model at a time, or two at once ----------

    private val _speed = MutableStateFlow<String?>(null)
    /** The speed test's progress, then its result. */
    val speed: StateFlow<String?> = _speed.asStateFlow()

    /**
     * Measures how fast the chat model and the helper write alone, then at the same time, and the helper twice at
     * once (how pages are condensed). Both must be loaded; nothing is swapped. Tells whether parallel work pays off.
     */
    fun speedTest(big: ModelInfo, small: ModelInfo) {
        if (_speed.value?.startsWith("Testing") == true) return
        if (!deviceBusy.compareAndSet(false, true)) {
            note("The Tiiny is busy with a job: run the test when it's done.")
            return
        }
        _speed.value = "Testing…"
        viewModelScope.launch {
            try {
                val out = withContext(Dispatchers.IO) {
                    val c = client()
                    /** (tokens written, seconds taken) for one ~300-token answer, thinking off where possible. */
                    fun one(model: String): Pair<Int, Double> {
                        val body = JSONObject().put("model", model).put("stream", false).put("max_tokens", 300).put("temperature", 0.7)
                            .put("messages", JSONArray().put(JSONObject().put("role", "user")
                                .put("content", "Write a story of about 250 words about a lighthouse keeper and a storm.")))
                        when (Tools.thinkingMode(c, model)) {
                            "switch" -> body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                            "always" -> body.put("reasoning_effort", "low")
                        }
                        val t0 = System.nanoTime()
                        val r = c.request("POST", "/v1/chat/completions", body, 600)
                        val secs = (System.nanoTime() - t0) / 1e9
                        if (!r.ok) throw MediaException("${model.substringAfterLast('/')}: ${r.error()}")
                        val o = r.json()
                        val msg = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                        val written = (msg?.optString("content").orEmpty() + msg?.optString("reasoning_content").orEmpty()).length
                        val tokens = o.optJSONObject("usage")?.optInt("completion_tokens", 0)?.takeIf { it > 0 } ?: maxOf(1, written / 4)
                        return tokens to secs
                    }
                    fun rate(r: Pair<Int, Double>) = r.first / r.second
                    _speed.value = "Testing ${big.name} alone…"
                    val a = one(big.id)
                    _speed.value = "Testing ${small.name} alone…"
                    val b = one(small.id)
                    _speed.value = "Testing both at the same time…"
                    val (a2, b2) = coroutineScope {
                        val x = async { one(big.id) }
                        val y = async { one(small.id) }
                        x.await() to y.await()
                    }
                    _speed.value = "Testing ${small.name} twice at the same time…"
                    val (h1, h2) = coroutineScope {
                        val x = async { one(small.id) }
                        val y = async { one(small.id) }
                        x.await() to y.await()
                    }
                    fun f(v: Double) = String.format(java.util.Locale.ROOT, "%.1f", v)
                    // The same work (what each wrote alone), timed one after the other and at the same time. Times,
                    // not added-up speeds: when the Tiiny takes requests in turn, two "speeds" would add up to nonsense.
                    val seq = a.first / rate(a) + b.first / rate(b)
                    val par = maxOf(a.first / rate(a2), b.first / rate(b2))
                    val helperSeq = 2 * b.first / rate(b)
                    val helperPar = maxOf(b.first / rate(h1), b.first / rate(h2))
                    fun verdict(seqS: Double, parS: Double): String {
                        val p = (1 - parS / seqS) * 100
                        val times = "${Math.round(parS)} s instead of ${Math.round(seqS)} s one after the other"
                        return when {
                            p >= 10 -> "$times (${Math.round(p)}% sooner): worth it"
                            p >= 3 -> "$times (${Math.round(p)}% sooner): a little faster"
                            p > -3 -> "$times: no gain, the Tiiny takes them in turn"
                            p > -5 -> "$times: about the same"
                            else -> "$times (${Math.round(-p)}% slower): better one after the other"
                        }
                    }
                    "Alone: ${big.name} ${f(rate(a))} tok/s, ${small.name} ${f(rate(b))} tok/s.\n" +
                        "Both models at once: ${verdict(seq, par)}.\n" +
                        "${small.name} twice at once: ${verdict(helperSeq, helperPar)}."
                }
                _speed.value = out
                ApiLog.add("Speed test: " + out.replace("\n", " "), "Chat")
            } catch (e: Exception) {
                _speed.value = "Speed test failed: ${e.message}"
                ApiLog.add("Speed test failed: ${e.message}", "Chat")
            } finally {
                deviceBusy.set(false)
            }
        }
    }

    /** The fast helper model chosen in Settings, when it's loaded and isn't the chat model itself (never swapped in for this). */
    private fun fastHelper(cfg: AppSettings, models: List<ModelInfo>, chatModel: String): String? =
        cfg.helperModel.takeIf { it.isNotBlank() && it != chatModel }
            ?.let { h -> models.firstOrNull { it.id == h && it.running && it.isChat }?.id }

    /** A message as the chat model receives it (with its picture, if any). */
    private fun asMessage(m: ChatMsg): JSONObject =
        if (m.image != null) {
            val content = JSONArray()
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", m.image)))
            if (m.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", m.text))
            JSONObject().put("role", m.role).put("content", content)
        } else {
            JSONObject().put("role", m.role).put("content", m.text)
        }

    /**
     * A long chat (over ~20,000 characters) keeps its last 6 messages as they are; the helper condenses everything before
     * into notes, so the chat model reads much less each turn. Done again only when 6 more messages have piled up.
     */
    private fun condenseIfLong(id: String, c: DeviceClient, helper: String, history: List<ChatMsg>): Pair<Int, String>? {
        val size = history.sumOf { it.text.length }
        if (size < 20_000 || history.size <= 8) return null
        val cut = history.size - 6
        condensed[id]?.let { (n, text) -> if (cut - n < 6) return n to text }
        setStatus(id, "Condensing the earlier chat with ${helper.substringAfterLast('/')}")
        val transcript = history.take(cut).joinToString("\n\n") { m ->
            (if (m.role == "user") "User: " else "Assistant: ") + m.text.take(3000) + (if (m.image != null) " [picture]" else "")
        }.takeLast(60_000)
        return try {
            val notes = Tools.chatOnce(c, helper, CONDENSE_SYSTEM, transcript, 700)
            ApiLog.add("Long chat condensed by the helper: $cut messages → ${notes.length} characters", "Chat")
            (cut to notes).also { condensed[id] = it }
        } catch (e: Exception) {
            ApiLog.add("The helper couldn't condense the chat (${e.message}); sending it in full", "Chat")
            null
        }
    }

    /**
     * Sends a message. With canMake on, the model may call tools (images, edits, speech, loops, songs); a tool's model
     * is loaded for it, unloading others if needed, and the chat model is loaded back afterwards. With a fast helper
     * model loaded beside it, the helper condenses pages (several at once) and long chats, and names the chat.
     */
    fun send(model: String, text: String, image: String?, thinking: Boolean, canMake: Boolean, web: Boolean, models: List<ModelInfo>) {
        val id = _currentChat.value
        if (id in _busyChats.value || (text.isBlank() && image == null)) return
        if (image != null) lastImages[id] = image
        val history = (_all.value[id] ?: emptyList()) + ChatMsg("user", text.trim(), image = image)
        upd(id) { history + ChatMsg("assistant", "") }
        saveChat(id)
        _busyChats.update { it + id }
        val chatStop = AtomicBoolean(false)
        stops[id] = chatStop
        val question = text.trim()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val c = client()
                    val cfg = settingsStore.load()
                    val helper = fastHelper(cfg, models, model)
                    val shown = history.filter { !(it.role == "assistant" && it.text.isBlank()) }
                    // A new chat is named by the helper from its first question, while the chat model answers: the speed
                    // test showed two different models working at once finish sooner than one after the other.
                    if (helper != null && shown.count { it.role == "user" } == 1 && question.isNotBlank() &&
                        customTitles[id] == null && autoTitles[id] == null
                    ) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val t = Tools.chatOnce(c, helper, TITLE_SYSTEM, "User: ${question.take(1500)}", 30)
                                    .lines().first().trim().trim('"', '“', '”', '*', '#', ' ').trimEnd('.').take(48)
                                if (t.isNotBlank() && _all.value.containsKey(id) && customTitles[id] == null) {
                                    autoTitles[id] = t
                                    saveChat(id)
                                }
                            } catch (e: Exception) {
                                ApiLog.add("The helper couldn't name the chat (${e.message})", "Chat")
                            }
                        }
                    }
                    val convo = JSONArray()
                    var system = SYSTEM_PROMPT + (if (canMake) TOOLS_PROMPT else "") + (if (web) webPrompt() else "")
                    val notes = helper?.let { condenseIfLong(id, c, it, shown) }
                    if (notes != null) {
                        system += "\n\nNotes on the earlier part of this conversation (condensed):\n" + notes.second
                        convo.put(JSONObject().put("role", "system").put("content", system))
                        for (m in shown.drop(notes.first)) convo.put(asMessage(m))
                    } else {
                        convo.put(JSONObject().put("role", "system").put("content", system))
                        for (m in shown) convo.put(asMessage(m))
                    }
                    val mode = Tools.thinkingMode(c, model)
                    val tools = JSONArray()
                    if (canMake) toolSpecs().let { a -> for (i in 0 until a.length()) tools.put(a.get(i)) }
                    if (web) webSpecs().let { a -> for (i in 0 until a.length()) tools.put(a.get(i)) }
                    val maxRounds = if (web) 8 else 4
                    val p = Progress({ m, _ -> setStatus(id, m) }, { chatStop.get() })
                    // Pages are read (and condensed) up to 3 at a time; the media tools one after the other.
                    val pages = Semaphore(3)
                    for (round in 0..maxRounds) {
                        val body = JSONObject().put("model", model).put("messages", convo).put("stream", true).put("max_tokens", 4096)
                        when (mode) {
                            "always" -> body.put("reasoning_effort", if (thinking) "medium" else "low")
                            "switch" -> body.put("chat_template_kwargs", JSONObject().put("enable_thinking", thinking))
                        }
                        if (tools.length() > 0 && round < maxRounds) body.put("tools", tools)
                        setStatus(id, "")
                        val done = c.streamChat(body, { content, reasoning ->
                            upd(id) { list ->
                                val last = list.lastOrNull() ?: return@upd list
                                list.dropLast(1) + last.copy(text = last.text + content, reasoning = last.reasoning + reasoning)
                            }
                        }, { chatStop.get() })
                        if (chatStop.get() || done.calls.isEmpty()) {
                            if (done.calls.isEmpty() && done.finish == "length" && (_all.value[id]?.lastOrNull()?.text ?: "").isBlank()) {
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
                        fun doCall(call: DeviceClient.ToolCall): Pair<ResultEntry?, String> {
                            val args = try {
                                JSONObject(call.args.toString().ifBlank { "{}" })
                            } catch (e: Exception) {
                                JSONObject()
                            }
                            return try {
                                if (call.name in WEB_TOOLS) null to webTool(id, call.name, args, cfg, c, helper, question)
                                else runTool(id, c, call.name, args, models, p)
                            } catch (e: Exception) {
                                if (chatStop.get()) throw e
                                null to "That didn't work: ${e.message}"
                            }
                        }
                        val answers = arrayOfNulls<Pair<ResultEntry?, String>>(done.calls.size)
                        coroutineScope {
                            val pageJobs = done.calls.withIndex().filter { it.value.name in WEB_TOOLS }.map { (i, call) ->
                                async(Dispatchers.IO) { i to pages.withPermit { doCall(call) } }
                            }
                            for ((i, call) in done.calls.withIndex()) if (call.name !in WEB_TOOLS) answers[i] = doCall(call)
                            for ((i, r) in pageJobs.awaitAll()) answers[i] = r
                        }
                        for ((i, call) in done.calls.withIndex()) {
                            val (entry, answer) = answers[i] ?: (null to "No answer.")
                            if (entry != null) {
                                _results.value = store.all()
                                upd(id) { list -> if (list.isEmpty()) list else list.dropLast(1) + list.last().let { it.copy(results = it.results + entry) } }
                            }
                            convo.put(JSONObject().put("role", "tool").put("tool_call_id", call.id).put("content", answer))
                        }
                        // keep what was said before the tool call on screen while the reply goes on
                        upd(id) { list ->
                            val last = list.lastOrNull() ?: return@upd list
                            list.dropLast(1) + last.copy(text = if (last.text.isBlank()) last.text else last.text + "\n\n")
                        }
                    }
                }
            } catch (e: Exception) {
                upd(id) { list ->
                    val last = list.lastOrNull() ?: return@upd list
                    list.dropLast(1) + last.copy(text = last.text + (if (last.text.isEmpty()) "" else "\n\n") + "Error: ${e.message}")
                }
            } finally {
                setStatus(id, "")
                _busyChats.update { it - id }
                stops.remove(id)
                if (_all.value.containsKey(id)) saveChat(id)
            }
        }
    }
}

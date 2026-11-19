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

data class ChatMsg(
    val role: String,
    val text: String,
    val reasoning: String = "",
    /** A picture sent with the message, as a data URI. */
    val image: String? = null,
)

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

    fun video(model: String, prompt: String, size: String, seconds: Int, steps: Int, seed: Long?, image: String?) {
        val title = "Video: " + prompt.ifBlank { "from a picture" }.take(60)
        run("video") { c, store, p, rj ->
            p.update("Sending the clip to the Tiiny", null)
            val vid = Tools.videoStart(c, model, prompt, size, seconds, steps, seed, image)
            prefs.edit().putString("pending_video", JSONObject().put("id", vid).put("model", model).put("title", title).toString()).apply()
            try {
                Tools.videoFinish(c, store, model, vid, title, rj, p)
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
                Tools.videoFinish(c, store, o.optString("model"), vid, o.optString("title"), rj, p)
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
        if (!_chatBusy.value) _chat.value = emptyList()
    }

    fun stopChat() {
        chatStop.set(true)
    }

    fun send(model: String, text: String, image: String?, thinking: Boolean) {
        if (_chatBusy.value || (text.isBlank() && image == null)) return
        val history = _chat.value + ChatMsg("user", text.trim(), image = image)
        _chat.value = history + ChatMsg("assistant", "")
        _chatBusy.value = true
        chatStop.set(false)
        val messages = JSONArray()
        for (m in history) {
            if (m.role == "assistant" && m.text.isBlank()) continue
            if (m.image != null) {
                val content = JSONArray()
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", m.image)))
                if (m.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", m.text))
                messages.put(JSONObject().put("role", m.role).put("content", content))
            } else {
                messages.put(JSONObject().put("role", m.role).put("content", m.text))
            }
        }
        val body = JSONObject().put("model", model).put("stream", true).put("messages", messages)
        if (!thinking) body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client().streamChat(body, { content, reasoning ->
                        _chat.update { list ->
                            val last = list.last()
                            list.dropLast(1) + last.copy(text = last.text + content, reasoning = last.reasoning + reasoning)
                        }
                    }, { chatStop.get() })
                }
            } catch (e: Exception) {
                _chat.update { list ->
                    val last = list.last()
                    list.dropLast(1) + last.copy(text = last.text + (if (last.text.isEmpty()) "" else "\n\n") + "Error: ${e.message}")
                }
            } finally {
                _chatBusy.value = false
            }
        }
    }
}

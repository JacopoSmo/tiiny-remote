package app.tiinyremote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One recommended model for a task, and why, in a few words. */
data class Rec(val task: String, val id: String, val why: String)

/** The whole set: when it was made and by whom (the built-in one, or a model on the Tiiny that reviewed the Store). */
data class RecSet(val asOf: String, val by: String, val recs: List<Rec>)

/** A task recommendations are made for: its key, its name in the table, and what it means (for the model too). */
class RecTask(val key: String, val label: String, val what: String)

/**
 * Recommended models: which Store model is best for each task, shown as 👍 in the model menus and as a table
 * (Models → 👍). The built-in set comes from public benchmarks (as of 26 Nov 2026); "Update with the Tiiny's AI"
 * has the most capable chat model on the Tiiny read the Store list and decide anew, nothing from outside needed.
 */
object Recs {
    val TASKS = listOf(
        RecTask("chat", "Chat, careful", "general chat and reasoning where quality matters most; may be slower"),
        RecTask("chatfast", "Chat, fast · code", "quick everyday chat and writing code; speed matters"),
        RecTask("helper", "Fast helper (Settings)", "a small, quick chat model loaded beside the main one: names chats, condenses pages and long chats, writes prompts; small"),
        RecTask("image", "Create images", "text-to-image quality, text in images"),
        RecTask("edit", "Edit images", "changing a photo with words"),
        RecTask("pe_t2i", "Prompts for Qwen-Image", "rewriting image prompts for Qwen-Image (prompt enhancer)"),
        RecTask("pe_i2i", "Edit prompts for Qwen-Image", "rewriting edit instructions for Qwen-Image (prompt enhancer)"),
        RecTask("q_extend", "Extend a picture", "outpainting over green borders"),
        RecTask("q_remove", "Remove an object", "removing a red-marked object"),
        RecTask("q_background", "Remove the background", "cutting out the subject"),
        RecTask("q_zoom", "Zoom into a part", "a close-up of a red-marked area"),
        RecTask("ocr", "Read text (OCR)", "text, tables and formulas from photos and documents"),
        RecTask("transcribe", "Speech to text", "accurate transcription, many languages incl. German"),
        RecTask("meetings", "Who said what", "transcripts with speakers and times"),
        RecTask("speech", "Read aloud", "natural voices in many languages incl. German, voice cloning"),
        RecTask("speechfast", "Read aloud, tiny", "a very small voice model to keep loaded (Always ready)"),
        RecTask("song", "Songs", "full songs with vocals"),
        RecTask("loop", "Music loops", "short loops at a set tempo"),
        RecTask("mesh", "3D models", "a 3D model from one image"),
        RecTask("video", "Video", "short clips"),
        RecTask("embed", "Project search", "embedding for searching files by meaning; small"),
        RecTask("rerank", "Project search order", "reranking search results; small"),
    )

    /** Which tasks a tool's model menu shows recommendations for. */
    fun tasksFor(tool: String): List<String> = when (tool) {
        "chat", "describe" -> listOf("chat", "chatfast")
        "code" -> listOf("chatfast", "chat")
        "image" -> listOf("image")
        "edit" -> listOf("edit")
        "ocr" -> listOf("ocr")
        "transcribe" -> listOf("transcribe", "meetings")
        "speech" -> listOf("speech", "speechfast")
        "song" -> listOf("song")
        "loop" -> listOf("loop")
        "mesh" -> listOf("mesh")
        "video" -> listOf("video")
        "embed" -> listOf("embed")
        "rerank" -> listOf("rerank")
        else -> emptyList()
    }

    private val BUILT_IN = RecSet(
        "26 Nov 2026", "public benchmarks",
        listOf(
            Rec("chat", "Qwen/Qwen3.8-27B", "most capable (intelligence 52)"),
            Rec("chatfast", "Qwen/Qwen3.6-35B-A3B-Turbo", "about 3× faster, strong at code"),
            Rec("helper", "Qwen/Qwen3.5-9B-Turbo", "newest small one, 24% NPU"),
            Rec("helper", "Qwen/Qwen3-4B-Instruct-2507", "no thinking: quickest replies"),
            Rec("image", "Qwen/Qwen-Image-2.1", "best open image model here"),
            Rec("edit", "HiDream-ai/HiDream-O1-Image-Dev", "top open editor, 16% NPU"),
            Rec("edit", "Qwen/Qwen-Image-2.1", "edits too, text in images"),
            Rec("pe_t2i", "Qwen/Qwen-Image-2.1-PE-T2I", "made for Qwen-Image's prompts"),
            Rec("pe_i2i", "Qwen/Qwen-Image-2.1-PE-I2I", "made for Qwen-Image's edits"),
            Rec("q_extend", "fal/FLUX.2-klein-4B-Outpaint", "trained for exactly this"),
            Rec("q_remove", "fal/FLUX.2-klein-4B-Object-Remove", "trained for exactly this"),
            Rec("q_background", "fal/FLUX.2-klein-4B-Background-Remove", "trained for exactly this"),
            Rec("q_zoom", "fal/FLUX.2-klein-4B-Zoom", "trained for exactly this"),
            Rec("ocr", "zai-org/GLM-OCR", "tops document benchmarks, 4% NPU"),
            Rec("transcribe", "Qwen/Qwen3-ASR-1.7B", "most accurate open speech-to-text"),
            Rec("meetings", "OpenMOSS-Team/MOSS-Transcribe-Diarize", "speakers and times in one pass"),
            Rec("speech", "openbmb/VoxCPM2", "30 languages incl. German, cloning"),
            Rec("speechfast", "hexgrad/Kokoro-82M", "tiny and fast (English)"),
            Rec("song", "ACE-Step/AceStep-XL-Turbo", "beats Suno v5 on SongEval"),
            Rec("loop", "RoyalCities/Foundation-1", "loops at a set tempo, 5% NPU"),
            Rec("mesh", "Tencent/Hunyuan3d-2.1", "most detailed"),
            Rec("mesh", "microsoft/TRELLIS.2-4B", "faster, 6% NPU"),
            Rec("video", "lightx2v/MiniMax-H3-Turbo", "the Store's video model"),
            Rec("embed", "Qwen/Qwen3-Embedding-0.6B", "small and good, 1% NPU"),
            Rec("rerank", "Qwen/Qwen3-Reranker-0.6B", "small and good, 2% NPU"),
        ),
    )

    private val _set = MutableStateFlow(BUILT_IN)
    val set: StateFlow<RecSet> = _set
    private fun file(ctx: Context) = File(ctx.filesDir, "recommendations.json")

    fun load(ctx: Context) {
        runCatching { parse(JSONObject(file(ctx).readText())) }.getOrNull()?.let { _set.value = it }
    }

    private fun parse(o: JSONObject): RecSet {
        val a = o.getJSONArray("recs")
        return RecSet(o.optString("as_of"), o.optString("by"), (0 until a.length()).map { a.getJSONObject(it) }.map { Rec(it.getString("task"), it.getString("id"), it.optString("why")) })
    }

    private fun save(ctx: Context, s: RecSet) {
        val a = JSONArray()
        s.recs.forEach { a.put(JSONObject().put("task", it.task).put("id", it.id).put("why", it.why)) }
        File(ctx.filesDir, "recommendations.json.tmp").also { it.writeText(JSONObject().put("as_of", s.asOf).put("by", s.by).put("recs", a).toString()) }
            .renameTo(file(ctx))
        _set.value = s
    }

    /** Back to the built-in set. */
    fun reset(ctx: Context) {
        file(ctx).delete()
        _set.value = BUILT_IN
    }

    fun forTasks(tasks: List<String>): List<Rec> = _set.value.recs.filter { it.task in tasks }.sortedBy { tasks.indexOf(it.task) }

    /** The recommended model for one task (the first), or null. */
    fun first(task: String): Rec? = _set.value.recs.firstOrNull { it.task == task }

    // ---------- made anew by the Tiiny's own AI ----------

    private const val SYSTEM = "You advise which AI models to install on a small home AI computer (a Tiiny, 48 GB NPU memory). " +
        "You get its model store as a list (id · type · size in parameters · share of the NPU it needs · download size · " +
        "context · abilities · version date · description) and a list of tasks. For each task pick the best one or two models " +
        "FROM THE LIST ONLY. Judge by what the list says: model generation and version date (newer families usually beat older " +
        "ones), size, abilities, description and known model families. A model must fit beside others: prefer under 60% of the " +
        "NPU unless the task needs more; for tasks marked small, prefer the smallest that does the job well. Do not invent ids. " +
        "Answer with JSON only, no prose: {\"recs\":[{\"task\":\"<task key>\",\"id\":\"<exact id>\",\"why\":\"<at most 8 words>\"}]}"

    /** The store as lines for the model: short, one per model, the description cut. */
    private fun storeLines(store: List<StoreModel>): String = store.joinToString("\n") { m ->
        listOfNotNull(
            m.id, m.type, m.params.ifBlank { null }, m.npuUsage?.let { "${Math.round(it)}% NPU" },
            if (m.sizeBytes > 0) "%.1f GB".format(java.util.Locale.ROOT, m.sizeBytes / 1e9) else null,
            m.contextWindow?.let { "${it / 1024}k ctx" }, m.supported.joinToString(", ").ifBlank { null },
            m.versionLabel.ifBlank { null }, m.desc.replace('\n', ' ').take(220).ifBlank { null },
        ).joinToString(" · ")
    }

    /**
     * Has [model] (loaded already) review the Store and write the set anew. Thinks first (high effort); the answer is
     * checked: only ids in the Store and known tasks are kept, at most two per task; a task it skipped keeps its pick.
     * Returns a short summary of what changed.
     */
    fun update(ctx: Context, c: DeviceClient, model: ModelInfo, store: List<StoreModel>, p: Progress): String {
        val tasks = TASKS.joinToString("\n") { "${it.key}: ${it.label} (${it.what})" }
        val current = _set.value.recs.joinToString("\n") { "${it.task}: ${it.id} (${it.why})" }
        val user = "Tasks:\n$tasks\n\nCurrent recommendations (keep a pick if nothing in the store is clearly better):\n$current\n\n" +
            "Store (${store.size} models):\n${storeLines(store)}"
        p.update("${model.name} is reviewing ${store.size} models", null)
        val mode = Tools.thinkingMode(c, model.id)
        val body = JSONObject().put("model", model.id).put("max_tokens", 12000).put("stream", true)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM))
                .put(JSONObject().put("role", "user").put("content", user)))
        when (mode) {
            "always" -> body.put("reasoning_effort", "high")
            "switch" -> body.put("chat_template_kwargs", JSONObject().put("enable_thinking", true))
        }
        // Streamed: the thinking takes minutes, and a connection silent that long was cut (tested: "connection
        // abort" after 8 minutes). Text keeps coming this way, and the progress shows how far it is. Cut anyway (the
        // phone's Wi-Fi), it's asked once more.
        var thought = 0
        var lastShown = 0L
        fun ask(): DeviceClient.Completion = c.streamChat(body, { content, reasoning ->
            thought += content.length + reasoning.length
            val now = System.currentTimeMillis()
            if (now - lastShown > 1500) {
                lastShown = now
                p.update("${model.name} is thinking it over (${thought / 1000}k characters)", null)
            }
        }, { false })
        val done = try {
            ask().also { if (!it.ended) throw java.io.IOException("the answer stopped before its end") }
        } catch (e: java.io.IOException) {
            ApiLog.add("Recommendation review: the connection dropped (${e.message}); asking once more", "Models")
            thought = 0
            ask()
        }
        val text = done.content.replace(Regex("(?s)<think>.*?</think>"), "")
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        val arr = (if (start in 0 until end) runCatching { JSONObject(text.substring(start, end + 1)).getJSONArray("recs") }.getOrNull() else null)
            ?: throw MediaException("${model.name} didn't answer in the expected form. Try again, or with another model.")
        val ids = store.map { it.id }.toSet()
        val keys = TASKS.map { it.key }.toSet()
        val got = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .map { Rec(it.optString("task"), it.optString("id"), it.optString("why").take(60)) }
            .filter { it.task in keys && it.id in ids }
            .groupBy { it.task }.mapValues { (_, l) -> l.distinctBy { it.id }.take(2) }
        if (got.isEmpty()) throw MediaException("${model.name} named no model from the Store. Try again, or with another model.")
        val old = _set.value.recs.groupBy { it.task }
        val merged = TASKS.flatMap { t -> got[t.key] ?: old[t.key].orEmpty() }
        val changed = TASKS.filter { t -> got[t.key]?.map { it.id } != null && got[t.key]!!.map { it.id } != old[t.key].orEmpty().map { it.id } }
        val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
        save(ctx, RecSet(date, model.name, merged))
        val summary = if (changed.isEmpty()) "No changes: the picks still stand." else "Changed: " + changed.joinToString("; ") { t ->
            t.label + " → " + got[t.key]!!.joinToString(", ") { it.id.substringAfterLast('/') }
        }
        ApiLog.add("Recommendations reviewed by ${model.name} (${got.size} tasks answered). $summary", "Models")
        return summary
    }

    /** The model that reviews: the recommended careful chat model if it's on the Tiiny, else the biggest one that thinks. */
    fun reviewer(models: List<ModelInfo>): ModelInfo? {
        val chats = models.filter { it.isChat }
        return first("chat")?.let { r -> chats.firstOrNull { it.id == r.id } }
            ?: chats.filter { it.thinks }.maxByOrNull { it.npuUsage ?: 0.0 }
            ?: chats.maxByOrNull { it.npuUsage ?: 0.0 }
    }
}

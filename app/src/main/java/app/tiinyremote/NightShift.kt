package app.tiinyremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Something to make tonight: the kind of job, its title, and what the NAS agent needs for it. */
data class NightJob(val kind: String, val title: String, val spec: JSONObject)

/**
 * The phone's side of the NAS agent's jobs (agent 1.8): tests run on the NAS now or tonight, and things made in the
 * night shift. Sends them, follows them, and brings finished ones into the app (tests into Statistics, files into
 * the Gallery, NPU faults into the fault log), then has the NAS delete its copy.
 */
object NightShift {
    fun connectors(ctx: Context) = AutoStore(ctx).connectors()

    /** The NAS agent is set up (whether it answers is seen when a job is sent). */
    fun ready(ctx: Context) = connectors(ctx).nasReady

    /** Things can wait for the night: the night shift is on and the NAS agent set up. */
    fun tonight(ctx: Context, s: AppSettings) = s.nightShift && ready(ctx)

    /** Sends a job; blocking. */
    fun submit(ctx: Context, kind: String, whenToRun: String, title: String, spec: JSONObject): NasAgent.NasJob =
        NasAgent.addJob(connectors(ctx), kind, whenToRun, title, spec).also {
            ApiLog.add("NAS agent: \"$title\" sent (${if (whenToRun == "night") "for tonight" else "to run now"})", "Automate")
        }

    /** The models of a test, as the NAS needs them. */
    private fun m(x: ModelInfo) = JSONObject().put("id", x.id).put("name", x.name)

    fun speedSpec(pairs: List<Pair<ModelInfo, ModelInfo>>, runs: Int): JSONObject = JSONObject()
        .put("runs", runs.coerceIn(1, 5))
        .put("texts", JSONObject()
            .put("story", SpeedTexts.STORY).put("job_system", CONDENSE_SYSTEM).put("job_text", SpeedTexts.JOB_TEXT)
            .put("prefix_text", SpeedTexts.PREFIX_TEXT).put("q1", SpeedTexts.PREFIX_Q1).put("q2", SpeedTexts.PREFIX_Q2))
        .put("pairs", JSONArray(pairs.map { (big, small) -> JSONObject().put("big", m(big)).put("small", m(small)) }))

    fun qualitySpec(models: List<ModelInfo>, judge: ModelInfo): JSONObject {
        val seed = System.currentTimeMillis()
        val names = models.map { it.name }
        return JSONObject()
            .put("models", JSONArray(models.map { m(it) }))
            .put("judge", m(judge))
            .put("tasks", QualityMath.tasksJson())
            .put("answer_system", QualityMath.ANSWER_SYSTEM)
            .put("grade", JSONObject().put("intro", QualityMath.GRADE_INTRO).put("outro", QualityMath.GRADE_OUTRO))
            .put("order", JSONObject().apply { for (t in QualityMath.TASKS) put(t.id, JSONArray(QualityMath.order(names, t.id, seed))) })
    }

    /**
     * Brings a finished job into the app: speed raws and quality answers through the same numbers as a phone test,
     * made files into the Gallery, an NPU fault into the fault log. Returns the text to show. Blocking.
     */
    fun import(ctx: Context, st: StudioViewModel, j: NasAgent.NasJob): String = import(ctx, st.tests, { st.addResult(it) }, st.store, j)

    fun import(ctx: Context, tests: Tests, add: (ResultEntry) -> Unit, store: ResultStore, j: NasAgent.NasJob): String {
        val c = connectors(ctx)
        val res = NasAgent.jobResult(c, j.id)
        res.optJSONObject("fault")?.let { f ->
            val models = f.optJSONArray("models")?.let { a -> (0 until a.length()).map { a.optString(it).substringAfterLast('/') } } ?: emptyList()
            FaultLog.record(ctx, f.optLong("at"), models)
        }
        if (j.status != "done") return "${j.title}: ${j.summary.ifBlank { j.status }}"
        return when (j.kind) {
            "speed" -> {
                val raws = res.optJSONArray("raws") ?: JSONArray()
                val texts = (0 until raws.length()).mapNotNull { i ->
                    raws.optJSONObject(i)?.let { o ->
                        val raw = SpeedMath.raw(o)
                        if (raw.runs.isEmpty()) null else "${raw.bigName} + ${raw.smallName}: " + tests.keepSpeed(raw, "nas").replace("\n", " ")
                    }
                }
                val lines = res.optJSONArray("lines")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { "failed" in it } } ?: emptyList()
                "Speed test on the NAS: ${texts.size} pair${if (texts.size != 1) "s" else ""}. Results in 📊 Statistics.\n" +
                    (texts + lines).joinToString("\n")
            }
            "quality" -> tests.keepQuality(QualityMath.raw(res.optJSONObject("raw") ?: JSONObject()), "nas")
            "chat" -> {
                val e = ResultEntry(store.newId(), "answer", "Answer: ${j.title.take(60)}", emptyList(), res.optString("text"), "", System.currentTimeMillis())
                add(e)
                "${j.title}: in the Gallery"
            }
            else -> {
                val id = store.newId()
                val dir = java.io.File(ctx.cacheDir, "nas-job").apply { mkdirs() }
                val files = j.files.map { name ->
                    val tmp = java.io.File(dir, name)
                    NasAgent.jobFile(c, j.id, name, tmp)
                    store.saveFile(id, name, tmp.readBytes()).also { tmp.delete() }
                }
                val kind = mapOf("image" to "image", "video" to "video", "music" to "song", "speech" to "speech").getValue(j.kind)
                add(ResultEntry(id, kind, j.title.take(80), files, j.title, "", System.currentTimeMillis()))
                "${j.title}: in the Gallery"
            }
        }
    }

    /**
     * Fetches every finished job not yet brought in (and deletes it on the NAS). Quietly does nothing without a NAS
     * agent, or with one older than 1.8. Returns the texts of what came in, and the jobs still waiting or running.
     */
    fun sync(ctx: Context, st: StudioViewModel, skip: Set<String> = emptySet()): Pair<List<String>, List<NasAgent.NasJob>> {
        if (!ready(ctx)) return emptyList<String>() to emptyList()
        val c = connectors(ctx)
        val all = try {
            NasAgent.jobs(c, log = false)
        } catch (e: Exception) {
            return emptyList<String>() to emptyList()
        }
        val texts = ArrayList<String>()
        for (j in all) {
            if (j.active || j.id in skip) continue
            try {
                texts += import(ctx, st, j)
                NasAgent.deleteJob(c, j.id)
            } catch (e: Exception) {
                ApiLog.add("Couldn't bring in \"${j.title}\" from the NAS: ${e.message}", "Automate", isProblem = true)
            }
        }
        if (texts.isNotEmpty()) ApiLog.add("From the NAS: " + texts.joinToString(" | ") { it.lineSequence().first() }, "Automate")
        return texts to all.filter { it.active }
    }
}

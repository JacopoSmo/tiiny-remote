package app.tiinyremote

import android.app.Application
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The speed and quality tests of the Test page: run on the phone, or on the NAS agent (which measures the same and
 * sends back the raw values; SpeedMath and QualityMath turn them into results either way). Owned by the
 * StudioViewModel, sharing its guard on the Tiiny's models ([deviceBusy]) so a test never swaps models under a job.
 */
class Tests(
    private val app: Application,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences,
    private val client: () -> DeviceClient,
    private val deviceBusy: AtomicBoolean,
    private val note: (String) -> Unit,
    private val clearChatSpeeds: () -> Unit,
    /** Brings a finished NAS job into the app (NightShift.import); its text. */
    private val importJob: (NasAgent.NasJob) -> String,
) {
    // ---------- on the NAS agent: run now (followed here) or tonight ----------

    /** The NAS job this page follows right now (a test running on the NAS), so the general sync leaves it alone. */
    @Volatile
    var followedJob: String? = null
        private set

    /**
     * Sends a test to the NAS agent. [night]: queued for the night shift. Else it runs now on the NAS, followed here
     * in [flow] like a phone test (and Stop reaches it); the models stay untouched by the phone meanwhile. When the
     * NAS can't take it (unreachable, older than 2.0), [fallback] runs it on the phone.
     */
    private fun onNas(flow: MutableStateFlow<String?>, kind: String, title: String, spec: JSONObject, night: Boolean, fallback: () -> Unit) {
        if (night) {
            scope.launch {
                flow.value = try {
                    withContext(Dispatchers.IO) { NightShift.submit(app, kind, "night", title, spec) }
                    "🌙 Added to tonight's queue on the NAS: $title. The results come into the app in the morning."
                } catch (e: Exception) {
                    "Couldn't send it to the NAS: ${e.message}"
                }
            }
            return
        }
        if (flow.value?.startsWith("Testing") == true) return
        if (!deviceBusy.compareAndSet(false, true)) {
            note("The Tiiny is busy with a job: run the test when it's done.")
            return
        }
        NpuGate.testing = true
        flow.value = "Testing on the NAS…"
        Background.set(app, kind, "$title on the NAS")
        scope.launch {
            var sent = false
            try {
                val job = withContext(Dispatchers.IO) { NightShift.submit(app, kind, "now", title, spec) }
                sent = true
                followedJob = job.id
                var misses = 0
                while (true) {
                    kotlinx.coroutines.delay(5_000)
                    val j = try {
                        withContext(Dispatchers.IO) { NasAgent.jobs(NightShift.connectors(app), log = false) }.firstOrNull { it.id == job.id }
                            ?: throw MediaException("The NAS no longer has this test.")
                    } catch (e: MediaException) {
                        if (++misses < 12) continue
                        // A minute without the NAS: the test goes on there; its result comes in when the app reaches it.
                        throw MediaException("The NAS doesn't answer: the test goes on there, and its result comes in when the app reaches it again.")
                    }
                    misses = 0
                    if (j.active) {
                        flow.value = "Testing on the NAS: ${j.progress.ifBlank { "waiting for its turn" }}…"
                        continue
                    }
                    val out = withContext(Dispatchers.IO) {
                        importJob(j).also { NasAgent.deleteJob(NightShift.connectors(app), j.id) }
                    }
                    flow.value = out
                    ApiLog.add("$title on the NAS: " + out.replace("\n", " | "), "Chat")
                    Notifier.done(app, "$title finished", out)
                    break
                }
            } catch (e: Exception) {
                flow.value = if (sent) e.message ?: "The NAS test stopped." else "The NAS couldn't take the test (${e.message}): it runs on the phone instead."
                if (!sent) ApiLog.add("$title: the NAS couldn't take it (${e.message}); running on the phone", "Chat", isProblem = true)
            } finally {
                followedJob = null
                speedStop = false
                _speedStopping.value = false
                qualityStop = false
                NpuGate.testing = false
                deviceBusy.set(false)
                Background.set(app, kind, null)
            }
            if (!sent) fallback()
        }
    }

    /** Stop for a test on the NAS: after its current step, or at once. */
    private fun stopOnNas(now: Boolean) {
        val id = followedJob ?: return
        scope.launch(Dispatchers.IO) {
            try {
                NasAgent.stopJob(NightShift.connectors(app), id, now)
            } catch (e: Exception) {
                ApiLog.add("Couldn't stop the test on the NAS: ${e.message}", "Chat", isProblem = true)
            }
        }
    }

    /**
     * The speed test of [pairs] (main model, helper) on the NAS: now ([night] false) or tonight. Without the NAS,
     * the phone runs it.
     */
    fun speedOnNas(pairs: List<Pair<ModelInfo, ModelInfo>>, runs: Int, night: Boolean) {
        if (pairs.isEmpty()) return
        val title = if (pairs.size == 1) "Speed test ${pairs[0].first.name} + ${pairs[0].second.name}" else "Speed test of ${pairs.size} pairs"
        onNas(_speed, "speed", title, NightShift.speedSpec(pairs, runs), night) {
            if (pairs.size == 1) speedTest(pairs[0].first, pairs[0].second, runs)
            else speedTestAll(pairs.map { it.first }.distinct(), pairs.map { it.second }.distinct(), runs)
        }
    }

    fun qualityOnNas(models: List<ModelInfo>, judge: ModelInfo, night: Boolean) {
        if (models.isEmpty()) return
        onNas(_quality, "quality", "Quality test of ${models.size} model${if (models.size != 1) "s" else ""}", NightShift.qualitySpec(models, judge), night) {
            qualityTest(models, judge)
        }
    }

    // ---------- speed test: one model at a time, or two at once ----------

    private val _speed = MutableStateFlow<String?>(null)

    /** Set by Stop: a test of several pairs ends at the next pause. */
    @Volatile
    private var speedStop = false
    /** The speed test's progress, then its result. */
    val speed: StateFlow<String?> = _speed.asStateFlow()

    private val _speedHistory = MutableStateFlow(loadSpeedHistory())
    /** Every speed test, newest first (the last 100). */
    val speedHistory: StateFlow<List<SpeedRecord>> = _speedHistory.asStateFlow()

    /**
     * Models the speed tests found about as quick at the helper job (within 15%) as the quickest measured
     * recommended helper, and no heavier on the NPU than the heaviest recommended one (+5 points): 👍 too.
     */
    fun measuredHelpers(models: List<ModelInfo>, recIds: Set<String>): Set<String> {
        val jobs = (_speedHistory.value.map { it.small to it.smallJob } + _speedHistory.value.map { it.big to it.bigJob })
            .filter { !it.second.isNaN() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, l) -> l.average() }
        return SpeedMath.measuredHelpers(jobs, models.map { Triple(it.id, it.name, it.npuUsage) }, recIds)
    }

    private fun loadSpeedHistory(): List<SpeedRecord> = try {
        val a = JSONArray(prefs.getString("speed_history", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> SpeedRecord.from(o) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun addSpeedHistory(r: SpeedRecord) {
        val list = (listOf(r) + _speedHistory.value).take(100)
        prefs.edit().putString("speed_history", JSONArray(list.map { it.json() }).toString()).apply()
        _speedHistory.value = list
    }

    fun clearSpeedHistory() {
        prefs.edit().remove("speed_history").apply()
        _speedHistory.value = emptyList()
        clearChatSpeeds()
    }

    /**
     * Measures how fast the chat model and the helper write alone, then at the same time, and the helper twice at
     * once (how pages are condensed), [runs] times (1–4), and averages: one run can be off by several percent. Both
     * must be loaded; nothing is swapped. Tells whether parallel work pays off.
     */
    fun speedTest(big: ModelInfo, small: ModelInfo, runs: Int = 1) {
        if (_speed.value?.startsWith("Testing") == true) return
        if (!deviceBusy.compareAndSet(false, true)) {
            note("The Tiiny is busy with a job: run the test when it's done.")
            return
        }
        val n = runs.coerceIn(1, 5)
        speedStop = false
        NpuGate.testing = true
        _speed.value = "Testing…"
        scope.launch {
            try {
                val out = withContext(Dispatchers.IO) { measurePair(client().also { speedClient = it }, big, small, n, "") }
                _speed.value = out
                ApiLog.add("Speed test: " + out.replace("\n", " "), "Chat")
                Notifier.done(app, "Speed test finished", out)
            } catch (e: Exception) {
                _speed.value = if (speedStop) "Speed test stopped." else "Speed test failed: ${e.message}"
                ApiLog.add(if (speedStop) "Speed test stopped" else "Speed test failed: ${e.message}", "Chat")
            } finally {
                speedStop = false
                _speedStopping.value = false
                speedClient = null
                NpuGate.testing = false
                deviceBusy.set(false)
            }
        }
    }

    /** The models chosen for the speed test of several pairs (Test → Speed test): ids, in order. */
    fun speedSet(key: String): List<String> = prefs.getString("set_speed.$key", null).orEmpty().split('\n').filter { it.isNotBlank() }
    fun setSpeedSet(key: String, ids: List<String>) = prefs.edit().putString("set_speed.$key", ids.joinToString("\n")).apply()

    /** The client of the running speed test, for Cancel now. */
    @Volatile
    private var speedClient: DeviceClient? = null
    private val _speedStopping = MutableStateFlow(false)
    /** Asked to stop after the current step: the button says so. */
    val speedStopping: StateFlow<Boolean> = _speedStopping.asStateFlow()

    /**
     * [now] false: the test stops after the current step (that step's answer isn't saved). True: the waiting requests
     * are broken off at once. Models loaded before a test of several pairs are still loaded back.
     */
    fun stopSpeedTest(now: Boolean) {
        speedStop = true
        _speedStopping.value = true
        stopOnNas(now)
        if (now) {
            ApiLog.add("Speed test: stopped at once", "Chat")
            speedClient?.abortAll()
        }
    }

    /**
     * The speed test for every chosen main model with every chosen helper ([runs] times each), one pair after the
     * other, loading each pair by itself (other models are unloaded for it) so you needn't change models. Each pair is
     * saved to 📊 Statistics as it finishes. Afterwards the models loaded before are loaded again.
     */
    fun speedTestAll(mains: List<ModelInfo>, helpers: List<ModelInfo>, runs: Int) {
        if (_speed.value?.startsWith("Testing") == true) return
        val pairs = mains.flatMap { m -> helpers.filter { it.id != m.id }.map { m to it } }
        if (pairs.isEmpty()) return
        if (!deviceBusy.compareAndSet(false, true)) {
            note("The Tiiny is busy with a job: run the test when it's done.")
            return
        }
        val n = runs.coerceIn(1, 5)
        speedStop = false
        NpuGate.testing = true
        _speed.value = "Testing…"
        val app = app
        Background.set(app, "speed", "Speed test of ${pairs.size} pairs")
        scope.launch {
            val lines = ArrayList<String>()
            var done = 0
            try {
                withContext(Dispatchers.IO) {
                    val c = client().also { speedClient = it }
                    val before = Tools.runningIds(c)
                    val p = Progress({ msg, _ -> _speed.value = "Testing: $msg…" }, { speedStop })
                    // Asks every 10 s whether the Tiiny answers again, for up to 3 minutes.
                    fun awaitBack(): Boolean {
                        val until = System.currentTimeMillis() + 180_000
                        while (System.currentTimeMillis() < until && !speedStop) {
                            p.update("the Tiiny doesn't answer, waiting for it", null)
                            if (runCatching { Tools.runningIds(c, log = false) }.isSuccess) return true
                            Thread.sleep(10_000)
                        }
                        return false
                    }
                    fun launch(m: ModelInfo) {
                        if (m.id in Tools.runningIds(c, log = false)) return
                        p.update("loading ${m.name}", null)
                        Tools.launch(c, m.id, p)
                    }
                    try {
                        for ((i, pair) in pairs.withIndex()) {
                            if (speedStop) break
                            val (big, small) = pair
                            val tag = " pair ${i + 1} of ${pairs.size}, ${big.name} + ${small.name}"
                            val share = (big.npuUsage ?: 0.0) + (small.npuUsage ?: 0.0)
                            if (share > 100) {
                                lines += "${big.name} + ${small.name}: skipped, together ${Math.round(share)}% of the NPU"
                                continue
                            }
                            // A lost connection (the Tiiny restarting, Wi-Fi): wait for it, then try the pair once more.
                            var gone = false
                            for (attempt in 1..2) {
                                try {
                                    // Only this pair on the NPU, so every pair is measured alike.
                                    for (id in Tools.runningIds(c) - setOf(big.id, small.id)) {
                                        p.update("unloading ${id.substringAfterLast('/')}", null)
                                        Tools.stop(c, id)
                                    }
                                    launch(big)
                                    launch(small)
                                    val out = measurePair(c, big, small, n, ",$tag") { awaitBack() }
                                    lines += "${big.name} + ${small.name}: " + out.replace("\n", " ")
                                    done++
                                    break
                                } catch (e: Exception) {
                                    if (speedStop) break
                                    ApiLog.add("Speed test, ${big.name} + ${small.name} failed: ${e.message}", "Chat", isProblem = true)
                                    val lost = e.message?.startsWith("Lost the connection") == true
                                    if (lost && !awaitBack()) {
                                        lines += "${big.name} + ${small.name}: failed, the Tiiny didn't answer for 3 minutes; the test ended here"
                                        gone = true
                                        break
                                    }
                                    if (lost && attempt == 1) {
                                        ApiLog.add("Speed test: the Tiiny is back, trying ${big.name} + ${small.name} once more", "Chat")
                                        continue
                                    }
                                    lines += "${big.name} + ${small.name}: failed (${e.message})"
                                    break
                                }
                            }
                            if (gone) break
                        }
                    } finally {
                        // What was loaded before comes back (best effort; a failure is only logged).
                        try {
                            if (runCatching { Tools.runningIds(c, log = false) }.isFailure) awaitBack()
                            val now = Tools.runningIds(c, log = false)
                            for (id in now - before) { _speed.value = "Testing: unloading ${id.substringAfterLast('/')}…"; Tools.stop(c, id) }
                            val quiet = Progress({ _, _ -> }, { false })
                            for (id in before - now) {
                                _speed.value = "Testing: loading ${id.substringAfterLast('/')} back…"
                                Tools.launch(c, id, quiet, wait = false)
                            }
                            for (id in before - now) Tools.waitLoaded(c, id, quiet)
                        } catch (e: Exception) {
                            ApiLog.add("Speed test: couldn't load the earlier models back: ${e.message}", "Chat", isProblem = true)
                        }
                    }
                }
                val head = (if (speedStop) "Stopped after $done of ${pairs.size} pairs" else "$done of ${pairs.size} pairs tested") +
                    (if (n > 1) ", $n runs each" else "") + ". Results in 📊 Statistics."
                _speed.value = head + "\n" + lines.joinToString("\n")
                ApiLog.add("Speed test of several pairs: $head " + lines.joinToString(" | "), "Chat")
                Notifier.done(app, "Speed test finished", head)
            } catch (e: Exception) {
                _speed.value = "Speed test failed: ${e.message}"
                ApiLog.add("Speed test of several pairs failed: ${e.message}", "Chat", isProblem = true)
            } finally {
                speedStop = false
                _speedStopping.value = false
                speedClient = null
                NpuGate.testing = false
                deviceBusy.set(false)
                Background.set(app, "speed", null)
            }
        }
    }

    /**
     * One speed test of a loaded pair on the phone ([n] runs, see SpeedMeasure), analysed by SpeedMath and kept for
     * 📊 Statistics. [tag] goes into the progress (the pair, in a test of several). Blocks: call on IO.
     */
    private suspend fun measurePair(
        c: DeviceClient, big: ModelInfo, small: ModelInfo, n: Int, tag: String, onLost: (() -> Boolean)? = null,
    ): String {
        val raw = SpeedMeasure(c, { speedStop }, { _speed.value = it }).pair(big, small, n, tag, onLost)
        return keepSpeed(raw, "phone")
    }

    /** A pair's raw measurements (from the phone or the NAS) analysed, remembered and kept for History; the text. */
    fun keepSpeed(raw: SpeedRaw, by: String): String {
        val res = SpeedMath.analyze(raw, System.currentTimeMillis(), by)
        // Remembered per model (the average): the chat uses them to decide whether the helper condenses pages.
        prefs.edit().putFloat("speed_${raw.bigId}", res.bigRate.toFloat()).putFloat("speed_${raw.smallId}", res.smallRate.toFloat()).apply()
        addSpeedHistory(res.record)
        return res.text
    }


    // ---------- quality test: the same tasks for every model, graded blind by the strongest one ----------

    private val _quality = MutableStateFlow<String?>(null)
    /** The quality test's progress, then its result. */
    val quality: StateFlow<String?> = _quality.asStateFlow()

    @Volatile
    private var qualityStop = false
    @Volatile
    private var qualityClient: DeviceClient? = null

    private val _qualityHistory = MutableStateFlow(loadQuality())
    /** Every model's quality grades, newest first (the last 200). */
    val qualityHistory: StateFlow<List<QualityRecord>> = _qualityHistory.asStateFlow()

    private fun loadQuality(): List<QualityRecord> = try {
        val a = JSONArray(prefs.getString("quality_history", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> QualityRecord.from(o) } }
    } catch (e: Exception) {
        emptyList()
    }

    fun clearQualityHistory() {
        prefs.edit().remove("quality_history").apply()
        _qualityHistory.value = emptyList()
    }

    /** The models chosen for the quality test (ids, in order). */
    fun qualitySet(): List<String> = prefs.getString("set_quality.models", null).orEmpty().split('\n').filter { it.isNotBlank() }
    fun setQualitySet(ids: List<String>) = prefs.edit().putString("set_quality.models", ids.joinToString("\n")).apply()

    /** [now] false: stops after the current answer; true: breaks off the waiting request at once. */
    fun stopQualityTest(now: Boolean) {
        qualityStop = true
        stopOnNas(now)
        if (now) qualityClient?.abortAll()
    }

    /** A quality test's raw results (from the phone or the NAS) graded, kept for History; the text. */
    fun keepQuality(raw: QualityRaw, by: String): String {
        val recs = QualityMath.records(raw, System.currentTimeMillis(), by)
        val list = (recs + _qualityHistory.value).take(200)
        prefs.edit().putString("quality_history", JSONArray(list.map { it.json() }).toString()).apply()
        _qualityHistory.value = list
        return QualityMath.summary(raw)
    }

    /** Only [keep] loaded on the Tiiny: the others unloaded, the missing ones loaded (and waited for). */
    private fun onlyLoaded(c: DeviceClient, keep: List<ModelInfo>, show: (String) -> Unit, p: Progress) {
        for (id in Tools.runningIds(c) - keep.map { it.id }.toSet()) {
            show("unloading ${id.substringAfterLast('/')}")
            Tools.stop(c, id)
        }
        for (m in keep) if (m.id !in Tools.runningIds(c, log = false)) {
            show("loading ${m.name}")
            Tools.launch(c, m.id, p)
        }
    }

    /** One answer to a task, as the model answers in a chat (its own thinking), and the seconds it took. */
    private fun answer(c: DeviceClient, model: String, task: QualityTask): QualityAnswer {
        val thinks = Tools.thinkingMode(c, model) != "none"
        val body = JSONObject().put("model", model).put("stream", false).put("temperature", 0.3)
            .put("max_tokens", task.maxTokens + if (thinks) 4096 else 0)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", QualityMath.ANSWER_SYSTEM))
                .put(JSONObject().put("role", "user").put("content", task.prompt)))
        var t0 = System.nanoTime()
        var r = c.request("POST", "/v1/chat/completions", body, 900)
        for (retry in 1..2) {
            if (r.ok || r.code < 500 || qualityStop) break
            Thread.sleep(10_000L * retry)
            t0 = System.nanoTime()
            r = c.request("POST", "/v1/chat/completions", body, 900)
        }
        val secs = (System.nanoTime() - t0) / 1e9
        if (!r.ok) throw MediaException(r.error())
        val text = r.json().optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            .replace(Regex("(?s)<think>.*?</think>"), "").trim()
        return QualityAnswer(task.id, "", text, secs)
    }

    /** The grader's reply for one task (it may think first: that's where careful grading happens). */
    private fun grade(c: DeviceClient, judge: String, prompt: String): String {
        val body = JSONObject().put("model", judge).put("stream", false).put("temperature", 0.0).put("max_tokens", 1500 + 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        var r = c.request("POST", "/v1/chat/completions", body, 1200)
        for (retry in 1..2) {
            if (r.ok || r.code < 500 || qualityStop) break
            Thread.sleep(10_000L * retry)
            r = c.request("POST", "/v1/chat/completions", body, 1200)
        }
        if (!r.ok) throw MediaException(r.error())
        return r.json().optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
    }

    /**
     * The quality test on the phone: each of [models] alone on the Tiiny answers every task, then [judge] alone
     * grades each task's answers blind. The models loaded before are loaded back at the end.
     */
    fun qualityTest(models: List<ModelInfo>, judge: ModelInfo) {
        if (_quality.value?.startsWith("Testing") == true || models.isEmpty()) return
        if (!deviceBusy.compareAndSet(false, true)) {
            note("The Tiiny is busy with a job: run the test when it's done.")
            return
        }
        qualityStop = false
        NpuGate.testing = true
        _quality.value = "Testing…"
        Background.set(app, "quality", "Quality test of ${models.size} models")
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    val c = client().also { qualityClient = it }
                    val before = Tools.runningIds(c)
                    fun show(msg: String) { _quality.value = "Testing: $msg…" }
                    val p = Progress({ msg, _ -> show(msg) }, { qualityStop })
                    val answers = ArrayList<QualityAnswer>()
                    try {
                        for ((i, m) in models.withIndex()) {
                            if (qualityStop) break
                            onlyLoaded(c, listOf(m), ::show, p)
                            for ((k, task) in QualityMath.TASKS.withIndex()) {
                                if (qualityStop) break
                                show("${m.name} (${i + 1} of ${models.size}), task ${k + 1} of ${QualityMath.TASKS.size}: ${task.title}")
                                answers += try {
                                    answer(c, m.id, task).copy(model = m.name)
                                } catch (e: Exception) {
                                    if (qualityStop) throw e
                                    ApiLog.add("Quality test: ${m.name}, ${task.title}: no answer (${e.message})", "Chat", isProblem = true)
                                    QualityAnswer(task.id, m.name, "", 0.0)
                                }
                            }
                        }
                        if (qualityStop) throw MediaException("Stopped")
                        onlyLoaded(c, listOf(judge), ::show, p)
                        val seed = System.currentTimeMillis()
                        val names = models.map { it.name }
                        val order = HashMap<String, List<String>>()
                        val verdicts = HashMap<String, String>()
                        for ((k, task) in QualityMath.TASKS.withIndex()) {
                            if (qualityStop) break
                            show("${judge.name} grades task ${k + 1} of ${QualityMath.TASKS.size}: ${task.title}")
                            val shown = QualityMath.order(names, task.id, seed)
                            val labels = QualityMath.labels(shown.size)
                            val prompt = QualityMath.gradePrompt(task, shown.mapIndexed { j, m ->
                                labels[j] to (answers.firstOrNull { it.task == task.id && it.model == m }?.text ?: "")
                            })
                            order[task.id] = shown
                            verdicts[task.id] = try {
                                grade(c, judge.id, prompt)
                            } catch (e: Exception) {
                                if (qualityStop) throw e
                                ApiLog.add("Quality test: grading ${task.title} failed (${e.message})", "Chat", isProblem = true)
                                ""
                            }
                        }
                        if (qualityStop) throw MediaException("Stopped")
                        QualityRaw(judge.name, answers, order, verdicts)
                    } finally {
                        // What was loaded before comes back (best effort; a failure is only logged).
                        try {
                            val now = Tools.runningIds(c, log = false)
                            for (id in now - before) Tools.stop(c, id)
                            val quiet = Progress({ _, _ -> }, { false })
                            for (id in before - now) Tools.launch(c, id, quiet, wait = false)
                        } catch (e: Exception) {
                            ApiLog.add("Quality test: couldn't load the earlier models back: ${e.message}", "Chat", isProblem = true)
                        }
                    }
                }
                val out = keepQuality(raw, "phone")
                _quality.value = out
                ApiLog.add("Quality test: " + out.replace("\n", " | "), "Chat")
                Notifier.done(app, "Quality test finished", out)
            } catch (e: Exception) {
                _quality.value = if (qualityStop) "Quality test stopped: nothing was graded." else "Quality test failed: ${e.message}"
                ApiLog.add(if (qualityStop) "Quality test stopped" else "Quality test failed: ${e.message}", "Chat", isProblem = !qualityStop)
            } finally {
                qualityStop = false
                qualityClient = null
                NpuGate.testing = false
                deviceBusy.set(false)
                Background.set(app, "quality", null)
            }
        }
    }
}

package app.tiinyremote

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/*
 * The speed test's numbers, apart from how they are measured: the phone and the NAS agent both measure the same
 * raw values (SpeedRaw) and hand them here, so a result means the same wherever the test ran. No Android in this
 * file: the automated tests check it on every build.
 */

/** One request as measured: tokens written and seconds taken. */
data class Took(val tokens: Int, val secs: Double) {
    val rate: Double get() = tokens / secs
}

/** One run of the speed test: each model alone, both at once, the helper twice at once, and the helper job (seconds). */
data class SpeedRun(
    val bigAlone: Took, val smallAlone: Took,
    val bigBoth: Took, val smallBoth: Took,
    val help1: Took, val help2: Took,
    val smallJob: Double, val bigJob: Double,
)

/**
 * The repeated-start check of one model: seconds until the first word for a long text read fresh ([cold]), the very
 * same request again ([same]), and the same text with a new question at the end ([newEnd]). A server that keeps what
 * it read starts much sooner the second and third time.
 */
data class PrefixCheck(val model: String, val chars: Int, val cold: Double, val same: Double, val newEnd: Double)

/** Everything measured for one pair: its finished runs, how many runs were left out after errors, the start checks. */
data class SpeedRaw(
    val bigId: String, val bigName: String, val smallId: String, val smallName: String,
    val runs: List<SpeedRun>, val left: Int = 0, val prefix: List<PrefixCheck> = emptyList(),
)

/** One speed test, for History: the pair, its speeds alone, and how much sooner both (or the helper twice) finished at once. */
data class SpeedRecord(
    val at: Long, val big: String, val small: String, val runs: Int,
    val bigRate: Double, val smallRate: Double, val bothGain: Double, val helperGain: Double, val outliers: Int,
    /** Seconds for the helper job (condensing a chat): the helper, and the main model; NaN in tests before 4.6.3. */
    val smallJob: Double = Double.NaN, val bigJob: Double = Double.NaN,
    /** The main model's repeated start: first word fresh, and the same text again (seconds); NaN before 4.8.0. */
    val prefixCold: Double = Double.NaN, val prefixSame: Double = Double.NaN,
    /** Where it was measured: "phone" or "nas". */
    val by: String = "phone",
) {
    fun json(): JSONObject = JSONObject().put("at", at).put("big", big).put("small", small).put("runs", runs)
        .put("big_rate", bigRate).put("small_rate", smallRate).put("both", bothGain).put("helper", helperGain).put("outliers", outliers)
        .put("by", by)
        .apply {
            if (!smallJob.isNaN()) put("small_job", smallJob)
            if (!bigJob.isNaN()) put("big_job", bigJob)
            if (!prefixCold.isNaN()) put("prefix_cold", prefixCold)
            if (!prefixSame.isNaN()) put("prefix_same", prefixSame)
        }

    companion object {
        fun from(o: JSONObject) = SpeedRecord(
            o.optLong("at"), o.optString("big"), o.optString("small"), o.optInt("runs", 1),
            o.optDouble("big_rate"), o.optDouble("small_rate"), o.optDouble("both"), o.optDouble("helper"), o.optInt("outliers"),
            o.optDouble("small_job"), o.optDouble("big_job"), o.optDouble("prefix_cold"), o.optDouble("prefix_same"),
            o.optString("by", "phone").ifBlank { "phone" },
        )
    }
}

/** A pair's analysed result: the record for History, the text shown, and each model's average speed alone. */
data class SpeedResult(val record: SpeedRecord, val text: String, val bigRate: Double, val smallRate: Double)

object SpeedMath {
    /** The helper condenses web pages only when it writes at least this much faster than the chat model. */
    const val PAGE_RULE = 1.25

    fun median(v: List<Double>): Double = v.sorted().let {
        if (it.isEmpty()) Double.NaN else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2
    }

    /**
     * From 3 values on, clear outliers (a hiccup on the Tiiny, say) are left out, one at a time: a value more than
     * 3 robust standard deviations (MAD) from the median of the rest, and more than [floor] away. At least 3 values
     * stay: 1 can go of 3 or 4, 2 of 5. Returns (kept, left out).
     */
    fun clean(l: List<Double>, floor: Double): Pair<List<Double>, List<Double>> {
        var kept = l
        val out = ArrayList<Double>()
        val most = if (l.size < 3) 0 else maxOf(1, l.size - 3)
        while (out.size < most) {
            val m = median(kept)
            val mad = median(kept.map { kotlin.math.abs(it - m) }) * 1.4826
            val worst = kept.indices.maxByOrNull { kotlin.math.abs(kept[it] - m) } ?: break
            if (kotlin.math.abs(kept[worst] - m) <= maxOf(3 * mad, floor)) break
            out += kept[worst]
            kept = kept.filterIndexed { j, _ -> j != worst }
        }
        return kept to out
    }

    /** How much sooner (in %) two answers finished at once ([par] seconds) than one after the other ([seq] seconds). */
    fun gain(seq: Double, par: Double) = (1 - par / seq) * 100

    fun f(v: Double): String = String.format(Locale.ROOT, "%.1f", v)
    fun pct(v: Double) = "${Math.round(v)}%"

    /** The text for a gain: worth it, a little, none, or slower. */
    fun verdictWord(p: Double): String = when {
        p >= 10 -> "${pct(p)} sooner: worth it"
        p >= 3 -> "${pct(p)} sooner: a little faster"
        p > -3 -> "no gain, the Tiiny takes them in turn"
        p > -5 -> "about the same"
        else -> "${pct(-p)} slower: better one after the other"
    }

    /**
     * Whether the Tiiny keeps what it read: the same start answered again at least 40% sooner than fresh (and at
     * least half a second), or null when it wasn't checked.
     */
    fun reuses(p: PrefixCheck): Boolean = p.cold - p.same >= maxOf(0.5, p.cold * 0.4)

    fun prefixText(p: PrefixCheck): String {
        val k = "${Math.round(p.chars / 1000.0)}k characters"
        return "Repeated start (${p.model}, $k): first word ${f(p.cold)} s fresh, ${f(p.same)} s the same again, " +
            "${f(p.newEnd)} s with a new question: " +
            if (reuses(p)) "the Tiiny reuses what it has read, so long chats start sooner." else "the Tiiny reads everything again each time."
    }

    /** One pair's result from its raw measurements: what History keeps and the text shown after the test. */
    fun analyze(raw: SpeedRaw, at: Long, by: String = "phone"): SpeedResult {
        require(raw.runs.isNotEmpty()) { "no run finished" }
        val bigRates = raw.runs.map { it.bigAlone.rate }
        val smallRates = raw.runs.map { it.smallAlone.rate }
        val bothGains = raw.runs.map { r ->
            gain(r.bigAlone.tokens / r.bigAlone.rate + r.smallAlone.tokens / r.smallAlone.rate,
                maxOf(r.bigAlone.tokens / r.bigBoth.rate, r.smallAlone.tokens / r.smallBoth.rate))
        }
        val helperGains = raw.runs.map { r ->
            gain(2 * r.smallAlone.tokens / r.smallAlone.rate, maxOf(r.smallAlone.tokens / r.help1.rate, r.smallAlone.tokens / r.help2.rate))
        }
        val (bigOk, bigOut) = clean(bigRates, bigRates.average() * 0.1)
        val (smallOk, smallOut) = clean(smallRates, smallRates.average() * 0.1)
        val (bothOk, bothOut) = clean(bothGains, 5.0)
        val (helperOk, helperOut) = clean(helperGains, 5.0)
        val (smallJobOk, smallJobOut) = clean(raw.runs.map { it.smallJob }, 1.0)
        val (bigJobOk, bigJobOut) = clean(raw.runs.map { it.bigJob }, 1.0)
        val bigRate = bigOk.average()
        val smallRate = smallOk.average()
        val smallJob = smallJobOk.average()
        val bigJob = bigJobOk.average()
        fun outText(out: List<Double>, show: (Double) -> String) =
            if (out.isEmpty()) "" else " [left out as outlier${if (out.size > 1) "s" else ""}: ${out.joinToString(", ") { show(it) }}]"
        fun spread(l: List<Double>, out: List<Double>) =
            (if (l.size > 1) " (${f(l.min())}–${f(l.max())})" else "") + outText(out) { f(it) }
        fun verdict(l: List<Double>, out: List<Double>) =
            verdictWord(l.average()) + (if (l.size > 1) ", runs ${l.joinToString(" / ") { pct(it) }}" else "") + outText(out) { pct(it) }
        val bigPrefix = raw.prefix.firstOrNull { it.model == raw.bigName }
        val record = SpeedRecord(
            at, raw.bigName, raw.smallName, raw.runs.size, bigRate, smallRate, bothOk.average(), helperOk.average(),
            bigOut.size + smallOut.size + bothOut.size + helperOut.size + smallJobOut.size + bigJobOut.size, smallJob, bigJob,
            bigPrefix?.cold ?: Double.NaN, bigPrefix?.same ?: Double.NaN, by,
        )
        val faster = (smallRate / bigRate - 1) * 100
        val pages = if (smallRate >= bigRate * PAGE_RULE)
            "${raw.smallName} is ${pct(faster)} faster than ${raw.bigName}: it condenses web pages."
        else
            "${raw.smallName} isn't at least 25% faster than ${raw.bigName} (${if (faster >= 0) "+" else ""}${if (faster in 24.5..25.0) "24.9%" else pct(faster)}): " +
                "${raw.bigName} reads web pages itself."
        val done = raw.runs.size
        val text = (if (done > 1) "Average of $done runs. " else "") +
            (if (raw.left > 0) "${raw.left} run${if (raw.left > 1) "s" else ""} left out after errors. " else "") +
            "Alone: ${raw.bigName} ${f(bigRate)} tok/s${spread(bigOk, bigOut)}, ${raw.smallName} ${f(smallRate)} tok/s${spread(smallOk, smallOut)}.\n" +
            "Both models at once: ${verdict(bothOk, bothOut)}.\n" +
            "${raw.smallName} twice at once: ${verdict(helperOk, helperOut)}.\n" +
            "A helper job (condensing a chat, thinking as the app asks): ${raw.smallName} ${f(smallJob)} s${spread(smallJobOk, smallJobOut)}, " +
            "${raw.bigName} ${f(bigJob)} s${spread(bigJobOk, bigJobOut)}.\n" +
            pages + raw.prefix.joinToString("") { "\n" + prefixText(it) }
        return SpeedResult(record, text, bigRate, smallRate)
    }

    /**
     * Models found about as quick at the helper job (within 15%) as the quickest measured recommended helper, and no
     * heavier on the NPU than the heaviest recommended one (+5 points). [jobs]: model name → average seconds;
     * [models]: (id, name, NPU share); [recIds]: the recommended helpers. Returns the ids that earn 👍 too.
     */
    fun measuredHelpers(jobs: Map<String, Double>, models: List<Triple<String, String, Double?>>, recIds: Set<String>): Set<String> {
        val recs = models.filter { it.first in recIds }
        val best = recs.mapNotNull { jobs[it.second] }.minOrNull() ?: return emptySet()
        val heaviest = recs.mapNotNull { it.third }.maxOrNull() ?: return emptySet()
        return models.filter { (id, name, npu) ->
            id !in recIds && (npu ?: 100.0) <= heaviest + 5 && (jobs[name] ?: Double.MAX_VALUE) <= best * 1.15
        }.map { it.first }.toSet()
    }

    // How the NAS agent hands back what it measured (the same fields the phone measures).
    fun took(o: JSONObject) = Took(o.optInt("tokens"), o.optDouble("secs"))
    fun tookJson(t: Took): JSONObject = JSONObject().put("tokens", t.tokens).put("secs", t.secs)

    fun rawJson(r: SpeedRaw): JSONObject = JSONObject()
        .put("big_id", r.bigId).put("big_name", r.bigName).put("small_id", r.smallId).put("small_name", r.smallName)
        .put("left", r.left)
        .put("runs", JSONArray(r.runs.map { run ->
            JSONObject().put("big_alone", tookJson(run.bigAlone)).put("small_alone", tookJson(run.smallAlone))
                .put("big_both", tookJson(run.bigBoth)).put("small_both", tookJson(run.smallBoth))
                .put("help1", tookJson(run.help1)).put("help2", tookJson(run.help2))
                .put("small_job", run.smallJob).put("big_job", run.bigJob)
        }))
        .put("prefix", JSONArray(r.prefix.map {
            JSONObject().put("model", it.model).put("chars", it.chars).put("cold", it.cold).put("same", it.same).put("new_end", it.newEnd)
        }))

    fun raw(o: JSONObject): SpeedRaw {
        val runs = o.optJSONArray("runs") ?: JSONArray()
        val pre = o.optJSONArray("prefix") ?: JSONArray()
        return SpeedRaw(
            o.optString("big_id"), o.optString("big_name"), o.optString("small_id"), o.optString("small_name"),
            (0 until runs.length()).mapNotNull { i ->
                runs.optJSONObject(i)?.let { x ->
                    SpeedRun(
                        took(x.getJSONObject("big_alone")), took(x.getJSONObject("small_alone")),
                        took(x.getJSONObject("big_both")), took(x.getJSONObject("small_both")),
                        took(x.getJSONObject("help1")), took(x.getJSONObject("help2")),
                        x.optDouble("small_job"), x.optDouble("big_job"),
                    )
                }
            },
            o.optInt("left"),
            (0 until pre.length()).mapNotNull { i ->
                pre.optJSONObject(i)?.let { p -> PrefixCheck(p.optString("model"), p.optInt("chars"), p.optDouble("cold"), p.optDouble("same"), p.optDouble("new_end")) }
            },
        )
    }
}

/** The wait in chats by how much was sent: short, medium and long chats. */
object ChatWaits {
    /** (label, middle wait in seconds, how many) for each band with answers in it; [l]: (characters sent, seconds). */
    fun bands(l: List<Pair<Int, Double>>): List<Triple<String, Double, Int>> {
        val known = l.filter { it.first > 0 }
        return listOf(
            Triple("short chats", 0, 8_000),
            Triple("medium", 8_000, 32_000),
            Triple("long", 32_000, Int.MAX_VALUE),
        ).mapNotNull { (label, from, to) ->
            val w = known.filter { it.first in from until to }.map { it.second }
            if (w.isEmpty()) null else Triple(label, SpeedMath.median(w), w.size)
        }
    }
}

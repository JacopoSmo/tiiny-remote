package app.tiinyremote

import org.json.JSONArray
import org.json.JSONObject

/*
 * The quality test: every chosen model answers the same tasks, then the strongest chat model on the Tiiny grades the
 * answers of each task side by side without knowing which model wrote which (A, B, C… in a shuffled order). The
 * tasks, the grading prompt and the numbers live here, the same for the phone and the NAS agent (which is sent the
 * tasks and returns what it got). No Android in this file: the automated tests check it on every build.
 */

/** One task: what the models are asked, and what the grader checks ([rubric], with the right answer where there is one). */
data class QualityTask(val id: String, val title: String, val prompt: String, val rubric: String, val maxTokens: Int = 1500)

/** One model's answer to one task, and the seconds it took (thinking included). An empty text: no answer (an error). */
data class QualityAnswer(val task: String, val model: String, val text: String, val secs: Double)

/**
 * What a quality test brings back: the answers, and for each task the grader's reply and the order the answers were
 * shown in (labels A, B, C… in that order).
 */
data class QualityRaw(
    val judgeName: String,
    val answers: List<QualityAnswer>,
    val order: Map<String, List<String>>,
    val verdicts: Map<String, String>,
)

/** A model's grades: per task (1–10, NaN if not graded), the average, and the average seconds per answer. */
data class QualityScore(val model: String, val perTask: Map<String, Double>, val average: Double, val secs: Double, val notes: Map<String, String>)

/** One model in one quality test, for History. */
data class QualityRecord(
    val at: Long, val model: String, val judge: String, val average: Double, val secs: Double,
    val perTask: Map<String, Double>, val by: String = "phone",
) {
    fun json(): JSONObject = JSONObject().put("at", at).put("model", model).put("judge", judge).put("average", average)
        .put("secs", secs).put("by", by)
        .put("tasks", JSONObject().apply { perTask.forEach { (k, v) -> if (!v.isNaN()) put(k, v) } })

    companion object {
        fun from(o: JSONObject): QualityRecord {
            val t = o.optJSONObject("tasks") ?: JSONObject()
            return QualityRecord(
                o.optLong("at"), o.optString("model"), o.optString("judge"), o.optDouble("average"), o.optDouble("secs"),
                t.keys().asSequence().associateWith { t.optDouble(it) }, o.optString("by", "phone").ifBlank { "phone" },
            )
        }
    }
}

object QualityMath {
    const val ANSWER_SYSTEM = "You are a helpful assistant. Answer the task as well as you can."

    val TASKS: List<QualityTask> = listOf(
        QualityTask(
            "summary", "Summary",
            "Summarise this text in exactly three sentences, for someone who hasn't read it:\n\n" +
                "The town council of Lindenau voted on Tuesday to close the old swimming pool at the end of the summer. " +
                "Repairs to the roof and the water treatment would have cost 4.2 million francs, more than a new building " +
                "on the same site, which is estimated at 3.8 million. The new pool is planned to open in spring 2029; " +
                "until then, residents can use the pool in the neighbouring town of Brugg at the Lindenau price, under an " +
                "agreement signed last month. Two council members voted against, arguing that the town would be without " +
                "its own pool for almost three years and that the swimming club would lose members. The club's president " +
                "said they would ask for bus transport to Brugg for children's training.",
            "Exactly three sentences. Must say: the old pool closes after the summer; repairs (4.2 million) would cost more " +
                "than a new pool (3.8 million) planned for spring 2029; residents can swim in Brugg at the local price meanwhile. " +
                "Mentioning the two votes against or the club is a plus. Nothing invented.",
            600,
        ),
        QualityTask(
            "translate", "Translation",
            "Translate into natural German (as written in Switzerland, so 'ss' instead of 'ß'):\n\n" +
                "\"We're running a bit behind schedule, so let's touch base on Friday instead. If anything comes up in the " +
                "meantime, just drop me a line — no need to wait for the meeting.\"",
            "Accurate and idiomatic, not word for word: 'behind schedule' ≈ 'im Verzug / etwas hinter dem Zeitplan', " +
                "'touch base' ≈ 'uns austauschen / kurz sprechen', 'drop me a line' ≈ 'schreib mir kurz'. Uses 'ss', never 'ß'. " +
                "Consistent du or Sie. No added content.",
            500,
        ),
        QualityTask(
            "reason", "Reasoning",
            "Anna, Ben and Clara each own exactly one pet: a cat, a dog or a parrot. The parrot's owner is older " +
                "than Ben. Clara is the youngest. Clara is allergic to dogs. Who owns which pet? Explain briefly.",
            "The only answer: Anna owns the parrot, Ben owns the dog, Clara owns the cat. (The parrot's owner is older than " +
                "Ben, so it isn't Ben, and not Clara, the youngest: Anna. Clara can't have the dog: she has the cat, Ben the dog.) " +
                "A wrong assignment scores 1–3 whatever the explanation.",
            1200,
        ),
        QualityTask(
            "math", "Arithmetic",
            "A train leaves at 08:47 and arrives at 11:32. It stops 4 times for 3 minutes each. How many minutes " +
                "is it actually moving? Show the steps.",
            "Right answer: 153 minutes (2 h 45 min = 165 min, minus 12 min of stops). A wrong number scores 1–3.",
            800,
        ),
        QualityTask(
            "code", "Code",
            "Write a Python function merge(intervals) that takes a list of [start, end] pairs and returns them merged " +
                "where they overlap or touch, sorted by start. Example: [[5,7],[1,3],[2,4],[7,8]] → [[1,4],[5,8]]. " +
                "Only the function and a one-line comment.",
            "Correct: sorts by start, merges when next start <= current end (touching counts, as in the example), " +
                "handles an empty list, doesn't change the input in a harmful way. The example must give [[1,4],[5,8]]. " +
                "Wrong results score 1–4.",
            900,
        ),
        QualityTask(
            "extract", "Data extraction",
            "From this message, return only JSON with the keys name, date (YYYY-MM-DD), people (a number) and " +
                "allergies (a list):\n\n\"Hi, this is Marta Keller. I'd like to book a table for Saturday 14 March 2026 " +
                "for four, maybe five, people — let's say five. One of us can't have nuts and another is gluten-free. Thanks!\"",
            "Exactly: {\"name\": \"Marta Keller\", \"date\": \"2026-03-14\", \"people\": 5, \"allergies\": [\"nuts\", \"gluten\"]} " +
                "(wording of the allergies may differ slightly). Only JSON, nothing around it. Each wrong or missing value costs points.",
            400,
        ),
        QualityTask(
            "german", "German letter",
            "Schreibe eine kurze, höfliche Absage auf eine Einladung zum Firmenjubiläum am 12. Juni. Grund: eine " +
                "Familienfeier am selben Tag. Gratuliere und biete an, bald einen Kaffee zu trinken. Höchstens 90 Wörter, Sie-Form.",
            "Correct, natural German in the Sie form, polite and warm; declines, gives the family celebration as the reason, " +
                "congratulates, offers a coffee soon; at most 90 words; uses 'ss' or 'ß' consistently. Errors in grammar cost points.",
            500,
        ),
        QualityTask(
            "format", "Following instructions",
            "Give exactly three bullet points on why walking is good for you. Each at most 10 words. No title, no " +
                "introduction, nothing after the list.",
            "Exactly three bullet points, each at most 10 words, nothing else at all (no heading, no intro, no closing line). " +
                "Content sensible. Any extra line or a fourth point: at most 4.",
            300,
        ),
    )

    /** The labels A, B, C… for [n] answers. */
    fun labels(n: Int): List<String> = (0 until n).map { ('A' + it).toString() }

    /** The order the answers of [task] are shown in: shuffled with a fixed seed per test, so no model always comes first. */
    fun order(models: List<String>, task: String, seed: Long): List<String> =
        models.shuffled(java.util.Random(seed xor task.hashCode().toLong()))

    /** The grader's prompt for one task: the rubric, then each answer under its label. */
    fun gradePrompt(task: QualityTask, answers: List<Pair<String, String>>): String = buildString {
        append("You are grading answers to the same task, without knowing who wrote them. Be strict and fair; judge only ")
        append("what is written. Score each answer from 1 (useless or wrong) to 10 (excellent, fully correct).\n\n")
        append("TASK:\n").append(task.prompt).append("\n\n")
        append("WHAT A GOOD ANSWER DOES:\n").append(task.rubric).append("\n\n")
        for ((label, text) in answers) {
            append("=== ANSWER ").append(label).append(" ===\n")
            append(text.ifBlank { "(no answer)" }.take(6000)).append("\n\n")
        }
        append("Reply with JSON only, one entry per answer, like ")
        append("{\"A\": {\"score\": 7, \"why\": \"one short reason\"}, \"B\": {\"score\": 3, \"why\": \"...\"}}")
    }

    /**
     * Reads the grader's reply: label → (score 1–10, the short reason). JSON first (anywhere in the reply, after any
     * thinking); else lines like "A: 7". Scores outside 1–10 are clamped; labels not in [labels] are ignored.
     */
    fun parseVerdict(reply: String, labels: List<String>): Map<String, Pair<Double, String>> {
        val text = reply.replace(Regex("(?s)<think>.*?</think>"), "")
        val out = LinkedHashMap<String, Pair<Double, String>>()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start in 0 until end) try {
            val o = JSONObject(text.substring(start, end + 1))
            for (l in labels) {
                val v = o.opt(l) ?: continue
                val (score, why) = when (v) {
                    is JSONObject -> v.optDouble("score") to v.optString("why")
                    is Number -> v.toDouble() to ""
                    else -> v.toString().toDoubleOrNull() to ""
                }
                if (score != null && !score.isNaN()) out[l] = score.coerceIn(1.0, 10.0) to why
            }
        } catch (_: Exception) {
        }
        if (out.isEmpty()) for (l in labels) {
            Regex("""(?m)^\W*(?:answer\s+)?$l\W{0,3}[:=\-–]\s*(\d{1,2}(?:\.\d)?)""", RegexOption.IGNORE_CASE).find(text)?.let {
                out[l] = it.groupValues[1].toDouble().coerceIn(1.0, 10.0) to ""
            }
        }
        return out
    }

    /** Every model's grades from what the test brought back, best first. */
    fun analyze(raw: QualityRaw): List<QualityScore> {
        val models = raw.answers.map { it.model }.distinct()
        val per = models.associateWith { LinkedHashMap<String, Double>() }
        val notes = models.associateWith { LinkedHashMap<String, String>() }
        for (task in TASKS) {
            val shown = raw.order[task.id] ?: continue
            val reply = raw.verdicts[task.id] ?: continue
            val labels = labels(shown.size)
            val got = parseVerdict(reply, labels)
            for ((i, m) in shown.withIndex()) {
                val answered = raw.answers.firstOrNull { it.task == task.id && it.model == m }?.text?.isNotBlank() == true
                val g = got[labels[i]]
                // No answer (an error) counts as 1; an answer the grader skipped stays ungraded.
                per[m]?.set(task.id, if (!answered) 1.0 else g?.first ?: Double.NaN)
                if (g != null && g.second.isNotBlank()) notes[m]?.set(task.id, g.second)
            }
        }
        return models.map { m ->
            val scores = per.getValue(m)
            val valid = scores.values.filter { !it.isNaN() }
            val secs = raw.answers.filter { it.model == m && it.text.isNotBlank() }.map { it.secs }
            QualityScore(m, scores, if (valid.isEmpty()) Double.NaN else valid.average(), if (secs.isEmpty()) Double.NaN else secs.average(), notes.getValue(m))
        }.sortedByDescending { if (it.average.isNaN()) -1.0 else it.average }
    }

    fun records(raw: QualityRaw, at: Long, by: String): List<QualityRecord> =
        analyze(raw).map { QualityRecord(at, it.model, raw.judgeName, it.average, it.secs, it.perTask, by) }

    /** The text shown after a test: each model's average, best first, and who graded. */
    fun summary(raw: QualityRaw): String {
        val scores = analyze(raw)
        val self = scores.any { it.model == raw.judgeName }
        return scores.joinToString("\n") { s ->
            "${s.model}: ${if (s.average.isNaN()) "—" else SpeedMath.f(s.average)} of 10" +
                (if (s.secs.isNaN()) "" else " · ${SpeedMath.f(s.secs)} s per answer")
        } + "\nGraded by ${raw.judgeName}" + if (self) " (it graded its own answers too, unknowingly: read its score with care)." else "."
    }

    // How the NAS agent hands back what it got.
    fun rawJson(r: QualityRaw): JSONObject = JSONObject().put("judge", r.judgeName)
        .put("answers", JSONArray(r.answers.map { JSONObject().put("task", it.task).put("model", it.model).put("text", it.text).put("secs", it.secs) }))
        .put("order", JSONObject().apply { r.order.forEach { (k, v) -> put(k, JSONArray(v)) } })
        .put("verdicts", JSONObject().apply { r.verdicts.forEach { (k, v) -> put(k, v) } })

    fun raw(o: JSONObject): QualityRaw {
        val a = o.optJSONArray("answers") ?: JSONArray()
        val ord = o.optJSONObject("order") ?: JSONObject()
        val ver = o.optJSONObject("verdicts") ?: JSONObject()
        return QualityRaw(
            o.optString("judge"),
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { QualityAnswer(it.optString("task"), it.optString("model"), it.optString("text"), it.optDouble("secs")) } },
            ord.keys().asSequence().associateWith { k -> ord.optJSONArray(k)?.let { l -> (0 until l.length()).map { l.optString(it) } } ?: emptyList() },
            ver.keys().asSequence().associateWith { ver.optString(it) },
        )
    }

    /** The tasks as sent to the NAS agent (so it asks exactly these). */
    fun tasksJson(): JSONArray = JSONArray(TASKS.map {
        JSONObject().put("id", it.id).put("title", it.title).put("prompt", it.prompt).put("rubric", it.rubric).put("max_tokens", it.maxTokens)
    })
}

package app.tiinyremote

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * The texts every speed test uses, the same on the phone and on the NAS agent (sent with a NAS test), so results
 * compare wherever they were measured.
 */
object SpeedTexts {
    const val STORY = "Write a story of about 250 words about a lighthouse keeper and a storm."

    /** A made-up earlier chat for the helper job: the same text every time, so tests compare. */
    val JOB_TEXT: String = buildString {
        listOf(
            "User: I'm planning a four-day trip to Lisbon in March with my sister. We like food, old neighbourhoods and views, not museums.",
            "Assistant: Base yourselves in Baixa or Chiado: flat streets, the tram 28 stop and the river close by. Day 1 Alfama and the castle, day 2 Belém for pastries and the river walk, day 3 a day trip to Sintra, day 4 LX Factory and the Bairro Alto viewpoints.",
            "User: Sintra sounds crowded. Is Cascais better? And how do we get there?",
            "Assistant: Cascais is calmer: a beach town 40 minutes by train from Cais do Sodré, trains every 20 minutes, about 2.30 euros each way. Sintra is busiest from 10 to 4; go early or skip it.",
            "User: We'll do Cascais. Budget is about 120 euros a day for both of us, rooms not included. Where should we eat on day 1?",
            "Assistant: In Alfama, try a tasca for grilled sardines and vinho verde, around 30 euros for two. In the evening a fado house; book ahead, the better ones fill up by 8.",
            "User: My sister doesn't eat fish. Also we land at 11 on Thursday and fly back Sunday at 18.",
            "Assistant: Then day 1 starts after lunch: Alfama and the castle in the afternoon, dinner at a place with grilled chicken or bifana. Sunday you have the morning: LX Factory opens at 10, the airport is 25 minutes by metro, leave by 15.",
            "User: Good. Can we fit a cooking class somewhere? And is the Lisboa Card worth it for us?",
            "Assistant: A pastel de nata class takes 2 hours, about 40 euros each; Saturday morning fits. The Lisboa Card pays off with many museums; for you, a Viva Viagem card with zapping credit is cheaper.",
        ).forEach { append(it).append("\n\n") }
    }

    /** The long start of the repeated-start check: about 12,000 characters (four notes of the trip chat). */
    val PREFIX_TEXT: String = (1..4).joinToString("") { "Note $it of a long chat kept for reference:\n\n$JOB_TEXT" }

    const val PREFIX_Q1 = "In one sentence: which town did they choose instead of Sintra?"
    const val PREFIX_Q2 = "In one sentence: how long is the cooking class?"
}

/**
 * Measures a speed test on the phone (the NAS agent measures the same in Python): the raw values go to SpeedMath.
 * [stopped]: the test was asked to stop; [show]: the progress line.
 */
class SpeedMeasure(private val c: DeviceClient, private val stopped: () -> Boolean, private val show: (String) -> Unit) {

    private fun thinkingOff(body: JSONObject, model: String) {
        when (Tools.thinkingMode(c, model)) {
            "switch" -> body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            "always" -> body.put("reasoning_effort", "low")
        }
    }

    /** One ~300-token answer, thinking off where possible. */
    fun one(model: String): Took {
        val body = JSONObject().put("model", model).put("stream", false).put("max_tokens", 300).put("temperature", 0.7)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", SpeedTexts.STORY)))
        thinkingOff(body, model)
        var t0 = System.nanoTime()
        var r = c.request("POST", "/v1/chat/completions", body, 600)
        // A model that has only just loaded, or two requests at once, can answer 500 (seen with gpt-oss): two retries.
        for (retry in 1..2) {
            if (r.ok || r.code < 500 || stopped()) break
            Thread.sleep(10_000L * retry)
            t0 = System.nanoTime()
            r = c.request("POST", "/v1/chat/completions", body, 600)
        }
        val secs = (System.nanoTime() - t0) / 1e9
        if (!r.ok) throw MediaException("${model.substringAfterLast('/')}: ${r.error()}")
        val o = r.json()
        val msg = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        val written = (msg?.optString("content").orEmpty() + msg?.optString("reasoning_content").orEmpty()).length
        val tokens = o.optJSONObject("usage")?.optInt("completion_tokens", 0)?.takeIf { it > 0 } ?: maxOf(1, written / 4)
        return Took(tokens, secs)
    }

    /**
     * Seconds until a typical helper job is done: condensing an earlier chat, asked exactly as the app asks it.
     * A real summary is a few hundred characters; a much shorter answer (seen: 1.6 s right after an NPU restart)
     * isn't the job done and would count as a very quick helper.
     */
    fun job(model: String): Double {
        fun once(): Double {
            val t0 = System.nanoTime()
            val out = Tools.chatOnce(c, model, CONDENSE_SYSTEM, SpeedTexts.JOB_TEXT, 700, timeoutSec = 600)
            if (out.length < 150) throw MediaException("${model.substringAfterLast('/')}: a summary of ${out.length} characters")
            return (System.nanoTime() - t0) / 1e9
        }
        return try {
            once()
        } catch (e: MediaException) {
            if (e.message?.startsWith("Lost the connection") == true || stopped()) throw e
            Thread.sleep(5000) // a 500 now and then under load: once more
            once()
        }
    }

    /** Seconds until the first word of a streamed answer (thinking off where possible, a few words only). */
    private fun firstWord(model: String, system: String, question: String): Double {
        val body = JSONObject().put("model", model).put("stream", true).put("max_tokens", 24).put("temperature", 0.0)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", question)))
        thinkingOff(body, model)
        val done = c.streamChat(body, { _, _ -> }, stopped)
        if (done.firstAfter <= 0.0) throw MediaException("${model.substringAfterLast('/')}: no answer in the repeated-start check")
        return done.firstAfter
    }

    /**
     * The repeated-start check: a ~12,000-character text read fresh (a random mark at its very start, so nothing
     * read before can match), the same request again, and the same text with a new question.
     */
    fun prefix(model: String, name: String): PrefixCheck {
        val system = "Reference ${java.util.UUID.randomUUID()}\n\n" + SpeedTexts.PREFIX_TEXT
        show("the repeated-start check, $name (reading ${system.length / 1000}k characters fresh)…")
        val cold = firstWord(model, system, SpeedTexts.PREFIX_Q1)
        Thread.sleep(PAUSE_MS)
        show("the repeated-start check, $name (the same again)…")
        val same = firstWord(model, system, SpeedTexts.PREFIX_Q1)
        Thread.sleep(PAUSE_MS)
        show("the repeated-start check, $name (a new question)…")
        val newEnd = firstWord(model, system, SpeedTexts.PREFIX_Q2)
        return PrefixCheck(name, system.length, cold, same, newEnd)
    }

    /**
     * [n] runs for a loaded pair. A run that hits an error (a 500, a lost connection) is left out, not the whole
     * pair: the other runs count. Lost the Tiiny: [onLost] waits for it (a test of several pairs); twice in a row, or
     * not back, ends the pair. The repeated-start check runs once, after the first run, for both models.
     */
    suspend fun pair(big: ModelInfo, small: ModelInfo, n: Int, tag: String, onLost: (() -> Boolean)?): SpeedRaw {
        val runs = ArrayList<SpeedRun>()
        val checks = ArrayList<PrefixCheck>()
        var left = 0
        var lostInRow = 0
        suspend fun pause() {
            if (stopped()) throw MediaException("Stopped")
            kotlinx.coroutines.delay(PAUSE_MS)
        }
        for (i in 1..n) {
            val run = if (n > 1) " (run $i of $n)" else ""
            fun say(what: String) = show("Testing$tag$run: $what")
            try {
                if (i > 1) pause()
                say("${big.name} alone…")
                val a = one(big.id)
                pause()
                say("${small.name} alone…")
                val b = one(small.id)
                pause()
                say("both at the same time…")
                val (a2, b2) = coroutineScope {
                    val x = async { one(big.id) }
                    val y = async { one(small.id) }
                    x.await() to y.await()
                }
                pause()
                say("${small.name} twice at the same time…")
                val (h1, h2) = coroutineScope {
                    val x = async { one(small.id) }
                    val y = async { one(small.id) }
                    x.await() to y.await()
                }
                pause()
                say("a helper job (condensing a chat), ${small.name}…")
                val sj = job(small.id)
                pause()
                say("the same job, ${big.name}…")
                val bj = job(big.id)
                // Only a finished run counts: everything of it at once.
                runs += SpeedRun(a, b, a2, b2, h1, h2, sj, bj)
                lostInRow = 0
            } catch (e: Exception) {
                if (stopped() || e is kotlinx.coroutines.CancellationException) throw e
                left++
                ApiLog.add("Speed test$tag: run $i left out (${e.message})", "Chat", isProblem = true)
                if (e.message?.startsWith("Lost the connection") == true && (++lostInRow >= 2 || onLost?.invoke() != true)) throw e
            }
            // The repeated-start check once per pair, after a run (so both models are warm), for each model.
            if (i == 1 || (checks.isEmpty() && runs.isNotEmpty())) {
                for (m in listOf(big, small)) {
                    if (checks.any { it.model == m.name }) continue
                    try {
                        pause()
                        checks += prefix(m.id, m.name)
                    } catch (e: Exception) {
                        if (stopped() || e is kotlinx.coroutines.CancellationException) throw e
                        ApiLog.add("Speed test$tag: repeated-start check of ${m.name} left out (${e.message})", "Chat", isProblem = true)
                    }
                }
            }
        }
        if (runs.isEmpty()) throw MediaException("no run finished; the last error is in the log")
        return SpeedRaw(big.id, big.name, small.id, small.name, runs, left, checks)
    }

    companion object {
        /** A short pause before every measurement, so one doesn't run into the end of the one before. */
        const val PAUSE_MS = 250L
    }
}

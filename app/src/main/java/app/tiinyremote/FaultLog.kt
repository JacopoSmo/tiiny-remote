package app.tiinyremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One NPU fault: when, and the models on the NPU then (names). */
data class NpuFault(val at: Long, val models: List<String>)

/**
 * Which pairs of models were on the NPU when it faulted (a pair counts each fault it was part of). Only models with
 * at least [MIN_SHARE]% of the NPU are paired: the small always-ready ones (embeddings, OCR, a voice) ride along
 * everywhere and say nothing. No Android here: checked by the automated tests.
 */
object FaultMath {
    const val MIN_SHARE = 10.0

    /** A pair's key, the same whichever way round. */
    fun key(a: String, b: String): String = listOf(a, b).sorted().joinToString(" + ")

    /** Pair key → (faults, the latest one's time). */
    fun pairs(faults: List<NpuFault>): Map<String, Pair<Int, Long>> {
        val out = HashMap<String, Pair<Int, Long>>()
        for (f in faults) {
            val m = f.models.distinct().sorted()
            for (i in m.indices) for (j in i + 1 until m.size) {
                val k = key(m[i], m[j])
                val (n, last) = out[k] ?: (0 to 0L)
                out[k] = (n + 1) to maxOf(last, f.at)
            }
        }
        return out
    }

    /**
     * The pair to warn about when [loading] joins [loaded] (names of models big enough to count): the one with the
     * most faults, from two on, that isn't silenced. Null: no warning.
     */
    fun warn(faults: List<NpuFault>, loading: String, loaded: List<String>, silenced: Set<String>): Triple<String, Int, Long>? {
        val p = pairs(faults)
        return loaded.filter { it != loading }
            .mapNotNull { other -> p[key(loading, other)]?.let { (n, last) -> Triple(other, n, last) } }
            .filter { it.second >= 2 && key(loading, it.first) !in silenced }
            .maxByOrNull { it.second }
    }
}

/** The faults seen (by the app, or by the NAS during a job), kept on the phone: the last 200. */
object FaultLog {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("npu_faults", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<NpuFault> = try {
        val a = JSONArray(prefs(ctx).getString("faults", "[]"))
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { o -> NpuFault(o.optLong("at"), o.optJSONArray("models")?.let { m -> (0 until m.length()).map { m.optString(it) } } ?: emptyList()) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Records a fault once (the same time again is the same fault, seen twice). [models]: those that count. */
    fun record(ctx: Context, at: Long, models: List<String>) {
        val list = all(ctx)
        if (list.any { kotlin.math.abs(it.at - at) < 60_000 }) return
        val next = (listOf(NpuFault(at, models)) + list).take(200)
        prefs(ctx).edit().putString("faults", JSONArray(next.map { JSONObject().put("at", it.at).put("models", JSONArray(it.models)) }).toString()).apply()
    }

    fun silenced(ctx: Context): Set<String> = prefs(ctx).getStringSet("silenced", emptySet()) ?: emptySet()

    fun silence(ctx: Context, pair: String) {
        prefs(ctx).edit().putStringSet("silenced", silenced(ctx) + pair).apply()
    }

    /** Warnings on again, for every pair. */
    fun unsilenceAll(ctx: Context) {
        prefs(ctx).edit().remove("silenced").apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}

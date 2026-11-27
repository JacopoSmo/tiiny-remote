package app.tiinyremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** One use of a model: a chat answer, a job made, or loading it by hand. */
data class ModelUse(val at: Long, val model: String, val name: String)

/** A model usually used on this weekday (1 Monday … 7 Sunday) in this hour, on [days] different days lately. */
data class UseSlot(val dow: Int, val hour: Int, val model: String, val name: String, val days: Int)

/**
 * Load ahead's learning: which model you use on which weekday at which hour. A slot counts once a model was used
 * in it on at least [MIN_DAYS] different days of the last four weeks. No Android here: checked by the automated tests.
 */
object LoadAheadMath {
    const val WINDOW_DAYS = 28L
    const val MIN_DAYS = 2
    /** Minutes before the hour a model is loaded (Automatic) or offered (Suggest). */
    const val LEAD_MIN = 10L

    fun plan(uses: List<ModelUse>, zone: ZoneId, now: Long): List<UseSlot> {
        val since = now - WINDOW_DAYS * 86_400_000L
        val names = HashMap<String, String>()
        val days = HashMap<Triple<Int, Int, String>, MutableSet<java.time.LocalDate>>()
        for (u in uses) {
            if (u.at < since || u.at > now) continue
            val t = Instant.ofEpochMilli(u.at).atZone(zone)
            names[u.model] = u.name
            days.getOrPut(Triple(t.dayOfWeek.value, t.hour, u.model)) { HashSet() } += t.toLocalDate()
        }
        return days.filter { it.value.size >= MIN_DAYS }
            .map { (k, d) -> UseSlot(k.first, k.second, k.third, names[k.third] ?: k.third, d.size) }
            .sortedWith(compareBy({ it.dow }, { it.hour }, { -it.days }))
    }

    /** The slots of the coming hour: from [LEAD_MIN] minutes before it until its end. */
    fun due(plan: List<UseSlot>, t: ZonedDateTime): List<UseSlot> {
        val s = t.plusMinutes(LEAD_MIN)
        return plan.filter { it.dow == s.dayOfWeek.value && it.hour == s.hour }
    }

    /** The plan as people read it: per model, its usual times ("Mon–Fri 08:00, Sat 10:00"). */
    fun describe(plan: List<UseSlot>): List<String> {
        val dn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return plan.groupBy { it.name }.map { (name, l) ->
            val byHour = l.groupBy { it.hour }.toSortedMap().map { (h, s) ->
                val ds = s.map { it.dow }.sorted()
                val days = if (ds.size >= 3 && ds.zipWithNext().all { (a, b) -> b == a + 1 }) "${dn[ds.first() - 1]}–${dn[ds.last() - 1]}"
                else ds.joinToString(", ") { dn[it - 1] }
                "$days %02d:00".format(h)
            }
            "$name: " + byHour.joinToString(" · ")
        }
    }
}

/** Load ahead's memory on the phone: the uses (last four weeks, compacted to one per model and hour). */
object LoadAhead {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("load_ahead", Context.MODE_PRIVATE)

    fun uses(ctx: Context): List<ModelUse> = try {
        val a = JSONArray(prefs(ctx).getString("uses", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> ModelUse(o.optLong("at"), o.optString("model"), o.optString("name")) } }
    } catch (e: Exception) {
        emptyList()
    }

    /** Notes a use of [model]: once per model and hour is enough. */
    @Synchronized
    fun note(ctx: Context, model: String, name: String) {
        if (model.isBlank()) return
        val now = System.currentTimeMillis()
        val hour = now / 3_600_000L
        val list = uses(ctx)
        if (list.any { it.model == model && it.at / 3_600_000L == hour }) return
        val keep = (listOf(ModelUse(now, model, name.ifBlank { model.substringAfterLast('/') })) + list)
            .filter { now - it.at <= LoadAheadMath.WINDOW_DAYS * 86_400_000L }.take(3000)
        prefs(ctx).edit().putString("uses", JSONArray(keep.map { JSONObject().put("at", it.at).put("model", it.model).put("name", it.name) }).toString()).apply()
    }

    fun plan(ctx: Context): JSONArray = JSONArray(
        LoadAheadMath.plan(uses(ctx), ZoneId.systemDefault(), System.currentTimeMillis()).map {
            JSONObject().put("dow", it.dow).put("hour", it.hour).put("model", it.model)
        },
    )

    fun slots(ctx: Context): List<UseSlot> = LoadAheadMath.plan(uses(ctx), ZoneId.systemDefault(), System.currentTimeMillis())

    /** The models to offer or load now (not dismissed for this hour). */
    fun dueNow(ctx: Context): List<UseSlot> {
        val now = ZonedDateTime.now()
        val key = now.plusMinutes(LoadAheadMath.LEAD_MIN).let { "%s %02d".format(it.toLocalDate(), it.hour) }
        val gone = prefs(ctx).getStringSet("dismissed", emptySet()).orEmpty()
        return LoadAheadMath.due(slots(ctx), now).filter { "$key ${it.model}" !in gone }
    }

    /** ✕ on the line, or tried once by Automatic: not again this hour. */
    fun dismiss(ctx: Context, model: String) {
        val now = ZonedDateTime.now().plusMinutes(LoadAheadMath.LEAD_MIN)
        val key = "%s %02d %s".format(now.toLocalDate(), now.hour, model)
        val today = now.toLocalDate().toString()
        val keep = prefs(ctx).getStringSet("dismissed", emptySet()).orEmpty().filter { it.startsWith(today) }.toSet() + key
        prefs(ctx).edit().putStringSet("dismissed", keep).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}

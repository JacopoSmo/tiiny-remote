package app.tiinyremote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live device numbers, read like Tiiny Studio does: the gateway's /api/v1/npu/status (NPU busy, memory, CPU, RAM),
 * /api/v1/sys/status (storage), and the optional on-device dashboard at :9109/api/stats for board power and
 * temperatures (a community add-on, not part of TiinyOS; when it isn't running those stay empty).
 */
data class DeviceStats(
    val npuUtil: Double? = null,
    val npuMemUsedMb: Double? = null,
    val npuMemTotalMb: Double? = null,
    val npuTempC: Double? = null,
    val npuPowerW: Double? = null,
    val cpuPct: Double? = null,
    val memPct: Double? = null,
    /** The Tiiny's RAM in GB: used and total (the total from the device if it says, else its 80 GB). */
    val ramUsedGb: Double? = null,
    val ramTotalGb: Double? = null,
    val diskFreeGb: Double? = null,
    val diskTotalGb: Double? = null,
    /** Every temperature sensor the dashboard reports, hottest first. */
    val temps: List<Pair<String, Double>> = emptyList(),
    /** A fault the NPU reported (e.g. a PCIe transfer timeout), with when it happened, while the Tiiny reports it. */
    val npuIssue: String? = null,
    /** A fault that happened while the app was watching (not an old one still in the status): the status bar blinks for it. */
    val npuFaultNew: String? = null,
    val gateway: Boolean = false,
    val dashboard: Boolean = false,
    val time: Long = System.currentTimeMillis(),
)

object Stats {
    /** The Tiiny's free storage as last read (GB), or null: jobs need room for their results there. */
    @Volatile
    var freeGb: Double? = null

    /** The models on the NPU as the app last saw them ("A 49% + B 29%"), for the log when a fault happens. */
    @Volatile
    var onNpu: String = ""

    /** A fault seen happening, kept until the NPU restarts cleanly, so the status bar keeps blinking until it's looked at. */
    @Volatile
    private var newFault: String? = null

    /** The last NPU fault logged, so each one is logged once. */
    @Volatile
    private var lastIssue: String? = null

    private fun JSONObject.num(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

    @Volatile
    private var logged = false

    /** A size in GB from whichever field the device uses (…_bytes, …_mb, …_gb, or bytes with no suffix). */
    private fun JSONObject.gb(name: String): Double? =
        num("${name}_bytes")?.let { it / 1e9 } ?: num("${name}_mb")?.let { it / 1024 } ?: num("${name}_gb")
            ?: num(name)?.let { if (it > 1e6) it / 1e9 else null }

    @Volatile
    private var ramTotal: Double? = null

    @Volatile
    private var disk: Pair<Long, Pair<Double, Double>?>? = null

    /** The Tiiny the cached values above belong to: another address starts afresh. */
    @Volatile
    private var cachedFor = ""

    /** When the on-device dashboard last didn't answer: it's an optional add-on, so it's only retried once a minute. */
    @Volatile
    private var dashboardMissingAt = 0L

    /** The dashboard answered at least once: then it's asked every time, and a busy moment doesn't hide it for a minute. */
    @Volatile
    private var dashboardSeen = false

    /**
     * The time ("20:37") of an NPU fault the Tiiny reports from the last [minutes] minutes, or null: asked when a chat
     * answer broke off, to tell a fault on the Tiiny from a network problem. Quick (5 s) and silent in the log.
     */
    fun recentFault(c: DeviceClient, minutes: Int = 15): String? = try {
        val r = c.request("GET", "/api/v1/npu/status", null, 5, log = false)
        val npu = if (r.ok) r.json().optJSONObject("npu") else null
        val faulted = npu != null && (npu.optJSONObject("reason") ?: npu.optJSONObject("failure")) != null
        val at = npu?.num("started_at")?.let { (it * 1000).toLong() }
        if (faulted && at != null && System.currentTimeMillis() - at in 0..minutes * 60_000L) {
            java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ENGLISH))
        } else null
    } catch (e: Exception) {
        null
    }

    fun read(settings: AppSettings): DeviceStats {
        var s = DeviceStats()
        val c = DeviceClient(settings.tiinyUrl, settings.apiKey)
        if (c.base != cachedFor) {
            cachedFor = c.base
            ramTotal = null
            disk = null
            dashboardMissingAt = 0L
            dashboardSeen = false
        }
        // /api/v1/sys/status is read at most once per round, for the RAM total and the storage.
        var sys: JSONObject? = null
        var sysRead = false
        fun sysStatus(): JSONObject? {
            if (!sysRead) {
                sysRead = true
                sys = try {
                    c.request("GET", "/api/v1/sys/status", null, 5, log = false).takeIf { it.ok }?.json()
                } catch (e: Exception) {
                    null
                }
            }
            return sys
        }
        try {
            val r = c.request("GET", "/api/v1/npu/status", null, 5, log = false)
            if (r.ok) {
                val o = r.json()
                if (!logged) {
                    logged = true
                    ApiLog.add("npu/status: ${o.toString().take(600)}")
                }
                val dev = o.optJSONArray("devices")?.optJSONObject(0) ?: JSONObject()
                // A fault reported by the NPU: logged once each time it happens (it's kept in the status afterwards).
                val npu = o.optJSONObject("npu")
                val reason = npu?.optJSONObject("reason") ?: npu?.optJSONObject("failure")
                // Besides a reason, a status that itself says error or failure counts as a fault.
                val bad = npu?.optString("status").orEmpty().lowercase().let { "error" in it || "fail" in it || "fault" in it }
                val issue = (if (reason == null && bad) npu?.optString("status_message")?.takeIf { it.isNotBlank() && it != "null" } ?: "NPU status: ${npu?.optString("status")}" else null) ?: reason?.let { r ->
                    val msg = r.optString("message").takeIf { it.isNotBlank() && it != "null" } ?: r.optString("code")
                    val at = npu?.num("started_at")?.let { t ->
                        java.time.Instant.ofEpochMilli((t * 1000).toLong()).atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.ENGLISH)) // "21 Nov 08:18"
                    }
                    msg.takeIf { it.isNotBlank() && it != "null" }?.let { m -> if (at != null) "$m (on $at; the NPU restarted then)" else m }
                }
                if (issue != null && issue != lastIssue) {
                    lastIssue = issue
                    // TiinyOS keeps the last fault in its status until the NPU restarts again: one from more than 10
                    // minutes ago (seen when the app starts, say) is an old one, and the log says so.
                    val ageMin = npu?.num("started_at")?.let { (System.currentTimeMillis() / 1000.0 - it) / 60 }
                    val code = reason?.optString("code")?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
                    val old = ageMin != null && ageMin > 10
                    if (!old) newFault = issue
                    ApiLog.add(
                        if (old) "Earlier NPU fault, still in the Tiiny's status (${Math.round(ageMin!!)} min ago, nothing new): $issue$code"
                        else "NPU fault reported by the Tiiny: $issue$code. On the NPU at the time: ${onNpu.ifBlank { "no model" }}.",
                        "Device",
                        isProblem = !old,
                    )
                }
                if (issue == null) newFault = null
                s = s.copy(
                    npuIssue = issue,
                    npuFaultNew = newFault,
                    gateway = true,
                    npuUtil = dev.num("util_percent"),
                    npuMemUsedMb = dev.num("mem_used_mb"),
                    npuMemTotalMb = dev.num("mem_total_mb"),
                    npuTempC = dev.num("temp_c"),
                    npuPowerW = dev.num("power_w"),
                    cpuPct = o.optJSONObject("cpu")?.num("total_percent"),
                    memPct = o.optJSONObject("memory")?.num("usage_percent"),
                    ramUsedGb = o.optJSONObject("memory")?.gb("used"),
                    ramTotalGb = o.optJSONObject("memory")?.gb("total"),
                )
            }
        } catch (e: Exception) {
        }
        // RAM: the sizes if the status has them; the total otherwise from /api/v1/sys/status, else the Tiiny's 80 GB.
        if (s.ramTotalGb != null) ramTotal = s.ramTotalGb
        if (ramTotal == null && s.gateway) {
            ramTotal = sysStatus()?.let { o ->
                (o.optJSONObject("memory") ?: o.optJSONObject("mem") ?: o.optJSONObject("ram"))?.gb("total")
            } ?: 80.0
        }
        val total = s.ramTotalGb ?: ramTotal
        if (total != null) s = s.copy(ramTotalGb = total, ramUsedGb = s.ramUsedGb ?: s.memPct?.let { it / 100 * total })

        // Storage changes slowly: read it once a minute.
        val d = disk
        if (d == null || System.currentTimeMillis() - d.first > 60_000) {
            val dk = sysStatus()?.optJSONObject("disk")
            val dTotal = dk?.num("total_bytes")
            val dUsed = dk?.num("used_bytes")
            val v = if (dTotal != null && dUsed != null && dTotal > 0) (dTotal - dUsed).coerceAtLeast(0.0) / 1e9 to dTotal / 1e9 else null
            disk = System.currentTimeMillis() to v
        }
        disk?.second?.let { (f, t) ->
            s = s.copy(diskFreeGb = f, diskTotalGb = t)
            freeGb = f
        }

        // The on-device dashboard (port 9109), when installed: board power, NPU load and every temperature.
        val host = try {
            URL(TiinyApi.normalize(settings.tiinyUrl)).host
        } catch (e: Exception) {
            ""
        }
        val now = System.currentTimeMillis()
        if (host.isNotEmpty() && (dashboardSeen || now - dashboardMissingAt > 60_000)) try {
            val conn = URL("http://$host:9109/api/stats").openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 2000
            try {
                if (conn.responseCode == 200) {
                    val o = JSONObject(conn.inputStream.bufferedReader().readText())
                    val npu = o.optJSONObject("npu") ?: JSONObject()
                    val t = npu.optJSONObject("temps")
                    val temps = t?.keys()?.asSequence()?.mapNotNull { k -> t.num(k)?.let { k to it } }?.sortedByDescending { it.second }?.toList().orEmpty()
                    dashboardSeen = true
                    s = s.copy(
                        dashboard = true,
                        npuPowerW = npu.num("board_power_w") ?: s.npuPowerW,
                        npuUtil = npu.num("avg_util") ?: s.npuUtil,
                        npuTempC = temps.firstOrNull()?.second ?: s.npuTempC,
                        temps = temps,
                    )
                } else {
                    dashboardMissingAt = now
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            dashboardMissingAt = now
        }
        return s
    }
}

/** What the Tiiny says about itself (Settings → About) and its own request statistics (Log). */
object TiinyInfo {
    /** TiinyOS, service and hardware lines for About, in order; empty when the Tiiny can't be asked. No device name. */
    fun about(settings: AppSettings): List<Pair<String, String>> {
        if (!settings.tiinyConfigured) return emptyList()
        val c = DeviceClient(settings.tiinyUrl, settings.apiKey)
        val o = try {
            c.request("GET", "/api/v1/sys/device_info", null, 8, log = false).takeIf { it.ok }?.json()
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        fun v(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        return listOfNotNull(
            v("tiiny_os")?.let { "TiinyOS" to it },
            v("version")?.let { "Service version" to it },
            listOfNotNull(v("device_model_name"), v("device_model_version")).joinToString(" ").ifBlank { null }?.let { "Model" to it },
            v("dnpu")?.let { "NPU" to it },
            v("cpu")?.let { "CPU" to it },
            v("ram")?.let { "RAM" to it },
            v("storage")?.let { "Storage" to it },
        )
    }

    /**
     * The Tiiny's own statistics of the requests it served (/api/v1/sys/performance), as one readable text: totals, the
     * endpoints that had errors (with their HTTP codes), and the slowest. For finding what failed around a fault.
     */
    fun requestStats(settings: AppSettings): Pair<String, Boolean> {
        val c = DeviceClient(settings.tiinyUrl, settings.apiKey)
        val r = c.request("GET", "/api/v1/sys/performance?limit=200&sort_by=error_count", null, 10, log = false)
        if (!r.ok) throw MediaException(if (r.code == 404) "This TiinyOS has no request statistics." else r.error())
        val o = r.json()
        fun ms(v: Double) = if (v >= 1000) String.format(java.util.Locale.ROOT, "%.1f s", v / 1000) else "${Math.round(v)} ms"
        val sum = o.optJSONObject("summary") ?: org.json.JSONObject()
        val eps = o.optJSONArray("endpoints") ?: org.json.JSONArray()
        val list = (0 until eps.length()).mapNotNull { eps.optJSONObject(it) }
        fun line(e: org.json.JSONObject): String {
            val codes = e.optJSONObject("status_code_counts")?.let { cc ->
                cc.keys().asSequence().filter { !it.startsWith("2") }.joinToString(", ") { "HTTP $it ×${cc.optInt(it)}" }
            }.orEmpty()
            // "2026-11-21T21:26:11.5+00:00" (UTC) → "22:26:11" in the phone's time zone.
            val last = e.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" }?.let { t ->
                " · last " + try {
                    java.time.OffsetDateTime.parse(t).atZoneSameInstant(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm:ss", java.util.Locale.ENGLISH))
                } catch (x: Exception) {
                    t
                }
            } ?: ""
            return "${e.optString("method")} ${e.optString("path")}: ${e.optInt("count")}×" +
                (if (e.optInt("error_count") > 0) ", ${e.optInt("error_count")} failed" + (if (codes.isNotBlank()) " ($codes)" else "") else "") +
                " · typical ${ms(e.optDouble("p50_ms"))}, slowest ${ms(e.optDouble("max_ms"))}$last"
        }
        val withErrors = list.filter { it.optInt("error_count") > 0 }.take(10)
        val slowest = list.sortedByDescending { it.optDouble("p95_ms") }.filter { it !in withErrors }.take(3)
        val errors = sum.optInt("total_errors")
        return buildString {
            // It counts only what its system service answers itself (status, settings…), not chats or models.
            append("The Tiiny's request statistics for its system service (status, device info, settings; chats and models aren't counted there): ")
            append("${sum.optInt("total_requests")} requests, ")
            val total = sum.optInt("total_requests")
            append(if (errors == 0) "none failed" else "$errors failed" + if (total > 0) String.format(java.util.Locale.ROOT, " (%.1f%%)", errors * 100.0 / total) else "")
            append(".")
            if (withErrors.isNotEmpty()) append("\nFailed requests:\n").append(withErrors.joinToString("\n") { "• " + line(it) })
            if (slowest.isNotEmpty()) append("\nSlowest:\n").append(slowest.joinToString("\n") { "• " + line(it) })
        } to (errors > 0)
    }
}

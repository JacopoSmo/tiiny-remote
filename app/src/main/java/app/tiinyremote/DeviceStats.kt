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
    val npuStatus: String? = null,
    val diskFreeGb: Double? = null,
    val diskTotalGb: Double? = null,
    /** Every temperature sensor the dashboard reports, hottest first. */
    val temps: List<Pair<String, Double>> = emptyList(),
    val gateway: Boolean = false,
    val dashboard: Boolean = false,
    val time: Long = System.currentTimeMillis(),
)

object Stats {
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
                s = s.copy(
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
                    npuStatus = o.optJSONObject("npu")?.optString("status")?.takeIf { it.isNotBlank() && it != "null" },
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
        disk?.second?.let { (f, t) -> s = s.copy(diskFreeGb = f, diskTotalGb = t) }

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

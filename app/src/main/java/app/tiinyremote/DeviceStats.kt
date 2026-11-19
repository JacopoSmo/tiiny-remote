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
    private var disk: Pair<Long, Pair<Double, Double>?>? = null

    fun read(settings: AppSettings): DeviceStats {
        var s = DeviceStats()
        val c = DeviceClient(settings.tiinyUrl, settings.apiKey)
        try {
            val r = c.request("GET", "/api/v1/npu/status", null, 5)
            if (r.ok) {
                val o = r.json()
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
                    npuStatus = o.optJSONObject("npu")?.optString("status")?.takeIf { it.isNotBlank() && it != "null" },
                )
            }
        } catch (e: Exception) {
        }
        // Storage changes slowly: read it once a minute.
        val d = disk
        if (d == null || System.currentTimeMillis() - d.first > 60_000) {
            val v = try {
                val r = c.request("GET", "/api/v1/sys/status", null, 5)
                val dk = r.json().optJSONObject("disk")
                val total = dk?.num("total_bytes")
                val used = dk?.num("used_bytes")
                if (r.ok && total != null && used != null && total > 0) (total - used).coerceAtLeast(0.0) / 1e9 to total / 1e9 else null
            } catch (e: Exception) {
                null
            }
            disk = System.currentTimeMillis() to v
        }
        disk?.second?.let { (f, t) -> s = s.copy(diskFreeGb = f, diskTotalGb = t) }

        // The on-device dashboard (port 9109), when installed: board power, NPU load and every temperature.
        val host = try {
            URL(TiinyApi.normalize(settings.tiinyUrl)).host
        } catch (e: Exception) {
            ""
        }
        if (host.isNotEmpty()) try {
            val conn = URL("http://$host:9109/api/stats").openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 2000
            try {
                if (conn.responseCode == 200) {
                    val o = JSONObject(conn.inputStream.bufferedReader().readText())
                    val npu = o.optJSONObject("npu") ?: JSONObject()
                    val t = npu.optJSONObject("temps")
                    val temps = t?.keys()?.asSequence()?.mapNotNull { k -> t.num(k)?.let { k to it } }?.sortedByDescending { it.second }?.toList().orEmpty()
                    s = s.copy(
                        dashboard = true,
                        npuPowerW = npu.num("board_power_w") ?: s.npuPowerW,
                        npuUtil = npu.num("avg_util") ?: s.npuUtil,
                        npuTempC = temps.firstOrNull()?.second ?: s.npuTempC,
                        temps = temps,
                    )
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
        }
        return s
    }
}

package app.tiinyremote

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/**
 * Settings → Tiiny → "Find on my Wi-Fi": asks every address of the phone's home network (a /24, up to 254) whether
 * it's a Tiiny, by the gateway's device status answer that only a Tiiny gives. Nothing leaves the home network, and
 * no key is sent.
 */
object Discover {
    /** While searching, and for a while after: the Wi-Fi is busy, so a missed answer means nothing (no VPN switch). */
    @Volatile private var searching = false
    @Volatile private var endedAt = 0L
    fun busySince(ms: Long) = searching || System.currentTimeMillis() - endedAt < ms

    /** The phone's own address on the current Wi-Fi and the network's size, or null off Wi-Fi. */
    private fun myNet(ctx: Context): Pair<IntArray, Int>? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        // The Wi-Fi network itself, not a VPN on top of it.
        val net = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true }
            ?: return null
        val la = cm.getLinkProperties(net)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return null
        val b = la.address.address.map { it.toInt() and 0xFF }.toIntArray()
        return b to la.prefixLength
    }

    /** How one address answered the Tiiny's status question: null (nothing there), its HTTP code and the start of its answer. */
    private fun ask(ip: String, key: String?): Pair<Int, String>? = try {
        val conn = URL("http://$ip/api/v1/npu/status").openConnection() as HttpURLConnection
        conn.connectTimeout = 1200
        conn.readTimeout = 2500
        if (key != null) conn.setRequestProperty("Authorization", "Bearer $key")
        try {
            val code = conn.responseCode
            val body = ((if (code >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes().take(4000).toByteArray() } ?: ByteArray(0)).toString(Charsets.UTF_8)
            code to body
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun looksTiiny(body: String) = "hm_smi_available" in body || ("\"npu\"" in body && "status" in body) || body.contains("tiiny", true)

    /** The Tiinys found, as addresses ("http://192.168.x.y"); [step] gets how far the search is (0–1). */
    suspend fun find(ctx: Context, step: (Float) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val (b, prefix) = myNet(ctx) ?: throw MediaException("Connect the phone to your home Wi-Fi first.")
        if (prefix < 22) throw MediaException("This network is too large to search; type the Tiiny's address instead.")
        val base = "${b[0]}.${b[1]}.${b[2]}."
        searching = true
        try { findIn(ctx, base, b[3], step) } finally { searching = false; endedAt = System.currentTimeMillis() }
    }

    private suspend fun findIn(ctx: Context, base: String, me: Int, step: (Float) -> Unit): List<String> = withContext(Dispatchers.IO) {
        // The address saved in Settings first: if it's the Tiiny, done without asking the whole network.
        val key0 = Store(ctx).load().let { it.tiinyUrl to it.apiKey.trim().takeIf { k -> k.isNotEmpty() } }
        val savedIp = runCatching { URL(key0.first.trim()).host }.getOrNull()
        if (savedIp != null && savedIp.startsWith(base)) {
            val plain = ask(savedIp, null)?.second?.let(::looksTiiny) == true
            if (plain || key0.second != null && ask(savedIp, key0.second)?.second?.let(::looksTiiny) == true) {
                ApiLog.add("Find on my Wi-Fi: the saved address $savedIp is the Tiiny", "Device")
                step(1f)
                return@withContext listOf("http://$savedIp")
            }
        }
        val b = intArrayOf(0, 0, 0, me)
        // Gently: a few at a time, so the Wi-Fi (and the app's own link to the Tiiny) isn't choked.
        val gate = Semaphore(12)
        var done = 0
        val answers = coroutineScope {
            (1..254).map { n ->
                async {
                    gate.withPermit {
                        val ip = base + n
                        val a = if (n != b[3]) ask(ip, null) else null
                        synchronized(this@Discover) { done++; step(done / 254f) }
                        a?.let { ip to it }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val hits = answers.filter { looksTiiny(it.second.second) }.map { it.first }.toMutableList()
        // A Tiiny may want the API key before it answers. Only addresses that refused with a JSON answer to exactly
        // this question are asked again with it (at most three), never everything on the network.
        val key = Store(ctx).load().apiKey.trim().takeIf { it.isNotEmpty() }
        val wantKey = answers.filter { (_, a) -> a.first in setOf(401, 403) && a.second.trimStart().startsWith("{") }.map { it.first }
        if (hits.isEmpty() && key != null) for (ip in wantKey.take(3)) ask(ip, key)?.let { if (looksTiiny(it.second)) hits.add(ip) }
        ApiLog.add(
            "Find on my Wi-Fi: searched $base1–254; ${answers.size} answered on port 80" +
                (if (answers.isNotEmpty()) " (" + answers.take(12).joinToString(", ") { (ip, a) -> "${ip.substringAfterLast('.')}: HTTP ${a.first}" } + ")" else "") +
                (if (hits.isEmpty()) "; no Tiiny among them" else "; Tiiny at " + hits.joinToString()),
            "Device", isProblem = hits.isEmpty(),
        )
        hits.map { "http://$it" }
    }
}

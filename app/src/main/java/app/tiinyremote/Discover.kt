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

    private fun isTiiny(ip: String): Boolean = try {
        val conn = URL("http://$ip/api/v1/npu/status").openConnection() as HttpURLConnection
        conn.connectTimeout = 600
        conn.readTimeout = 1500
        try {
            val code = conn.responseCode
            val body = ((if (code >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes().take(4000).toByteArray() } ?: ByteArray(0)).toString(Charsets.UTF_8)
            "hm_smi_available" in body || ("\"npu\"" in body && "status" in body) || body.contains("tiiny", true)
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        false
    }

    /** The Tiinys found, as addresses ("http://192.168.x.y"); [step] gets how far the search is (0–1). */
    suspend fun find(ctx: Context, step: (Float) -> Unit): List<String> = withContext(Dispatchers.IO) {
        val (b, prefix) = myNet(ctx) ?: throw MediaException("Connect the phone to your home Wi-Fi first.")
        if (prefix < 22) throw MediaException("This network is too large to search; type the Tiiny's address instead.")
        val base = "${b[0]}.${b[1]}.${b[2]}."
        val gate = Semaphore(48)
        var done = 0
        coroutineScope {
            (1..254).map { n ->
                async {
                    gate.withPermit {
                        val ip = base + n
                        val hit = if (n != b[3] && isTiiny(ip)) "http://$ip" else null
                        synchronized(this@Discover) { done++; step(done / 254f) }
                        hit
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}

package app.tiinyremote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL

/**
 * Controls the official WireGuard app's tunnels through the intents it offers to other apps
 * (SET_TUNNEL_UP / SET_TUNNEL_DOWN with the tunnel's name). WireGuard only obeys when
 * "Allow remote control apps" is on in its settings, and it gives no answer, so callers check
 * afterwards whether a VPN came up or went down.
 */
object Vpn {
    const val PACKAGE = "com.wireguard.android"
    const val PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS"

    fun installed(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun permitted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun setTunnel(ctx: Context, tunnel: String, up: Boolean) {
        val action = if (up) "com.wireguard.android.action.SET_TUNNEL_UP" else "com.wireguard.android.action.SET_TUNNEL_DOWN"
        val intent = Intent(action)
            .setPackage(PACKAGE)
            .putExtra("tunnel", tunnel.trim())
            // Deliver promptly even when Android has put WireGuard to sleep in the background.
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        ctx.sendBroadcast(intent)
    }

    fun openApp(ctx: Context) {
        ctx.packageManager.getLaunchIntentForPackage(PACKAGE)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(it)
        }
    }

    /**
     * The addresses of every VPN on the phone right now ("10.8.0.3"…). Android runs one VPN at a time, so this tells
     * WireGuard from another app's VPN (DuckDuckGo's App Tracking Protection, say) once WireGuard's address is known.
     */
    @Suppress("DEPRECATION")
    fun addresses(ctx: Context): Set<String> {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return emptySet()
        return cm.allNetworks.filter { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            .flatMap { n -> cm.getLinkProperties(n)?.linkAddresses.orEmpty().mapNotNull { it.address?.hostAddress?.substringBefore('%') } }
            .toSet()
    }

    /**
     * Is WireGuard on: a VPN with WireGuard's address ([own]); while that isn't known yet, any VPN except one already
     * found not to be WireGuard ([others]: it stayed when WireGuard was asked to go off).
     */
    fun wireGuardOn(ctx: Context, own: Set<String>, others: Set<String> = emptySet()): Boolean {
        val now = addresses(ctx)
        return if (own.isNotEmpty()) now.any { it in own } else now.isNotEmpty() && !now.all { it in others } || (now.isEmpty() && active(ctx))
    }

    /** Is any VPN active on the phone right now (WireGuard's or another one)? */
    @Suppress("DEPRECATION")
    fun active(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    /**
     * Is the phone on the Tiiny's home network? True when a Wi-Fi (or Ethernet) network the phone is on has the
     * Tiiny's address in its range (e.g. phone 192.168.3.140/24, Tiiny 192.168.3.143). Works with the VPN on and
     * needs no location permission. Null when it can't tell (the Tiiny address is a name, not an IP).
     */
    @Suppress("DEPRECATION")
    fun onHomeNetwork(ctx: Context, tiinyUrl: String): Boolean? {
        val host = try {
            URL(TiinyApi.normalize(tiinyUrl)).host
        } catch (e: Exception) {
            return null
        }
        if (!Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(host)) return null
        val target = InetAddress.getByName(host) as? Inet4Address ?: return null
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) continue
            val lp = cm.getLinkProperties(n) ?: continue
            for (la in lp.linkAddresses) {
                val a = la.address as? Inet4Address ?: continue
                if (sameSubnet(a, target, la.prefixLength)) return true
            }
        }
        return false
    }

    private fun sameSubnet(a: Inet4Address, b: Inet4Address, prefix: Int): Boolean {
        if (prefix !in 1..32) return false
        val x = toInt(a)
        val y = toInt(b)
        val mask = if (prefix == 32) -1 else (-1 shl (32 - prefix))
        return (x and mask) == (y and mask)
    }

    private fun toInt(a: Inet4Address): Int = a.address.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }

    /** Brings Tiiny Remote back to the front after WireGuard was opened to wake it. */
    fun backToApp(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (e: Exception) {
        }
    }
}

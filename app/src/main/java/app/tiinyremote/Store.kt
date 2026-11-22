package app.tiinyremote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Runs `tiiny login` on the NAS. The PATH line covers a session that hasn't read ~/.profile. */
const val DEFAULT_UNLOCK_COMMAND =
    ". ~/.profile >/dev/null 2>&1; export PATH=\"\$HOME/.local/bin:\$PATH\"; tiiny login"

data class AppSettings(
    val tiinyUrl: String = "",
    val apiKey: String = "",
    val nasHost: String = "",
    val nasPort: Int = 22,
    val nasUser: String = "",
    val nasPassword: String = "",
    val unlockCommand: String = DEFAULT_UNLOCK_COMMAND,
    val autoUnlock: Boolean = true,
    /** The Tiiny's own password (the one "tiiny login" asks for), used to unlock it directly. */
    val tiinyPassword: String = "",
    /** The WireGuard tunnel's name, exactly as shown in the WireGuard app. */
    val wgTunnel: String = "",
    val autoVpn: Boolean = false,
    val vpnOffAtHome: Boolean = true,
    /** The user's SearXNG (e.g. on the NAS), for the chat's web search. */
    val searxUrl: String = "",
    /** auto: SearXNG when reachable, else the phone searches; searx: only SearXNG; phone: never SearXNG. */
    val searchMode: String = "auto",
    /** Optional Tavily API key (free tier) for better searches from the phone. */
    val tavilyKey: String = "",
    /** Display: layout (auto, square, tall), overall size and text size, and whether Enter sends a chat message. */
    val layout: String = "auto",
    val uiScale: String = "1.0",
    val textScale: String = "1.0",
    val enterSends: Boolean = true,
    /** A small, fast chat model kept loaded beside the main one: summarises pages, names and condenses chats, runs the helpers. */
    val helperModel: String = "",
    /** Keep working while the app is in the background (a foreground service while a job runs). */
    val keepRunning: Boolean = false,
    /** A notification when a job, chat answer, speed test or download finishes while the app isn't on screen. */
    val notifyDone: Boolean = false,
    /** Labels as words, pictograms or both ("words", "icons", "both"). */
    val labels: String = "both",
    /** Look for a newer Tiiny Remote once a day (GitHub Releases) and notify. */
    val dailyUpdateCheck: Boolean = false,
    /** How many times the speed test repeats its measurements (1, or 3–5 for averaging with outliers left out). */
    val speedRuns: Int = 3,
    /** Auto thinking asks the helper how deep to think (answers start a moment later) instead of the app's own rule. */
    val autoAsksHelper: Boolean = false,
    /** Stay connected in the background (a silent notification): status, auto-unlock and the Tiiny checked every minute. */
    val stayConnected: Boolean = false,
) {
    val vpnConfigured get() = wgTunnel.isNotBlank()
    val tiinyConfigured get() = tiinyUrl.isNotBlank() && apiKey.isNotBlank()
    val directConfigured get() = tiinyUrl.isNotBlank() && tiinyPassword.isNotBlank()
    val nasConfigured get() = nasHost.isNotBlank() && nasUser.isNotBlank() && nasPassword.isNotBlank()
}

/** Settings in app-private storage; the API key and NAS password are encrypted with a key kept in the Android Keystore. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tiiny_remote", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        tiinyUrl = prefs.getString("tiiny_url", "") ?: "",
        apiKey = Crypto.decrypt(prefs.getString("api_key", null)) ?: "",
        nasHost = prefs.getString("nas_host", "") ?: "",
        nasPort = prefs.getInt("nas_port", 22),
        nasUser = prefs.getString("nas_user", "") ?: "",
        nasPassword = Crypto.decrypt(prefs.getString("nas_password", null)) ?: "",
        unlockCommand = prefs.getString("unlock_command", null)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_UNLOCK_COMMAND,
        autoUnlock = prefs.getBoolean("auto_unlock", true),
        tiinyPassword = Crypto.decrypt(prefs.getString("tiiny_password", null)) ?: "",
        wgTunnel = prefs.getString("wg_tunnel", "") ?: "",
        autoVpn = prefs.getBoolean("auto_vpn", false),
        vpnOffAtHome = prefs.getBoolean("vpn_off_at_home", true),
        searxUrl = prefs.getString("searx_url", "") ?: "",
        searchMode = prefs.getString("search_mode", "auto") ?: "auto",
        tavilyKey = Crypto.decrypt(prefs.getString("tavily_key", null)) ?: "",
        layout = prefs.getString("layout", "auto") ?: "auto",
        uiScale = prefs.getString("ui_scale", "1.0") ?: "1.0",
        textScale = prefs.getString("text_scale", "1.0") ?: "1.0",
        enterSends = prefs.getBoolean("enter_sends", true),
        helperModel = prefs.getString("helper_model", "") ?: "",
        keepRunning = prefs.getBoolean("keep_running", false),
        notifyDone = prefs.getBoolean("notify_done", false),
        labels = prefs.getString("labels", "both") ?: "both",
        dailyUpdateCheck = prefs.getBoolean("daily_update_check", false),
        speedRuns = prefs.getInt("speed_runs", 3).coerceIn(1, 5),
        autoAsksHelper = prefs.getBoolean("auto_asks_helper", false),
        stayConnected = prefs.getBoolean("stay_connected", false),
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("tiiny_url", s.tiinyUrl.trim())
            .putString("api_key", Crypto.encrypt(s.apiKey.trim()))
            .putString("nas_host", s.nasHost.trim())
            .putInt("nas_port", s.nasPort)
            .putString("nas_user", s.nasUser.trim())
            .putString("nas_password", Crypto.encrypt(s.nasPassword))
            .putString("unlock_command", s.unlockCommand)
            .putBoolean("auto_unlock", s.autoUnlock)
            .putString("tiiny_password", Crypto.encrypt(s.tiinyPassword))
            .putString("wg_tunnel", s.wgTunnel.trim())
            .putBoolean("auto_vpn", s.autoVpn)
            .putBoolean("vpn_off_at_home", s.vpnOffAtHome)
            .putString("searx_url", s.searxUrl.trim())
            .putString("search_mode", s.searchMode)
            .putString("tavily_key", Crypto.encrypt(s.tavilyKey.trim()))
            .putString("layout", s.layout)
            .putString("ui_scale", s.uiScale)
            .putString("text_scale", s.textScale)
            .putBoolean("enter_sends", s.enterSends)
            .putString("helper_model", s.helperModel)
            .putBoolean("keep_running", s.keepRunning)
            .putBoolean("notify_done", s.notifyDone)
            .putString("labels", s.labels)
            .putBoolean("daily_update_check", s.dailyUpdateCheck)
            .putInt("speed_runs", s.speedRuns.coerceIn(1, 5))
            .putBoolean("auto_asks_helper", s.autoAsksHelper)
            .putBoolean("stay_connected", s.stayConnected)
            .apply()
    }

    /** The login key the Tiiny gave in exchange for its password (what "tiiny login" keeps), encrypted. */
    fun authKey(): String? = Crypto.decrypt(prefs.getString("auth_key", null))?.takeIf { it.isNotEmpty() }

    fun setAuthKey(key: String?) {
        prefs.edit().putString("auth_key", key?.let { Crypto.encrypt(it) }).apply()
    }

    /** Models the Tiiny lists but answers "not found" for when asked to load them. */
    fun unavailable(): Set<String> = prefs.getStringSet("unavailable_models", emptySet())?.toSet() ?: emptySet()

    fun setUnavailable(ids: Set<String>) {
        prefs.edit().putStringSet("unavailable_models", ids).apply()
    }

    /** The NAS's SSH host key as first seen (trust on first use), per host:port. */
    fun hostKey(id: String): String? = prefs.getString("hostkey_$id", null)

    fun setHostKey(id: String, value: String) {
        prefs.edit().putString("hostkey_$id", value).apply()
    }

    fun clearHostKeys() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("hostkey_") }.forEach { e.remove(it) }
        e.apply()
    }
}

internal object Crypto {
    private const val ALIAS = "tiiny_remote_secrets"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(c.iv) + ":" + b64(ct)
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrEmpty()) return null
        return try {
            val (iv, ct) = stored.split(":", limit = 2)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(iv)))
            String(c.doFinal(unb64(ct)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

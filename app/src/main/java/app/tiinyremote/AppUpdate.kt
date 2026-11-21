package app.tiinyremote

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** A release of Tiiny Remote on GitHub: its version, the .apk to download, and its page. */
data class AppRelease(val version: String, val apkUrl: String, val pageUrl: String, val sizeBytes: Long)

/**
 * Updates of the app itself, from this repository's GitHub Releases: the normal app follows the release marked
 * Latest, "Tiiny Remote Exp" the newest experimental pre-release. The browser downloads the update and Android
 * installs it only if it's signed with the same key as the installed app.
 */
object AppUpdate {
    private const val REPO = "JacopoSmo/tiiny-remote"
    private const val PREFS = "app_update"

    fun experimental(ctx: Context) = ctx.packageName.endsWith(".exp")

    fun installed(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Whether version `a` is newer than `b`, number by number (3.1.10 > 3.1.9; 3.1.0-exp.13 > 3.1.0-exp.12). */
    fun newer(a: String, b: String): Boolean {
        val x = Regex("\\d+").findAll(a).map { it.value.toLong() }.toList()
        val y = Regex("\\d+").findAll(b).map { it.value.toLong() }.toList()
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "TiinyRemote")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub answered HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    /** The newest release for this app's channel, or null if there's none. */
    fun latest(ctx: Context): AppRelease? {
        val exp = experimental(ctx)
        val list = if (exp) JSONArray(get("https://api.github.com/repos/$REPO/releases?per_page=30"))
        else JSONArray().put(JSONObject(get("https://api.github.com/repos/$REPO/releases/latest")))
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val tag = o.optString("tag_name")
            if (o.optBoolean("draft")) continue
            if (exp != (o.optBoolean("prerelease") && tag.startsWith("exp-"))) continue
            val assets = o.optJSONArray("assets") ?: continue
            val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: continue
            val url = apk.optString("browser_download_url")
            // Only this repository's own release files.
            if (!url.startsWith("https://github.com/$REPO/releases/download/")) continue
            return AppRelease(tag.removePrefix("exp-").removePrefix("v"), url, o.optString("html_url"), apk.optLong("size"))
        }
        return null
    }

    /** A newer release than the installed one, or null. Remembered for Settings. */
    fun check(ctx: Context): AppRelease? {
        val r = latest(ctx)?.takeIf { newer(it.version, installed(ctx)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("checked", System.currentTimeMillis())
            .putString("available", r?.version ?: "")
            .apply()
        return r
    }

    /** The newer version found by the last check, if any (it may have been installed since). */
    fun known(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("available", "")
        ?.takeIf { it.isNotBlank() && newer(it, installed(ctx)) }

    /**
     * Opens the release's .apk in the browser, which downloads it; you install it by opening the download. The app
     * doesn't install apps itself: that permission makes Play Protect warn about the app.
     */
    fun openDownload(ctx: Context, r: AppRelease) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Settings → "Check for updates once a day": a daily check while there's internet, or none. */
    fun schedule(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (on) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork("app-update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork("app-update-check")
        }
    }
}

/** The daily check: a notification when a newer version is out (once per version). */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val r = try {
            AppUpdate.check(app)
        } catch (e: Exception) {
            return Result.retry()
        } ?: return Result.success()
        val prefs = app.getSharedPreferences("app_update", Context.MODE_PRIVATE)
        if (prefs.getString("notified", "") != r.version) {
            Notifier.update(app, r.version)
            prefs.edit().putString("notified", r.version).apply()
        }
        return Result.success()
    }
}

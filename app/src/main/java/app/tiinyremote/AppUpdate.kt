package app.tiinyremote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** A release of Tiiny Remote on GitHub: its version, the .apk to download, and its page. */
data class AppRelease(val version: String, val apkUrl: String, val pageUrl: String, val sizeBytes: Long)

/**
 * Updates of the app itself, from this repository's GitHub Releases: the normal app follows the release marked
 * Latest, "Tiiny Remote Exp" the newest experimental pre-release. Android installs an update only if it's signed
 * with the same key as the installed app, so a tampered download can't be installed over it.
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

    /** Downloads the .apk into the app's cache (at most 300 MB), reporting the percent done. */
    fun download(ctx: Context, r: AppRelease, progress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // an older download is no use any more
        val out = File(dir, "TiinyRemote-${r.version.replace(Regex("[^0-9A-Za-z.-]"), "_")}.apk")
        val tmp = File(dir, out.name + ".part")
        val c = URL(r.apkUrl).openConnection() as HttpURLConnection // GitHub redirects to its file host (https)
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "TiinyRemote")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("The download answered HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
            var done = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        done += n
                        if (done > 300L * 1024 * 1024) throw IllegalStateException("The download is far bigger than the app: stopped.")
                        o.write(buf, 0, n)
                        if (total > 0) progress((done * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (!tmp.renameTo(out)) throw IllegalStateException("Couldn't save the download.")
        return out
    }

    /** Android asks once whether Tiiny Remote may install apps; until then this opens that setting. */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the downloaded .apk to Android's installer, which shows what it installs and asks you. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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

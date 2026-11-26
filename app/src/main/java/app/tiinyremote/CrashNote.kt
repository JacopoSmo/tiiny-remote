package app.tiinyremote

import android.content.Context
import java.io.File

/**
 * When the app crashes, the reason is written to a file before Android closes it; at the next start it goes into the
 * log ("[App] Tiiny Remote crashed last time…"), so a shared log says what happened. Redacted like every log line.
 */
object CrashNote {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val before = Thread.getDefaultUncaughtExceptionHandler()
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val frames = generateSequence(e) { it.cause }.take(3).joinToString("\ncaused by ") { t ->
                    t.javaClass.name + ": " + (t.message ?: "") + "\n" +
                        t.stackTrace.take(12).joinToString("\n") { "  at $it" }
                }
                file(app).writeText("$version · ${thread.name}\n$frames")
            }
            before?.uncaughtException(thread, e)
        }
    }

    /** Puts the last crash, if any, into the log once. */
    fun report(ctx: Context) {
        val f = file(ctx)
        if (!f.exists()) return
        val text = runCatching { f.readText() }.getOrNull()
        f.delete()
        if (!text.isNullOrBlank()) ApiLog.add("Tiiny Remote crashed last time (please send this log):\n" + ApiLog.redact(text).take(4000), "App")
    }
}

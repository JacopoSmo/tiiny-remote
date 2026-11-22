package app.tiinyremote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the app working while it's in the background (Settings → "Keep working in the background"): while a tool job,
 * a chat answer, the speed test or a model download is going on, a foreground service with a quiet notification keeps
 * Android from stopping the app. It ends by itself when nothing is left to do.
 */
object Background {
    /** The app is on screen (set by MainActivity): then "done" notifications aren't needed. */
    @Volatile
    var visible = false

    private val reasons = LinkedHashMap<String, String>()
    private var running = false

    /** What one part of the app is doing (`label`), or that it's done (null). */
    fun set(ctx: Context, key: String, label: String?) {
        val now = synchronized(this) {
            if (label == null) reasons.remove(key) else reasons[key] = label
            current()
        }
        if (now == lastApplied) return // nothing changed (the download check repeats every few seconds)
        apply(ctx.applicationContext, now)
    }

    /** The service ended (stopped, or Android ended it): the next job starts it again. */
    @Synchronized
    fun serviceGone() {
        running = false
    }

    /** After the setting changed: start or stop the service to match. */
    fun refresh(ctx: Context) = apply(ctx.applicationContext, synchronized(this) { current() })

    private fun current(): String? =
        reasons.values.firstOrNull()?.let { if (reasons.size > 1) "$it (and ${reasons.size - 1} more)" else it }

    @Volatile
    private var lastApplied: String? = null

    @Synchronized
    private fun apply(app: Context, label: String?) {
        lastApplied = label
        val keep = Store(app).load().keepRunning
        val intent = Intent(app, WorkService::class.java)
        if (label != null && keep) {
            if (!running) {
                try {
                    ContextCompat.startForegroundService(app, intent.putExtra("label", label))
                    running = true
                } catch (e: Exception) {
                    // Android refuses to start it while the app is in the background: the work goes on regardless.
                    ApiLog.add("Couldn't keep working in the background: ${e.message}")
                }
            } else {
                Notifier.updateWork(app, label)
            }
        } else if (running) {
            running = false
            try {
                // Through the service itself, so it has called startForeground before it stops (else Android crashes it).
                app.startService(intent.setAction(WorkService.STOP))
            } catch (e: Exception) {
                app.stopService(intent)
            }
        }
    }
}

/** The foreground service behind Background: a notification saying what's going on, nothing else. */
class WorkService : Service() {
    companion object {
        const val STOP = "app.tiinyremote.STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.work(this, intent?.getStringExtra("label") ?: "Working…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifier.WORK_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifier.WORK_ID, n)
        }
        if (intent?.action == STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Android 15 allows this kind of service 6 hours a day; when they're used up it must stop at once. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ApiLog.add("Android ended background work (its daily limit for it is used up); the app keeps working while open")
        stopSelf()
    }

    override fun onDestroy() {
        Background.serviceGone()
        super.onDestroy()
    }
}

/** Notifications: the quiet "working" one, and "done" ones (Settings → "Notify when a job is done"). */
object Notifier {
    const val WORK_ID = 1
    private const val WORK = "work"
    private const val DONE = "done"
    private val ids = AtomicInteger(100)

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(WORK, "Working in the background", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(DONE, "Finished jobs", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun allowed(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun work(ctx: Context, label: String): Notification {
        channels(ctx)
        return NotificationCompat.Builder(ctx, WORK)
            .setSmallIcon(R.drawable.ic_stat_tiiny)
            .setContentTitle("Tiiny Remote is working")
            .setContentText(label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun updateWork(ctx: Context, label: String) {
        if (allowed(ctx)) ctx.getSystemService(NotificationManager::class.java).notify(WORK_ID, work(ctx, label))
    }

    /** "Tiiny Remote x.y.z is out": from the daily update check (Settings), whatever the "done" setting says. */
    fun update(ctx: Context, version: String) {
        val app = ctx.applicationContext
        if (!allowed(app)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = NotificationCompat.Builder(app, "updates")
            .setSmallIcon(R.drawable.ic_stat_tiiny)
            .setContentTitle("Tiiny Remote $version is out")
            .setContentText("Open Settings in the app and tap Update app.")
            .setAutoCancel(true)
            .setContentIntent(openApp(app))
            .build()
        app.getSystemService(NotificationManager::class.java).notify(UPDATE_ID, n)
    }

    private const val UPDATE_ID = 2

    /** An automation's result (or failure): always, on its own channel, private on the lock screen; opens Automations. */
    fun automation(ctx: Context, run: AutoRun) {
        val app = ctx.applicationContext
        if (!allowed(app)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("automations", "Automations", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val title = if (run.ok) run.name else "${run.name} didn't work"
        val text = run.text.replace(Regex("[#*_`>]"), "").trim().take(1500)
        val open = PendingIntent.getActivity(
            app,
            3,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.OPEN, "automations")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, "automations")
            .setSmallIcon(R.drawable.ic_stat_tiiny)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(app, "automations").setSmallIcon(R.drawable.ic_stat_tiiny)
                    .setContentTitle("Tiiny Remote: an automation finished").build(),
            )
            .build()
        app.getSystemService(NotificationManager::class.java).notify(ids.incrementAndGet(), n)
    }

    /** "Done" notice, when that's turned on and the app isn't on screen (you'd see it there anyway). */
    fun done(ctx: Context, title: String, text: String) {
        val app = ctx.applicationContext
        if (Background.visible || !Store(app).load().notifyDone || !allowed(app)) return
        channels(app)
        val n = NotificationCompat.Builder(app, DONE)
            .setSmallIcon(R.drawable.ic_stat_tiiny)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(app))
            // On the lock screen only "Tiiny Remote: done": the text (a chat answer, for one) shows once unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(app, DONE)
                    .setSmallIcon(R.drawable.ic_stat_tiiny)
                    .setContentTitle("Tiiny Remote")
                    .setContentText("A job is done")
                    .build(),
            )
            .build()
        app.getSystemService(NotificationManager::class.java).notify(ids.incrementAndGet(), n)
    }
}

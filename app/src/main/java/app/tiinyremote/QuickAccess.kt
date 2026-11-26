package app.tiinyremote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews

/**
 * Quick ways in from outside the app: Quick Settings tiles, the home-screen widget and the icon's long-press menu
 * (res/xml/shortcuts.xml). Each opens the app at the right place via MainActivity.OPEN:
 * "dictate" (Chat, recording at once), "chat", "translate_clip" (Translate with what's copied), "gallery".
 */
object QuickAccess {
    fun intent(ctx: Context, open: String): Intent = Intent(ctx, MainActivity::class.java)
        .putExtra(MainActivity.OPEN, open)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun pending(ctx: Context, open: String, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, 100 + code, intent(ctx, open), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** Opens the app from a tile and closes the Quick Settings panel (Android 14 wants a PendingIntent for that). */
private fun TileService.openApp(open: String, code: Int) {
    if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(QuickAccess.pending(this, open, code))
    else @Suppress("DEPRECATION") startActivityAndCollapse(QuickAccess.intent(this, open))
}

/** Quick Settings tile "Ask Tiiny": Chat, recording a question at once. */
class AskTile : TileService() {
    override fun onClick() = openApp("dictate", 1)
}

/** Quick Settings tile "Translate copied": Translate, with the copied text already in. */
class TranslateTile : TileService() {
    override fun onClick() = openApp("translate_clip", 2)
}

/** The home-screen widget: Chat, Dictate and Translate. */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val v = RemoteViews(ctx.packageName, R.layout.widget_quick).apply {
            setOnClickPendingIntent(R.id.w_chat, QuickAccess.pending(ctx, "chat", 3))
            setOnClickPendingIntent(R.id.w_dictate, QuickAccess.pending(ctx, "dictate", 4))
            setOnClickPendingIntent(R.id.w_translate, QuickAccess.pending(ctx, "translate_clip", 5))
        }
        ids.forEach { mgr.updateAppWidget(it, v) }
    }
}

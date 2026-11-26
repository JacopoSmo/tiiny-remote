package app.tiinyremote

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    companion object {
        /** Extra naming the screen to open (from a notification): "automations". */
        const val OPEN = "open"
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(OPEN)?.let { Nav.request.value = it }
    }

    override fun onStart() {
        super.onStart()
        Background.visible = true
        AppLock.onStart(vm.state.value.settings)
    }

    override fun onStop() {
        Background.visible = false
        AppLock.onStop()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Backup.applySecrets(this) // after a restore: the backup's passwords, sealed again with this phone's key
        NasAgent.init(this) // which app this is for the NAS agent (normal or exp)
        // The daily update check (Settings) is kept scheduled, or cancelled, to match the setting.
        AppUpdate.schedule(this, Store(this).load().dailyUpdateCheck)
        // Scheduled automations stay queued (after an update, say); what's already waiting is kept.
        AutoScheduler.scheduleAll(this)
        intent?.getStringExtra(OPEN)?.let { Nav.request.value = it }
        // Check the Tiiny only while the app is on screen; stops in the background to save battery.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.statsLoop() }
                launch { vm.downloadLoop() }
                vm.pollLoop()
            }
        }
        setContent {
            val set = vm.state.collectAsState().value.settings
            AppTheme(set.theme, set.style, if (!set.designedFonts) "system" else if (set.oneFace) "one" else "pair", set.lineIcons, set.lineColour) {
                val locked by AppLock.locked.collectAsState()
                if (locked) LockScreen { AppLock.ask(this) } else AppRoot(vm)
            }
        }
    }
}

@Composable
fun AppTheme(theme: String = "auto", style: String = "wallpaper", faces: String = "system", lineIcons: Boolean = false, lineColour: Boolean = false, content: @Composable () -> Unit) {
    // Settings → Display → Mode: as the phone is set, or always dark / light.
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    // Settings → Display → Style: a designed look (Look.kt), else Android's colours from the wallpaper.
    val scheme = Look.colors(style, dark) ?: when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    // The phone's status and navigation bar icons follow the chosen mode too (dark icons on a light app), else
    // Light on a phone set to dark leaves white icons on a light bar.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = Look.typography(style, faces)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLineIcons provides lineIcons, LocalLineColour provides lineColour, content = content)
    }
}

/** The app lock's screen: nothing of the app shows until the phone says it's you. Asks at once, and on tap. */
@Composable
fun LockScreen(ask: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { ask() }
    androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Lock, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(48.dp))
            Text("Tiiny Remote is locked", style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.Button(onClick = ask) { Text("Unlock") }
        }
    }
}

/** A screen to open, asked from outside the UI (a notification): AppRoot goes there and clears it. */
object Nav {
    val request = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** Start dictating in Chat at once (tile, widget, shortcut). */
    val dictate = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** Put the copied text into Translate (tile, widget, shortcut). */
    val translateClip = kotlinx.coroutines.flow.MutableStateFlow(false)
}

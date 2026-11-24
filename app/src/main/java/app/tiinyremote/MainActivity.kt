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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
    }

    override fun onStop() {
        Background.visible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val theme = vm.state.collectAsState().value.settings.theme
            AppTheme(theme) { AppRoot(vm) }
        }
    }
}

@Composable
fun AppTheme(theme: String = "auto", content: @Composable () -> Unit) {
    // Settings → Display → Mode: as the phone is set, or always dark / light.
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A screen to open, asked from outside the UI (a notification): AppRoot goes there and clears it. */
object Nav {
    val request = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

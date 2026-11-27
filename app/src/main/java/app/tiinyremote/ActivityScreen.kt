@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.tiinyremote

import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import java.util.Locale

// The Activity page: the device's state, unlocking, and the log.

/** Colours of the log's kinds. */
internal fun logColor(type: String): Color = when (type) {
    "Device" -> Color(0xFF1E88E5)
    "Models" -> Color(0xFF3949AB)
    "Unlock" -> Color(0xFF8E24AA)
    "VPN" -> Color(0xFF00897B)
    "Chat" -> Color(0xFFF4511E)
    "Search" -> Color(0xFF43A047)
    "Create" -> Color(0xFFFFB300)
    "Automate" -> Color(0xFF6D4C41)
    "Code" -> Color(0xFF00ACC1)
    else -> Color.Gray
}

@Composable
internal fun UnlockScreen(s: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("log_ui", android.content.Context.MODE_PRIVATE) }
    val entries by ApiLog.entries.collectAsState()
    // Which kinds to show (empty = all), and "problems only"; remembered.
    var shown by remember { mutableStateOf(prefs.getStringSet("types", emptySet())!!.toSet()) }
    var problems by remember { mutableStateOf(prefs.getBoolean("problems", false)) }
    val list = entries.filter { (shown.isEmpty() || it.type in shown) && (!problems || it.error) }
    val compact = LocalUi.current.compact
    // Long-press picks entries: then Share and Copy send only those (an error with what came before and after it).
    var picked by remember { mutableStateOf(setOf<Long>()) }
    val pickedEntries = entries.filter { it.id in picked }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Logs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.unlock() }, enabled = !s.unlocking) {
                BtnLabel(if (s.unlocking) "Unlocking…" else "Unlock Tiiny", androidx.compose.material.icons.Icons.Filled.Lock)
            }
        }
        if (s.unlocking) LinearProgressIndicator(Modifier.fillMaxWidth())
        // Filter buttons, each in its kind's colour.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = shown.isEmpty(),
                onClick = { shown = emptySet(); prefs.edit().putStringSet("types", shown).apply() },
                label = { Text("All") },
            )
            for (t in ApiLog.TYPES) {
                val c = logColor(t)
                val n = entries.count { it.type == t }
                FilterChip(
                    selected = t in shown,
                    onClick = {
                        shown = if (t in shown) shown - t else shown + t
                        prefs.edit().putStringSet("types", shown).apply()
                    },
                    label = { Text(if (n > 0) "$t $n" else t) },
                    leadingIcon = { Box(Modifier.size(10.dp).background(c, CircleShape)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.25f)),
                )
            }
            FilterChip(
                selected = problems,
                onClick = { problems = !problems; prefs.edit().putBoolean("problems", problems).apply() },
                label = { Text("⚠ Problems only") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer),
            )
        }
        if (pickedEntries.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${pickedEntries.size} chosen",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { shareLog(ctx, s, picked = pickedEntries) }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(buildReport(ctx, s, picked = pickedEntries))); picked = emptySet() }) {
                BtnLabel("Copy", symbol = "⧉")
            }
            TextButton(onClick = { picked = emptySet() }) { BtnLabel("Cancel", androidx.compose.material.icons.Icons.Filled.Close) }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${list.size} entr${if (list.size == 1) "y" else "ies"}" + if (shown.isNotEmpty() || problems) " shown" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // The Tiiny's own request statistics, added to the log as one entry.
            TextButton(onClick = { vm.logTiinyStats() }, enabled = s.device == DeviceState.ONLINE) { BtnLabel("Tiiny stats", symbol = "📊") }
            TextButton(onClick = { shareLog(ctx, s, shown.takeIf { it.isNotEmpty() }, problems) }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
            DangerButton("Clear", onClick = { vm.clearLog() }, icon = androidx.compose.material.icons.Icons.Filled.Clear)
        }
        Text(
            if (pickedEntries.isNotEmpty()) "Tap entries to add or remove them. Share and Copy send only the chosen ones."
            else "Share sends what's shown, without keys, passwords or email addresses. Long-press an entry to choose just some. " +
                "Tiiny stats adds what the Tiiny itself counted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (list.isEmpty()) item { Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium) }
            items(list, key = { it.id }) { e ->
                val k = e.id
                LogRow(
                    e,
                    chosen = k in picked,
                    choosing = picked.isNotEmpty(),
                    onChoose = { picked = if (k in picked) picked - k else picked + k },
                )
            }
        }
    }
}

/** One log line: a bar in its kind's colour, time and kind, and the text (tap to see all of a long one). */
@Composable
internal fun LogRow(e: LogEntry, chosen: Boolean, choosing: Boolean, onChoose: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = logColor(e.type)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                when {
                    chosen -> MaterialTheme.colorScheme.primaryContainer
                    e.error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
            )
            // Tap opens a long entry (or, while choosing, adds or removes it); long-press starts choosing.
            .combinedClickable(onClick = { if (choosing) onChoose() else open = !open }, onLongClick = onChoose),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(c))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.time, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(e.type, style = MaterialTheme.typography.labelSmall, color = c)
                if (chosen) {
                    Spacer(Modifier.width(6.dp))
                    Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (e.error) {
                    Spacer(Modifier.width(6.dp))
                    Text("⚠", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                e.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

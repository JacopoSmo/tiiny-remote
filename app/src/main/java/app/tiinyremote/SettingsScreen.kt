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

// The Settings page, its pictogram legend and the About dialog.

/** The ⓘ beside Labels: what each pictogram means. */
@Composable
internal fun PictogramLegend(onClose: () -> Unit) {
    // A Material icon or a text symbol, as the buttons use them.
    val signs = listOf<Pair<Any, String>>(
        Icons.Filled.Menu to "Sidebar: all pages",
        Icons.AutoMirrored.Filled.ArrowBack to "Back to where you were",
        "?" to "Help for this page",
        Icons.Filled.PlayArrow to "Load a model, resume a download",
        "■" to "Unload a model; Stop (it asks first)",
        "⤓" to "Download, save to the phone",
        "⟳" to "Update a model",
        "⏸" to "Pause a download",
        Icons.Filled.Close to "Cancel, remove, close",
        Icons.Filled.Edit to "Edit, rename; in the chat: improve your message",
        Icons.Filled.Check to "Save",
        Icons.Filled.Share to "Share",
        "⧉" to "Copy",
        Icons.AutoMirrored.Filled.Send to "Send",
        Icons.Filled.Search to "Search",
        Icons.Filled.MoreVert to "More actions (Code: the project's)",
        Icons.Filled.Info to "About this, what it can do",
        PowerIcon to "Power off the Tiiny; in the sidebar: shut the app down",
        Icons.Filled.Refresh to "Try again, check again, restart the Tiiny",
        Icons.Filled.Lock to "Unlock the Tiiny",
        Icons.Filled.Settings to "Open Settings",
        "▶" to "Run an automation now; Code: preview a web page",
        "🔌" to "Test a connection (email, NAS agent)",
        Icons.Filled.Delete to "Delete",
        "📊" to "Statistics: the Tiiny's own request counts (Activity), speed and quality tests, chat speeds and waits (Test)",
        "🎓" to "Test: the quality test (and Student mode in Chat)",
        "⚡" to "The fast helper model (Test), switched on or off in the chat's options",
        "🌙" to "Tonight: into the NAS's night queue (Settings → Background efficiency tools)",
        "🕘" to "Load ahead: the model you usually use around now",
        "⚠" to "A problem, or an NPU fault (Test → NPU faults)",
        HistoryIcon to "History: earlier chats, past speed tests",
        Icons.Filled.Add to "New chat; beside the message: attach an image or a file",
        MicIcon to "Dictate a message (needs a speech-to-text model)",
        SaveIcon to "Chat: green, chats are saved; red, incognito",
        "🧭" to "Models: help me choose a model (asks the chat)",
        "👍" to "Recommended model (Models → 👍: the whole set, and why)",
        "🧊" to "A 3D model: opens in the app's 3D viewer",
        "🎬" to "A video",
        "↓" to "Jump to the newest message",
        "✨" to "Start: what you can do, as tasks",
        "🔎" to "Find (Settings: the Tiiny on your Wi-Fi)",
        "📦" to "Back up now (Settings → Privacy and backup)",
        "🔐" to "Forget the NAS agent's certificate (Connectors)",
        "🗃" to "Copies the Tiiny keeps of texts and chats (Chat's history, Create, Gallery)",
        "↩" to "Undo a delete (the bar at the bottom)",
    )
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text("What the pictograms mean") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Models", style = MaterialTheme.typography.titleMedium)
                val meaning = mapOf(
                    "Chat" to "Chat model",
                    "Thinks" to "Can think before answering (can be switched off); in the chat: thinking on (how deep: the Thinking slider)",
                    "Always thinks" to "Always thinks before answering",
                    "Makes things" to "Can use the chat's tools (images, speech, songs…); in the chat: tools on",
                    "Sees images" to "Understands images you attach",
                    "MoE" to "Mixture of experts: writes much faster than its size suggests",
                    "Main chat model" to "The chat's main model (when a helper is loaded)",
                    "Helper" to "The fast helper model",
                    "working" to "Working on something right now",
                    "Web search" to "Web search: in the chat (on), and for agents",
                    "Suggestions" to "Chat: follow-up suggestions on",
                    "Socratic" to "Chat: Socratic mode (guides with questions)",
                    "Student" to "Chat: Student mode (teaches step by step)",
                    "Translates" to "Can translate (every chat model); also the Translate page",
                    "Coding" to "Good at code (made for it, or 20B parameters or more); also Models → Types → Coding",
                    "Automate" to "Automate: agents and processes",
                )
                for ((word, what) in meaning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignText(TAG_ICONS[word] ?: "", modifier = Modifier.width(36.dp))
                        Text(what, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Buttons", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                for ((sign, what) in signs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(36.dp)) {
                            if (sign is androidx.compose.ui.graphics.vector.ImageVector) Icon(sign, null, Modifier.size(18.dp)) else Text(sign.toString())
                        }
                        Text(what, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Create and Gallery", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                for (c in CATEGORIES) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.icon, modifier = Modifier.width(36.dp))
                        SignText(c.label + ": " + c.tools.joinToString(", ") { t -> CHOICES[t]?.let { (i, w) -> if (i.isEmpty()) w else "$i $w" } ?: t },
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Model types (Models → Types)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                for (t in listOf("ASR", "Image-Text-to-Text", "Image-to-3D", "Image-to-Text", "Image-to-Video", "Music Generation",
                    "Text Classification", "Text Embedding", "Text Generation", "Text Reranking", "Text-to-Image", "Text-to-Speech", "Text-to-Video")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignText(typeIcon(t), modifier = Modifier.width(36.dp))
                        Text(t, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Pages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                for ((icon, what) in listOf(GalleryIcon to "Gallery: everything made", TranslateIcon to "Translate: text, photos, speech",
                    AutomationIcon to "Automate: $SIGN_AGENT agents, $SIGN_PROCESS processes", CodeIcon to "Code: 💬 ask, 🛠 build, 🎓 learn")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(36.dp)) { Icon(icon, null, Modifier.size(18.dp)) }
                        SignText(what, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Others: ● loaded, ▾ opens a menu, ★ favourite, 📁 Code's projects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}


@Composable
internal fun SettingsScreen(s: UiState, vm: MainViewModel, st: StudioViewModel) {
    val cur = s.settings
    // Display changes save at once; they shouldn't reset what is being typed in the other fields.
    val formKey = cur.copy(layout = "", uiScale = "", textScale = "", enterSends = true, keepRunning = false, notifyDone = false, labels = "", helperModel = "", dailyUpdateCheck = false, speedRuns = 1, autoAsksHelper = false, helperOn = true, nightShift = false, nightFrom = 1, nightTo = 6, loadAhead = "", stayConnected = false, keepAsr = false, keepOcr = false, keepEmbed = false, keepRerank = false, keepTts = false, startScreen = "", navOrder = "", theme = "", style = "", lineIcons = false, lineColour = false, designedFonts = false, oneFace = false, quietStatus = false, showRecs = true, appLock = false, lockAfter = 0)
    var url by remember(formKey) { mutableStateOf(cur.tiinyUrl) }
    var key by remember(formKey) { mutableStateOf(cur.apiKey) }
    var host by remember(formKey) { mutableStateOf(cur.nasHost) }
    var port by remember(formKey) { mutableStateOf(cur.nasPort.toString()) }
    var user by remember(formKey) { mutableStateOf(cur.nasUser) }
    var pw by remember(formKey) { mutableStateOf(cur.nasPassword) }
    var auto by remember(formKey) { mutableStateOf(cur.autoUnlock) }
    var cmd by remember(formKey) { mutableStateOf(cur.unlockCommand) }
    var tpw by remember(formKey) { mutableStateOf(cur.tiinyPassword) }
    var tunnel by remember(formKey) { mutableStateOf(cur.wgTunnel) }
    var autoVpn by remember(formKey) { mutableStateOf(cur.autoVpn) }
    var offHome by remember(formKey) { mutableStateOf(cur.vpnOffAtHome) }
    var searx by remember(formKey) { mutableStateOf(cur.searxUrl) }
    var searxTest by remember { mutableStateOf<String?>(null) }
    var searchMode by remember(formKey) { mutableStateOf(cur.searchMode) }
    var tavily by remember(formKey) { mutableStateOf(cur.tavilyKey) }
    val scope = rememberCoroutineScope()

    // From the saved settings: fields this form doesn't show (speed test runs, helper options…) are kept as they are.
    fun current() = cur.copy(
        tiinyUrl = url.trim(),
        apiKey = key.trim(),
        nasHost = host.trim(),
        nasPort = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 22,
        nasUser = user.trim(),
        nasPassword = pw,
        unlockCommand = cmd.ifBlank { DEFAULT_UNLOCK_COMMAND },
        autoUnlock = auto,
        tiinyPassword = tpw,
        wgTunnel = tunnel.trim(),
        autoVpn = autoVpn,
        vpnOffAtHome = offHome,
        searxUrl = searx.trim(),
        searchMode = searchMode,
        tavilyKey = tavily.trim(),
        helperModel = cur.helperModel,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Display", style = MaterialTheme.typography.titleMedium)
        Text("Changes apply right away.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingDropdown("Layout", LAYOUTS, cur.layout) { vm.setDisplay(cur.copy(layout = it)) }
        Text(
            "Square · compact: sections in the ☰ sidebar, a slim top bar and the chat options on one line: more room for content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The look: colours, pictograms, typefaces and status bar, each on its own (alphabetical after Mode's light/dark).
        SettingDropdown("Style", Look.STYLES, cur.style) { vm.setDisplay(cur.copy(style = it)) }
        SettingDropdown("Mode", THEMES, cur.theme) { vm.setDisplay(cur.copy(theme = it)) }
        SettingDropdown(
            "Pictograms",
            listOf("emoji" to "Colourful (the phone's emoji)", "lines_colour" to "Line icons (colourful)", "lines_style" to "Line icons (the style's colour)"),
            if (!cur.lineIcons) "emoji" else if (cur.lineColour) "lines_colour" else "lines_style",
        ) { vm.setDisplay(cur.copy(lineIcons = it != "emoji", lineColour = it == "lines_colour")) }
        SettingDropdown("Recommended models", listOf("shown" to "Shown (👍 in the model menus)", "hidden" to "Hidden"), if (cur.showRecs) "shown" else "hidden") {
            vm.setDisplay(cur.copy(showRecs = it == "shown"))
        }
        SettingDropdown("Status bar", listOf("standard" to "Standard (coloured)", "quiet" to "Quiet (one thin line)"), if (cur.quietStatus) "quiet" else "standard") {
            vm.setDisplay(cur.copy(quietStatus = it == "quiet"))
        }
        SettingDropdown("Typefaces", Look.TYPEFACES, if (!cur.designedFonts) "system" else if (cur.oneFace) "one" else "pair") {
            vm.setDisplay(cur.copy(designedFonts = it != "system", oneFace = it == "one"))
        }
        SettingDropdown("Size of everything", UI_SCALES, cur.uiScale) { vm.setDisplay(cur.copy(uiScale = it)) }
        SettingDropdown("Text size", TEXT_SCALES, cur.textScale) { vm.setDisplay(cur.copy(textScale = it)) }
        var legend by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SettingDropdown("Labels", LABEL_STYLES, cur.labels) { vm.setDisplay(cur.copy(labels = it)) }
            }
            IconButton(onClick = { legend = true }) {
                Icon(Icons.Filled.Info, contentDescription = "What the pictograms mean", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (legend) PictogramLegend { legend = false }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = cur.enterSends, onCheckedChange = { vm.setDisplay(cur.copy(enterSends = it)) })
            Spacer(Modifier.width(10.dp))
            Text("Enter key sends messages in Chat and Code (Shift+Enter for a new line), for the hardware keyboard")
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Tiiny", style = MaterialTheme.typography.titleMedium)
        // Find the Tiiny on the home Wi-Fi (🔎, left of the address) instead of typing its address.
        val findCtx = LocalContext.current
        val findScope = rememberCoroutineScope()
        var finding by remember { mutableStateOf<Float?>(null) }
        var found by remember { mutableStateOf<List<String>?>(null) }
        var findNote by remember { mutableStateOf<String?>(null) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = finding == null, onClick = {
                findNote = null; found = null; finding = 0f
                findScope.launch {
                    try {
                        val hits = Discover.find(findCtx) { finding = it }
                        val maybe = Discover.maybe
                        found = hits.ifEmpty { maybe }
                        when (hits.size) {
                            0 -> findNote = when (maybe.size) {
                                0 -> "No Tiiny answered on this Wi-Fi. Is it on, and on the same network?"
                                1 -> { url = maybe[0]; "Maybe the Tiiny: ${maybe[0]} (it wants the API key; filled in, Save to try it)." }
                                else -> "These may be the Tiiny (they want the API key): pick one, then Save to try it."
                            }
                            1 -> { url = hits[0]; findNote = "Found: ${hits[0]} (filled in; Save to keep it)." }
                            else -> findNote = "Several found: pick one."
                        }
                    } catch (e: Exception) {
                        findNote = e.message
                    } finally {
                        finding = null
                    }
                }
            }) { Text("🔎", fontSize = 20.sp, modifier = Modifier.semantics { contentDescription = "Find the Tiiny on my Wi-Fi" }) }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Tiiny address") },
                placeholder = { Text("http://192.168.x.x") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        finding?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        findNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        found?.takeIf { it.size > 1 }?.let { hits -> Row { for (h in hits) TextButton(onClick = { url = h }) { Text(h.removePrefix("http://")) } } }
        SecretField(key, { key = it }, "API key (TiinyOS → Settings → API Key)")
        // Restart and power off: in the Tiiny's own section, beside its address and key.
        var powerAsk by remember { mutableStateOf<Boolean?>(null) } // true: restart, false: power off
        val tiinyOn = s.device == DeviceState.ONLINE
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { powerAsk = true }, enabled = tiinyOn) { BtnLabel("Restart", Icons.Filled.Refresh) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { powerAsk = false },
                enabled = tiinyOn,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { BtnLabel("Power off", PowerIcon) }
        }
        Text(
            "Both unload the models and stop what's running. After a restart it's locked until unlocked (automatic with the saved password); after power off, its power button starts it again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        powerAsk?.let { restart ->
            val running = s.models.any { it.busy } || s.downloads.isNotEmpty()
            ScaledAlertDialog(
                onDismissRequest = { powerAsk = null },
                title = { Text(if (restart) "Restart the Tiiny?" else "Power off the Tiiny?") },
                text = {
                    Text(
                        (if (running) "Something is still running on it (a model at work or a download): it will be stopped. " else "") +
                            if (restart) "The loaded models are unloaded. It's back in a few minutes, locked until unlocked " +
                                "(the app does that by itself with the saved password)."
                            else "The loaded models are unloaded and it switches off in about 10 seconds. " +
                                "To switch it on again, press the power button on the Tiiny.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.power(restart); powerAsk = null }) {
                        Text(if (restart) "Restart" else "Power off", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { powerAsk = null }) { Text("Keep it on") } },
            )
        }

        // Everything about reaching the Tiiny together: its address and power, unlocking it, WireGuard from away.
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Unlocking after a restart", style = MaterialTheme.typography.titleMedium)
        SecretField(tpw, { tpw = it }, "Tiiny password (the one tiiny login asks for)")
        Text(
            "Used to unlock the Tiiny directly. It goes only to the Tiiny and is stored encrypted on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = auto, onCheckedChange = { auto = it })
            Spacer(Modifier.width(10.dp))
            Text("Unlock automatically when the Tiiny is locked")
        }
        DangerButton("Forget saved Tiiny login key", onClick = { vm.forgetTiinyLogin() })
        // The NAS fallback belongs to unlocking: right under it.
        Text("NAS, as a fallback for unlocking", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("NAS address") },
            placeholder = { Text("192.168.x.x") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = port,
            onValueChange = { v -> port = v.filter { it.isDigit() }.take(5) },
            label = { Text("SSH port") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("NAS username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(pw, { pw = it }, "NAS password")
        OutlinedTextField(
            value = cmd,
            onValueChange = { cmd = it },
            label = { Text("Unlock command (advanced)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        TextButton(onClick = { cmd = DEFAULT_UNLOCK_COMMAND }) { Text("Reset unlock command") }

        Button(onClick = { vm.saveSettings(current()) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        OutlinedButton(
            onClick = {
                vm.saveSettings(current())
                vm.testNas()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save and test NAS login") }
        DangerButton("Forget saved NAS key", onClick = { vm.forgetHostKey() })

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("WireGuard VPN", style = MaterialTheme.typography.titleMedium)
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.permissionResult(granted)
        }
        OutlinedTextField(
            value = tunnel,
            onValueChange = { tunnel = it },
            label = { Text("Tunnel name, exactly as in the WireGuard app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "In the WireGuard app, turn on Settings → \"Allow remote control apps\" so this app can switch the " +
                "tunnel. Then allow it here once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                !s.vpn.wireguardInstalled -> Text("WireGuard app not found on this phone.", style = MaterialTheme.typography.bodyMedium)
                s.vpn.permitted -> Text("Control of WireGuard: allowed", style = MaterialTheme.typography.bodyMedium)
                else -> OutlinedButton(onClick = { permLauncher.launch(Vpn.PERMISSION) }) { Text("Allow controlling WireGuard") }
            }
            Spacer(Modifier.weight(1f))
            if (s.vpn.wireguardInstalled) TextButton(onClick = { vm.openWireGuard() }) { BtnLabel("Open WireGuard", androidx.compose.material.icons.Icons.AutoMirrored.Filled.ExitToApp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = autoVpn, onCheckedChange = { autoVpn = it })
            Spacer(Modifier.width(10.dp))
            Text("Switch the VPN on automatically when I'm away from home (or the Tiiny isn't reachable)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = offHome, onCheckedChange = { offHome = it }, enabled = autoVpn)
            Spacer(Modifier.width(10.dp))
            Text("…and off again when I'm back on my home network")
        }
        Text(
            "Home is recognised by your Wi-Fi having the Tiiny's address range (e.g. 192.168.3.x); no location " +
                "access is needed. Automatic switching runs while this app is open and pauses for 10 minutes after " +
                "you switch the VPN yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Web search", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = searx,
            onValueChange = { searx = it; searxTest = null },
            label = { Text("SearXNG address") },
            placeholder = { Text("http://192.168.x.x:8080 (the one RikkaHub uses)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Text(
            "Lets chat models search the web and read pages (\"Web search\" in the Chat tab). Pages are always opened by this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var searxGuide by remember { mutableStateOf(false) }
        TextButton(onClick = { searxGuide = true }) { Text("Set up SearXNG") }
        if (searxGuide) TerminalGuide(
            "SearXNG from the terminal", SEARX_STEPS,
            "Keep port 8888 on your home network (don't forward it on the router); away from home the app reaches it through WireGuard.",
        ) { searxGuide = false }
        Text("Search with", style = MaterialTheme.typography.labelLarge)
        for ((v, label) in listOf(
            "auto" to "Your SearXNG when reachable, else the phone (works anywhere)",
            "searx" to "Only your SearXNG (at home or on the VPN)",
            "phone" to "Only the phone (never the NAS)",
        )) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = searchMode == v, onClick = { searchMode = v; searxTest = null })
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (searchMode != "searx") {
            SecretField(tavily, { tavily = it; searxTest = null }, "Tavily API key (optional)")
            Text(
                "With a Tavily key (free: 1,000 searches a month at tavily.com) the phone searches there; without, Bing, DuckDuckGo, then Wikipedia. News: Google News and Bing News. The log shows which one answered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                searxTest = "Searching…"
                val cfg = current()
                scope.launch {
                    searxTest = try {
                        val found = withContext(Dispatchers.IO) { WebSearch.search(cfg, "Tiiny AI", 3) }
                        if (found.hits.isEmpty()) "Reachable, but no results came back." else "Works via ${found.via}: ${found.hits.size} results, e.g. ${found.hits.first().title}"
                    } catch (e: Exception) {
                        e.message ?: e.toString()
                    }
                }
            }) { Text("Test search") }
        }
        searxTest?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("In the background", style = MaterialTheme.typography.titleMedium)
        val bgCtx = LocalContext.current
        // Android 13+ asks before an app may show notifications: asked when you turn either option on.
        val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        fun askIfNeeded() {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(bgCtx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stay connected in the background", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "A silent notification keeps the app checking the Tiiny every minute, so it unlocks it after a restart even when closed. A little battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = cur.stayConnected, onCheckedChange = {
                vm.setStayConnected(it)
                if (it) askIfNeeded()
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep working in the background", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Jobs, answers, tests and downloads go on when you switch away or lock the phone (quiet notification until done).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = cur.keepRunning, onCheckedChange = {
                vm.setDisplay(cur.copy(keepRunning = it))
                if (it) askIfNeeded()
                Background.refresh(bgCtx)
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notify when a job is done", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "A notification when a creation, chat answer, speed test or model download finishes while the app " +
                        "isn't on screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = cur.notifyDone, onCheckedChange = {
                vm.setDisplay(cur.copy(notifyDone = it))
                if (it) askIfNeeded()
            })
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Always ready", style = MaterialTheme.typography.titleMedium)
        Text(
            "Small models kept loaded whenever there's room on the NPU, so they start at once instead of loading first. " +
                "Nothing is unloaded for them; they come back by themselves once there's room again. Unloading one yourself " +
                "switches it off here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // In the alphabetical order of their names.
        for ((label, on, kind) in listOf(
            Triple("🔗 Embedding (searches projects' files by meaning)", cur.keepEmbed) { m: ModelInfo -> m.isEmbedder },
            Triple("$SIGN_OCR OCR (text from photos)", cur.keepOcr) { m: ModelInfo -> m.isOcr },
            Triple("🗣 Read aloud (Read a text, the chat's speak)", cur.keepTts) { m: ModelInfo -> m.isSpeech },
            Triple("🥇 Reranker (sorts what projects' search found)", cur.keepRerank) { m: ModelInfo -> m.isReranker },
            Triple("👂 Speech-to-text (dictation, Translate speech, ASR)", cur.keepAsr) { m: ModelInfo -> m.isAsr },
        )) {
            val m = smallestOfKind(s.models.filter(kind))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SignText(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        m?.let { "${it.name} · ${Math.round(it.npuUsage ?: 0.0)}% of the NPU" + if (it.running) " · loaded" else "" }
                            ?: "No such model on the Tiiny (Models → Store).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = on, enabled = m != null || on, onCheckedChange = {
                    vm.setDisplay(when {
                        "Speech" in label -> cur.copy(keepAsr = it)
                        "OCR" in label -> cur.copy(keepOcr = it)
                        "Embedding" in label -> cur.copy(keepEmbed = it)
                        "Read aloud" in label -> cur.copy(keepTts = it)
                        else -> cur.copy(keepRerank = it)
                    })
                })
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Background efficiency tools", style = MaterialTheme.typography.titleMedium)
        BackgroundTools(s, vm, st)

        Text(
            "The log (with Share) is in the Activity tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        PrivacySection(vm, cur)

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("App updates", style = MaterialTheme.typography.titleMedium)
        val ctx = LocalContext.current
        val version = remember { AppUpdate.installed(ctx) }
        val updScope = rememberCoroutineScope()
        var release by remember { mutableStateOf<AppRelease?>(null) }
        var updBusy by remember { mutableStateOf<String?>(null) }
        var updNote by remember {
            mutableStateOf(AppUpdate.known(ctx)?.let { "${AppUpdate.appName(ctx)} $it is out (found by the daily check)." })
        }
        fun checkNow(then: ((AppRelease) -> Unit)? = null) {
            updBusy = "Checking…"
            updScope.launch {
                val r = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { AppUpdate.check(ctx) } }
                updBusy = null
                r.onSuccess {
                    release = it
                    updNote = if (it == null) "${AppUpdate.appName(ctx)} $version is the newest." else "${AppUpdate.appName(ctx)} ${it.version} is out."
                    if (it != null) then?.invoke(it)
                }.onFailure { updNote = "Couldn't check for updates: ${it.message}" }
            }
        }
        fun updateNow(r: AppRelease) {
            updNote = "Your browser downloads Tiiny Remote ${r.version}: open the downloaded file to install it. Your settings stay."
            try {
                AppUpdate.openDownload(ctx, r)
            } catch (e: Exception) {
                updNote = "Couldn't open the browser: ${e.message}"
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { checkNow() }, enabled = updBusy == null) { BtnLabel("Check for updates", Icons.Filled.Refresh) }
            Spacer(Modifier.width(8.dp))
            if (release != null || AppUpdate.known(ctx) != null) {
                Button(onClick = { release?.let { updateNow(it) } ?: checkNow { updateNow(it) } }, enabled = updBusy == null) {
                    BtnLabel("Update app", symbol = "⤓")
                }
            }
        }
        (updBusy ?: updNote)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check for updates once a day", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Looks for a newer ${if (AppUpdate.experimental(ctx)) "experimental version" else "version"} on GitHub " +
                        "and sends a notification when there is one. Updating is always your tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = cur.dailyUpdateCheck, onCheckedChange = {
                vm.setDisplay(cur.copy(dailyUpdateCheck = it))
                AppUpdate.schedule(ctx, it)
                if (it) askIfNeeded()
            })
        }
        var about by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${AppUpdate.appName(ctx)} $version · secrets are stored encrypted on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { Guides.request.value = -1 }) { Text("Welcome tour") }
            IconButton(onClick = { about = true }) {
                Icon(Icons.Filled.Info, contentDescription = "About this app", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (about) AboutDialog(version, cur) { about = false }
    }
}

internal const val REPO = "https://github.com/JacopoSmo/tiiny-remote"

/** The ⓘ beside the version: what the app is, where it lives, what it sends where, and what it's built with. */
@Composable
internal fun AboutDialog(version: String, settings: AppSettings, onClose: () -> Unit) {
    val ctx = LocalContext.current
    // What the Tiiny says about itself: TiinyOS and service versions, hardware (asked when About opens).
    var tiiny by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) { tiiny = withContext(kotlinx.coroutines.Dispatchers.IO) { TiinyInfo.about(settings) } }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val exp = AppUpdate.experimental(ctx)
    val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    val os = tiiny?.filter { it.first in setOf("TiinyOS", "Service version") }?.joinToString(" · ") { "${it.first} ${it.second}" }
    val info = "Tiiny Remote $version${if (exp) " (experimental)" else ""} · ${ctx.packageName} · $device" + (os?.ifBlank { null }?.let { " · $it" } ?: "")
    fun open(url: String) { runCatching { uri.openUri(url) } }
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text("About Tiiny Remote") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AboutBody("Controls your Tiiny AI Pocket Lab from your phone: models, chat with tools and web search, " +
                    "images, speech, music, video and 3D, status, and unlocking after a restart.")
                AboutHead("Version")
                AboutBody("$version${if (exp) " · experimental channel (separate app, newest features first)" else ""}\n${ctx.packageName}\n$device")
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(info)) }) {
                    BtnLabel("Copy for a bug report", symbol = "⧉")
                }
                AboutHead("Your Tiiny")
                AboutBody(
                    when {
                        tiiny == null -> "Asking the Tiiny…"
                        tiiny!!.isEmpty() -> "The Tiiny isn't reachable right now, or doesn't say."
                        else -> tiiny!!.joinToString("\n") { "${it.first}: ${it.second}" }
                    },
                )
                AboutHead("Links")
                AboutLink("Source code on GitHub") { open(REPO) }
                AboutLink("All versions and what's new") { open("$REPO/releases") }
                AboutLink("Report a problem or suggest an idea") { open("$REPO/issues") }
                AboutLink("Licence: MIT (free to use, change and share; no warranty)") { open("$REPO/blob/main/LICENSE") }
                AboutHead("Your data")
                AboutBody("• Talks only to your Tiiny (and your NAS, if you unlock over SSH), over your Wi-Fi or your WireGuard tunnel. No account, no ads, no tracking, no analytics.\n" +
                    "• Web search, when you turn it on, goes from the phone to the search service you chose (SearXNG, Tavily, Bing, DuckDuckGo or Wikipedia), and the pages the model opens are fetched by the phone.\n" +
                    "• The update check asks GitHub for the newest version; nothing about you is sent.\n" +
                    "• Passwords and keys are stored encrypted on this phone. Chats and results stay on the phone; the log hides keys, e-mail addresses and device IDs.")
                AboutHead("Good to know")
                AboutBody("• Not made by or affiliated with Tiiny AI. Tiiny and TiinyOS are theirs.\n" +
                    "• Installed from GitHub, not Google Play: updates come through Settings → App updates.\n" +
                    "• AI answers can be wrong. Check what matters, especially facts, figures and medical, legal or financial advice.")
                AboutHead("Built with")
                AboutBody("Kotlin and Jetpack Compose (Android), AndroidX WorkManager, Kotlin coroutines (Apache 2.0), JSch (BSD) for SSH, " +
                    "and JavaMail (EPL 2.0 / GPL 2.0 with Classpath Exception) for reading email. The NAS agent is Python's standard library only. " +
                    "Made by JacopoSmo, built with Claude (Anthropic), supported by his home Tiiny AI.")
            }
        },
        confirmButton = {},
    )
}

@Composable
internal fun AboutHead(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

/** A pictogram for each kind of model the Tiiny lists, the same as Create's; in Models → Types always with the name. */
internal fun typeIcon(type: String): String = when (type.lowercase()) {
    "asr", "automatic-speech-recognition", "speech-to-text" -> "👂"
    "image-text-to-text" -> "👁"
    "image-to-3d", "text-to-3d" -> "🧊"
    "image-to-text" -> SIGN_OCR
    "image-to-image" -> "🖌"
    "image-to-video" -> "🎞"
    "music generation", "text-to-audio", "audio generation" -> "🎵"
    "text classification" -> "🏷"
    "text embedding" -> "🔗"
    "text generation" -> "💬"
    "text reranking" -> "🥇"
    "text-to-image" -> "🖼"
    "text-to-speech" -> "🗣"
    "text-to-video" -> "🎬"
    "translation" -> "🌐"
    else -> "▫"
}

@Composable
internal fun AboutBody(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium)

@Composable
internal fun AboutLink(label: String, onClick: () -> Unit) = Text(
    label,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
)

internal val historyTime = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

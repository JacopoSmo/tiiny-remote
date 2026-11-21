@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.tiinyremote

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

private fun pct(v: Double?): String {
    if (v == null) return "?%"
    return if (v % 1.0 == 0.0) "${v.toInt()}%" else String.format(Locale.ROOT, "%.1f%%", v)
}

private fun fmtSec(s: Int): String =
    if (s >= 60) "${s / 60} min" + (if (s % 60 > 0) " ${s % 60} s" else "") else "$s s"

/** Everything useful for debugging in one text: status, the API log and the unlock log. No keys or passwords. */
private fun buildReport(ctx: Context, s: UiState, types: Set<String>? = null, errorsOnly: Boolean = false): String {
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    return buildString {
        append("Tiiny Remote ").append(version).append('\n')
        append("Status: ").append(s.device).append(' ').append(s.deviceMessage).append("\n\n")
        // The same choice as the Activity screen, so Share sends exactly what's shown.
        val shown = ApiLog.entries.value.filter { (types == null || it.type in types) && (!errorsOnly || it.error) }
        append("== Log (newest first)")
        if (types != null) append(": ").append(types.joinToString(", "))
        if (errorsOnly) append(", problems only")
        append(" ==\n")
        append(if (shown.isEmpty()) "(none)" else shown.joinToString("\n\n") { "${it.time}  [${it.type}] ${it.text}" })
    }
}

private fun shareLog(ctx: Context, s: UiState, types: Set<String>? = null, errorsOnly: Boolean = false) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Tiiny Remote log")
        putExtra(Intent.EXTRA_TEXT, buildReport(ctx, s, types, errorsOnly))
    }
    ctx.startActivity(Intent.createChooser(send, "Share log"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val st: StudioViewModel = viewModel()
    val job by st.job.collectAsState()
    val anyChatBusy by st.busyChats.collectAsState()
    val studioNote by st.note.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var toolId by rememberSaveable { mutableStateOf<String?>(null) }
    // Where you came from: ← and Android's back gesture go back through it; with nothing left, back closes the app.
    val history = remember { mutableStateListOf<Pair<Int, String?>>() }
    fun goTab(i: Int) {
        if (i == tab) return
        history.add(tab to toolId)
        if (history.size > 30) history.removeAt(0)
        tab = i
    }
    fun openToolNav(id: String?) {
        if (id == toolId) return
        if (id != null) history.add(tab to toolId)
        else if (history.lastOrNull() == (tab to null)) history.removeAt(history.size - 1) // "‹ Tools" = back
        toolId = id
    }
    val canGoBack = history.isNotEmpty() || (tab == 1 && toolId != null)
    fun back() {
        if (history.isNotEmpty()) {
            val (t, tool) = history.removeAt(history.size - 1)
            tab = t
            toolId = tool
        } else if (tab == 1 && toolId != null) toolId = null
    }
    BackHandler(enabled = canGoBack) { back() }
    val openTool by st.openTool.collectAsState()
    LaunchedEffect(openTool) {
        if (openTool != null) goTab(1) // "Edit this" from the chat or results goes to the Create tab
    }
    val snackbar = remember { SnackbarHostState() }

    // Keep the screen on while the Tiiny works on something, so Android doesn't stop the app mid-job.
    val view = LocalView.current
    val working = job?.running == true || anyChatBusy.isNotEmpty()
    DisposableEffect(working) {
        view.keepScreenOn = working
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(studioNote) {
        val msg = studioNote
        if (msg != null) {
            snackbar.showSnackbar(msg)
            st.noteShown()
        }
    }

    LaunchedEffect(s.toast) {
        val msg = s.toast
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.toastShown()
        }
    }

    // Display: compact on square screens (like the Titan 2) or when chosen; overall and text size from Settings.
    val conf = LocalConfiguration.current
    val compact = when (s.settings.layout) {
        "square" -> true
        "tall" -> false
        else -> isSquare(conf.screenWidthDp, conf.screenHeightDp)
    }
    val base = LocalDensity.current
    val uiScale = s.settings.uiScale.toFloatOrNull() ?: 1f
    val textScale = s.settings.textScale.toFloatOrNull() ?: 1f
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val chats by st.chatList.collectAsState()
    val current by st.currentChat.collectAsState()
    val busyChats by st.busyChats.collectAsState()
    var chatMenu by remember { mutableStateOf<ChatInfo?>(null) }
    var renaming by remember { mutableStateOf<ChatInfo?>(null) }
    val sections = listOf(
        Triple("Models", Icons.Filled.Home, 0),
        Triple("Chat", Icons.Filled.Send, 2),
        Triple("Create", Icons.Filled.Create, 1),
        Triple("Activity", Icons.Filled.List, 3),
        Triple("Settings", Icons.Filled.Settings, 4),
    )
    fun go(i: Int) {
        goTab(i)
        scope.launch { drawer.close() }
    }

    val appDensity = Density(base.density * uiScale, base.fontScale * textScale)
    CompositionLocalProvider(
        LocalDensity provides appDensity,
        LocalAppDensity provides appDensity,
        LocalUi provides UiPrefs(compact = compact, enterSends = s.settings.enterSends, labels = s.settings.labels),
    ) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(Modifier.widthIn(max = 300.dp)) {
                    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                        Text("Tiiny Remote", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        for ((label, icon, i) in sections) {
                            NavigationDrawerItem(
                                label = { Text(label) },
                                icon = { Icon(icon, contentDescription = null) },
                                selected = tab == i && i != 2,
                                onClick = { go(i) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Chats", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { st.newChat(); go(2) }) { Text("+ New chat") }
                        }
                        if (chats.isEmpty()) {
                            Text("No saved chats yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
                        }
                        for (c in chats) {
                            Box {
                                NavigationDrawerItem(
                                    label = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    badge = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (c.id in busyChats) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            IconButton(onClick = { chatMenu = c }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Filled.MoreVert, contentDescription = "Chat options")
                                            }
                                        }
                                    },
                                    selected = tab == 2 && c.id == current,
                                    onClick = { st.selectChat(c.id); go(2) },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                                ScaledDropdownMenu(expanded = chatMenu?.id == c.id, onDismissRequest = { chatMenu = null }) {
                                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Edit, null) }, onClick = { renaming = c; chatMenu = null })
                                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { st.deleteChat(c.id); chatMenu = null })
                                }
                            }
                        }
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        expandedHeight = if (compact) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
                        navigationIcon = {
                            Row {
                                IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                                if (canGoBack) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                            }
                        },
                        title = {
                            val t = when {
                                tab == 2 -> chats.firstOrNull { it.id == current }?.title ?: "New chat"
                                tab == 1 && toolId != null -> toolTitle(toolId!!)
                                else -> sections.first { it.third == tab }.first
                            }
                            Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            if (tab == 2) IconButton(onClick = { st.newChat() }) { Icon(Icons.Filled.Add, contentDescription = "New chat") }
                            IconButton(onClick = { vm.refreshNow() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        },
                    )
                },
                bottomBar = {
                    // On square screens the sidebar replaces the bottom bar, leaving more room for the output.
                    if (!compact) NavigationBar {
                        for ((label, icon, i) in sections) {
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { goTab(i) },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { pad ->
                Column(
                    Modifier
                        .padding(pad)
                        .consumeWindowInsets(pad)
                        // Android 15 draws apps under the keyboard: lift the content above it so you see what you type.
                        .imePadding()
                        .fillMaxSize()
                ) {
                    if (s.settings.tiinyConfigured) StatsStrip(s, vm, st)
                    Box(Modifier.weight(1f).fillMaxWidth()) { when (tab) {
                        0 -> ModelsScreen(s, vm, st, onOpenSettings = { goTab(4) })
                        1 -> CreateScreen(s, vm, st, toolId, { openToolNav(it) }, openChat = { goTab(2) })
                        2 -> ChatScreen(s, vm, st)
                        3 -> UnlockScreen(s, vm)
                        else -> SettingsScreen(s, vm, st)
                    } }
                }
            }
        }
    }

    renaming?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.title) }
        ScaledAlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { st.renameChat(c.id, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { BtnLabel("Cancel", androidx.compose.material.icons.Icons.Filled.Close) } },
        )
    }

    s.swap?.let { sw ->
        val victim = sw.plan.candidateName ?: "another model"
        val victimNpu = sw.plan.candidateNpu?.let { " (${pct(it)})" } ?: ""
        ScaledAlertDialog(
            onDismissRequest = { vm.cancelSwap() },
            title = { Text("Not enough NPU room") },
            text = {
                Text(
                    "${sw.targetName} needs ${pct(sw.plan.npuRequired)} of the NPU, but only " +
                        "${pct(sw.plan.npuAvailable)} is free. Unload $victim$victimNpu to make room?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmSwap() }) { Text("Unload & load") } },
            dismissButton = { TextButton(onClick = { vm.cancelSwap() }) { BtnLabel("Cancel", androidx.compose.material.icons.Icons.Filled.Close) } },
        )
    }
}

@Composable
private fun StatusCard(s: UiState, onUnlock: () -> Unit, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val green = Color(0xFF2E7D32)
    val orange = Color(0xFFE65100)
    val red = Color(0xFFC62828)
    val anyRunning = s.models.any { it.status == "running" }
    // The whole field is coloured: green = online, orange = a model is loaded and running, red = offline or blocked.
    val (title, color) = when (s.device) {
        DeviceState.ONLINE -> if (anyRunning) "Tiiny running" to orange else "Tiiny online" to green
        DeviceState.CHECKING -> "Checking the Tiiny…" to cs.surfaceVariant
        DeviceState.NOT_CONFIGURED -> "Not set up yet" to cs.surfaceVariant
        DeviceState.LOCKED -> "Tiiny locked after a restart" to red
        DeviceState.UNAUTHORIZED -> "API key rejected" to red
        DeviceState.UNREACHABLE -> "Tiiny offline" to red
        DeviceState.ERROR -> "Unexpected answer from the Tiiny" to red
    }
    val strong = color == green || color == orange || color == red
    Card(
        colors = CardDefaults.cardColors(containerColor = color, contentColor = if (strong) Color.White else cs.onSurface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (s.device == DeviceState.ONLINE) {
                val loaded = s.models.count { it.status == "running" }
                Text("NPU in use: ${pct(s.npuUsed)} · $loaded model${if (loaded == 1) "" else "s"} loaded")
            }
            if (s.deviceMessage.isNotBlank()) Text(s.deviceMessage, style = MaterialTheme.typography.bodyMedium)
            if (s.device == DeviceState.UNREACHABLE && s.settings.vpnConfigured && !s.vpn.active && s.vpn.onHome != true) {
                Text("The VPN is off: away from home, switch it on below.", style = MaterialTheme.typography.bodyMedium)
            }
            when (s.device) {
                DeviceState.LOCKED ->
                    if (s.unlocking) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Unlocking…")
                        }
                    } else {
                        Button(onClick = onUnlock) { BtnLabel("Unlock now", androidx.compose.material.icons.Icons.Filled.Lock) }
                    }
                DeviceState.NOT_CONFIGURED, DeviceState.UNAUTHORIZED ->
                    Button(onClick = onSettings) { BtnLabel("Open Settings", androidx.compose.material.icons.Icons.Filled.Settings) }
                else -> {}
            }
        }
    }
}

@Composable
private fun VpnCard(s: UiState, vm: MainViewModel, onSettings: () -> Unit) {
    val v = s.vpn
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.permissionResult(granted)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (v.active) "VPN on" else "VPN off",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (v.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    val where = when (v.onHome) {
                        true -> "On your home network"
                        false -> "Away from your home network"
                        null -> "Network: can't tell"
                    }
                    val auto = if (s.settings.autoVpn) " · automatic" else ""
                    Text("$where$auto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    !v.wireguardInstalled -> TextButton(onClick = onSettings) { Text("Set up") }
                    !v.permitted -> Button(onClick = { launcher.launch(Vpn.PERMISSION) }) { Text("Allow") }
                    v.switching -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    v.active -> OutlinedButton(onClick = { vm.toggleVpn() }) { Text("Turn off") }
                    else -> Button(onClick = { vm.toggleVpn() }) { Text("Turn on") }
                }
            }
            if (!v.wireguardInstalled) {
                Text("The WireGuard app isn't installed on this phone.", style = MaterialTheme.typography.bodySmall)
            } else if (!v.permitted) {
                Text("Allow Tiiny Remote to switch WireGuard tunnels (asked once).", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val FILTERS = listOf<Triple<String, String, (ModelInfo) -> Boolean>>(
    Triple("chat", "Chat", { it.isChat }),
    Triple("makes", "Makes things", { it.makesThings }),
    Triple("sees", "Sees images", { it.isChat && it.seesImages }),
    Triple("thinks", "Thinks", { it.thinks }),
    Triple("moe", "Mixture of experts", { it.isMoE }),
)

private val SORTS = listOf("name" to "Name", "type" to "Type", "npu" to "NPU load")

@Composable
private fun ModelsScreen(s: UiState, vm: MainViewModel, st: StudioViewModel, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("models_ui", android.content.Context.MODE_PRIVATE) }
    // Filters, sorting and favourites are remembered.
    var filters by remember { mutableStateOf(prefs.getStringSet("filters", emptySet())!!.toSet()) }
    var sort by remember { mutableStateOf(prefs.getString("sort", "name") ?: "name") }
    var descending by remember { mutableStateOf(prefs.getBoolean("desc", false)) }
    var favs by remember { mutableStateOf(prefs.getStringSet("favs", emptySet())!!.toSet()) }
    var loadedFirst by remember { mutableStateOf(prefs.getBoolean("loaded_first", false)) }
    var info by remember { mutableStateOf<ModelInfo?>(null) }
    var renameM by remember { mutableStateOf<ModelInfo?>(null) }
    var deleteM by remember { mutableStateOf<ModelInfo?>(null) }
    var storeView by remember { mutableStateOf(false) }
    var typeSel by remember { mutableStateOf(prefs.getStringSet("types", emptySet())!!.toSet()) }
    var typesOpen by remember { mutableStateOf(false) }
    val typeOrder = remember(s.models.size) {
        // The order arranged in Create → Create, then any other type the Tiiny has.
        val o = createTypeOrder(st)
        o + s.models.map { it.type }.filter { it.isNotBlank() && it !in o }.distinct()
    }
    val stats by vm.stats.collectAsState()
    var sortOpen by remember { mutableStateOf(false) }

    val active = FILTERS.filter { it.first in filters }
    val byKey: Comparator<ModelInfo> = when (sort) {
        "type" -> compareBy<ModelInfo>({ it.type.ifBlank { "~" }.lowercase() }, { it.name.lowercase() })
        "npu" -> compareBy<ModelInfo>({ it.npuUsage ?: Double.MAX_VALUE }, { it.name.lowercase() })
        else -> compareBy { it.name.lowercase() }
    }
    val list = s.models
        .filter { m -> active.all { it.third(m) } && (typeSel.isEmpty() || m.type in typeSel) }
        .sortedWith(
            compareBy<ModelInfo>(
                { if (loadedFirst && it.status != "stopped") 0 else 1 }, // loaded models first, when that's on
                { if (it.id in favs) 0 else 1 }, // favourites on top
                { if (it.id in s.unavailable && it.status == "stopped") 1 else 0 },
            ).then(if (descending) byKey.reversed() else byKey)
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (LocalUi.current.compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Online/offline and the loaded models are in the strip at the top; this card only shows when something needs doing.
        if (s.device in setOf(DeviceState.LOCKED, DeviceState.NOT_CONFIGURED, DeviceState.UNAUTHORIZED, DeviceState.ERROR) ||
            (s.device == DeviceState.UNREACHABLE && s.deviceMessage.isNotBlank())
        ) {
            item { StatusCard(s, onUnlock = { vm.unlock() }, onSettings = onOpenSettings) }
        }
        if (s.settings.vpnConfigured) {
            item { VpnCard(s, vm, onSettings = onOpenSettings) }
        }
        if (s.device == DeviceState.ONLINE) {
            item {
                // Installed models, or the Model Store.
                val updates = s.models.count { it.needUpdate }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !storeView,
                        onClick = { storeView = false },
                        label = { Text("Installed ${s.models.size}" + if (updates > 0) " · ⟳ $updates" else "") },
                    )
                    FilterChip(
                        selected = storeView,
                        onClick = { storeView = true; if (s.store == null) vm.loadStore() },
                        label = { Text("Store" + (s.store?.let { " ${it.size}" } ?: "")) },
                    )
                    // Types: the kinds from Create → Create, for both lists.
                    Box {
                        FilterChip(
                            selected = typeSel.isNotEmpty(),
                            onClick = { typesOpen = true },
                            label = { Text(if (typeSel.isEmpty()) "Types ▾" else "Types ${typeSel.size} ▾") },
                        )
                        ScaledDropdownMenu(expanded = typesOpen, onDismissRequest = { typesOpen = false }, modifier = Modifier.heightIn(max = 460.dp)) {
                            DropdownMenuItem(
                                text = { Text((if (typeSel.isEmpty()) "● " else "○ ") + "All types") },
                                onClick = { typeSel = emptySet(); prefs.edit().putStringSet("types", typeSel).apply() },
                            )
                            HorizontalDivider()
                            for (t in typeOrder) {
                                val n = if (storeView) s.store?.count { it.type == t } ?: 0 else s.models.count { it.type == t }
                                DropdownMenuItem(
                                    text = { Text((if (t in typeSel) "☑ " else "☐ ") + t + "  ($n)") },
                                    onClick = {
                                        typeSel = if (t in typeSel) typeSel - t else typeSel + t
                                        prefs.edit().putStringSet("types", typeSel).apply()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (s.downloads.isNotEmpty()) item { DownloadsCard(s, vm) }
            if (storeView) {
                item { StoreList(s, vm, stats?.diskFreeGb, typeSel) }
            } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for ((key, label, _) in FILTERS) {
                                FilterChip(
                                    selected = key in filters,
                                    onClick = {
                                        filters = if (key in filters) filters - key else filters + key
                                        prefs.edit().putStringSet("filters", filters).apply()
                                    },
                                    label = { Text(tag(label)) },
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Round button in the "Loaded" colour: loaded models to the top.
                        val loadedColor = Color(0xFFE65100)
                        Surface(
                            onClick = {
                                loadedFirst = !loadedFirst
                                prefs.edit().putBoolean("loaded_first", loadedFirst).apply()
                            },
                            shape = CircleShape,
                            color = if (loadedFirst) loadedColor else Color.Transparent,
                            border = BorderStroke(2.dp, loadedColor),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = if (loadedFirst) "Loaded models first: on" else "Loaded models first: off",
                                    tint = if (loadedFirst) Color.White else loadedColor,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${list.size} of ${s.models.size} models" + listOfNotNull(
                                if (loadedFirst) "loaded first" else null,
                                if (favs.isNotEmpty()) "★ first" else null,
                            ).joinToString("") { " · $it" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            TextButton(onClick = { sortOpen = true }) { Text("Sort: ${SORTS.first { it.first == sort }.second} ▾") }
                            ScaledDropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                for ((k, l) in SORTS) DropdownMenuItem(
                                    text = { Text((if (k == sort) "● " else "○ ") + l) },
                                    onClick = {
                                        sort = k
                                        prefs.edit().putString("sort", k).apply()
                                        sortOpen = false
                                    },
                                )
                            }
                        }
                        TextButton(onClick = { descending = !descending; prefs.edit().putBoolean("desc", descending).apply() }) {
                            Text(if (descending) "↓" else "↑")
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item { Text(if (s.models.isEmpty()) "No models on the Tiiny yet. Download some from the Store above." else "No model matches all the chosen filters.") }
            }
            items(list, key = { it.id }) { m ->
                ModelCard(
                    m = m,
                    working = m.id in s.working,
                    unavailable = m.id in s.unavailable && m.status == "stopped",
                    favourite = m.id in favs,
                    onFavourite = {
                        favs = if (m.id in favs) favs - m.id else favs + m.id
                        prefs.edit().putStringSet("favs", favs).apply()
                    },
                    onInfo = { info = m },
                    onRename = { renameM = m },
                    onUpdate = { vm.updateModel(m) },
                    onDelete = { deleteM = m },
                    onLoad = { vm.load(m) },
                    onRetry = { vm.retry(m) },
                    onUnload = { vm.unload(m) },
                )
            }
            }
        }
    }
    deleteM?.let { m ->
        ScaledAlertDialog(
            onDismissRequest = { deleteM = null },
            title = { Text("Delete ${m.name}?") },
            text = {
                Text(
                    "This removes the model from the Tiiny" + (if (m.sizeBytes > 0) " and frees ${gbSize(m.sizeBytes)}" else "") +
                        ". You can download it again from the Store.",
                )
            },
            confirmButton = { DangerButton("Delete", onClick = { vm.deleteModel(m); deleteM = null }) },
            dismissButton = { TextButton(onClick = { deleteM = null }) { BtnLabel("Cancel", Icons.Filled.Close) } },
        )
    }
    info?.let { m -> ModelInfoDialog(m) { info = null } }
    renameM?.let { m ->
        var name by remember(m.id) { mutableStateOf(m.name) }
        ScaledAlertDialog(
            onDismissRequest = { renameM = null },
            title = { Text("Rename model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only in this app. The Tiiny's name: ${m.fullName.ifBlank { m.name }}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name in this app") })
                }
            },
            confirmButton = { TextButton(onClick = { vm.renameModel(m.id, name); renameM = null }) { BtnLabel("Save", Icons.Filled.Check) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.renameModel(m.id, ""); renameM = null }) { Text("Use original") }
                    TextButton(onClick = { renameM = null }) { BtnLabel("Cancel", Icons.Filled.Close) }
                }
            },
        )
    }
}

/** A small "MoE" tag beside a mixture-of-experts model's name. */
@Composable
fun MoeBadge() {
    Text(
        tag("MoE"),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * "About this model", as in Tiiny Studio: the model's own description plus what the Tiiny says about it. As wide as
 * the screen allows (up to 640 dp), so square and narrow screens both get a readable box, and a title that fits.
 */
@Composable
private fun ModelInfoDialog(m: ModelInfo, onClose: () -> Unit) {
    val full = m.fullName.ifBlank { m.name }
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = {
            ClosableTitle(onClose) {
                Column {
                    Text(full.substringAfterLast('/'), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if ('/' in full) Text(full.substringBeforeLast('/'), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.fullName.isNotBlank() && m.fullName != m.name) {
                    Text("Shown in this app as “${m.name}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("About this model", style = MaterialTheme.typography.titleSmall)
                Text(m.desc.replace("**", "").ifBlank { "The Tiiny has no description for this model." }, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                val rows = listOfNotNull(
                    "Full name" to m.fullName.ifBlank { m.name },
                    m.versionLabel.takeIf { it.isNotBlank() }?.let { "Version" to it + if (m.needUpdate) " (update available)" else "" },
                    m.sizeBytes.takeIf { it > 0 }?.let { "Size on the Tiiny" to gbSize(it) },
                    "ID" to m.id,
                    m.type.takeIf { it.isNotBlank() }?.let { "Type" to it },
                    m.input.takeIf { it.isNotBlank() }?.let { "Input" to it.replace(",", ", ") },
                    m.supported.takeIf { it.isNotEmpty() }?.let { "Supports" to it.joinToString(", ") },
                    m.abilities.takeIf { it.isNotEmpty() }?.let { "In this app" to it.joinToString(" · ") },
                    if (m.isMoE) "Architecture" to "Mixture of experts" + (m.activeParams?.let { ": about $it active per word" } ?: "") +
                        ", so it writes much faster than a dense model of its size" else null,
                    m.npuUsage?.let { "NPU" to "${pct(it)} of the NPU when loaded" },
                    m.loadTimeSec?.let { "Loading" to "about ${fmtSec(it)}" },
                    if (m.thinks) "Thinking" to (if (m.alwaysThinks) "always reasons (effort adjustable)" else "can be switched on and off") else null,
                    "Status" to when (m.status) {
                        "running" -> if (m.busy) "loaded, working" else "loaded"
                        "loading" -> "loading"
                        else -> "not loaded"
                    },
                )
                for ((k, v) in rows) {
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ModelCard(
    m: ModelInfo,
    working: Boolean,
    unavailable: Boolean,
    favourite: Boolean,
    onFavourite: () -> Unit,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
    onUnload: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (m.isMoE) MoeBadge()
                    IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = "About this model", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename in this app", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    if (m.status == "stopped") IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete from the Tiiny", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }
                if (m.needUpdate) {
                    TextButton(
                        onClick = onUpdate,
                        colors = ButtonDefaults.textButtonColors(containerColor = Color(0xFFE65100).copy(alpha = 0.15f), contentColor = Color(0xFFE65100)),
                    ) { BtnLabel("Update available", symbol = "⟳") }
                }
                val meta = listOfNotNull(
                    m.type.takeIf { it.isNotBlank() },
                    m.npuUsage?.let { "${pct(it)} NPU" },
                    m.loadTimeSec?.let { "loads in ~${fmtSec(it)}" },
                    m.sizeBytes.takeIf { it > 0 }?.let { gbSize(it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.abilities.isNotEmpty()) {
                    Text(tags(m.abilities), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (unavailable) {
                    Text(
                        "The Tiiny lists this model but can't load it, often a leftover copy. Delete it with the bin above to remove it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        m.status == "running" -> if (m.busy) "Loaded · working" else "Loaded"
                        m.status == "loading" -> "Loading…"
                        unavailable -> "Not available on the Tiiny"
                        else -> "Not loaded"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (m.status == "running") Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The same fixed-width column on every card: the star always sits centred above the button.
            Column(
                Modifier.width(112.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onFavourite) {
                    Text(
                        if (favourite) "★" else "☆",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (favourite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    when {
                        working || m.status == "loading" -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        m.status == "running" -> OutlinedButton(onClick = onUnload, modifier = Modifier.fillMaxWidth()) { BtnLabel("Unload", symbol = "■") }
                        unavailable -> TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { BtnLabel("Try again", androidx.compose.material.icons.Icons.Filled.Refresh) }
                        else -> Button(onClick = onLoad, modifier = Modifier.fillMaxWidth()) { BtnLabel("Load", androidx.compose.material.icons.Icons.Filled.PlayArrow) }
                    }
                }
            }
        }
    }
}

/** Colours of the log's kinds. */
private fun logColor(type: String): Color = when (type) {
    "Device" -> Color(0xFF1E88E5)
    "Models" -> Color(0xFF3949AB)
    "Unlock" -> Color(0xFF8E24AA)
    "VPN" -> Color(0xFF00897B)
    "Chat" -> Color(0xFFF4511E)
    "Search" -> Color(0xFF43A047)
    "Create" -> Color(0xFFFFB300)
    else -> Color.Gray
}

@Composable
private fun UnlockScreen(s: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("log_ui", android.content.Context.MODE_PRIVATE) }
    val entries by ApiLog.entries.collectAsState()
    // Which kinds to show (empty = all), and "problems only"; remembered.
    var shown by remember { mutableStateOf(prefs.getStringSet("types", emptySet())!!.toSet()) }
    var problems by remember { mutableStateOf(prefs.getBoolean("problems", false)) }
    val list = entries.filter { (shown.isEmpty() || it.type in shown) && (!problems || it.error) }
    val compact = LocalUi.current.compact

    Column(
        Modifier
            .fillMaxSize()
            .padding(if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${list.size} entr${if (list.size == 1) "y" else "ies"}" + if (shown.isNotEmpty() || problems) " shown" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { shareLog(ctx, s, shown.takeIf { it.isNotEmpty() }, problems) }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
            DangerButton("Clear", onClick = { vm.clearLog() }, icon = androidx.compose.material.icons.Icons.Filled.Clear)
        }
        Text(
            "Share sends what's shown, without keys, passwords or email addresses.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (list.isEmpty()) item { Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium) }
            items(list, key = { "${it.ms}-${it.text.hashCode()}-${it.type}" }) { e -> LogRow(e) }
        }
    }
}

/** One log line: a bar in its kind's colour, time and kind, and the text (tap to see all of a long one). */
@Composable
private fun LogRow(e: LogEntry) {
    var open by remember { mutableStateOf(false) }
    val c = logColor(e.type)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (e.error) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { open = !open },
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(c))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.time, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(e.type, style = MaterialTheme.typography.labelSmall, color = c)
                if (e.error) {
                    Spacer(Modifier.width(6.dp))
                    Text("⚠", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                e.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
        },
    )
}

@Composable
private fun SettingDropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == selected }?.second ?: selected}  ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ScaledDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((v, l) in options) DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
        }
    }
}

/** The ⓘ beside Labels: what each pictogram means. */
@Composable
private fun PictogramLegend(onClose: () -> Unit) {
    // A Material icon or a text symbol, as the buttons use them.
    val signs = listOf<Pair<Any, String>>(
        Icons.Filled.PlayArrow to "Load a model, resume a download",
        "■" to "Unload a model, stop",
        "⤓" to "Download, save to the phone",
        "⟳" to "Update a model",
        "❚❚" to "Pause a download",
        Icons.Filled.Close to "Cancel, remove, close",
        Icons.Filled.Edit to "Edit, rename",
        Icons.Filled.Check to "Save",
        Icons.Filled.Share to "Share",
        "⧉" to "Copy",
        PowerIcon to "Power off the Tiiny",
        Icons.Filled.Refresh to "Try again, check again, restart the Tiiny",
        Icons.Filled.Lock to "Unlock the Tiiny",
        Icons.Filled.Settings to "Open Settings",
    )
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text("What the pictograms mean") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Models", style = MaterialTheme.typography.titleSmall)
                val meaning = mapOf(
                    "Chat" to "Chat model",
                    "Thinks" to "Can think before answering (can be switched off); in the chat: thinking on (how deep: the Thinking slider)",
                    "Always thinks" to "Always thinks before answering",
                    "Makes things" to "Can use the chat's tools (images, speech, songs…); in the chat: tools on",
                    "Sees images" to "Understands pictures you attach",
                    "MoE" to "Mixture of experts: writes much faster than its size suggests",
                    "Main chat model" to "The chat's main model (when a helper is loaded)",
                    "Helper" to "The fast helper model",
                    "working" to "Working on something right now",
                    "Web search" to "Chat: web search on",
                    "Suggestions" to "Chat: follow-up suggestions on",
                    "Socratic" to "Chat: Socratic mode (guides with questions)",
                    "Student" to "Chat: Student mode (teaches step by step)",
                )
                for ((word, what) in meaning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(TAG_ICONS[word] ?: "", modifier = Modifier.width(36.dp))
                        Text(what, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Buttons", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                for ((sign, what) in signs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(36.dp)) {
                            if (sign is androidx.compose.ui.graphics.vector.ImageVector) Icon(sign, null, Modifier.size(18.dp)) else Text(sign.toString())
                        }
                        Text(what, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "A button with no pictogram always shows its word. Others: ● loaded, ▾ opens a menu, ★ favourite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

/** The ⓘ beside the speed test: dense vs. mixture-of-experts models, and which to use for what. */
@Composable
private fun ModelChoiceInfo(onClose: () -> Unit) {
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text("Which model for what?") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Two kinds of chat models", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Dense, like Qwen3.8-27B: uses all its 27 billion parameters for every word. Usually more careful on " +
                        "hard reasoning, but on the Tiiny it writes only about 10 tokens (word pieces) per second.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Mixture of experts (MoE), like the 35B-A3B models or gpt-oss-20b: holds 20–35 billion parameters " +
                        "but uses only about 3–4 billion per word (\"A3B\" = 3 billion active). It knows about as much, " +
                        "writes 2–2.5× faster (about 23 tokens per second on your Tiiny), but can be less thorough on long, " +
                        "tricky reasoning. Marked \"MoE\" in Models, with a filter of its own.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("How to use them", style = MaterialTheme.typography.titleSmall)
                Text(
                    "• Everyday chat, web search, summaries, quick drafts: an A3B model.\n" +
                        "• Hard problems, careful writing, analysis you want double-checked: the dense 27B.\n" +
                        "• Helper: a fast, small MoE like gpt-oss-20b (27% NPU). It names chats and condenses long ones. " +
                        "It also condenses web pages, but only when it's clearly faster than the chat model (see below).\n" +
                        "• Both pairs fit: A3B + gpt-oss about 75%, 27B + gpt-oss about 86% of the NPU.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("The speed test measures two different things", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. How fast each model writes alone. This decides who reads web pages. It barely changes " +
                        "between runs (a few percent), and the speeds are remembered.\n\n" +
                        "2. How much sooner two jobs finish when both models work at the same time instead of one after " +
                        "the other. This tells whether the helper can name a chat while the chat model answers. It " +
                        "varies a lot between runs (the same pair measured 7% and 18%). Any gain means the title costs " +
                        "nothing, so this is done unless the test finds it slower. From 10% the test calls it \"worth it\". " +
                        "The same model twice brings nothing: the Tiiny takes a model's requests in turn.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Web pages: the 25% rule", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Before answering, the helper shortens each web page it found to the parts that matter, and the " +
                        "chat model reads only those. It does this only when the helper writes at least 25% faster than " +
                        "the chat model, for example gpt-oss-20b beside the 27B (more than twice as fast). Beside an A3B " +
                        "model (as fast as gpt-oss) the chat model reads the pages itself.\n\n" +
                        "Drawbacks of condensing:\n" +
                        "• Details can get lost: exact numbers, dates, names, quotes or a side remark the helper didn't " +
                        "think important. The answer can then only be as good as the summary.\n" +
                        "• Mistakes add up: the smaller helper can misread a page, and the chat model can't notice, " +
                        "because it never sees the original.\n" +
                        "• An extra step: the helper has to read the whole page first. That only pays off when it's " +
                        "clearly faster, hence the strict 25%.\n" +
                        "When details matter, use the chat model alone (helper None) or pair the helper with an A3B model.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Test runs", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Run the test once for each chat model you pair with the helper. With 2–4 runs the results are " +
                        "averaged, the spread is shown, and the rules apply to the average, so one lucky or unlucky run " +
                        "doesn't decide.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SettingsScreen(s: UiState, vm: MainViewModel, st: StudioViewModel) {
    val cur = s.settings
    // Display changes save at once; they shouldn't reset what is being typed in the other fields.
    val formKey = cur.copy(layout = "", uiScale = "", textScale = "", enterSends = true, keepRunning = false, notifyDone = false, labels = "", helperModel = "", dailyUpdateCheck = false, speedRuns = 1, autoAsksHelper = false)
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
    var helper by remember(formKey) { mutableStateOf(cur.helperModel) }
    val scope = rememberCoroutineScope()

    fun current() = AppSettings(
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
        layout = cur.layout,
        uiScale = cur.uiScale,
        textScale = cur.textScale,
        enterSends = cur.enterSends,
        helperModel = helper,
        keepRunning = cur.keepRunning,
        notifyDone = cur.notifyDone,
        labels = cur.labels,
        dailyUpdateCheck = cur.dailyUpdateCheck,
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
            "Square · compact puts the sections in the ☰ sidebar instead of a bottom bar, uses a slim top bar and folds the model and chat options into one line, leaving more room for chats, pictures and video.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            Text("Enter key sends chat messages (Shift+Enter for a new line), for the hardware keyboard")
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Tiiny", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Tiiny address") },
            placeholder = { Text("http://192.168.x.x") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        SecretField(key, { key = it }, "API key (TiinyOS → Settings → API Key)")

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
                "The phone searches with Tavily if you add a key (free plan: 1,000 searches a month, no card, at tavily.com), otherwise with Bing and DuckDuckGo, and Wikipedia as a last resort. News comes from Google News and Bing News. The keyless sources can occasionally refuse; the log shows which one answered.",
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
                Text("Keep working in the background", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "While a tool job, chat answer, speed test or download is going on, the app keeps working when you " +
                        "switch away or lock the phone, with a quiet notification. It stops by itself when done.",
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
        Text("Fast helper model", style = MaterialTheme.typography.titleMedium)
        val chatModels = s.models.filter { it.isChat }
        SettingDropdown(
            "Helper",
            // ● = loaded now, as in the chat's model menu; mixture-of-experts models carry their tag.
            listOf("" to "None (the chat model does everything)") + chatModels.map { m ->
                // No-break spaces: the ● stays in front of its name, even when a long name wraps.
                m.id to (if (m.running) "●\u00A0" else "") + m.name + if (m.isMoE) "\u00A0\u00A0" + tag("MoE") else ""
            },
            helper,
        ) {
            // Saved at once (no Save needed), like the display settings.
            helper = it
            vm.setDisplay(cur.copy(helperModel = it))
        }
        Text(
            "A quick chat model kept loaded beside your main one. While it's loaded it names new chats (while the main model " +
                "answers), suggests follow-up questions, condenses long chats, and runs Improve prompt, lyrics and tags. It condenses web pages too, but " +
                "only when the speed test found it at least 25% faster than the chat model (details can get lost, see ⓘ); otherwise the chat model reads " +
                "them itself. It's never swapped in: when it isn't loaded, the chat model does everything.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto thinking asks the helper", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "With the chat's Thinking on Auto, the helper reads each question and chooses Low, Medium or High. " +
                        "Smarter than the app's own rule, but every answer starts about 2 seconds later. Off: the app " +
                        "decides at once from the question's words and length.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = cur.autoAsksHelper, onCheckedChange = { vm.setDisplay(cur.copy(autoAsksHelper = it)) })
        }
        val speed by st.speed.collectAsState()
        val small = chatModels.firstOrNull { it.id == helper && it.running }
        val big = chatModels.firstOrNull { it.running && it.id == st.pick("chat") && it.id != helper }
            ?: chatModels.firstOrNull { it.running && it.id != helper }
        var speedInfo by remember { mutableStateOf(false) }
        SettingDropdown(
            "Test runs",
            (1..4).map { n -> n.toString() to if (n == 1) "1 (quick)" else "$n, averaged" },
            cur.speedRuns.toString(),
        ) { vm.setDisplay(cur.copy(speedRuns = it.toInt())) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { if (big != null && small != null) st.speedTest(big, small, cur.speedRuns) },
                enabled = big != null && small != null && speed?.startsWith("Testing") != true,
                modifier = Modifier.weight(1f),
            ) { Text("Speed test: one at a time or both at once?") }
            IconButton(onClick = { speedInfo = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Which models to use, and why", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (speedInfo) ModelChoiceInfo { speedInfo = false }
        Text(
            when {
                helper.isBlank() -> "Choose a helper first."
                small == null -> "Load the helper and your chat model to run the speed test."
                big == null -> "Load your chat model too to run the speed test."
                else -> "Tests ${big.name} and ${small.name}, alone and at the same time, " +
                    (if (cur.speedRuns == 1) "once (about 2 minutes). Results vary by a few percent from run to run: more runs give a steadier average."
                    else "${cur.speedRuns} times and averages the results (about ${cur.speedRuns * 2} minutes).")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        speed?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

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
        Text("NAS, as a fallback for unlocking", style = MaterialTheme.typography.titleMedium)
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

        Text(
            "The log (with Share) is in the Activity tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Tiiny", style = MaterialTheme.typography.titleMedium)
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
            "Power off: to switch the Tiiny on again, press its power button. Restart: afterwards it's locked until " +
                "unlocked (automatic with its saved password). Both unload the models and stop what's running on it.",
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

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("App updates", style = MaterialTheme.typography.titleMedium)
        val ctx = LocalContext.current
        val version = remember { AppUpdate.installed(ctx) }
        val updScope = rememberCoroutineScope()
        var release by remember { mutableStateOf<AppRelease?>(null) }
        var updBusy by remember { mutableStateOf<String?>(null) }
        var updNote by remember {
            mutableStateOf(AppUpdate.known(ctx)?.let { "Tiiny Remote $it is out (found by the daily check)." })
        }
        fun checkNow(then: ((AppRelease) -> Unit)? = null) {
            updBusy = "Checking…"
            updScope.launch {
                val r = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { AppUpdate.check(ctx) } }
                updBusy = null
                r.onSuccess {
                    release = it
                    updNote = if (it == null) "Tiiny Remote $version is the newest." else "Tiiny Remote ${it.version} is out."
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
                "Tiiny Remote $version · secrets are stored encrypted on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { about = true }) {
                Icon(Icons.Filled.Info, contentDescription = "About this app", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (about) AboutDialog(version) { about = false }
    }
}

private const val REPO = "https://github.com/JacopoSmo/tiiny-remote"

/** The ⓘ beside the version: what the app is, where it lives, what it sends where, and what it's built with. */
@Composable
private fun AboutDialog(version: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val exp = AppUpdate.experimental(ctx)
    val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    val info = "Tiiny Remote $version${if (exp) " (experimental)" else ""} · ${ctx.packageName} · $device"
    fun open(url: String) { runCatching { uri.openUri(url) } }
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text("About Tiiny Remote") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AboutBody("Controls your Tiiny AI Pocket Lab from your phone: models, chat with tools and web search, " +
                    "pictures, speech, music, video and 3D, status, and unlocking after a restart.")
                AboutHead("Version")
                AboutBody("$version${if (exp) " · experimental channel (separate app, newest features first)" else ""}\n${ctx.packageName}\n$device")
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(info)) }) {
                    BtnLabel("Copy for a bug report", symbol = "⧉")
                }
                AboutHead("Links")
                AboutLink("Source code on GitHub") { open(REPO) }
                AboutLink("All versions and what's new") { open("$REPO/releases") }
                AboutLink("Report a problem or suggest an idea") { open("$REPO/issues") }
                AboutHead("Your data")
                AboutBody("• Talks only to your Tiiny (and your NAS, if you unlock over SSH), over your Wi-Fi or your WireGuard tunnel. No account, no ads, no tracking, no analytics.\n" +
                    "• Web search, when you turn it on, goes from the phone to the search service you chose (SearXNG, Tavily, Bing, DuckDuckGo or Wikipedia).\n" +
                    "• The update check asks GitHub for the newest version; nothing about you is sent.\n" +
                    "• Passwords and keys are stored encrypted on this phone. Chats and results stay on the phone; the log hides keys, e-mail addresses and device IDs.")
                AboutHead("Good to know")
                AboutBody("• Not made by or affiliated with Tiiny AI. Tiiny and TiinyOS are theirs.\n" +
                    "• Installed from GitHub, not Google Play: updates come through Settings → App updates.\n" +
                    "• AI answers can be wrong. Check what matters, especially facts, figures and medical, legal or financial advice.")
                AboutHead("Built with")
                AboutBody("Kotlin and Jetpack Compose (Android), AndroidX WorkManager, Kotlin coroutines (Apache 2.0) and JSch (BSD) for SSH. " +
                    "Made by JacopoSmo, written with Claude (Anthropic).")
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun AboutHead(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun AboutBody(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun AboutLink(label: String, onClick: () -> Unit) = Text(
    label,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
)

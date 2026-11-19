package app.tiinyremote

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
private fun buildReport(ctx: Context, s: UiState): String {
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    return buildString {
        append("Tiiny Remote ").append(version).append('\n')
        append("Status: ").append(s.device).append(' ').append(s.deviceMessage).append("\n\n")
        append("== API calls (newest first) ==\n")
        val api = ApiLog.all()
        append(if (api.isEmpty()) "(none)" else api.joinToString("\n\n"))
        append("\n\n== Unlock log ==\n")
        append(if (s.log.isEmpty()) "(none)" else s.log.joinToString("\n"))
    }
}

private fun shareLog(ctx: Context, s: UiState) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Tiiny Remote log")
        putExtra(Intent.EXTRA_TEXT, buildReport(ctx, s))
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
    val openTool by st.openTool.collectAsState()
    LaunchedEffect(openTool) {
        if (openTool != null) tab = 1 // "Edit this" from the chat or results goes to the Create tab
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
        Triple("Create", Icons.Filled.Create, 1),
        Triple("Chat", Icons.Filled.Send, 2),
        Triple("Activity", Icons.Filled.Lock, 3),
        Triple("Settings", Icons.Filled.Settings, 4),
    )
    fun go(i: Int) {
        tab = i
        scope.launch { drawer.close() }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(base.density * uiScale, base.fontScale * textScale),
        LocalUi provides UiPrefs(compact = compact, enterSends = s.settings.enterSends),
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
                                DropdownMenu(expanded = chatMenu?.id == c.id, onDismissRequest = { chatMenu = null }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { renaming = c; chatMenu = null })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { st.deleteChat(c.id); chatMenu = null })
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
                            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                        },
                        title = {
                            val t = if (tab == 2) chats.firstOrNull { it.id == current }?.title ?: "New chat" else sections[tab].first
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
                                onClick = { tab = i },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { pad ->
                Box(
                    Modifier
                        .padding(pad)
                        .fillMaxSize()
                ) {
                    when (tab) {
                        0 -> ModelsScreen(s, vm, onOpenSettings = { tab = 4 })
                        1 -> CreateScreen(s, vm, st, openChat = { tab = 2 })
                        2 -> ChatScreen(s, vm, st)
                        3 -> UnlockScreen(s, vm)
                        else -> SettingsScreen(s, vm)
                    }
                }
            }
        }
    }

    renaming?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { st.renameChat(c.id, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    s.swap?.let { sw ->
        val victim = sw.plan.candidateName ?: "another model"
        val victimNpu = sw.plan.candidateNpu?.let { " (${pct(it)})" } ?: ""
        AlertDialog(
            onDismissRequest = { vm.cancelSwap() },
            title = { Text("Not enough NPU room") },
            text = {
                Text(
                    "${sw.targetName} needs ${pct(sw.plan.npuRequired)} of the NPU, but only " +
                        "${pct(sw.plan.npuAvailable)} is free. Unload $victim$victimNpu to make room?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmSwap() }) { Text("Unload & load") } },
            dismissButton = { TextButton(onClick = { vm.cancelSwap() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatusCard(s: UiState, onUnlock: () -> Unit, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (title, color) = when (s.device) {
        DeviceState.ONLINE -> "Tiiny online" to cs.primaryContainer
        DeviceState.CHECKING -> "Checking the Tiiny…" to cs.surfaceVariant
        DeviceState.NOT_CONFIGURED -> "Not set up yet" to cs.surfaceVariant
        DeviceState.LOCKED -> "Tiiny locked after a restart" to cs.errorContainer
        DeviceState.UNAUTHORIZED -> "API key rejected" to cs.errorContainer
        DeviceState.UNREACHABLE -> "Tiiny not reachable" to cs.errorContainer
        DeviceState.ERROR -> "Unexpected answer from the Tiiny" to cs.errorContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
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
                            Text("Unlocking through the NAS…")
                        }
                    } else {
                        Button(onClick = onUnlock) { Text("Unlock now") }
                    }
                DeviceState.NOT_CONFIGURED, DeviceState.UNAUTHORIZED ->
                    Button(onClick = onSettings) { Text("Open Settings") }
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

@Composable
private fun ModelsScreen(s: UiState, vm: MainViewModel, onOpenSettings: () -> Unit) {
    var chatOnly by rememberSaveable { mutableStateOf(false) }
    val list = s.models
        .filter { !chatOnly || it.isChat }
        .sortedWith(
            compareBy<ModelInfo>(
                {
                    when {
                        it.status == "running" -> 0
                        it.status == "loading" -> 1
                        it.id in s.unavailable -> 3
                        else -> 2
                    }
                },
                { it.name.lowercase() },
            )
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StatusCard(s, onUnlock = { vm.unlock() }, onSettings = onOpenSettings) }
        if (s.settings.vpnConfigured) {
            item { VpnCard(s, vm, onSettings = onOpenSettings) }
        }
        if (s.device == DeviceState.ONLINE) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = chatOnly, onCheckedChange = { chatOnly = it })
                    Spacer(Modifier.width(10.dp))
                    Text("Chat models only")
                }
            }
            if (list.isEmpty()) {
                item { Text("No models to show. Download models in TiinyOS first.") }
            }
            items(list, key = { it.id }) { m ->
                ModelCard(
                    m = m,
                    working = m.id in s.working,
                    unavailable = m.id in s.unavailable && m.status == "stopped",
                    onLoad = { vm.load(m) },
                    onRetry = { vm.retry(m) },
                    onUnload = { vm.unload(m) },
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    m: ModelInfo,
    working: Boolean,
    unavailable: Boolean,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
    onUnload: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium)
            val meta = listOfNotNull(
                m.type.takeIf { it.isNotBlank() },
                m.npuUsage?.let { "${pct(it)} NPU" },
                m.loadTimeSec?.let { "loads in ~${fmtSec(it)}" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (m.abilities.isNotEmpty()) {
                Text(
                    m.abilities.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (unavailable) {
                Text(
                    "The Tiiny lists this model but can't load it, often a leftover copy. Delete it in TiinyOS to remove it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = when {
                    m.status == "running" -> if (m.busy) "Loaded · working" else "Loaded"
                    m.status == "loading" -> "Loading…"
                    unavailable -> "Not available on the Tiiny"
                    else -> "Not loaded"
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (m.status == "running") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                when {
                    working || m.status == "loading" ->
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    m.status == "running" -> OutlinedButton(onClick = onUnload) { Text("Unload") }
                    unavailable -> TextButton(onClick = onRetry) { Text("Try again") }
                    else -> Button(onClick = onLoad) { Text("Load") }
                }
            }
        }
    }
}

@Composable
private fun UnlockScreen(s: UiState, vm: MainViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Unlock and activity", style = MaterialTheme.typography.titleLarge)
        Text(
            "After a restart the Tiiny stays locked until someone logs in. With the Tiiny's password saved in " +
                "Settings, the app unlocks it directly, the way \"tiiny login\" does. Without it, or if that fails, " +
                "it runs \"tiiny login\" on your NAS over SSH (switch SSH on in UGOS first).",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (s.settings.autoUnlock) {
            Text(
                "Automatic unlock is on: when the app finds the Tiiny locked, it does this by itself " +
                    "(at most once every 5 minutes).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.unlock() }, enabled = !s.unlocking) {
                Text(if (s.unlocking) "Unlocking…" else "Unlock Tiiny now")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.clearLog() }) { Text("Clear log") }
            Spacer(Modifier.width(10.dp))
            val ctx = LocalContext.current
            OutlinedButton(onClick = { shareLog(ctx, s) }) { Text("Share log") }
        }
        if (s.unlocking) LinearProgressIndicator(Modifier.fillMaxWidth())
        Card(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val scroll = rememberScrollState()
            LaunchedEffect(s.log.size) { scroll.animateScrollTo(scroll.maxValue) }
            Text(
                if (s.log.isEmpty()) "No activity yet." else s.log.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(12.dp),
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((v, l) in options) DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
        }
    }
}

@Composable
private fun SettingsScreen(s: UiState, vm: MainViewModel) {
    val cur = s.settings
    // Display changes save at once; they shouldn't reset what is being typed in the other fields.
    val formKey = cur.copy(layout = "", uiScale = "", textScale = "", enterSends = true)
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
    var showDebug by remember { mutableStateOf(false) }

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
        TextButton(onClick = { vm.forgetTiinyLogin() }) { Text("Forget saved Tiiny login key") }

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
            if (s.vpn.wireguardInstalled) TextButton(onClick = { vm.openWireGuard() }) { Text("Open WireGuard") }
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
        TextButton(onClick = { vm.forgetHostKey() }) { Text("Forget saved NAS key") }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        val shareCtx = LocalContext.current
        OutlinedButton(onClick = { shareLog(shareCtx, s) }, modifier = Modifier.fillMaxWidth()) {
            Text("Share log (no keys or passwords)")
        }
        TextButton(onClick = { showDebug = !showDebug }) {
            Text(if (showDebug) "Hide last API answers" else "Show last API answers")
        }
        if (showDebug) {
            val lines = remember(s.updatedAt, s.device) { ApiLog.all() }
            Text(
                if (lines.isEmpty()) "Nothing yet." else lines.joinToString("\n\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        val ctx = LocalContext.current
        val version = remember {
            try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
        }
        Text(
            "Tiiny Remote $version · secrets are stored encrypted on this phone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

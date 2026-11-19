package app.tiinyremote

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
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.platform.LocalContext
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(s.toast) {
        val msg = s.toast
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.toastShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiiny Remote") },
                actions = {
                    IconButton(onClick = { vm.refreshNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Models") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    label = { Text("Unlock") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
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
                0 -> ModelsScreen(s, vm, onOpenSettings = { tab = 2 })
                1 -> UnlockScreen(s, vm)
                else -> SettingsScreen(s, vm)
            }
        }
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
        Text("Unlock after a restart", style = MaterialTheme.typography.titleLarge)
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
private fun SettingsScreen(s: UiState, vm: MainViewModel) {
    val cur = s.settings
    var url by remember(cur) { mutableStateOf(cur.tiinyUrl) }
    var key by remember(cur) { mutableStateOf(cur.apiKey) }
    var host by remember(cur) { mutableStateOf(cur.nasHost) }
    var port by remember(cur) { mutableStateOf(cur.nasPort.toString()) }
    var user by remember(cur) { mutableStateOf(cur.nasUser) }
    var pw by remember(cur) { mutableStateOf(cur.nasPassword) }
    var auto by remember(cur) { mutableStateOf(cur.autoUnlock) }
    var cmd by remember(cur) { mutableStateOf(cur.unlockCommand) }
    var tpw by remember(cur) { mutableStateOf(cur.tiinyPassword) }
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
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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

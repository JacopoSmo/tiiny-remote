package app.tiinyremote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Automations: the list, running one now, results (phone and NAS), and the connectors. */
class AutoViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AutoStore(app)
    private val _list = MutableStateFlow(store.automations())
    val list: StateFlow<List<Automation>> = _list.asStateFlow()
    private val _runs = MutableStateFlow(store.runs())
    val runs: StateFlow<List<AutoRun>> = _runs.asStateFlow()
    private val _nasRuns = MutableStateFlow<List<AutoRun>>(emptyList())
    val nasRuns: StateFlow<List<AutoRun>> = _nasRuns.asStateFlow()
    private val _conn = MutableStateFlow(store.connectors())
    val connectors: StateFlow<Connectors> = _conn.asStateFlow()
    /** What a run started here is doing right now, per automation. */
    private val _running = MutableStateFlow<Map<String, String>>(emptyMap())
    val running: StateFlow<Map<String, String>> = _running.asStateFlow()
    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    fun noteShown() {
        _note.value = null
    }

    fun refresh() {
        _list.value = store.automations()
        _runs.value = store.runs()
        if (_conn.value.nasReady) viewModelScope.launch {
            _nasRuns.value = try {
                withContext(Dispatchers.IO) { NasAgent.runs(_conn.value) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun save(a: Automation) {
        val item = if (a.id.isBlank()) a.copy(id = Automation.newId()) else a
        val old = _list.value.firstOrNull { it.id == item.id }
        val next = if (old == null) _list.value + item else _list.value.map { if (it.id == item.id) item else it }
        store.saveAutomations(next)
        _list.value = next
        AutoScheduler.schedule(getApplication(), item)
        // The NAS gets its list again when a NAS automation changed (or one moved away from it).
        if (item.where == "nas" || old?.where == "nas") pushNas()
    }

    fun delete(a: Automation) {
        val next = _list.value.filter { it.id != a.id }
        store.saveAutomations(next)
        _list.value = next
        AutoScheduler.cancel(getApplication(), a.id)
        if (a.where == "nas") pushNas()
    }

    fun setEnabled(a: Automation, on: Boolean) = save(a.copy(enabled = on))

    fun runNow(a: Automation) {
        if (a.id in _running.value) return
        if (a.where == "nas") {
            viewModelScope.launch {
                _note.value = try {
                    withContext(Dispatchers.IO) { NasAgent.runNow(_conn.value, a.id) }
                    "Started on the NAS: its result shows here when it's done."
                } catch (e: Exception) {
                    e.message
                }
            }
            return
        }
        _running.update { it + (a.id to "Starting…") }
        viewModelScope.launch {
            try {
                AutoRunner.run(getApplication(), a) { what -> _running.update { it + (a.id to what) } }
            } finally {
                _running.update { it - a.id }
                _runs.value = store.runs()
            }
        }
    }

    fun deleteRun(r: AutoRun) {
        store.deleteRuns(setOf(r.id))
        _runs.value = store.runs()
    }

    fun saveConnectors(c: Connectors) {
        store.saveConnectors(c)
        _conn.value = store.connectors()
        if (c.nasReady) pushNas()
    }

    fun testEmail(c: Connectors) = viewModelScope.launch {
        _note.value = try {
            withContext(Dispatchers.IO) { Mail.test(c) }
        } catch (e: Exception) {
            "Email didn't work: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun testNas(c: Connectors) = viewModelScope.launch {
        _note.value = try {
            withContext(Dispatchers.IO) { NasAgent.test(c) }
        } catch (e: Exception) {
            e.message
        }
    }

    private fun pushNas() {
        if (!_conn.value.nasReady) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { NasAgent.push(getApplication()) }
            } catch (e: Exception) {
                _note.value = "Couldn't update the NAS agent: ${e.message}"
            }
        }
    }
}

private val runTime = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)

private fun fmtTime(ms: Long) = runTime.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationsScreen(s: UiState, st: StudioViewModel, av: AutoViewModel, openChat: () -> Unit) {
    val list by av.list.collectAsState()
    val runs by av.runs.collectAsState()
    val nasRuns by av.nasRuns.collectAsState()
    val running by av.running.collectAsState()
    val conn by av.connectors.collectAsState()
    val note by av.note.collectAsState()
    var editing by remember { mutableStateOf<Automation?>(null) }
    var section by remember { mutableStateOf("list") } // list, results, connectors
    var agentInfo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { av.refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = section == "list", onClick = { section = "list" }, label = { SignText(sign("Automate") + " Automate") })
            FilterChip(selected = section == "results", onClick = { section = "results"; av.refresh() }, label = { Text("Results") })
            FilterChip(selected = section == "connectors", onClick = { section = "connectors" }, label = { Text("Connectors") })
        }
        note?.let { n ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { av.noteShown() }) { Text("OK") }
                }
            }
        }
        when (section) {
            "results" -> ResultsList((runs + nasRuns).sortedByDescending { it.at }, st, av, openChat)
            "connectors" -> ConnectorsCard(conn, av)
            else -> {
                Text(
                    "A process fetches something (news, your calendar, email), a model works on it, and you get the result. " +
                        "An agent gets a goal and tools and decides its own steps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Alphabetical: Agent, then Process.
                    OutlinedButton(onClick = { editing = Automation("", "New agent", kind = "agent", time = "", tools = setOf("web", "pages")) }) {
                        BtnLabel("Agent", AgentIcon)
                    }
                    OutlinedButton(onClick = { editing = Automation("", "New process") }) { BtnLabel("Process", ProcessIcon) }
                    TextButton(onClick = { agentInfo = true }) { Text("ⓘ") }
                }
                Text("Start from an example:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in AUTOMATION_TEMPLATES) {
                        TextButton(onClick = { editing = t }) { Text("+ ${t.name}") }
                    }
                }
                if (list.isEmpty()) Text("No automations yet.", style = MaterialTheme.typography.bodyMedium)
                for (a in list) {
                    val last = (runs + nasRuns).filter { it.automationId == a.id }.maxByOrNull { it.at }
                    AutomationCard(a, running[a.id], last, onEdit = { editing = a }, onRun = { av.runNow(a) }, onEnable = { av.setEnabled(a, it) })
                }
                if (list.any { it.where == "phone" && it.scheduled }) {
                    Text(
                        "Phone automations run about at their time, even with the app closed, when the phone can reach the Tiiny " +
                            "(home Wi-Fi, or the VPN already on). Android may delay them by some minutes while the phone sleeps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    editing?.let { e -> AutomationEditor(e, s.models, conn, onSave = { av.save(it); editing = null }, onDelete = { av.delete(it); editing = null }) { editing = null } }
    if (agentInfo) AgentInfo { agentInfo = false }
}

@Composable
private fun AutomationCard(a: Automation, doing: String?, last: AutoRun?, onEdit: () -> Unit, onRun: () -> Unit, onEnable: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignText((if (a.kind == "agent") "$SIGN_AGENT " else "$SIGN_PROCESS ") + a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Switch(checked = a.enabled, onCheckedChange = onEnable)
            }
            val what = if (a.kind == "agent") "Tools: " + a.tools.sorted().joinToString(", ").ifBlank { "none" }
            else mapOf("web" to "Web search", "news" to "News", "calendar" to "Calendar", "email" to "Email")[a.source] ?: "No input"
            Text(
                "${a.whenText()} · ${if (a.where == "nas") "on the NAS" else "on the phone"} · $what",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (doing != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(doing, style = MaterialTheme.typography.bodySmall)
            } else last?.let {
                Text(
                    "Last: ${fmtTime(it.at)} · " + if (it.ok) "done" else "failed: ${it.text.take(80)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row {
                TextButton(onClick = onRun, enabled = doing == null) { BtnLabel("Run now", symbol = "▶") }
                TextButton(onClick = onEdit) { BtnLabel("Edit", symbol = "✎") }
            }
        }
    }
}

@Composable
private fun ResultsList(runs: List<AutoRun>, st: StudioViewModel, av: AutoViewModel, openChat: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    if (runs.isEmpty()) Text("No results yet. Run an automation, or wait for its time.", style = MaterialTheme.typography.bodyMedium)
    for (r in runs) androidx.compose.runtime.key(r.id) {
        var open by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth().clickable { open = !open }) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    (if (r.ok) "" else "⚠ ") + r.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (r.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
                Text(
                    fmtTime(r.at) + (if (r.where == "nas") " · NAS" else " · phone") + (r.model.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (open) {
                    androidx.compose.foundation.text.selection.SelectionContainer { MarkdownText(r.text) }
                    if (r.steps.isNotEmpty()) Text(
                        "Steps:\n" + r.steps.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        if (r.ok) TextButton(onClick = { st.startChatWith(r.name, r.text); openChat() }) { BtnLabel("Continue in chat", symbol = "💬") }
                        TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(r.text)) }) { BtnLabel("Copy", symbol = "⧉") }
                        if (r.where == "phone") TextButton(onClick = { av.deleteRun(r) }) { BtnLabel("Delete", androidx.compose.material.icons.Icons.Filled.Delete) }
                    }
                } else Text(r.text.replace(Regex("[#*_`>]"), "").take(140), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ConnectorsCard(conn: Connectors, av: AutoViewModel) {
    val ctx = LocalContext.current
    var calOk by remember { mutableStateOf(PhoneCalendar.allowed(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calOk = it }
    var host by remember(conn) { mutableStateOf(conn.imapHost) }
    var port by remember(conn) { mutableStateOf(conn.imapPort.toString()) }
    var user by remember(conn) { mutableStateOf(conn.imapUser) }
    var pw by remember(conn) { mutableStateOf(conn.imapPassword) }
    var ssl by remember(conn) { mutableStateOf(conn.imapSsl) }
    var nasUrl by remember(conn) { mutableStateOf(conn.nasUrl) }
    var token by remember(conn) { mutableStateOf(conn.nasToken) }
    var ics by remember(conn) { mutableStateOf(conn.icsUrl) }
    var ntfy by remember(conn) { mutableStateOf(conn.ntfyUrl) }
    fun current() = Connectors(
        host.trim(), port.toIntOrNull()?.takeIf { it in 1..65535 } ?: if (ssl) 993 else 143, user.trim(), pw, ssl,
        ics.trim(), nasUrl.trim(), token.trim(), ntfy.trim(),
    )
    Text(
        "Choose how much to connect: nothing, the calendar, email, or both. Everything stays between your phone, your " +
            "Tiiny and (if you use it) your NAS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("📅 Calendar", style = MaterialTheme.typography.titleSmall)
            Text(
                "Reads the calendars on this phone (Google, Exchange, … whatever is synced), read-only.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (calOk) Text("Allowed ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            else OutlinedButton(onClick = { ask.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow calendar access") }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✉ Email (IMAP, read-only)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Your mail server's IMAP address and an app password (Gmail, Outlook, GMX, Bluewin… all offer one). Nothing is " +
                    "marked as read, moved or deleted.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(host, { host = it }, label = { Text("IMAP server, e.g. imap.gmail.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(110.dp))
                Spacer(Modifier.width(12.dp))
                Switch(checked = ssl, onCheckedChange = { ssl = it; port = if (it) "993" else "143" })
                Spacer(Modifier.width(6.dp))
                Text(if (ssl) "SSL/TLS" else "STARTTLS", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(user, { user = it }, label = { Text("User (your email address)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SecretField(pw, { pw = it }, "App password")
            Row {
                TextButton(onClick = { av.saveConnectors(current()) }) { BtnLabel("Save", symbol = "✓") }
                TextButton(onClick = { av.saveConnectors(current()); av.testEmail(current()) }, enabled = host.isNotBlank() && user.isNotBlank() && pw.isNotBlank()) {
                    BtnLabel("Test", symbol = "🔌")
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🖥 NAS agent (optional, runs 24/7)", style = MaterialTheme.typography.titleSmall)
            Text(
                "A small service in Docker on your NAS runs automations marked \"on the NAS\" even when your phone is off. " +
                    "Set it up from nas-agent/ in the Tiiny Remote repository on GitHub, then enter its address and token here. " +
                    "The app sends it the NAS automations, the Tiiny's address and key, and the email details.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(nasUrl, { nasUrl = it }, label = { Text("Address, e.g. http://192.168.3.10:8765") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SecretField(token, { token = it }, "Token (AGENT_TOKEN on the NAS)")
            Text("The NAS can't read this phone's calendar: give it your calendar's private iCal link instead (optional).", style = MaterialTheme.typography.bodySmall)
            SecretField(ics, { ics = it }, "Private iCal (.ics) link")
            OutlinedTextField(ntfy, { ntfy = it }, label = { Text("Push via ntfy (optional), e.g. https://ntfy.sh/your-topic") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = { av.saveConnectors(current()) }) { BtnLabel("Save", symbol = "✓") }
                TextButton(onClick = { av.saveConnectors(current()); av.testNas(current()) }, enabled = nasUrl.isNotBlank() && token.isNotBlank()) {
                    BtnLabel("Test", symbol = "🔌")
                }
            }
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            var terminal by remember { mutableStateOf(false) }
            // Side by side (scrolling sideways when the screen is narrow).
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { runCatching { uri.openUri("https://github.com/JacopoSmo/tiiny-remote/tree/main/nas-agent") } }) {
                    Text("How to set it up (GitHub)")
                }
                TextButton(onClick = { terminal = true }) { Text("How to set it up (Terminal)") }
            }
            if (terminal) TerminalGuide(
                "NAS agent from the terminal", NAS_STEPS,
                "Keep port 8765 on your home network (don't forward it on the router); away from home you reach it through WireGuard.",
            ) { terminal = false }
        }
    }
}

/** The commands to set up the NAS agent over SSH (Termius, Termux…), step by step, each with Copy. */
private val NAS_STEPS = listOf(
    "1. Connect to the NAS with your SSH app (on Ugreen: Control Panel → Terminal → SSH on). Then make a folder and a secret token, and copy the long token it prints:" to
        "mkdir -p ~/tiiny-agent && cd ~/tiiny-agent\nTOKEN=$(openssl rand -hex 24); echo \$TOKEN",
    "2. Write the setup file (paste the whole block; it puts the token in). The last line shows it: AGENT_TOKEN must not be empty." to
        "cat > docker-compose.yml <<EOF\nservices:\n  tiiny-agent:\n    image: ghcr.io/jacoposmo/tiiny-remote-agent:latest\n" +
        "    container_name: tiiny-agent\n    restart: unless-stopped\n    ports:\n      - \"8765:8765\"\n    environment:\n" +
        "      AGENT_TOKEN: \"\$TOKEN\"\n      TZ: \"Europe/Zurich\"\n    volumes:\n      - tiiny-agent-data:/data\nvolumes:\n" +
        "  tiiny-agent-data:\nEOF\ncat docker-compose.yml",
    "3. Start it (if \"compose\" isn't known: sudo docker-compose up -d):" to "sudo docker compose up -d",
    "If it says denied or unauthorized while pulling the image, let the NAS build it itself instead:" to
        "sed -i 's#image: ghcr.io/jacoposmo/tiiny-remote-agent:latest#build: https://github.com/JacopoSmo/tiiny-remote.git\\#main:nas-agent#' docker-compose.yml\nsudo docker compose up -d --build",
    "4. Check it: the log says \"Tiiny Remote NAS agent … on port 8765\", the status comes back in {…}. The last line shows the NAS's address (192.168.…):" to
        "sudo docker logs tiiny-agent\ncurl -s -H \"Authorization: Bearer \$TOKEN\" http://localhost:8765/status\nhostname -I",
    "5. Here in the app: address http://<NAS address>:8765 and the token, Save, Test. Later, to update the agent:" to
        "cd ~/tiiny-agent && sudo docker compose pull && sudo docker compose up -d",
    "6. Optional, after an update: delete the old images nothing uses any more, to free space on the NAS (images of " +
        "running containers stay):" to "sudo docker image prune -f",
)


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationEditor(
    start: Automation, models: List<ModelInfo>, conn: Connectors,
    onSave: (Automation) -> Unit, onDelete: (Automation) -> Unit, onClose: () -> Unit,
) {
    var a by remember(start) { mutableStateOf(start) }
    val chatModels = models.filter { it.isChat }
    val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { SignText(if (a.kind == "agent") "$SIGN_AGENT Agent" else "$SIGN_PROCESS Process") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a.name, { a = a.copy(name = it.take(60)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // 1. When
                Text("When", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = a.time.isNotBlank(), onCheckedChange = { a = a.copy(time = if (it) "07:00" else "") })
                    Spacer(Modifier.width(8.dp))
                    Text(if (a.time.isNotBlank()) "At a set time" else "Only when I tap Run", style = MaterialTheme.typography.bodyMedium)
                }
                if (a.time.isNotBlank()) {
                    OutlinedTextField(
                        a.time, { t -> a = a.copy(time = t.filter { it.isDigit() || it == ':' }.take(5)) },
                        label = { Text("Time (HH:MM)") }, singleLine = true, isError = runCatching { java.time.LocalTime.parse(a.time) }.isFailure,
                        modifier = Modifier.width(160.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (d in 1..7) FilterChip(
                            selected = d in a.days,
                            onClick = { a = a.copy(days = if (d in a.days) a.days - d else a.days + d) },
                            label = { Text(dayNames[d - 1]) },
                        )
                    }
                }
                SettingDropdown("Runs on", listOf("phone" to "This phone", "nas" to "The NAS agent (24/7)"), a.where) { a = a.copy(where = it) }
                if (a.where == "nas" && !conn.nasReady) Text("Set up the NAS agent under Connectors first.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                // 2. Input or tools
                if (a.kind == "process") {
                    Text("Input", style = MaterialTheme.typography.titleSmall)
                    SettingDropdown(
                        "Get",
                        listOf("none" to "Nothing", "news" to "News", "web" to "Web search", "calendar" to "Calendar", "email" to "Email"),
                        a.source,
                    ) { a = a.copy(source = it, sourceArg = if (it == "calendar") "today" else if (it == "email") "24" else "") }
                    when (a.source) {
                        "news", "web" -> OutlinedTextField(a.sourceArg, { a = a.copy(sourceArg = it.take(200)) },
                            label = { Text(if (a.source == "news") "Topic (blank: top stories)" else "Search for") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        "calendar" -> SettingDropdown("Range", listOf("today" to "Today", "tomorrow" to "Tomorrow", "week" to "Next 7 days"), a.sourceArg) { a = a.copy(sourceArg = it) }
                        "email" -> {
                            SettingDropdown("From the last", listOf("12" to "12 hours", "24" to "24 hours", "72" to "3 days", "168" to "7 days"), a.sourceArg) { a = a.copy(sourceArg = it) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = a.unreadOnly, onCheckedChange = { a = a.copy(unreadOnly = it) })
                                Spacer(Modifier.width(8.dp))
                                Text("Only unread", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (!conn.emailReady) Text("Set up email under Connectors first.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Text("Tools the agent may use", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((k, label) in listOf("web" to "$SIGN_WEB Web search", "news" to "📰 News", "pages" to "📄 Open pages", "calendar" to "📅 Calendar", "email" to "✉ Email")) {
                            FilterChip(selected = k in a.tools, onClick = { a = a.copy(tools = if (k in a.tools) a.tools - k else a.tools + k) }, label = { SignText(label) })
                        }
                    }
                    if (a.tools.any { it in setOf("calendar", "email") } && a.tools.any { it in setOf("web", "news", "pages") }) {
                        Text(
                            "Safety: once the agent has read your calendar or email in a run, it can't search or open pages for the " +
                                "rest of that run, so nothing private can leak out in a search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingDropdown("Most steps", listOf("3", "6", "9", "12").map { it to it }, a.maxSteps.toString()) { a = a.copy(maxSteps = it.toInt()) }
                }
                // 3. Model
                Text("Model", style = MaterialTheme.typography.titleSmall)
                SettingDropdown(
                    "Use",
                    listOf("" to "Whichever chat model is loaded") + chatModels.map { it.id to ((if (it.running) "● " else "") + it.name) },
                    a.model,
                ) { a = a.copy(model = it) }
                if (a.model.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = a.loadModel, onCheckedChange = { a = a.copy(loadModel = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Load it if needed (never unloads another model)", style = MaterialTheme.typography.bodyMedium)
                }
                // 4. Task
                Text(if (a.kind == "agent") "Goal" else "What to do with it", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(a.instruction, { a = a.copy(instruction = it.take(2000)) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (a.kind == "agent") "e.g. Find this week's best offers for a wing foil board under 900 CHF" else "e.g. Summarise in 5 bullet points") })
                // 5. Result
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = a.notify, onCheckedChange = { a = a.copy(notify = it) })
                    Spacer(Modifier.width(8.dp))
                    Text("Notify me with the result (failures always notify)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            val valid = a.name.isNotBlank() && a.instruction.isNotBlank() &&
                (a.time.isBlank() || runCatching { java.time.LocalTime.parse(a.time) }.isSuccess) &&
                (a.kind != "agent" || a.tools.isNotEmpty()) && (a.where != "nas" || conn.nasReady)
            TextButton(onClick = { onSave(a) }, enabled = valid) { BtnLabel("Save", symbol = "✓") }
        },
        dismissButton = if (start.id.isNotBlank()) ({ TextButton(onClick = { onDelete(start) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }) else null,
    )
}

@Composable
private fun AgentInfo(onClose: () -> Unit) {
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text("Agents and processes") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SignText("$SIGN_AGENT Agent: a goal and tools", fontWeight = FontWeight.Bold)
                Text("You give a goal; the model decides itself what to search, which pages to read, what to look up, step by step, until it has an answer (or reaches the step limit). Good for research. Slower, and a smaller model can lose its way: a chat model of 20B or more works best.")
                SignText("$SIGN_PROCESS Process: fixed steps", fontWeight = FontWeight.Bold)
                Text("Always the same: get something (news, calendar, email), the model works on it, you get the result. Predictable and quick: the right choice for daily summaries.")
                Text("Where it runs", fontWeight = FontWeight.Bold)
                Text("• On the phone: nothing to set up. It runs when the phone is on and can reach the Tiiny (home or VPN).\n• On the NAS: a small Docker service runs it around the clock, even with the phone off. A bit of setup once (nas-agent/ on GitHub).")
                Text("Safety", fontWeight = FontWeight.Bold)
                Text("Agents only open links from their own search results, treat page and email text as data (not orders), and once they've read your calendar or email they don't go online again in that run.")
            }
        },
        confirmButton = {},
    )
}

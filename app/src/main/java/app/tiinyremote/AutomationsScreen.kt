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
    init {
        NasAgent.init(app)
    }

    /**
     * The open section (list, results, connectors) and where the page was scrolled. Going back to Automate (from the log
     * opened by a test answer, say) shows them as they were; opening it from the side menu starts on the list, at the top.
     */
    var section by mutableStateOf("list")
    val scroll = androidx.compose.foundation.ScrollState(0)
    var cameBack = false

    fun openFresh() {
        section = "list"
        viewModelScope.launch { scroll.scrollTo(0) }
    }

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
                val before = (try {
                    withContext(Dispatchers.IO) { NasAgent.runs(_conn.value) }
                } catch (e: Exception) {
                    _nasRuns.value
                }).map { it.id }.toSet()
                try {
                    withContext(Dispatchers.IO) { NasAgent.runNow(_conn.value, a.id) }
                } catch (e: Exception) {
                    _note.value = e.message
                    return@launch
                }
                // Shown on its card while the NAS works; its result appears under Results by itself (checked every 10 s,
                // at most 16 minutes: an automation's own limit is 15).
                _running.update { it + (a.id to "Running on the NAS…") }
                try {
                    repeat(96) {
                        kotlinx.coroutines.delay(10_000)
                        val runs = try {
                            withContext(Dispatchers.IO) { NasAgent.runs(_conn.value) }
                        } catch (e: Exception) {
                            return@repeat
                        }
                        _nasRuns.value = runs
                        if (runs.any { it.automationId == a.id && it.id !in before }) return@launch
                    }
                } finally {
                    _running.update { it - a.id }
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

    /** Saves; answerAt names the card ("email", "nas") whose Save was tapped: the answer shows right under its button. */
    fun saveConnectors(c: Connectors, answerAt: String? = null) {
        store.saveConnectors(c)
        _conn.value = store.connectors()
        if (c.nasReady) pushNas(answerAt)
        else answerAt?.let { _tests.value = _tests.value + (it to TestResult(true, "Saved.")) }
    }

    fun testEmail(c: Connectors) = test("email", "Email") {
        try {
            Mail.test(c)
        } catch (e: Exception) {
            val why = generateSequence<Throwable>(e) { it.cause }.toList()
            throw MediaException("Email didn't work: " + when {
                why.any { it is java.net.UnknownHostException } -> "the server ${c.imapHost} wasn't found. Check its address."
                why.any { it is java.net.SocketTimeoutException || it is java.net.ConnectException } ->
                    "no answer from ${c.imapHost} on port ${c.imapPort}. Check the address, port and SSL/TLS."
                why.any { it is javax.mail.AuthenticationFailedException } ->
                    "the server refused the user or password. Most services need an app password, not your normal one."
                else -> e.message ?: e.javaClass.simpleName
            })
        }
    }

    /** A test's answer, shown under its Test button (ok: it worked). */
    data class TestResult(val ok: Boolean, val text: String)

    /** Tests in progress or answered, per connector ("email", "nas"): null = not tested since the screen opened. */
    private val _tests = MutableStateFlow<Map<String, TestResult?>>(emptyMap())
    val tests: StateFlow<Map<String, TestResult?>> = _tests.asStateFlow()
    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    private fun test(key: String, label: String, block: () -> String) = viewModelScope.launch {
        _testing.value = _testing.value + key
        _tests.value = _tests.value + (key to null)
        val r = try {
            TestResult(true, withContext(Dispatchers.IO) { block() })
        } catch (e: Exception) {
            TestResult(false, e.message ?: e.javaClass.simpleName)
        }
        ApiLog.add("$label test: ${r.text}", "Automate", isProblem = !r.ok)
        _tests.value = _tests.value + (key to r)
        _testing.value = _testing.value - key
    }

    fun testNas(c: Connectors) = test("nas", "NAS agent") { NasAgent.test(c) }

    /** A card's answer goes once one of its fields changes: it was about what was there before. */
    fun clearTest(key: String) {
        if (_tests.value[key] != null) _tests.value = _tests.value - key
    }

    private fun pushNas(answerAt: String? = null) {
        if (!_conn.value.nasReady) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { NasAgent.push(getApplication()) }
                val push = if (_conn.value.ntfyUrl.isNotBlank()) " Results also come as ntfy pushes (Test sends one)." else ""
                answerAt?.let { _tests.value = _tests.value + (it to TestResult(true, "Saved and sent to the NAS agent.$push")) }
            } catch (e: Exception) {
                val msg = "Saved on this phone, but the NAS agent couldn't be updated: ${e.message}"
                if (answerAt != null) _tests.value = _tests.value + (answerAt to TestResult(false, msg)) else _note.value = msg
            }
        }
    }
}

/** An agent's tools, alphabetical: key, sign, name. */
private val AGENT_TOOLS = listOf(
    Triple("calendar", "📅", "Calendar"), Triple("email", "✉️", "Email"), Triple("news", "📰", "News"),
    Triple("pages", "📄", "Open pages"), Triple("web", SIGN_WEB, "Web search"),
)

private val runTime = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)

private fun fmtTime(ms: Long) = runTime.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationsScreen(s: UiState, st: StudioViewModel, av: AutoViewModel, openChat: () -> Unit, openLogs: () -> Unit = {}) {
    val list by av.list.collectAsState()
    val runs by av.runs.collectAsState()
    val nasRuns by av.nasRuns.collectAsState()
    val running by av.running.collectAsState()
    val conn by av.connectors.collectAsState()
    val note by av.note.collectAsState()
    var editing by remember { mutableStateOf<Automation?>(null) }
    var section by av::section // list, results, connectors; kept in the view model, so coming back opens the same one
    var agentInfo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (av.cameBack) av.cameBack = false
        else {
            section = "list"
            av.scroll.scrollTo(0)
        }
        av.refresh()
    }

    Column(Modifier.fillMaxSize().verticalScroll(av.scroll).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            "connectors" -> ConnectorsCard(conn, av, openLogs)
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
            val what = if (a.kind == "agent") "Tools: " + AGENT_TOOLS.filter { it.first in a.tools }.joinToString(", ") { it.third }.ifBlank { "none" }
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
private fun ConnectorsCard(conn: Connectors, av: AutoViewModel, openLogs: () -> Unit) {
    val ctx = LocalContext.current
    val testing by av.testing.collectAsState()
    val tests by av.tests.collectAsState()
    var calOk by remember { mutableStateOf(PhoneCalendar.allowed(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calOk = it }
    var host by remember(conn) { mutableStateOf(conn.imapHost) }
    var port by remember(conn) { mutableStateOf(conn.imapPort.toString()) }
    var user by remember(conn) { mutableStateOf(conn.imapUser) }
    var pw by remember(conn) { mutableStateOf(conn.imapPassword) }
    var ssl by remember(conn) { mutableStateOf(conn.imapSsl) }
    var nasUrl by remember(conn) { mutableStateOf(conn.nasUrl) }
    var token by remember(conn) { mutableStateOf(conn.nasToken) }
    // One field per calendar; at least one (empty) field.
    var ics by remember(conn) { mutableStateOf(conn.icsLinks.ifEmpty { listOf("") }) }
    var dropped by remember(conn) { mutableStateOf(0) }
    var ntfy by remember(conn) { mutableStateOf(conn.ntfyUrl) }
    // An answer under Save/Test is about the fields as they were: it goes when one of them changes (not on coming back).
    val emailFields = listOf(host, port, user, pw, ssl)
    val nasFields = listOf(nasUrl, token, ics, ntfy)
    var seenEmail by remember(conn) { mutableStateOf(emailFields) }
    var seenNas by remember(conn) { mutableStateOf(nasFields) }
    if (emailFields != seenEmail) androidx.compose.runtime.SideEffect { seenEmail = emailFields; av.clearTest("email") }
    if (nasFields != seenNas) androidx.compose.runtime.SideEffect { seenNas = nasFields; av.clearTest("nas") }
    fun current() = Connectors(
        host.filterNot { it.isWhitespace() }, port.toIntOrNull()?.takeIf { it in 1..65535 } ?: if (ssl) 993 else 143, user.trim(), pw, ssl,
        ics.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"), nasUrl.trim(), token.trim(), ntfy.trim(),
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
            Text("✉️ Email (IMAP, read-only)", style = MaterialTheme.typography.titleSmall)
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
                TextButton(onClick = { av.saveConnectors(current(), "email") }) { BtnLabel("Save", symbol = "✓") }
                TextButton(onClick = { av.saveConnectors(current()); av.testEmail(current()) }, enabled = host.isNotBlank() && user.isNotBlank() && pw.isNotBlank()) {
                    BtnLabel("Test", symbol = "🔌")
                }
            }
            TestAnswer("email", testing, tests, openLogs)
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
            Text(
                "The NAS can't read this phone's calendar: give it your calendars' private iCal links instead (optional, one per " +
                    "calendar, up to 10).",
                style = MaterialTheme.typography.bodySmall,
            )
            ics.forEachIndexed { i, link ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecretField(
                        link,
                        { v ->
                            // Several links pasted at once (one per line) become several fields.
                            val parts = v.split('\n', '\r', ' ').filter { it.isNotBlank() }
                            if (parts.size > 1) {
                                val all = ics.take(i) + parts + ics.drop(i + 1)
                                dropped = (all.size - 10).coerceAtLeast(0)
                                ics = all.take(10)
                            } else ics = ics.toMutableList().also { it[i] = v.trim() }
                        },
                        if (ics.size > 1) "Private iCal (.ics) link ${i + 1}" else "Private iCal (.ics) link",
                        Modifier.weight(1f),
                    )
                    // ✕ removes a field; on the last one left it empties it.
                    if (ics.size > 1 || link.isNotBlank()) TextButton(onClick = {
                        ics = if (ics.size > 1) ics.filterIndexed { j, _ -> j != i } else listOf("")
                        dropped = 0
                    }) { Text("✕") }
                }
            }
            if (ics.size < 10 && ics.last().isNotBlank()) TextButton(onClick = { ics = ics + "" }) { BtnLabel("Another calendar", symbol = "＋") }
            if (dropped > 0) Text(
                "Up to 10 calendars: the last $dropped pasted link${if (dropped == 1) " was" else "s were"} left out.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            )
            val icsBad = ics.any { it.isNotBlank() && !Regex("^(https?|webcals?)://", RegexOption.IGNORE_CASE).containsMatchIn(it.trim()) }
            if (icsBad) Text(
                "A calendar link starts with https:// (or webcal://).", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            // ntfy needs the whole address and a topic at its end (http://192.168.3.10:8090/tiiny).
            val nt = ntfy.trim()
            val ntfyWhy = when {
                nt.isBlank() -> null
                !nt.startsWith("https://") && !nt.startsWith("http://") -> "The whole address, starting with https:// or http://"
                Regex("^https?://[^/]+/?$").matches(nt) -> "Add the topic at the end, e.g. ${nt.trimEnd('/')}/tiiny"
                else -> null
            }
            OutlinedTextField(
                ntfy, { ntfy = it }, label = { Text("Push via ntfy (optional), e.g. https://ntfy.sh/your-topic") }, singleLine = true,
                isError = ntfyWhy != null, modifier = Modifier.fillMaxWidth(),
                supportingText = ntfyWhy?.let { w -> { Text(w) } },
            )
            Row {
                TextButton(onClick = { av.saveConnectors(current(), "nas") }) { BtnLabel("Save", symbol = "✓") }
                TextButton(onClick = { av.saveConnectors(current()); av.testNas(current()) }, enabled = nasUrl.isNotBlank() && token.isNotBlank()) {
                    BtnLabel("Test", symbol = "🔌")
                }
            }
            TestAnswer("nas", testing, tests, openLogs)
            var guide by remember { mutableStateOf<String?>(null) }
            // The three set-up guides, side by side (scrolling sideways when the screen is narrow).
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { guide = "nas" }) { Text("Set up NAS") }
                TextButton(onClick = { guide = "ical" }) { Text("Set up iCal") }
                TextButton(onClick = { guide = "ntfy" }) { Text("Set up ntfy") }
            }
            when (guide) {
                "nas" -> TerminalGuide(
                    "Set up the NAS agent", NAS_STEPS,
                    "Keep port 8765 on your home network (don't forward it on the router); away from home you reach it through WireGuard.",
                    link = "The same guide on GitHub →" to "https://github.com/JacopoSmo/tiiny-remote/tree/main/nas-agent",
                ) { guide = null }
                "ical" -> TerminalGuide(
                    "Set up iCal (your calendar for the NAS)", ICAL_STEPS,
                    "The link is as good as a password: anyone with it can read your calendar. The app keeps it encrypted and sends " +
                        "it only to your NAS. Automations on the phone don't need it: they read the phone's calendar directly.",
                ) { guide = null }
                "ntfy" -> TerminalGuide(
                    "Set up ntfy (results as push messages)", NTFY_STEPS,
                    "Without ntfy, NAS results are under Automate → Results; with it, they also come to your phone as a notification, " +
                        "failures too.",
                    link = "ntfy's own documentation →" to "https://docs.ntfy.sh/",
                ) { guide = null }
            }
        }
    }
}

/** Where to find a calendar's private iCal link: it comes from the service the calendar syncs with, not from Android. */
private val ICAL_STEPS = listOf(
    "## Google Calendar" to "",
    "1. On the phone, open calendar.google.com in Chrome, and in Chrome's ⋮ menu tick \"Desktop site\" (the mobile view hides the settings)." to "",
    "2. ⚙ Settings → on the left, under \"Settings for my calendars\", your calendar → \"Integrate calendar\"." to "",
    "3. Copy \"Secret address in iCal format\" (it ends in /basic.ics). Each calendar has its own; the same page can reset it." to "",
    "## Outlook.com / Microsoft 365" to "",
    "outlook.live.com (or outlook.office.com) in the browser → ⚙ Settings → Calendar → Shared calendars → Publish a calendar → your " +
        "calendar, \"Can view all details\" → Publish → copy the ICS link. Work accounts sometimes have publishing switched off." to "",
    "## iCloud" to "",
    "Only from an iPhone, iPad or Mac: Calendar → the calendar's ⓘ → Public Calendar on → share the link. This link is public, not secret." to "",
    "## Then here" to "",
    "Paste the link into \"Private iCal (.ics) link\" above, then Save. For more calendars, tap \"＋ Another calendar\" and paste " +
        "the next link (up to 10). The NAS reads them whenever an automation needs your calendar, merges them by time and marks " +
        "each event with its calendar's name." to "",
)

/** ntfy: push messages for NAS results, from the public ntfy.sh or from ntfy on the NAS itself. */
private val NTFY_STEPS = listOf(
    "ntfy (\"notify\") is a free, open-source service for push messages: the NAS agent posts each result to your topic, and the " +
        "ntfy app on the phone shows it as a notification, also when Tiiny Remote isn't running." to "",
    "## Quick: the public server ntfy.sh" to "",
    "1. Install the ntfy app (Play Store or F-Droid), tap ＋ and subscribe to a topic with a long, random name, e.g. tiiny-7f3k9q2x-results " +
        "(anyone who knows the name can read it, so treat it like a password). Or make a name in the terminal:" to "echo tiiny-$(openssl rand -hex 6)",
    "2. Here, in \"Push via ntfy\" above: https://ntfy.sh/<your topic>, then Save. Results pass through ntfy.sh's server." to "",
    "## Private: ntfy on your NAS" to "",
    "1. Connect to the NAS with your SSH app, make a folder and the setup file (paste the whole block; it puts the NAS's address in):" to
        "mkdir -p ~/ntfy && cd ~/ntfy\nIP=$(hostname -I | awk '{print $1}'); echo \$IP\ncat > docker-compose.yml <<EOF\nservices:\n" +
        "  ntfy:\n    image: binwiederhier/ntfy:latest\n    container_name: ntfy\n    command: serve\n    restart: unless-stopped\n" +
        "    ports:\n      - \"8090:80\"\n    environment:\n      NTFY_BASE_URL: \"http://\$IP:8090\"\n" +
        "      NTFY_CACHE_FILE: \"/var/cache/ntfy/cache.db\"\n    volumes:\n      - ntfy-cache:/var/cache/ntfy\nvolumes:\n  ntfy-cache:\nEOF\ncat docker-compose.yml",
    "2. Start it (if \"compose\" isn't known: sudo docker-compose up -d):" to "sudo docker compose up -d",
    "3. In the ntfy app: ＋ → topic \"tiiny\" → \"Use another server\" → http://<NAS address>:8090 → Subscribe. In the subscription's " +
        "settings, turn on instant delivery (your own server has no Google push). Then send a test from the NAS terminal (the SSH app, as in step 1); it should pop up on the phone:" to
        "curl -d \"Hello from the NAS\" http://localhost:8090/tiiny",
    "4. Here, in \"Push via ntfy\" above: http://<NAS address>:8090/tiiny, then Save. Nothing leaves your home network; away from home " +
        "the phone gets the messages once WireGuard is on." to "",
    "5. Later, to update ntfy (and optionally delete the old image afterwards):" to
        "cd ~/ntfy && sudo docker compose pull && sudo docker compose up -d\nsudo docker image prune -f",
)

/** The commands to set up the NAS agent over SSH (Termius, Termux…), step by step, each with Copy. */
private val NAS_STEPS = listOf(
    "1. Connect to the NAS with your SSH app (on Ugreen: Control Panel → Terminal → SSH on). Then make a folder and a secret token, and copy the long token it prints:" to
        "mkdir -p ~/tiiny-agent && cd ~/tiiny-agent\nTOKEN=$(openssl rand -hex 24); echo \$TOKEN",
    "2. Write the setup file (paste the whole block; it puts the token in). The last line shows it: AGENT_TOKEN must not be empty." to
        "cat > docker-compose.yml <<EOF\nservices:\n  tiiny-agent:\n    image: ghcr.io/jacoposmo/tiiny-remote-agent:latest\n" +
        "    container_name: tiiny-agent\n    restart: unless-stopped\n    ports:\n      - \"8765:8765\"\n    environment:\n" +
        "      AGENT_TOKEN: \"\$TOKEN\"\n    volumes:\n      - tiiny-agent-data:/data\nvolumes:\n" +
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
    "Lost the token? The NAS still has it: the first line shows it from the setup file, the second from the running agent " +
        "(they should be the same):" to
        "grep AGENT_TOKEN ~/tiiny-agent/docker-compose.yml\nsudo docker exec tiiny-agent printenv AGENT_TOKEN",
)


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationEditor(
    start: Automation, models: List<ModelInfo>, conn: Connectors,
    onSave: (Automation) -> Unit, onDelete: (Automation) -> Unit, onClose: () -> Unit,
) {
    var a by remember(start) { mutableStateOf(start) }
    // Its own switch: emptying the time field to retype it doesn't turn "At a set time" off.
    var timed by remember(start) { mutableStateOf(start.time.isNotBlank()) }
    var confirmDelete by remember(start) { mutableStateOf(false) }
    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete \"${start.name}\"?") },
        text = { Text("The automation is removed${if (start.where == "nas") " from this app and the NAS" else ""}. This can't be undone; its results stay under Results.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(start) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
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
                    Switch(checked = timed, onCheckedChange = { timed = it; a = a.copy(time = if (it) a.time.ifBlank { "07:00" } else "") })
                    Spacer(Modifier.width(8.dp))
                    Text(if (timed) "At a set time" else "Only when I tap Run", style = MaterialTheme.typography.bodyMedium)
                }
                if (timed) {
                    OutlinedTextField(
                        a.time, { t -> a = a.copy(time = t.filter { it.isDigit() || it == ':' }.take(5)) },
                        label = { Text("Time (HH:MM)") }, singleLine = true, isError = normTime(a.time) == null,
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
                        for ((k, label) in AGENT_TOOLS.map { (k, sign, name) -> k to "$sign $name" }) {
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
                (!timed || normTime(a.time) != null) &&
                (a.kind != "agent" || a.tools.isNotEmpty()) && (a.where != "nas" || conn.nasReady)
            TextButton(onClick = { onSave(a.copy(time = if (timed) normTime(a.time) ?: "" else "")) }, enabled = valid) { BtnLabel("Save", symbol = "✓") }
        },
        dismissButton = if (start.id.isNotBlank()) ({ TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }) else null,
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

/** Under a Test button: "Testing…" while it runs, then its answer (green if it worked, red if not) and a way to the log. */
@Composable
private fun TestAnswer(key: String, testing: Set<String>, tests: Map<String, AutoViewModel.TestResult?>, openLogs: () -> Unit) {
    if (key in testing) {
        Text("Testing…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        return
    }
    val r = tests[key] ?: return
    Column(
        Modifier.fillMaxWidth()
            .background(
                (if (r.ok) androidx.compose.ui.graphics.Color(0xFF43A047) else MaterialTheme.colorScheme.error).copy(alpha = 0.15f),
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text((if (r.ok) "✓ " else "⚠ ") + r.text, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = openLogs, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Open the log (Automate) →") }
    }
}

/** A typed time as HH:MM ("7:00", "0700" and "07:00" all work), or null if it isn't one. */
private fun normTime(t: String): String? {
    val m = Regex("""^(\d{1,2}):?(\d{2})$""").matchEntire(t.trim()) ?: return null
    val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) String.format(java.util.Locale.ROOT, "%02d:%02d", h, min) else null
}

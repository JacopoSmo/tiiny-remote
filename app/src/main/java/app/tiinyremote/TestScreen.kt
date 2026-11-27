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

/*
 * The Test page (sidebar, between Automate and Translate): the fast helper model, the speed test and the quality
 * test, and the NPU faults seen per pair of models. Tests run on the NAS agent when it's set up (so the phone can
 * sleep), else on the phone.
 */

@Composable
internal fun TestScreen(s: UiState, vm: MainViewModel, st: StudioViewModel) {
    val cur = s.settings
    var helper by remember(cur.helperModel) { mutableStateOf(cur.helperModel) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Fast helper model", style = MaterialTheme.typography.titleMedium)
        val chatModels = s.models.filter { it.isChat }
        // 👍 the recommended helpers (Settings → Display → Recommended models), a missing one offered below.
        val helperRecs = if (s.settings.showRecs) Recs.forTasks(listOf("helper")) else emptyList()
        // And those the speed tests found about as quick at a helper's job.
        val speedRecords by st.tests.speedHistory.collectAsState()
        val measuredOk = remember(speedRecords, chatModels, helperRecs) {
            if (s.settings.showRecs) st.tests.measuredHelpers(chatModels, helperRecs.map { it.id }.toSet()) else emptySet()
        }
        val helperM = chatModels.firstOrNull { it.id == helper }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.weight(1f)) {
        SettingDropdown(
            "Helper",
            // ● = loaded now, as in the chat's model menu; mixture-of-experts models carry their tag.
            listOf("" to "None (the chat model does everything)") + chatModels.map { m ->
                // No-break spaces: the ● stays in front of its name, even when a long name wraps.
                m.id to (if (m.running) "●\u00A0" else "") + m.name + (if (m.isMoE) "\u00A0\u00A0" + tag("MoE") else "") +
                    (if (helperRecs.any { it.id == m.id } || m.id in measuredOk) "\u00A0\u00A0👍" else "")
            },
            helper,
        ) {
            // Saved at once (no Save needed), like the display settings.
            helper = it
            vm.setDisplay(cur.copy(helperModel = it))
        }
          }
          // Load (▶) or unload (■) the chosen helper right here, as on the Models page; a swap is asked as there.
          if (helperM != null) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
              when {
                  helperM.id in s.working || helperM.status == "loading" -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                  helperM.running -> IconButton(onClick = { vm.unload(helperM) }) { SignText("■", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                  else -> IconButton(onClick = { vm.load(helperM) }) {
                      Icon(Icons.Filled.PlayArrow, contentDescription = "Load ${helperM.name}", tint = MaterialTheme.colorScheme.primary)
                  }
              }
          }
        }
        helperRecs.firstOrNull { r -> chatModels.none { it.id == r.id } && helperRecs.none { h -> chatModels.any { it.id == h.id } } }?.let { r ->
            var ask by remember { mutableStateOf(false) }
            val shop = LocalModelShop.current
            LaunchedEffect(Unit) { if (shop?.store == null) shop?.loadStore() }
            TextButton(onClick = { ask = true }, enabled = shop?.downloading?.contains(r.id) != true) {
                Text("👍 " + (shop?.nameOf(r.id) ?: r.id) + " · " + r.why + (shop?.sizeOf(r.id)?.let { " · $it" } ?: "") + "  ⤓")
            }
            if (ask) DownloadAsk(listOf(r.id), "👍 " + r.why) { ask = false }
        }
        SignText(
            "A small chat model loaded beside your main one. It names chats, suggests follow-ups, condenses long chats and files, and runs Improve prompt, lyrics and tags. " +
                "It condenses web pages only if the speed test found it at least 25% faster ($SIGN_INFO). ▶ beside the menu loads it, ■ unloads it; the chat's options switch it on or off (⚡). Off or not loaded, the chat model does all this.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto thinking asks the helper", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "With Thinking on Auto, the helper picks Low, Medium or High per question: smarter, but answers start about 2 s later. Off: the app decides from the question's length and words.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = cur.autoAsksHelper, onCheckedChange = { vm.setDisplay(cur.copy(autoAsksHelper = it)) })
        }
        val speed by st.tests.speed.collectAsState()
        val small = chatModels.firstOrNull { it.id == helper && it.running }
        val big = chatModels.firstOrNull { it.running && it.id == st.pick("chat") && it.id != helper }
            ?: chatModels.firstOrNull { it.running && it.id != helper }
        var speedInfo by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(false) }
        // Several pairs at once: up to 3 helpers × up to 9 main models, each pair loaded and tested in turn.
        val byName = chatModels.sortedBy { it.name.lowercase() }
        var testHelpers by remember { mutableStateOf(st.tests.speedSet("helpers").filter { id -> chatModels.any { it.id == id } }) }
        var testMains by remember { mutableStateOf(st.tests.speedSet("mains").filter { id -> chatModels.any { it.id == id } }) }
        val pairs = testMains.sumOf { m -> testHelpers.count { it != m } }
        val batch = testHelpers.isNotEmpty() && testMains.isNotEmpty()
        val testing = speed?.startsWith("Testing") == true
        Box {
            var pick by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth(), enabled = !testing) {
                Text(
                    "Test models: " + (if (!batch) "the loaded pair" else
                        "${testHelpers.size} helper${if (testHelpers.size > 1) "s" else ""} × ${testMains.size} main · $pairs pair${if (pairs != 1) "s" else ""}") + "  ▾",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            ScaledDropdownMenu(expanded = pick, onDismissRequest = { pick = false }) {
                DropdownMenuItem(
                    text = { Text("Only the loaded pair · clear the lists") },
                    onClick = { testHelpers = emptyList(); testMains = emptyList(); st.tests.setSpeedSet("helpers", testHelpers); st.tests.setSpeedSet("mains", testMains); pick = false },
                )
                // Two lists of checkboxes: (title, the chosen and the recommended, how many at most, save).
                // 👍: recommended as helper, or as chat model (Settings → Display → Recommended models).
                val recHelpers = if (s.settings.showRecs) Recs.forTasks(listOf("helper")).map { it.id }.toSet() + measuredOk else emptySet()
                val recMains = if (s.settings.showRecs) Recs.forTasks(Recs.tasksFor("chat")).map { it.id }.toSet() else emptySet()
                for ((title, lists, most, put) in listOf(
                    Quad("Helpers (up to 3)", testHelpers to recHelpers, 3) { l: List<String> -> testHelpers = l; st.tests.setSpeedSet("helpers", l) },
                    Quad("Main models (up to 9)", testMains to recMains, 9) { l: List<String> -> testMains = l; st.tests.setSpeedSet("mains", l) },
                )) {
                    val (chosen, recd) = lists
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }, onClick = {}, enabled = false)
                    for (m in byName) {
                        val on = m.id in chosen
                        // A chosen helper stays choosable as a main model, greyed: it's tested with the other helpers only.
                        val alsoHelper = most == 9 && m.id in testHelpers
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (on) "☑ " else "☐ ") + m.name + (if (m.id in recd) "\u00A0\u00A0👍" else ""),
                                    color = if (alsoHelper) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else Color.Unspecified,
                                )
                            },
                            enabled = on || chosen.size < most,
                            onClick = { put(if (on) chosen - m.id else chosen + m.id) },
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SettingDropdown(
                    "Test runs",
                    listOf(1, 3, 4, 5).map { n -> n.toString() to if (n == 1) "1 (quick, no statistics)" else "$n, averaged" },
                    cur.speedRuns.toString(),
                ) { vm.setDisplay(cur.copy(speedRuns = it.toInt())) }
            }
            TextButton(onClick = { history = true }) { BtnLabel("Statistics", symbol = "📊") }
        }
        if (history) SpeedHistoryDialog(st) { history = false }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The red Stop asks: after this step, or now.
            if (testing) {
                val stopping by st.tests.speedStopping.collectAsState()
                StopAsking(
                    "the speed test",
                    "After this step: the current measurement ends first (it isn't saved). Now: breaks off at once. " +
                        "Finished pairs stay in 📊 Statistics; models loaded before are loaded back.",
                    onStop = { st.tests.stopSpeedTest(now = true) },
                    modifier = Modifier.weight(1f),
                    text = if (stopping) "Stopping after this step…" else "Stop the speed test",
                    stopText = "Stop now",
                    extra = if (stopping) null else ("After this step" to { st.tests.stopSpeedTest(now = false) }),
                )
            }
            else if (batch) OutlinedButton(
                onClick = {
                    st.tests.speedTestAll(
                        testMains.mapNotNull { id -> chatModels.firstOrNull { it.id == id } },
                        testHelpers.mapNotNull { id -> chatModels.firstOrNull { it.id == id } },
                        cur.speedRuns,
                    )
                },
                enabled = pairs > 0 && !testing,
                modifier = Modifier.weight(1f),
            ) { Text("Speed tests: $pairs pair${if (pairs != 1) "s" else ""}, one after the other") }
            else OutlinedButton(
                onClick = { if (big != null && small != null) st.tests.speedTest(big, small, cur.speedRuns) },
                enabled = big != null && small != null && !testing,
                modifier = Modifier.weight(1f),
            ) { Text("Speed test: one at a time or both at once?") }
            IconButton(onClick = { speedInfo = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Which models to use, and why", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (speedInfo) ModelChoiceInfo { speedInfo = false }
        Text(
            when {
                batch && pairs == 0 -> "Choose at least one helper and one other main model."
                batch -> "Tests $pairs pair${if (pairs != 1) "s" else ""} one after the other, ${if (cur.speedRuns == 1) "once" else "${cur.speedRuns} times"} each: " +
                    "only that pair is loaded for its test, your models are loaded back at the end. " +
                    "About ${Tools.fmtDuration(pairs * (cur.speedRuns * 3L + 2) * 60)}; it keeps going in the background. Each pair goes to 📊 Statistics."
                helper.isBlank() -> "Choose a helper first."
                small == null -> "Load the helper and your chat model to run the speed test."
                big == null -> "Load your chat model too to run the speed test."
                else -> "Tests ${big.name} and ${small.name}, alone and at the same time, " +
                    (if (cur.speedRuns == 1) "once (about 2 minutes). Results vary from run to run: 3–5 runs give a steadier average."
                    else "${cur.speedRuns} times with short pauses between, and averages the results (about ${cur.speedRuns * 2} minutes).")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        speed?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        QualitySection(s, st)
    }
}

/** The ⓘ beside the speed test: dense vs. mixture-of-experts models, and which to use for what. */
@Composable
internal fun ModelChoiceInfo(onClose: () -> Unit) {
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text("Which model for what?") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Two kinds of chat models", style = MaterialTheme.typography.titleMedium)
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
                Text("How to use them", style = MaterialTheme.typography.titleMedium)
                Text(
                    "• Everyday chat, web search, summaries, quick drafts: an A3B model.\n" +
                        "• Hard problems, careful writing, analysis you want double-checked: the dense 27B.\n" +
                        "• Helper: a small model that answers without thinking first, like Qwen3-4B-Instruct (25% NPU). It " +
                        "names chats and condenses long ones in about 20 s. gpt-oss-20b writes faster but thinks first, so " +
                        "a helper job takes it about twice as long. It also condenses web pages, but only when it's clearly " +
                        "faster than the chat model (see below).\n" +
                        "• Both fit beside any main model: 27B + Qwen3-4B about 77%, an A3B + Qwen3-4B about 74% of the NPU.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("The speed test measures two different things", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1. How fast each model writes alone. This decides who reads web pages. It barely changes " +
                        "between runs (a few percent), and the speeds are remembered.\n\n" +
                        "2. How much sooner two jobs finish when both models work at the same time instead of one after " +
                        "the other. It varies a lot between runs (the same pair measured 7% and 18%). It shows that the " +
                        "helper can name a chat and suggest follow-ups beside the chat model at no cost: so far every pair " +
                        "came out faster together. From 10% the test calls it \"worth it\". " +
                        "The same model twice brings nothing: the Tiiny takes a model's requests in turn.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Web pages: the 25% rule", style = MaterialTheme.typography.titleMedium)
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
                Text("Test runs", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Run the test once for each chat model you pair with the helper. With 3–5 runs the results are " +
                        "averaged, the spread is shown, and the rules apply to the average, so one lucky or unlucky run " +
                        "doesn't decide. Clear outliers (far from the others, e.g. a hiccup on the Tiiny) are left out: " +
                        "1 of 3 or 4 runs, up to 2 of 5, so at least 3 always count. A short pause comes before every " +
                        "measurement. History keeps every test, to compare pairs and models over time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {},
    )
}

/** Four values in one, for a list of rows. */
internal data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** The quality test: which models, who grades, start or stop, the latest result and 📊 Quality results. */
@Composable
internal fun QualitySection(s: UiState, st: StudioViewModel) {
    val t = st.tests
    val chatModels = s.models.filter { it.isChat }.sortedBy { it.name.lowercase() }
    val quality by t.quality.collectAsState()
    var chosen by remember { mutableStateOf(t.qualitySet().filter { id -> chatModels.any { it.id == id } }) }
    var results by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val testing = quality?.startsWith("Testing") == true
    val judge = Recs.reviewer(s.models)
    val recMains = if (s.settings.showRecs) Recs.forTasks(Recs.tasksFor("chat")).map { it.id }.toSet() else emptySet()
    Text("Quality test", style = MaterialTheme.typography.titleMedium)
    Box {
        var pick by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth(), enabled = !testing) {
            Text(
                "Models: " + (if (chosen.isEmpty()) "choose up to 9" else "${chosen.size} chosen") + "  ▾",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ScaledDropdownMenu(expanded = pick, onDismissRequest = { pick = false }) {
            DropdownMenuItem(text = { Text("Clear the list") }, onClick = { chosen = emptyList(); t.setQualitySet(chosen); pick = false })
            HorizontalDivider()
            for (m in chatModels) {
                val on = m.id in chosen
                DropdownMenuItem(
                    text = { Text((if (on) "☑ " else "☐ ") + m.name + (if (m.id in recMains) "  👍" else "")) },
                    enabled = on || chosen.size < 9,
                    onClick = { chosen = if (on) chosen - m.id else chosen + m.id; t.setQualitySet(chosen) },
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (testing) StopAsking(
            "the quality test",
            "Nothing is graded yet, so nothing is kept. Models loaded before are loaded back.",
            onStop = { t.stopQualityTest(now = true) },
            modifier = Modifier.weight(1f),
            text = "Stop the quality test",
        ) else OutlinedButton(
            onClick = { if (judge != null) t.qualityTest(chosen.mapNotNull { id -> chatModels.firstOrNull { it.id == id } }, judge) },
            enabled = chosen.isNotEmpty() && judge != null,
            modifier = Modifier.weight(1f),
        ) { Text("Quality test: ${chosen.size} model${if (chosen.size != 1) "s" else ""}") }
        TextButton(onClick = { results = true }) { BtnLabel("Results", symbol = "📊") }
        IconButton(onClick = { info = true }) {
            Icon(Icons.Filled.Info, contentDescription = "What the quality test asks", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(
        when {
            judge == null -> "No chat model on the Tiiny to grade the answers."
            chosen.isEmpty() -> "Choose the models to compare. Each answers the same ${QualityMath.TASKS.size} tasks; then ${judge.name} grades them."
            else -> "Each model answers the same ${QualityMath.TASKS.size} tasks, alone on the Tiiny; then ${judge.name} grades each task's " +
                "answers side by side, without knowing who wrote which. About ${Tools.fmtDuration((chosen.size * QualityMath.TASKS.size * 60L) + QualityMath.TASKS.size * 120L + chosen.size * 60L)}; " +
                "it keeps going in the background, and your models are loaded back at the end."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    quality?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    if (results) QualityResultsDialog(st) { results = false }
    if (info) QualityTasksInfo { info = false }
}

/** 📊 Quality results: each model's latest grades, per task, and every test. */
@Composable
internal fun QualityResultsDialog(st: StudioViewModel, onClose: () -> Unit) {
    val all by st.tests.qualityHistory.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f).widthIn(max = 720.dp),
        title = { ClosableTitle(onClose) { Text("Quality results") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (all.isEmpty()) Text("No quality test yet.", style = MaterialTheme.typography.bodyMedium)
                // Each model's latest test, best first; then the grade per task.
                val latest = all.groupBy { it.model }.mapValues { (_, l) -> l.maxBy { it.at } }.values.sortedByDescending { if (it.average.isNaN()) -1.0 else it.average }
                if (latest.isNotEmpty()) Text("Latest grade per model (1–10)", style = MaterialTheme.typography.titleMedium)
                for (r in latest) {
                    Text(
                        "${r.model}: ${SpeedMath.f(r.average)}" + (if (r.secs.isNaN()) "" else " · ${SpeedMath.f(r.secs)} s per answer") +
                            " · graded by ${r.judge}" + (if (r.by == "nas") " · on the NAS" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        QualityMath.TASKS.joinToString(" · ") { task -> "${task.title} ${r.perTask[task.id]?.takeIf { !it.isNaN() }?.let { SpeedMath.f(it) } ?: "—"}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (all.size > latest.size) {
                    HorizontalDivider()
                    Text("Every test", style = MaterialTheme.typography.titleMedium)
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.ENGLISH)
                    for (r in all) Text(
                        java.time.Instant.ofEpochMilli(r.at).atZone(java.time.ZoneId.systemDefault()).format(fmt) +
                            "  ${r.model}: ${SpeedMath.f(r.average)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "The grader sees each task's answers side by side as A, B, C… in a shuffled order, not who wrote them. " +
                        "One test is a small sample: differences under about one point are within chance. A grader that " +
                        "is also among the tested models may favour its own style.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (all.isNotEmpty()) {
                if (confirmClear) TextButton(onClick = { st.tests.clearQualityHistory(); confirmClear = false }) { Text("Delete all results") }
                else TextButton(onClick = { confirmClear = true }) { Text("Clear…") }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** The ⓘ beside the quality test: the tasks, and how grading works. */
@Composable
private fun QualityTasksInfo(onClose: () -> Unit) {
    ScaledAlertDialog(
        onDismissRequest = onClose,
        title = { ClosableTitle(onClose) { Text("The quality test's tasks") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (task in QualityMath.TASKS) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                    Text(task.prompt.take(220) + if (task.prompt.length > 220) "…" else "", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Each model answers as in a chat, with its own thinking. The grader gets each task's answers together, " +
                        "with what a good answer does (and the right result where there is one), and gives 1–10. No answer " +
                        "(an error) counts as 1.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

/** Settings → Background efficiency tools: the night shift (the NAS agent works the night queue) and loading ahead. */
@Composable
internal fun BackgroundTools(s: UiState, vm: MainViewModel) {
    val cur = s.settings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Night shift", style = MaterialTheme.typography.bodyLarge)
            Text(
                "The NAS agent works through the night queue (🌙 Tonight beside Generate, and the tests) in this window, " +
                    "loading the models each job needs; in the morning the results are in the app and a report comes as a notice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = cur.nightShift, onCheckedChange = { vm.setDisplay(cur.copy(nightShift = it)) })
    }
    if (cur.nightShift) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val hours = (0..23).map { it.toString() to "%02d:00".format(it) }
        Box(Modifier.weight(1f)) { SettingDropdown("From", hours, cur.nightFrom.toString()) { vm.setDisplay(cur.copy(nightFrom = it.toInt())) } }
        Box(Modifier.weight(1f)) { SettingDropdown("To", hours, cur.nightTo.toString()) { vm.setDisplay(cur.copy(nightTo = it.toInt())) } }
    }
    SettingDropdown(
        "Load ahead",
        listOf("off" to "Off", "suggest" to "Suggest (a line offers to load)", "auto" to "Automatic (never unloads a model in use)"),
        cur.loadAhead,
    ) { vm.setDisplay(cur.copy(loadAhead = it)) }
    Text(
        "Learns when you use which model and loads it shortly before, so you don't wait for loading. It needs one to " +
            "two weeks of use to guess well; Test → 📊 Statistics shows what it has learned.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

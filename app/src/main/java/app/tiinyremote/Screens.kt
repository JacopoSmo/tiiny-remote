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

private fun pct(v: Double?): String {
    if (v == null) return "?%"
    return if (v % 1.0 == 0.0) "${v.toInt()}%" else String.format(Locale.ROOT, "%.1f%%", v)
}

private fun fmtSec(s: Int): String =
    if (s >= 60) "${s / 60} min" + (if (s % 60 > 0) " ${s % 60} s" else "") else "$s s"

/** Everything useful for debugging in one text: status, the API log and the unlock log. No keys or passwords. */
private fun buildReport(ctx: Context, s: UiState, types: Set<String>? = null, errorsOnly: Boolean = false, picked: List<LogEntry>? = null): String {
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    return buildString {
        append("Tiiny Remote ").append(version).append('\n')
        append("Status: ").append(s.device).append(' ').append(s.deviceMessage).append("\n\n")
        // The same choice as the Activity screen, so Share sends exactly what's shown.
        val shown = picked ?: ApiLog.entries.value.filter { (types == null || it.type in types) && (!errorsOnly || it.error) }
        append("== Log (newest first)")
        if (picked != null) append(": ${picked.size} chosen entr${if (picked.size == 1) "y" else "ies"}")
        else {
            if (types != null) append(": ").append(types.joinToString(", "))
            if (errorsOnly) append(", problems only")
        }
        append(" ==\n")
        append(if (shown.isEmpty()) "(none)" else shown.joinToString("\n\n") { "${it.time}  [${it.type}] ${it.text}" })
    }
}

private fun shareLog(ctx: Context, s: UiState, types: Set<String>? = null, errorsOnly: Boolean = false, picked: List<LogEntry>? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Tiiny Remote log")
        putExtra(Intent.EXTRA_TEXT, buildReport(ctx, s, types, errorsOnly, picked))
    }
    ctx.startActivity(Intent.createChooser(send, "Share log"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.state.collectAsState()
    val st: StudioViewModel = viewModel()
    val av: AutoViewModel = viewModel()
    val job by st.job.collectAsState()
    val anyChatBusy by st.busyChats.collectAsState()
    val studioNote by st.note.collectAsState()
    val navPrefs = androidx.compose.ui.platform.LocalContext.current.let { remember { it.getSharedPreferences("nav", android.content.Context.MODE_PRIVATE) } }
    // The screen the app opens on (Sidebar settings): where you left it, or a chosen one.
    var tab by rememberSaveable {
        mutableIntStateOf(
            s.settings.startScreen.toIntOrNull()?.takeIf { it in ALL_TABS } ?: navPrefs.getInt("last_tab", 0).takeIf { it in ALL_TABS } ?: 0,
        )
    }
    LaunchedEffect(tab) { navPrefs.edit().putInt("last_tab", tab).apply() }
    var toolId by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the Gallery opens (a tool or a kind, from Create's link), or everything.
    var galleryStart by rememberSaveable { mutableStateOf<String?>(null) }
    // Where you came from: ← and Android's back gesture go back through it; with nothing left, back closes the app.
    val history = remember { mutableStateListOf<Pair<Int, String?>>() }
    fun goTab(i: Int) {
        if (i == tab) {
            if (i == 5) av.openFresh() // Automate chosen again while open: back to its list, at the top
            return
        }
        history.add(tab to toolId)
        if (history.size > 30) history.removeAt(0)
        tab = i
    }
    fun openToolNav(id: String?) {
        if (id == toolId || (id == "image" && toolId == null)) return // no tool open = Image → Create
        history.add(tab to toolId)
        if (history.size > 30) history.removeAt(0)
        toolId = id
    }
    /** Straight to a Create tool from another page (Start, the Gallery): one step back returns there. */
    fun openCreate(id: String) {
        history.add(tab to toolId)
        if (history.size > 30) history.removeAt(0)
        tab = 1
        toolId = id
    }
    val canGoBack = history.isNotEmpty() || (tab == 1 && toolId != null && toolId != "image")
    fun back() {
        if (history.isNotEmpty()) {
            val (t, tool) = history.removeAt(history.size - 1)
            if (t == 5) av.cameBack = true // back to Automate: the section and place you left (the side menu starts on its list)
            tab = t
            toolId = tool
        } else if (tab == 1 && toolId != null) toolId = null
    }
    BackHandler(enabled = canGoBack) { back() }
    // A notification can ask for a screen (an automation's result opens Automate).
    val navRequest by Nav.request.collectAsState()
    LaunchedEffect(navRequest) {
        // From a notification, a Quick Settings tile, the widget or the icon's long-press menu (QuickAccess).
        when (navRequest) {
            "automations" -> goTab(5)
            "chat" -> goTab(2)
            "dictate" -> { goTab(2); Nav.dictate.value = true }
            "gallery" -> goTab(6)
            "translate_clip" -> { goTab(7); Nav.translateClip.value = true }
        }
        Nav.request.value = null
    }
    val openTool by st.openTool.collectAsState()
    LaunchedEffect(openTool) {
        if (openTool != null) goTab(1) // "Edit this" from the chat or results goes to the Create tab
    }
    val toChat by st.toChat.collectAsState()
    LaunchedEffect(toChat) {
        if (toChat != null) goTab(2) // "Send to chat" from Create or the Gallery
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

    // Undo after a delete: the bar offers it for ten seconds.
    val undo by st.undo.collectAsState()
    LaunchedEffect(undo) {
        val u = undo ?: return@LaunchedEffect
        // Cleared only once the bar is gone: clearing it first restarted this and took the bar away at once.
        val r = snackbar.showSnackbar(u.msg, actionLabel = "Undo", withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Long)
        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) u.undo()
        if (st.undo.value === u) st.undoShown()
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
    var historyOpen by remember { mutableStateOf(false) }
    val projectList by st.projectList.collectAsState()
    var openProject by remember { mutableStateOf<Project?>(null) }
    var newProject by remember { mutableStateOf(false) }
    val saving by st.saving.collectAsState()
    var renaming by remember { mutableStateOf<ChatInfo?>(null) }
    // The top part in your order (Sidebar settings); Activity, Models and Settings always below.
    val sections = topOrder(s.settings.navOrder).map { i -> navEntry(i) } + listOf(3, 0, 4).map { navEntry(it) }
    var navSettings by remember { mutableStateOf(false) }
    var quitting by remember { mutableStateOf(false) }
    fun go(i: Int) {
        if (i == 6) galleryStart = null // from the menu, the Gallery shows everything
        goTab(i)
        scope.launch { drawer.close() }
    }

    val appDensity = Density(base.density * uiScale, base.fontScale * textScale)
    CompositionLocalProvider(
        LocalDensity provides appDensity,
        LocalAppDensity provides appDensity,
        LocalUi provides UiPrefs(compact = compact, enterSends = s.settings.enterSends, labels = s.settings.labels),
        LocalModelShop provides ModelShop(
            store = s.store, installed = s.models.map { it.id }.toSet(), downloading = s.downloads.map { it.id }.toSet(),
            showRecs = s.settings.showRecs, download = { id, name -> vm.download(id, name) }, loadStore = { vm.loadStore() },
        ),
    ) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(Modifier.widthIn(max = 300.dp)) {
                    Column(Modifier.fillMaxHeight()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
                                Text(AppUpdate.appName(LocalContext.current), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { navSettings = true }) { Icon(Icons.Filled.Settings, contentDescription = "Sidebar settings: start screen and order") }
                            }
                            for ((label, icon, i) in sections) {
                                // Using the Tiiny above, looking after it (Activity, Models, Settings) below.
                                if (i == 3) HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 20.dp))
                                NavigationDrawerItem(
                                    label = { Text(label) },
                                    icon = { Icon(icon, contentDescription = null) },
                                    selected = tab == i,
                                    onClick = { go(i) },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        }
                        // Bottom middle: close the app completely (background connection included).
                        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { quitting = true }) {
                                Icon(PowerIcon, contentDescription = "Shut down the app", tint = MaterialTheme.colorScheme.error)
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
                            // The page title (the scale's first level); compact: the same size, a tighter line for the slim 48 dp bar.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    t, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = if (compact) MaterialTheme.typography.titleLarge.copy(lineHeight = 24.sp)
                                    else MaterialTheme.typography.titleLarge,
                                )
                                // ?: a walk through this page's basic functions.
                                Surface(
                                    onClick = { Guides.request.value = tab },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp).size(24.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        },
                        actions = {
                            if (tab == 2) {
                                // History: earlier chats, newest first; tap one to go back to it.
                                Box {
                                    IconButton(onClick = { historyOpen = true }) { Icon(HistoryIcon, contentDescription = "Chat history") }
                                    ChatHistoryMenu(historyOpen, chats, current, busyChats, { historyOpen = false }, { st.resumeChat(it.id); historyOpen = false },
                                        onRename = { renaming = it; historyOpen = false }, onDelete = { st.deleteChat(it.id) },
                                        projects = projectList, onProject = { openProject = it; historyOpen = false }, onNewProject = { newProject = true; historyOpen = false })
                                    openProject?.let { pr -> ProjectDialog(st, pr, onOpenChat = { st.resumeChat(it); openProject = null }) { openProject = null } }
                                    if (newProject) ProjectNameDialog(null) { n -> newProject = false; if (n != null) openProject = st.createProject(n) }
                                }
                                // Save: green, chats are kept in History; red, incognito (nothing is kept from now on).
                                IconButton(onClick = { st.setSaving(!saving) }) {
                                    Icon(
                                        SaveIcon, tint = if (saving) Color(0xFF43A047) else MaterialTheme.colorScheme.error,
                                        contentDescription = if (saving) "Chats are saved (tap for incognito)" else "Incognito: chats aren't saved (tap to save them)",
                                    )
                                }
                                IconButton(onClick = { st.newChat() }) { Icon(Icons.Filled.Add, contentDescription = "New chat") }
                            }
                            // Chat, Create and the Gallery: what the Tiiny kept of the texts sent from here (chats, Describe,
                            // OCR, prompts…), always just left of ⟳.
                            if (tab == 1 || tab == 2 || tab == 6) {
                                var copies by remember { mutableStateOf(false) }
                                IconButton(onClick = { copies = true }) {
                                    Text("🗃", fontSize = 20.sp, modifier = Modifier.semantics { contentDescription = "Copies kept on the Tiiny" })
                                }
                                if (copies) TiinyCopiesDialog(st) { copies = false }
                            }
                            // Models: 👍 the recommended set (a table, downloads, a review by the Tiiny's own AI).
                            if (tab == 0) {
                                var recsOpen by remember { mutableStateOf(false) }
                                IconButton(onClick = { recsOpen = true }) {
                                    Text("👍", fontSize = 20.sp, modifier = Modifier.semantics { contentDescription = "Recommended models" })
                                }
                                if (recsOpen) RecsDialog(st, s.models) { recsOpen = false }
                            }
                            // Models: "Help me choose" asks the chat model which model fits what you want to do.
                            if (tab == 0) IconButton(onClick = { st.askInChat(HELP_CHOOSE_DRAFT) }) {
                                Text("🧭", fontSize = 20.sp, modifier = Modifier.semantics { contentDescription = "Help me choose a model" })
                            }
                            IconButton(onClick = { vm.refreshNow(); if (tab == 5) av.refresh() }) { // in Automate: its results too
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        },
                    )
                },
                bottomBar = {
                    // On square screens the sidebar replaces the bottom bar, leaving more room for the output.
                    // Material's bar holds five: the main places; the rest are in the ☰ menu.
                    if (!compact) NavigationBar {
                        for ((label, icon, i) in listOf(0, 2, 1, 6, 7).map { n -> sections.first { it.third == n } }) {
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { if (i == 6) galleryStart = null; goTab(i) },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                },
                // Above the keyboard when it opens: an Undo bar must stay visible and reachable.
                snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
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
                        1 -> CreateScreen(s, vm, st, toolId, { openToolNav(it) }, openGallery = { galleryStart = it; goTab(6) })
                        6 -> CreationsScreen(st, galleryStart) { t -> openCreate(t) }
                        7 -> TranslateScreen(s, st)
                        2 -> ChatScreen(s, vm, st)
                        3 -> UnlockScreen(s, vm)
                        5 -> AutomationsScreen(s, st, av, openChat = { goTab(2) }, openLogs = {
                            // The log, showing only what Automate did (the filter stays until you change it there).
                            ctx.getSharedPreferences("log_ui", android.content.Context.MODE_PRIVATE).edit()
                                .putStringSet("types", setOf("Automate")).putBoolean("problems", false).apply()
                            goTab(3)
                        })
                        8 -> CodeScreen(s, vm, st)
                        9 -> StartScreen(st, { goTab(it) }, { openCreate(it) })
                        else -> SettingsScreen(s, vm, st)
                    } }
                }
            }
        }
    }

    if (navSettings) NavSettingsDialog(s, vm) { navSettings = false }
    // The welcome tour once at the first start; a page's guide from the ? beside its title.
    LaunchedEffect(Unit) { if (!navPrefs.getBoolean("tour_seen", false)) Guides.request.value = -1 }
    val guide by Guides.request.collectAsState()
    guide?.let { g ->
        val (title, steps) = if (g == -1) "Welcome" to WELCOME_TOUR else pageGuide(g)
        CompositionLocalProvider(LocalAppDensity provides appDensity) {
            GuideOverlay(title, steps) {
                if (g == -1) navPrefs.edit().putBoolean("tour_seen", true).apply()
                Guides.request.value = null
            }
        }
    }
    if (quitting) QuitDialog(s, vm, anyChatBusy.isNotEmpty() || job?.running == true) { quitting = false }

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
        val victimText = if (sw.victims.isNotEmpty()) sw.victims.joinToString(" and ") { "${it.name} (${pct(it.npuUsage)})" }
        else (sw.plan.candidateName ?: "another model") + (sw.plan.candidateNpu?.let { " (${pct(it)})" } ?: "")
        ScaledAlertDialog(
            onDismissRequest = { vm.cancelSwap() },
            title = { Text("Not enough NPU room") },
            text = {
                Text(
                    "${sw.targetName} needs ${pct(sw.plan.npuRequired)} of the NPU, but only " +
                        "${pct(sw.plan.npuAvailable)} is free. Unload $victimText to make room?"
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
                        if (v.active) "VPN on" else if (v.other) "Another VPN is on" else "VPN off",
                        style = MaterialTheme.typography.titleSmall,
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
            if (v.other) {
                Text(
                    "Another app's VPN (DuckDuckGo's App Tracking Protection, say) is on; WireGuard isn't. Android runs one " +
                        "VPN at a time, so switching WireGuard on stops the other one.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
    Triple("translate", "Translates", { it.isChat }),
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
        s.models.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
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
        .filter { m -> active.all { it.third(m) } && (typeSel.isEmpty() || m.type in typeSel || CODING in typeSel && m.codes) }
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
        // Online/offline and the loaded models are in the status bar at the top; this card only shows when something needs doing.
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
                    // Types: every kind of model the Tiiny has, for both lists.
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
                            // Coding: not a type of its own on the Tiiny, but chat models that are good at code (see Code).
                            val nCode = if (storeView) s.store?.count { (it.type == "Text Generation" || it.type == "Image-Text-to-Text") && codesByName(it.id) } ?: 0
                                else s.models.count { it.codes }
                            DropdownMenuItem(
                                text = { Text((if (CODING in typeSel) "☑ " else "☐ ") + "💻 Coding  ($nCode)") },
                                onClick = {
                                    typeSel = if (CODING in typeSel) typeSel - CODING else typeSel + CODING
                                    prefs.edit().putStringSet("types", typeSel).apply()
                                },
                            )
                            for (t in typeOrder) {
                                val n = if (storeView) s.store?.count { it.type == t } ?: 0 else s.models.count { it.type == t }
                                DropdownMenuItem(
                                    text = { SignText((if (t in typeSel) "☑ " else "☐ ") + typeIcon(t) + " " + t + "  ($n)") },
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
                                    label = { SignText(tag(label)) },
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Updates at a glance: every model with an update, and the updates under way, in one list.
                        val updatingIds = s.downloads.map { it.id }.toSet()
                        val toUpdate = s.models.filter { it.needUpdate || it.id in updatingIds }
                        var updatesOpen by remember { mutableStateOf(false) }
                        if (toUpdate.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${toUpdate.size}") } }) {
                                Surface(
                                    onClick = { updatesOpen = true },
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("⟳", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.semantics { contentDescription = "Update models: ${toUpdate.size}" })
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (updatesOpen) UpdatesDialog(s, vm) { updatesOpen = false }
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
                    updating = s.downloads.firstOrNull { it.id == m.id },
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
                    if (m.thinks) "Thinking" to (if (m.alwaysThinks) "always reasons; depth Low to High on the chat's Thinking slider" else "Off to Max on the chat's Thinking slider") else null,
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
    /** Its update under way (from the Tiiny's download list), shown instead of "Update available". */
    updating: Download? = null,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
    onUnload: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
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
                if (updating != null) {
                    // The update under way: its share, live from the Tiiny's download list.
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                updating.failed -> "Update stopped by a problem"
                                updating.paused -> "Update paused" + (updating.progress?.let { " at ${Math.round(it)}%" } ?: "")
                                else -> "Updating " + (updating.progress?.let { "${Math.round(it)}%" } ?: "…")
                            },
                            style = MaterialTheme.typography.labelLarge, color = Color(0xFFE65100),
                        )
                        updating.progress?.let { LinearProgressIndicator(progress = { (it / 100).toFloat() }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFE65100)) }
                    }
                } else if (m.needUpdate) {
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
                    SignText(tags(m.abilities), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
    "Automate" -> Color(0xFF6D4C41)
    "Code" -> Color(0xFF00ACC1)
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
private fun LogRow(e: LogEntry, chosen: Boolean, choosing: Boolean, onChoose: () -> Unit) {
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

@Composable
internal fun SecretField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
        },
    )
}

@Composable
internal fun SettingDropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
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

/** Four values in one, for a list of rows. */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** The ⓘ beside Labels: what each pictogram means. */
@Composable
private fun PictogramLegend(onClose: () -> Unit) {
    // A Material icon or a text symbol, as the buttons use them.
    val signs = listOf<Pair<Any, String>>(
        Icons.Filled.Menu to "Sidebar: all pages",
        Icons.AutoMirrored.Filled.ArrowBack to "Back to where you were",
        "?" to "Help for this page",
        Icons.Filled.PlayArrow to "Load a model, resume a download",
        "■" to "Unload a model, stop",
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
        "📊" to "Statistics: the Tiiny's own request counts (Activity), speed tests and chat speeds (Settings → Fast helper model)",
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
                        "• Helper: a fast, small MoE like gpt-oss-20b (27% NPU). It names chats and condenses long ones. " +
                        "It also condenses web pages, but only when it's clearly faster than the chat model (see below).\n" +
                        "• Both pairs fit: A3B + gpt-oss about 75%, 27B + gpt-oss about 86% of the NPU.",
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

@Composable
private fun SettingsScreen(s: UiState, vm: MainViewModel, st: StudioViewModel) {
    val cur = s.settings
    // Display changes save at once; they shouldn't reset what is being typed in the other fields.
    val formKey = cur.copy(layout = "", uiScale = "", textScale = "", enterSends = true, keepRunning = false, notifyDone = false, labels = "", helperModel = "", dailyUpdateCheck = false, speedRuns = 1, autoAsksHelper = false, helperOn = true, stayConnected = false, keepAsr = false, keepOcr = false, keepEmbed = false, keepRerank = false, keepTts = false, startScreen = "", navOrder = "", theme = "", style = "", lineIcons = false, lineColour = false, designedFonts = false, oneFace = false, quietStatus = false, showRecs = true, appLock = false, lockAfter = 0)
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
        helperModel = helper,
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
        Text("Fast helper model", style = MaterialTheme.typography.titleMedium)
        val chatModels = s.models.filter { it.isChat }
        // 👍 the recommended helpers (Settings → Display → Recommended models), a missing one offered below.
        val helperRecs = if (s.settings.showRecs) Recs.forTasks(listOf("helper")) else emptyList()
        // And those the speed tests found about as quick at a helper's job.
        val speedRecords by st.speedHistory.collectAsState()
        val measuredOk = remember(speedRecords, chatModels, helperRecs) {
            if (s.settings.showRecs) st.measuredHelpers(chatModels, helperRecs.map { it.id }.toSet()) else emptySet()
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
        val speed by st.speed.collectAsState()
        val small = chatModels.firstOrNull { it.id == helper && it.running }
        val big = chatModels.firstOrNull { it.running && it.id == st.pick("chat") && it.id != helper }
            ?: chatModels.firstOrNull { it.running && it.id != helper }
        var speedInfo by remember { mutableStateOf(false) }
        var history by remember { mutableStateOf(false) }
        // Several pairs at once: up to 3 helpers × up to 9 main models, each pair loaded and tested in turn.
        val byName = chatModels.sortedBy { it.name.lowercase() }
        var testHelpers by remember { mutableStateOf(st.speedSet("helpers").filter { id -> chatModels.any { it.id == id } }) }
        var testMains by remember { mutableStateOf(st.speedSet("mains").filter { id -> chatModels.any { it.id == id } }) }
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
                    text = { Text("Only the loaded pair") },
                    onClick = { testHelpers = emptyList(); testMains = emptyList(); st.setSpeedSet("helpers", testHelpers); st.setSpeedSet("mains", testMains); pick = false },
                )
                // Two lists of checkboxes: (title, the chosen and the recommended, how many at most, save).
                // 👍: recommended as helper, or as chat model (Settings → Display → Recommended models).
                val recHelpers = if (s.settings.showRecs) Recs.forTasks(listOf("helper")).map { it.id }.toSet() + measuredOk else emptySet()
                val recMains = if (s.settings.showRecs) Recs.forTasks(Recs.tasksFor("chat")).map { it.id }.toSet() else emptySet()
                for ((title, lists, most, put) in listOf(
                    Quad("Helpers (up to 3)", testHelpers to recHelpers, 3) { l: List<String> -> testHelpers = l; st.setSpeedSet("helpers", l) },
                    Quad("Main models (up to 9)", testMains to recMains, 9) { l: List<String> -> testMains = l; st.setSpeedSet("mains", l) },
                )) {
                    val (chosen, recd) = lists
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }, onClick = {}, enabled = false)
                    for (m in byName) {
                        val on = m.id in chosen
                        DropdownMenuItem(
                            text = { Text((if (on) "☑ " else "☐ ") + m.name + (if (m.id in recd) "\u00A0\u00A0👍" else "")) },
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
            if (testing && batch) OutlinedButton(onClick = { st.stopSpeedTest() }, modifier = Modifier.weight(1f)) { BtnLabel("Stop after this step", symbol = "■") }
            else if (batch) OutlinedButton(
                onClick = {
                    st.speedTestAll(
                        testMains.mapNotNull { id -> chatModels.firstOrNull { it.id == id } },
                        testHelpers.mapNotNull { id -> chatModels.firstOrNull { it.id == id } },
                        cur.speedRuns,
                    )
                },
                enabled = pairs > 0 && !testing,
                modifier = Modifier.weight(1f),
            ) { Text("Speed tests: $pairs pair${if (pairs != 1) "s" else ""}, one after the other") }
            else OutlinedButton(
                onClick = { if (big != null && small != null) st.speedTest(big, small, cur.speedRuns) },
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

private const val REPO = "https://github.com/JacopoSmo/tiiny-remote"

/** The ⓘ beside the version: what the app is, where it lives, what it sends where, and what it's built with. */
@Composable
private fun AboutDialog(version: String, settings: AppSettings, onClose: () -> Unit) {
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
private fun AboutHead(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

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
private fun AboutBody(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun AboutLink(label: String, onClick: () -> Unit) = Text(
    label,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
)

private val historyTime = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

/** Chat → History: every saved chat, newest first, with when and which model; rename or delete from here too.
 *  A window, not a drop-down: the drop-down's small buttons in a row got no taps on the phone. */
@Composable
private fun ChatHistoryMenu(
    open: Boolean, chats: List<ChatInfo>, current: String, busy: Set<String>,
    onDismiss: () -> Unit, onOpen: (ChatInfo) -> Unit, onRename: (ChatInfo) -> Unit, onDelete: (ChatInfo) -> Unit,
    projects: List<Project> = emptyList(), onProject: (Project) -> Unit = {}, onNewProject: () -> Unit = {},
) {
    if (!open) return
    // Chats of a project are listed in the project; here only the others.
    val ids = projects.map { it.id }.toSet()
    val loose = chats.filter { it.project == null || it.project !in ids }
    var confirm by remember { mutableStateOf<String?>(null) }
    ScaledAlertDialog(
        onDismissRequest = { confirm = null; onDismiss() },
        title = { Text("Chats") },
        text = {
            Column {
                // Projects first: a name, files and chats; each question in them gets the matching pieces of the files.
                for (p in projects) Row(
                    Modifier.fillMaxWidth().clickable { onProject(p) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📁 ${p.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(chats.count { it.project == p.id }.let { n -> if (n == 1) "1 chat" else "$n chats" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Always with its word: a lone ＋ here could mean a new chat as well.
                TextButton(onClick = onNewProject) { Text("＋ New project") }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (loose.isEmpty()) Text("No saved chats yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(loose, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (c.id == current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { onOpen(c) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    historyTime.format(java.time.Instant.ofEpochMilli(c.updated).atZone(java.time.ZoneId.systemDefault())) +
                                        (c.model.takeIf { it.isNotBlank() }?.let { " · " + it.substringAfterLast('/') } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (c.id in busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            IconButton(onClick = { onRename(c) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename", modifier = Modifier.size(20.dp))
                            }
                            // Deleted at once; the Undo bar brings it back.
                            IconButton(onClick = { onDelete(c) }) {
                                Icon(
                                    Icons.Filled.Delete, contentDescription = if (confirm == c.id) "Tap again to delete" else "Delete",
                                    tint = if (confirm == c.id) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        },

        confirmButton = { TextButton(onClick = { confirm = null; onDismiss() }) { Text("Close") } },
    )
}

/**
 * The chats the Tiiny keeps in its vault, per day, from every app (TiinyOS does this; incognito here doesn't stop it).
 * Only whole days can be deleted: the warning says so before anything is deleted.
 */
@Composable
internal fun TiinyCopiesDialog(st: StudioViewModel, onClose: () -> Unit) {
    var days by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<List<String>?>(null) } // the days about to be deleted (the confirmation)
    LaunchedEffect(Unit) { st.tiinyChatDays { d, e -> days = d; error = e } }
    val today = java.time.LocalDate.now().toString()
    val list = ask
    if (list != null) {
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(if (list.size == 1) (if (list[0] == today) "Delete everything kept of today?" else "Delete everything kept of ${list[0]}?") else "Delete all ${list.size} days?") },
            text = {
                Text(
                    "Everything the Tiiny kept of ${if (list.size == 1) "that day" else "those days"} goes: chats from all apps, and the texts " +
                        "sent from Create, Gallery and Translate, with what it remembered from them. TiinyOS's own chat list stays. Can't be undone.",
                )
            },
            confirmButton = { TextButton(onClick = { st.deleteTiinyChatDays(list); ask = null; onClose() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Copies kept on the Tiiny") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The Tiiny keeps a copy of every text it answers, by day: chats from every app (incognito ones too), and what Create, " +
                        "the Gallery and Translate send as text (Describe, OCR, Improve prompt, lyrics, translations). " +
                        "Images, sound and videos it doesn't keep. Only whole days can be deleted.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val d = days
                when {
                    error != null -> Text("Couldn't ask the Tiiny: $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    d == null -> Text("Asking the Tiiny…", style = MaterialTheme.typography.bodySmall)
                    d.isEmpty() -> Text("It keeps nothing right now.", style = MaterialTheme.typography.bodyMedium)
                    else -> Text(
                        "It keeps texts of ${d.size} day${if (d.size == 1) "" else "s"}" + (if (d.size > 1) ", from ${d.first()} to ${d.last()}." else ": ${d[0]}."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            val d = days.orEmpty()
            Row {
                if (today in d) TextButton(onClick = { ask = listOf(today) }) { Text("Delete today", color = MaterialTheme.colorScheme.error) }
                if (d.isNotEmpty()) TextButton(onClick = { ask = d }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/**
 * Models → ⟳: every model with an update and every update under way, with its state: update one, all of them, or
 * pause and resume. The same as each card's "Update available", in one place. Finished ones show "Updated".
 */
@Composable
private fun UpdatesDialog(s: UiState, vm: MainViewModel, onClose: () -> Unit) {
    val downloads = s.downloads.associateBy { it.id }
    // Models updated while this was open stay listed as "Updated" instead of vanishing.
    val seen = remember { mutableStateListOf<String>() }
    val now = s.models.filter { it.needUpdate || it.id in downloads }
    for (m in now) if (m.id !in seen) seen.add(m.id)
    val rows = seen.mapNotNull { id -> s.models.firstOrNull { it.id == id } }
    ScaledAlertDialog(
        onDismissRequest = onClose,
        title = { ClosableTitle(onClose) { Text("Update models") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rows.isEmpty()) Text("All models are up to date.")
                for (m in rows) {
                    val d = downloads[m.id]
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        d == null && !m.needUpdate -> "Updated ✓"
                                        d == null -> "Update available" + (m.sizeBytes.takeIf { it > 0 }?.let { " · ${gbSize(it)}" } ?: "")
                                        d.failed -> "Stopped by a problem: Retry"
                                        d.paused -> "Paused" + (d.progress?.let { " at ${Math.round(it)}%" } ?: "")
                                        else -> "Updating" + (d.progress?.let { " · ${Math.round(it)}%" } ?: "…")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (d == null && !m.needUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when {
                                d == null && m.needUpdate -> TextButton(onClick = { vm.updateModel(m) }) { BtnLabel("Update", symbol = "⟳") }
                                d != null && (d.paused || d.failed) -> TextButton(onClick = { vm.resumeDownload(d) }) { Text(if (d.failed) "Retry" else "Resume") }
                                d != null -> TextButton(onClick = { vm.pauseDownload(d) }) { Text("⏸ Pause") }
                            }
                        }
                        if (d != null && !d.paused && !d.failed) {
                            val p = d.progress
                            if (p != null) LinearProgressIndicator(progress = { (p / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            val waiting = rows.filter { it.needUpdate && it.id !in downloads }
            if (waiting.size > 1) TextButton(onClick = { waiting.forEach { vm.updateModel(it) } }) { BtnLabel("Update all (${waiting.size})", symbol = "⟳") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** Every section's number: 0 Models, 1 Create, 2 Chat, 3 Activity, 4 Settings, 5 Automate, 6 Gallery, 7 Translate, 8 Code. */
private val ALL_TABS = 0..9

/** The sidebar's top part, default order. */
private val DEFAULT_TOP = listOf(9, 2, 8, 1, 6, 5, 7)

/** The top part in the order saved in Settings (numbers missing there, new sections say, go at the end). */
internal fun topOrder(saved: String): List<Int> {
    val mine = saved.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in DEFAULT_TOP }.distinct()
    return mine + DEFAULT_TOP.filter { it !in mine }
}

internal fun navEntry(i: Int): Triple<String, androidx.compose.ui.graphics.vector.ImageVector, Int> = when (i) {
    0 -> Triple("Models", Icons.Filled.Home, 0)
    1 -> Triple("Create", Icons.Filled.Create, 1)
    2 -> Triple("Chat", Icons.AutoMirrored.Filled.Send, 2)
    3 -> Triple("Activity", Icons.AutoMirrored.Filled.List, 3)
    5 -> Triple("Automate", AutomationIcon, 5)
    6 -> Triple("Gallery", GalleryIcon, 6)
    7 -> Triple("Translate", TranslateIcon, 7)
    8 -> Triple("Code", CodeIcon, 8)
    9 -> Triple("Start", LINE_ICONS.getValue("✨"), 9)
    else -> Triple("Settings", Icons.Filled.Settings, 4)
}

/** The sidebar's ⚙: which screen the app opens on, and the order of its top part. */
@Composable
private fun NavSettingsDialog(s: UiState, vm: MainViewModel, onClose: () -> Unit) {
    val cur = s.settings
    val order = topOrder(cur.navOrder)
    fun move(i: Int, by: Int) {
        val l = order.toMutableList()
        val j = i + by
        if (j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }
        vm.setDisplay(cur.copy(navOrder = l.joinToString(",")))
    }
    ScaledAlertDialog(
        onDismissRequest = onClose,
        title = { ClosableTitle(onClose) { Text("Sidebar") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingDropdown(
                    "Start with",
                    listOf("last" to "Where you left off") + (order + listOf(3, 0, 4)).map { it.toString() to navEntry(it).first },
                    cur.startScreen,
                ) { vm.setDisplay(cur.copy(startScreen = it)) }
                Text("Order", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                for ((i, t) in order.withIndex()) {
                    val (label, icon, _) = navEntry(t)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        TextButton(onClick = { move(i, -1) }, enabled = i > 0) { Text("↑") }
                        TextButton(onClick = { move(i, 1) }, enabled = i < order.lastIndex) { Text("↓") }
                    }
                }
                Text(
                    "Activity, Models and Settings stay at the bottom. On taller phones the bottom bar keeps its five.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cur.navOrder.isNotBlank()) TextButton(onClick = { vm.setDisplay(cur.copy(navOrder = "")) }) { Text("Back to the usual order") }
            }
        },
        confirmButton = {},
    )
}

/**
 * The sidebar's ⏻: closes the app completely, background connection included. With models loaded on the Tiiny, asks
 * whether to unload them first (they'd otherwise keep their room on the NPU).
 */
@Composable
private fun QuitDialog(s: UiState, vm: MainViewModel, working: Boolean, onClose: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val loaded = s.models.filter { it.running }
    var closing by remember { mutableStateOf(false) }
    fun quit(unload: Boolean) {
        closing = true
        vm.quitApp(if (unload) loaded else emptyList()) {
            activity?.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    ScaledAlertDialog(
        onDismissRequest = { if (!closing) onClose() },
        title = { Text("Shut down Tiiny Remote?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "The app closes completely, its background connection too. Scheduled automations still run at their time." +
                        if (working) " Something is still being made or answered: it stops." else "",
                )
                if (loaded.isNotEmpty()) Text(
                    "Loaded on the Tiiny: ${loaded.joinToString(", ") { it.name }}. Unload ${if (loaded.size == 1) "it" else "them"} first?",
                    fontWeight = FontWeight.Bold,
                )
                if (closing) Text("Closing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            if (loaded.isNotEmpty()) {
                Button(onClick = { quit(true) }, enabled = !closing) { Text("Unload and close") }
            } else {
                Button(onClick = { quit(false) }, enabled = !closing) { Text("Close") }
            }
        },
        dismissButton = {
            Row {
                if (loaded.isNotEmpty()) TextButton(onClick = { quit(false) }, enabled = !closing) { Text("Keep loaded, close") }
                TextButton(onClick = onClose, enabled = !closing) { Text("Cancel") }
            }
        },
    )
}

/** SearXNG in Docker on the NAS, over SSH: what Settings → Web search → Set up SearXNG shows. */
private val SEARX_STEPS = listOf(
    "1. Connect to the NAS with your SSH app (on Ugreen: Control Panel → Terminal → SSH on). Then make a folder and SearXNG's settings: " +
        "JSON answers on (the app needs them), the bot limiter off (it's only for you), a random secret." to
        "mkdir -p ~/searxng/config && cd ~/searxng\ncat > config/settings.yml <<EOF\nuse_default_settings: true\nserver:\n" +
        "  secret_key: \"$(openssl rand -hex 32)\"\n  limiter: false\n  image_proxy: false\nsearch:\n  formats:\n    - html\n    - json\nEOF",
    "2. Write the setup file (paste the whole block):" to
        "cat > docker-compose.yml <<'EOF'\nservices:\n  searxng:\n    image: searxng/searxng:latest\n    container_name: searxng\n" +
        "    restart: unless-stopped\n    ports:\n      - \"8888:8080\"\n    volumes:\n      - ./config:/etc/searxng\nEOF",
    "3. Start it (if \"compose\" isn't known: sudo docker-compose up -d):" to "sudo docker compose up -d",
    "4. Check it: after a few seconds the search should answer with {\"query\": \"test\"…; the last line shows the NAS's address (192.168.…). " +
        "If it doesn't answer, sudo docker logs searxng says why." to
        "curl -s \"http://localhost:8888/search?q=test&format=json\" | head -c 300; echo\nhostname -I",
    "5. Here in Settings: SearXNG address http://<NAS address>:8888, then Test. Later, to update SearXNG:" to
        "cd ~/searxng && sudo docker compose pull && sudo docker compose up -d",
    "6. Optional, after an update: delete the old images nothing uses any more, to free space on the NAS (images of " +
        "running containers stay):" to "sudo docker image prune -f",
)

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

internal fun pct(v: Double?): String {
    if (v == null) return "?%"
    return if (v % 1.0 == 0.0) "${v.toInt()}%" else String.format(Locale.ROOT, "%.1f%%", v)
}

internal fun fmtSec(s: Int): String =
    if (s >= 60) "${s / 60} min" + (if (s % 60 > 0) " ${s % 60} s" else "") else "$s s"

/** Everything useful for debugging in one text: status, the API log and the unlock log. No keys or passwords. */
internal fun buildReport(ctx: Context, s: UiState, types: Set<String>? = null, errorsOnly: Boolean = false, picked: List<LogEntry>? = null): String {
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

internal fun shareLog(ctx: Context, s: UiState, types: Set<String>? = null, errorsOnly: Boolean = false, picked: List<LogEntry>? = null) {
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
                    if (tab == 2 || tab == 9) LoadAheadLine(s, vm)
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
                        10 -> TestScreen(s, vm, st)
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

    s.faultWarn?.let { w ->
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.ENGLISH)
        val last = java.time.Instant.ofEpochMilli(w.last).atZone(java.time.ZoneId.systemDefault()).format(fmt)
        val isHelper = s.settings.helperModel == w.other.id
        ScaledAlertDialog(
            onDismissRequest = { vm.answerFaultWarn("cancel") },
            title = { Text("This pair upset the NPU before") },
            text = {
                Text(
                    "With ${w.model.name} and ${w.other.name} on the NPU together, the Tiiny reported an NPU fault " +
                        "${w.count} times (the last on $last); each time the NPU restarted. " +
                        "Unloading ${w.other.name} first makes them work one after the other instead." +
                        if (isHelper) " It's your helper: it gets switched off in the chat's options." else "",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.answerFaultWarn("first") }) { Text("Unload ${w.other.name} first") }
                    TextButton(onClick = { vm.answerFaultWarn("anyway") }) { Text("Load anyway") }
                    TextButton(onClick = { vm.answerFaultWarn("silence") }) { Text("Don't warn again for this pair") }
                }
            },
            dismissButton = { TextButton(onClick = { vm.answerFaultWarn("cancel") }) { BtnLabel("Cancel", androidx.compose.material.icons.Icons.Filled.Close) } },
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
internal fun StatusCard(s: UiState, onUnlock: () -> Unit, onSettings: () -> Unit) {
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
internal fun VpnCard(s: UiState, vm: MainViewModel, onSettings: () -> Unit) {
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

internal val FILTERS = listOf<Triple<String, String, (ModelInfo) -> Boolean>>(
    Triple("chat", "Chat", { it.isChat }),
    Triple("makes", "Makes things", { it.makesThings }),
    Triple("sees", "Sees images", { it.isChat && it.seesImages }),
    Triple("thinks", "Thinks", { it.thinks }),
    Triple("moe", "Mixture of experts", { it.isMoE }),
    Triple("translate", "Translates", { it.isChat }),
)

internal val SORTS = listOf("name" to "Name", "type" to "Type", "npu" to "NPU load")

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


/** Chat → History: every saved chat, newest first, with when and which model; rename or delete from here too.
 *  A window, not a drop-down: the drop-down's small buttons in a row got no taps on the phone. */
@Composable
internal fun ChatHistoryMenu(
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
internal fun UpdatesDialog(s: UiState, vm: MainViewModel, onClose: () -> Unit) {
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

/** Every section's number: 0 Models, 1 Create, 2 Chat, 3 Activity, 4 Settings, 5 Automate, 6 Gallery, 7 Translate, 8 Code, 9 Start, 10 Test. */
internal val ALL_TABS = 0..10

/** The sidebar's top part, default order. */
internal val DEFAULT_TOP = listOf(9, 2, 8, 1, 6, 5, 10, 7)

/** The top part in the order saved in Settings (numbers missing there, new sections say, go at the end). */
internal fun topOrder(saved: String): List<Int> {
    val mine = saved.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in DEFAULT_TOP }.distinct().toMutableList()
    // A section new since the order was saved goes where it is by default: after the one before it there (Test after
    // Automate), else at the end.
    for ((i, t) in DEFAULT_TOP.withIndex()) if (t !in mine) {
        val after = DEFAULT_TOP.getOrNull(i - 1)?.let { mine.indexOf(it) } ?: -1
        if (after >= 0) mine.add(after + 1, t) else mine.add(t)
    }
    return mine
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
    10 -> Triple("Test", TestIcon, 10)
    9 -> Triple("Start", LINE_ICONS.getValue("✨"), 9)
    else -> Triple("Settings", Icons.Filled.Settings, 4)
}

/** The sidebar's ⚙: which screen the app opens on, and the order of its top part. */
@Composable
internal fun NavSettingsDialog(s: UiState, vm: MainViewModel, onClose: () -> Unit) {
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
internal fun QuitDialog(s: UiState, vm: MainViewModel, working: Boolean, onClose: () -> Unit) {
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
internal val SEARX_STEPS = listOf(
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

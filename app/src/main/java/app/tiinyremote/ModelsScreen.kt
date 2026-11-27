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

// The Models page: installed models and the Store, each model's card and its info.

@Composable
internal fun ModelsScreen(s: UiState, vm: MainViewModel, st: StudioViewModel, onOpenSettings: () -> Unit) {
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
internal fun ModelInfoDialog(m: ModelInfo, onClose: () -> Unit) {
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
internal fun ModelCard(
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

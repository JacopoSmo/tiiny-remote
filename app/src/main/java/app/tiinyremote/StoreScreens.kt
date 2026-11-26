package app.tiinyremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import java.util.Locale

internal fun gbSize(bytes: Long): String = if (bytes <= 0) "?" else String.format(Locale.ROOT, "%.1f GB", bytes / 1e9)

private val ORANGE = Color(0xFFE65100)

/** Downloads in progress: name, progress, Pause/Resume and Cancel. */
@Composable
internal fun DownloadsCard(s: UiState, vm: MainViewModel) {
    if (s.downloads.isEmpty()) return
    // Cancelling deletes what's downloaded so far on the Tiiny: asked first, never on one tap.
    var cancel by remember { mutableStateOf<Download?>(null) }
    val updates by vm.updates.collectAsState()
    cancel?.let { d ->
        if (d.id in updates) ScaledAlertDialog(
            // An update: the Tiiny removed the old copy when it began, so stopping leaves none. Pause is the safe way.
            onDismissRequest = { cancel = null },
            title = { Text("Stop updating ${d.name}?") },
            text = { Text("The Tiiny removed the old copy when the update began. Stopping deletes the new one too, so ${d.name} is gone until you download it again. Pause keeps what's downloaded.") },
            confirmButton = {
                Row {
                    TextButton(onClick = { if (!d.paused) vm.pauseDownload(d); cancel = null }) { Text("Pause") }
                    TextButton(onClick = { vm.cancelDownload(d); cancel = null }) { Text("Stop and delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { cancel = null }) { Text("Keep updating") } },
        ) else ScaledAlertDialog(
            onDismissRequest = { cancel = null },
            title = { Text("Cancel downloading ${d.name}?") },
            text = { Text("The Tiiny deletes what it has downloaded of this model so far.") },
            confirmButton = { TextButton(onClick = { vm.cancelDownload(d); cancel = null }) { Text("Cancel download", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { cancel = null }) { Text("Keep downloading") } },
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Downloading on the Tiiny", style = MaterialTheme.typography.titleSmall)
            for (d in s.downloads) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(
                            (d.progress?.let { "${Math.round(it)}%" } ?: "") + if (d.failed) " · stopped by an error" else if (d.paused) " · paused" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ((d.progress ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
                        color = if (d.failed) MaterialTheme.colorScheme.error else if (d.paused) MaterialTheme.colorScheme.outline else ORANGE,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    Row {
                        if (d.failed) TextButton(onClick = { vm.resumeDownload(d) }) { BtnLabel("Retry", Icons.Filled.Refresh) }
                        else if (d.paused) TextButton(onClick = { vm.resumeDownload(d) }) { BtnLabel("Resume", Icons.Filled.PlayArrow) }
                        else TextButton(onClick = { vm.pauseDownload(d) }) { BtnLabel("Pause", symbol = "⏸") }
                        Spacer(Modifier.weight(1f))
                        DangerButton("Cancel", onClick = { cancel = d }, icon = Icons.Filled.Close)
                    }
                }
            }
            Text(
                "The Tiiny downloads over its own Wi-Fi (without internet there, a download stops with an error); the progress updates every 5 s.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the store says a model can do, in a few words. */
private fun storeAbilities(m: StoreModel): String = buildList {
    if (m.thinks) add("Thinks")
    if ("Tool Use" in m.supported) add("Makes things")
    if (m.input.contains("Image") && (m.type == "Image-Text-to-Text" || m.type == "Text Generation")) add("Sees images")
    addAll(m.supported.filter { it != "Tool Use" && it != "Reasoning" })
}.distinct().joinToString(" · ")

/** The Model Store: search, every model with its size, NPU share and an ⓘ, and Download. */
/** Models' 🧭 (top right): Chat opens with this, you add what you want to do. */
const val HELP_CHOOSE_DRAFT = "Which model, on my Tiiny or in the Store, fits best for this: "

@Composable
internal fun StoreList(s: UiState, vm: MainViewModel, freeGb: Double?, types: Set<String> = emptySet()) {
    var query by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<StoreModel?>(null) }
    var confirm by remember { mutableStateOf<StoreModel?>(null) }
    val installed = s.models.associateBy { it.id }
    val downloading = s.downloads.map { it.id }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search the store") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.loadStore() }) { Icon(Icons.Filled.Refresh, contentDescription = "Reload the store") }
        }
        if (freeGb != null) {
            Text(
                "Free on the Tiiny: ${Math.round(freeGb)} GB",
                style = MaterialTheme.typography.bodySmall,
                color = if (freeGb < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            s.storeLoading && s.store == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Opening the Model Store…")
            }
            s.storeError != null -> Text(s.storeError, color = MaterialTheme.colorScheme.error)
        }
        val list = (s.store ?: emptyList()).filter { m ->
            types.isEmpty() || m.type in types ||
                CODING in types && (m.type == "Text Generation" || m.type == "Image-Text-to-Text") && codesByName(m.id)
        }.filter { m ->
            query.isBlank() || listOf(m.name, m.id, m.type, m.desc).any { it.contains(query.trim(), true) }
        }
        if (s.store != null) {
            Text(
                "${list.size} of ${s.store.size} models · ${s.store.count { it.id in installed }} on your Tiiny",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (m in list) {
            val have = installed[m.id]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        IconButton(onClick = { info = m }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Info, contentDescription = "About this model", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        listOfNotNull(
                            m.type.takeIf { it.isNotBlank() },
                            m.params.takeIf { it.isNotBlank() },
                            gbSize(m.sizeBytes),
                            m.npuUsage?.let { "${Math.round(it)}% NPU" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    storeAbilities(m).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                m.id in downloading -> "Downloading…"
                                have?.needUpdate == true -> "On your Tiiny · update available"
                                have != null -> "On your Tiiny"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (have != null || m.id in downloading) ORANGE else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        when {
                            m.id in downloading -> {}
                            have?.needUpdate == true -> Button(onClick = { vm.updateModel(have) }) { BtnLabel("Update", symbol = "⟳") }
                            have == null -> Button(onClick = { confirm = m }) { BtnLabel("Download", symbol = "⤓") }
                        }
                    }
                }
            }
        }
    }

    info?.let { m -> StoreInfoDialog(m) { info = null } }
    confirm?.let { m ->
        val tight = freeGb != null && m.sizeBytes / 1e9 > freeGb - 5
        ScaledAlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Download ${m.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${gbSize(m.sizeBytes)} · the Tiiny downloads it over its own Wi-Fi, which needs internet.")
                    freeGb?.let { Text("Free on the Tiiny: ${Math.round(it)} GB", color = if (tight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                    if (tight) Text("That's about all the free space: delete a model first.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button(onClick = { vm.download(m.id, m.name); confirm = null }) { BtnLabel("Download", symbol = "⤓") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { BtnLabel("Cancel", Icons.Filled.Close) } },
        )
    }
}

/** "About this model" for a store model. */
@Composable
private fun StoreInfoDialog(m: StoreModel, onClose: () -> Unit) {
    ScaledAlertDialog(
        onDismissRequest = onClose,
        title = { Text(m.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About this model", style = MaterialTheme.typography.titleSmall)
                Text(m.desc.replace("**", "").ifBlank { "The store has no description for this model." }, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                val rows = listOfNotNull(
                    "ID" to m.id,
                    m.type.takeIf { it.isNotBlank() }?.let { "Type" to it },
                    m.params.takeIf { it.isNotBlank() }?.let { "Parameters" to it },
                    "Download size" to gbSize(m.sizeBytes),
                    m.npuUsage?.let { "NPU" to "${Math.round(it)}% of the NPU when loaded" },
                    m.contextWindow?.let { "Context" to if (it >= 1024) "${it / 1024}K tokens" else "$it tokens" },
                    if (m.input.isNotBlank() || m.output.isNotBlank()) "In → out" to "${m.input.replace(",", ", ")} → ${m.output}" else null,
                    m.supported.takeIf { it.isNotEmpty() }?.let { "Supports" to it.joinToString(", ") },
                    if (m.thinks) "Thinking" to "yes" else null,
                    m.versionLabel.takeIf { it.isNotBlank() }?.let { "Version" to it },
                )
                for ((k, v) in rows) {
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { BtnLabel("Close", Icons.Filled.Close) } },
    )
}

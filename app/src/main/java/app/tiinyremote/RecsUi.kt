package app.tiinyremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What any screen needs to offer a model from the Store: the list, what's on the Tiiny, and Download. */
class ModelShop(
    val store: List<StoreModel>?,
    val installed: Set<String>,
    val downloading: Set<String>,
    val showRecs: Boolean,
    val download: (String, String) -> Unit,
    val loadStore: () -> Unit,
) {
    fun storeModel(id: String) = store?.firstOrNull { it.id == id }
    fun sizeOf(id: String) = storeModel(id)?.sizeBytes?.takeIf { it > 0 }?.let { "%.1f GB".format(it / 1e9) }
    fun nameOf(id: String) = storeModel(id)?.name ?: id.substringAfterLast('/')
}

val LocalModelShop = staticCompositionLocalOf<ModelShop?> { null }

/**
 * Asks once before a download: the model, why, its size and NPU share, and what's free on the Tiiny. [ids] may be
 * several ("Download all missing"): the sizes are added up.
 */
@Composable
internal fun DownloadAsk(ids: List<String>, why: String?, onClose: () -> Unit) {
    val shop = LocalModelShop.current ?: return onClose()
    LaunchedEffect(Unit) { if (shop.store == null) shop.loadStore() }
    val total = ids.sumOf { shop.storeModel(it)?.sizeBytes ?: 0L }
    val free = Stats.freeGb
    val tooBig = free != null && total / 1e9 > free - 2
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (ids.size == 1) "Download ${shop.nameOf(ids[0])}?" else "Download ${ids.size} models?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                why?.let { Text(it) }
                if (ids.size > 1) for (id in ids) Text("• ${shop.nameOf(id)}" + (shop.sizeOf(id)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                Text(
                    (if (total > 0) "%.1f GB".format(total / 1e9) else "Size not known yet") +
                        (if (ids.size == 1) shop.storeModel(ids[0])?.npuUsage?.let { " · ${Math.round(it)}% of the NPU when loaded" } ?: "" else "") +
                        (free?.let { " · free on the Tiiny: ${Math.round(it)} GB" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tooBig) Text("Not enough room: delete a model you don't use first (Models).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("The Tiiny downloads it over its own Wi-Fi; Models shows the progress.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = !tooBig, onClick = {
                ids.forEach { shop.download(it, shop.nameOf(it)) }
                onClose()
            }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/**
 * The recommended set as a table: one row per task, the model, why, and ✓ (on the Tiiny), ⤓ (downloadable) or …
 * (downloading). Download all missing; and Update with the Tiiny's AI, which reviews the Store anew in the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecsDialog(st: StudioViewModel, models: List<ModelInfo>, focus: List<String> = emptyList(), onClose: () -> Unit) {
    val shop = LocalModelShop.current
    val set by Recs.set.collectAsState()
    val busy by st.recBusy.collectAsState()
    val result by st.recResult.collectAsState()
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { if (shop?.store == null) shop?.loadStore() }
    ask?.let { DownloadAsk(it, null) { ask = null } }
    val missing = set.recs.map { it.id }.distinct().filter { shop != null && it !in shop.installed && it !in shop.downloading && shop.storeModel(it) != null }
    val reviewer = Recs.reviewer(models)
    // Built by hand rather than AlertDialog: its button area (24 dp + button + 24 dp) is too tall under a long list.
    BasicAlertDialog(onDismissRequest = onClose) {
      Surface(shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
       Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 6.dp)) {
        Text("👍 Recommended models", style = MaterialTheme.typography.headlineSmall, color = AlertDialogDefaults.titleContentColor)
        Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f, fill = false).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "As of ${set.asOf} · by ${set.by}. ✓ on your Tiiny · ⤓ tap to download · … downloading.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (t in Recs.TASKS) {
                    val recs = set.recs.filter { it.task == t.key }
                    if (recs.isEmpty()) continue
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        t.label, style = MaterialTheme.typography.titleSmall,
                        color = if (t.key in focus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    for (r in recs) {
                        val have = shop?.installed?.contains(r.id) == true
                        val loading = shop?.downloading?.contains(r.id) == true
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(shop?.nameOf(r.id) ?: r.id.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    // The NPU share comes from the Store; a reason that already names it would show it twice.
                                    run {
                                        val npu = shop?.storeModel(r.id)?.npuUsage
                                        val why = if (npu != null) r.why.replace(Regex("""[,;·]?\s*\d+\s*% NPU"""), "").trim() else r.why
                                        why + (shop?.sizeOf(r.id)?.let { " · $it" } ?: "") + (npu?.let { " · ${Math.round(it)}% NPU" } ?: "")
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when {
                                have -> Text("✓", color = MaterialTheme.colorScheme.primary)
                                loading -> Text("…")
                                shop?.storeModel(r.id) != null -> TextButton(onClick = { ask = listOf(r.id) }) { Text("⤓") }
                                else -> Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (missing.isNotEmpty()) OutlinedButton(onClick = { ask = missing }) { Text("⤓ Download all missing (${missing.size})") }
                Text(
                    "Update with the Tiiny's AI: " + (reviewer?.let { "${it.name} reads the Store's list and decides anew, in the background (several minutes; other models are put back afterwards)." }
                        ?: "needs a chat model on the Tiiny."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = busy == null && reviewer != null && shop?.store != null,
                        onClick = { st.reviewRecommendations(shop?.store.orEmpty(), models) },
                    ) { Text("✦ Update with the Tiiny's AI") }
                    busy?.let {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        var ask by remember { mutableStateOf(false) }
                        StopButton(onClick = { ask = true })
                        if (ask) StopAsk(
                            "the review", "The recommendations stay as they are; models unloaded for it are loaded back.",
                            onStop = { st.stopReview() }, onClose = { ask = false },
                        )
                    }
                }
                busy?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (set.by != "public benchmarks") TextButton(onClick = { Recs.reset(ctx) }) { Text("Back to the built-in set") }
            }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Close") }
        }
       }
      }
    }
}

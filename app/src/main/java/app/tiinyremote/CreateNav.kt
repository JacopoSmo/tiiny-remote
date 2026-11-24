package app.tiinyremote

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One of Create's five kinds, with its choices (tool ids), in Tiiny Studio's order. */
data class Category(val id: String, val label: String, val icon: String, val tools: List<String>)

internal val CATEGORIES = listOf(
    Category("3d", "3D", "🧊", listOf("mesh")),
    Category("audio", "Audio", "🎧", listOf("speech", "loop", "song", "transcribe")),
    Category("image", "Image", "🖼", listOf("image", "describe", "edit", "ocr")),
    Category("text", "Text", "📝", listOf("classify", "embed", "rerank")),
    Category("video", "Video", "🎬", listOf("video")),
)

/** The choices' names and pictograms; OCR and ASR are known by their letters, so they get none. */
internal val CHOICES = mapOf(
    "image" to ("✦" to "Create"), "describe" to ("👁" to "Describe"), "edit" to ("✎" to "Edit"), "ocr" to ("" to "OCR"),
    "speech" to ("🗣" to "Voice"), "loop" to ("➰" to "Loop"), "song" to ("🎵" to "Song"), "transcribe" to ("" to "ASR"),
    "classify" to ("🏷" to "Classification"), "embed" to ("🔗" to "Embedding"), "rerank" to ("🥇" to "Reranking"),
    "mesh" to ("🧊" to "3D model"), "video" to ("🎬" to "Video"),
)

internal fun categoryOf(tool: String) = CATEGORIES.firstOrNull { tool in it.tools } ?: CATEGORIES[2]

/** A label as words, pictogram or both (Settings → Labels); without a pictogram, always the word. */
@Composable
internal fun choiceLabel(icon: String, word: String): String {
    if (icon.isEmpty()) return word
    return when (LocalUi.current.labels) {
        "words" -> word
        "icons" -> icon
        else -> "$icon $word"
    }
}

/** Which Create choice made a result: its place in the Gallery, and where "Create more" goes. */
internal fun toolOf(e: ResultEntry): String = when (e.kind) {
    "image" -> if (e.title.startsWith("Edited")) "edit" else "image"
    "description" -> "describe"
    "ocr" -> "ocr"
    "speech" -> "speech"
    "loop" -> "loop"
    "song" -> "song"
    "transcript" -> "transcribe"
    "classification" -> "classify"
    "embedding" -> "embed"
    "rerank" -> "rerank"
    "mesh" -> "mesh"
    "video" -> "video"
    else -> "image"
}

/** The five kinds on top, the open kind's choices below them, and a link to what was made with it. */
@Composable
internal fun CreateHeader(current: String, setTool: (String) -> Unit, openGallery: (String) -> Unit) {
    val cat = categoryOf(current)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (c in CATEGORIES) {
            FilterChip(
                selected = c.id == cat.id,
                // A kind opens on its first choice (Image → Create); Back returns to where you were.
                onClick = { if (c.id != cat.id) setTool(c.tools.first()) },
                label = { Text(choiceLabel(c.icon, c.label)) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (cat.tools.size > 1) Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in cat.tools) {
                val (icon, word) = CHOICES[t] ?: ("" to t)
                FilterChip(selected = t == current, onClick = { if (t != current) setTool(t) }, label = { Text(choiceLabel(icon, word)) })
            }
        } else Spacer(Modifier.weight(1f))
        TextButton(onClick = { openGallery(current) }) { BtnLabel("Gallery", GalleryIcon) }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val yearFmt = DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH)

/**
 * Gallery: everything made, by kind (3D, Audio, Image, Text, Video) and choice, searchable, newest or oldest first,
 * grouped by day, month or year. Pictures and video stills show as a grid; the rest as cards. "Create more" goes back
 * to the matching place in Create.
 */
@Composable
fun CreationsScreen(st: StudioViewModel, start: String?, openTool: (String) -> Unit) {
    val results by st.results.collectAsState()
    // start: a tool id (from Create's link) or a kind's id; null = everything.
    var cat by rememberSaveable(start) { mutableStateOf(start?.let { s -> CATEGORIES.firstOrNull { it.id == s }?.id ?: categoryOf(s).id }) }
    var sub by rememberSaveable(start) { mutableStateOf(start?.takeIf { s -> CATEGORIES.none { it.id == s } }) }
    var query by rememberSaveable { mutableStateOf("") }
    var newest by rememberSaveable { mutableStateOf(true) }
    var group by rememberSaveable { mutableStateOf("day") }
    var full by remember { mutableStateOf<ResultEntry?>(null) }
    // Long-press marks results; then a tap marks or unmarks, and the bar on top shares or deletes them.
    var selected by remember { mutableStateOf(setOf<String>()) }
    var askDelete by remember { mutableStateOf(false) }
    fun toggle(e: ResultEntry) { selected = if (e.id in selected) selected - e.id else selected + e.id }
    val q = query.trim().lowercase()
    val shown = results
        .filter { e ->
            val t = toolOf(e)
            (cat == null || categoryOf(t).id == cat) && (sub == null || t == sub) &&
                (q.isEmpty() || e.title.lowercase().contains(q) || e.text.lowercase().contains(q) || e.model.lowercase().contains(q))
        }
        .sortedBy { if (newest) -it.created else it.created }
    val fmt = when (group) {
        "month" -> monthFmt
        "year" -> yearFmt
        "none" -> null
        else -> dayFmt
    }
    val sections = if (fmt == null) listOf("" to shown)
    else shown.groupBy { fmt.format(Instant.ofEpochMilli(it.created).atZone(ZoneId.systemDefault())) }.toList()

    val marked = results.filter { it.id in selected }
    if (askDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("Delete ${marked.size} result${if (marked.size == 1) "" else "s"}?") },
        text = { Text("They're deleted from this phone. This can't be undone; copies saved to the phone's own folders stay.") },
        confirmButton = {
            TextButton(onClick = { askDelete = false; st.deleteMany(marked); selected = emptySet() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
    )
    Column(Modifier.fillMaxSize()) {
    if (selected.isNotEmpty()) Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${marked.size} marked", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { selected = shown.map { it.id }.toSet() }) { Text("All") }
        TextButton(onClick = { st.shareMany(marked) }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
        TextButton(onClick = { askDelete = true }) { BtnLabel("Delete", androidx.compose.material.icons.Icons.Filled.Delete) }
        TextButton(onClick = { selected = emptySet() }) { Text("✕") }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = cat == null, onClick = { cat = null; sub = null }, label = { Text("All ${results.size}") })
                    for (c in CATEGORIES) {
                        val n = results.count { categoryOf(toolOf(it)).id == c.id }
                        FilterChip(selected = cat == c.id, onClick = { cat = c.id; sub = null }, label = { Text(choiceLabel(c.icon, c.label) + " $n") })
                    }
                }
                val c = CATEGORIES.firstOrNull { it.id == cat }
                if (c != null && c.tools.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = sub == null, onClick = { sub = null }, label = { Text("All") })
                    for (t in c.tools) {
                        val (icon, word) = CHOICES[t] ?: ("" to t)
                        val n = results.count { toolOf(it) == t }
                        FilterChip(selected = sub == t, onClick = { sub = t }, label = { Text(choiceLabel(icon, word) + " $n") })
                    }
                }
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search titles, texts and models") },
                    trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") } },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = newest, onClick = { newest = !newest }, label = { Text(if (newest) "Newest first ↓" else "Oldest first ↑") })
                    for ((k, l) in listOf("day" to "By day", "month" to "By month", "year" to "By year", "none" to "No groups")) {
                        FilterChip(selected = group == k, onClick = { group = k }, label = { Text(l) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${shown.size} shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // Back to Create, at the place that makes this kind of thing.
                    val target = sub ?: c?.tools?.first()
                    if (target != null) TextButton(onClick = { openTool(target) }) { Text(choiceLabel("✦", "Create more")) }
                }
                if (results.isEmpty()) Text("Nothing made yet: what you create appears here.", style = MaterialTheme.typography.bodyMedium)
                else if (shown.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        for ((title, list) in sections) {
            if (title.isNotEmpty()) item(key = "h-$title") {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            // Pictures as a grid of three; everything else as cards.
            val pics = list.filter { it.kind == "image" && it.mainFile != null }
            for (row in pics.chunked(3)) item(key = "g-" + row.joinToString("-") { it.id }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in row) Thumb(e, st, Modifier.weight(1f), e.id in selected, onLongPress = { toggle(e) }) {
                        if (selected.isNotEmpty()) toggle(e) else full = e
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            items(list.filter { it !in pics }, key = { it.id }) { e ->
                ResultCard(e, st, marked = e.id in selected, selecting = selected.isNotEmpty(), onMark = { toggle(e) })
            }
        }
        item {
            if (results.isNotEmpty() && selected.isEmpty()) Text(
                "Long-press to mark several, then share or delete them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(8.dp))
        }
    }
    }
    full?.let { e -> FullView(e, st) { full = null } }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Thumb(e: ResultEntry, st: StudioViewModel, modifier: Modifier, marked: Boolean, onLongPress: () -> Unit, onOpen: () -> Unit) {
    val bmp = rememberThumbnail(st.store.file(e.mainFile ?: return), 384)
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .then(if (marked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier),
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), e.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(e.title, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        if (marked) Text(
            "✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 6.dp),
        )
    }
}

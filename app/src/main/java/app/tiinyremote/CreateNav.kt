package app.tiinyremote

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
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

/** The choices' names and pictograms (the same as Models → Types). */
internal val CHOICES = mapOf(
    "image" to ("✦" to "Create"), "describe" to ("👁" to "Describe"), "edit" to ("✎" to "Edit"), "ocr" to (SIGN_OCR to "OCR"),
    "speech" to ("🗣" to "Voice"), "loop" to ("➰" to "Loop"), "song" to ("🎵" to "Song"), "transcribe" to ("👂" to "ASR"),
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
                label = { SignText(choiceLabel(c.icon, c.label)) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (cat.tools.size > 1) Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in cat.tools) {
                val (icon, word) = CHOICES[t] ?: ("" to t)
                FilterChip(selected = t == current, onClick = { if (t != current) setTool(t) }, label = { SignText(choiceLabel(icon, word)) })
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
    var viewing by remember { mutableStateOf<ResultEntry?>(null) }
    var viewed by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Long-press marks results; then a tap marks or unmarks, and the bar on top shares or deletes them.
    var selected by remember { mutableStateOf(setOf<String>()) }
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
    // Back leaves marking first.
    androidx.activity.compose.BackHandler(enabled = marked.isNotEmpty()) { selected = emptySet() }
    Column(Modifier.fillMaxSize()) {
    if (marked.isNotEmpty()) Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${marked.size} marked", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { selected = shown.map { it.id }.toSet() }) { Text("All") }
        TextButton(onClick = { st.shareMany(marked) }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
        // Deleted at once; the Undo bar brings them back.
        TextButton(onClick = { st.deleteMany(marked); selected = emptySet() }) { BtnLabel("Delete", androidx.compose.material.icons.Icons.Filled.Delete) }
        TextButton(onClick = { selected = emptySet() }) { Text("✕") }
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = cat == null, onClick = { cat = null; sub = null }, label = { Text("All ${results.size}") })
                    for (c in CATEGORIES) {
                        val n = results.count { categoryOf(toolOf(it)).id == c.id }
                        FilterChip(selected = cat == c.id, onClick = { cat = c.id; sub = null }, label = { SignText(choiceLabel(c.icon, c.label) + " $n") })
                    }
                }
                val c = CATEGORIES.firstOrNull { it.id == cat }
                if (c != null && c.tools.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = sub == null, onClick = { sub = null }, label = { Text("All") })
                    for (t in c.tools) {
                        val (icon, word) = CHOICES[t] ?: ("" to t)
                        val n = results.count { toolOf(it) == t }
                        FilterChip(selected = sub == t, onClick = { sub = t }, label = { SignText(choiceLabel(icon, word) + " $n") })
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
                    if (target != null) TextButton(onClick = { openTool(target) }) { SignText(choiceLabel("✦", "Create more")) }
                }
                if (results.isEmpty()) Text("Nothing made yet: what you create appears here.", style = MaterialTheme.typography.bodyMedium)
                else if (shown.isEmpty()) Text("Nothing matches.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        for ((title, list) in sections) {
            if (title.isNotEmpty()) item(key = "h-$title") {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            // Images, videos and 3D models as a grid of three; everything else as cards.
            val pics = list.filter { it.kind in TILE_KINDS && it.mainFile != null }
            for (row in pics.chunked(3)) item(key = "g-" + row.joinToString("-") { it.id }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in row) androidx.compose.runtime.key(e.id, viewed) {
                        Thumb(e, st, Modifier.weight(1f), e.id in selected, onLongPress = { toggle(e) }) {
                            if (marked.isNotEmpty()) toggle(e) else if (e.kind == "mesh") viewing = e else full = e
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            items(list.filter { it !in pics }, key = { it.id }) { e ->
                ResultCard(e, st, marked = e.id in selected, selecting = marked.isNotEmpty(), onMark = { toggle(e) }, onFull = { full = e })
            }
        }
        item {
            if (results.isNotEmpty() && marked.isEmpty()) Text(
                "Long-press to mark several, then share or delete them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(8.dp))
        }
    }
    }
    // The full view swipes through the results in the order they're shown here (tiles first in each group).
    full?.let { e ->
        val browse = sections.flatMap { (_, l) -> l.filter { it.kind in TILE_KINDS && it.mainFile != null } + l.filter { !(it.kind in TILE_KINDS && it.mainFile != null) } }
            .filter(::browsable)
        FullView(browse, browse.indexOfFirst { it.id == e.id }.coerceAtLeast(0), st) { full = null }
    }
    viewing?.let { e ->
        e.mainFile?.let { MeshViewer(e.title, st.store.file(it), st.store.renderOf(e)) { viewing = null; viewed++ } }
    }
}

/** Shown as tiles in the Gallery (the rest as cards). */
private val TILE_KINDS = setOf("image", "mesh", "video")

/** A frame from a video (one second in, or the first), for its tile. */
@Composable
private fun rememberVideoFrame(file: java.io.File): android.graphics.Bitmap? =
    androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, file.path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val r = android.media.MediaMetadataRetriever()
            try {
                r.setDataSource(file.absolutePath)
                (r.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.frameAtTime)
                    ?.let { b -> val k = 384f / maxOf(b.width, b.height); if (k < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true) else b }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }
    }.value

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Thumb(e: ResultEntry, st: StudioViewModel, modifier: Modifier, marked: Boolean, onLongPress: () -> Unit, onOpen: () -> Unit) {
    // A 3D model shows its picture (or the image it came from) with 🧊 in the corner; a video a frame from it with 🎬.
    val main = st.store.file(e.mainFile ?: return)
    val bmp = when (e.kind) {
        "mesh" -> st.store.pictureOf(e)?.let { rememberThumbnail(it, 384) }
        "video" -> rememberVideoFrame(main)
        else -> rememberThumbnail(main, 384)
    }
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .then(if (marked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier),
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), e.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(e.title, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        if (e.kind == "mesh" || e.kind == "video") Text(
            if (e.kind == "mesh") "🧊 3D" else "🎬 Video", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            color = androidx.compose.ui.graphics.Color.White,
        )
        if (marked) Text(
            "✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 6.dp),
        )
    }
}

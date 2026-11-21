package app.tiinyremote

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * How the screen is laid out: compact (square screens like the Titan 2) or roomy (tall phones), and whether labels
 * are words, pictograms or both ("words", "icons", "both").
 */
data class UiPrefs(val compact: Boolean = false, val enterSends: Boolean = true, val labels: String = "both")

/** Settings → Display → Labels. */
val LABEL_STYLES = listOf(
    "both" to "Pictograms and words",
    "words" to "Words only",
    "icons" to "Pictograms only (saves space)",
)

/** The pictogram for each label word used for models across the app (the legend in Settings lists them). */
val TAG_ICONS = linkedMapOf(
    "Chat" to "💬",
    "Thinks" to "💭",
    "Always thinks" to "🧠",
    "Makes things" to "🛠",
    "Sees images" to "👁",
    "MoE" to "🧩",
    "Mixture of experts" to "🧩",
    "Main chat model" to "◉",
    "Helper" to "⚡",
    "working" to "⏳",
)

/** One label as Settings wants it: the word, its pictogram, or both. */
@Composable
fun tag(word: String): String {
    val pic = TAG_ICONS[word] ?: return word
    return when (LocalUi.current.labels) {
        "words" -> word
        "icons" -> pic
        else -> "$pic $word"
    }
}

/** Several labels in a row: pictograms close together, words with dots between. */
@Composable
fun tags(words: List<String>): String =
    words.map { tag(it) }.joinToString(if (LocalUi.current.labels == "icons") "  " else "  ·  ")

val LocalUi = compositionLocalOf { UiPrefs() }

/** Screen layouts the user can pick in Settings → Display. */
val LAYOUTS = listOf(
    "auto" to "Auto (from the screen's shape)",
    "square" to "Square · compact (Unihertz Titan 2, 1:1)",
    "tall" to "Tall phone (19.5:9 and similar)",
)
val UI_SCALES = listOf("0.85" to "Small", "1.0" to "Normal", "1.15" to "Large")
val TEXT_SCALES = listOf("0.9" to "Small", "1.0" to "Normal", "1.15" to "Large", "1.3" to "Extra large")

/** Square-ish screens (width and height within 25 %) get the compact layout in Auto. */
fun isSquare(widthDp: Int, heightDp: Int): Boolean {
    val a = maxOf(widthDp, heightDp).toFloat()
    val b = minOf(widthDp, heightDp).toFloat()
    return a > 0 && a / b < 1.25f
}

/** Decodes a result picture off the main thread, so lists don't stutter; null until it's ready (or unreadable). */
@Composable
fun rememberThumbnail(file: java.io.File, maxSide: Int = 1024): android.graphics.Bitmap? =
    androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, file.path, maxSide) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Inputs.thumbnail(file, maxSide) }
    }.value

/** A picture you can pinch to zoom, drag, and double-tap to reset. */
@Composable
fun ZoomImage(file: java.io.File, modifier: Modifier = Modifier) {
    val bmp = rememberThumbnail(file, 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .pointerInput(file.path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput("tap" + file.path) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else scale = 2.5f
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let {
            Image(
                it.asImageBitmap(),
                null,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** A result on the whole screen: pictures to zoom, a video, or the text to read and copy. */
@Composable
fun FullView(e: ResultEntry, st: StudioViewModel, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = { st.share(e) }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                when (e.kind) {
                    "image" -> {
                        var page by remember(e.id) { mutableStateOf(0) }
                        val name = e.files.getOrNull(page)
                        if (name != null) ZoomImage(st.store.file(name), Modifier.weight(1f).fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (e.files.size > 1) {
                                TextButton(onClick = { page = (page - 1 + e.files.size) % e.files.size }) { Text("‹ Previous") }
                                Text("${page + 1} / ${e.files.size}")
                                TextButton(onClick = { page = (page + 1) % e.files.size }) { Text("Next ›") }
                            } else {
                                Text("Pinch to zoom · double-tap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (name != null) TextButton(onClick = { onClose(); st.editThis(name) }) { BtnLabel("Edit this", androidx.compose.material.icons.Icons.Filled.Edit) }
                        }
                    }
                    "video" -> e.mainFile?.let { n ->
                        val f = st.store.file(n)
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(f.absolutePath)
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnPreparedListener { it.isLooping = true; start() }
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    else -> SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            e.text,
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A button's content: a sign (icon or symbol) and/or the word, as Settings → Labels says. A button without a sign
 * always shows its word; with pictograms only, the word is still read out by TalkBack.
 */
@Composable
fun BtnLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, symbol: String? = null) {
    val style = LocalUi.current.labels
    val hasSign = icon != null || symbol != null
    val showSign = hasSign && style != "words"
    val showWord = !hasSign || style != "icons"
    val gap = if (showWord) Modifier.padding(end = 6.dp) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = text },
    ) {
        if (showSign) {
            if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, modifier = gap.size(18.dp))
            else Text(symbol!!, modifier = gap)
        }
        if (showWord) Text(text, maxLines = 1)
    }
}

/** A dialog's title with a small ✕ to close it, instead of a Close button taking a whole row. */
@Composable
fun ClosableTitle(onClose: () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) { content() }
        androidx.compose.material3.IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Close")
        }
    }
}

/** Delete, Clear and Forget: a slightly red button with the bin (or another) sign. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector = androidx.compose.material.icons.Icons.Filled.Delete,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) { BtnLabel(text, icon) }
}

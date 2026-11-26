package app.tiinyremote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.Close
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
    // The chat's switches use the same signs as the model labels.
    "Thinking" to "💭",
    "Can make things" to "🛠",
    // Drawn pictograms (a character only stands in for them; SignText draws them): web search a globe with a
    // magnifier, Translate and Automate the same as their pages.
    "Web search" to SIGN_WEB,
    "Automate" to SIGN_AUTOMATE,
    "Translates" to SIGN_TRANSLATE,
    "Translate" to SIGN_TRANSLATE,
    "Coding" to "💻",
    "Suggestions" to "💡",
    "Socratic" to "🤔",
    "Student" to "🎓",
)

/** Characters standing in for drawn pictograms in labels (private use, never typed); SignText draws them. */
const val SIGN_WEB = "\uE101"
const val SIGN_TRANSLATE = "\uE102"
const val SIGN_AUTOMATE = "\uE103"
const val SIGN_AGENT = "\uE104"
const val SIGN_PROCESS = "\uE105"
// The buttons' own drawn pictograms, so the help and hints show exactly what's on screen.
const val SIGN_HISTORY = "\uE106"
const val SIGN_SAVE = "\uE107"
const val SIGN_MIC = "\uE108"
const val SIGN_OCR = "\uE109"
const val SIGN_REFRESH = "\uE10A"
const val SIGN_SHARE = "\uE10B"
const val SIGN_DELETE = "\uE10C"
const val SIGN_EDIT = "\uE10D"
const val SIGN_INFO = "\uE10E"
const val SIGN_LOCK = "\uE10F"
const val SIGN_SETTINGS = "\uE110"
const val SIGN_MENU = "\uE111"
const val SIGN_ADD = "\uE112"
const val SIGN_CLOSE = "\uE113"
const val SIGN_PLAY = "\uE114"
const val SIGN_MORE = "\uE115"
const val SIGN_POWER = "\uE116"
const val SIGN_GALLERY = "\uE117"
const val SIGN_SEND = "\uE118"
const val SIGN_SEARCH = "\uE119"
const val SIGN_CODE = "\uE11A"
const val SIGN_BACK = "\uE11B"
// The sidebar's pages, as drawn there.
const val SIGN_MODELS = "\uE11C"
const val SIGN_CREATE = "\uE11D"
const val SIGN_CHAT = "\uE11E"
const val SIGN_ACTIVITY = "\uE11F"

/** Agent (Automate): a robot head. */
val AgentIcon by lazy {
    pathIcon(
        "Agent",
        "M12,2a2,2 0 0 1 2,2c0,0.74 -0.4,1.39 -1,1.73V7h1a7,7 0 0 1 7,7h1a1,1 0 0 1 1,1v3a1,1 0 0 1 -1,1h-1v1a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2v-1H2" +
            "a1,1 0 0 1 -1,-1v-3a1,1 0 0 1 1,-1h1a7,7 0 0 1 7,-7h1V5.73c-0.6,-0.34 -1,-0.99 -1,-1.73a2,2 0 0 1 2,-2M7.5,13A2.5,2.5 0 0 0 5,15.5" +
            "A2.5,2.5 0 0 0 7.5,18a2.5,2.5 0 0 0 2.5,-2.5A2.5,2.5 0 0 0 7.5,13m9,0a2.5,2.5 0 0 0 -2.5,2.5a2.5,2.5 0 0 0 2.5,2.5a2.5,2.5 0 0 0 2.5,-2.5" +
            "a2.5,2.5 0 0 0 -2.5,-2.5z",
    )
}

/** Process (Automate): a numbered list, fixed steps in order. */
val ProcessIcon by lazy {
    pathIcon(
        "Process",
        "M2,17h2v0.5H3v1h1v0.5H2v1h3v-4H2v1zM3,8h1V4H2v1h1V8zM2,11h1.8L2,13.1v0.9h3v-1H3.2L5,10.9V10H2v1zM7,5v2h14V5H7zM7,19h14v-2H7v2zM7,13h14v-2H7v2z",
    )
}

/** Web search: a globe with a magnifier (Material's "travel explore"), blue. */
val WebSearchIcon by lazy {
    pathIcon(
        "Web search",
        "M19.3,16.9c0.4,-0.7 0.7,-1.5 0.7,-2.4c0,-2.5 -2,-4.5 -4.5,-4.5S11,12 11,14.5s2,4.5 4.5,4.5c0.9,0 1.7,-0.3 2.4,-0.7l3.2,3.2l1.4,-1.4L19.3,16.9z" +
            "M15.5,17c-1.4,0 -2.5,-1.1 -2.5,-2.5s1.1,-2.5 2.5,-2.5s2.5,1.1 2.5,2.5S16.9,17 15.5,17z" +
            "M12,20v2C6.48,22 2,17.52 2,12C2,6.48 6.48,2 12,2c4.84,0 8.87,3.44 9.8,8h-2.07c-0.64,-2.46 -2.4,-4.47 -4.73,-5.41V5" +
            "c0,1.1 -0.9,2 -2,2h-2v2c0,0.55 -0.45,1 -1,1H8v2h2v3H9l-4.79,-4.79C4.08,10.79 4,11.38 4,12C4,16.41 7.59,20 12,20z",
    )
}

private val WEB_BLUE = androidx.compose.ui.graphics.Color(0xFF1E88E5)

/**
 * Text that may hold drawn pictograms (SIGN_WEB, SIGN_TRANSLATE, SIGN_AUTOMATE): each is drawn in its place at the
 * text's size, web search in blue, the others in the text's colour. Plain text otherwise.
 */
@Composable
fun SignText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
) {
    // The stand-in signs always; colour emoji too when Settings → Display → Pictograms is "Line icons".
    val annotated = iconized(text, SIGN_ICONS, lines = LocalLineIcons.current)
    if (annotated == null) {
        androidx.compose.material3.Text(text, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow, fontWeight = fontWeight)
        return
    }
    val tint = if (color != androidx.compose.ui.graphics.Color.Unspecified) color else androidx.compose.material3.LocalContentColor.current
    androidx.compose.material3.Text(
        annotated, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow, fontWeight = fontWeight,
        inlineContent = iconContent(annotated, SIGN_ICONS, tint, 1.25f) { k -> if (k == SIGN_WEB) WEB_BLUE else tint },
    )
}

/** Each stand-in character and the pictogram SignText draws for it. */
val SIGN_ICONS: Map<String, androidx.compose.ui.graphics.vector.ImageVector> by lazy {
    val i = androidx.compose.material.icons.Icons.Filled
    mapOf(
        SIGN_WEB to WebSearchIcon, SIGN_TRANSLATE to TranslateIcon, SIGN_AUTOMATE to AutomationIcon,
        SIGN_AGENT to AgentIcon, SIGN_PROCESS to ProcessIcon,
        SIGN_HISTORY to HistoryIcon, SIGN_SAVE to SaveIcon, SIGN_MIC to MicIcon, SIGN_OCR to OcrIcon,
        SIGN_GALLERY to GalleryIcon, SIGN_CODE to CodeIcon, SIGN_POWER to PowerIcon,
        SIGN_REFRESH to i.Refresh,
        SIGN_SHARE to i.Share, SIGN_DELETE to i.Delete, SIGN_EDIT to i.Edit, SIGN_INFO to i.Info, SIGN_LOCK to i.Lock,
        SIGN_SETTINGS to i.Settings, SIGN_MENU to i.Menu, SIGN_ADD to i.Add, SIGN_CLOSE to i.Close, SIGN_PLAY to i.PlayArrow,
        SIGN_MORE to i.MoreVert, SIGN_SEARCH to i.Search,
        SIGN_SEND to androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send,
        SIGN_BACK to androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
        SIGN_MODELS to i.Home, SIGN_CREATE to i.Create, SIGN_CHAT to SIGN_ICONS_CHAT, SIGN_ACTIVITY to SIGN_ICONS_LIST,
    )
}

private val SIGN_ICONS_CHAT get() = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send
private val SIGN_ICONS_LIST get() = androidx.compose.material.icons.Icons.AutoMirrored.Filled.List

/** OCR: a page with lines of text, read through a viewfinder ("OCR" inside it). */
val OcrIcon by lazy {
    pathIcon(
        "OCR",
        // The page: top, folded corner, right side, bottom, upper left side; lines of text.
        "M9,2h7.5v1.4H9zM16.5,2H18l3,3v1.5zM19.6,5.5H21V22h-1.4zM13.2,20.6H21V22h-7.8zM9,2h1.4v7.6H9z" +
            "M12,6.4h6v1.2h-6zM12,9.2h6v1.2h-6zM14.6,12h3.4v1.2h-3.4zM14.6,14.8h3.4v1.2h-3.4z" +
            // The viewfinder's four corners.
            "M2,11h3v1.3H3.3V14H2zM13,11v3h-1.3v-1.7H10V11zM2,22v-3h1.3v1.7H5V22zM13,22h-3v-1.3h1.7V19H13z" +
            // O, C, R.
            "M3.3,15.4h2.3v3.4H3.3zM4.1,16.2v1.8h0.7v-1.8z" +
            "M6.3,15.4h2.1v0.8H7.1v1.8h1.3v0.8H6.3z" +
            "M9,15.4h0.8v3.4H9zM9.8,15.4h1.4v1.9H9.8v-0.7h0.7v-0.5H9.8zM10.2,17.3h0.8l0.8,1.5H11z",
    )
}

/** The sign alone, for places that show only signs (the chat's compact switch summary). */
fun sign(word: String): String = TAG_ICONS[word] ?: word

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

/**
 * The app's own scale (Settings → Size of everything, Text size). Android draws dialogs, menus and pop-ups in windows
 * of their own at its default scale, so they get it handed on through these wrappers.
 */
val LocalAppDensity = compositionLocalOf<androidx.compose.ui.unit.Density?> { null }

/** Applies the app's scale inside a dialog, menu or pop-up. */
@Composable
fun AppScale(content: @Composable () -> Unit) {
    val d = LocalAppDensity.current
    if (d == null) content() else androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides d, content = content)
}

/** AlertDialog at the app's scale. */
@Composable
fun ScaledAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) = androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { AppScale(confirmButton) },
    modifier = modifier,
    dismissButton = dismissButton?.let { b -> { AppScale(b) } },
    icon = icon?.let { i -> { AppScale(i) } },
    title = title?.let { t -> { AppScale(t) } },
    text = text?.let { t -> { AppScale(t) } },
    properties = properties,
)

/**
 * DropdownMenu at the app's scale. The menu is placed in the phone's own scale while its items are drawn larger, so a
 * long list could run past the top and bottom of the screen: it's held to 70 % of the screen's height, and scrolls.
 */
@Composable
fun ScaledDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) = androidx.compose.material3.DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = modifier.heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.7f).dp),
) {
    val scope = this
    AppScale { scope.content() }
}

/** Screen layouts the user can pick in Settings → Display. */
val LAYOUTS = listOf(
    "auto" to "Auto (from the screen's shape)",
    "square" to "Square · compact (Unihertz Titan 2, 1:1)",
    "tall" to "Tall phone (19.5:9 and similar)",
)
val THEMES = listOf(
    "auto" to "Auto (as the phone is set)",
    "dark" to "Dark",
    "light" to "Light",
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
fun ZoomImage(file: java.io.File, modifier: Modifier = Modifier, onSwipe: ((Int) -> Unit)? = null) {
    val bmp = rememberThumbnail(file, 2048)
    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offset by remember(file.path) { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            // Not zoomed in: a swipe goes to the next or previous one (zoomed in, a drag moves the picture).
            .then(if (onSwipe != null) Modifier.swipeBetween(file.path) { if (scale == 1f) onSwipe(it) } else Modifier)
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

/**
 * Watches a one-finger swipe across [this] without taking it from what's inside (a video's controls, a scrolling
 * text): left gives +1 (next), right -1 (previous). Mostly-vertical moves and two-finger gestures don't count.
 */
fun Modifier.swipeBetween(key: Any, onSwipe: (Int) -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        val pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        var multi = false
        var last = down.position
        while (true) {
            val ev = awaitPointerEvent(pass)
            if (ev.changes.count { it.pressed } > 1) multi = true
            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
            last = c.position
            if (!c.pressed) break
        }
        val dx = last.x - down.position.x
        val dy = last.y - down.position.y
        if (!multi && kotlin.math.abs(dx) > 80.dp.toPx() && kotlin.math.abs(dx) > 2 * kotlin.math.abs(dy)) onSwipe(if (dx < 0) 1 else -1)
    }
}

/** Results that open in the full view (sound plays in its card, so it's left out when swiping through). */
fun browsable(e: ResultEntry) = e.kind !in setOf("speech", "loop", "song") && (e.mainFile != null || e.text.isNotBlank())

/** A result on the whole screen; swipe left or right through [entries] (the Gallery's order), starting at [start]. */
@Composable
fun FullView(entries: List<ResultEntry>, start: Int, st: StudioViewModel, onClose: () -> Unit) {
    var at by remember { mutableStateOf(start.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) }
    val e = entries.getOrNull(at) ?: run { onClose(); return }
    FullView(e, st, onClose, position = if (entries.size > 1) "${at + 1} / ${entries.size}" else null) { d ->
        at = (at + d).coerceIn(0, entries.size - 1)
    }
}

/**
 * A dialog over the whole screen, drawn edge to edge, with its content kept clear of the status bar and the phone's
 * bottom buttons. (A plain full-size dialog is placed below the status bar but keeps the screen's full height, so its
 * bottom ran off the screen.)
 */
@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) { content() }
        }
    }
}

/** A result on the whole screen: pictures to zoom, a video, a 3D model's picture, or the text to read and copy. */
@Composable
fun FullView(e: ResultEntry, st: StudioViewModel, onClose: () -> Unit, position: String? = null, onSwipe: ((Int) -> Unit)? = null) {
    FullScreenDialog(onClose) {
            Column(Modifier.fillMaxSize()) {
                var page by remember(e.id) { mutableStateOf(0) }
                // A swipe turns the pages of a result with several images first, then goes to the next result.
                val swipe: ((Int) -> Unit)? = if (onSwipe == null && e.files.size < 2 || e.kind != "image") onSwipe else { d ->
                    val p = page + d
                    if (p in e.files.indices) page = p else onSwipe?.invoke(d)
                }
                val name = if (e.kind == "image") e.files.getOrNull(page) else null
                var view3d by remember(e.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        if (position != null) Text("$position · swipe for more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    var askDelete by remember(e.id) { mutableStateOf(false) }
                    if (askDelete) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { askDelete = false },
                        title = { Text("Delete this result?") },
                        text = {
                            Text(
                                (if (e.files.size > 1) "All ${e.files.size} images of \"${e.title}\" are deleted from this phone." else "\"${e.title}\" is deleted from this phone.") +
                                    " This can't be undone; copies saved to the phone's own folders stay.",
                            )
                        },
                        confirmButton = { TextButton(onClick = { askDelete = false; onClose(); st.delete(e) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
                        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
                    )
                    TextButton(onClick = { askDelete = true }) { BtnLabel("Delete", androidx.compose.material.icons.Icons.Filled.Delete) }
                    if (name != null) TextButton(onClick = { onClose(); st.sendToChat(e, name) }) {
                        BtnLabel("Send to chat", androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send)
                    } else if (e.kind != "image" && st.canSendToChat(e)) TextButton(onClick = { onClose(); st.sendToChat(e) }) {
                        BtnLabel("Send to chat", androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send)
                    }
                    if (name != null) TextButton(onClick = { onClose(); st.editThis(name) }) {
                        BtnLabel("Edit", androidx.compose.material.icons.Icons.Filled.Edit)
                    }
                    if (e.kind == "mesh") TextButton(onClick = { view3d = true }) { BtnLabel("View in 3D", symbol = "🧊") }
                    TextButton(onClick = { st.share(e) }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
                    TextButton(onClick = onClose) { Text("Close") }
                }
                when (e.kind) {
                    "image" -> {
                        if (name != null) Box(Modifier.weight(1f).fillMaxWidth().then(if (e.files.size > 1) Modifier else Modifier.navigationBarsPadding())) {
                            ZoomImage(st.store.file(name), Modifier.fillMaxSize(), onSwipe = swipe)
                            ZoomHint(Modifier.align(Alignment.BottomEnd).padding(12.dp))
                        }
                        if (e.files.size > 1) Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { page = (page - 1 + e.files.size) % e.files.size }) { Text("‹ Previous") }
                            Text("${page + 1} / ${e.files.size}")
                            TextButton(onClick = { page = (page + 1) % e.files.size }) { Text("Next ›") }
                        }
                    }
                    "video" -> e.mainFile?.let { n ->
                        val f = st.store.file(n)
                        androidx.compose.runtime.key(e.id) { AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(f.absolutePath)
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnPreparedListener { it.isLooping = true; start() }
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()
                                .then(if (swipe != null) Modifier.swipeBetween(e.id, swipe) else Modifier),
                        ) }
                    }
                    "mesh" -> e.mainFile?.let { n ->
                        // The 3D model's picture: tap it (or 🧊 at the top) to turn the model in the viewer.
                        Box(
                            Modifier.weight(1f).fillMaxWidth().clickable(onClick = { view3d = true })
                                .then(if (swipe != null) Modifier.swipeBetween(e.id, swipe) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            st.store.pictureOf(e)?.let { p ->
                                rememberThumbnail(p, 1024)?.let { Image(it.asImageBitmap(), e.title, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                            }
                        }
                        if (view3d) MeshViewer(e.title, st.store.file(n), st.store.renderOf(e)) { view3d = false }
                    }
                    else -> SelectionContainer(Modifier.weight(1f).then(if (swipe != null) Modifier.swipeBetween(e.id, swipe) else Modifier)) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            if (e.kind == "description" || e.kind == "ocr") MarkdownText(e.text) else Text(e.text)
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
        // The word for TalkBack when only the sign shows (when the word shows, it's read already).
        modifier = if (showWord) Modifier else Modifier.semantics(mergeDescendants = true) { contentDescription = text },
    ) {
        if (showSign) {
            if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, modifier = gap.size(18.dp))
            else Text(symbol!!, modifier = gap)
        }
        if (showWord) Text(text, maxLines = 1)
    }
}

/**
 * The power sign (Material's "power settings new", drawn here): the ⏻ character is missing from some phones' fonts
 * and showed as an empty box.
 */
val PowerIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Power", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            androidx.compose.ui.graphics.vector.addPathNodes(
                "M13,3h-2v10h2L13,3zM17.83,5.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7" +
                    "c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9" +
                    "c0,-2.74 -1.23,-5.18 -3.17,-6.83z",
            ),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
        .build()
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

/** A Material-style icon from SVG path data (24×24), for pictograms the core icon set doesn't have. */
private fun pathIcon(name: String, path: String): androidx.compose.ui.graphics.vector.ImageVector =
    androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            androidx.compose.ui.graphics.vector.addPathNodes(path),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
        .build()

/** Translate: an "A" and a character, as in Material's translate icon. */
val TranslateIcon by lazy {
    pathIcon(
        "Translate",
        "M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53H17V4h-7V2H8v2H1v1.99h11.17" +
            "C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02" +
            "L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z",
    )
}

/** History: a clock with an arrow going back. */
val HistoryIcon by lazy {
    pathIcon(
        "History",
        "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7" +
            "c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
            "M12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z",
    )
}

/** Gallery: a stack of pictures. */
val GalleryIcon by lazy {
    pathIcon(
        "Gallery",
        "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2z" +
            "M11,12l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z",
    )
}

/** Automate: two arrows going round. */
val AutomationIcon by lazy {
    pathIcon(
        "Automate",
        "M12,6v3l4,-4 -4,-4v3c-4.42,0 -8,3.58 -8,8 0,1.57 0.46,3.03 1.24,4.26L6.7,14.8c-0.45,-0.83 -0.7,-1.79 -0.7,-2.8" +
            "c0,-3.31 2.69,-6 6,-6zM18.76,7.74L17.3,9.2c0.44,0.84 0.7,1.79 0.7,2.8 0,3.31 -2.69,6 -6,6v-3l-4,4 4,4v-3" +
            "c4.42,0 8,-3.58 8,-8 0,-1.57 -0.46,-3.03 -1.24,-4.26z",
    )
}

/** Dictate: a microphone. */
val MicIcon by lazy {
    pathIcon(
        "Mic",
        "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
            "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
    )
}

/** Save: a floppy disk (green: chats are saved; red: incognito). */
val SaveIcon by lazy {
    pathIcon(
        "Save",
        "M17,3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9H5V5h10v4z",
    )
}

/** Code: angle brackets, as in Material's code icon. */
val CodeIcon by lazy {
    pathIcon(
        "Code",
        "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z",
    )
}

/**
 * The set-up guides ("Set up NAS", "Set up SearXNG"…): the commands to run over SSH (Termius, Termux…) on the NAS, step by step, each in
 * its own box with Copy. [steps]: what the step does, then its commands.
 */
@Composable
fun TerminalGuide(
    title: String, steps: List<Pair<String, String>>, note: String,
    link: Pair<String, String>? = null, onClose: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f).widthIn(max = 680.dp),
        title = { ClosableTitle(onClose) { Text(title) } },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // A link to the same guide elsewhere (GitHub), first.
                link?.let { (label, url) ->
                    androidx.compose.material3.TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text(label) }
                }
                for ((what, cmd) in steps) {
                    // "## …" starts a part of the guide; a step without commands is only text.
                    if (what.startsWith("## ")) {
                        Text(what.removePrefix("## "), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                        continue
                    }
                    Text(what, style = MaterialTheme.typography.bodyMedium)
                    if (cmd.isBlank()) continue
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .padding(8.dp),
                    ) {
                        Text(
                            cmd,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(cmd)) },
                            modifier = Modifier.align(Alignment.End),
                        ) { BtnLabel("Copy", symbol = "⧉") }
                    }
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
    )
}

/** A faint magnifier in the image's corner: pinch or double-tap to zoom. */
@Composable
private fun ZoomHint(modifier: Modifier) {
    val c = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
    androidx.compose.foundation.Canvas(
        modifier.size(36.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
            .padding(6.dp).semantics { contentDescription = "Pinch or double-tap to zoom" },
    ) {
        val w = size.minDimension
        val r = w * 0.32f
        val cx = w * 0.4f
        val stroke = w * 0.09f
        drawCircle(c, r, androidx.compose.ui.geometry.Offset(cx, cx), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawLine(c, androidx.compose.ui.geometry.Offset(cx + r * 0.7f, cx + r * 0.7f), androidx.compose.ui.geometry.Offset(w * 0.95f, w * 0.95f), stroke * 1.3f, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c, androidx.compose.ui.geometry.Offset(cx - r * 0.5f, cx), androidx.compose.ui.geometry.Offset(cx + r * 0.5f, cx), stroke)
        drawLine(c, androidx.compose.ui.geometry.Offset(cx, cx - r * 0.5f), androidx.compose.ui.geometry.Offset(cx, cx + r * 0.5f), stroke)
    }
}

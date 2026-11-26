package app.tiinyremote

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/** One thing to do, as the Start page shows it. [key] stays the same across versions: the saved order uses it. */
private class Task(val key: String, val icon: String, val title: String, val sub: String, val go: () -> Unit)

/** The order you dragged the cards into (keys), kept on this phone; empty = the original order. */
private object StartOrder {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("start", 0)
    fun load(ctx: Context): List<String> = prefs(ctx).getString("order", "")!!.split(',').filter { it.isNotBlank() }
    fun save(ctx: Context, keys: List<String>?) = prefs(ctx).edit().apply { if (keys == null) remove("order") else putString("order", keys.joinToString(",")) }.apply()

    /** The tasks in the saved order; tasks the saved order doesn't know (added in a newer version) go at the end. */
    fun <T> apply(saved: List<String>, items: List<T>, keyOf: (T) -> String): List<T> {
        val byKey = items.associateBy(keyOf)
        return saved.mapNotNull { byKey[it] } + items.filter { keyOf(it) !in saved }
    }
}

/**
 * The Start page: what you can do, as tasks rather than models ("Translate a photo", "Make a song"). Each card opens
 * the right page and tool; the models are picked there as usual. (Sidebar ⚙ → Start with: Start, to open on it.)
 * Cards in a row are the same height, icons at the top; a long press picks a card up and drag puts it anywhere.
 */
@Composable
fun StartScreen(st: StudioViewModel, goTab: (Int) -> Unit, openTool: (String) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    fun tool(id: String) = openTool(id) // one step: Back returns to Start
    val tasks = listOf(
        Task("chat", "💬", "Ask anything", "Chat with a model on your Tiiny") { goTab(2) },
        Task("dictate", SIGN_MIC, "Dictate a question", "Speak; the answer comes as text") { goTab(2); Nav.dictate.value = true },
        Task("document", "📄", "Ask about a document", "Attach a PDF or text in Chat") { goTab(2) },
        Task("translate", SIGN_TRANSLATE, "Translate", "A text, a photo or speech") { goTab(7) },
        Task("image", "🖼", "Make an image", "From a description") { tool("image") },
        Task("edit", "✎", "Change an image", "Edit one of yours") { tool("edit") },
        Task("describe", "👁", "Describe an image", "What's in a photo") { tool("describe") },
        Task("ocr", SIGN_OCR, "Read text from a photo", "OCR: a receipt, a sign, a page") { tool("ocr") },
        Task("song", "🎵", "Make a song", "Style and lyrics, with vocals") { tool("song") },
        Task("speech", "🗣", "Read a text aloud", "In a voice, or your own") { tool("speech") },
        Task("video", "🎬", "Make a short video", "From a scene or a photo") { tool("video") },
        Task("mesh", "🧊", "Make a 3D model", "From a photo") { tool("mesh") },
        Task("code", SIGN_CODE, "Build a web page", "The model writes it; preview it here") { goTab(8) },
        Task("automate", SIGN_AUTOMATE, "Automate something", "Daily email summaries, news, reminders") { goTab(5) },
        Task("gallery", SIGN_GALLERY, "See what you made", "Images, sound, video, 3D, texts") { goTab(6) },
        Task("choose", "🧭", "Help me choose a model", "Say what you want to do") { st.askInChat(HELP_CHOOSE_DRAFT) },
    )
    var order by remember { mutableStateOf(StartOrder.apply(StartOrder.load(ctx), tasks.map { it.key }) { it }) }
    val byKey = tasks.associateBy { it.key }
    val shown = order.mapNotNull { byKey[it] }

    // Dragging: which card, where the finger is (in root coordinates), where on the card it was grabbed.
    var dragging by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val scroll = rememberScrollState()

    /** Moves the dragged card to the place under the finger, if that's another card's place. */
    fun follow() {
        val d = dragging ?: return
        val over = bounds.entries.firstOrNull { (k, r) -> k != d && r.contains(finger) }?.key ?: return
        val to = order.indexOf(over)
        if (to < 0) return
        order = order.toMutableList().apply { remove(d); add(to, d) }
    }

    // Near the top or bottom edge while dragging: scroll, so a card can go anywhere on a long page.
    LaunchedEffect(dragging) {
        while (dragging != null) {
            val edge = 80f
            val step = when {
                finger.y < viewport.top + edge -> -18f
                finger.y > viewport.bottom - edge -> 18f
                else -> 0f
            }
            if (step != 0f) { scroll.scrollBy(step); follow() }
            delay(16)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }) {
        val gap = 10.dp
        val cols = ((maxWidth - 32.dp + gap) / (160.dp + gap)).toInt().coerceAtLeast(1)
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
            Text("What would you like to do?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 14.dp))
            TileGrid(cols, gap) {
                for (t in shown) key(t.key) {
                    val lifted = dragging == t.key
                    val here = bounds[t.key]
                    Card(
                        Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            .onGloballyPositioned { bounds[t.key] = it.boundsInRoot() }
                            .graphicsLayer {
                                if (lifted && here != null) {
                                    translationX = finger.x - grab.x - here.left
                                    translationY = finger.y - grab.y - here.top
                                    scaleX = 1.04f; scaleY = 1.04f
                                }
                            }
                            .then(if (lifted) Modifier.shadow(8.dp, CardDefaults.shape) else Modifier)
                            .pointerInput(t.key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { at ->
                                        val r = bounds[t.key] ?: return@detectDragGesturesAfterLongPress
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        grab = at
                                        finger = Offset(r.left + at.x, r.top + at.y)
                                        dragging = t.key
                                    },
                                    onDrag = { change, delta -> change.consume(); finger += delta; follow() },
                                    onDragEnd = { dragging = null; StartOrder.save(ctx, order) },
                                    onDragCancel = { dragging = null; StartOrder.save(ctx, order) },
                                )
                            }
                            .clickable(enabled = dragging == null, onClick = t.go),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        // Icon and text start at the top, so icons line up across a row whatever the text's length.
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            SignText(t.icon, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall)
                                Text(t.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            val original = tasks.map { it.key }
            if (order != original) TextButton(
                onClick = { order = original; StartOrder.save(ctx, null) },
                modifier = Modifier.padding(top = 6.dp),
            ) { Text("Original order") }
            Text(
                "Long-press a card to move it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

/**
 * Cards in [cols] equal columns; every card in a row as tall as the tallest one in it (measured by its natural
 * height first), so rows line up. One parent for all cards: a card dragged to another row keeps its gesture.
 */
@Composable
private fun TileGrid(cols: Int, gap: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content) { items, c ->
        val g = gap.roundToPx()
        val w = ((c.maxWidth - g * (cols - 1)) / cols).coerceAtLeast(0)
        val rows = items.chunked(cols)
        val heights = rows.map { row -> row.maxOf { it.maxIntrinsicHeight(w) } }
        val placed = rows.mapIndexed { i, row -> row.map { it.measure(Constraints.fixed(w, heights[i])) } }
        val total = heights.sum() + g * (rows.size - 1).coerceAtLeast(0)
        layout(c.maxWidth, total) {
            var y = 0
            placed.forEachIndexed { i, row ->
                row.forEachIndexed { j, p -> p.place(j * (w + g), y) }
                y += heights[i] + g
            }
        }
    }
}

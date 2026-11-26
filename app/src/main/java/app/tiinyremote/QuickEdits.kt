package app.tiinyremote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas as DrawCanvas
import kotlin.random.Random

/**
 * One-tap edits with the FLUX.2-klein-4B specialists from the Store, each trained for one job and its own prompt
 * (fal's LoRAs): mark what to remove or zoom into in red, extend over green borders, or cut out the background.
 */
private class QuickEdit(val key: String, val label: String, val idPart: String, val prompt: String, val needs: String)

private val QUICK_EDITS = listOf( // alphabetical, as shown
    QuickEdit("extend", "Extend the image", "Outpaint", "Fill the green spaces according to the image", "pad"),
    QuickEdit("remove", "Remove an object", "Object-Remove", "Remove the highlighted object from the scene", "mark"),
    QuickEdit("background", "Remove the background", "Background-Remove", "Remove the background from the image", ""),
    QuickEdit("zoom", "Zoom into a part", "Zoom", "Zoom into the red highlighted area", "mark"),
)

/** The marked or padded picture as a PNG for the Tiiny, at most 1024 px on its long side (multiples of 16). */
private fun forModel(b: Bitmap): Pair<PickedImage, String> {
    val k = minOf(1.0, 1024.0 / maxOf(b.width, b.height))
    val w = maxOf(16, (b.width * k / 16).toInt() * 16)
    val h = maxOf(16, (b.height * k / 16).toInt() * 16)
    val s = Bitmap.createScaledBitmap(b, w, h, true)
    val out = java.io.ByteArrayOutputStream()
    s.compress(Bitmap.CompressFormat.PNG, 100, out)
    return PickedImage(out.toByteArray(), "image/png", s) to "${w}x$h"
}

/** The picture in the middle of a pure green canvas, [extra] wider and/or taller: the green is what gets filled in. */
private fun padded(src: Bitmap, sides: String, extra: Float): Bitmap {
    val addW = if (sides != "tall") (src.width * extra).toInt() else 0
    val addH = if (sides != "wide") (src.height * extra).toInt() else 0
    val out = Bitmap.createBitmap(src.width + addW, src.height + addH, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(android.graphics.Color.rgb(0, 255, 0))
    c.drawBitmap(src, addW / 2f, addH / 2f, null)
    return out
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickEdits(st: StudioViewModel, models: List<ModelInfo>, source: PickedImage?) {
    var marking by remember { mutableStateOf<QuickEdit?>(null) }
    var extending by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf<QuickEdit?>(null) }
    fun modelFor(q: QuickEdit) = models.firstOrNull { it.id.contains("klein", true) && it.id.contains(q.idPart, true) }

    fun start(q: QuickEdit, img: Bitmap) {
        val m = modelFor(q) ?: return
        val (pic, size) = forModel(img)
        val seed = Random.nextLong(0, 1L shl 31)
        st.run("edit") { c, store, p, _ ->
            // The specialist is loaded for this one edit; what had to make room comes back afterwards.
            val unloaded = Tools.makeRoom(c, m.id, p)
            try {
                Tools.image(c, store, m.id, q.prompt, "", size, null, seed, listOf(pic.dataUri), p)
            } finally {
                Tools.putBack(c, m.id, unloaded, p)
            }
        }
    }

    LabelRow("Quick edits", "one tap, a model made for each")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (q in QUICK_EDITS) AssistChip(
            enabled = source != null || modelFor(q) == null,
            onClick = {
                when {
                    modelFor(q) == null -> missing = q
                    source == null -> Unit
                    q.needs == "mark" -> marking = q
                    q.needs == "pad" -> extending = true
                    else -> start(q, source.preview)
                }
            },
            label = { Text(q.label + if (modelFor(q) == null) " ⤓" else "") },
        )
    }
    // Not on the Tiiny yet: download it from here (asked once, with the size); once it's there, the same tap edits.
    missing?.let { q ->
        val id = Recs.first("q_${q.key}")?.id ?: "fal/FLUX.2-klein-4B-${q.idPart}"
        DownloadAsk(listOf(id), "${q.label} needs this model, trained for exactly that. Once it's on the Tiiny, the same tap does it.") { missing = null }
    }
    val src = source?.preview
    marking?.let { q ->
        if (src != null) MarkDialog(src, if (q.key == "zoom") "Paint over the part to zoom into" else "Paint over what to remove", onClose = { marking = null }) { marked ->
            marking = null
            start(q, marked)
        }
    }
    if (extending && src != null) ExtendDialog(onClose = { extending = false }) { sides, extra ->
        extending = false
        start(QUICK_EDITS.first { it.key == "extend" }, padded(src, sides, extra))
    }
}

/** Paint in red over the picture with a finger; the strokes are drawn into the picture the model gets. */
@Composable
private fun MarkDialog(src: Bitmap, title: String, onClose: () -> Unit, onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() } // in the picture's own pixels
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val brush = maxOf(src.width, src.height) * 0.045f
    FullScreenDialog(onClose) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text("Cover it generously; the red goes to the model with the image.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val scale = minOf(constraints.maxWidth.toFloat() / src.width, constraints.maxHeight.toFloat() / src.height)
                val wDp = with(androidx.compose.ui.platform.LocalDensity.current) { (src.width * scale).toDp() }
                val hDp = with(androidx.compose.ui.platform.LocalDensity.current) { (src.height * scale).toDp() }
                Box(Modifier.size(wDp, hDp)) {
                    Image(src.asImageBitmap(), null, Modifier.fillMaxSize())
                    DrawCanvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it / scale) },
                                onDrag = { ch, _ -> ch.consume(); current = current + (ch.position / scale) },
                                onDragEnd = { strokes.add(current); current = emptyList() },
                            )
                        },
                    ) {
                        for (s in strokes + listOf(current)) for (i in 1 until s.size) drawLine(
                            Color(0xAAFF0000), s[i - 1] * scale, s[i] * scale, brush * scale, StrokeCap.Round,
                        )
                        for (s in strokes + listOf(current)) if (s.size == 1) drawCircle(Color(0xAAFF0000), brush * scale / 2, s[0] * scale)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Cancel") }
                TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                Button(enabled = strokes.isNotEmpty(), onClick = {
                    val out = src.copy(Bitmap.Config.ARGB_8888, true)
                    val c = Canvas(out)
                    val paint = Paint().apply {
                        color = android.graphics.Color.RED; strokeWidth = brush; strokeCap = Paint.Cap.ROUND
                        style = Paint.Style.STROKE; isAntiAlias = true
                    }
                    for (s in strokes) {
                        if (s.size == 1) c.drawCircle(s[0].x, s[0].y, brush / 2, Paint(paint).apply { style = Paint.Style.FILL })
                        for (i in 1 until s.size) c.drawLine(s[i - 1].x, s[i - 1].y, s[i].x, s[i].y, paint)
                    }
                    onDone(out)
                }) { Text("Apply") }
            }
        }
    }
}

/** Which way to extend, and by how much. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtendDialog(onClose: () -> Unit, onDone: (String, Float) -> Unit) {
    var sides by remember { mutableStateOf("all") }
    var extra by remember { mutableStateOf(0.25f) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Extend the image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((k, l) in listOf("all" to "All around", "tall" to "Taller", "wide" to "Wider")) {
                        FilterChip(selected = sides == k, onClick = { sides = k }, label = { Text(l) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (e in listOf(0.25f, 0.5f, 1f)) FilterChip(selected = extra == e, onClick = { extra = e }, label = { Text("+${(e * 100).toInt()}%") })
                }
                Text("The new parts are painted to match the image.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onDone(sides, extra) }) { Text("Extend") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.tiinyremote

import androidx.compose.material.icons.filled.Close
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import kotlin.random.Random

/*
 * The image workspace, laid out like Tiiny Studio's (static/image.js): Create, Edit and Text.
 */

private val CREATE_CHIPS = listOf("35mm photo", "cinematic lighting", "golden hour", "isometric 3D", "studio product shot", "watercolor")
private val EDIT_CHIPS = listOf("Change the background to ", "Make it night time", "Turn it into a watercolor painting", "Remove the ", "Add ", "Change the color of the ")

// ---------------- size math (Tiiny Studio's) ----------------

private class Shape(val id: String, val w: Int, val h: Int, val sizes: List<Pair<Int, Int>> = emptyList())

private val ASPECTS = listOf(Shape("1:1", 1, 1), Shape("4:3", 4, 3), Shape("3:4", 3, 4), Shape("16:9", 16, 9), Shape("9:16", 9, 16))

/** Sizes the model's description lists (e.g. "verified: 432x768, 768x432 …"); off-list sizes can come out with seams. */
private fun modelSizes(m: ModelInfo?): List<Pair<Int, Int>>? {
    val d = m?.desc ?: return null
    if (!Regex("resolution|sizes?\\b", RegexOption.IGNORE_CASE).containsMatchIn(d)) return null
    val out = LinkedHashSet<Pair<Int, Int>>()
    for (x in Regex("\\b(\\d{3,4})\\s*[x×]\\s*(\\d{3,4})\\b").findAll(d)) {
        val w = x.groupValues[1].toInt()
        val h = x.groupValues[2].toInt()
        if (w >= 256 && h >= 256) out.add(w to h)
    }
    return if (out.size >= 2) out.toList() else null
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** The usual shapes; a model's sizes are shown under the nearest of these (600×448 is 4:3, 1360×768 is 16:9). */
private val USUAL = listOf("1:1", "4:3", "3:4", "3:2", "2:3", "16:10", "10:16", "16:9", "9:16", "21:9", "9:21")

private fun aspectKey(w: Int, h: Int): String {
    val r = Math.log(w.toDouble() / h)
    val best = USUAL.minBy { k -> val (a, b) = k.split(":").map { it.toDouble() }; Math.abs(r - Math.log(a / b)) }
    val (a, b) = best.split(":").map { it.toDouble() }
    if (Math.abs(r - Math.log(a / b)) < 0.03) return best // within 3 %
    val g = gcd(w, h)
    return "${w / g}:${h / g}"
}

private fun shapesFor(m: ModelInfo?): Pair<Boolean, List<Shape>> {
    val list = modelSizes(m) ?: return false to ASPECTS
    val groups = LinkedHashMap<String, MutableList<Pair<Int, Int>>>()
    // Only the usual shapes: a size far from all of them (an odd one like 75:56 would be) isn't offered.
    for ((w, h) in list) aspectKey(w, h).takeIf { it in USUAL }?.let { groups.getOrPut(it) { ArrayList() }.add(w to h) }
    if (groups.isEmpty()) return false to ASPECTS
    val shapes = groups.map { (id, sizes) ->
        val (a, b) = id.split(":").map { it.toInt() }
        Shape(id, a, b, sizes.sortedBy { it.first * it.second })
    }
    val skew = { s: Shape -> Math.round(Math.abs(Math.log(s.w.toDouble() / s.h)) * 1e6) }
    return true to shapes.sortedWith(compareBy<Shape>({ skew(it) }, { -(it.w.toDouble() / it.h) }))
}

/** The size to ask for: the verified size nearest the resolution, or the shape at about res × res pixels in multiples of 64. */
private fun sizeFor(verified: Boolean, shapes: List<Shape>, aspect: String, res: Int, src: PickedImage?): Pair<Int, Int> {
    if (verified) {
        var g = shapes.firstOrNull { it.id == aspect }
        if (aspect == "src" && src != null) {
            val r = src.preview.width.toDouble() / src.preview.height
            g = shapes.minByOrNull { Math.abs(Math.log(it.w.toDouble() / it.h / r)) }
        }
        val s = g ?: shapes.first()
        return s.sizes.minByOrNull { Math.abs(maxOf(it.first, it.second) - res) }!!
    }
    var rw = 1.0
    var rh = 1.0
    if (aspect == "src") {
        if (src != null) {
            rw = src.preview.width.toDouble()
            rh = src.preview.height.toDouble()
        }
    } else ASPECTS.firstOrNull { it.id == aspect }?.let { rw = it.w.toDouble(); rh = it.h.toDouble() }
    val px = res.toDouble() * res
    val w = Math.sqrt(px * rw / rh)
    val h = px / w
    fun r64(v: Double) = maxOf(256, Math.round(v / 64).toInt() * 64)
    return r64(w) to r64(h)
}

private fun resOptions(verified: Boolean, shapes: List<Shape>, aspect: String, res: Int, src: PickedImage?): List<Int> {
    if (!verified) return listOf(512, 768, 1024)
    var g = shapes.firstOrNull { it.id == aspect }
    if (aspect == "src" && src != null) {
        val (w, h) = sizeFor(true, shapes, aspect, res, src)
        g = shapes.firstOrNull { it.id == aspectKey(w, h) }
    }
    return (g ?: shapes.first()).sizes.map { maxOf(it.first, it.second) }
}

/** How many reference pictures an editing model takes ("up to N reference images" in its description). */
private fun maxRefs(m: ModelInfo?): Int =
    if (m?.canEditImages != true) 0 else MAX_REFS.find(m.desc)?.groupValues?.get(1)?.toIntOrNull() ?: 1

private fun makesTransparent(m: ModelInfo?) = m != null && TRANSPARENT.containsMatchIn(m.desc)

/** Tiiny Studio's first guess before it has timed a model: ~75 s per 512² image at 30 steps (10 s for turbo models). */
private fun imageEstimate(model: String, w: Int, h: Int, steps: Int?, n: Int, edit: Boolean): Double {
    val units = (w * h) / (512.0 * 512) * (steps ?: 30) / 30.0 * n * (if (edit) 1.15 else 1.0)
    return (if (model.contains("turbo", true)) 10.0 else 75.0) * units
}

// ---------------- small pieces ----------------

@Composable
private fun ShapeButton(shape: Shape?, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    OutlinedCard(onClick = onClick, border = BorderStroke(if (selected) 2.dp else 1.dp, color)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (shape != null) {
                    val k = 24f / maxOf(shape.w, shape.h)
                    Box(Modifier.size((shape.w * k).dp, (shape.h * k).dp).border(2.dp, color, RoundedCornerShape(4.dp)))
                } else {
                    Text("🖼")
                }
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Pictures made earlier, to pick as a source. */
@Composable
internal fun RecentImages(st: StudioViewModel, onPick: (PickedImage) -> Unit) {
    val results by st.results.collectAsState()
    val scope = rememberCoroutineScope()
    val recent = results.filter { it.kind == "image" && it.mainFile != null }.take(12)
    if (recent.isEmpty()) return
    LabelRow("From your images")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (e in recent) {
            val f = st.store.file(e.mainFile!!)
            val thumb = rememberThumbnail(f, 256)
            if (thumb != null) {
                Card(onClick = { scope.launch { withContext(Dispatchers.IO) { Inputs.fromFile(f) }?.let(onPick) } }) {
                    Image(thumb.asImageBitmap(), e.title, Modifier.size(76.dp), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

// ---------------- Create and Edit ----------------

@Composable
internal fun ImageTool(model: ModelInfo?, notReady: String?, st: StudioViewModel, models: List<ModelInfo>, edit: Boolean) {
    val helper by st.helper.collectAsState()
    val editSource by st.editSource.collectAsState()
    val mid = model?.id ?: ""
    val refs = maxOf(1, maxRefs(model))
    val transparentOk = !edit && makesTransparent(model)
    val (verified, shapes) = remember(mid) { shapesFor(model) }

    var prompt by rememberSaveable(edit, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var before by remember(edit) { mutableStateOf<String?>(null) } // the prompt before "Improve", for Undo
    var images by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var aspect by rememberSaveable(edit) { mutableStateOf(st.setting(if (edit) "img.aspect.edit" else "img.aspect", if (edit) "src" else "1:1")) }
    var res by rememberSaveable { mutableStateOf(st.setting("img.res", "768").toInt()) }
    var n by rememberSaveable { mutableStateOf(1) }
    var transparent by rememberSaveable { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    // Remembered per model, like Tiiny Studio: negative prompt, steps and guidance.
    var negative by rememberSaveable(mid) { mutableStateOf(st.setting("img.$mid.neg", "")) }
    var stepsAuto by rememberSaveable(mid) { mutableStateOf(st.setting("img.$mid.stepsAuto", "1") == "1") }
    var steps by rememberSaveable(mid) { mutableStateOf(st.setting("img.$mid.steps", "30").toFloat()) }
    var cfgAuto by rememberSaveable(mid) { mutableStateOf(st.setting("img.$mid.cfgAuto", "1") == "1") }
    var cfg by rememberSaveable(mid) { mutableStateOf(st.setting("img.$mid.cfg", "4").toFloat()) }
    var seed by rememberSaveable { mutableStateOf("") }
    var seedLocked by rememberSaveable { mutableStateOf(false) }
    var livePreview by remember { mutableStateOf(st.setting("img.preview", "0") == "1") }
    // Characters: a fixed description and seed each, used word for word in every picture they're in (as Story Lantern
    // and TiinyComics do), so they look the same from picture to picture.
    var characters by remember { mutableStateOf(loadCharacters(st.setting("img.characters", "[]"))) }
    var chosen by rememberSaveable { mutableStateOf(setOf<String>()) }
    var editingChar by remember { mutableStateOf<PicCharacter?>(null) }
    fun saveChars(list: List<PicCharacter>) { characters = list; st.setSetting("img.characters", charactersJson(list)) }

    // "Edit this" on a result brings the picture here.
    LaunchedEffect(editSource, edit) {
        val f = editSource
        if (edit && f != null) {
            withContext(Dispatchers.IO) { Inputs.fromFile(st.store.file(f)) }?.let { images = listOf(it) }
            st.editSourceTaken()
        }
    }

    if (edit) {
        LabelRow("Source image", if (refs > 1) "source + up to ${refs - 1} references" else "pick a photo or one of yours")
        ImagePickerRow(images, refs, if (refs > 1) "Choose image(s)" else "Choose an image") { images = it }
        RecentImages(st) { img -> images = (if (refs > 1) images + img else listOf(img)).take(refs) }
        if (refs > 1 && images.size > 1) Hint("Call them “image 1”, “image 2” … in the instruction.")
    }

    LabelRow(if (edit) "Describe the change" else "Describe the image", "${prompt.text.length}")
    OutlinedTextField(
        value = prompt,
        onValueChange = { prompt = it; before = null },
        placeholder = {
            Text(if (edit) "e.g. Make it winter: snow on the roofs and trees, keep everything else exactly the same" else "e.g. A red tram crossing an old stone bridge in the rain, evening lights reflecting on the wet street")
        },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (models.any { it.isChat }) {
            OutlinedButton(
                enabled = prompt.text.isNotBlank() && helper == null,
                onClick = {
                    val old = prompt.text
                    st.improvePrompt(old, edit, models) { prompt = TextFieldValue(it, TextRange(it.length)); before = old }
                },
            ) { Text(if (helper?.startsWith("Improving") == true) helper!! else "✦ Improve prompt") }
        }
        before?.let { old ->
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { prompt = TextFieldValue(old, TextRange(old.length)); before = null }) { Text("Undo") }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (edit) {
            for (c in EDIT_CHIPS) AssistChip(
                onClick = { prompt = TextFieldValue(c, TextRange(c.length)) },
                label = { Text(c.trim().replace(Regex(" (the|to)$"), " …").let { if (c.endsWith(" ") && !it.endsWith("…")) "$it …" else it }) },
            )
        } else {
            for (c in CREATE_CHIPS) AssistChip(
                onClick = {
                    val t = prompt.text.trim()
                    val nt = if (t.isEmpty()) c else t.trimEnd(',', '.') + ", " + c
                    prompt = TextFieldValue(nt, TextRange(nt.length))
                },
                label = { Text("+ $c") },
            )
            if (transparentOk) FilterChip(selected = transparent, onClick = { transparent = !transparent }, label = { Text("◩ Transparent background") })
        }
    }

    // Characters
    LabelRow("Characters", "look the same in every image")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (ch in characters) FilterChip(
            selected = ch.name in chosen,
            onClick = { chosen = if (ch.name in chosen) chosen - ch.name else chosen + ch.name },
            label = { Text(ch.name) },
            trailingIcon = { Text("✎", modifier = Modifier.clickable { editingChar = ch }) },
        )
        AssistChip(onClick = { editingChar = PicCharacter("", "", Random.nextLong(0, 1L shl 31)) }, label = { Text("+ Character") })
    }
    if (characters.isEmpty()) Hint("Describe someone once; tick them and they look the same in every image.")
    editingChar?.let { ch ->
        CharacterDialog(ch, onClose = { editingChar = null }, onDelete = {
            saveChars(characters.filter { it.name != ch.name }); chosen = chosen - ch.name; editingChar = null
        }) { new ->
            saveChars(characters.filter { it.name != ch.name && it.name != new.name } + new)
            chosen = (chosen - ch.name) + new.name
            editingChar = null
        }
    }

    // Shape and resolution
    val src = images.firstOrNull()
    val shapeIds = shapes.map { it.id } + (if (edit) listOf("src") else emptyList())
    val asp = aspect.takeIf { it in shapeIds } ?: shapeIds.first()
    val (w, h) = sizeFor(verified, shapes, asp, res, src)
    LabelRow("Shape", "$w × $h" + if (verified) " · sizes this model supports" else "")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (sh in shapes) ShapeButton(sh, sh.id, asp == sh.id) {
            aspect = sh.id
            st.setSetting(if (edit) "img.aspect.edit" else "img.aspect", sh.id)
        }
        if (edit) ShapeButton(null, "Source", asp == "src") {
            aspect = "src"
            st.setSetting("img.aspect.edit", "src")
        }
    }
    val resList = resOptions(verified, shapes, asp, res, src)
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            LabelRow("Resolution")
            val shown = resList.minByOrNull { Math.abs(it - res) } ?: res
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in resList) FilterChip(selected = r == shown, onClick = { res = r; st.setSetting("img.res", "$r") }, label = { Text("$r") })
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Images", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { n = maxOf(1, n - 1) }, enabled = n > 1) { Text("−") }
                Text("$n", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { n = minOf(4, n + 1) }, enabled = n < 4) { Text("+") }
            }
        }
    }

    // Advanced
    val summary = listOfNotNull(
        if (!stepsAuto) "${steps.toInt()} steps" else null,
        if (!cfgAuto) "cfg ${"%.1f".format(cfg)}" else null,
        if (seed.isNotBlank()) "seed $seed" + (if (seedLocked) " 🔒" else "") else null,
    ).joinToString(" · ").ifBlank { "auto" }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { advanced = !advanced }) { Text((if (advanced) "▾ " else "▸ ") + "Advanced") }
                Spacer(Modifier.weight(1f))
                Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (advanced) {
                LabelRow("Negative prompt", "what to avoid")
                OutlinedTextField(
                    value = negative,
                    onValueChange = { negative = it; st.setSetting("img.$mid.neg", it) },
                    placeholder = { Text("e.g. text, extra fingers, oversaturated colours") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabelRow("Seed", if (seedLocked) "locked" else if (seed.isNotBlank()) "used once, then re-rolled" else "new each run")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { v -> seed = v.filter { it.isDigit() }.take(10) },
                        placeholder = { Text("random") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { seed = Random.nextLong(0, 1L shl 31).toString() }) { Text("🎲") }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { seedLocked = !seedLocked }) { Text(if (seedLocked) "🔒" else "🔓") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Steps" + if (stepsAuto) "" else "  ${steps.toInt()}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Checkbox(checked = stepsAuto, onCheckedChange = { stepsAuto = it; st.setSetting("img.$mid.stepsAuto", if (it) "1" else "0") })
                    Text("model default", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = steps,
                    onValueChange = { steps = Math.round(it).toFloat(); st.setSetting("img.$mid.steps", "${steps.toInt()}") },
                    valueRange = 4f..60f,
                    enabled = !stepsAuto,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Guidance (CFG)" + if (cfgAuto) "" else "  ${"%.1f".format(cfg)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Checkbox(checked = cfgAuto, onCheckedChange = { cfgAuto = it; st.setSetting("img.$mid.cfgAuto", if (it) "1" else "0") })
                    Text("model default", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = cfg,
                    onValueChange = { cfg = Math.round(it * 2) / 2f; st.setSetting("img.$mid.cfg", "$cfg") },
                    valueRange = 1f..10f,
                    steps = 17,
                    enabled = !cfgAuto,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Live preview", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Watch the image form. Slower: each preview costs about 2.5 s per megapixel.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = livePreview, onCheckedChange = { livePreview = it; st.setSetting("img.preview", if (it) "1" else "0") })
                }
            }
        }
    }

    val stp = if (stepsAuto) null else steps.toInt()
    val est = imageEstimate(mid, w, h, stp, n, edit)
    val why = notReady ?: when {
        prompt.text.isBlank() -> if (edit) "Describe the change." else "Describe the image."
        edit && images.isEmpty() -> "Choose an image to edit."
        else -> null
    }
    val tool = if (edit) "edit" else "image"
    RunPanel(tool, (if (edit) "Apply edit" else "Generate") + "  ·  ~" + Tools.fmtDuration(Math.round(est).coerceAtLeast(1L)), why == null, why, st) {
        var pr = prompt.text.trim()
        if (transparent && transparentOk && !pr.contains("transparent background", true)) {
            pr = pr.trimEnd('.', ' ') + ". The image has an alpha channel and a transparent background."
        }
        val neg = negative.trim()
        val size = "${w}x$h"
        val picked = characters.filter { it.name in chosen }
        if (picked.isNotEmpty()) {
            // Word for word, every time: that is what keeps them looking the same.
            pr += "\n\n" + picked.joinToString("\n") { "${it.name}: ${it.description.trim().trimEnd('.')}." }
        }
        // A character's own seed, unless one was set here.
        val sd = seed.toLongOrNull() ?: picked.firstOrNull()?.seed ?: Random.nextLong(0, 1L shl 31)
        if (!seedLocked) seed = "" else seed = "$sd"
        val g = if (cfgAuto) null else cfg.toDouble()
        val srcs = if (edit) images.map { it.dataUri } else emptyList()
        val count = n
        val every = if (livePreview) 4 else 0
        st.run(tool) { c, store, p, _ -> Tools.image(c, store, mid, pr, neg, size, stp, sd, srcs, p, count, g, every) }
    }
}

// ---------------- Text (OCR) ----------------

@Composable
internal fun TextTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<TextSource?>(null) }
    var opening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            opening = true
            error = null
            try {
                source = withContext(Dispatchers.IO) { DocText.open(ctx, uri) }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't open that file."
            } finally {
                opening = false
            }
        }
    }
    LabelRow("Source image", "pick an image or document")
    OutlinedCard(onClick = { pick.launch(DocText.MIMES) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(if (opening) "Opening…" else "Choose an image or document", style = MaterialTheme.typography.titleSmall)
            Hint("Photos, scans, screenshots, PDF, Word, PowerPoint, Excel, OpenDocument, EPUB, RTF, HTML or text. Documents are read right away.")
        }
    }
    RecentImages(st) { img -> source = TextSource.Picture("your image", img) }
    error?.let { Hint(it, warn = true) }
    when (val s = source) {
        is TextSource.Picture -> {
            Image(s.image.preview.asImageBitmap(), s.name, Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
            TextButton(onClick = { source = null }) { BtnLabel("Remove", androidx.compose.material.icons.Icons.Filled.Close) }
        }
        is TextSource.Pages -> {
            Text("${s.name} · ${s.pages.size} page${if (s.pages.size == 1) "" else "s"}" + (if (s.total > s.pages.size) " of ${s.total} (the first ${DocText.MAX_PAGES} are read)" else ""))
            TextButton(onClick = { source = null }) { BtnLabel("Remove", androidx.compose.material.icons.Icons.Filled.Close) }
        }
        is TextSource.Document -> {
            Text("${s.name} · ${s.text.length} characters, read without the model")
            TextButton(onClick = { source = null }) { BtnLabel("Remove", androidx.compose.material.icons.Icons.Filled.Close) }
        }
        null -> {}
    }
    val doc = source as? TextSource.Document
    val why = when {
        source == null -> "Choose something to read."
        doc != null -> null
        else -> notReady
    }
    RunPanel("ocr", if (source is TextSource.Pages) "Read text  ·  ${(source as TextSource.Pages).pages.size} pages" else "Read text", why == null, why, st) {
        val s = source!!
        val id = model?.id ?: ""
        st.run("ocr") { c, store, p, _ ->
            when (s) {
                is TextSource.Picture -> Tools.ocr(c, store, id, s.image.dataUri, p)
                is TextSource.Pages -> Tools.ocrPages(c, store, id, s.name, s.pages, s.total, p)
                is TextSource.Document -> ResultEntry(store.newId(), "ocr", "Text from ${s.name}", emptyList(), s.text, "document", System.currentTimeMillis())
            }
        }
    }
}

private val MAX_REFS = Regex("up to (\\d+) reference images", RegexOption.IGNORE_CASE)
private val TRANSPARENT = Regex("transparent|alpha channel|RGBA", RegexOption.IGNORE_CASE)


/** A character for the pictures: its name, its fixed description, and its seed. */
internal data class PicCharacter(val name: String, val description: String, val seed: Long)

internal fun loadCharacters(json: String): List<PicCharacter> = try {
    val a = org.json.JSONArray(json)
    (0 until a.length()).mapNotNull { i ->
        a.optJSONObject(i)?.let { o -> PicCharacter(o.optString("name"), o.optString("description"), o.optLong("seed")) }
    }.filter { it.name.isNotBlank() }
} catch (e: Exception) {
    emptyList()
}

internal fun charactersJson(list: List<PicCharacter>): String = org.json.JSONArray(
    list.map { org.json.JSONObject().put("name", it.name).put("description", it.description).put("seed", it.seed) },
).toString()

@Composable
private fun CharacterDialog(ch: PicCharacter, onClose: () -> Unit, onDelete: () -> Unit, onSave: (PicCharacter) -> Unit) {
    var name by remember { mutableStateOf(ch.name) }
    var desc by remember { mutableStateOf(ch.description) }
    var seed by remember { mutableStateOf(ch.seed.toString()) }
    var askDelete by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (ch.name.isBlank()) "New character" else "Character") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    desc, { desc = it.take(600) }, label = { Text("How they look") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. a small scruffy wheat-coloured terrier with one folded left ear and a red collar") },
                )
                Text(
                    "Colours, clothes, hair and marks work best. Used word for word, with the seed, in every image.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(seed, { v -> seed = v.filter { it.isDigit() }.take(10) }, label = { Text("Seed") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (ch.name.isNotBlank()) {
                    if (askDelete) TextButton(onClick = onDelete) { Text("Really delete ${ch.name}", color = MaterialTheme.colorScheme.error) }
                    else TextButton(onClick = { askDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(PicCharacter(name.trim(), desc.trim(), seed.toLongOrNull() ?: ch.seed)) },
                enabled = name.isNotBlank() && desc.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

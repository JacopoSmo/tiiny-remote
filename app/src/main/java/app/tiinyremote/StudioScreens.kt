@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.tiinyremote

import android.Manifest
import android.content.pm.PackageManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** A tool, filed under the model type TiinyOS shows in its store. */
private class Tool(val id: String, val type: String, val title: String, val what: String, val fits: (ModelInfo) -> Boolean)

private val TOOLS = listOf(
    Tool("describe", "Image-Text-to-Text", "Describe image", "Have a picture described, or ask a question about it", { it.seesImages }),
    Tool("chat", "Text Generation", "Chat", "Talk with the loaded model (also in the Chat tab)", { it.isChat }),
    Tool("image", "Text-to-Image", "Create image", "A picture from a description", { it.isImageGen }),
    Tool("edit", "Text-to-Image", "Edit image", "Change a photo with words", { it.canEditImages }),
    Tool("transcribe", "ASR", "Transcribe", "Speech to text from a recording or file", { it.isAsr }),
    Tool("speech", "Text-to-Speech", "Voice", "Text to speech, built-in, described or cloned voices", { it.isSpeech }),
    Tool("ocr", "Image-to-Text", "Read text (OCR)", "Copy the text out of a photo or screenshot", { it.isOcr }),
    Tool("loop", "Music Generation", "Loop", "Short music loops at a set tempo and key", { it.isLoopModel }),
    Tool("song", "Music Generation", "Song", "Full songs with lyrics, or instrumentals", { it.isSongModel }),
    Tool("mesh", "Image-to-3D", "3D model", "A 3D model (.glb) from one picture", { it.is3d }),
    Tool("embed", "Text Embedding", "Compare texts", "How similar in meaning are a few texts?", { it.isEmbedding }),
    Tool("rerank", "Text Reranking", "Rank documents", "Order texts by how well they answer a question", { it.isReranker }),
    Tool("classify", "Text Classification", "Classify text", "Yes/no, pick-one and score questions about a text", { it.isClassifier }),
    Tool("video", "Text-to-Video", "Video", "Short clips from a description or a picture", { it.isVideo }),
)

private val KEYS = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    .flatMap { listOf("$it major", "$it minor") }

private const val LYRICS_TEMPLATE = "[verse]\nCity lights are fading slow\nI keep walking where the rivers go\n\n" +
    "[chorus]\nHold on, hold on to the night\nWe're burning like a paper kite"

// ---------------- the Create tab ----------------

@Composable
fun CreateScreen(s: UiState, vm: MainViewModel, st: StudioViewModel, openChat: () -> Unit) {
    var toolId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = toolId != null) { toolId = null }
    val tool = TOOLS.firstOrNull { it.id == toolId }
    if (tool == null) {
        ToolHub(s, st) { id ->
            if (id == "chat") openChat()
            if (id != "chat") toolId = id
        }
    } else {
        ToolScreen(tool, s, vm, st) { toolId = null }
    }
}

@Composable
private fun ToolHub(s: UiState, st: StudioViewModel, open: (String) -> Unit) {
    val results by st.results.collectAsState()
    val job by st.job.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (s.device != DeviceState.ONLINE) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Text("The Tiiny isn't online: see the Models tab.", Modifier.padding(14.dp))
            }
        }
        job?.takeIf { it.running }?.let { j ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Working: ${TOOLS.firstOrNull { it.id == j.tool }?.title ?: j.tool}", style = MaterialTheme.typography.titleSmall)
                    Text(j.message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { open(j.tool) }) { Text("Open") }
                }
            }
        }
        var lastType = ""
        for (t in TOOLS) {
            if (t.type != lastType) {
                lastType = t.type
                Text(t.type, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            }
            val installed = s.models.count(t.fits)
            val loaded = s.models.count { t.fits(it) && it.running }
            Card(onClick = { open(t.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    Text(t.what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            installed == 0 -> "No model for this on your Tiiny"
                            loaded > 0 -> "$loaded model${if (loaded == 1) "" else "s"} loaded"
                            else -> "$installed model${if (installed == 1) "" else "s"} installed, none loaded"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (loaded > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Your results", style = MaterialTheme.typography.titleMedium)
        if (results.isEmpty()) Text("Nothing yet. What you make is kept here.", style = MaterialTheme.typography.bodyMedium)
        for (e in results.take(60)) ResultCard(e, st)
    }
}

// ---------------- shared pieces ----------------

/** Picks the model for a tool and offers to load it. Returns the chosen model, or null if none fits. */
@Composable
private fun ModelPicker(tool: String, fits: (ModelInfo) -> Boolean, s: UiState, vm: MainViewModel, st: StudioViewModel): ModelInfo? {
    val choices = s.models.filter(fits).filter { it.id !in s.unavailable }
    if (choices.isEmpty()) {
        Text(
            if (s.device == DeviceState.ONLINE) "No model for this is installed on your Tiiny. Download one in TiinyOS."
            else "The Tiiny isn't online: see the Models tab.",
            color = MaterialTheme.colorScheme.error,
        )
        return null
    }
    var picked by remember(tool) { mutableStateOf(st.pick(tool)) }
    val model = choices.firstOrNull { it.id == picked } ?: choices.firstOrNull { it.running } ?: choices.first()
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(model.name, maxLines = 1)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (m in choices) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(m.name + if (m.running) "  (loaded)" else "")
                                    if (m.abilities.isNotEmpty()) {
                                        Text(m.abilities.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = {
                                picked = m.id
                                st.setPick(tool, m.id)
                                open = false
                            },
                        )
                    }
                }
            }
            if (model.abilities.isNotEmpty()) {
                Text(model.abilities.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val working = model.id in s.working || model.status == "loading"
                Text(
                    when {
                        model.running -> "Loaded and ready"
                        working -> "Loading…"
                        else -> "Not loaded" + (model.loadTimeSec?.let { " · loads in ~$it s" } ?: "")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (model.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    model.running -> {}
                    working -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> Button(onClick = { vm.load(model) }) { Text("Load") }
                }
            }
        }
    }
    return model
}

@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((value, label) in options) {
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, lines: Int = 1, placeholder: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = lines == 1,
        minLines = lines,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Progress, error and result of the tool's last job, plus the Run button. */
@Composable
private fun RunPanel(tool: String, label: String, ready: Boolean, whyNot: String?, st: StudioViewModel, onRun: () -> Unit) {
    val job by st.job.collectAsState()
    val mine = job?.takeIf { it.tool == tool }
    val busyElsewhere = job?.running == true && job?.tool != tool
    if (whyNot != null && mine?.running != true) {
        Text(whyNot, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (mine?.running == true) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mine.message.ifBlank { "Working…" })
                val pct = mine.percent
                if (pct != null && pct > 0) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Keep the app open until it's done; the screen stays on meanwhile." +
                        if (tool == "video") " A video still rendering is picked up again if the app closes." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { st.cancel() }) { Text("Cancel") }
            }
        }
    } else {
        Button(
            onClick = onRun,
            enabled = ready && !busyElsewhere,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busyElsewhere) "Busy with another tool…" else label) }
    }
    mine?.error?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Text(it, Modifier.padding(14.dp))
        }
    }
    mine?.result?.let { ResultCard(it, st, expanded = true) }
}

@Composable
private fun ImagePickerRow(images: List<PickedImage>, max: Int, label: String, onPicked: (List<PickedImage>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            try {
                onPicked(listOf(withContext(Dispatchers.IO) { Inputs.image(ctx, uri) }))
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read that picture."
            }
        }
    }
    val multi = if (max > 1) rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(max)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            try {
                onPicked(withContext(Dispatchers.IO) { uris.map { Inputs.image(ctx, it) } })
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read that picture."
            }
        }
    } else null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (multi != null) multi.launch(req) else single.launch(req)
            }) { Text(if (images.isEmpty()) label else "Choose again") }
            if (images.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onPicked(emptyList()) }) { Text("Remove") }
            }
        }
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (img in images) {
                    Image(img.preview.asImageBitmap(), null, Modifier.size(110.dp), contentScale = ContentScale.Crop)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Record from the microphone (16 kHz WAV) or pick an audio file. */
@Composable
private fun AudioInput(audio: PickedAudio?, onAudio: (PickedAudio?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { WavRecorder() }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0.0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recording) {
        while (recording) {
            seconds = recorder.seconds
            delay(250)
        }
    }
    val startRecording: () -> Unit = {
        try {
            recorder.start()
            recording = true
            error = null
        } catch (e: Exception) {
            error = "Couldn't start recording: ${e.message}"
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording()
        if (!ok) error = "Recording needs microphone access."
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            try {
                onAudio(withContext(Dispatchers.IO) { Inputs.audio(ctx, uri) })
                error = null
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (recording) {
                Button(onClick = {
                    val wav = recorder.stop()
                    recording = false
                    onAudio(PickedAudio(wav, "audio/wav", "recording.wav", wav.size / 32000.0))
                }) { Text("Stop (%.0f s)".format(seconds)) }
            } else {
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                    else perm.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Record") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { pick.launch("audio/*") }) { Text("Pick a file") }
            }
        }
        audio?.let { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.name + (a.seconds?.let { " · %.0f s".format(it) } ?: "") + " · ${a.bytes.size / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAudio(null) }) { Text("Remove") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun seedOf(text: String): Long = text.trim().toLongOrNull() ?: Random.nextLong(0, 1L shl 31)

// ---------------- the tools ----------------

@Composable
private fun ToolScreen(tool: Tool, s: UiState, vm: MainViewModel, st: StudioViewModel, back: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹ Tools") }
            Spacer(Modifier.width(6.dp))
            Column {
                Text(tool.title, style = MaterialTheme.typography.titleLarge)
                Text(tool.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val model = ModelPicker(tool.id, tool.fits, s, vm, st)
        val notReady = when {
            model == null -> "No model to use."
            !model.running -> "Load the model first."
            else -> null
        }
        when (tool.id) {
            "image" -> ImageTool(model, notReady, st, edit = false)
            "edit" -> ImageTool(model, notReady, st, edit = true)
            "ocr" -> ReadTool(model, notReady, st, ocr = true)
            "describe" -> ReadTool(model, notReady, st, ocr = false)
            "speech" -> SpeechTool(model, notReady, st)
            "transcribe" -> TranscribeTool(model, notReady, st)
            "loop" -> LoopTool(model, notReady, st)
            "song" -> SongTool(model, notReady, st)
            "video" -> VideoTool(model, notReady, st)
            "embed" -> EmbedTool(model, notReady, st)
            "rerank" -> RerankTool(model, notReady, st)
            "classify" -> ClassifyTool(model, notReady, st)
            "mesh" -> MeshTool(model, notReady, st, s.models)
        }
    }
}

@Composable
private fun ImageTool(model: ModelInfo?, notReady: String?, st: StudioViewModel, edit: Boolean) {
    var prompt by rememberSaveable { mutableStateOf("") }
    var negative by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf(if (edit) "" else "1024x1024") }
    var steps by rememberSaveable { mutableStateOf("") }
    var seed by rememberSaveable { mutableStateOf("") }
    var images by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var advanced by remember { mutableStateOf(false) }
    if (edit) ImagePickerRow(images, 4, "Choose photo(s) to edit") { images = it }
    Field(prompt, { prompt = it }, if (edit) "What to change" else "Describe the picture", lines = 3)
    val sizes = listOf("1024x1024" to "Square", "768x1024" to "Portrait", "1024x768" to "Landscape", "512x512" to "Small")
    Chips(if (edit) listOf("" to "Keep size") + sizes else sizes, size) { size = it }
    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
    if (advanced) {
        Field(negative, { negative = it }, "Avoid (negative prompt)")
        Field(steps, { steps = it.filter(Char::isDigit) }, "Steps (empty = model default)")
        Field(seed, { seed = it.filter(Char::isDigit) }, "Seed (empty = random)")
    }
    val why = notReady ?: when {
        prompt.isBlank() -> if (edit) "Say what to change." else "Describe the picture."
        edit && images.isEmpty() -> "Choose a photo to edit."
        else -> null
    }
    RunPanel(if (edit) "edit" else "image", if (edit) "Edit" else "Create", why == null, why, st) {
        val m = model!!.id
        val srcs = images.map { it.dataUri }
        val stp = steps.toIntOrNull()
        val sd = seed.toLongOrNull()
        st.run(if (edit) "edit" else "image") { c, store, p, _ -> Tools.image(c, store, m, prompt.trim(), negative, size, stp, sd, srcs, p) }
    }
}

@Composable
private fun ReadTool(model: ModelInfo?, notReady: String?, st: StudioViewModel, ocr: Boolean) {
    val tool = if (ocr) "ocr" else "describe"
    var images by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var question by rememberSaveable { mutableStateOf("") }
    ImagePickerRow(images, 1, "Choose a picture") { images = it }
    if (!ocr) {
        Field(question, { question = it }, "Question (empty = describe it)", lines = 2)
        Text(
            "Uses a chat model that sees images, e.g. one marked Image-Text-to-Text.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val why = notReady ?: if (images.isEmpty()) "Choose a picture." else null
    RunPanel(tool, if (ocr) "Read text" else "Describe", why == null, why, st) {
        val m = model!!.id
        val img = images.first().dataUri
        val q = question
        st.run(tool) { c, store, p, _ -> if (ocr) Tools.ocr(c, store, m, img, p) else Tools.describe(c, store, m, img, q, p) }
    }
}

@Composable
private fun SpeechTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var caps by remember { mutableStateOf<Tools.VoiceCaps?>(null) }
    LaunchedEffect(model?.id, model?.running) {
        caps = if (model?.running == true) withContext(Dispatchers.IO) { runCatching { Tools.voiceCaps(st.client(), model.id) }.getOrNull() } else null
    }
    val c = caps
    val modes = buildList {
        if (c != null && c.speakers.isNotEmpty()) add("speaker" to "Built-in voice")
        if (c == null || c.clone) add("clone" to "Clone a voice")
        if (c != null && c.design) add("design" to "Describe a voice")
    }
    var mode by rememberSaveable { mutableStateOf("") }
    val m = if (modes.any { it.first == mode }) mode else modes.firstOrNull()?.first ?: "clone"
    var text by rememberSaveable { mutableStateOf("") }
    var speaker by rememberSaveable { mutableStateOf("") }
    var design by rememberSaveable { mutableStateOf("") }
    var refText by rememberSaveable { mutableStateOf("") }
    var instruct by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var clip by remember { mutableStateOf<PickedAudio?>(null) }
    var speakerOpen by remember { mutableStateOf(false) }

    if (modes.size > 1) Chips(modes, m) { mode = it }
    when (m) {
        "speaker" -> {
            val list = c?.speakers ?: emptyList()
            val chosen = speaker.takeIf { it in list } ?: list.firstOrNull() ?: ""
            Box {
                OutlinedButton(onClick = { speakerOpen = true }) { Text("Voice: $chosen") }
                DropdownMenu(expanded = speakerOpen, onDismissRequest = { speakerOpen = false }) {
                    for (v in list) DropdownMenuItem(text = { Text(v) }, onClick = { speaker = v; speakerOpen = false })
                }
            }
        }
        "design" -> Field(design, { design = it }, "Describe the voice", lines = 2, placeholder = "e.g. a calm, deep male voice")
        else -> {
            Text("Record or pick a clear 3–20 second clip of the voice, and type exactly what's said in it.", style = MaterialTheme.typography.bodySmall)
            AudioInput(clip) { clip = it }
            Field(refText, { refText = it }, "What's said in the clip", lines = 2)
            if (c?.instruct == true) Field(instruct, { instruct = it }, "Style (optional)", placeholder = "e.g. cheerful, slow")
        }
    }
    Field(text, { text = it }, "Text to speak", lines = 4)
    val langs = c?.languages ?: emptyList()
    if (langs.isNotEmpty()) Chips(listOf("" to "Auto") + langs.map { it to it }, language) { language = it }
    else Field(language, { language = it }, "Language (optional)")
    val why = notReady ?: when {
        text.isBlank() -> "Type something to say."
        m == "clone" && clip == null -> "Record or pick a voice clip."
        m == "clone" && refText.isBlank() -> "Type what's said in the clip."
        m == "design" && design.isBlank() -> "Describe the voice."
        else -> null
    }
    RunPanel("speech", "Speak", why == null, why, st) {
        val id = model!!.id
        val sp = if (m == "speaker") (speaker.takeIf { it in (c?.speakers ?: emptyList()) } ?: c?.speakers?.firstOrNull()) else null
        val ds = if (m == "design") design else null
        val ref = if (m == "clone") clip?.bytes else null
        val rt = if (m == "clone") refText else null
        val t = text
        val lang = language
        val ins = instruct
        st.saveVoice(ref, rt, sp) // the chat's "speak" uses the voice last used here
        st.run("speech") { cl, store, p, _ -> Tools.speech(cl, store, id, t, sp, ds, ref, rt, lang, ins, p) }
    }
}

@Composable
private fun TranscribeTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var audio by remember { mutableStateOf<PickedAudio?>(null) }
    var language by rememberSaveable { mutableStateOf("") }
    AudioInput(audio) { audio = it }
    Field(language, { language = it }, "Language (optional, e.g. en, de)")
    val why = notReady ?: if (audio == null) "Record or pick something to transcribe." else null
    RunPanel("transcribe", "Transcribe", why == null, why, st) {
        val a = audio!!
        val id = model!!.id
        val lang = language.trim()
        st.run("transcribe") { c, store, p, _ -> Tools.transcribe(c, store, id, a.bytes, a.mime, a.name, lang, p) }
    }
}

@Composable
private fun LoopTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var desc by rememberSaveable { mutableStateOf("Wavetable Synth, warm, chord progression") }
    var bpm by rememberSaveable { mutableStateOf("120") }
    var bars by rememberSaveable { mutableStateOf("4") }
    var key by rememberSaveable { mutableStateOf("A minor") }
    var steps by rememberSaveable { mutableStateOf("64") }
    var cfg by rememberSaveable { mutableStateOf("6") }
    var keyOpen by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    Field(desc, { desc = it }, "Instrument and character", lines = 2)
    Text("Tempo (BPM)", style = MaterialTheme.typography.labelLarge)
    Chips(listOf("100", "110", "120", "128", "130", "140", "150").map { it to it }, bpm) { bpm = it }
    Text("Length", style = MaterialTheme.typography.labelLarge)
    Chips(listOf("4" to "4 bars", "8" to "8 bars"), bars) { bars = it }
    Box {
        OutlinedButton(onClick = { keyOpen = true }) { Text("Key: $key") }
        DropdownMenu(expanded = keyOpen, onDismissRequest = { keyOpen = false }) {
            for (k in KEYS) DropdownMenuItem(text = { Text(k) }, onClick = { key = k; keyOpen = false })
        }
    }
    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
    if (advanced) {
        Field(steps, { steps = it.filter(Char::isDigit) }, "Steps")
        Field(cfg, { cfg = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Guidance (CFG)")
    }
    val b = bpm.toInt()
    val n = bars.toInt()
    val prompt = "${desc.trim()}, $n bars, $b BPM, $key"
    val secs = n * 4 * 60.0 / b
    Text("Prompt: $prompt · %.1f s".format(secs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val why = notReady ?: if (desc.isBlank()) "Describe the loop." else null
    RunPanel("loop", "Make loop", why == null, why, st) {
        val id = model!!.id
        val stp = steps.toIntOrNull() ?: 64
        val cf = cfg.toDoubleOrNull() ?: 6.0
        st.run("loop") { c, store, p, _ -> Tools.loop(c, store, id, prompt, secs, stp, cf, seedOf(""), p) }
    }
}

@Composable
private fun SongTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var caption by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("mixed") }
    var lyrics by rememberSaveable { mutableStateOf(LYRICS_TEMPLATE) }
    var seconds by rememberSaveable { mutableStateOf("60") }
    Field(caption, { caption = it }, "Style", lines = 2, placeholder = "e.g. upbeat pop, female vocals, synth")
    Chips(listOf("mixed" to "With vocals", "bgm" to "Instrumental", "vocal" to "Vocals only"), type) { type = it }
    if (type != "bgm") Field(lyrics, { lyrics = it }, "Lyrics (with [verse], [chorus] … tags)", lines = 8)
    Text("Length", style = MaterialTheme.typography.labelLarge)
    Chips(listOf("30" to "30 s", "60" to "1 min", "120" to "2 min", "180" to "3 min", "240" to "Full (4 min+)"), seconds) { seconds = it }
    Text("Songs take a while: roughly 3–4 seconds of work per second of music.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val why = notReady ?: when {
        caption.isBlank() -> "Describe the style."
        type != "bgm" && lyrics.isBlank() -> "Add lyrics, or choose Instrumental."
        else -> null
    }
    RunPanel("song", "Compose", why == null, why, st) {
        val id = model!!.id
        val cap = caption.trim()
        val lyr = lyrics.trim()
        val t = type
        val secs = seconds.toInt()
        st.run("song") { c, store, p, _ -> Tools.song(c, store, id, cap, lyr, t, secs, seedOf(""), p) }
    }
}

/** Tiiny Studio's estimate, measured on TiinyOS 0.1.38: ~5 min per clip, then ~1 min per second at 480p; more pixels and steps cost more. */
private fun videoEstimate(size: String, seconds: Int, steps: Int): Double {
    val (w, h) = size.split("x").map { it.toDoubleOrNull() ?: 0.0 }.let { (it.getOrElse(0) { 832.0 }) to (it.getOrElse(1) { 480.0 }) }
    return (314 + 60.4 * seconds * Math.pow(w * h / (832.0 * 480.0), 1.75)) * Math.pow(steps / 4.0, 1.4)
}

private const val VIDEO_LIMIT = 3600.0 // the Tiiny stops a video render after 1 hour

private fun sizeLabel(c: String): String {
    val (w, h) = c.split("x").map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 0 } to it.getOrElse(1) { 0 } }
    return "${if (w > h) "Wide" else "Tall"} ${minOf(w, h)}p"
}

@Composable
private fun VideoTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var info by remember { mutableStateOf<Tools.VideoInfo?>(null) }
    LaunchedEffect(model?.id, model?.running) {
        info = if (model?.running == true) withContext(Dispatchers.IO) { runCatching { Tools.videoInfo(st.client(), model.id) }.getOrNull() } else null
    }
    // MiniMax-H3-Turbo's values when the model doesn't say (or isn't loaded yet)
    val canvases = info?.canvases?.takeIf { it.isNotEmpty() } ?: listOf("832x480", "480x832", "1344x768", "768x1344")
    val stepOptions = info?.steps?.takeIf { it.isNotEmpty() } ?: listOf(4, 5, 6, 7, 8)
    val defSteps = info?.defaultSteps ?: 7
    val fps = info?.fps ?: 24
    val audio = info?.audio ?: true

    var prompt by rememberSaveable { mutableStateOf("") }
    var size by remember { mutableStateOf(st.setting("video.size", "832x480")) }
    var seconds by remember { mutableStateOf(st.setting("video.seconds", "2")) }
    var steps by remember { mutableStateOf(st.setting("video.steps", "4")) } // "0" = the model's default
    var seed by rememberSaveable { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var longOk by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<PickedImage>>(emptyList()) }

    val sz = if (size in canvases) size else canvases.firstOrNull { it == "832x480" } ?: canvases.first()
    val stp = if (steps == "0" || steps.toIntOrNull()?.let { it in stepOptions } == true) steps else "0"
    val usedSteps = stp.toInt().takeIf { it > 0 } ?: defSteps
    val secs = seconds.toIntOrNull() ?: 2
    val est = videoEstimate(sz, secs, usedSteps)
    val over = est > VIDEO_LIMIT
    LaunchedEffect(over) { if (!over) longOk = false }

    Text("Prompt", style = MaterialTheme.typography.labelLarge)
    Field(prompt, { prompt = it }, "Describe the clip", lines = 4, placeholder = "What happens in the clip: subject, motion, camera, sound…")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Video is slow on the Tiiny: about ${Tools.fmtDuration(est.toLong())} for these settings (2 s at 480p with 4 steps took 7 minutes). " +
                "The device does nothing else meanwhile; start small.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Start from a picture", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text("optional", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ImagePickerRow(images, 1, "First frame") { images = it }

    Text("Size", style = MaterialTheme.typography.labelLarge)
    Chips(canvases.map { it to sizeLabel(it) }, sz) { size = it; st.setSetting("video.size", it) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Length", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text("$fps fps" + if (audio) " · with sound" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // MiniMax-H3-Turbo is built for clips up to 7 s; longer ones fail at once
    Chips(listOf(2, 3, 5, 7).map { "$it" to "$it s" }, seconds) { seconds = it; st.setSetting("video.seconds", it) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { advanced = !advanced }) { Text((if (advanced) "▾ " else "▸ ") + "Advanced") }
                Spacer(Modifier.weight(1f))
                Text(
                    "$usedSteps steps" + (if (seed.isNotBlank()) " · seed $seed" else ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (advanced) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Steps", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("fewer is faster", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Chips(listOf("0" to "Default ($defSteps)") + stepOptions.map { "$it" to "$it" }, stp) { steps = it; st.setSetting("video.steps", it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seed", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("blank = new each run", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { v -> seed = v.filter { it.isDigit() }.take(10) },
                        placeholder = { Text("random") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { seed = Random.nextLong(0, 1L shl 31).toString() }) { Text("Roll") }
                }
            }
        }
    }

    if (over) {
        // what would finish inside the hour: the smallest size in this shape, the fewest steps, as many seconds as fit
        val tall = sz.split("x").let { (it.getOrNull(1)?.toIntOrNull() ?: 0) > (it.getOrNull(0)?.toIntOrNull() ?: 0) }
        val small = canvases.filter { c -> c.split("x").let { (it.getOrNull(1)?.toIntOrNull() ?: 0) > (it.getOrNull(0)?.toIntOrNull() ?: 0) } == tall }
            .minByOrNull { c -> c.split("x").mapNotNull { it.toIntOrNull() }.fold(1) { a, b -> a * b } } ?: sz
        val few = stepOptions.minOrNull() ?: 4
        var fit = secs
        while (fit > 1 && videoEstimate(small, fit, few) > VIDEO_LIMIT * 0.9) fit--
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Estimated at ${Tools.fmtDuration(est.toLong())}, but the Tiiny stops a video after 1 hour, so it would end unfinished. " +
                    "At ${sizeLabel(small).substringAfter(' ')} and $few steps, $fit s should fit." +
                    if (longOk) " Tap again to start it anyway." else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    val why = notReady ?: if (prompt.isBlank() && images.isEmpty()) "Describe the clip, or give it a picture to start from." else null
    RunPanel("video", "Make video  (~${Tools.fmtDuration(est.toLong())})", why == null, why, st) {
        if (over && !longOk) {
            longOk = true
            return@RunPanel
        }
        longOk = false
        st.video(model!!.id, prompt.trim(), sz, secs, stp.toInt().takeIf { it > 0 }, seed.toLongOrNull(), images.firstOrNull()?.dataUri)
    }
}

/** An image model that can put a subject on a transparent background (Qwen-Image): loaded ones first, then real alpha. */
private fun cutoutModel(models: List<ModelInfo>): ModelInfo? {
    val ok = Regex("transparent|alpha channel|RGBA", RegexOption.IGNORE_CASE)
    val alpha = Regex("alpha channel|RGBA", RegexOption.IGNORE_CASE)
    return models.filter { it.canEditImages && ok.containsMatchIn(it.desc) }
        .sortedWith(compareBy({ if (it.running) 0 else 1 }, { if (alpha.containsMatchIn(it.desc)) 0 else 1 }))
        .firstOrNull()
}

/** The cut-out keeps the picture's shape at about 512 px on its long side (what Hunyuan3D uses), in steps of 64. */
private fun cutoutSize(w: Int, h: Int): String {
    val k = 512.0 / maxOf(w, h)
    fun r64(v: Int) = maxOf(256, Math.round(v * k / 64).toInt() * 64)
    return "${r64(w)}x${r64(h)}"
}

@Composable
private fun SwitchRow(checked: Boolean, enabled: Boolean = true, title: String, detail: String, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LabelRow(label: String, hint: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MeshTool(model: ModelInfo?, notReady: String?, st: StudioViewModel, models: List<ModelInfo>) {
    var caps by remember { mutableStateOf<Tools.MeshCaps?>(null) }
    LaunchedEffect(model?.id, model?.running) {
        caps = if (model?.running == true) withContext(Dispatchers.IO) { runCatching { Tools.meshCaps(st.client(), model.id) }.getOrNull() } else null
    }
    val t = caps ?: Tools.MeshCaps(
        "hunyuan3d", false, listOf("background", "shape", "simplify", "texture", "export"),
        listOf(Triple("draft", "Draft", "192"), Triple("standard", "Standard", "288"), Triple("high", "High", "384")),
        listOf("fast", "balance", "quality"), emptyList(),
    )
    var images by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var precision by remember { mutableStateOf(st.setting("mesh.precision", "draft")) }
    var quality by remember { mutableStateOf(st.setting("mesh.quality", "balance")) }
    var asset by remember { mutableStateOf(st.setting("mesh.asset", "")) }
    var faces by remember { mutableStateOf(st.setting("mesh.faces", "40000")) }
    var texture by remember { mutableStateOf(st.setting("mesh.texture", "true") == "true") }
    var cutout by remember { mutableStateOf(st.setting("mesh.cutout", "true") == "true") }
    var seed by rememberSaveable { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    val results by st.results.collectAsState()
    val scope = rememberCoroutineScope()

    val prec = if (t.precisions.any { it.first == precision }) precision else t.precisions.firstOrNull()?.first ?: "draft"
    val qual = if (quality in t.qualities) quality else if ("balance" in t.qualities) "balance" else t.qualities.firstOrNull() ?: "balance"
    val nFaces = if (t.simplify) faces.toIntOrNull() ?: 0 else 0
    val tex = texture && t.texture
    val img = images.firstOrNull()
    val cm = cutoutModel(models)
    val cuts = cutout && cm != null && img?.transparent != true
    val cutSecs = if (cuts && cm != null) 85 + (if (cm.running) 0 else 2 * (cm.loadTimeSec ?: 30)) else 0
    val est = Tools.meshEstimate(t, prec, qual, nFaces, tex, cutSecs)
    val plan = listOfNotNull(if (cuts) "cutout" else null, "shape", if (nFaces > 0) "simplify" else null) + (if (tex) listOf("texture", "export") else emptyList())

    LabelRow("Source image")
    ImagePickerRow(images, 1, "Choose a picture") { images = it }
    Text("One object, plain background works best.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val recent = results.filter { it.kind == "image" && it.mainFile != null }.take(12)
    if (recent.isNotEmpty()) {
        LabelRow("From your images")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (e in recent) {
                val f = st.store.file(e.mainFile!!)
                val thumb = remember(f.path) { Inputs.thumbnail(f, 256) }
                if (thumb != null) {
                    Card(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { Inputs.fromFile(f) }?.let { images = listOf(it) }
                        }
                    }) {
                        Image(thumb.asImageBitmap(), e.title, Modifier.size(84.dp), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }

    SwitchRow(
        checked = cutout && cm != null,
        enabled = cm != null,
        title = "Cut out the subject first",
        detail = when {
            cm == null -> "Needs an image model that makes transparent pictures (Qwen-Image)."
            img?.transparent == true -> "This picture already has a transparent background, so it's used as it is."
            else -> "${cm.name} puts the subject on a transparent background first, so the background doesn't end up in the model · adds ~1.5 min" +
                if (cm.running) "" else ", plus loading it (models are swapped back after)"
        },
    ) { cutout = it; st.setSetting("mesh.cutout", it.toString()) }

    if (t.precisions.isNotEmpty()) {
        val hint = t.precisions.map { it.third }.filter { it.isNotBlank() }.let { h ->
            if (h.isEmpty()) "" else if (t.family == "hunyuan3d") "octree " + h.joinToString(" / ") else h.joinToString(" / ") + " px"
        }
        LabelRow("Geometry detail", hint)
        Chips(t.precisions.map { it.first to it.second }, prec) { precision = it; st.setSetting("mesh.precision", it) }
    }
    if (t.qualities.isNotEmpty()) {
        LabelRow("Sampling quality")
        val names = mapOf("fast" to "Fast", "balance" to "Balanced", "quality" to "Best")
        Chips(t.qualities.map { it to (names[it] ?: it) }, qual) { quality = it; st.setSetting("mesh.quality", it) }
    }
    if (t.known && t.assetQualities.isNotEmpty()) {
        LabelRow("Mesh quality", "simplify and export")
        val names = mapOf("fast" to "Fast", "balance" to "Balanced", "quality" to "Best")
        Chips(listOf("" to "Default") + t.assetQualities.map { it to (names[it] ?: it) }, asset) { asset = it; st.setSetting("mesh.asset", it) }
    }
    if (t.simplify) {
        LabelRow("Polygon budget", "simplify before texturing")
        Chips(listOf("0" to "Full", "100000" to "100k", "40000" to "40k", "10000" to "10k"), faces) { faces = it; st.setSetting("mesh.faces", it) }
    }
    if (t.texture) {
        SwitchRow(
            checked = texture,
            title = "PBR texture",
            detail = "Paint color, metal and roughness maps · adds ~${if (t.family == "trellis2") "2.5" else "5"} min",
        ) { texture = it; st.setSetting("mesh.texture", it.toString()) }
        if (texture && nFaces == 0) {
            Text("Texturing a full-resolution mesh is slow: a 40k budget is recommended.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { advanced = !advanced }) { Text((if (advanced) "▾ " else "▸ ") + "Advanced") }
                Spacer(Modifier.weight(1f))
                Text(if (seed.isNotBlank()) "seed $seed" else "auto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (advanced) {
                LabelRow("Seed", "blank = new each run")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = seed,
                        onValueChange = { v -> seed = v.filter { it.isDigit() }.take(10) },
                        placeholder = { Text("random") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { seed = Random.nextLong(0, 1L shl 31).toString() }) { Text("Roll") }
                }
            }
        }
    }

    Text(plan.joinToString(" → "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val why = notReady ?: if (img == null) "Choose a picture." else null
    RunPanel("mesh", "Make 3D model" + if (img != null) "  (~${Tools.fmtDuration(est.toLong())})" else "", why == null, why, st) {
        val id = model!!.id
        val pic = img!!
        val opts = Tools.MeshOptions(
            precision = prec,
            quality = qual,
            assetQuality = asset.takeIf { it.isNotBlank() && t.known && it in t.assetQualities },
            faces = nFaces,
            textured = tex,
            seed = seed.toLongOrNull(),
            cutoutModel = if (cuts) cm?.id else null,
            cutoutSize = cutoutSize(pic.preview.width, pic.preview.height),
        )
        st.run("mesh") { c, store, p, rj -> Tools.mesh(c, store, id, pic.bytes, pic.mime, opts, rj, p) }
    }
}

/** Splits a box of text into items: one per line, or per paragraph when blank lines separate them. */
private fun items(text: String): List<String> {
    val t = text.trim()
    val parts = if (Regex("\\n\\s*\\n").containsMatchIn(t)) t.split(Regex("\\n\\s*\\n")) else t.lines()
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
}

@Composable
private fun EmbedTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var texts by rememberSaveable { mutableStateOf("") }
    Field(texts, { texts = it }, "Texts to compare, one per line", lines = 6)
    Text(
        "Shows how close the texts are in meaning. Useful to test an embedding model before using it for search.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val list = items(texts)
    val why = notReady ?: if (list.size < 2) "Enter at least two texts." else null
    RunPanel("embed", "Compare", why == null, why, st) {
        val id = model!!.id
        st.run("embed") { c, store, p, _ -> Tools.embeddings(c, store, id, list, p) }
    }
}

@Composable
private fun RerankTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var docs by rememberSaveable { mutableStateOf("") }
    Field(query, { query = it }, "Question or search", lines = 2)
    Field(docs, { docs = it }, "Documents: one per line, or separated by blank lines", lines = 8)
    val list = items(docs)
    val why = notReady ?: when {
        query.isBlank() -> "Enter the question."
        list.size < 2 -> "Enter at least two documents."
        else -> null
    }
    RunPanel("rerank", "Rank", why == null, why, st) {
        val id = model!!.id
        val q = query.trim()
        st.run("rerank") { c, store, p, _ -> Tools.rerank(c, store, id, q, list, p) }
    }
}

private class QuestionDraft(type: String = "noul") {
    var type by mutableStateOf(type)
    var instructions by mutableStateOf("")
    var options by mutableStateOf("")
}

@Composable
private fun ClassifyTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val questions = remember { androidx.compose.runtime.mutableStateListOf(QuestionDraft()) }
    Field(text, { text = it }, "Text to classify", lines = 5)
    Text(
        "Ask typed questions about the text; the model answers with probabilities instead of writing text.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    for ((i, q) in questions.withIndex()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Question ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (questions.size > 1) TextButton(onClick = { questions.removeAt(i) }) { Text("Remove") }
                }
                Chips(listOf("noul" to "Yes / no", "choice" to "Pick one", "score" to "Score"), q.type) { q.type = it }
                Field(q.instructions, { q.instructions = it }, "The question", lines = 2,
                    placeholder = when (q.type) {
                        "choice" -> "e.g. What is this message about?"
                        "score" -> "e.g. How urgent is this?"
                        else -> "e.g. Is this about billing?"
                    })
                when (q.type) {
                    "choice" -> Field(q.options, { q.options = it }, "Options, one per line (name: description)", lines = 4,
                        placeholder = "billing: payments, refunds\nshipping: delivery")
                    "score" -> Field(q.options, { q.options = it }, "Levels from low to high, one per line", lines = 4,
                        placeholder = "not urgent\nsomewhat urgent\nvery urgent")
                }
            }
        }
    }
    if (questions.size < 16) OutlinedButton(onClick = { questions.add(QuestionDraft()) }) { Text("Add a question") }
    val qs = questions.map { Tools.Question(it.type, it.instructions.trim(), it.options.lines().map { l -> l.trim() }.filter { l -> l.isNotEmpty() }) }
    val why = notReady ?: when {
        text.isBlank() -> "Enter the text to classify."
        qs.any { it.instructions.isBlank() } -> "Every question needs its wording."
        qs.any { it.type == "choice" && it.options.size < 2 } -> "Pick-one questions need at least two options."
        qs.any { it.type == "score" && it.options.size < 2 } -> "Score questions need at least two levels."
        else -> null
    }
    RunPanel("classify", "Classify", why == null, why, st) {
        val id = model!!.id
        val t = text.trim()
        st.run("classify") { c, store, p, _ -> Tools.classify(c, store, id, t, qs, p) }
    }
}

// ---------------- results ----------------

private val TEXT_KINDS = setOf("ocr", "description", "transcript", "embedding", "rerank", "classification")

private val dateFmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

@Composable
fun ResultCard(e: ResultEntry, st: StudioViewModel, expanded: Boolean = false) {
    var open by remember(e.id) { mutableStateOf(expanded) }
    var confirmDelete by remember(e.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Card(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = if (open) 4 else 1)
            Text(
                "${dateFmt.format(Date(e.created))} · ${e.model.substringAfterLast('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!open) return@Column
            val file = e.mainFile?.let { st.store.file(it) }
            when (e.kind) {
                "image" -> file?.let { f ->
                    val bmp = remember(f.path) { Inputs.thumbnail(f) }
                    bmp?.let { Image(it.asImageBitmap(), e.title, Modifier.fillMaxWidth().heightIn(max = 420.dp), contentScale = ContentScale.Fit) }
                }
                "speech", "loop", "song" -> file?.let { f ->
                    var playing by remember(f.path) { mutableStateOf(Player.playing == f.name) }
                    Button(onClick = {
                        Player.toggle(f) { playing = false }
                        playing = Player.playing == f.name
                    }) { Text(if (playing) "Stop" else "Play") }
                }
                "video" -> file?.let { f ->
                    if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
                "mesh" -> file?.let { f ->
                    if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { st.open(f.name) }) { Text("Open in a 3D viewer") }
                }
            }
            if (e.kind in TEXT_KINDS && e.text.isNotBlank()) {
                SelectionContainer {
                    Text(e.text, fontSize = 14.sp)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (e.files.isNotEmpty()) TextButton(onClick = { st.saveToPhone(e) }) { Text("Save to phone") }
                if (e.text.isNotBlank() && e.kind in TEXT_KINDS) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(e.text)) }) { Text("Copy") }
                }
                TextButton(onClick = { st.share(e) }) { Text("Share") }
                if (confirmDelete) TextButton(onClick = { st.delete(e) }) { Text("Really delete", color = MaterialTheme.colorScheme.error) }
                else TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
    }
}

// ---------------- chat ----------------

@Composable
fun ChatScreen(s: UiState, vm: MainViewModel, st: StudioViewModel) {
    val msgs by st.chat.collectAsState()
    val busy by st.chatBusy.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var thinking by rememberSaveable { mutableStateOf(true) }
    var canMake by rememberSaveable { mutableStateOf(true) }
    var image by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    val scroll = rememberScrollState()
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        val model = ModelPicker("chat", { it.isChat }, s, vm, st)
        val thinks = model?.thinks == true
        val makes = model?.makesThings == true
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = thinks && thinking,
                onClick = { thinking = !thinking },
                enabled = thinks,
                label = { Text(if (model?.alwaysThinks == true) "Thinking: ${if (thinking) "more" else "less"}" else "Thinking") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = canMake, onClick = { canMake = !canMake }, label = { Text("Can make things") })
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { st.clearChat() }, enabled = !busy && msgs.isNotEmpty()) { Text("New chat") }
        }
        val hint = when {
            model == null -> null
            canMake && !makes -> "This model isn't marked for tool use: it may not make things, or not reliably. Pick one marked \"Makes things\"."
            canMake -> "The model may create and edit images, speak, and make loops and songs. Models are swapped on the Tiiny when needed, which can take a few minutes."
            !thinks -> "This model doesn't think before answering."
            else -> null
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (msgs.isEmpty()) {
                Text(
                    "Chat with the loaded model. For web search, use RikkaHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            for ((i, m) in msgs.withIndex()) ChatBubble(m, live = busy && i == msgs.lastIndex, st = st)
        }
        if (model?.seesImages == true) {
            ImagePickerRow(image, 1, "Add a picture") { image = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
            )
            Spacer(Modifier.width(8.dp))
            if (busy) {
                OutlinedButton(onClick = { st.stopChat() }) { Text("Stop") }
            } else {
                Button(
                    onClick = {
                        st.send(model!!.id, input, image.firstOrNull()?.dataUri, thinks && thinking, canMake, s.models)
                        input = ""
                        image = emptyList()
                    },
                    enabled = model?.running == true && (input.isNotBlank() || image.isNotEmpty()),
                ) { Text("Send") }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg, live: Boolean, st: StudioViewModel) {
    val mine = m.role == "user"
    var showThinking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.image != null) Text("(picture attached)", style = MaterialTheme.typography.bodySmall)
                if (m.reasoning.isNotBlank()) {
                    TextButton(onClick = { showThinking = !showThinking }) {
                        Text(if (showThinking) "Hide thinking" else if (live && m.text.isEmpty()) "Thinking…" else "Show thinking")
                    }
                    if (showThinking) Text(m.reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.text.isNotEmpty()) SelectionContainer { Text(m.text) }
                else if (live && m.reasoning.isBlank() && m.status.isBlank()) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                for (r in m.results) ResultCard(r, st, expanded = true)
                if (live && m.status.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(m.status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

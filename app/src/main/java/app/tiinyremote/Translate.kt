package app.tiinyremote

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Languages for Translate (named in English for the model), the chat's list and a few more. */
private val LANGUAGES = (TRANSLATE_LANGUAGES + listOf("Arabic", "Greek", "Hindi", "Korean", "Russian", "Swedish", "Turkish", "Ukrainian"))
    .distinct().sorted()

/**
 * Translate: typed text, the text in a photo (a menu, a sign), or speech. Text needs a loaded chat model; a photo is
 * read by a chat model that sees images, or an OCR model; speech by the ASR model. Models that must load for it are
 * loaded and your models put back afterwards.
 */
@Composable
fun TranslateScreen(s: UiState, st: StudioViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val busy by st.helper.collectAsState()
    var mode by rememberSaveable { mutableStateOf(st.setting("translate.mode", "text")) }
    var from by rememberSaveable { mutableStateOf(st.setting("translate.from", "")) }
    val phone = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
    var to by rememberSaveable { mutableStateOf(st.setting("translate.to", if (phone in LANGUAGES) phone else "English")) }
    var input by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<PickedImage?>(null) }
    var audio by remember { mutableStateOf<PickedAudio?>(null) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var detected by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val done: (Pair<String, String>) -> Unit = { (src, text) -> detected = src; result = text }
    // Asked from outside (Quick Settings tile, widget, shortcut): the copied text goes in, ready to translate.
    val clip by Nav.translateClip.collectAsState()
    LaunchedEffect(clip) {
        if (clip) {
            Nav.translateClip.value = false
            val t = clipboard.getText()?.text.orEmpty()
            mode = "text"
            if (t.isNotBlank()) { input = t; result = null; error = null } else error = "Nothing copied yet: copy a text, then try again."
        }
    }

    // The camera app saves a full-size photo here; it's read, then deleted.
    val shot = remember { File(ctx.cacheDir, "camera").apply { mkdirs() }.let { File(it, "photo.jpg") } }
    val shotUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", shot) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            try {
                photo = withContext(Dispatchers.IO) { Inputs.image(ctx, shotUri, 2048) }
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't read the photo."
            } finally {
                shot.delete()
            }
        }
    }

    val chatReady = s.models.any { it.isChat && it.running }
    val visionReady = s.models.any { it.isChat && it.seesImages && it.running }
    val hasOcr = s.models.any { it.isOcr }
    val hasAsr = s.models.any { it.isAsr }
    val needs = when {
        !chatReady -> "Load a chat model first (Models): it does the translating."
        mode == "photo" && !visionReady && !hasOcr -> "For photos, load a chat model that sees images, or install an OCR model."
        mode == "voice" && !hasAsr -> "For speech, install a speech-to-text (ASR) model, e.g. Qwen3-ASR."
        else -> null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chips(
            listOf("text" to choiceLabel("Aa", "Text"), "photo" to choiceLabel("📷", "Photo"), "voice" to choiceLabel("👂", "Speech")),
            mode,
        ) { mode = it; st.setSetting("translate.mode", it); result = null }
        // From → To, with a swap.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SettingDropdown("From", listOf("" to "Detect") + LANGUAGES.map { it to it }, from) { from = it; st.setSetting("translate.from", it) }
            }
            TextButton(
                onClick = {
                    val f = from
                    from = to; to = f
                    st.setSetting("translate.from", from); st.setSetting("translate.to", to)
                    result?.let { r -> input = r; result = null }
                },
                enabled = from.isNotBlank() && mode == "text",
            ) { Text("⇄") }
            Box(Modifier.weight(1f)) {
                SettingDropdown("To", LANGUAGES.map { it to it }, to) { to = it; st.setSetting("translate.to", it) }
            }
        }
        when (mode) {
            "photo" -> {
                Text("A menu, a sign, a letter: take a photo or choose one.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { runCatching { camera.launch(shotUri) }.onFailure { ApiLog.add("Opening another app failed: ${it.javaClass.name}: ${it.message}\n" + it.stackTrace.take(6).joinToString("\n") { f -> "  at $f" }, "App"); error = "No camera app found." } }) {
                        Text(choiceLabel("📷", "Take photo"))
                    }
                    Box(Modifier.padding(start = 8.dp)) {
                        ImagePickerRow(emptyList(), 1, "Choose photo", st) { photo = it.firstOrNull() }
                    }
                }
                photo?.let { p ->
                    Image(p.preview.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
                }
            }
            "voice" -> {
                Text("Record someone speaking, or pick an audio file.", style = MaterialTheme.typography.bodySmall)
                AudioInput(audio) { audio = it }
            }
            else -> OutlinedTextField(
                input, { input = it }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                label = { Text("Text to translate") },
            )
        }
        needs?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        val ready = needs == null && busy == null && when (mode) {
            "photo" -> photo != null
            "voice" -> audio != null
            else -> input.isNotBlank()
        }
        Button(
            onClick = {
                result = null
                error = null
                when (mode) {
                    "photo" -> photo?.let { st.translatePhoto(it.dataUri, to, s.models, done) }
                    "voice" -> audio?.let { st.translateSpeech(it, to, s.models, done) }
                    else -> st.translateFull(input, from, to, s.models, done)
                }
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { SignText(choiceLabel(SIGN_TRANSLATE, "Translate into $to")) }
        busy?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        (if (detected.isNotBlank()) "$detected → " else "") + to,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SelectionContainer { MarkdownText(r) }
                    Row {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(r)) }) { BtnLabel("Copy", symbol = "⧉") }
                        TextButton(onClick = {
                            runCatching {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, r)
                                ctx.startActivity(android.content.Intent.createChooser(send, "Share"))
                            }
                        }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
                    }
                }
            }
        }
    }
}

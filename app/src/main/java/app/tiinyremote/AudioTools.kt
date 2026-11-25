@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.tiinyremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.random.Random

/*
 * The audio workspace, laid out like Tiiny Studio's (static/audio.js): Voice, Loop, Song and Transcribe.
 */

// ---------------- vocabularies (from Tiiny Studio) ----------------

private val VOICE_LANGS = listOf(
    "" to "Auto", "en" to "English", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
    "german" to "German", "french" to "French", "spanish" to "Spanish", "italian" to "Italian",
    "pt" to "Portuguese", "russian" to "Russian", "beijing_dialect" to "Beijing dialect", "sichuan_dialect" to "Sichuan dialect",
)

private val ASR_LANGS = listOf(
    "" to "Auto-detect", "en" to "English", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
    "de" to "German", "fr" to "French", "es" to "Spanish", "it" to "Italian", "pt" to "Portuguese",
    "ru" to "Russian", "ar" to "Arabic", "hi" to "Hindi", "vi" to "Vietnamese", "th" to "Thai", "id" to "Indonesian",
)

private val STYLE_CHIPS = listOf("Calm and warm", "Excited and upbeat", "Whispering", "Like a news anchor", "Slow and clear", "Sad, quiet")
private val DESIGN_CHIPS = listOf(
    "A calm, warm narrator", "A bright, fast young woman", "A deep, slow old man", "A cheerful child", "A gravelly movie-trailer voice",
)

private val FAMILIES = linkedMapOf(
    "Synth" to listOf("Wavetable Synth", "Supersaw", "FM Synth", "Analog Pad", "Pluck Synth", "Lead Synth"),
    "Keys" to listOf("Grand Piano", "Electric Piano", "Rhodes", "Church Organ", "Harpsichord"),
    "Bass" to listOf("Sub Bass", "Reese Bass", "Synth Bass", "Electric Bass"),
    "Strings" to listOf("Violin", "Cello", "String Ensemble"),
    "Mallets" to listOf("Vibraphone", "Marimba", "Glockenspiel", "Kalimba"),
    "Winds" to listOf("Flute", "Ocarina", "Clarinet", "Pan Flute"),
    "Guitar" to listOf("Acoustic Guitar", "Electric Guitar", "Clean Guitar"),
    "Brass" to listOf("Trumpet", "French Horn", "Brass Section"),
    "Vocals" to listOf("Choir", "Vocal Chops"),
    "Plucked" to listOf("Harp", "Koto", "Pizzicato"),
)
private val CHARACTER = listOf("warm", "bright", "dark", "airy", "gritty", "wide", "punchy", "soft", "glassy", "metallic")
private val PHRASE = listOf("chord progression", "melody", "arp", "bassline", "rising", "complex", "simple")
private val FX = listOf("reverb", "delay", "chorus", "distortion", "sidechain", "lo-fi")
private val BPMS = listOf(100, 110, 120, 128, 130, 140, 150)

private val SONG_CHIPS = listOf(
    "pop", "rock", "lofi", "edm", "jazz", "folk", "cinematic", "hip hop", "male vocals", "female vocals",
    "upbeat", "melancholic", "chill", "energetic", "piano", "acoustic guitar", "synth",
)
private val ACE_LANGS = listOf(
    "" to "Auto", "en" to "English", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "es" to "Spanish",
    "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian",
)
private val TIME_SIGS = listOf("" to "Auto", "2" to "2/4", "3" to "3/4", "4" to "4/4", "6" to "6/8")

// ---------------- small pieces ----------------

@Composable
private fun TagChips(options: List<String>, isOn: (String) -> Boolean, label: (String) -> String = { it }, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (o in options) FilterChip(selected = isOn(o), onClick = { onClick(o) }, label = { Text(label(o)) })
    }
}

@Composable
private fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text("$label: ${options.firstOrNull { it.first == selected }?.second ?: selected.ifBlank { "Auto" }}  ▾")
        }
        ScaledDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((v, l) in options) DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false })
        }
    }
}

internal fun hasTag(text: String, tag: String) = Regex("\\b${Regex.escape(tag)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

/** Adds ", tag" to a comma list, or takes it out (with its comma) when it's there. */
internal fun toggleTag(text: String, tag: String): String =
    if (hasTag(text, tag)) {
        text.replace(Regex("\\s*,?\\s*\\b${Regex.escape(tag)}\\b", RegexOption.IGNORE_CASE), "")
            .trim().trimStart(',').trim()
    } else text.trimEnd().trimEnd(',', '.').let { if (it.isBlank()) tag else "$it, $tag" }

@Composable
internal fun Hint(text: String, warn: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SeedRow(seed: String, onSeed: (String) -> Unit) {
    LabelRow("Seed", "blank = new each run")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = seed,
            onValueChange = { v -> onSeed(v.filter { it.isDigit() }.take(10)) },
            placeholder = { Text("random") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onSeed(Random.nextLong(0, 1L shl 31).toString()) }) { Text("Roll") }
    }
}

@Composable
private fun Advanced(summary: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle) { Text((if (open) "▾ " else "▸ ") + "Advanced") }
                Spacer(Modifier.weight(1f))
                Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) content()
        }
    }
}

private fun fmtLen(sec: Int): String = when {
    sec < 60 -> "$sec s"
    sec % 60 == 0 -> "${sec / 60}m"
    else -> "${sec / 60}m ${sec % 60}s"
}

private fun about(sec: Double) = "~" + Tools.fmtDuration(Math.round(sec).coerceAtLeast(1L))

private fun langName(code: String): String =
    VOICE_LANGS.firstOrNull { it.first.equals(code, true) }?.second
        ?: code.replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun speakerName(s: String) = s.replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

// ---------------- Voice ----------------

@Composable
internal fun SpeechTool(model: ModelInfo?, notReady: String?, st: StudioViewModel, models: List<ModelInfo>) {
    var caps by remember { mutableStateOf<Tools.VoiceCaps?>(null) }
    LaunchedEffect(model?.id, model?.running) {
        caps = if (model?.running == true) withContext(Dispatchers.IO) { runCatching { Tools.voiceCaps(st.client(), model.id) }.getOrNull() } else null
    }
    val c = caps
    val voices by st.voices.collectAsState()
    val helper by st.helper.collectAsState()
    val canClone = c == null || c.clone
    val speakers = c?.speakers ?: emptyList()
    val canDesign = c?.design == true
    val choices = buildList {
        // The model's own voice (no voice sent), for models without built-in speakers: VoxCPM2 speaks this way.
        if (speakers.isEmpty()) add("own")
        speakers.forEach { add("spk:$it") }
        if (canClone) voices.forEach { add("saved:${it.id}") }
        if (canDesign) add("design")
        if (canClone) add("new")
    }
    var sel by rememberSaveable { mutableStateOf(st.setting("voice.sel", "")) }
    val cur = sel.takeIf { it in choices } ?: choices.firstOrNull { it != "new" } ?: "new"
    fun choose(v: String) {
        sel = v
        st.setSetting("voice.sel", v)
    }
    val saved = voices.firstOrNull { "saved:${it.id}" == cur }

    var text by rememberSaveable { mutableStateOf("") }
    var design by rememberSaveable { mutableStateOf("") }
    var instruct by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var clip by remember { mutableStateOf<PickedAudio?>(null) }
    var clipText by rememberSaveable { mutableStateOf("") }
    var clipName by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember(cur) { mutableStateOf(false) }

    LabelRow(
        "Voice",
        when {
            speakers.isNotEmpty() && canClone -> "built-in, or cloned from a 3–20 s clip"
            speakers.isNotEmpty() -> "built into this model"
            else -> "its own, or cloned from a 3–20 s clip"
        },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in speakers) FilterChip(selected = cur == "spk:$s", onClick = { choose("spk:$s") }, label = { Text(speakerName(s)) })
        if (canClone) for (v in voices) FilterChip(selected = cur == "saved:${v.id}", onClick = { choose("saved:${v.id}") }, label = { Text("🎙 ${v.name}") })
        if ("own" in choices) FilterChip(selected = cur == "own", onClick = { choose("own") }, label = { Text("The model's own voice") })
        if (canDesign) FilterChip(selected = cur == "design", onClick = { choose("design") }, label = { Text("Describe a voice") })
        if (canClone) FilterChip(selected = cur == "new", onClick = { choose("new") }, label = { Text("+ New voice") })
    }

    when {
        saved != null -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(saved.name, style = MaterialTheme.typography.titleSmall)
                Hint("“${saved.text.take(160)}”")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val f = st.voiceFile(saved.id)
                    val playing = Player.playing == f.name
                    OutlinedButton(onClick = { Player.toggle(f) }) { BtnLabel(if (playing) "Stop" else "Play clip", symbol = if (playing) "■" else "▶") }
                    Spacer(Modifier.width(8.dp))
                    if (confirmDelete) DangerButton("Really delete", onClick = { st.deleteVoice(saved.id); choose("") })
                    else DangerButton("Delete", onClick = { confirmDelete = true })
                }
            }
        }
        cur == "new" -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("New voice from a clip", style = MaterialTheme.typography.titleSmall)
                Hint("A clear 3–20 second recording of one person speaking, without music.")
                AudioInput(clip, arrayOf("audio/*", "video/*"), "Upload…", st) { clip = it }
                clip?.seconds?.let { s ->
                    if (s < 3 || s > 20) Hint("Works best between 3 and 20 seconds; this one is %.0f s.".format(s), warn = true)
                }
                Field(clipText, { clipText = it }, "Transcript", lines = 3, placeholder = "Word for word what the clip says (the clone gets much better)")
                if (models.any { it.isAsr }) {
                    OutlinedButton(
                        enabled = clip != null && helper == null,
                        onClick = { clip?.let { a -> st.autoTranscribe(a, models) { clipText = it } } },
                    ) { Text(if (helper?.startsWith("Transcrib") == true) helper!! else "Auto-transcribe") }
                }
                Field(clipName, { clipName = it }, "Voice name", placeholder = "e.g. Me, Grandpa, Narrator")
                Button(
                    enabled = clip != null && clipText.isNotBlank(),
                    onClick = {
                        val v = st.addVoice(clipName, clip!!.bytes, clipText)
                        clip = null
                        clipText = ""
                        clipName = ""
                        choose("saved:${v.id}")
                    },
                ) { Text("Save voice") }
                if (clip != null && clipText.isBlank()) Hint("Type (or auto-transcribe) what's said, then save.")
            }
        }
        cur == "design" -> {
            Field(design, { design = it }, "Describe the voice", lines = 2, placeholder = "e.g. a cheerful young radio host, bright and quick")
            TagChips(DESIGN_CHIPS, { design == it }) { design = it }
        }
    }

    LabelRow("What to say", "${text.length} characters")
    Field(text, { text = it }, "Text", lines = 5, placeholder = "e.g. Good morning! Today will be sunny, with a light breeze on the lake.")
    if (text.length > 320) Hint("Long text is read in ${(text.length + 299) / 300} parts and joined.")

    if (saved != null && c?.instruct == true) {
        LabelRow("Delivery", "optional")
        Field(instruct, { instruct = it }, "How to say it", placeholder = "e.g. whispering, as if telling a secret")
        TagChips(STYLE_CHIPS, { instruct == it }) { instruct = if (instruct == it) "" else it }
    }

    val langs = c?.languages?.filter { it.lowercase() !in setOf("auto", "na") }.orEmpty()
    val langOptions = if (langs.isNotEmpty()) listOf("" to "Auto") + langs.map { it to langName(it) } else VOICE_LANGS
    Dropdown("Language", langOptions, language) { language = it }

    val why = notReady ?: when {
        cur == "new" -> "Save the new voice first, or pick one."
        text.isBlank() -> "Type something to say."
        cur == "design" && design.isBlank() -> "Describe the voice."
        else -> null
    }
    RunPanel("speech", "Speak", why == null, why, st) {
        val id = model!!.id
        val sp = cur.removePrefix("spk:").takeIf { cur.startsWith("spk:") }
        val ds = design.trim().takeIf { cur == "design" }
        val ref = saved?.let { st.voiceFile(it.id).readBytes() }
        val rt = saved?.text
        val t = text
        val lang = language
        val ins = if (saved != null) instruct else ""
        st.saveVoice(ref, rt, sp) // the chat's "speak" uses the voice last used here
        st.run("speech") { cl, store, p, _ -> Tools.speech(cl, store, id, t, sp, ds, ref, rt, lang, ins, p) }
    }
}

// ---------------- Transcribe ----------------

@Composable
internal fun TranscribeTool(model: ModelInfo?, notReady: String?, st: StudioViewModel) {
    var audio by remember { mutableStateOf<PickedAudio?>(null) }
    var language by rememberSaveable { mutableStateOf(st.setting("asr.lang", "")) }
    LabelRow("Audio", "speech, singing, songs")
    AudioInput(audio, arrayOf("audio/*", "video/*"), "Upload…", st) { audio = it }
    Hint("WAV · MP3 · M4A · WebM · video too")
    Dropdown("Language", ASR_LANGS, language) { language = it; st.setSetting("asr.lang", it) }
    val why = notReady ?: if (audio == null) "Add audio to transcribe." else null
    val est = 2 + (audio?.seconds ?: 30.0) / 10
    RunPanel("transcribe", "Transcribe  ·  ${about(est)}", why == null, why, st) {
        val a = audio!!
        val id = model!!.id
        val lang = language
        st.run("transcribe") { c, store, p, _ ->
            // An empty transcript says nothing: say why it may be empty instead.
            Tools.transcribe(c, store, id, a.bytes, a.mime, a.name, lang, p).also {
                if (it.text.isBlank()) throw MediaException(
                    "No speech was recognised in ${a.name}. Is there speech in it (not only music), and is the language right?",
                )
            }
        }
    }
}

// ---------------- Loop ----------------

private fun tagSet(csv: String): Set<String> = csv.split('|').filter { it.isNotBlank() }.toSet()
private fun Set<String>.csv() = joinToString("|")

@Composable
internal fun LoopTool(model: ModelInfo?, notReady: String?, st: StudioViewModel, models: List<ModelInfo>) {
    val helper by st.helper.collectAsState()
    var desc by rememberSaveable { mutableStateOf("") }
    var family by rememberSaveable { mutableStateOf("Synth") }
    var role by rememberSaveable { mutableStateOf("Wavetable Synth") }
    var character by rememberSaveable { mutableStateOf("warm") }
    var phrase by rememberSaveable { mutableStateOf("chord progression") }
    var fx by rememberSaveable { mutableStateOf("") }
    var bpm by rememberSaveable { mutableStateOf(120) }
    var bars by rememberSaveable { mutableStateOf(4) }
    var key by rememberSaveable { mutableStateOf("A minor") }
    var custom by rememberSaveable { mutableStateOf<String?>(null) }
    var steps by rememberSaveable { mutableStateOf(64f) }
    var cfg by rememberSaveable { mutableStateOf(6f) }
    var seed by rememberSaveable { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }

    val chars = tagSet(character)
    val phr = tagSet(phrase)
    val eff = tagSet(fx)
    val built = (listOf(role) + CHARACTER.filter { it in chars } + FX.filter { it in eff } + PHRASE.filter { it in phr } +
        listOf("$bars bars", "$bpm BPM", key)).joinToString(", ")
    val prompt = custom ?: built
    val secs = bars * 4 * 60.0 / bpm

    fun toggle(csv: String, v: String): String {
        val s = tagSet(csv)
        return (if (v in s) s - v else s + v).csv()
    }

    // Describe it: a chat model maps your words to the tag vocabulary.
    LabelRow("Describe it", "optional")
    Field(desc, { desc = it }, "In your own words", lines = 2, placeholder = "e.g. warm lo-fi piano with soft vinyl crackle, for studying")
    if (models.any { it.isChat }) {
        OutlinedButton(
            enabled = desc.isNotBlank() && helper == null,
            onClick = {
                val vocab = "Vocabulary\nInstruments: ${FAMILIES.values.flatten().joinToString(", ")}\nCharacter: ${CHARACTER.joinToString(", ")}\n" +
                    "Phrase: ${PHRASE.joinToString(", ")}\nEffects: ${FX.joinToString(", ")}"
                st.suggestTags(desc, vocab, models) { text ->
                    val bad = Regex("\\b(bpm|bars?|major|minor|drums?)\\b", RegexOption.IGNORE_CASE)
                    val tags = text.split(",").map { it.trim() }.filter { it.isNotEmpty() && !bad.containsMatchIn(it) }
                    fun find(list: List<String>, t: String) = list.firstOrNull { it.equals(t, true) }
                    val fam = FAMILIES.keys.firstOrNull { f -> tags.any { find(FAMILIES.getValue(f), it) != null } }
                    val c2 = HashSet<String>()
                    val p2 = HashSet<String>()
                    val f2 = HashSet<String>()
                    val extra = ArrayList<String>()
                    var r2 = role
                    if (fam != null) {
                        family = fam
                        r2 = tags.firstNotNullOfOrNull { find(FAMILIES.getValue(fam), it) } ?: FAMILIES.getValue(fam).first()
                        role = r2
                    }
                    for (t in tags) {
                        if (fam != null && find(FAMILIES.getValue(fam), t) != null) continue
                        val ch = find(CHARACTER, t)
                        val ph = find(PHRASE, t)
                        val ef = find(FX, t)
                        when {
                            ch != null -> c2.add(ch)
                            ph != null -> p2.add(ph)
                            ef != null -> f2.add(ef)
                            else -> extra.add(t)
                        }
                    }
                    character = c2.csv()
                    phrase = p2.csv()
                    fx = f2.csv()
                    // Tags outside the chip vocabulary still go to the model, through the editable prompt.
                    custom = if (extra.isNotEmpty() || fam == null) {
                        (listOfNotNull(if (fam != null) r2 else null) + tags.filter { !(fam != null && find(FAMILIES.getValue(fam), it) != null) } +
                            listOf("$bars bars", "$bpm BPM", key)).joinToString(", ")
                    } else null
                }
            },
        ) { Text(if (helper?.startsWith("Suggest") == true) helper!! else "Suggest tags") }
    }

    LabelRow("Instrument")
    TagChips(FAMILIES.keys.toList(), { it == family }) { family = it; role = FAMILIES.getValue(it).first(); custom = null }
    TagChips(FAMILIES.getValue(family), { it == role }) { role = it; custom = null }
    LabelRow("Character")
    TagChips(CHARACTER, { it in chars }) { character = toggle(character, it); custom = null }
    LabelRow("Phrase")
    TagChips(PHRASE, { it in phr }) { phrase = toggle(phrase, it); custom = null }
    LabelRow("Effects")
    TagChips(FX, { it in eff }) { fx = toggle(fx, it); custom = null }
    LabelRow("Tempo", "BPM")
    TagChips(BPMS.map { "$it" }, { it == "$bpm" }) { bpm = it.toInt(); custom = null }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            LabelRow("Bars")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (b in listOf(4, 8)) FilterChip(selected = bars == b, onClick = { bars = b; custom = null }, label = { Text("$b") })
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            LabelRow("Key")
            Dropdown("Key", KEYS.map { it to it }, key) { key = it; custom = null }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Prompt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text("%.1f s loop".format(secs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (custom != null) TextButton(onClick = { custom = null }) { Text("reset") }
    }
    OutlinedTextField(value = prompt, onValueChange = { custom = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
    Hint("Foundation-1 works best with tag-style prompts at these exact tempos. No drums in this release.")

    Advanced("${steps.toInt()} steps · guidance ${"%.1f".format(cfg)}" + (if (seed.isNotBlank()) " · seed $seed" else ""), advanced, { advanced = !advanced }) {
        LabelRow("Steps", "${steps.toInt()} · more is cleaner, slower")
        Slider(value = steps, onValueChange = { steps = Math.round(it).toFloat() }, valueRange = 8f..200f)
        LabelRow("Guidance", "%.1f · how closely it follows the tags".format(cfg))
        Slider(value = cfg, onValueChange = { cfg = Math.round(it * 2) / 2f }, valueRange = 1f..12f, steps = 21)
        SeedRow(seed) { seed = it }
    }

    val est = secs * 1.8 * (steps / 64.0)
    val why = notReady ?: if (prompt.isBlank()) "Describe the loop." else null
    RunPanel("loop", "Make loop  ·  ${about(est)}", why == null, why, st) {
        val id = model!!.id
        val pr = prompt.trim()
        val stp = steps.toInt()
        val cf = cfg.toDouble()
        val sd = seedOf(seed)
        st.run("loop") { c, store, p, _ -> Tools.loop(c, store, id, pr, secs, stp, cf, sd, p) }
    }
}

// ---------------- Song ----------------

@Composable
internal fun SongTool(model: ModelInfo?, notReady: String?, st: StudioViewModel, models: List<ModelInfo>) {
    val helper by st.helper.collectAsState()
    var info by remember { mutableStateOf<Tools.SongInfo?>(null) }
    LaunchedEffect(model?.id, model?.running) {
        info = if (model != null) withContext(Dispatchers.IO) { runCatching { Tools.songInfo(st.client(), model.id) }.getOrNull() } else null
    }
    val sm = info ?: Tools.SongInfo(false, model?.id?.startsWith("ACE-Step/") == true, model?.id?.startsWith("ACE-Step/") == true,
        model?.id?.contains("YuE", true) == true, null, null, null, null, null, 270, null)

    var caption by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("mixed") }
    var lyrics by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(LYRICS_TEMPLATE)) }
    var seconds by rememberSaveable { mutableStateOf(60) }
    var styleRef by remember { mutableStateOf<PickedAudio?>(null) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var aceOpen by remember { mutableStateOf(false) }
    var bpm by rememberSaveable { mutableStateOf("") }
    var keyScale by rememberSaveable { mutableStateOf("") }
    var timeSig by rememberSaveable { mutableStateOf("") }
    var vocalLang by rememberSaveable { mutableStateOf("") }
    var avoid by rememberSaveable { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var seed by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var temperature by rememberSaveable { mutableStateOf("") }
    var topK by rememberSaveable { mutableStateOf("") }
    var topP by rememberSaveable { mutableStateOf("") }

    val t = if (sm.yue) "mixed" else type
    val secs = seconds.coerceIn(15, sm.maxSeconds)
    val vocals = t != "bgm"
    val usePreview = preview && !sm.plain

    // Mode
    LabelRow("Mode")
    val modes = if (sm.yue) listOf(Triple("mixed", "With vocals", "YuE2 always sings"))
    else listOf(
        Triple("mixed", "With vocals", "Singing over music"),
        Triple("bgm", "Instrumental", "No vocals"),
        Triple("vocal", "Vocals only", "A cappella vocal track"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((v, l, _) in modes) FilterChip(selected = t == v, onClick = { type = v }, label = { Text(l) })
    }
    Hint(modes.firstOrNull { it.first == t }?.third ?: "")

    // Style
    LabelRow("Style")
    Field(caption, { caption = it }, "Genre, mood, instruments, voice", lines = 2, placeholder = "e.g. alpine folk-rock, male choir, accordion and stomping drums")
    // A chip adds its word; tapped again (✓) it takes it out.
    TagChips(SONG_CHIPS, { hasTag(caption, it) }, { if (hasTag(caption, it)) "✓ $it" else "+ $it" }) { chip ->
        caption = toggleTag(caption, chip)
    }

    // Lyrics
    if (vocals) {
        LabelRow("Lyrics", "use section tags")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (tag in listOf("intro", "verse", "chorus", "bridge", "outro")) {
                TextButton(onClick = {
                    val tx = lyrics.text
                    val at = lyrics.selection.start.coerceIn(0, tx.length)
                    val before = tx.substring(0, at)
                    val ins = (if (before.isEmpty() || before.endsWith("\n\n")) "" else if (before.endsWith("\n")) "\n" else "\n\n") + "[$tag]\n"
                    lyrics = TextFieldValue(before + ins + tx.substring(at), TextRange(at + ins.length))
                }) { Text("[$tag]") }
            }
            TextButton(onClick = { lyrics = TextFieldValue(LYRICS_TEMPLATE) }) { Text("Example") }
        }
        OutlinedTextField(
            value = lyrics,
            onValueChange = { lyrics = it },
            placeholder = { Text("[verse]\nThe first lines…\n\n[chorus]\nThe part everyone sings…") },
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        if (models.any { it.isChat }) {
            OutlinedButton(
                enabled = caption.isNotBlank() && helper == null,
                onClick = {
                    val shape = when {
                        secs <= 45 -> "one verse and one chorus"
                        secs <= 120 -> "verse, chorus, verse, chorus"
                        else -> "verse, chorus, verse, chorus, bridge, chorus"
                    }
                    val cur = lyrics.text.trim()
                    val draft = if (cur.isNotEmpty() && cur != LYRICS_TEMPLATE.trim()) "\nBuild on these ideas or draft lines:\n$cur" else ""
                    st.writeLyrics("Song style: ${caption.trim()}\nLength: about $secs seconds, so $shape.$draft", models) {
                        lyrics = TextFieldValue(it)
                    }
                },
            ) { Text(if (helper?.startsWith("Writing") == true) helper!! else "Write lyrics") }
            if (caption.isBlank()) Hint("Describe the style first: the lyrics follow it.")
        }
    }

    // Preview first (SongGeneration only)
    if (!sm.plain) {
        SwitchRow(preview, title = "Preview first (8 s)", detail = "Hear the opening in about a minute, then continue to the full song only if you like it") {
            preview = it
        }
    }

    // AceStep's own settings
    if (sm.ace) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { aceOpen = !aceOpen }) { Text((if (aceOpen) "▾ " else "▸ ") + "Song settings") }
                    Spacer(Modifier.weight(1f))
                    Text("optional · AceStep", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (aceOpen) {
                    Field(bpm, { bpm = it.filter(Char::isDigit).take(3) }, "Tempo (BPM)", placeholder = "auto")
                    Dropdown("Key", listOf("" to "Auto") + KEYS.map { it to it }, keyScale) { keyScale = it }
                    Dropdown("Time signature", TIME_SIGS, timeSig) { timeSig = it }
                    if (vocals) Dropdown("Sung in", ACE_LANGS, vocalLang) { vocalLang = it }
                    Field(avoid, { avoid = it }, "Avoid (optional)", placeholder = "e.g. vocals, heavy bass")
                }
            }
        }
    }

    // Style reference: a clip of music whose feel the song borrows (models that take one)
    if (sm.styleRef) {
        LabelRow("Style reference", "optional · a WAV, ${if (sm.ace) "5" else "10"}–30 s of music whose feel the song borrows")
        AudioInput(styleRef, arrayOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"), "Upload a WAV…", st, wavOnly = true) { styleRef = it }
    }

    // Length
    if (!usePreview) {
        LabelRow("Length", fmtLen(secs) + " · up to ${fmtLen(sm.maxSeconds)}")
        Slider(
            value = secs.toFloat(),
            onValueChange = { seconds = (Math.round(it / 5f) * 5).coerceIn(15, sm.maxSeconds) },
            valueRange = 15f..sm.maxSeconds.toFloat(),
        )
        if (!sm.plain && secs >= 240) Hint("4 minutes and more: the model composes its full-length arrangement.")
    }

    // Advanced
    fun num(v: Double?) = v?.let { if (it == Math.floor(it)) it.toLong().toString() else "%.2f".format(it) } ?: ""
    Advanced(if (listOf(seed, steps, temperature, topK, topP).all { it.isBlank() }) "model defaults" else "custom", advanced, { advanced = !advanced }) {
        SeedRow(seed) { seed = it }
        Field(steps, { steps = it.filter(Char::isDigit).take(4) }, "Decoder steps" + (sm.maxSteps?.let { " (up to $it)" } ?: ""), placeholder = num(sm.steps).ifBlank { "10" })
        Field(temperature, { temperature = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "Temperature", placeholder = num(sm.temperature).ifBlank { "1.0" })
        Field(topK, { topK = it.filter(Char::isDigit).take(5) }, "Top-k", placeholder = num(sm.topK).ifBlank { "250" })
        Field(topP, { topP = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "Top-p", placeholder = num(sm.topP).ifBlank { "0" })
        Hint("Blank fields use the model's defaults.")
    }

    val est = when {
        sm.plain -> 15 + secs * (sm.secsPerSec ?: 1.5)
        usePreview -> 50.0
        secs >= 240 -> 900.0
        else -> 10 + secs * 3.2
    }
    val bpmN = bpm.toIntOrNull()
    val refSecs = styleRef?.seconds
    val why = notReady ?: when {
        caption.isBlank() -> "Describe the style."
        // The Tiiny takes a WAV clip only: 10 s at least for SongGeneration, 5 s for AceStep (Tiiny Studio's check).
        sm.styleRef && styleRef != null && !(styleRef!!.bytes.size > 12 && String(styleRef!!.bytes, 0, 4) == "RIFF") ->
            "The style reference must be a WAV file."
        sm.styleRef && styleRef != null && refSecs != null && refSecs < (if (sm.ace) 5.0 else if (sm.yue) 0.0 else 10.0) ->
            "The style reference must be at least ${if (sm.ace) 5 else 10} s long."
        sm.styleRef && styleRef != null && refSecs != null && refSecs > 120.0 -> "The style reference can be at most 120 s long (30 s is plenty)."
        sm.styleRef && (styleRef?.bytes?.size ?: 0) > 25 * 1024 * 1024 -> "The style reference is too big: use a shorter clip (up to 25 MB)."
        vocals && lyrics.text.isBlank() -> "Add lyrics, or switch to Instrumental."
        sm.ace && bpm.isNotBlank() && (bpmN == null || bpmN !in 30..300) -> "Tempo is a number of beats per minute (30–300)."
        else -> null
    }
    RunPanel("song", (if (usePreview) "Compose preview" else "Compose") + "  ·  ${about(est)}", why == null, why, st) {
        val cfg = JSONObject().put("caption", caption.trim()).put("generation_type", t).put("seed", seedOf(seed))
        if (vocals) cfg.put("lyrics", lyrics.text.trim())
        if (sm.styleRef) styleRef?.let { cfg.put("reference_audio_base64", android.util.Base64.encodeToString(it.bytes, android.util.Base64.NO_WRAP)) }
        steps.toIntOrNull()?.let { cfg.put("steps", it) }
        temperature.toDoubleOrNull()?.let { cfg.put("temperature", it) }
        topK.toIntOrNull()?.let { cfg.put("top_k", it) }
        topP.toDoubleOrNull()?.let { cfg.put("top_p", it) }
        if (sm.ace) {
            val ace = JSONObject()
            bpmN?.let { ace.put("bpm", it) }
            if (keyScale.isNotBlank()) ace.put("key_scale", keyScale)
            if (timeSig.isNotBlank()) ace.put("time_signature", timeSig)
            if (vocals && vocalLang.isNotBlank()) ace.put("vocal_language", vocalLang)
            if (avoid.isNotBlank()) ace.put("negative_prompt", avoid.trim())
            if (ace.length() > 0) cfg.put("ace_step", ace)
        }
        st.song(model!!.id, cfg, secs, usePreview)
    }
}

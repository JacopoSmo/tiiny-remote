@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.tiinyremote

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import android.Manifest
import android.content.pm.PackageManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.SmallFloatingActionButton

// ---------------- chat ----------------

@Composable
fun ChatScreen(s: UiState, vm: MainViewModel, st: StudioViewModel) {
    val msgs by st.chat.collectAsState()
    val busy by st.chatBusy.collectAsState()
    val current by st.currentChat.collectAsState()
    // What's being typed is kept per chat (also when another app, WireGuard say, takes over the screen meanwhile).
    var input by remember(current) { mutableStateOf(st.draftOf(current)) }
    LaunchedEffect(input, current) { st.setDraft(current, input) }
    var depth by rememberSaveable { mutableStateOf(st.setting("chat.depth", "auto").takeIf { it in DEPTHS } ?: "auto") }
    var chatMode by rememberSaveable { mutableStateOf(st.setting("chat.mode", "normal")) }
    var suggest by rememberSaveable { mutableStateOf(st.setting("chat.suggest", "1") == "1") }
    fun setDepth(d: String) { depth = d; st.setSetting("chat.depth", d) }
    fun setMode(m: String) { chatMode = m; st.setSetting("chat.mode", m) }
    fun toggleSuggest() { suggest = !suggest; st.setSetting("chat.suggest", if (suggest) "1" else "0") }
    val suggestions by st.suggestions.collectAsState()
    val helperBusy by st.helper.collectAsState()
    var quick by remember { mutableStateOf<Pair<String, String>?>(null) } // a summary or translation: title to text
    var undoDraft by remember { mutableStateOf<String?>(null) } // the message before the last ✎ rewrite
    var canMake by rememberSaveable { mutableStateOf(true) }
    var web by rememberSaveable { mutableStateOf(st.setting("chat.web", "0") == "1") }
    val hasSearx = s.settings.searchMode != "searx" || s.settings.searxUrl.isNotBlank() // web search is available
    var image by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var files by remember { mutableStateOf<List<PickedFile>>(emptyList()) } // documents attached to the next message (up to 5)
    // A result sent here from Create or the Gallery: attached to the next message.
    val handed by st.toChat.collectAsState()
    LaunchedEffect(handed) {
        handed?.let { h ->
            h.image?.let { image = listOf(it) }
            h.file?.let { files = listOf(it) }
            // A question to finish (from Models → Store): in the box, with "Can make things" on so it can look up models.
            h.draft?.let { input = it; canMake = true }
            st.toChatTaken()
        }
    }
    var attachError by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    var lastCount by remember { mutableStateOf(0) }
    val ideasNow = st.suggestions.collectAsState().value[st.currentChat.collectAsState().value]
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length, ideasNow) {
        // A new message always shows; a streaming answer follows only while you're near the end, so reading higher up stays put.
        val near = scroll.maxValue - scroll.value < scroll.viewportSize / 2 + 200
        if (msgs.size != lastCount || near) scroll.animateScrollTo(scroll.maxValue)
        lastCount = msgs.size
    }
    val ui = LocalUi.current
    // Switching chats (or reopening one from History) jumps to the end; again while the messages are still being laid
    // out (a long chat takes a moment), so it really lands on the newest message.
    LaunchedEffect(current) {
        repeat(8) {
            scroll.scrollTo(scroll.maxValue)
            kotlinx.coroutines.delay(120)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = if (ui.compact) 8.dp else 12.dp)) {
        Spacer(Modifier.height(if (ui.compact) 4.dp else 8.dp))
        var options by remember { mutableStateOf(false) }
        // The fast helper (Settings): on, it's used and loaded if it isn't; off, it isn't used even when loaded.
        val helperM = s.models.firstOrNull { it.id == s.settings.helperModel && it.isChat }
        val helperShown = helperM != null && s.settings.helperOn
        fun toggleHelper() {
            val on = !s.settings.helperOn
            vm.setDisplay(s.settings.copy(helperOn = on))
            if (on && helperM != null && helperM.status != "running" && helperM.status != "loading" && helperM.id !in s.working) vm.load(helperM)
        }
        var sub by remember { mutableStateOf<String?>(null) } // "depth" or "mode": the menu shows that choice
        var model: ModelInfo? = null
        val modeLabel = CHAT_MODES.firstOrNull { it.first == chatMode }?.second ?: "Normal"
        if (ui.compact) {
            // One line: model picker and a menu with the chat options.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { model = ModelPicker("chat", { it.isChat }, s, vm, st) }
                val thinksNow = model?.thinks == true
                val shownDepth = if (model?.alwaysThinks == true && depth == "off") "low" else depth
                Box {
                    TextButton(onClick = { options = true }) {
                        SignText(listOfNotNull(
                            if (thinksNow && shownDepth != "off") sign("Thinking") else null,
                            if (canMake) sign("Can make things") else null,
                            if (web && hasSearx) sign("Web search") else null,
                            if (suggest) sign("Suggestions") else null,
                            if (helperShown) sign("Helper") else null,
                            if (chatMode != "normal") sign(modeLabel) else null,
                        ).joinToString("").ifEmpty { "⋯" } + " ▾")
                    }
                    ScaledDropdownMenu(expanded = options, onDismissRequest = { options = false; sub = null }) {
                        when (sub) {
                            "depth" -> {
                                DropdownMenuItem(text = { Text("◂ Back") }, onClick = { sub = null })
                                DepthSlider(depth, model?.alwaysThinks == true) { setDepth(it) }
                            }
                            "mode" -> {
                                DropdownMenuItem(text = { Text("◂ Back") }, onClick = { sub = null })
                                ModeChoices(chatMode) { setMode(it); sub = null }
                            }
                            else -> {
                                DropdownMenuItem(
                                    text = { Text("Thinking: " + (if (thinksNow) DEPTH_LABELS[shownDepth] else "—") + "  ▸") },
                                    enabled = thinksNow,
                                    onClick = { sub = "depth" },
                                )
                                DropdownMenuItem(text = { Text((if (canMake) "☑ " else "☐ ") + "Can make things") }, onClick = { canMake = !canMake })
                                DropdownMenuItem(
                                    text = { Text((if (web && hasSearx) "☑ " else "☐ ") + "Web search") },
                                    enabled = hasSearx,
                                    onClick = { web = !web; st.setSetting("chat.web", if (web) "1" else "0") },
                                )
                                DropdownMenuItem(text = { Text((if (suggest) "☑ " else "☐ ") + "Suggestions") }, onClick = { toggleSuggest() })
                                DropdownMenuItem(
                                    text = { SignText(if (helperM == null) "☐ Helper: choose one in Test" else (if (s.settings.helperOn) "☑ " else "☐ ") + "Helper " + sign("Helper")) },
                                    enabled = helperM != null,
                                    onClick = { toggleHelper() },
                                )
                                DropdownMenuItem(text = { Text("Mode: $modeLabel  ▸") }, onClick = { sub = "mode" })
                                DropdownMenuItem(
                                    text = { Text("Summarise this chat") },
                                    enabled = msgs.isNotEmpty() && !busy && helperBusy == null,
                                    onClick = { options = false; st.summariseChat(s.models) { quick = "Summary of this chat" to it } },
                                )
                                DropdownMenuItem(text = { Text("New chat") }, enabled = msgs.isNotEmpty(), onClick = { st.newChat(); options = false })
                            }
                        }
                    }
                }
            }
        } else {
            model = ModelPicker("chat", { it.isChat }, s, vm, st)
        }
        val thinks = model?.thinks == true
        val makes = model?.makesThings == true
        if (!ui.compact) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Box {
                val shownDepth = if (model?.alwaysThinks == true && depth == "off") "low" else depth
                FilterChip(
                    selected = thinks && shownDepth != "off",
                    onClick = { sub = "depth" },
                    enabled = thinks,
                    label = { Text("Thinking: ${if (thinks) DEPTH_LABELS[shownDepth] else "—"} ▾") },
                )
                ScaledDropdownMenu(expanded = sub == "depth", onDismissRequest = { sub = null }) {
                    DepthSlider(depth, model?.alwaysThinks == true) { setDepth(it) }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = canMake, onClick = { canMake = !canMake }, label = { Text("Can make things") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = web && hasSearx, onClick = { web = !web; st.setSetting("chat.web", if (web) "1" else "0") }, enabled = hasSearx, label = { SignText(tag("Web search")) })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = suggest, onClick = { toggleSuggest() }, label = { Text("Suggestions") })
            Spacer(Modifier.width(8.dp))
            if (helperM != null) {
                FilterChip(selected = s.settings.helperOn, onClick = { toggleHelper() }, label = { SignText(tag("Helper")) })
                Spacer(Modifier.width(8.dp))
            }
            Box {
                FilterChip(selected = chatMode != "normal", onClick = { sub = "mode" }, label = { Text("Mode: $modeLabel ▾") })
                ScaledDropdownMenu(expanded = sub == "mode", onDismissRequest = { sub = null }) {
                    ModeChoices(chatMode) { setMode(it); sub = null }
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { st.summariseChat(s.models) { quick = "Summary of this chat" to it } },
                enabled = msgs.isNotEmpty() && !busy && helperBusy == null,
            ) { Text("Summarise") }
            TextButton(onClick = { st.newChat() }, enabled = msgs.isNotEmpty()) { Text("New chat") }
        }
        val hint = when {
            model == null -> null
            !hasSearx && web -> "Web search is set to SearXNG only, but no address is set: Settings → Web search."
            (canMake || (web && hasSearx)) && !makes -> "This model isn't marked for tool use: it may not make things or search, or not reliably. Pick one marked \"Makes things\"."
            web && hasSearx && !canMake -> "The model may search the web and read pages."
            canMake -> (if (web && hasSearx) "The model may search the web, " else "The model may ") + "make and edit images, speak and make music, swapping models when needed (can take minutes)."
            !thinks -> "This model doesn't think before answering."
            else -> null
        }
        // The fast helper (Settings) at work beside the chat model, when it's loaded.
        val helperName = s.models.firstOrNull { it.id == s.settings.activeHelper && it.running && it.id != model?.id }?.name
        val helperNote = helperName?.let { "Helper $it names the chat, suggests follow-ups, condenses long chats, and condenses pages when it's clearly faster." }
        val modeNote = when (chatMode) {
            "socratic" -> "Socratic: the model guides you with questions instead of giving the answer."
            "student" -> "Student: the model teaches step by step and checks your understanding."
            else -> null
        }
        val shownHint = listOfNotNull(hint, modeNote, helperNote).joinToString(" ").ifBlank { null }
        // On compact screens the hint shows only before the chat starts, to leave room for the conversation.
        if (shownHint != null && (!ui.compact || msgs.isEmpty())) {
            Text(shownHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A tapped suggestion goes alone: what's being typed and a picked picture stay for the next message.
        fun sendText(text: String, typed: Boolean) {
            val m = model ?: return
            if (busy) return
            val pic = if (typed) image.firstOrNull()?.dataUri else null
            st.send(m.id, text, pic, if (thinks) depth else "off", chatMode, canMake, web && hasSearx, suggest, s.models, if (typed) Docs.combine(files) else null)
            if (typed) {
                input = ""
                image = emptyList()
                files = emptyList()
                undoDraft = null
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (msgs.isEmpty()) {
                SignText(
                    "Earlier chats: $SIGN_HISTORY top right. \"A3B\" models answer fast; 27B ones are slower but more careful.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            for ((i, m) in msgs.withIndex()) {
                val live = busy && i == msgs.lastIndex
                // The question an answer belongs to, for "Share question and answer".
                val asked = msgs.getOrNull(i - 1)?.takeIf { m.role == "assistant" && it.role == "user" }?.text
                ChatBubble(m, live = live, st = st, question = asked, onTranslate = if (live || helperBusy != null) null else { lang ->
                    st.translateText(m.text, lang, s.models) { quick = "Translation ($lang)" to it }
                })
            }
            // Opened from History: which model this chat last used, and a tap to use it again. At the bottom, by the
            // newest message, where a reopened chat is shown (nobody scrolls up a long chat to find it).
            val resumed by st.resumed.collectAsState()
            val lastId = if (resumed == current) st.lastModelOf(current) else null
            if (lastId != null) {
                val last = s.models.firstOrNull { it.id == lastId }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "This chat last used ${last?.name ?: lastId.substringAfterLast('/')}" + when {
                                last == null -> " (no longer on the Tiiny)."
                                last.running -> " (loaded)."
                                else -> " (not loaded)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (last != null && model?.id != last.id) TextButton(onClick = {
                            st.setPick("chat", last.id)
                            if (!last.running) vm.load(last)
                        }) { Text(if (last.running) "Use it" else "Load it") }
                        TextButton(onClick = { st.resumedSeen() }) { Text("✕") }
                    }
                }
            }
            val ideas = suggestions[current].orEmpty()
            if (suggest && !busy && ideas.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (idea in ideas) {
                        SuggestionChip(
                            onClick = { if (model?.running == true) sendText(idea, typed = false) },
                            label = { Text(idea, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            icon = { Text(sign("Suggestions")) },
                        )
                    }
                }
            }
        }
            // Scrolled up by more than about a screen: a button back down to the newest message.
            val farUp by remember { derivedStateOf { scroll.maxValue - scroll.value > scroll.viewportSize * 1.2f } }
            val jumpScope = rememberCoroutineScope()
            if (farUp) {
                SmallFloatingActionButton(
                    onClick = { jumpScope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) { Icon(androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to the newest message") }
            }
        }
        // In a project: its name and files, above the message; tap for the project, ✕ takes this chat out of it.
        val proj by st.currentProject.collectAsState()
        val projTick by st.projectTick.collectAsState()
        var projOpen by remember { mutableStateOf(false) }
        proj?.let { pr ->
            val n = remember(projTick, pr.id) { st.projects.docs(pr.id).size }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📁 ${pr.name} · $n file" + (if (n == 1) "" else "s"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { projOpen = true }.padding(vertical = 6.dp),
                )
                TextButton(onClick = { st.setChatProject(st.currentChat.value, null) }) { Text("✕") }
            }
            if (projOpen) ProjectDialog(st, pr, onOpenChat = { st.resumeChat(it); projOpen = false }) { projOpen = false }
        }
        // What's attached to the next message, only when something is: a small line, not a permanent row.
        if (image.isNotEmpty() || files.isNotEmpty() || attachError != null) {
            // One line per file: a long name is cut with … so its ✕ always stays on screen.
            for (f in files) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📄 ${f.name}" + if (f.cut) " (first ${Docs.MAX_CHARS / 1000}k characters)" else "",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { files = files - f }) { Text("✕") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                image.firstOrNull()?.let { p ->
                    androidx.compose.foundation.Image(
                        p.preview.asImageBitmap(), "Attached image",
                        Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop,
                    )
                    TextButton(onClick = { image = emptyList() }) { Text("✕") }
                    if (model?.seesImages == false) Text(
                        "This model can't see images: pick one with 👁 above.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    )
                }
                attachError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        val canSend = model?.running == true && (input.isNotBlank() || image.isNotEmpty() || files.isNotEmpty()) && !busy &&
            (image.isEmpty() || model?.seesImages == true)
        fun doSend() = sendText(input, typed = true)
        helperBusy?.let { what ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        quick?.let { (title, text) -> QuickResult(title, text) { quick = null } }
        var shiftEnter by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = if (ui.compact) 4.dp else 8.dp)) {
            AttachButton(
                st = st,
                seesImages = model?.seesImages == true,
                onImage = { image = listOf(it); attachError = null },
                onFile = { f ->
                    attachError = if (files.size >= Docs.MAX_FILES) "Up to ${Docs.MAX_FILES} files per message." else null
                    if (files.size < Docs.MAX_FILES && files.none { it.name == f.name }) files = files + f
                },
                onError = { attachError = it },
            )
            OutlinedTextField(
                value = input,
                onValueChange = { v ->
                    // Some keyboards (the phone's own keyboard app among them) take a hardware Enter themselves and type
                    // a line break, so the key never reaches the app: a single line break typed at the end then sends
                    // too, unless Shift was held (that's a new line).
                    if (ui.enterSends && !shiftEnter && v.length == input.length + 1 && v.endsWith("\n") && v.dropLast(1) == input) {
                        if (canSend) doSend()
                    } else input = v
                    shiftEnter = false
                },
                placeholder = { Text(if (ui.enterSends) "Message · Enter sends" else "Message") },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { k ->
                        // Hardware keyboard (e.g. the Titan 2's): Enter sends, Shift+Enter makes a new line.
                        if ((k.key == Key.Enter || k.key == Key.NumPadEnter) && k.isShiftPressed) shiftEnter = true
                        if (ui.enterSends && (k.key == Key.Enter || k.key == Key.NumPadEnter) && !k.isShiftPressed) {
                            if (k.type == KeyEventType.KeyDown && canSend) doSend()
                            true
                        } else false
                    },
                maxLines = if (ui.compact) 3 else 5,
            )
            // ✎: the helper (or the chat model) shortens or rewords what's typed; Undo puts the original back.
            var draftMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { draftMenu = true },
                    enabled = (input.isNotBlank() || undoDraft != null) && helperBusy == null && model?.running == true,
                ) { Icon(androidx.compose.material.icons.Icons.Filled.Edit, contentDescription = "Improve my message") }
                ScaledDropdownMenu(expanded = draftMenu, onDismissRequest = { draftMenu = false }) {
                    for ((how, _) in DRAFT_ACTIONS) {
                        DropdownMenuItem(text = { Text(how) }, enabled = input.isNotBlank(), onClick = {
                            draftMenu = false
                            val before = input
                            // Only if the message wasn't changed meanwhile: typing on mustn't be overwritten.
                            st.rewriteDraft(before, how, s.models) { out -> if (input == before) { undoDraft = before; input = out } }
                        })
                    }
                    undoDraft?.let { old ->
                        DropdownMenuItem(text = { Text("Undo: back to my text") }, onClick = { draftMenu = false; input = old; undoDraft = null })
                    }
                }
            }
            when {
                busy -> StopAsking(
                    "this answer", "What's written so far stays in the chat. Say \"go on\" later to continue it.",
                    onStop = { st.stopChat() },
                )
                // Nothing typed or attached yet: the microphone, to dictate the message instead.
                input.isBlank() && image.isEmpty() && files.isEmpty() ->
                    DictateButton(enabled = helperBusy == null, models = s.models, st = st) { said -> input = (input + " " + said).trim() }
                else -> Button(onClick = { doSend() }, enabled = canSend) { BtnLabel("Send", androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send) }
            }
        }
    }
}

@Composable
internal fun ChatBubble(m: ChatMsg, live: Boolean, st: StudioViewModel, question: String? = null, onTranslate: ((String) -> Unit)? = null) {
    val mine = m.role == "user"
    var showThinking by remember { mutableStateOf(false) }
    var langs by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.image != null) Text("🖼 image attached", style = MaterialTheme.typography.bodySmall)
                m.fileName?.let { Text("📄 $it", style = MaterialTheme.typography.bodySmall) }
                if (m.reasoning.isNotBlank()) {
                    TextButton(onClick = { showThinking = !showThinking }) {
                        Text(if (showThinking) "Hide thinking" else if (live && m.text.isEmpty()) "Thinking…" else "Show thinking")
                    }
                    if (showThinking) Text(m.reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.text.isNotEmpty()) SelectionContainer { if (mine) Text(m.text) else MarkdownText(m.text) }
                else if (live && m.reasoning.isBlank() && m.status.isBlank()) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                for (r in m.results) ResultCard(r, st, expanded = true)
                if (m.sources.isNotEmpty()) {
                    val uri = androidx.compose.ui.platform.LocalUriHandler.current
                    Text("Sources", style = MaterialTheme.typography.labelLarge)
                    for ((i, src) in m.sources.take(12).withIndex()) {
                        Text(
                            "${i + 1}. ${src.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            // Only web addresses open: a source can't launch another app through intent:// or similar.
                            modifier = Modifier.clickable {
                                if (src.url.startsWith("https://", true) || src.url.startsWith("http://", true)) runCatching { uri.openUri(src.url) }
                            },
                        )
                    }
                }
                // Bottom right: copy a question; share (or copy) and translate an answer. Words, signs or both, as set in Settings.
                if (mine && m.text.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(m.text.trim())) }) { BtnLabel("Copy", symbol = "⧉") }
                }
                if (!mine && !live && m.text.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    // Copy or share this answer, alone or with the question it answers.
                    val pair = if (question != null) "Question:\n${question.trim()}\n\nAnswer:\n${m.text.trim()}" else m.text.trim()
                    fun share(text: String) = runCatching {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, text)
                        ctx.startActivity(android.content.Intent.createChooser(send, "Share"))
                    }
                    if (onTranslate != null && !m.text.startsWith("Error:")) Box {
                        TextButton(onClick = { langs = true }) { BtnLabel("Translate", TranslateIcon) }
                        ScaledDropdownMenu(expanded = langs, onDismissRequest = { langs = false }) {
                            for (lang in TRANSLATE_LANGUAGES) {
                                DropdownMenuItem(text = { Text(lang) }, onClick = { langs = false; onTranslate(lang) })
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { shareMenu = true }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
                        ScaledDropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            if (question != null) {
                                DropdownMenuItem(text = { Text("Share question and answer") }, onClick = { shareMenu = false; share(pair) })
                                DropdownMenuItem(text = { Text("Copy question and answer") }, onClick = { shareMenu = false; clipboard.setText(AnnotatedString(pair)) })
                            }
                            DropdownMenuItem(text = { Text("Share the answer") }, onClick = { shareMenu = false; share(m.text.trim()) })
                            DropdownMenuItem(text = { Text("Copy the answer") }, onClick = { shareMenu = false; clipboard.setText(AnnotatedString(m.text.trim())) })
                        }
                    }
                }
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

/** The thinking-depth slider: Off, Auto, Low, Medium, High, Max. A model that always thinks (gpt-oss) can't go to Off. */
@Composable
internal fun DepthSlider(stored: String, alwaysThinks: Boolean, onChange: (String) -> Unit) {
    val depth = if (alwaysThinks && stored == "off") "low" else stored
    val i = DEPTHS.indexOf(depth).coerceAtLeast(0)
    Column(Modifier.width(300.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Thinking: ${DEPTH_LABELS[depth]}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = i.toFloat(),
            onValueChange = { v ->
                val d = DEPTHS[v.roundToInt().coerceIn(0, DEPTHS.lastIndex)]
                onChange(if (alwaysThinks && d == "off") "low" else d)
            },
            valueRange = 0f..DEPTHS.lastIndex.toFloat(),
            steps = DEPTHS.size - 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (d in DEPTHS) Text(if (d == "medium") "Mid" else DEPTH_LABELS.getValue(d), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (depth) {
                "off" -> "Answers at once, without thinking first. Fastest."
                "auto" -> "Picks the depth from each question: little for everyday questions, more for maths, code, analysis and plans."
                "low" -> "Thinks briefly. Good for simple questions and chat."
                "medium" -> "Thinks as much as the model usually does."
                "high" -> "Thinks carefully and checks its result. Slower; more room for the answer."
                else -> "Thinks as long as it needs and double-checks every step. Slowest: on the 27B several minutes."
            } + if (alwaysThinks) " This model always thinks: Off isn't possible, and its own Low, Medium and High are used." else
                " The model is told how deeply to think; it mostly follows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The chat's modes, with what each does. */
@Composable
internal fun ModeChoices(selected: String, onPick: (String) -> Unit) {
    for ((key, label) in CHAT_MODES) {
        DropdownMenuItem(
            text = {
                Column {
                    Text((if (key == selected) "◉ " else "○ ") + label + if (key != "normal") " " + sign(label) else "")
                    Text(
                        when (key) {
                            "socratic" -> "Guides you to the answer with questions and hints instead of giving it. Ask for the answer and you get it."
                            "student" -> "Teaches: step by step, with examples, shows the method on exercises, and ends with a question to check understanding."
                            else -> "Answers directly."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.width(300.dp),
            onClick = { onPick(key) },
        )
    }
}

/** A summary or translation from the helper, to read, copy or share. */
@Composable
internal fun QuickResult(title: String, text: String, onClose: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        title = { ClosableTitle(onClose) { Text(title) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer { MarkdownText(text) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { BtnLabel("Copy", symbol = "⧉") }
                TextButton(onClick = {
                    runCatching {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, text)
                        ctx.startActivity(android.content.Intent.createChooser(send, title))
                    }
                }) { BtnLabel("Share", androidx.compose.material.icons.Icons.Filled.Share) }
            }
        },
    )
}

/**
 * The ＋ beside the message: an image (from the phone, or a new photo; only for models that see images) or a file
 * (PDF, Word, Excel, PowerPoint, text), whose text is read on the phone and sent along.
 */
@Composable
internal fun AttachButton(seesImages: Boolean, st: StudioViewModel, onImage: (PickedImage) -> Unit, onFile: (PickedFile) -> Unit, onError: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var fromGallery by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    fun load(block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } catch (e: Exception) {
            onError(e.message ?: "Couldn't read it.")
        }
    }
    if (fromGallery) GalleryImagePicker(st, onClose = { fromGallery = false }) { name ->
        fromGallery = false
        load {
            val img = withContext(kotlinx.coroutines.Dispatchers.IO) { Inputs.fromFile(st.store.file(name)) }
            if (img != null) onImage(img) else onError("Couldn't read that image.")
        }
    }
    val pickImage = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) load { onImage(withContext(kotlinx.coroutines.Dispatchers.IO) { Inputs.image(ctx, uri, 2048) }) }
    }
    val shot = remember { java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }.let { java.io.File(it, "chat.jpg") } }
    val shotUri = remember { androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", shot) }
    val camera = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) load {
            try {
                onImage(withContext(kotlinx.coroutines.Dispatchers.IO) { Inputs.image(ctx, shotUri, 2048) })
            } finally {
                shot.delete()
            }
        }
    }
    // Several files at once (up to five per message); each read on its own, so one that can't be read doesn't stop the rest.
    val pickFile = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) load {
            for (u in uris.take(Docs.MAX_FILES)) try {
                onFile(withContext(kotlinx.coroutines.Dispatchers.IO) { Docs.read(ctx, u) })
            } catch (e: Exception) {
                onError(e.message ?: "Couldn't read one of the files.")
            }
        }
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = "Attach an image or a file") }
        ScaledDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
            if (!seesImages) Text(
                "Images: pick a model that sees them (👁)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            DropdownMenuItem(
                text = { Text("🖼 Image from the phone") },
                enabled = seesImages,
                contentPadding = pad,
                onClick = {
                    open = false
                    runCatching { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .onFailure { ApiLog.add("Opening another app failed: ${it.javaClass.name}: ${it.message}\n" + it.stackTrace.take(6).joinToString("\n") { f -> "  at $f" }, "App"); onError("The phone's image picker didn't open.") }
                },
            )
            DropdownMenuItem(
                text = { Text("🖼 Image from the Gallery") },
                enabled = seesImages,
                contentPadding = pad,
                onClick = { open = false; fromGallery = true },
            )
            DropdownMenuItem(
                text = { Text("📷 Take a photo") },
                enabled = seesImages,
                contentPadding = pad,
                onClick = { open = false; runCatching { camera.launch(shotUri) }.onFailure { ApiLog.add("Opening another app failed: ${it.javaClass.name}: ${it.message}\n" + it.stackTrace.take(6).joinToString("\n") { f -> "  at $f" }, "App"); onError("No camera app found.") } },
            )
            DropdownMenuItem(
                text = { Text("📄 File: PDF, Office, text") },
                contentPadding = pad,
                onClick = { open = false; runCatching { pickFile.launch(Docs.MIMES) }.onFailure { ApiLog.add("Opening another app failed: ${it.javaClass.name}: ${it.message}\n" + it.stackTrace.take(6).joinToString("\n") { f -> "  at $f" }, "App"); onError("No file picker found.") } },
            )
        }
    }
}

/** The app's own pictures (Create's results), newest first, to pick one. */
@Composable
internal fun GalleryImagePicker(st: StudioViewModel, onClose: () -> Unit, onPick: (String) -> Unit) {
    val results by st.results.collectAsState()
    val pics = results.filter { it.kind == "image" }.sortedByDescending { it.created }.flatMap { e -> e.files.map { e to it } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pick an image from the Gallery", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("✕") }
                }
                if (pics.isEmpty()) Text("No images made yet.", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in pics.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((e, name) in row) {
                            val bmp = rememberThumbnail(st.store.file(name), 256)
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onPick(name) }) {
                                if (bmp != null) Image(bmp.asImageBitmap(), e.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * The microphone for dictating a message: tap to start, tap again to stop; the Tiiny's speech-to-text model writes it
 * out (nothing goes to Google or anywhere else) and it lands in the message box to check before sending.
 */
@Composable
internal fun DictateButton(enabled: Boolean, models: List<ModelInfo>, st: StudioViewModel, onText: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rec = remember { WavRecorder() }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(recording) {
        while (recording) {
            kotlinx.coroutines.delay(500)
            seconds = rec.seconds.toInt()
        }
    }
    DisposableEffect(Unit) { onDispose { if (rec.recording) rec.stop() } }
    fun start() {
        seconds = 0
        rec.start()
        recording = true
    }
    val ask = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) start() }
    val hasAsr = models.any { it.isAsr }
    // Asked from outside (Quick Settings tile, widget, shortcut): start recording right away.
    val quick by Nav.dictate.collectAsState()
    LaunchedEffect(quick, hasAsr) {
        if (quick && hasAsr && !recording) {
            Nav.dictate.value = false
            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) start() else ask.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    if (recording) {
        Button(
            onClick = {
                recording = false
                val wav = rec.stop()
                if (wav.size > 16_000) st.dictate(wav, models, onText) // under half a second: nothing said
            },
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("■ ${seconds}s") }
    } else {
        IconButton(
            onClick = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) start() else ask.launch(android.Manifest.permission.RECORD_AUDIO)
            },
            enabled = enabled && hasAsr,
        ) { Icon(MicIcon, contentDescription = if (hasAsr) "Dictate" else "Dictating needs a speech-to-text (ASR) model on the Tiiny") }
    }
}

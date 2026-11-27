package app.tiinyremote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One step of a guide: what it's about, where on the screen it is, and what it does. */
data class GuideStep(val icon: String, val title: String, val text: String, val where: String = "")

/** Asks AppRoot to show a guide: -1 the welcome tour, else a page's (its section number). */
object Guides {
    val request = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
}

/** The welcome tour, shown once at the first start (Settings → About ⓘ shows it again). */
val WELCOME_TOUR = listOf(
    GuideStep("👋", "Welcome to Tiiny Remote", "Use your Tiiny AI at home from this phone: chat, create images, sound and video, translate, code and automate. Everything runs on your own Tiiny; nothing goes to a cloud."),
    GuideStep(SIGN_SETTINGS, "First: connect your Tiiny", "In Settings → Tiiny, 🔎 \"Find on my Wi-Fi\" fills in your Tiiny's address; add its API key (TiinyOS → Settings → API Key). For use away from home, add your WireGuard tunnel: the app switches it on and off by itself.", "Sidebar → Settings"),
    GuideStep(SIGN_MENU, "The sidebar", "The $SIGN_MENU top left opens it. At the top are the things you do: Start (tasks to pick from), Chat, Code, Create, Gallery, Automate, Translate. At the bottom are the ones that look after the Tiiny: Activity, Models, Settings. Its $SIGN_SETTINGS sets the start screen and the order; the red $SIGN_POWER at the bottom shuts the app down completely.", "Top left"),
    GuideStep("✨", "Start", "Tasks to pick from (\"Translate\", \"Make a song\"…): each opens the right page and tool. Long-press a card and drag it to put it where you like.", "Sidebar → Start"),
    GuideStep("▤", "The status bar", "Under the title: online (green), checking (amber) or not reachable (red), how many models are loaded, how busy the NPU and CPU are. Tap it for the details. It pulses red when the NPU reports a new fault.", "Below the title"),
    GuideStep(SIGN_MODELS, "Models first", "A model must be loaded on the Tiiny before it can work. In Models (or straight from the model button in Chat, Create…), tap $SIGN_PLAY to load and ■ to unload. Two can be loaded side by side if they fit together.", "Sidebar → Models"),
    GuideStep(SIGN_CHAT, "Then chat", "Pick a model, type or dictate ($SIGN_MIC), add an image or a file with $SIGN_ADD. Earlier chats and projects (files the chat searches) are under $SIGN_HISTORY; $SIGN_SAVE green means chats are saved, red means incognito.", "Sidebar → Chat"),
    GuideStep("■", "Stop", "Everything that makes something (an answer, an image, music, code, a test, a download) has the same red ■ Stop. It asks first and says what stays and what is lost.", "Beside the progress"),
    GuideStep("👍", "Recommended models", "Not sure which model to get? 👍 marks the recommended one in every model menu, and a missing one can be downloaded right there. Models → 👍 shows the whole set and why; the Tiiny's own AI can review it anew.", "Model menus, Models → 👍"),
    GuideStep("🎨", "Your look", "Settings → Display: colours from your wallpaper or a designed style (A, B, C), colourful emoji or line icons, typefaces, and a quiet status bar.", "Settings → Display"),
    GuideStep(SIGN_LOCK, "Private and safe", "Settings → Privacy and backup: an app lock (fingerprint, face or screen lock) and encrypted backups to your NAS.", "Settings"),
    GuideStep("📲", "Quick ways in", "Long-press the app's icon, add the widget, or the Quick Settings tiles \"Ask Tiiny\" and \"Translate copied\".", "Home screen"),
    GuideStep("❓", "Help on every page", "The ? next to each page's title walks you through that page. Settings → Labels → $SIGN_INFO explains every pictogram.", "Next to the title"),
)

/** The page guide behind each title's ?. */
fun pageGuide(tab: Int): Pair<String, List<GuideStep>> = when (tab) {
    0 -> "Models" to listOf(
        GuideStep(SIGN_MODELS, "Your models", "Everything installed on your Tiiny. Loaded ones show \"Loaded\" in orange; each one takes a share of the NPU (\"59% NPU\")."),
        GuideStep(SIGN_PLAY, "Load and unload", "$SIGN_PLAY loads a model (the card says roughly how long it takes), ■ unloads it. If it doesn't fit beside the loaded ones, the app offers to swap.", "Right on each card"),
        GuideStep("★", "Favourites, names, info", "★ keeps a model at the top, $SIGN_EDIT gives it a shorter name in this app, $SIGN_INFO tells what it can do, $SIGN_DELETE deletes it from the Tiiny.", "On each card"),
        GuideStep("🔎", "Find the right one", "Installed / Store switches between your models and the Model Store. Types filters by kind (👂 ASR, $SIGN_OCR OCR, 🧊 3D, 🎬 video… and 💻 Coding). The chips (💬 chat, 🛠 tools, 👁 images, 💭 thinking…) filter by ability; the orange $SIGN_PLAY puts loaded models first; Sort orders them.", "Top of the list"),
        GuideStep("⟳", "Update models", "With updates waiting, ⟳ (with their number) appears beside the orange $SIGN_PLAY: all of them in one list, each with its state (available, updating with %, paused, updated ✓); update one, all, pause or resume. Each card's \"Update available\" does the same for one. The Tiiny removes the old copy first, so the model is unavailable until the update finishes; ■ Stop on an update offers Pause, since stopping leaves no copy.", "Top of the list"),
        GuideStep("⤓", "The Store", "Download new models; free space on the Tiiny is shown. Search by name, maker, type or ability (\"tts\", \"ocr\", \"image editing\", \"0.6B\"); every word must fit. Downloads continue in the background.", "Store"),
        GuideStep("👍", "Recommended models", "👍 (top right) shows the recommended model for each task, why, and ✓ on your Tiiny or ⤓ to download (asked once, with the size and the free space); Download all missing gets the rest. ✦ Update with the Tiiny's AI: the most capable chat model on your Tiiny reads the Store's list and decides anew, in the background, with nothing from outside. In every model menu (and for the fast helper in Settings), the first line \"👍 Recommended · ⓘ why these\" opens this table, 👍 marks the recommended models and a missing one is offered with ⤓ (Settings → Display → Recommended models hides them).", "Top right"),
        GuideStep("🧭", "Help me choose", "Not sure which model? 🧭 opens Chat with a question; add what you want to do (\"make short videos from my photos\"). The chat model looks through your Tiiny and the Store and suggests one or two: why, whether it's installed, how much of the NPU it takes. Needs 🛠 on and a model that uses tools.", "Top right"),
        GuideStep("🔐", "VPN card", "Shows whether WireGuard is on and whether you're at home. The app switches it by itself; Turn on / Turn off does it by hand.", "Top"),
    )
    1 -> "Create" to listOf(
        GuideStep(SIGN_CREATE, "Make something", "Images, speech, music, video, 3D models, text from photos and more, each with a model made for it."),
        GuideStep("🗂", "Choose what", "The first row picks the kind (3D, Audio, Image, Text, Video), the second the task (Create, Edit, Voice, Song…).", "Top"),
        GuideStep("🧠", "The model", "The model button shows which one is used; ● means loaded. $SIGN_PLAY Load loads it, swapping others out if needed.", "Under the choices"),
        GuideStep("✍", "Describe and start", "Fill in what it asks for (a description, a photo, a text) and start. Long jobs keep running in the background; you get a notice when they're done.", "Middle"),
        GuideStep("🙂", "Characters (Image)", "Describe someone once; tick them and they look the same in every image.", "Image → Characters"),
        GuideStep("👁", "Live preview (Image)", "Watch the image form. Switch it on or off beforehand (Image → Advanced) or while the image is being made: the Preview switch beside ■ Stop on the progress card. ✕ stops.", "Image → Advanced, progress card"),
        GuideStep("✦", "Improve prompt (Image)", "✦ rewrites your description or edit instruction. With Qwen-Image's prompt enhancer on the Tiiny (Qwen-Image-2.1-PE-T2I for creating, -PE-I2I for editing; Store), that one does it, loaded for it if needed. With Qwen-Image chosen and no enhancer yet, ✦ offers to download it (or to use the chat model this time).", "Image → under the description"),
        GuideStep("✂", "Quick edits (Image → Edit)", "One tap each, with a FLUX.2-klein model made for the job (Store; ⤓ = not on the Tiiny yet): Extend the image (it's painted further), Remove an object or Zoom into a part (paint over it in red first), Remove the background. A missing model is offered for download right there.", "Image → Edit, under the image"),
        GuideStep("🗣", "Dialogue and voice change (Audio → Voice)", "Models that can (VibeVoice-1.5B) speak a dialogue: one line per turn, \"Anna: …\", up to 4 people, each with their own voice. CosyVoice changes a recording's voice into one of your saved voices.", "Audio → Voice, top"),
        GuideStep("👂", "Who said what (Audio → Transcribe)", "With MOSS-Transcribe-Diarize the transcript says who speaks when: \"Speaker 1 (0:12): …\".", "Audio → Transcribe"),
        GuideStep("🎵", "Songs", "A song is exactly as long as chosen; with Separate tracks, vocals and backing come as extra files. If the song model's own port can't be reached, a shorter song is the opening of a full-length one.", "Audio → Song"),
        GuideStep("🖼", "Results", "Everything made lands in the Gallery. Under a result: Send to chat, Edit (images), $SIGN_SHARE Share, $SIGN_DELETE Delete (Undo at the bottom brings it back). 🧊 3D models open in the app's own viewer (turn with a finger, pinch to zoom); \"Other app\" hands the file on."),
        GuideStep("🗃", "Copies on the Tiiny", "The Tiiny keeps the texts sent from here (Describe, OCR, Improve prompt, lyrics) like chats, by day. 🗃 at the top shows and deletes them (whole days).", "Top right"),
    )
    2 -> "Chat" to listOf(
        GuideStep("🧠", "The model", "The button on top picks the chat model (● = loaded); $SIGN_PLAY loads it. A3B models answer fast, 27B ones more carefully.", "Top"),
        GuideStep("💭", "Options", "The signs beside it open the chat's options: thinking depth (💭), tools (🛠: make images, speak, make music, and find models for you), web search ($SIGN_WEB), follow-up ideas (💡), the fast helper (⚡: on, it's used and loaded if needed; off, it isn't used even when loaded), and Socratic (🤔) or Student (🎓) mode.", "Right of the model"),
        GuideStep(SIGN_ADD, "Attach", "$SIGN_ADD adds an image (from the phone, the Gallery or a new photo, for models that see images) or files (up to five at once): PDF, Word, Excel, PowerPoint, text. ✕ beside a name takes it off again.", "Left of the message"),
        GuideStep(SIGN_MIC, "Dictate", "With the message box empty, $SIGN_MIC records you; the Tiiny's speech-to-text (👂 ASR) writes it out into the box. $SIGN_EDIT improves your message before sending.", "Right of the message"),
        GuideStep(SIGN_HISTORY, "Chats", "$SIGN_HISTORY earlier chats: tap one to open it, $SIGN_EDIT renames, $SIGN_DELETE deletes (Undo at the bottom brings it back). $SIGN_SAVE green = saved / red = incognito, $SIGN_ADD new chat, 🗃 the copies the Tiiny keeps of every chat, by day (delete whole days there), $SIGN_REFRESH try the last answer again.", "Top right"),
        GuideStep("📁", "Projects", "In $SIGN_HISTORY: ＋ New project, then add its files (up to 50). Every question in a project's chats gets the pieces of its files that match it: by meaning with an embedding model on the Tiiny (the smallest installed, loaded if it fits; a reranker sorts them if one fits too), otherwise by words. The files stay on the phone (and in the backup). The 📁 line above the message opens the project; ✕ takes the chat out of it.", "$SIGN_HISTORY top right"),
        GuideStep("⧉", "Answers", "Copy (⧉) or $SIGN_SHARE share an answer, or $SIGN_TRANSLATE translate it; when you've scrolled up, ↓ jumps back to the newest message.", "Under each answer"),
    )
    3 -> "Activity" to listOf(
        GuideStep(SIGN_ACTIVITY, "Logs", "Everything the app did and what the Tiiny answered, newest first. Problems are red with ⚠."),
        GuideStep("🏷", "Filters", "The chips show one kind only (Device, Models, VPN, Chat…); ⚠ Problems only hides the rest.", "Top"),
        GuideStep(SIGN_LOCK, "Unlock", "$SIGN_LOCK unlocks the Tiiny's system pages, when it asks for that.", "Top right"),
        GuideStep("📊", "Tiiny stats", "📊 adds what the Tiiny itself counted (requests, errors).", "Above the list"),
        GuideStep(SIGN_SHARE, "Share", "$SIGN_SHARE sends what's shown, without keys, passwords or email addresses. Long-press entries to choose just some. Clear empties the log.", "Above the list"),
    )
    4 -> "Settings" to listOf(
        GuideStep("🔌", "Tiiny", "Everything about reaching your Tiiny, together: its address (🔎 \"Find on my Wi-Fi\" fills it in; a device that wants the API key is only offered, the key goes nowhere until you Save it as the address) and API key, Restart and Power off, unlocking after a restart (with the NAS as a fallback), and the WireGuard tunnel for away from home."),
        GuideStep("🖥", "Display", "Style: colours from your wallpaper or a designed look (A · Ink & Paper, B · Graphite & Brass, C · Alpine Slate), light or dark. Pictograms: colourful emoji, line icons in colour, or line icons in the style's colour (with line icons, every pictogram in the app is drawn in the same thin line). Recommended models (👍 in the model menus, or hidden), Typefaces (the phone's font, the designed pair, or Inter everywhere; one size scale either way), the quiet one-line status bar, sizes, and labels with words, pictograms or both ($SIGN_INFO beside Labels explains every pictogram)."),
        GuideStep(SIGN_LOCK, "Privacy and backup", "App lock: fingerprint, face or the screen lock to open the app. Backup: everything of the app's, encrypted on the phone with your backup password (Show lets you see it while typing), kept by the NAS agent (the newest five); ⟳ reloads the list. Restore checks the backup first, then puts it in place and closes the app; also onto a new phone. With the lock on, the recent-apps view shows no preview of the app.", "Privacy and backup"),
        GuideStep("⚡", "Fast helper model", "A small second chat model for side jobs: naming chats, follow-up ideas, condensing. Pick one that fits beside your main model and answers without thinking first. 👍 marks the recommended ones, and any model the speed tests found about as quick at a helper's job. $SIGN_PLAY beside the menu loads it, ■ unloads it. In the chat's options, ⚡ Helper switches it on or off.", "Fast helper model"),
        GuideStep("📊", "Speed tests", "Measures whether a helper beside the main model saves time, and how many seconds each takes for a typical helper job (condensing a chat, thinking included). Test models: \"Only the loaded pair\" (this also clears the lists), or up to 3 helpers and up to 9 main models (👍 marks the recommended ones; a chosen helper is greyed among the main models, and as a main model it's paired with the other helpers only); each pair is then loaded and tested in turn, and your models come back at the end. Test runs: how often each pair is tested. ■ Stop the speed test asks: after this step, or now. 📊 Statistics keeps every result.", "Below the helper"),
        GuideStep("🎯", "Always ready", "Keeps the embedding model (🔗) and reranker (🥇) that search projects' files, OCR ($SIGN_OCR), a voice for reading aloud (🗣) and speech-to-text (👂) loaded whenever there's room, so they start at once. Nothing is unloaded for them.", "Always ready"),
        GuideStep("📶", "Stay connected", "Keeps the app connected in the background (a silent notification), for results and automations on time.", "Further down"),
        GuideStep(SIGN_INFO, "About", "$SIGN_INFO at the bottom: the app's version, your Tiiny's system versions and the link to the code. \"Welcome tour\" there shows the first-start tour again.", "Bottom"),
    )
    5 -> "Automate" to listOf(
        GuideStep(SIGN_AUTOMATE, "Let the Tiiny work on its own", "Jobs that run at a set time: a morning briefing, a news digest, a look at your calendar or mail."),
        GuideStep(SIGN_AGENT, "Agent or process", "$SIGN_AGENT An agent decides itself what to look up (web, news, calendar, mail) before it writes. $SIGN_PROCESS A process follows fixed steps.", "When you make one"),
        GuideStep(SIGN_ADD, "Make one", "Start from a template or from scratch: name, time, days, model, what it should do, which tools it may use.", "Automate"),
        GuideStep("📬", "Results", "Results lists what came out; \"Continue in chat\" takes one into a chat.", "Results"),
        GuideStep("🔗", "Connectors", "Your calendar, your email (read-only, via IMAP) and optionally your NAS, which can run automations even with the phone off. With agent 1.7 the link to the NAS is encrypted; Test shows its certificate, and the agent's log shows the same characters. Set the agent up anew? \"Forget certificate\".", "Connectors"),
    )
    6 -> "Gallery" to listOf(
        GuideStep(SIGN_GALLERY, "Everything you made", "Images, sound, video, 3D and texts from Create, newest first. Images, videos (🎬) and 3D models (🧊) show as tiles: tap one to see it big, play it or turn it. Deleted by mistake? Undo at the bottom brings it back for a few seconds."),
        GuideStep("🗂", "Filter and search", "The chips filter by kind and task; search finds words in descriptions.", "Top"),
        GuideStep("📅", "Order", "Newest or oldest first, grouped by day, month, year or not at all.", "Top"),
        GuideStep("↔", "Open and browse", "Tap an item to open it big; swipe left or right for the next or previous one. There: $SIGN_DELETE delete, send to chat, $SIGN_EDIT edit (images), $SIGN_SHARE share.", "Each item"),
        GuideStep("☑", "Several at once", "Long-press to mark items, tap more, then share or delete them together (Undo brings them back).", "Each item"),
        GuideStep("🗃", "Copies on the Tiiny", "Texts sent to the Tiiny (descriptions, OCR, prompts) stay there by day, like chats: 🗃 at the top deletes them. Images, sound and videos it doesn't keep.", "Top right"),
    )
    7 -> "Translate" to listOf(
        GuideStep(SIGN_TRANSLATE, "Translate", "Text, a photo (a sign, a menu, a letter) or speech, with the models on your Tiiny."),
        GuideStep("🗂", "What to translate", "Text (Aa), Photo (📷) or Speech (👂) at the top. A photo can come from the camera or the phone.", "Top"),
        GuideStep("⇄", "Languages", "From (Detect finds it by itself) and To; ⇄ swaps them.", "Below"),
        GuideStep(SIGN_TRANSLATE, "Go", "\"$SIGN_TRANSLATE Translate into …\" starts it; the result can be copied or shared.", "Bottom"),
    )
    8 -> "Code" to listOf(
        GuideStep(SIGN_CODE, "Code with your Tiiny", "A coding model works with you on projects kept on this phone. Nothing leaves home unless you push it to GitHub."),
        GuideStep("📁", "Projects", "The 📁 button picks a project or makes a new one; $SIGN_MORE beside it has more (new file, preview, share as .zip, GitHub, clear, delete).", "Top"),
        GuideStep("💬", "Ask · Build · Learn", "💬 Ask: questions and code to copy. 🛠 Build: the model writes and changes the files itself (✎ lines). 🎓 Learn: step by step, with a question at the end.", "Top"),
        GuideStep("📄", "Files", "Files shows the project's files; tap one to read or edit it yourself.", "Top"),
        GuideStep(SIGN_PLAY, "Preview", "Code can't run on the phone, but a web page can: $SIGN_PLAY Preview at the top shows index.html. A good first try: Build \"a simple to-do list as a web page\".", "Top row"),
        GuideStep("🐙", "GitHub", "Copy a repository into a new project, push a project in one commit, or make a new private repository. Needs a GitHub token (the dialog links to where you make one).", "$SIGN_MORE → GitHub"),
    )
    9 -> "Start" to listOf(
        GuideStep("✨", "What would you like to do?", "Tasks instead of models: tap one and the right page opens with the right tool; the model is picked there. Back returns here."),
        GuideStep("✋", "Your order", "Long-press a card, then drag it where you want it (near the edge, the page scrolls). \"Original order\" at the bottom undoes it."),
        GuideStep("📲", "From outside the app", "Long-press the app's icon (Dictate, Chat, Translate copied text, Gallery), add the widget to the home screen, or the Quick Settings tiles \"Ask Tiiny\" and \"Translate copied\" (pull down twice, then edit the tiles)."),
        GuideStep(SIGN_MENU, "Open on it", "The sidebar's $SIGN_SETTINGS → Start with: Start.", "Sidebar"),
    )
    else -> "This page" to emptyList()
}

/**
 * A guide laid over the page: the page stays visible behind a light shade, the steps in a card at the bottom with
 * Back, Next and Skip.
 */
@Composable
fun GuideOverlay(title: String, steps: List<GuideStep>, onClose: () -> Unit) {
    if (steps.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }
        return
    }
    var i by remember(steps) { mutableIntStateOf(0) }
    val step = steps[i.coerceIn(steps.indices)]
    // Its own window over the page (which stays visible, shaded); taps beside the card do nothing.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) { AppScale {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).navigationBarsPadding().padding(12.dp),
            elevation = CardDefaults.cardElevation(8.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$title · ${i + 1} of ${steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text(if (i == steps.lastIndex) "Close" else "Skip") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignText(step.icon, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 30.sp), modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.title, style = MaterialTheme.typography.titleMedium)
                        if (step.where.isNotBlank()) SignText("📍 ${step.where}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    SignText(step.text, style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Dots: where in the guide you are; tap one to jump there.
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in steps.indices) Box(
                            Modifier.size(if (n == i) 10.dp else 8.dp)
                                .background(
                                    if (n == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    CircleShape,
                                )
                                .clickable { i = n },
                        )
                    }
                    if (i > 0) TextButton(onClick = { i-- }) { Text("Back") }
                    Spacer(Modifier.size(4.dp))
                    Button(onClick = { if (i < steps.lastIndex) i++ else onClose() }) { Text(if (i < steps.lastIndex) "Next" else "Done") }
                }
            }
        }
    }
    } }
}

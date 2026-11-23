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
import androidx.compose.material3.Text
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
    GuideStep("👋", "Welcome to Tiiny Remote", "Use your Tiiny AI at home from this phone: chat, create pictures, sound and video, translate, code and automate. Everything runs on your own Tiiny; nothing goes to a cloud."),
    GuideStep("⚙", "First: connect your Tiiny", "In Settings, enter your Tiiny's address and its API key (from Tiiny Studio). For use away from home, add your WireGuard tunnel: the app switches it on and off by itself.", "Sidebar → Settings"),
    GuideStep("☰", "The sidebar", "The ☰ top left opens it. At the top are the things you do: Chat, Code, Create, Gallery, Automate, Translate. At the bottom are the ones that look after the Tiiny: Activity, Models, Settings. Its ⚙ sets the start screen and the order; the red ⏻ at the bottom shuts the app down completely.", "Top left"),
    GuideStep("▤", "The status strip", "Under the title: online or not, how many models are loaded, how busy the NPU and CPU are. Tap it for the details. It pulses red when the NPU reports a new fault.", "Below the title"),
    GuideStep("🧠", "Models first", "A model must be loaded on the Tiiny before it can work. In Models (or straight from the model button in Chat, Create…), tap ▶ to load and ■ to unload. Two can be loaded side by side if they fit together.", "Sidebar → Models"),
    GuideStep("💬", "Then chat", "Pick a model, type or dictate (🎤), add an image or a file with ＋. Earlier chats are under 🕘; 💾 green means chats are saved, red means incognito.", "Sidebar → Chat"),
    GuideStep("❓", "Help on every page", "The ? next to each page's title walks you through that page. Settings → \"What the pictograms mean\" explains every sign.", "Next to the title"),
)

/** The page guide behind each title's ?. */
fun pageGuide(tab: Int): Pair<String, List<GuideStep>> = when (tab) {
    0 -> "Models" to listOf(
        GuideStep("🧠", "Your models", "Everything installed on your Tiiny. Loaded ones show \"Loaded\" in orange; each one takes a share of the NPU (\"59% NPU\")."),
        GuideStep("▶", "Load and unload", "▶ loads a model (the card says roughly how long it takes), ■ unloads it. If it doesn't fit beside the loaded ones, the app offers to swap.", "Right on each card"),
        GuideStep("★", "Favourites, names, info", "★ keeps a model at the top, ✎ gives it a shorter name in this app, ⓘ tells what it can do, 🗑 deletes it from the Tiiny.", "On each card"),
        GuideStep("🔎", "Find the right one", "Installed / Store switches between your models and the Model Store. Types filters by kind (also Coding). The chips (💬 chat, 🛠 tools, 👁 images, 💭 thinking…) filter by ability; the orange ▶ puts loaded models first; Sort orders them.", "Top of the list"),
        GuideStep("⤓", "The Store", "Download new models; free space on the Tiiny is shown. Downloads continue in the background.", "Store"),
        GuideStep("🔐", "VPN card", "Shows whether WireGuard is on and whether you're at home. The app switches it by itself; Turn on / Turn off does it by hand.", "Top"),
    )
    1 -> "Create" to listOf(
        GuideStep("✦", "Make something", "Pictures, speech, music, video, 3D models, text from photos and more, each with a model made for it."),
        GuideStep("🗂", "Choose what", "The first row picks the kind (3D, Audio, Image, Text, Video), the second the task (Create, Edit, Voice, Song…).", "Top"),
        GuideStep("🧠", "The model", "The model button shows which one is used; ● means loaded. ▶ Load loads it, swapping others out if needed.", "Under the choices"),
        GuideStep("✍", "Describe and start", "Fill in what it asks for (a description, a photo, a text) and start. Long jobs keep running in the background; you get a notice when they're done.", "Middle"),
        GuideStep("🖼", "Gallery", "Everything made lands in the Gallery; the Gallery link on this page goes there."),
    )
    2 -> "Chat" to listOf(
        GuideStep("🧠", "The model", "The button on top picks the chat model (● = loaded); ▶ loads it. A3B models answer fast, 27B ones more carefully.", "Top"),
        GuideStep("💭", "Options", "The signs beside it open the chat's options: thinking depth, tools (🛠 make pictures, speak…), web search (🔍), follow-up ideas (💡), and Socratic or Student mode.", "Right of the model"),
        GuideStep("＋", "Attach", "＋ adds an image (from the phone or a new photo, for models that see images) or a file: PDF, Word, Excel, PowerPoint, text.", "Left of the message"),
        GuideStep("🎤", "Dictate", "With the message box empty, 🎤 records you; the Tiiny writes it out into the box. ✎ improves your message before sending.", "Right of the message"),
        GuideStep("🕘", "Chats", "🕘 earlier chats (rename or delete them there), 💾 green = saved / red = incognito, ＋ new chat, ⟳ try the last answer again.", "Top right"),
        GuideStep("⧉", "Answers", "Copy or share an answer from its buttons; when you've scrolled up, ↓ jumps back to the newest message.", "Under each answer"),
    )
    3 -> "Activity" to listOf(
        GuideStep("📜", "Logs", "Everything the app did and what the Tiiny answered, newest first. Problems are red with ⚠."),
        GuideStep("🏷", "Filters", "The chips show one kind only (Device, Models, VPN, Chat…); ⚠ Problems only hides the rest.", "Top"),
        GuideStep("🔒", "Unlock", "The lock button unlocks the Tiiny's system pages, when it asks for that.", "Top right"),
        GuideStep("📊", "Tiiny stats", "📊 adds what the Tiiny itself counted (requests, errors).", "Above the list"),
        GuideStep("⤴", "Share", "Share sends what's shown, without keys, passwords or email addresses. Long-press entries to choose just some. ✕ clears the log.", "Above the list"),
    )
    4 -> "Settings" to listOf(
        GuideStep("🔌", "Connection", "Your Tiiny's address and API key, and the WireGuard tunnel for using it away from home."),
        GuideStep("🖥", "Display", "Size of everything, labels with words or signs only, the compact layout for small or square screens."),
        GuideStep("⚡", "Fast helper model", "A small second model for quick side jobs (naming chats, follow-up ideas, condensing). The speed test checks whether two models together are worth it; 📊 Statistics shows the results and the speed of your chats.", "Fast helper model"),
        GuideStep("🎯", "Always ready", "Keeps speech-to-text and/or OCR loaded whenever there's room, so they start at once.", "Always ready"),
        GuideStep("📶", "Stay connected", "Keeps the app connected in the background (a silent notification), for results and automations on time.", "Further down"),
        GuideStep("ⓘ", "About", "ⓘ at the bottom: the app's version, your Tiiny's system versions and the link to the code. \"Welcome tour\" there shows the first-start tour again.", "Bottom"),
    )
    5 -> "Automate" to listOf(
        GuideStep("🔁", "Let the Tiiny work on its own", "Jobs that run at a set time: a morning briefing, a news digest, a look at your calendar or mail."),
        GuideStep("📋", "Process or agent", "📋 A process follows fixed steps. 🤖 An agent decides itself what to look up (web, news, calendar, mail) before it writes.", "When you make one"),
        GuideStep("＋", "Make one", "Start from a template or from scratch: name, time, days, model, what it should do, which tools it may use.", "Automate"),
        GuideStep("📬", "Results", "Results lists what came out; \"Continue in chat\" takes one into a chat.", "Results"),
        GuideStep("🔗", "Connectors", "Your calendar, your email (read-only, via IMAP) and optionally your NAS, which can run automations even with the phone off.", "Connectors"),
    )
    6 -> "Gallery" to listOf(
        GuideStep("🖼", "Everything you made", "Pictures, sound, video, 3D and texts from Create, newest first."),
        GuideStep("🗂", "Filter and search", "The chips filter by kind and task; search finds words in descriptions.", "Top"),
        GuideStep("📅", "Order", "Newest or oldest first, grouped by day, month, year or not at all.", "Top"),
        GuideStep("⤴", "Open, share, redo", "Tap an item to open it, share it, or go back to Create with the same settings.", "Each item"),
    )
    7 -> "Translate" to listOf(
        GuideStep("🌐", "Translate", "Text, a photo (a sign, a menu, a letter) or speech, with the models on your Tiiny."),
        GuideStep("🗂", "What to translate", "Text, Photo or Speech at the top. A photo can come from the camera or the phone.", "Top"),
        GuideStep("⇄", "Languages", "From (Detect finds it by itself) and To; ⇄ swaps them.", "Below"),
        GuideStep("▶", "Go", "\"Translate into …\" starts it; the result can be copied or shared.", "Bottom"),
    )
    8 -> "Code" to listOf(
        GuideStep("💻", "Code with your Tiiny", "A coding model works with you on projects kept on this phone. Nothing leaves home unless you push it to GitHub."),
        GuideStep("📁", "Projects", "The 📁 button picks a project or makes a new one; ⋮ has more (new file, preview, share as .zip, GitHub, delete).", "Top"),
        GuideStep("💬", "Ask · Build · Learn", "💬 Ask: questions and code to copy. 🛠 Build: the model writes and changes the files itself (✎ lines). 🎓 Learn: step by step, with a question at the end.", "Top"),
        GuideStep("📄", "Files", "Files shows the project's files; tap one to read or edit it yourself.", "Top"),
        GuideStep("▶", "Preview", "Code can't run on the phone, but a web page can: ⋮ → Preview shows index.html. A good first try: Build \"a simple to-do list as a web page\".", "⋮ menu"),
        GuideStep("🐙", "GitHub", "Copy a repository into a new project, push a project in one commit, or make a new private repository. Needs a GitHub token (the dialog links to where you make one).", "⋮ → GitHub"),
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
                    Text(step.icon, fontSize = 30.sp, modifier = Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (step.where.isNotBlank()) Text("📍 ${step.where}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    Text(step.text, style = MaterialTheme.typography.bodyMedium)
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

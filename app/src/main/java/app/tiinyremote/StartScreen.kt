package app.tiinyremote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing to do, as the Start page shows it. */
private class Task(val icon: String, val title: String, val sub: String, val go: () -> Unit)

/**
 * The Start page: what you can do, as tasks rather than models ("Translate a photo", "Make a song"). Each card opens
 * the right page and tool; the models are picked there as usual. (Sidebar ⚙ → Start with: Start, to open on it.)
 */
@Composable
fun StartScreen(st: StudioViewModel, goTab: (Int) -> Unit, openTool: (String) -> Unit) {
    fun tool(id: String) = openTool(id) // one step: Back returns to Start
    val tasks = listOf(
        Task("💬", "Ask anything", "Chat with a model on your Tiiny") { goTab(2) },
        Task(SIGN_MIC, "Dictate a question", "Speak; the answer comes as text") { goTab(2); Nav.dictate.value = true },
        Task("📄", "Ask about a document", "Attach a PDF or text in Chat") { goTab(2) },
        Task(SIGN_TRANSLATE, "Translate", "A text, a photo or speech") { goTab(7) },
        Task("🖼", "Make an image", "From a description") { tool("image") },
        Task("✎", "Change an image", "Edit one of yours") { tool("edit") },
        Task("👁", "Describe an image", "What's in a photo") { tool("describe") },
        Task(SIGN_OCR, "Read text from a photo", "OCR: a receipt, a sign, a page") { tool("ocr") },
        Task("🎵", "Make a song", "Style and lyrics, with vocals") { tool("song") },
        Task("🗣", "Read a text aloud", "In a voice, or your own") { tool("speech") },
        Task("🎬", "Make a short video", "From a scene or a photo") { tool("video") },
        Task("🧊", "Make a 3D model", "From a photo") { tool("mesh") },
        Task(SIGN_CODE, "Build a web page", "The model writes it; preview it here") { goTab(8) },
        Task(SIGN_AUTOMATE, "Automate something", "Daily email summaries, news, reminders") { goTab(5) },
        Task(SIGN_GALLERY, "See what you made", "Images, sound, video, 3D, texts") { goTab(6) },
        Task("🧭", "Help me choose a model", "Say what you want to do") { st.askInChat(HELP_CHOOSE_DRAFT) },
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("What would you like to do?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
        }
        items(tasks) { t ->
            Card(
                Modifier.fillMaxWidth().clickable(onClick = t.go),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SignText(t.icon, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text(t.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

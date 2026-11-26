package app.tiinyremote

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asks for a project's name: a new one ([current] null) or a new name for one. */
@Composable
internal fun ProjectNameDialog(current: String?, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(current ?: "") }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (current == null) "New project" else "Rename project") },
        text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text(if (current == null) "Create" else "Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/**
 * A project: its chats (open one, or start a new one in it) and its files (add, remove). Every question in its chats
 * gets the pieces of these files that match it.
 */
@Composable
internal fun ProjectDialog(st: StudioViewModel, project: Project, onOpenChat: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by st.projectTick.collectAsState()
    val chats by st.chatList.collectAsState()
    val list by st.projectList.collectAsState()
    val p = list.find { it.id == project.id } ?: project
    val docs = remember(tick, p.id) { st.projects.docs(p.id) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val read = ArrayList<PickedFile>()
            val problems = ArrayList<String>()
            for ((i, u) in uris.withIndex()) {
                busy = "Reading ${i + 1} of ${uris.size}…"
                try {
                    read.add(withContext(Dispatchers.IO) { Docs.read(ctx, u) })
                } catch (e: Exception) {
                    problems.add(e.message ?: "A file couldn't be read.")
                }
            }
            st.addProjectFiles(p.id, read) { more ->
                busy = null
                note = (problems + more).joinToString("\n").ifBlank { null }
            }
        }
    }
    if (renaming) ProjectNameDialog(p.name) { n -> renaming = false; if (n != null) st.renameProject(p.id, n) }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${p.name}?") },
        text = { Text("Its files go from this phone. Its chats stay, as ordinary chats.") },
        confirmButton = { TextButton(onClick = { deleting = false; st.deleteProject(p.id); onClose() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
    ScaledAlertDialog(
        onDismissRequest = onClose,
        title = { Text("📁 ${p.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Chats", style = MaterialTheme.typography.titleMedium)
                val mine = chats.filter { it.project == p.id }
                if (mine.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (c in mine) Text(
                    c.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChat(c.id) }.padding(vertical = 6.dp),
                )
                OutlinedButton(onClick = { st.newChatIn(p.id); onClose() }, modifier = Modifier.padding(top = 4.dp)) { Text("＋ New chat in this project") } // with words always: two ＋ here
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("Files", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Each question gets the pieces of these files that match it: by meaning with an embedding model on the Tiiny " +
                        "(the smallest installed, loaded if it fits), otherwise by words. The files stay on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (d in docs) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("📄 ${d.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${"%,d".format(d.chars)} characters" + (if (d.cut) " (cut)" else "") +
                                (if (d.flag.isNotBlank()) " · ⚠ looks like it gives the model instructions: answers run without tools and web" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (d.flag.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { st.removeProjectFile(p.id, d.id) }) { Text("✕") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { pick.launch(Docs.MIMES) }.onFailure { note = "The phone's file picker didn't open: ${it.message}" }
                    }, enabled = busy == null) { Text("＋ Add files") }
                    busy?.let {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                note?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Up to ${Projects.MAX_DOCS} files, ${Projects.MAX_TOTAL / 1_000_000} million characters together.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = {
            Row {
                TextButton(onClick = { deleting = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { renaming = true }) { Text("Rename") }
            }
        },
    )
}

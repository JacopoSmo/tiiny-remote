package app.tiinyremote

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

private val CODE_MODES = listOf(
    Triple("ask", "💬", "Ask"),
    Triple("build", "🛠", "Build"),
    Triple("learn", "🎓", "Learn"),
)

private val MODE_HINTS = mapOf(
    "ask" to "Ask: questions about code or your project, snippets to copy. The model may read the project's files; it changes none.",
    "build" to "Build: the model writes and changes the project's files itself (watch the ✎ lines). Try a web page first: " +
        "\"make a simple to-do list as a web page\", then ⋮ → Preview.",
    "learn" to "Learn: step by step, one idea at a time, with small examples and a question to check. Good to start coding.",
)

/**
 * Code: a coding model on the Tiiny works with you on projects kept on the phone. Ask (answers), Build (it changes the
 * files) and Learn (it teaches); Files to look at or edit them yourself; ⋮ for projects, Preview of a web page, sharing
 * as .zip and GitHub. Everything stays at home unless you push it to GitHub.
 */
@Composable
fun CodeScreen(s: UiState, vm: MainViewModel, st: StudioViewModel) {
    val cv: CodeViewModel = viewModel()
    val ctx = LocalContext.current
    val ui = LocalUi.current
    val projects by cv.projects.collectAsState()
    val current by cv.current.collectAsState()
    val files by cv.files.collectAsState()
    val msgs by cv.msgs.collectAsState()
    val busy by cv.busy.collectAsState()
    val note by cv.note.collectAsState()
    var mode by rememberSaveable { mutableStateOf(st.setting("code.mode", "ask")) }
    var think by rememberSaveable { mutableStateOf(st.setting("code.think", "0") == "1") }
    var input by rememberSaveable(current) { mutableStateOf("") }
    var showFiles by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var projMenu by remember { mutableStateOf(false) }
    var newProject by remember { mutableStateOf(false) }
    var github by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) } // a file's path ("" = a new file)
    var preview by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length, msgs.lastOrNull()?.steps?.size) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().padding(horizontal = if (ui.compact) 8.dp else 12.dp)) {
        Spacer(Modifier.height(if (ui.compact) 4.dp else 8.dp))
        val model = ModelPicker("code", { it.codes }, s, vm, st)
        // Project, mode, files and the ⋮ menu on one line (it scrolls sideways when narrow).
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { projMenu = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                        Text("📁 " + (current ?: "No project") + " ▾", maxLines = 1)
                    }
                    ScaledDropdownMenu(expanded = projMenu, onDismissRequest = { projMenu = false }) {
                        for (p in projects) DropdownMenuItem(
                            text = { Text((if (p == current) "● " else "○ ") + p) },
                            onClick = { cv.open(p); projMenu = false },
                        )
                        if (projects.isNotEmpty()) HorizontalDivider()
                        DropdownMenuItem(text = { Text("＋ New project") }, onClick = { newProject = true; projMenu = false })
                        DropdownMenuItem(text = { Text("⤓ From GitHub…") }, onClick = { github = true; projMenu = false })
                    }
                }
                for ((key, icon, word) in CODE_MODES) FilterChip(
                    selected = mode == key,
                    onClick = { mode = key; st.setSetting("code.mode", key) },
                    label = { Text(choiceLabel(icon, word)) },
                )
                if (model?.thinks == true) FilterChip(
                    selected = think,
                    onClick = { think = !think; st.setSetting("code.think", if (think) "1" else "0") },
                    label = { Text(sign("Thinking")) },
                )
                FilterChip(selected = showFiles, onClick = { showFiles = !showFiles }, label = { Text("Files ${files.size}") })
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Project menu") }
                ScaledDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("＋ New project") }, onClick = { newProject = true; menu = false })
                    DropdownMenuItem(text = { Text("▶ Preview (web page)") }, enabled = cv.previewFile() != null, onClick = { preview = true; menu = false })
                    DropdownMenuItem(text = { Text("📄 New file") }, enabled = current != null, onClick = { editing = ""; menu = false })
                    DropdownMenuItem(text = { Text("⤴ Share as .zip") }, enabled = files.isNotEmpty(), onClick = {
                        menu = false
                        cv.zip()?.let { z ->
                            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", z)
                            ctx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                "Share the project",
                            ))
                        }
                    })
                    DropdownMenuItem(text = { Text("🐙 GitHub…") }, onClick = { github = true; menu = false })
                    DropdownMenuItem(text = { Text("🧹 Clear this conversation") }, enabled = msgs.isNotEmpty() && busy == null, onClick = { cv.clearChat(); menu = false })
                    DropdownMenuItem(text = { Text("🗑 Delete project") }, enabled = current != null && busy == null, onClick = { confirmDelete = true; menu = false })
                }
            }
        }
        if (showFiles) {
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                if (files.isEmpty()) Text("No files yet. Build makes them, or ⋮ → New file.", style = MaterialTheme.typography.bodySmall)
                for (f in files) Row(Modifier.fillMaxWidth().clickable { editing = f.path }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.path, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (f.size < 1024) "${f.size} B" else "${f.size / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msgs.isEmpty()) {
                Text(
                    (MODE_HINTS[mode] ?: "") + "\n\nYour projects stay on this phone; the model runs on your Tiiny, so nothing leaves home " +
                        "unless you push it to GitHub. Code can't be run here, but a web page can be previewed (⋮ → Preview).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (current == null) Button(onClick = { newProject = true }) { Text("＋ New project") }
            }
            for ((i, m) in msgs.withIndex()) CodeBubble(m, live = busy != null && i == msgs.lastIndex)
        }
        busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = if (ui.compact) 4.dp else 8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).heightIn(max = 160.dp),
                placeholder = { Text(if (mode == "build") "What should it build or change?" else "Your question") },
            )
            Spacer(Modifier.width(6.dp))
            if (busy != null) {
                OutlinedButton(onClick = { cv.stopNow() }) { BtnLabel("Stop", symbol = "■") }
            } else {
                Button(
                    onClick = { model?.let { cv.send(it.id, mode, think, input); input = "" } },
                    enabled = model?.running == true && input.isNotBlank() && current != null,
                ) { BtnLabel("Send", Icons.AutoMirrored.Filled.Send) }
            }
        }
    }

    note?.let { n ->
        ScaledAlertDialog(onDismissRequest = { cv.noteShown() }, text = { Text(n) }, confirmButton = { TextButton(onClick = { cv.noteShown() }) { Text("OK") } })
    }
    if (newProject) {
        var name by remember { mutableStateOf("") }
        ScaledAlertDialog(
            onDismissRequest = { newProject = false },
            title = { Text("New project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                    Text("Letters, digits, - and _ (the same name works for a GitHub repository).", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { if (cv.newProject(name)) newProject = false }) { Text("Make it") } },
            dismissButton = { TextButton(onClick = { newProject = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) current?.let { p ->
        ScaledAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete $p?") },
            text = { Text("Its files and conversation are deleted from this phone. A copy on GitHub stays.") },
            confirmButton = { TextButton(onClick = { cv.deleteProject(p); confirmDelete = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    editing?.let { path -> FileEditor(cv, path) { editing = null } }
    if (preview) cv.previewFile()?.let { f -> WebPreview(f) { preview = false } }
    if (github) GitHubDialog(cv, current) { github = false }
}

/** A message: text with Markdown, code blocks in monospace with a Copy button, and what was done with the files. */
@Composable
private fun CodeBubble(m: CodeMsg, live: Boolean) {
    val mine = m.role == "user"
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth()
            .background(
                if (mine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (step in m.steps) Text(step, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        if (m.text.isEmpty() && live) Text("…")
        // Fenced code blocks shown apart, each with its own Copy.
        val parts = remember(m.text) { m.text.split(Regex("(?m)^```")).mapIndexed { i, t -> (i % 2 == 1) to t } }
        for ((isCode, t) in parts) {
            if (isCode) {
                val code = t.substringAfter('\n', "").trimEnd()
                val lang = t.substringBefore('\n').trim()
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lang.ifBlank { "code" }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("⧉ Copy") }
                    }
                    SelectionContainer {
                        Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.horizontalScroll(rememberScrollState()))
                    }
                }
            } else if (t.isNotBlank()) {
                SelectionContainer { if (mine) Text(t.trim()) else MarkdownText(t.trim()) }
            }
        }
    }
}

/** A file to read or edit yourself ("" = a new one, named here). */
@Composable
private fun FileEditor(cv: CodeViewModel, path: String, onClose: () -> Unit) {
    val original = remember(path) { if (path.isEmpty()) "" else cv.read(path) }
    var name by remember(path) { mutableStateOf(path) }
    var text by remember(path) { mutableStateOf(original ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f),
        title = { ClosableTitle(onClose) { Text(if (path.isEmpty()) "New file" else path, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (path.isEmpty()) OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Path, e.g. index.html or src/app.py") })
                if (original == null && path.isNotEmpty()) {
                    Text("This isn't a text file, so it can't be shown here.")
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        },
        confirmButton = {
            if (original != null || path.isEmpty()) TextButton(
                onClick = { if (cv.write(name, text)) onClose() },
                enabled = name.isNotBlank() && (path.isEmpty() || text != original),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (path.isNotEmpty() && original != null) TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("⧉") }
                if (path.isNotEmpty()) {
                    if (confirmDelete) TextButton(onClick = { cv.delete(path); onClose() }) { Text("Really delete?", color = MaterialTheme.colorScheme.error) }
                    else TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                }
            }
        },
    )
}

/**
 * A web page of the project, shown as it would be in a browser (its CSS, JavaScript and pictures from the project
 * folder; the internet too, for libraries it loads from a CDN).
 */
@Composable
private fun WebPreview(file: java.io.File, onClose: () -> Unit) {
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.98f),
        title = { ClosableTitle(onClose) { Text("Preview: ${file.name}") } },
        text = {
            AndroidView(
                factory = { c ->
                    android.webkit.WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Only this project's folder: the page can load its own files, nothing else of the app's.
                        @Suppress("DEPRECATION")
                        settings.allowFileAccess = true
                        webViewClient = android.webkit.WebViewClient()
                        loadUrl(android.net.Uri.fromFile(file).toString())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(460.dp),
            )
        },
        confirmButton = {},
    )
}

/** GitHub: the token, this project's repository (or a new one), Push, and copying a repository into a new project. */
@Composable
private fun GitHubDialog(cv: CodeViewModel, project: String?, onClose: () -> Unit) {
    val busy by cv.busy.collectAsState()
    val t = remember(project) { cv.gitTarget() }
    var token by remember { mutableStateOf(cv.githubToken()) }
    var owner by remember(project) { mutableStateOf(t?.owner ?: "") }
    var repo by remember(project) { mutableStateOf(t?.repo ?: project ?: "") }
    var branch by remember(project) { mutableStateOf(t?.branch ?: "main") }
    var message by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f),
        title = { ClosableTitle(onClose) { Text("GitHub") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = token, onValueChange = { token = it }, singleLine = true, label = { Text("Token") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "A fine-grained token with \"Contents: read and write\" (and \"Administration: write\" to make new repositories), " +
                        "only for the repositories you choose. Kept encrypted on this phone. Public repositories can be copied without one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { uri.openUri("https://github.com/settings/personal-access-tokens/new") }) { Text("Make a token on github.com") }
                if (token != cv.githubToken()) TextButton(onClick = { cv.setGithubToken(token) }) { Text("Save the token") }
                HorizontalDivider()
                if (project != null) {
                    Text("This project: $project", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = owner, onValueChange = { owner = it }, singleLine = true, label = { Text("Owner") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = repo, onValueChange = { repo = it }, singleLine = true, label = { Text("Repository") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = branch, onValueChange = { branch = it }, singleLine = true, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = message, onValueChange = { message = it }, singleLine = true, label = { Text("What changed (commit message)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { cv.setGithubToken(token); cv.setGitTarget(owner, repo, branch); cv.push(message) },
                            enabled = busy == null && owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank(),
                        ) { Text("⤴ Push") }
                        OutlinedButton(onClick = { cv.setGithubToken(token); cv.createRepo() }, enabled = busy == null && token.isNotBlank()) { Text("New private repository") }
                    }
                    Text(
                        "Push sends the project's files in one commit; files only on GitHub stay, unless they came from here and " +
                            "were deleted here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                }
                Text("Copy a repository into a new project", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = from, onValueChange = { from = it }, singleLine = true, label = { Text("owner/repository or its link") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { if (token.isNotBlank()) cv.setGithubToken(token); cv.importRepo(from, "") }, enabled = busy == null && from.isNotBlank()) { Text("⤓ Copy it here") }
                Text("Text files up to 300 KB (at most 400); pictures and other binary files are left out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                busy?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {},
    )
}

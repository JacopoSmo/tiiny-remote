package app.tiinyremote

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A coding model: a chat model made for code (by its name), or a big enough general one (20B parameters or more). */
private val CODE_NAME = Regex("(?i)coder|codestral|devstral|starcoder|codegemma|codellama")
private val PARAMS = Regex("(?i)(?<![a-z0-9])(\\d+(?:\\.\\d+)?)b(?![a-z])")

internal fun codesByName(id: String): Boolean =
    CODE_NAME.containsMatchIn(id) || (PARAMS.find(id.substringAfterLast('/'))?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0) >= 20.0

val ModelInfo.codes get() = isChat && codesByName(id)

/** The Models → Types entry for coding models (not a type the Tiiny has). */
internal const val CODING = "Coding"

/** A message in a project's conversation; steps are what the model did with the files ("✎ wrote index.html"). */
data class CodeMsg(val role: String, val text: String, val steps: List<String> = emptyList())

data class CodeFile(val path: String, val size: Long)

/** Where a project goes on GitHub, and which files were last in sync there (only those are ever deleted there). */
data class GitTarget(val owner: String, val repo: String, val branch: String, val known: Set<String> = emptySet())

/**
 * Code: projects on the phone (a folder each, under the app's private files), a conversation per project with a coding
 * model on the Tiiny, and GitHub (import a repository, push a project in one commit). Ask and Learn may read the
 * files; Build may change them. Nothing runs code: the Tiiny has no place for that, but a web page can be previewed.
 */
class CodeViewModel(app: Application) : AndroidViewModel(app) {
    private val root = File(app.filesDir, "code").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("code", Context.MODE_PRIVATE)
    private val settings = Store(app)

    private val _projects = MutableStateFlow(listProjects())
    val projects: StateFlow<List<String>> = _projects.asStateFlow()

    private val _current = MutableStateFlow(prefs.getString("current", null)?.takeIf { File(root, it).isDirectory } ?: _projects.value.firstOrNull())
    val current: StateFlow<String?> = _current.asStateFlow()

    private val _files = MutableStateFlow<List<CodeFile>>(emptyList())
    val files: StateFlow<List<CodeFile>> = _files.asStateFlow()

    private val _msgs = MutableStateFlow<List<CodeMsg>>(emptyList())
    val msgs: StateFlow<List<CodeMsg>> = _msgs.asStateFlow()

    /** What's going on ("Writing…", "Pushing to GitHub…"), or null when idle. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()
    fun noteShown() {
        _note.value = null
    }

    private var stop = AtomicBoolean(false)

    init {
        open(_current.value)
    }

    // ---------- projects ----------

    private fun listProjects() = root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()

    private fun dir(p: String) = File(root, p)
    private fun meta(p: String) = File(dir(p), ".tiiny").apply { mkdirs() }

    fun open(p: String?) {
        // Not while the model works: its answer belongs to the project it was asked in.
        if (_busy.value != null && p != _current.value) {
            _note.value = "Wait until the answer is finished (or ■ Stop it) before switching projects."
            return
        }
        switchTo(p)
    }

    private fun switchTo(p: String?) {
        _current.value = p
        prefs.edit().putString("current", p).apply()
        _msgs.value = if (p == null) emptyList() else loadMsgs(p)
        refreshFiles()
    }

    /** A folder-safe name: letters, digits, - and _ (the name of a GitHub repository fits too). */
    private fun clean(name: String) = name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.').take(60)

    fun newProject(name: String): Boolean {
        if (_busy.value != null) {
            _note.value = "Wait until the answer is finished first."
            return false
        }
        val n = clean(name)
        if (n.isBlank() || dir(n).exists()) {
            _note.value = if (n.isBlank()) "Give the project a name." else "There's already a project called $n."
            return false
        }
        dir(n).mkdirs()
        _projects.value = listProjects()
        open(n)
        return true
    }

    fun deleteProject(p: String) {
        dir(p).deleteRecursively()
        _projects.value = listProjects()
        switchTo(_projects.value.firstOrNull())
    }

    fun clearChat() {
        val p = _current.value ?: return
        _msgs.value = emptyList()
        saveMsgs(p)
    }

    private fun loadMsgs(p: String): List<CodeMsg> = try {
        val a = JSONArray(File(meta(p), "chat.json").readText())
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            CodeMsg(o.optString("role"), o.optString("text"), o.optJSONArray("steps")?.let { s -> (0 until s.length()).map { s.optString(it) } } ?: emptyList())
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveMsgs(p: String) {
        val a = JSONArray()
        for (m in _msgs.value.takeLast(200)) a.put(JSONObject().put("role", m.role).put("text", m.text).put("steps", JSONArray(m.steps)))
        try {
            File(meta(p), "chat.json").writeText(a.toString())
        } catch (e: Exception) {
        }
    }

    // ---------- files ----------

    fun refreshFiles() {
        val p = _current.value
        _files.value = if (p == null) emptyList() else dir(p).walkTopDown()
            .onEnter { it.name != ".tiiny" && it.name != ".git" }
            .filter { it.isFile }
            .map { CodeFile(it.relativeTo(dir(p)).path.replace('\\', '/'), it.length()) }
            .sortedBy { it.path.lowercase() }
            .toList()
    }

    /**
     * A path inside the project, or null for anything that would leave it (.., absolute paths) or touch the app's own
     * .tiiny folder.
     */
    private fun safe(p: String, path: String): File? {
        val rel = path.trim().replace('\\', '/').removePrefix("./").trimStart('/')
        if (rel.isBlank() || rel.split('/').any { it.isBlank() || it == ".." || it == "." } || rel.startsWith(".tiiny") || rel.startsWith(".git/")) return null
        val f = File(dir(p), rel)
        return f.takeIf { it.canonicalPath.startsWith(dir(p).canonicalPath + File.separator) }
    }

    fun read(path: String): String? = _current.value?.let { p -> safe(p, path)?.takeIf { it.isFile }?.let { textOf(it) } }

    private fun textOf(f: File): String? {
        val b = f.readBytes()
        return if (b.take(4000).any { it == 0.toByte() }) null else String(b, Charsets.UTF_8)
    }

    fun write(path: String, text: String): Boolean {
        val p = _current.value ?: return false
        val f = safe(p, path) ?: run { _note.value = "That path isn't allowed."; return false }
        f.parentFile?.mkdirs()
        f.writeText(text)
        refreshFiles()
        return true
    }

    fun delete(path: String) {
        val p = _current.value ?: return
        safe(p, path)?.delete()
        refreshFiles()
    }

    /** The project packed as a .zip in the cache (for Share), without the app's .tiiny folder. */
    fun zip(): File? {
        val p = _current.value ?: return null
        val out = File(getApplication<Application>().cacheDir, "share").apply { mkdirs() }.let { File(it, "$p.zip") }
        ZipOutputStream(out.outputStream()).use { z ->
            for (f in _files.value) {
                z.putNextEntry(ZipEntry("$p/${f.path}"))
                File(dir(p), f.path).inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return out
    }

    /** The project's index.html (or the first .html file), for the preview. */
    fun previewFile(): File? {
        val p = _current.value ?: return null
        val html = _files.value.firstOrNull { it.path.equals("index.html", true) } ?: _files.value.firstOrNull { it.path.endsWith(".html", true) }
        return html?.let { File(dir(p), it.path) }
    }

    // ---------- the model ----------

    fun stopNow() {
        stop.set(true)
    }

    /**
     * Sends a message in [mode]: "ask" (answers, may read the files), "learn" (teaches step by step, may read the
     * files) or "build" (changes the files itself). [think]: let the model reason first (slower, often better code).
     */
    fun send(model: String, mode: String, think: Boolean, text: String, usesTools: Boolean = true) {
        val p = _current.value ?: run { _note.value = "Make a project first (⋮ → New project)."; return }
        if (_busy.value != null || text.isBlank()) return
        val before = _msgs.value
        _msgs.value = before + CodeMsg("user", text.trim()) + CodeMsg("assistant", "")
        saveMsgs(p)
        val halt = AtomicBoolean(false)
        stop = halt
        _busy.value = if (mode == "build") "Building…" else "Writing…"
        Background.set(getApplication(), "code", "Coding on the Tiiny")
        viewModelScope.launch {
            val steps = ArrayList<String>()
            fun show(extra: String = "") {
                _msgs.value = _msgs.value.dropLast(1) + _msgs.value.last().let { it.copy(text = it.text + extra, steps = steps.toList()) }
            }
            try {
                withContext(Dispatchers.IO) {
                    val s = settings.load()
                    val c = DeviceClient(s.tiinyUrl, s.apiKey)
                    val tm = Tools.thinkingMode(c, model)
                    val convo = JSONArray().put(JSONObject().put("role", "system").put("content", systemFor(p, mode, usesTools)))
                    // The earlier conversation (text only, the last 16 messages), then the new message.
                    for (m in before.takeLast(16)) if (m.text.isNotBlank()) {
                        convo.put(JSONObject().put("role", m.role).put("content", m.text + if (m.steps.isNotEmpty()) "\n\n(Done with the files: ${m.steps.joinToString("; ")})" else ""))
                    }
                    convo.put(JSONObject().put("role", "user").put("content", text.trim()))
                    val tools = toolSpecs(mode == "build")
                    // A model not marked for tool use answers in one go (with code to copy) instead of using the files.
                    val rounds = if (!usesTools) 0 else if (mode == "build") 30 else 8
                    for (round in 0..rounds) {
                        if (halt.get()) break
                        val body = JSONObject().put("model", model).put("messages", convo).put("stream", true)
                            .put("max_tokens", if (mode == "build") 8192 else 4096)
                        when (tm) {
                            "switch" -> body.put("chat_template_kwargs", JSONObject().put("enable_thinking", think))
                            "always" -> body.put("reasoning_effort", if (think) "medium" else "low")
                        }
                        if (round < rounds) body.put("tools", tools)
                        val done = c.streamChat(body, { content, _ -> if (content.isNotEmpty()) show(content) }, { halt.get() })
                        if (halt.get() || done.calls.isEmpty()) break
                        val calls = JSONArray()
                        done.calls.forEachIndexed { i, call ->
                            if (call.id.isBlank()) call.id = "call_$i"
                            calls.put(JSONObject().put("id", call.id).put("type", "function").put("function",
                                JSONObject().put("name", call.name).put("arguments", call.args.toString().ifBlank { "{}" })))
                        }
                        convo.put(JSONObject().put("role", "assistant").put("content", done.content).put("tool_calls", calls))
                        for (call in done.calls) {
                            val args = try {
                                JSONObject(call.args.toString().ifBlank { "{}" })
                            } catch (e: Exception) {
                                JSONObject()
                            }
                            val answer = tool(p, call.name, args, mode == "build", steps)
                            withContext(Dispatchers.Main) { show(); refreshFiles() }
                            convo.put(JSONObject().put("role", "tool").put("tool_call_id", call.id).put("content", answer))
                        }
                        if (_msgs.value.last().text.isNotBlank()) withContext(Dispatchers.Main) { show(if (_msgs.value.last().text.endsWith("\n\n")) "" else "\n\n") }
                    }
                }
                if (_msgs.value.last().text.isBlank() && steps.isNotEmpty()) show("Done.")
            } catch (e: Exception) {
                show((if (_msgs.value.last().text.isEmpty()) "" else "\n\n") + "Error: ${e.message}")
            } finally {
                _msgs.value = _msgs.value.dropLast(1) + _msgs.value.last().let { it.copy(text = it.text.trim(), steps = steps.toList()) }
                saveMsgs(p)
                refreshFiles()
                _busy.value = null
                Background.set(getApplication(), "code", null)
            }
        }
    }

    private fun systemFor(p: String, mode: String, usesTools: Boolean): String {
        val list = _files.value.take(200).joinToString("\n") { "${it.path} (${it.size} bytes)" }.ifBlank { "(no files yet)" }
        val base = "You're a coding assistant in Tiiny Remote, running on the user's own Tiiny AI device. The user works on the " +
            "project \"$p\", a folder of files on their phone. Its files now:\n$list\n\n" +
            "Code can't be run here: there's no terminal or compiler. A web page (index.html with its CSS and JavaScript) can " +
            "be previewed in the app (⋮ → Preview). The project can be shared as a .zip or pushed to GitHub. "
        if (!usesTools) return base + "You can't open or change the files yourself here: ask the user to paste what you " +
            "need. Give complete files in fenced code blocks, each with its path on the line before it, so the user can copy " +
            "them into the project (Files). " + if (mode == "learn") "Teach step by step, one idea at a time, with a question at the end. " +
            "Answer in the user's language." else "Be concise. Answer in the user's language."
        return base + when (mode) {
            "build" -> "Build what the user asks by changing the files yourself with the tools: look at the relevant files " +
                "first (read_file), then write them (write_file replaces a whole file; edit_file changes one exact piece). Write " +
                "complete, working files, never placeholders like \"rest unchanged\". Keep to what was asked. When done, say in a " +
                "few lines what you changed and how to try it. Answer in the user's language."
            "learn" -> "Teach: the user is learning to code. Explain step by step in plain words, one idea at a time, with " +
                "short examples, and check understanding with a question at the end. Read the project's files when it helps " +
                "(read_file); don't write the whole solution at once unless asked. Answer in the user's language."
            else -> "Answer questions about code and the project; read files when needed (read_file). Show code in fenced " +
                "blocks with the language named. Be concise. To change files, the user can switch to Build. Answer in the user's language."
        }
    }

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String>) = JSONObject().put("type", "function").put(
        "function",
        JSONObject().put("name", name).put("description", desc)
            .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required))),
    )

    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)

    private fun toolSpecs(write: Boolean): JSONArray {
        val a = JSONArray()
            .put(fn("list_files", "List the project's files with their sizes.", JSONObject(), emptyList()))
            .put(fn("read_file", "Read a file of the project (text).", JSONObject().put("path", str("Path inside the project, e.g. src/main.py")), listOf("path")))
        if (write) {
            a.put(fn("write_file", "Create or replace a whole file with the given content.",
                JSONObject().put("path", str("Path inside the project")).put("content", str("The complete new content of the file")), listOf("path", "content")))
            a.put(fn("edit_file", "Replace one exact piece of a file (it must occur exactly once) with new text.",
                JSONObject().put("path", str("Path inside the project")).put("old", str("The exact text to replace")).put("new", str("The new text")),
                listOf("path", "old", "new")))
            a.put(fn("delete_file", "Delete a file of the project.", JSONObject().put("path", str("Path inside the project")), listOf("path")))
        }
        return a
    }

    private fun kb(n: Int) = if (n < 1024) "$n B" else String.format(java.util.Locale.ROOT, "%.1f KB", n / 1024.0)

    /** Runs one tool call; returns what the model is told. Writing tools only in Build. */
    private fun tool(p: String, name: String, a: JSONObject, write: Boolean, steps: MutableList<String>): String {
        val path = a.optString("path")
        return when (name) {
            "list_files" -> {
                refreshFiles()
                _files.value.joinToString("\n") { "${it.path} (${it.size} bytes)" }.ifBlank { "(no files yet)" }
            }
            "read_file" -> {
                val f = safe(p, path) ?: return "Not allowed: $path"
                if (!f.isFile) return "No such file: $path"
                steps.add("👁 read $path")
                val t = textOf(f) ?: return "$path isn't a text file."
                if (t.length > 60_000) t.take(60_000) + "\n…(cut: the file is ${t.length} characters long)" else t
            }
            "write_file" -> {
                if (!write) return "Changing files needs Build mode."
                val f = safe(p, path) ?: return "Not allowed: $path"
                val content = a.optString("content")
                f.parentFile?.mkdirs()
                val existed = f.exists()
                f.writeText(content)
                steps.add("✎ ${if (existed) "rewrote" else "wrote"} $path (${kb(content.toByteArray().size)})")
                "Written: $path (${content.length} characters)"
            }
            "edit_file" -> {
                if (!write) return "Changing files needs Build mode."
                val f = safe(p, path) ?: return "Not allowed: $path"
                val t = (if (f.isFile) textOf(f) else null) ?: return "No such text file: $path"
                val old = a.optString("old")
                var count = 0
                var at = if (old.isEmpty()) -1 else t.indexOf(old)
                while (at >= 0 && count < 2) {
                    count++
                    at = t.indexOf(old, at + 1)
                }
                when {
                    count == 0 -> "The text to replace wasn't found in $path: read it again and copy the piece exactly."
                    count > 1 -> "The text to replace occurs more than once in $path: include more of the lines around it."
                    else -> {
                        f.writeText(t.replace(old, a.optString("new")))
                        steps.add("✎ edited $path")
                        "Edited: $path"
                    }
                }
            }
            "delete_file" -> {
                if (!write) return "Changing files needs Build mode."
                val f = safe(p, path) ?: return "Not allowed: $path"
                if (!f.isFile) return "No such file: $path"
                f.delete()
                steps.add("🗑 deleted $path")
                "Deleted: $path"
            }
            else -> "Unknown tool: $name"
        }
    }

    // ---------- GitHub ----------

    /** A GitHub token (fine-grained, with Contents: read and write), encrypted with the Android Keystore. */
    fun githubToken(): String = Crypto.decrypt(prefs.getString("gh_token", null)) ?: ""

    fun setGithubToken(t: String) {
        prefs.edit().putString("gh_token", if (t.isBlank()) null else Crypto.encrypt(t.trim())).apply()
    }

    fun gitTarget(): GitTarget? = _current.value?.let { p ->
        try {
            val o = JSONObject(File(meta(p), "github.json").readText())
            GitTarget(o.optString("owner"), o.optString("repo"), o.optString("branch", "main"),
                o.optJSONArray("known")?.let { k -> (0 until k.length()).map { k.optString(it) }.toSet() } ?: emptySet())
        } catch (e: Exception) {
            null
        }
    }

    private fun saveTarget(p: String, t: GitTarget) {
        File(meta(p), "github.json").writeText(
            JSONObject().put("owner", t.owner).put("repo", t.repo).put("branch", t.branch).put("known", JSONArray(t.known.toList())).toString(),
        )
    }

    fun setGitTarget(owner: String, repo: String, branch: String) {
        val p = _current.value ?: return
        val old = gitTarget()
        val same = old != null && old.owner == owner.trim() && old.repo == repo.trim()
        saveTarget(p, GitTarget(owner.trim(), repo.trim(), branch.trim().ifBlank { "main" }, if (same) old!!.known else emptySet()))
    }

    private class Gh(val code: Int, val text: String) {
        val ok get() = code in 200..299
        fun json() = JSONObject(text)
    }

    private fun gh(method: String, path: String, body: JSONObject? = null, raw: Boolean = false): Gh {
        val conn = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        // Android's HttpURLConnection (OkHttp inside) takes PATCH; the plain Java one doesn't: then POST with an override header.
        try {
            conn.requestMethod = method
        } catch (e: java.net.ProtocolException) {
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", method)
        }
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Accept", if (raw) "application/vnd.github.raw" else "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        githubToken().takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                val b = body.toString().toByteArray()
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(b) }
            }
            val code = conn.responseCode
            val text = (if (code >= 400) conn.errorStream else conn.inputStream)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            if (code >= 400) ApiLog.add("GitHub $method $path -> HTTP $code: ${text.take(200)}", "Code")
            return Gh(code, text)
        } finally {
            conn.disconnect()
        }
    }

    /** A download from GitHub (following its redirect to codeload), up to [max] bytes; null if it fails or is bigger. */
    private fun ghBytes(path: String, max: Long): ByteArray? {
        val conn = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github.raw")
        githubToken().takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        return try {
            if (conn.responseCode == 200) conn.inputStream.use { Docs.readAtMost(it, max) } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun why(r: Gh) = try {
        r.json().optString("message").ifBlank { "HTTP ${r.code}" }
    } catch (e: Exception) {
        "HTTP ${r.code}"
    } + when (r.code) {
        401 -> " (the token is wrong or expired)"
        403, 404 -> " (no access: check the name, and that the token may use this repository)"
        else -> ""
    }

    private fun github(label: String, block: suspend () -> String) {
        if (_busy.value != null) {
            _note.value = "Wait until the answer is finished first."
            return
        }
        _busy.value = label
        viewModelScope.launch {
            try {
                _note.value = withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                _note.value = "GitHub: ${e.message}"
            } finally {
                refreshFiles()
                _busy.value = null
            }
        }
    }

    /** Git's own name for a file's content, to skip files GitHub already has unchanged. */
    private fun blobSha(b: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${b.size}\u0000".toByteArray())
        return md.digest(b).joinToString("") { "%02x".format(it) }
    }

    /** Makes a new private repository with the project's name (and a first commit, so the push has a branch to go on). */
    fun createRepo() = github("Making the repository…") {
        val p = _current.value ?: return@github "No project open."
        if (githubToken().isBlank()) return@github "Enter a GitHub token first."
        val r = gh("POST", "/user/repos", JSONObject().put("name", p).put("private", true).put("auto_init", true)
            .put("description", "Made with Tiiny Remote"))
        if (!r.ok) return@github "Couldn't make the repository: ${why(r)}"
        val o = r.json()
        saveTarget(p, GitTarget(o.optJSONObject("owner")?.optString("login").orEmpty(), o.optString("name"), o.optString("default_branch", "main")))
        "Private repository ${o.optString("full_name")} made. Push to put the files there."
    }

    /**
     * Pushes the project to its repository in one commit. Files GitHub has unchanged are skipped; a file is deleted
     * there only if it was in sync with this project before and is gone here now (others in the repository stay).
     */
    fun push(message: String) = github("Pushing to GitHub…") {
        val p = _current.value ?: return@github "No project open."
        val t = gitTarget() ?: return@github "Choose the repository first."
        if (githubToken().isBlank()) return@github "Enter a GitHub token first."
        val repo = "/repos/${t.owner}/${t.repo}"
        var ref = gh("GET", "$repo/git/ref/heads/${t.branch}")
        if (ref.code == 404 || ref.code == 409) {
            // An empty repository has no branch yet: the first file through the simple API makes one.
            val first = _files.value.firstOrNull() ?: return@github "The project has no files to push."
            val bytes = File(dir(p), first.path).readBytes()
            val r = gh("PUT", "$repo/contents/${first.path}", JSONObject().put("message", message.ifBlank { "First files from Tiiny Remote" })
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("branch", t.branch))
            if (!r.ok) return@github "Couldn't push: ${why(r)}"
            ref = gh("GET", "$repo/git/ref/heads/${t.branch}")
        }
        if (!ref.ok) return@github "Couldn't push: ${why(ref)}"
        val head = ref.json().optJSONObject("object")?.optString("sha") ?: return@github "Couldn't push: no branch ${t.branch}."
        val baseTree = gh("GET", "$repo/git/commits/$head").takeIf { it.ok }?.json()?.optJSONObject("tree")?.optString("sha")
            ?: return@github "Couldn't push: couldn't read the branch."
        val remote = gh("GET", "$repo/git/trees/$baseTree?recursive=1").takeIf { it.ok }?.json()?.optJSONArray("tree")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("type") == "blob" }.associate { it.optString("path") to it.optString("sha") }
        } ?: emptyMap()
        val entries = JSONArray()
        var changed = 0
        for (f in _files.value) {
            val bytes = File(dir(p), f.path).readBytes()
            if (remote[f.path] == blobSha(bytes)) continue
            val b = gh("POST", "$repo/git/blobs", JSONObject().put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("encoding", "base64"))
            if (!b.ok) return@github "Couldn't push ${f.path}: ${why(b)}"
            entries.put(JSONObject().put("path", f.path).put("mode", "100644").put("type", "blob").put("sha", b.json().optString("sha")))
            changed++
        }
        val here = _files.value.map { it.path }.toSet()
        val gone = t.known.filter { it !in here && it in remote }
        for (g in gone) entries.put(JSONObject().put("path", g).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL))
        if (entries.length() == 0) {
            saveTarget(p, t.copy(known = here))
            return@github "Nothing to push: GitHub already has these files."
        }
        val tree = gh("POST", "$repo/git/trees", JSONObject().put("base_tree", baseTree).put("tree", entries))
        if (!tree.ok) return@github "Couldn't push: ${why(tree)}"
        val commit = gh("POST", "$repo/git/commits", JSONObject().put("message", message.ifBlank { "Update from Tiiny Remote" })
            .put("tree", tree.json().optString("sha")).put("parents", JSONArray().put(head)))
        if (!commit.ok) return@github "Couldn't push: ${why(commit)}"
        val moved = gh("PATCH", "$repo/git/refs/heads/${t.branch}", JSONObject().put("sha", commit.json().optString("sha")))
        if (!moved.ok) return@github "Couldn't push: ${why(moved)}"
        saveTarget(p, t.copy(known = here))
        ApiLog.add("Pushed $p to ${t.owner}/${t.repo} (${t.branch}): $changed changed, ${gone.size} deleted", "Code")
        "Pushed to ${t.owner}/${t.repo}: $changed file${if (changed == 1) "" else "s"} changed" + if (gone.isNotEmpty()) ", ${gone.size} deleted." else "."
    }

    /** Copies a repository into a new project (text files up to 300 KB, at most 400; pictures and other binaries are left out). */
    fun importRepo(spec: String, branch: String) = github("Getting the repository…") {
        val m = Regex("(?:github\\.com/)?([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$").find(spec.trim())
            ?: return@github "Write it as owner/repository (or paste its github.com link)."
        val (owner, name) = m.destructured
        val info = gh("GET", "/repos/$owner/$name")
        if (!info.ok) return@github "Couldn't open $owner/$name: ${why(info)}"
        val br = branch.trim().ifBlank { info.json().optString("default_branch", "main") }
        // The whole repository in one download (a .zip), so even without a token GitHub's hourly limit isn't reached.
        val zip = ghBytes("/repos/$owner/$name/zipball/${java.net.URLEncoder.encode(br, "UTF-8")}", 200L * 1024 * 1024)
            ?: return@github "Couldn't download $owner/$name (too big, over 200 MB, or no access)."
        var p = clean(name)
        var n = 2
        while (dir(p).exists()) p = clean(name) + "-" + n++
        dir(p).mkdirs()
        val got = HashSet<String>()
        var skipped = 0
        java.util.zip.ZipInputStream(zip.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                // Entries are "<owner>-<repo>-<commit>/path": the first folder goes.
                val path = e.name.substringAfter('/', "")
                if (!e.isDirectory && path.isNotBlank()) {
                    val bytes = Docs.readAtMost(z, 300_000)
                    val f = safe(p, path)
                    if (f == null || bytes == null || got.size >= 400 || bytes.take(4000).any { it == 0.toByte() }) skipped++
                    else {
                        f.parentFile?.mkdirs()
                        f.writeBytes(bytes)
                        got.add(path)
                    }
                }
                e = z.nextEntry
            }
        }
        saveTarget(p, GitTarget(owner, name, br, got))
        withContext(Dispatchers.Main) {
            _projects.value = listProjects()
            switchTo(p)
        }
        "$owner/$name copied into the project $p: ${got.size} files" + if (skipped > 0) " ($skipped large, binary or excess files left out)." else "."
    }
}

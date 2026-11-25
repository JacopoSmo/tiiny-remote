package app.tiinyremote

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** One finished piece of work: an image, a sound, a video, a 3D model or a text. */
data class ResultEntry(
    val id: String,
    /** image, ocr, speech, transcript, loop, song, video, mesh */
    val kind: String,
    val title: String,
    val files: List<String>,
    val text: String,
    val model: String,
    val created: Long,
) {
    val mainFile get() = files.firstOrNull()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind).put("title", title)
        .put("files", JSONArray(files)).put("text", text).put("model", model).put("created", created)

    companion object {
        fun fromJson(o: JSONObject) = ResultEntry(
            id = o.optString("id"),
            kind = o.optString("kind"),
            title = o.optString("title"),
            files = o.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            text = o.optString("text"),
            model = o.optString("model"),
            created = o.optLong("created"),
        )
    }
}

/** Results live in the app's private storage; "Save to phone" copies them to the phone's shared folders. */
class ResultStore(private val ctx: Context) {
    val dir = File(ctx.filesDir, "results").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.ROOT)

    @Synchronized
    fun all(): List<ResultEntry> = try {
        val a = JSONArray(index.readText())
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(ResultEntry::fromJson) }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun write(list: List<ResultEntry>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(index)
    }

    /** Date and time (so file managers sort them) plus a random tail, so two results in the same second can't collide. */
    fun newId(): String =
        stamp.format(java.time.LocalDateTime.now()) + "-" + java.util.UUID.randomUUID().toString().take(4)

    /** Saves bytes as a result file; the name starts with the date so file managers sort them. */
    fun saveFile(id: String, suffix: String, bytes: ByteArray): String {
        val name = "$id-$suffix"
        File(dir, name).writeBytes(bytes)
        return name
    }

    /** A result file by name; a name that would lead out of the results folder is refused. */
    fun file(name: String): File {
        val f = File(dir, name)
        require(f.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "Not a result file: $name" }
        return f
    }

    @Synchronized
    fun add(e: ResultEntry) {
        write(listOf(e) + all().filter { it.id != e.id })
    }

    /** A 3D model's picture of itself, taken the first time it's viewed (for the Gallery). */
    fun renderOf(e: ResultEntry) = File(dir, "${e.id}-render.png")
    /** The image a 3D model was made from, until it has been viewed once. */
    fun posterOf(e: ResultEntry) = File(dir, "${e.id}-poster.png")
    /** What to show for a 3D model: its own picture, else the image it came from, else nothing. */
    fun pictureOf(e: ResultEntry): File? = listOf(renderOf(e), posterOf(e)).firstOrNull { it.exists() }

    @Synchronized
    fun delete(e: ResultEntry) {
        e.files.forEach { runCatching { file(it).delete() } }
        runCatching { renderOf(e).delete(); posterOf(e).delete() }
        write(all().filter { it.id != e.id })
    }

    fun uri(name: String): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, name))

    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "glb" -> "model/gltf-binary"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun share(e: ResultEntry) {
        val send = if (e.files.isEmpty()) {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, e.text)
        } else {
            val uris = ArrayList(e.files.map { uri(it) })
            val i = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            i.setType(if (e.files.size == 1) mime(e.files[0]) else "*/*")
            if (e.text.isNotBlank()) i.putExtra(Intent.EXTRA_TEXT, e.text)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Several results shared at once: all their files (texts joined, for results without files). */
    fun shareMany(list: List<ResultEntry>) {
        if (list.size == 1) return share(list[0])
        val files = list.flatMap { it.files }
        val texts = list.filter { it.files.isEmpty() && it.text.isNotBlank() }.joinToString("\n\n———\n\n") { "${it.title}\n\n${it.text}" }
        val send = if (files.isEmpty()) {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texts)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { uri(it) }))
                .setType(files.map { mime(it).substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .also { if (texts.isNotBlank()) it.putExtra(Intent.EXTRA_TEXT, texts) }
        }
        ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens a file in another app (e.g. a 3D viewer for .glb). False when no app on the phone can open it. */
    fun open(name: String): Boolean {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(name), mime(name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copies a result into the phone's shared folders (Pictures, Music, Movies or Download, in "Tiiny Remote"),
     * where the gallery and file manager see it. Android 10 and newer; returns where it went, or null.
     */
    fun saveToPhone(name: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val mime = mime(name)
        val (collection, folder) = when {
            mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Tiiny Remote")
        }
        val resolver = ctx.contentResolver
        val target = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(target)?.use { out -> File(dir, name).inputStream().use { it.copyTo(out) } }
            "$folder/Tiiny Remote"
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            null
        }
    }
}

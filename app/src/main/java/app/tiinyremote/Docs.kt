package app.tiinyremote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.zip.ZipInputStream

/** A file attached to a chat message: its name and the text read from it on the phone. */
data class PickedFile(val name: String, val text: String, val cut: Boolean)

/**
 * Reads the text of a file for the chat, on the phone, so any chat model can work with it: PDF, Word (.docx), Excel
 * (.xlsx), PowerPoint (.pptx) and plain text (.txt, .md, .csv, .json…). Old binary Office files (.doc, .xls, .ppt) aren't
 * readable this way. At most 60,000 characters go to the model (about 15,000 tokens).
 */
object Docs {
    const val MAX_CHARS = 60_000
    val MIMES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/*", "application/json", "application/xml",
    )

    /** Reads at most [max] bytes; null if there's more (a zip bomb or a far too big file isn't read whole). */
    fun readAtMost(input: java.io.InputStream, max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** One part of an Office file, at most 40 MB unpacked. */
    private fun part(z: ZipInputStream, name: String): String =
        readAtMost(z, 40L * 1024 * 1024)?.toString(Charsets.UTF_8) ?: throw MediaException("$name is too big to read on the phone.")

    fun read(ctx: Context, uri: Uri): PickedFile {
        val cr = ctx.contentResolver
        val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "file"
        val size = cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
        if (size > 50L * 1024 * 1024) throw MediaException("$name is over 50 MB: too big to read on the phone.")
        val ext = name.substringAfterLast('.', "").lowercase()
        val type = cr.getType(uri).orEmpty()
        val text = cr.openInputStream(uri)?.use { input ->
            when {
                ext == "pdf" || type == "application/pdf" -> pdf(ctx, input, name)
                ext == "docx" -> office(input, Regex("word/(document|footnotes|endnotes)\\.xml"), name) { xml -> paragraphs(xml) }
                ext == "pptx" -> office(input, Regex("ppt/slides/slide(\\d+)\\.xml"), name, numbered = "Slide") { xml -> paragraphs(xml) }
                ext == "xlsx" -> xlsx(input, name)
                ext in setOf("doc", "xls", "ppt") -> throw MediaException("$name is an old Office format: save it as .${ext}x (or PDF) first.")
                // Text: only the start matters (at most 60,000 characters go along); 4 MB is plenty for that.
                else -> (readAtMost(input, 4L * 1024 * 1024) ?: throw MediaException("$name is a very large text file: only up to 4 MB can be read.")).toString(Charsets.UTF_8)
            }
        } ?: throw MediaException("Couldn't open $name.")
        val clean = text.replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
        if (clean.isBlank()) throw MediaException("No text found in $name (a scanned PDF? Then attach it as an image, or use OCR).")
        return PickedFile(name, clean.take(MAX_CHARS), clean.length > MAX_CHARS)
    }

    private fun pdf(ctx: Context, input: java.io.InputStream, name: String): String {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx.applicationContext)
        return try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { doc ->
                com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw MediaException("$name is password-protected.")
        }
    }

    /** The XML parts of an Office file that match [part], in order (slides by number), each turned into text. */
    private fun office(input: java.io.InputStream, part: Regex, name: String, numbered: String? = null, text: (String) -> String): String {
        val parts = ArrayList<Pair<Int, String>>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val m = part.matchEntire(e.name)
                if (m != null) parts += (m.groupValues.getOrNull(1)?.toIntOrNull() ?: parts.size) to text(part(z, name))
                e = z.nextEntry
            }
        }
        if (parts.isEmpty()) throw MediaException("$name doesn't look like a valid Office file.")
        return parts.sortedBy { it.first }.joinToString("\n\n") { (n, t) -> if (numbered != null) "$numbered $n:\n$t" else t }
    }

    /** Word and PowerPoint XML: paragraphs become lines, tabs stay tabs, the rest of the markup goes. */
    private fun paragraphs(xml: String) = unescape(
        xml.replace(Regex("</(w|a):p>"), "\n").replace(Regex("<w:tab/>|<w:tab [^>]*/>"), "\t").replace(Regex("<w:br[^>]*/>|<a:br[^>]*/>"), "\n")
            .replace(Regex("<[^>]+>"), ""),
    )

    /** Excel: each sheet as tab-separated rows, shared strings filled in. */
    private fun xlsx(input: java.io.InputStream, name: String): String {
        var shared = emptyList<String>()
        val sheets = ArrayList<Pair<Int, String>>()
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val xml = if (e.name == "xl/sharedStrings.xml" || e.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml"))) part(z, name) else null
                if (e.name == "xl/sharedStrings.xml" && xml != null) {
                    shared = Regex("(?s)<si>(.*?)</si>").findAll(xml).map { si ->
                        unescape(Regex("(?s)<t[^>]*>(.*?)</t>").findAll(si.groupValues[1]).joinToString("") { it.groupValues[1] })
                    }.toList()
                } else if (xml != null) {
                    sheets += (Regex("sheet(\\d+)").find(e.name)!!.groupValues[1].toInt()) to xml
                }
                e = z.nextEntry
            }
        }
        if (sheets.isEmpty()) throw MediaException("$name doesn't look like a valid Excel file.")
        val cell = Regex("(?s)<c ([^>]*?)(?:/>|>(.*?)</c>)")
        return sheets.sortedBy { it.first }.joinToString("\n\n") { (n, xml) ->
            "Sheet $n:\n" + Regex("(?s)<row[^>]*>(.*?)</row>").findAll(xml).map { row ->
                cell.findAll(row.groupValues[1]).map { c ->
                    val attrs = c.groupValues[1]
                    val body = c.groupValues[2]
                    val v = Regex("(?s)<v>(.*?)</v>").find(body)?.groupValues?.get(1)
                    when {
                        "t=\"s\"" in attrs -> v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        "t=\"inlineStr\"" in attrs -> unescape(Regex("(?s)<t[^>]*>(.*?)</t>").find(body)?.groupValues?.get(1) ?: "")
                        else -> unescape(v ?: "")
                    }
                }.joinToString("\t").trimEnd()
            }.filter { it.isNotBlank() }.joinToString("\n")
        }
    }

    private val NUMERIC = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        .replace(NUMERIC) { m ->
            val v = m.groupValues[1]
            (if (v.startsWith("x")) v.drop(1).toIntOrNull(16) else v.toIntOrNull())?.takeIf { Character.isValidCodePoint(it) }
                ?.let { String(Character.toChars(it)) } ?: m.value
        }
        .replace("&amp;", "&")
}

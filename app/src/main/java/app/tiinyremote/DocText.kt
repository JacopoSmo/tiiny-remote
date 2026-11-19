package app.tiinyremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** What the Text tab was given: a picture to read with the OCR model, PDF pages to read, or a document's own text. */
sealed class TextSource(val name: String) {
    class Picture(name: String, val image: PickedImage) : TextSource(name)
    class Pages(name: String, val pages: List<String>, val total: Int) : TextSource(name) // JPEG data URIs
    class Document(name: String, val text: String) : TextSource(name)
}

/**
 * Opens what the user picked in the Text tab, like Tiiny Studio: photos and scans go to the OCR model, PDFs page by
 * page, and documents (Word, PowerPoint, Excel, OpenDocument, EPUB, RTF, HTML, plain text) are read right away.
 */
object DocText {
    const val MAX_PAGES = 30

    val MIMES = arrayOf("image/*", "application/pdf", "text/*", "application/*")

    private val TEXT_EXT = setOf("txt", "md", "csv", "tsv", "json", "xml", "srt", "vtt", "log")
    private val HTML_EXT = setOf("html", "htm", "xhtml")
    private val ZIP_EXT = setOf("docx", "docm", "pptx", "ppsx", "xlsx", "xlsm", "odt", "odp", "ods", "epub")

    fun open(ctx: Context, uri: Uri): TextSource {
        var name = "document"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "pdf" || mime == "application/pdf" -> pdf(ctx, uri, name)
            ext in setOf("tif", "tiff") || mime == "image/tiff" ->
                throw MediaException("TIFF can't be opened on Android: save the page as PNG, JPG or PDF instead.")
            mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp", "heic", "heif", "gif", "bmp") ->
                TextSource.Picture(name, Inputs.image(ctx, uri))
            else -> {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw MediaException("Couldn't read that file.")
                val text = when {
                    ext in ZIP_EXT -> zipText(bytes, ext)
                    ext in HTML_EXT || mime == "text/html" -> html(String(bytes))
                    ext == "rtf" || mime.contains("rtf") -> rtf(String(bytes))
                    ext in setOf("doc", "xls", "ppt") -> throw MediaException("Old Office files (.$ext) can't be read here: save it as .${ext}x or PDF.")
                    ext in TEXT_EXT || mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") -> String(bytes)
                    else -> throw MediaException("That kind of file can't be read: use a picture, PDF or document.")
                }.trim()
                if (text.isBlank()) throw MediaException("There's no text in that document.")
                TextSource.Document(name, text)
            }
        }
    }

    /** Renders PDF pages white-backed at about 1536 px on the long side. */
    private fun pdf(ctx: Context, uri: Uri, name: String): TextSource.Pages {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw MediaException("Couldn't open that PDF.")
        pfd.use {
            PdfRenderer(it).use { r ->
                val n = minOf(r.pageCount, MAX_PAGES)
                val pages = (0 until n).map { i ->
                    r.openPage(i).use { page ->
                        val k = 1536.0 / maxOf(page.width, page.height)
                        val bmp = Bitmap.createBitmap((page.width * k).toInt().coerceAtLeast(1), (page.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        bmp.recycle()
                        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                }
                if (pages.isEmpty()) throw MediaException("That PDF has no pages.")
                return TextSource.Pages(name, pages, r.pageCount)
            }
        }
    }

    private fun zipText(bytes: ByteArray, ext: String): String {
        val parts = sortedMapOf<String, String>(compareBy({ it.length }, { it }))
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                val wanted = when (ext) {
                    "docx", "docm" -> n == "word/document.xml"
                    "pptx", "ppsx" -> Regex("ppt/slides/slide\\d+\\.xml").matches(n)
                    "xlsx", "xlsm" -> n == "xl/sharedStrings.xml"
                    "epub" -> Regex(".*\\.x?html?", RegexOption.IGNORE_CASE).matches(n)
                    else -> n == "content.xml"
                }
                if (wanted) parts[n] = String(z.readBytes())
            }
        }
        if (parts.isEmpty()) throw MediaException("Couldn't find the text in that document.")
        return parts.values.joinToString("\n\n") { if (ext == "epub") html(it) else xml(it) }
    }

    /** Office XML to text: paragraphs, rows and slides become lines. */
    private fun xml(s: String): String = unescape(
        s.replace(Regex("<w:tab/>|<text:tab/>"), "\t")
            .replace(Regex("<w:br/>|<a:br/>|<text:line-break/>"), "\n")
            .replace(Regex("</w:p>|</a:p>|</text:p>|</text:h>|</si>|</table:table-row>"), "\n")
            .replace(Regex("</table:table-cell>"), "\t")
            .replace(Regex("<[^>]+>"), ""),
    ).replace(Regex("\n{3,}"), "\n\n")

    private fun html(s: String): String =
        Html.fromHtml(s.replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), ""), Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('￼', ' ').replace(Regex("\n{3,}"), "\n\n")

    /** A rough RTF reader: drops groups like fonts and colours, control words, keeps the text. */
    private fun rtf(s: String): String {
        var t = s.replace(Regex("\\{\\\\(fonttbl|colortbl|stylesheet|info|\\*)[^{}]*(\\{[^{}]*\\}[^{}]*)*\\}"), "")
        t = t.replace(Regex("\\\\par[d]?\\b ?"), "\n").replace(Regex("\\\\tab\\b ?"), "\t")
        t = t.replace(Regex("\\\\'([0-9a-fA-F]{2})")) { it.groupValues[1].toInt(16).toChar().toString() }
        t = t.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "").replace(Regex("[{}]"), "")
        return t.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
}

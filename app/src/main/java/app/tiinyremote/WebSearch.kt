package app.tiinyremote

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Web search through the user's own SearXNG (on the NAS), and reading a page, for the chat's "Web search". */
object WebSearch {
    class Hit(val title: String, val url: String, val snippet: String)

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"

    private fun get(url: String, timeoutSec: Int, max: Int): Pair<Int, Pair<String, ByteArray>> {
        var u = URL(url)
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = timeoutSec * 1000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.5")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return code to ("" to ByteArray(0))
                    u = URL(u, loc)
                    return@repeat
                }
                val stream = if (code >= 400) c.errorStream else c.inputStream
                val bytes = stream?.use { s ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16384)
                    while (out.size() < max) {
                        val n = s.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: ByteArray(0)
                return code to ((c.contentType ?: "") to bytes)
            } finally {
                c.disconnect()
            }
        }
        throw MediaException("Too many redirects.")
    }

    fun search(base: String, query: String, count: Int = 6): List<Hit> {
        if (base.isBlank()) throw MediaException("Set your SearXNG address in Settings → Web search first.")
        val root = base.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
        val (code, res) = try {
            get("$root/search?q=${URLEncoder.encode(query, "UTF-8")}&format=json&safesearch=0", 25, 2_000_000)
        } catch (e: Exception) {
            throw MediaException("SearXNG isn't reachable at $root (${e.message}). At home or on the VPN?")
        }
        if (code == 403) throw MediaException("SearXNG refused JSON answers: add json under search → formats in its settings.yml.")
        if (code != 200) throw MediaException("SearXNG answered HTTP $code.")
        val o = try {
            JSONObject(String(res.second))
        } catch (e: Exception) {
            throw MediaException("SearXNG didn't send JSON: is this the right address?")
        }
        val list = o.optJSONArray("results") ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Hit>()
        for (i in 0 until list.length()) {
            val r = list.optJSONObject(i) ?: continue
            val url = r.optString("url")
            if (url.isBlank() || !seen.add(url)) continue
            out.add(Hit(r.optString("title").ifBlank { url }, url, r.optString("content").trim()))
            if (out.size >= count) break
        }
        return out
    }

    /** A web page as plain text, cut to `max` characters. */
    fun read(url: String, max: Int = 8000): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw MediaException("Only web addresses (http/https) can be opened.")
        val (code, res) = get(url, 25, 3_000_000)
        if (code != 200) throw MediaException("The page answered HTTP $code.")
        val (type, bytes) = res
        val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(type)?.groupValues?.get(1) ?: "UTF-8"
        val raw = try {
            String(bytes, charset(charset))
        } catch (e: Exception) {
            String(bytes)
        }
        val text = when {
            type.contains("html", true) || raw.trimStart().startsWith("<") -> {
                val body = raw.replace(Regex("(?is)<(script|style|noscript|svg|head|nav|footer|header|form)[^>]*>.*?</\\1>"), " ")
                Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY).toString().replace('￼', ' ')
            }
            type.startsWith("text/") || type.contains("json") || type.contains("xml") -> raw
            else -> throw MediaException("That isn't a web page (${type.substringBefore(';')}).")
        }
        val clean = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        return if (clean.length > max) clean.take(max) + "\n[… cut, the page goes on]" else clean
    }
}

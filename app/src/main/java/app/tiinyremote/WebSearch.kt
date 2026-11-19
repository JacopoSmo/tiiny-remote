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

    private fun get(
        url: String, timeoutSec: Int, max: Int, post: String? = null, postType: String = "application/x-www-form-urlencoded",
        headers: Map<String, String> = emptyMap(), connectSec: Int = 10,
    ): Pair<Int, Pair<String, ByteArray>> {
        var u = URL(url)
        var body = post
        repeat(5) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = connectSec * 1000
            c.readTimeout = timeoutSec * 1000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.5")
            c.setRequestProperty("Accept-Language", "en,de;q=0.8")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            try {
                body?.let { b ->
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", postType)
                    c.outputStream.use { it.write(b.toByteArray()) }
                }
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return code to ("" to ByteArray(0))
                    u = URL(u, loc)
                    body = null
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

    /** Hits and where they came from. */
    class Found(val hits: List<Hit>, val via: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * Searches with what's set up: your SearXNG when it answers (at home or on the VPN), otherwise straight from the
     * phone: Tavily if you added a key, else DuckDuckGo, else Wikipedia. mode: auto, searx (only SearXNG) or phone.
     */
    fun search(s: AppSettings, query: String, count: Int = 6): Found {
        val errors = ArrayList<String>()
        val useSearx = s.searchMode != "phone" && s.searxUrl.isNotBlank()
        if (useSearx) {
            try {
                return Found(searx(s.searxUrl, query, count, if (s.searchMode == "searx") 10 else 4), "SearXNG")
            } catch (e: Exception) {
                if (s.searchMode == "searx") throw e
                errors.add("SearXNG: ${e.message}")
            }
        }
        if (s.searchMode == "searx") throw MediaException("Set your SearXNG address in Settings → Web search first.")
        if (s.tavilyKey.isNotBlank()) {
            try {
                return Found(tavily(s.tavilyKey, query, count), "Tavily")
            } catch (e: Exception) {
                errors.add("Tavily: ${e.message}")
            }
        }
        try {
            val hits = duckDuckGo(query, count)
            if (hits.isNotEmpty()) return Found(hits, "DuckDuckGo")
            errors.add("DuckDuckGo: no results")
        } catch (e: Exception) {
            errors.add("DuckDuckGo: ${e.message}")
        }
        try {
            return Found(wikipedia(query, count), "Wikipedia")
        } catch (e: Exception) {
            errors.add("Wikipedia: ${e.message}")
        }
        throw MediaException("Web search failed (${errors.joinToString("; ")}). Is the phone online?")
    }

    fun tavily(key: String, query: String, count: Int): List<Hit> {
        val body = JSONObject().put("query", query).put("max_results", count).put("search_depth", "basic").toString()
        val (code, res) = get("https://api.tavily.com/search", 30, 2_000_000, body, "application/json", mapOf("Authorization" to "Bearer ${key.trim()}"))
        if (code == 401 || code == 403) throw MediaException("the key was refused")
        if (code == 432 || code == 429) throw MediaException("this month's free searches are used up")
        if (code != 200) throw MediaException("HTTP $code")
        val list = JSONObject(String(res.second)).optJSONArray("results") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val r = list.optJSONObject(i) ?: return@mapNotNull null
            Hit(r.optString("title").ifBlank { r.optString("url") }, r.optString("url"), r.optString("content").trim())
        }.filter { it.url.isNotBlank() }
    }

    private fun htmlText(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    /** DuckDuckGo's plain HTML results page (no key; unofficial, so it can stop working or ask for a captcha). */
    fun duckDuckGo(query: String, count: Int): List<Hit> {
        val (code, res) = get("https://html.duckduckgo.com/html/", 20, 1_500_000, "q=${enc(query)}&kl=wt-wt")
        if (code != 200) throw MediaException("HTTP $code")
        val html = String(res.second)
        if (html.contains("anomaly-modal") || html.contains("challenge-form")) throw MediaException("it asked for a captcha")
        val links = Regex("<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val snippets = Regex("<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { htmlText(it.groupValues[1]) }.toList()
        val out = ArrayList<Hit>()
        for ((i, m) in links.withIndex()) {
            var href = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
            Regex("[?&]uddg=([^&]+)").find(href)?.let { href = java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }
            if (href.startsWith("//")) href = "https:$href"
            if (!href.startsWith("http") || href.contains("duckduckgo.com/y.js")) continue // ads
            out.add(Hit(htmlText(m.groupValues[2]), href, snippets.getOrElse(i) { "" }))
            if (out.size >= count) break
        }
        return out
    }

    /** Wikipedia's search API: always free, a last resort for facts. */
    fun wikipedia(query: String, count: Int): List<Hit> {
        val out = ArrayList<Hit>()
        for (lang in listOf("en", "de")) {
            val (code, res) = get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=$count&srsearch=${enc(query)}", 20, 1_000_000)
            if (code != 200) continue
            val list = JSONObject(String(res.second)).optJSONObject("query")?.optJSONArray("search") ?: continue
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                val t = r.optString("title")
                out.add(Hit("$t – Wikipedia", "https://$lang.wikipedia.org/wiki/${enc(t.replace(' ', '_'))}", htmlText(r.optString("snippet"))))
            }
            if (out.isNotEmpty()) break
        }
        if (out.isEmpty()) throw MediaException("no results")
        return out.take(count)
    }

    fun searx(base: String, query: String, count: Int = 6, connectSec: Int = 10): List<Hit> {
        if (base.isBlank()) throw MediaException("Set your SearXNG address in Settings → Web search first.")
        val root = base.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
        val (code, res) = try {
            get("$root/search?q=${enc(query)}&format=json&safesearch=0", 25, 2_000_000, connectSec = connectSec)
        } catch (e: Exception) {
            throw MediaException("not reachable at $root (${e.message}); at home or on the VPN?")
        }
        if (code == 403) throw MediaException("it refused JSON answers: add json under search → formats in its settings.yml")
        if (code != 200) throw MediaException("HTTP $code")
        val o = try {
            JSONObject(String(res.second))
        } catch (e: Exception) {
            throw MediaException("no JSON came back: is this the right address?")
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

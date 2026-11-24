package app.tiinyremote

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Web search through the user's own SearXNG (on the NAS), and reading a page, for the chat's "Web search". */
object WebSearch {
    class Hit(val title: String, val url: String, val snippet: String)

    /** Only web addresses: a result pointing at intent:// or another app's scheme would open that app when tapped. */
    private fun web(url: String) = url.startsWith("https://", true) || url.startsWith("http://", true)

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"

    private fun get(
        url: String, timeoutSec: Int, max: Int, post: String? = null, postType: String = "application/x-www-form-urlencoded",
        headers: Map<String, String> = emptyMap(), connectSec: Int = 10, publicOnly: Boolean = false,
    ): Pair<Int, Pair<String, ByteArray>> {
        var u = URL(url)
        var body = post
        val firstHost = u.host
        repeat(5) {
            if (publicOnly) checkPublic(u)
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = connectSec * 1000
            c.readTimeout = timeoutSec * 1000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.5")
            c.setRequestProperty("Accept-Language", "en,de;q=0.8")
            // Keys (Tavily's) go only to the site they're for, never to where it redirects.
            if (u.host.equals(firstHost, true)) for ((k, v) in headers) c.setRequestProperty(k, v)
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
    class Found(val hits: List<Hit>, val via: String, val skipped: List<String> = emptyList())

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
                return Found(searx(s.searxUrl, query, count, if (s.searchMode == "searx") 10 else 4), "SearXNG", errors)
            } catch (e: Exception) {
                if (s.searchMode == "searx") throw e
                errors.add("SearXNG: ${e.message}")
            }
        }
        if (s.searchMode == "searx") throw MediaException("Set your SearXNG address in Settings → Web search first.")
        if (s.tavilyKey.isNotBlank()) {
            try {
                return Found(tavily(s.tavilyKey, query, count, false), "Tavily")
            } catch (e: Exception) {
                errors.add("Tavily: ${e.message}")
            }
        }
        for ((name, fn) in listOf<Pair<String, () -> List<Hit>>>(
            "Bing" to { bingRss("https://www.bing.com/search?format=rss&setlang=en&q=${enc(query)}", count) },
            "DuckDuckGo" to { duckDuckGo(query, count) },
            "DuckDuckGo Lite" to { duckLite(query, count) },
        )) {
            try {
                val hits = fn()
                if (hits.isNotEmpty()) return Found(hits, name, errors)
                errors.add("$name: no results")
            } catch (e: Exception) {
                errors.add("$name: ${e.message}")
            }
        }
        try {
            return Found(wikipedia(query, count), "Wikipedia", errors)
        } catch (e: Exception) {
            errors.add("Wikipedia: ${e.message}")
        }
        throw MediaException("Web search failed (${errors.joinToString("; ")}). Is the phone online?")
    }

    /**
     * Current news: SearXNG's news category when reachable, Tavily's news search with a key, else Bing News and
     * Google News feeds. topic blank = top stories; region "ch", "de", "us", "uk" or blank for world (English).
     */
    fun news(s: AppSettings, topic: String, region: String, count: Int = 10): Found {
        val errors = ArrayList<String>()
        val q = topic.trim()
        // SearXNG's news of the day, else of the week; only a handful (2 stray items, say) goes on to the other sources.
        var few: List<Hit> = emptyList()
        if (s.searchMode != "phone" && s.searxUrl.isNotBlank() && q.isNotEmpty()) {
            try {
                for (range in listOf("day", "week")) {
                    val hits = searx(s.searxUrl, q, count, if (s.searchMode == "searx") 10 else 4, "&categories=news&time_range=$range")
                    if (hits.size >= 4) return Found(hits, "SearXNG news", errors)
                    if (hits.size > few.size) few = hits
                }
                errors.add("SearXNG: only ${few.size} news items")
            } catch (e: Exception) {
                errors.add("SearXNG: ${e.message}")
            }
        }
        if (s.searchMode == "searx") {
            if (few.isNotEmpty()) return Found(few, "SearXNG news", errors)
            throw MediaException("SearXNG had no news (${errors.joinToString("; ")}).")
        }
        if (s.tavilyKey.isNotBlank()) {
            try {
                val hits = tavily(s.tavilyKey, q.ifEmpty { "top news today" }, count, true)
                if (hits.isNotEmpty()) return Found(hits, "Tavily news", errors)
            } catch (e: Exception) {
                errors.add("Tavily: ${e.message}")
            }
        }
        val r = region.trim().lowercase()
        val (hl, gl) = when (r) {
            "ch", "switzerland", "schweiz" -> "de" to "CH"
            "de", "germany" -> "de" to "DE"
            "at", "austria" -> "de" to "AT"
            "uk", "gb" -> "en" to "GB"
            "us", "usa" -> "en" to "US"
            "fr", "france" -> "fr" to "FR"
            "it", "italy" -> "it" to "IT"
            else -> "en" to "US"
        }
        val world = r.isEmpty() || r == "world"
        for ((name, fn) in listOf<Pair<String, () -> List<Hit>>>(
            "Google News" to {
                val base = if (q.isEmpty()) "https://news.google.com/rss" + (if (world) "/headlines/section/topic/WORLD" else "")
                else "https://news.google.com/rss/search"
                val qs = (if (q.isEmpty()) "" else "q=${enc("$q when:1d")}&") + "hl=$hl-$gl&gl=$gl&ceid=$gl:$hl"
                rss("$base?$qs", count, google = true)
            },
            "Bing News" to {
                bingRss("https://www.bing.com/news/search?format=rss&cc=$gl&setlang=$hl&q=${enc(q.ifEmpty { if (world) "world news" else "top stories" })}", count)
            },
        )) {
            try {
                val hits = fn()
                if (hits.isNotEmpty()) return Found(hits, name, errors)
                errors.add("$name: no results")
            } catch (e: Exception) {
                errors.add("$name: ${e.message}")
            }
        }
        if (few.isNotEmpty()) return Found(few, "SearXNG news", errors)
        throw MediaException("Couldn't get news (${errors.joinToString("; ")}). Is the phone online?")
    }

    private fun tag(item: String, name: String): String {
        val m = Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(item) ?: return ""
        return m.groupValues[1].replace(Regex("^\\s*<!\\[CDATA\\[|]]>\\s*$"), "").trim()
    }

    /** An RSS feed's items. Google News titles end in " - Source"; its links are redirects, so the source goes in the snippet. */
    private fun rss(url: String, count: Int, google: Boolean = false): List<Hit> {
        val (code, res) = get(url, 20, 2_000_000)
        if (code != 200) throw MediaException("HTTP $code")
        val xml = String(res.second)
        if (!xml.contains("<rss") && !xml.contains("<feed")) throw MediaException("no feed came back")
        return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).take(count).map { m ->
            val it = m.groupValues[1]
            var link = htmlText(tag(it, "link"))
            Regex("[?&]url=([^&]+)").find(link)?.let { u -> link = java.net.URLDecoder.decode(u.groupValues[1], "UTF-8") }
            val date = tag(it, "pubDate")
            val src = htmlText(tag(it, "source")).ifEmpty { htmlText(tag(it, "News:Source")) }
            val desc = if (google) "" else htmlText(tag(it, "description"))
            Hit(htmlText(tag(it, "title")), link, listOf(src, date, desc).filter { s -> s.isNotBlank() }.joinToString(" · "))
        }.filter { it.title.isNotBlank() && web(it.url) }.toList()
    }

    /** Bing's RSS answers (web or news); news links are click-redirects carrying the real address. */
    private fun bingRss(url: String, count: Int) = rss(url, count)

    /** DuckDuckGo Lite: a second, simpler page to try. */
    fun duckLite(query: String, count: Int): List<Hit> {
        val (code, res) = get("https://lite.duckduckgo.com/lite/", 20, 1_500_000, "q=${enc(query)}&kl=wt-wt")
        if (code != 200) throw MediaException("HTTP $code")
        val html = String(res.second)
        if (html.contains("anomaly") || html.contains("challenge")) throw MediaException("it asked for a captcha")
        val links = Regex("<a[^>]+href=\"([^\"]+)\"[^>]+class=['\"]result-link['\"][^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val snippets = Regex("<td[^>]+class=['\"]result-snippet['\"][^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(html).map { htmlText(it.groupValues[1]) }.toList()
        val out = ArrayList<Hit>()
        for ((i, m) in links.withIndex()) {
            var href = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
            Regex("[?&]uddg=([^&]+)").find(href)?.let { href = java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }
            if (href.startsWith("//")) href = "https:$href"
            if (!web(href) || href.contains("duckduckgo.com/y.js")) continue
            out.add(Hit(htmlText(m.groupValues[2]), href, snippets.getOrElse(i) { "" }))
            if (out.size >= count) break
        }
        return out
    }

    fun tavily(key: String, query: String, count: Int, news: Boolean): List<Hit> {
        val o = JSONObject().put("query", query).put("max_results", count).put("search_depth", "basic")
        if (news) o.put("topic", "news").put("time_range", "day")
        val body = o.toString()
        val (code, res) = get("https://api.tavily.com/search", 30, 2_000_000, body, "application/json", mapOf("Authorization" to "Bearer ${key.trim()}"))
        if (code == 401 || code == 403) throw MediaException("the key was refused")
        if (code == 432 || code == 429) throw MediaException("this month's free searches are used up")
        if (code != 200) throw MediaException("HTTP $code")
        val list = JSONObject(String(res.second)).optJSONArray("results") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val r = list.optJSONObject(i) ?: return@mapNotNull null
            Hit(r.optString("title").ifBlank { r.optString("url") }, r.optString("url"),
                listOf(r.optString("published_date"), r.optString("content").trim()).filter { it.isNotBlank() && it != "null" }.joinToString(" · "))
        }.filter { web(it.url) }
    }

    private fun htmlText(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    /** DuckDuckGo's plain HTML results page (no key; unofficial, so it can stop working or ask for a captcha). */
    fun duckDuckGo(query: String, count: Int): List<Hit> {
        val (code, res) = get("https://html.duckduckgo.com/html/", 20, 1_500_000, "q=${enc(query)}&kl=wt-wt")
        if (code == 202) throw MediaException("it refused the app (HTTP 202, a bot check)")
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
            if (!web(href) || href.contains("duckduckgo.com/y.js")) continue // ads
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

    fun searx(base: String, query: String, count: Int = 6, connectSec: Int = 10, extra: String = ""): List<Hit> {
        if (base.isBlank()) throw MediaException("Set your SearXNG address in Settings → Web search first.")
        val root = base.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
        val (code, res) = try {
            get("$root/search?q=${enc(query)}&format=json&safesearch=0$extra", 25, 2_000_000, connectSec = connectSec)
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
            if (!web(url) || !seen.add(url)) continue
            out.add(Hit(r.optString("title").ifBlank { url }, url, r.optString("content").trim()))
            if (out.size >= count) break
        }
        return out
    }

    /**
     * The chat model chooses which pages to open, and a web page could talk it into trying addresses on your home
     * network (the router, the NAS, the Tiiny). So open_page only goes to the public internet: names that resolve to
     * private, local or loopback addresses are refused, at every redirect too.
     */
    private fun checkPublic(u: URL) {
        if (u.protocol != "http" && u.protocol != "https") throw MediaException("Only web addresses (http/https) can be opened.")
        val host = u.host.trim('[', ']')
        if (host.isBlank() || host.endsWith(".local", true) || host.equals("localhost", true) || !host.contains('.') && !host.contains(':')) {
            throw MediaException("Pages on your own network can't be opened from the chat.")
        }
        val addrs = try {
            java.net.InetAddress.getAllByName(host)
        } catch (e: Exception) {
            throw MediaException("Couldn't find $host.")
        }
        for (a in addrs) {
            val b = a.address
            val ula = b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC // IPv6 fc00::/7
            val cgnat = b.size == 4 && (b[0].toInt() and 0xFF) == 100 && (b[1].toInt() and 0xC0) == 64 // 100.64/10
            // NAT64 (64:ff9b::/96) carries an IPv4 address inside: check that one too, so 64:ff9b::192.168.1.1 can't pass.
            val nat64 = b.size == 16 && b[0].toInt() == 0 && b[1].toInt() == 0x64 && (b[2].toInt() and 0xFF) == 0xFF &&
                (b[3].toInt() and 0xFF) == 0x9B && (4..11).all { b[it].toInt() == 0 }
            val inner = if (nat64) java.net.InetAddress.getByAddress(b.copyOfRange(12, 16)) else null
            val innerLocal = inner != null && (inner.isLoopbackAddress || inner.isSiteLocalAddress || inner.isLinkLocalAddress || inner.isAnyLocalAddress)
            if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress || ula || cgnat || innerLocal) {
                throw MediaException("Pages on your own network can't be opened from the chat.")
            }
        }
    }

    /** A web page as plain text, cut to `max` characters. */
    fun read(url: String, max: Int = 8000): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw MediaException("Only web addresses (http/https) can be opened.")
        val (code, res) = get(url, 25, 3_000_000, publicOnly = true)
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

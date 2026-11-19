package app.tiinyremote

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class UnlockException(message: String, val unsupported: Boolean = false) : Exception(message)

/**
 * Unlocks the Tiiny over the network, the way "tiiny login" does (as found in the Tiiny Control Panel project):
 * its password goes to the Tiiny's account service (Host: auth.api.tiiny.local on port 80), which answers with a
 * login key; the key then unlocks the device's data after a restart. The password goes only to the Tiiny.
 */
class DirectUnlock(tiinyUrl: String, private val store: Store, private val password: String) {
    private val url = URL(TiinyApi.normalize(tiinyUrl))
    private val host = url.host
    private val port = if (url.port > 0) url.port else 80

    private companion object {
        const val AUTH_HOST = "auth.api.tiiny.local"
    }

    /** Returns the data state the Tiiny reports afterwards ("unlocked", "locked", or "" if it didn't say). */
    fun unlock(log: (String) -> Unit): String {
        var key = store.authKey()
        var fresh = false
        if (key == null) {
            log("Logging in to the Tiiny with its password…")
            key = grantKey()
            fresh = true
        }
        var (code, state) = connect(key)
        if (code !in 200..299 && !fresh && password.isNotEmpty()) {
            log("The saved login key wasn't accepted (HTTP $code). Logging in again with the password…")
            key = grantKey()
            fresh = true
            val again = connect(key)
            code = again.first
            state = again.second
        }
        if (code !in 200..299) throw UnlockException("The Tiiny refused the login key (HTTP $code).")
        log("The Tiiny reports its data as: ${state.ifEmpty { "(no state given)" }}")
        if (state == "locked") {
            log("Unlocking…")
            val res = request("POST", "/api/v1/account/unlock_with_auth_key", JSONObject().put("auth_key", key), key, 60_000)
            if (res.first !in 200..299) {
                throw UnlockException("Unlocking failed (HTTP ${res.first}): ${message(res.second)}")
            }
            state = connect(key).second
            log("The Tiiny now reports: ${state.ifEmpty { "(no state given)" }}")
        }
        return state
    }

    private fun grantKey(): String {
        if (password.isEmpty()) throw UnlockException("Enter the Tiiny's password in Settings to unlock it directly.")
        val (code, text) = request("POST", "/api/v1/account/auth", JSONObject().put("password", password), null, 30_000)
        val data = parse(text)
        val key = data.optString("auth_key")
        if (code in 200..299 && key.isNotEmpty()) {
            store.setAuthKey(key)
            val user = data.optJSONObject("user_info")?.optString("user_name").orEmpty()
            ApiLog.add("Logged in to the Tiiny" + (if (user.isNotEmpty()) " as $user" else "") + "; login key saved (encrypted).")
            return key
        }
        if (data.optString("status").contains("activ", ignoreCase = true)) {
            throw UnlockException("This Tiiny isn't activated yet. Activate it with TiinyOS first.")
        }
        if (code == 404 || code == 405) {
            throw UnlockException("This Tiiny doesn't offer the password login (HTTP $code).", unsupported = true)
        }
        throw UnlockException("The Tiiny didn't accept the password (HTTP $code): ${message(text)}")
    }

    /** Registers the key with the Tiiny and returns (HTTP status, data_state). */
    private fun connect(key: String): Pair<Int, String> {
        val (code, text) = request("POST", "/api/v1/connect", JSONObject().put("auth_key", key), key, 15_000)
        return code to parse(text).optString("data_state")
    }

    private fun parse(text: String): JSONObject = try {
        JSONObject(text.trim())
    } catch (e: Exception) {
        JSONObject()
    }

    private fun message(text: String): String {
        val o = parse(text)
        for (k in listOf("msg", "message", "detail", "error")) {
            val v = o.opt(k)
            if (v is String && v.isNotBlank()) return v.take(200)
            if (v is JSONObject) v.optString("message").takeIf { it.isNotBlank() }?.let { return it.take(200) }
        }
        return text.trim().take(200)
    }

    /**
     * A plain HTTP/1.1 request over a socket, so the Host header is exactly "auth.api.tiiny.local"
     * (the Tiiny's port 80 picks the service by that name). Never logs the password or the key.
     */
    private fun request(method: String, path: String, body: JSONObject?, bearer: String?, timeoutMs: Int): Pair<Int, String> {
        val payload = body?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 8_000)
                s.soTimeout = timeoutMs
                val head = buildString {
                    append("$method $path HTTP/1.1\r\n")
                    append("Host: $AUTH_HOST\r\n")
                    append("Accept: application/json\r\n")
                    append("User-Agent: TiinyRemote\r\n")
                    append("Connection: close\r\n")
                    if (!bearer.isNullOrEmpty()) append("Authorization: Bearer $bearer\r\n")
                    if (body != null) append("Content-Type: application/json\r\n")
                    append("Content-Length: ${payload.size}\r\n\r\n")
                }
                val out = s.getOutputStream()
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.write(payload)
                out.flush()
                val (code, text) = readResponse(s.getInputStream().readBytes())
                ApiLog.add("$method $AUTH_HOST$path\n  -> HTTP $code: ${redact(text).take(500)}")
                return code to text
            }
        } catch (e: UnlockException) {
            throw e
        } catch (e: Exception) {
            ApiLog.add("$method $AUTH_HOST$path\n  -> ${e.javaClass.simpleName}: ${e.message}")
            throw UnlockException("Can't reach the Tiiny at $host:$port (${e.message ?: e.javaClass.simpleName}).")
        }
    }

    /** Keys never go to the log: the "auth_key" value in an answer is shown as ***. */
    private fun redact(text: String) = text.replace(Regex("(\"auth_key\"\\s*:\\s*\")[^\"]*\""), "$1***\"")

    private fun readResponse(raw: ByteArray): Pair<Int, String> {
        val sep = indexOf(raw, "\r\n\r\n".toByteArray())
        if (sep < 0) throw UnlockException("The Tiiny sent an incomplete answer.")
        val head = String(raw, 0, sep, Charsets.ISO_8859_1)
        val lines = head.split("\r\n")
        val code = lines.first().split(" ").getOrNull(1)?.toIntOrNull()
            ?: throw UnlockException("The Tiiny sent an unexpected answer.")
        val chunked = lines.drop(1).any {
            it.startsWith("Transfer-Encoding:", ignoreCase = true) && it.contains("chunked", ignoreCase = true)
        }
        var body = raw.copyOfRange(sep + 4, raw.size)
        if (chunked) body = dechunk(body)
        return code to String(body, Charsets.UTF_8)
    }

    private fun dechunk(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < b.size) {
            val lineEnd = indexOf(b, "\r\n".toByteArray(), i)
            if (lineEnd < 0) break
            val size = String(b, i, lineEnd - i, Charsets.ISO_8859_1).substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            val start = lineEnd + 2
            val end = minOf(start + size, b.size)
            out.write(b, start, end - start)
            i = end + 2
        }
        return out.toByteArray()
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}

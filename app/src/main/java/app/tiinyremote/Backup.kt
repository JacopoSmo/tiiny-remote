package app.tiinyremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Settings → Backup: everything of the app's (settings, chats, the Gallery, voices, Code projects, automations) as one
 * file, encrypted on the phone with a backup password the NAS never sees, kept by the NAS agent (the newest five).
 *
 * The file: "TRB1", the key's salt and rounds, then the zipped data in pieces of 1 MB, each sealed with AES-GCM (its
 * number and whether it's the last are sealed in too, so a piece can't be swapped, dropped or the end cut off).
 * Passwords and keys are stored encrypted with a key that never leaves this phone, so they travel in the backup as
 * plain text inside the encryption and are sealed again with the new phone's key when restored.
 */
object Backup {
    private val MAGIC = "TRB1".toByteArray()
    private const val ROUNDS = 210_000
    private const val PIECE = 1 shl 20

    /** The secrets, by preference file and key, that only this phone's key can read. */
    private val SECRETS = mapOf(
        "tiiny_remote" to listOf("api_key", "nas_password", "tiiny_password", "tavily_key", "auth_key"),
        "connectors" to listOf("imap_password", "ics_url", "nas_token"),
        "code" to listOf("gh_token"),
    )

    private fun key(password: CharArray, salt: ByteArray, rounds: Int) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, rounds, 256)).encoded, "AES",
    )

    private class SealOut(private val out: DataOutputStream, private val key: SecretKeySpec, private val head: ByteArray, private val base: ByteArray) : OutputStream() {
        private val buf = ByteArray(PIECE)
        private var n = 0
        private var index = 0
        private fun seal(last: Boolean) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, base + java.nio.ByteBuffer.allocate(4).putInt(index).array()))
            c.updateAAD(head + java.nio.ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array())
            val ct = c.doFinal(buf, 0, n)
            out.writeByte(if (last) 1 else 0)
            out.writeInt(ct.size)
            out.write(ct)
            index++
            n = 0
        }
        override fun write(b: Int) { if (n == PIECE) seal(false); buf[n++] = b.toByte() }
        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var l = len
            while (l > 0) {
                if (n == PIECE) seal(false)
                val k = minOf(l, PIECE - n)
                System.arraycopy(b, o, buf, n, k)
                n += k; o += k; l -= k
            }
        }
        override fun close() { seal(true); out.close() }
    }

    private class OpenIn(private val inp: DataInputStream, private val key: SecretKeySpec, private val head: ByteArray, private val base: ByteArray) : InputStream() {
        private var buf = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var ended = false
        private fun next(): Boolean {
            if (ended) return false
            val last = inp.readByte().toInt() == 1
            val len = inp.readInt()
            if (len < 16 || len > PIECE + 16) throw MediaException("This backup is damaged.")
            val ct = ByteArray(len).also { inp.readFully(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, base + java.nio.ByteBuffer.allocate(4).putInt(index).array()))
            c.updateAAD(head + java.nio.ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array())
            buf = try {
                c.doFinal(ct)
            } catch (e: javax.crypto.AEADBadTagException) {
                throw MediaException(if (index == 0) "Wrong backup password." else "This backup is damaged.")
            }
            pos = 0
            index++
            ended = last
            return true
        }
        override fun read(): Int {
            while (pos >= buf.size) if (!next()) return -1
            return buf[pos++].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (pos >= buf.size) if (!next()) return -1
            val k = minOf(len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, k)
            pos += k
            return k
        }
    }

    private fun data(ctx: Context) = ctx.filesDir.parentFile!!

    /** Packs and encrypts everything into a file in the app's cache; [step] tells how far it got. */
    fun create(ctx: Context, password: CharArray, step: (String) -> Unit): File {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val base = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val head = MAGIC + java.nio.ByteBuffer.allocate(4).putInt(ROUNDS).array() + salt + base
        val out = File(ctx.cacheDir, "backup.trb")
        val dout = DataOutputStream(out.outputStream().buffered(1 shl 16))
        dout.write(head)
        ZipOutputStream(SealOut(dout, key(password, salt, ROUNDS), head, base)).use { zip ->
            zip.setLevel(1) // images and sound hardly shrink: quick beats small
            // The secrets, readable again on any phone (inside the encryption only).
            val secrets = JSONObject()
            for ((file, keys) in SECRETS) {
                val p = ctx.getSharedPreferences(file, Context.MODE_PRIVATE)
                val o = JSONObject()
                for (k in keys) Crypto.decrypt(p.getString(k, null))?.takeIf { it.isNotEmpty() }?.let { o.put(k, it) }
                secrets.put(file, o)
            }
            zip.putNextEntry(ZipEntry("secrets.json")); zip.write(secrets.toString().toByteArray()); zip.closeEntry()
            val root = data(ctx)
            for (top in listOf("files", "shared_prefs")) {
                val dir = File(root, top)
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(root).invariantSeparatorsPath
                    step(rel.substringAfter('/').substringBefore('/'))
                    zip.putNextEntry(ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zip, 1 shl 16) }
                    zip.closeEntry()
                }
            }
        }
        return out
    }

    /**
     * Decrypts and unpacks [file] into a waiting folder: everything is checked before anything of today's is
     * touched (a wrong password stops at the first piece). Then [swap] puts it in place.
     */
    fun unpack(ctx: Context, file: File, password: CharArray, step: (String) -> Unit): File {
        val inp = DataInputStream(file.inputStream().buffered(1 shl 16))
        val magic = ByteArray(4).also { inp.readFully(it) }
        if (!magic.contentEquals(MAGIC)) throw MediaException("This isn't a Tiiny Remote backup.")
        val rounds = inp.readInt()
        if (rounds < 100_000 || rounds > 5_000_000) throw MediaException("This backup is damaged.")
        val salt = ByteArray(16).also { inp.readFully(it) }
        val base = ByteArray(8).also { inp.readFully(it) }
        val head = MAGIC + java.nio.ByteBuffer.allocate(4).putInt(rounds).array() + salt + base
        val stage = File(data(ctx), "restore_stage").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(OpenIn(inp, key(password, salt, rounds), head, base)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    // Only what a backup holds, and nothing that could lead outside the app's folder.
                    val ok = name == "secrets.json" || (name.startsWith("files/") || name.startsWith("shared_prefs/")) &&
                        ".." !in name.split('/') && !name.startsWith("/")
                    if (!ok || e.isDirectory) continue
                    val f = File(stage, name)
                    if (!f.canonicalPath.startsWith(stage.canonicalPath + File.separator)) continue
                    step(name.substringAfter('/').substringBefore('/'))
                    f.parentFile?.mkdirs()
                    f.outputStream().use { zip.copyTo(it, 1 shl 16) }
                }
            }
        } catch (e: Exception) {
            stage.deleteRecursively()
            throw e
        }
        return stage
    }

    /**
     * Replaces the app's data with the unpacked backup. The secrets wait, sealed with this phone's key, until the app
     * opens again (then [applySecrets]); the app must close right after this.
     */
    fun swap(ctx: Context, stage: File) {
        val root = data(ctx)
        val secrets = File(stage, "secrets.json").takeIf { it.exists() }?.readText()
        for (top in listOf("files", "shared_prefs")) {
            val from = File(stage, top)
            if (!from.exists()) continue
            val to = File(root, top)
            to.deleteRecursively()
            if (!from.renameTo(to)) { from.copyRecursively(to, overwrite = true) }
        }
        if (secrets != null) File(root, "files/.restore_secrets").writeText(Crypto.encrypt(secrets))
        stage.deleteRecursively()
    }

    /** At the start after a restore: the backup's passwords and keys, sealed again with this phone's key. */
    fun applySecrets(ctx: Context) {
        val f = File(ctx.filesDir, ".restore_secrets")
        if (!f.exists()) return
        runCatching {
            val o = JSONObject(Crypto.decrypt(f.readText()) ?: "{}")
            for (file in o.keys()) {
                val p = ctx.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
                val s = o.getJSONObject(file)
                for (k in s.keys()) if ((SECRETS[file] ?: emptyList()).contains(k)) p.putString(k, Crypto.encrypt(s.getString(k)))
                p.commit()
            }
        }
        f.delete()
    }

    /** A name for a new backup: date and time, then the phone's model. */
    fun newName(): String = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.ROOT).format(java.util.Date()) +
        "_" + android.os.Build.MODEL.replace(Regex("[^A-Za-z0-9]+"), "-").take(24)

    data class Entry(val name: String, val size: Long, val time: Long)

    fun parseList(json: String): List<Entry> {
        val a = JSONObject(json).optJSONArray("backups") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { Entry(it.getString("name"), it.optLong("size"), it.optLong("time")) }
    }
}

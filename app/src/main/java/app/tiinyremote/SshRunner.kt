package app.tiinyremote

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream

data class SshResult(
    val ok: Boolean,
    val exitCode: Int?,
    val output: String,
    val error: String?,
)

/** Runs one command on the NAS over SSH with the saved username and password. */
object SshRunner {

    fun run(s: AppSettings, store: Store, command: String, timeoutSec: Int = 90): SshResult {
        val jsch = JSch()
        jsch.hostKeyRepository = TrustOnFirstUse(store, "${s.nasHost.trim()}:${s.nasPort}")
        var session: Session? = null
        try {
            session = jsch.getSession(s.nasUser.trim(), s.nasHost.trim(), s.nasPort)
            session.setPassword(s.nasPassword)
            session.userInfo = PasswordInfo(s.nasPassword)
            // "yes" makes JSch refuse a host key that differs from the one saved on first use.
            session.setConfig("StrictHostKeyChecking", "yes")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
            session.timeout = 20_000
            session.connect(15_000)

            val ch = session.openChannel("exec") as ChannelExec
            val out = ByteArrayOutputStream()
            ch.setCommand(command)
            ch.setInputStream(null)
            ch.setOutputStream(out)
            ch.setErrStream(out)
            ch.connect(10_000)

            val deadline = System.currentTimeMillis() + timeoutSec * 1000L
            while (!ch.isClosed && System.currentTimeMillis() < deadline) Thread.sleep(250)
            val timedOut = !ch.isClosed
            val exit = if (timedOut) null else ch.exitStatus
            ch.disconnect()

            val text = out.toString("UTF-8").trim()
            return SshResult(
                ok = !timedOut && exit == 0,
                exitCode = exit,
                output = text,
                error = when {
                    timedOut -> "No answer after $timeoutSec s. If \"tiiny login\" is waiting for input " +
                        "(first login), run it once in Termius."
                    exit != 0 && text.contains("command not found") ->
                        "The Tiiny CLI wasn't found on the NAS. Check the unlock command in Settings."
                    exit != 0 -> "The command ended with exit code $exit."
                    else -> null
                },
            )
        } catch (e: Exception) {
            return SshResult(false, null, "", friendly(e))
        } finally {
            session?.disconnect()
        }
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: e.toString()
        val all = m + " " + (e.cause?.toString() ?: "")
        return when {
            all.contains("HostKey has been changed", true) ->
                "The NAS's SSH key differs from the one seen at the first connection. If you reset or " +
                    "reinstalled the NAS, tap \"Forget saved NAS key\" in Settings; otherwise don't connect."
            all.contains("refused", true) ->
                "Connection refused: SSH looks switched off on the NAS. Switch it on in UGOS under " +
                    "Control Panel → Terminal (or check the port)."
            all.contains("Auth fail", true) || all.contains("Auth cancel", true) ->
                "Login failed: check the NAS username and password (the local NAS admin account)."
            all.contains("timeout", true) || all.contains("timed out", true) ||
                all.contains("NoRouteToHost", true) || all.contains("unreachable", true) ->
                "The NAS doesn't answer: check its IP, and that you're on home wifi or WireGuard."
            all.contains("UnknownHost", true) -> "Unknown NAS address: $m"
            else -> m
        }
    }

    private class PasswordInfo(private val pw: String) : UserInfo, UIKeyboardInteractive {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String = pw
        override fun promptPassword(message: String?): Boolean = true
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean = false
        override fun showMessage(message: String?) {}
        override fun promptKeyboardInteractive(
            destination: String?,
            name: String?,
            instruction: String?,
            prompt: Array<String>?,
            echo: BooleanArray?,
        ): Array<String> = Array(prompt?.size ?: 0) { pw }
    }

    /** Saves the NAS's host key the first time and refuses a different one afterwards. */
    private class TrustOnFirstUse(private val store: Store, private val id: String) : HostKeyRepository {
        override fun check(host: String?, key: ByteArray?): Int {
            if (key == null) return HostKeyRepository.NOT_INCLUDED
            val seen = Base64.encodeToString(key, Base64.NO_WRAP)
            val known = store.hostKey(id)
            return when {
                known == null -> {
                    store.setHostKey(id, seen)
                    HostKeyRepository.OK
                }
                known == seen -> HostKeyRepository.OK
                else -> HostKeyRepository.CHANGED
            }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) {}
        override fun remove(host: String?, type: String?) {}
        override fun remove(host: String?, type: String?, key: ByteArray?) {}
        override fun getKnownHostsRepositoryID(): String = "tiiny-remote"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }
}

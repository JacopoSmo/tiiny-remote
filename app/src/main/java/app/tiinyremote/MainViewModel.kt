package app.tiinyremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DeviceState { NOT_CONFIGURED, CHECKING, ONLINE, LOCKED, UNAUTHORIZED, UNREACHABLE, ERROR }

data class PendingSwap(val targetId: String, val targetName: String, val plan: Plan)

data class UiState(
    val settings: AppSettings,
    val device: DeviceState = DeviceState.CHECKING,
    val deviceMessage: String = "",
    val models: List<ModelInfo> = emptyList(),
    val npuUsed: Double = 0.0,
    /** Models with a load or unload request in flight. */
    val working: Set<String> = emptySet(),
    val swap: PendingSwap? = null,
    /** Models the Tiiny lists but refuses to load ("not found"), remembered across app starts. */
    val unavailable: Set<String> = emptySet(),
    val unlocking: Boolean = false,
    val log: List<String> = emptyList(),
    val toast: String? = null,
    val updatedAt: Long = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(UiState(store.load(), unavailable = store.unavailable()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private var lastAutoUnlock = 0L

    private val settings get() = _state.value.settings
    private fun api() = TiinyApi(settings.tiinyUrl, settings.apiKey)

    /** Runs while the app is on screen (see MainActivity): checks the Tiiny every 15 s, every 5 s while something changes. */
    suspend fun pollLoop() {
        while (true) {
            refresh()
            val s = _state.value
            val fast = s.device == DeviceState.LOCKED || s.unlocking || s.working.isNotEmpty() ||
                s.models.any { it.status == "loading" }
            delay(if (fast) 5_000 else 15_000)
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val st = settings
        if (!st.tiinyConfigured) {
            _state.update {
                it.copy(
                    device = DeviceState.NOT_CONFIGURED,
                    deviceMessage = "Enter the Tiiny's address and API key in Settings.",
                )
            }
            return
        }
        try {
            val cat = withContext(Dispatchers.IO) { api().catalog() }
            // A model that is running after all (loaded from TiinyOS, say) is no longer marked unavailable.
            val nowRunning = cat.models.filter { it.status == "running" }.map { it.id }.toSet()
            if (_state.value.unavailable.any { it in nowRunning }) setUnavailable(_state.value.unavailable - nowRunning)
            _state.update {
                it.copy(
                    device = DeviceState.ONLINE,
                    deviceMessage = "",
                    models = cat.models,
                    npuUsed = cat.npuUsed,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        } catch (e: TiinyException) {
            val ds = when (e.kind) {
                TiinyException.Kind.LOCKED -> DeviceState.LOCKED
                TiinyException.Kind.UNAUTHORIZED -> DeviceState.UNAUTHORIZED
                TiinyException.Kind.UNREACHABLE -> DeviceState.UNREACHABLE
                TiinyException.Kind.NOT_FOUND, TiinyException.Kind.HTTP -> DeviceState.ERROR
            }
            _state.update { it.copy(device = ds, deviceMessage = e.message ?: "") }
            if (ds == DeviceState.LOCKED) maybeAutoUnlock()
        }
    }

    private fun maybeAutoUnlock() {
        val s = _state.value
        val now = System.currentTimeMillis()
        // At most one automatic try every 5 minutes, so a wrong password doesn't hammer the NAS.
        if (s.settings.autoUnlock && canUnlock(s.settings) && !s.unlocking && now - lastAutoUnlock > 5 * 60_000) {
            lastAutoUnlock = now
            unlock(automatic = true)
        }
    }

    // ---------- models ----------

    fun load(model: ModelInfo) {
        viewModelScope.launch {
            setWorking(model.id, true)
            try {
                val plan = withContext(Dispatchers.IO) { api().plan(model.id) }
                if (plan.unloadRequired && plan.candidateId != null) {
                    _state.update { it.copy(swap = PendingSwap(model.id, model.name, plan)) }
                    return@launch
                }
                withContext(Dispatchers.IO) { api().launch(model.id) }
                toast("Loading ${model.name}…")
            } catch (e: TiinyException) {
                ApiLog.add("Loading ${model.id} failed: ${e.message}")
                if (e.kind == TiinyException.Kind.NOT_FOUND) {
                    setUnavailable(_state.value.unavailable + model.id)
                    toast("${model.name} isn't available on the Tiiny. If it's a leftover copy, delete it in TiinyOS.")
                } else {
                    toast(e.message ?: "Loading failed")
                }
            } finally {
                setWorking(model.id, false)
                refresh()
            }
        }
    }

    /** Unloads the model the Tiiny suggested, then tries the load again (one unload may not be enough). */
    fun confirmSwap() {
        val swap = _state.value.swap ?: return
        _state.update { it.copy(swap = null) }
        val victim = swap.plan.candidateId ?: return
        viewModelScope.launch {
            setWorking(swap.targetId, true)
            try {
                withContext(Dispatchers.IO) { api().stop(victim) }
            } catch (e: TiinyException) {
                toast(e.message ?: "Unloading failed")
                setWorking(swap.targetId, false)
                return@launch
            }
            setWorking(swap.targetId, false)
            refresh()
            val target = _state.value.models.firstOrNull { it.id == swap.targetId }
                ?: ModelInfo(swap.targetId, swap.targetName, "", null, null, "stopped", false)
            load(target)
        }
    }

    fun cancelSwap() {
        _state.update { it.copy(swap = null) }
    }

    fun unload(model: ModelInfo) {
        viewModelScope.launch {
            setWorking(model.id, true)
            try {
                withContext(Dispatchers.IO) { api().stop(model.id) }
                toast("${model.name} unloaded")
            } catch (e: TiinyException) {
                ApiLog.add("Unloading ${model.id} failed: ${e.message}")
                toast(e.message ?: "Unloading failed")
            } finally {
                setWorking(model.id, false)
                refresh()
            }
        }
    }

    /** "Try again" on a model marked unavailable. */
    fun retry(model: ModelInfo) {
        setUnavailable(_state.value.unavailable - model.id)
        load(model)
    }

    private fun setUnavailable(ids: Set<String>) {
        store.setUnavailable(ids)
        _state.update { it.copy(unavailable = ids) }
    }

    private fun setWorking(id: String, on: Boolean) {
        _state.update { it.copy(working = if (on) it.working + id else it.working - id) }
    }

    // ---------- unlock over SSH ----------

    private fun canUnlock(st: AppSettings) = st.directConfigured || st.nasConfigured

    /**
     * Unlocks the Tiiny after a restart: directly with its password when one is saved (the way "tiiny login" does it),
     * otherwise, or if that fails, through the NAS over SSH. Then waits until the Tiiny answers again.
     */
    fun unlock(automatic: Boolean = false) {
        if (_state.value.unlocking) return
        val st = settings
        if (!canUnlock(st)) {
            toast("Enter the Tiiny's password (or the NAS login) in Settings first.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(unlocking = true) }
            if (automatic) log("Tiiny is locked: unlocking automatically…")
            var ok = false

            if (st.directConfigured) {
                log("Unlocking directly with the Tiiny's password…")
                val r = withContext(Dispatchers.IO) {
                    runCatching { DirectUnlock(st.tiinyUrl, store, st.tiinyPassword).unlock { line -> log("  $line") } }
                }
                r.onSuccess { ok = true }
                r.onFailure { e ->
                    log("Direct unlock failed: ${e.message}")
                    if (st.nasConfigured) log("Trying through the NAS instead…")
                }
            }

            if (!ok && st.nasConfigured) {
                log("Connecting to the NAS…")
                val res = withContext(Dispatchers.IO) { SshRunner.run(st, store, st.unlockCommand) }
                if (res.output.isNotBlank()) res.output.lines().filter { it.isNotBlank() }.forEach { log("  $it") }
                when {
                    res.output.contains("Password:", ignoreCase = true) && res.error != null ->
                        log(
                            "Unlock through the NAS failed: \"tiiny login\" asked for the Tiiny's password, which it " +
                                "can't be given over this connection. Enter the Tiiny's password in Settings to unlock directly."
                        )
                    res.error != null -> log("Unlock through the NAS failed: ${res.error}")
                    else -> ok = true
                }
            }

            if (!ok) {
                _state.update { it.copy(unlocking = false) }
                if (!automatic) toast("Unlock failed: see the Unlock tab")
                return@launch
            }
            log("Login done. Waiting for the Tiiny's services (usually up to 2 minutes)…")
            val deadline = System.currentTimeMillis() + 4 * 60_000
            var back = false
            while (System.currentTimeMillis() < deadline) {
                delay(5_000)
                refresh()
                if (_state.value.device == DeviceState.ONLINE) {
                    back = true
                    break
                }
            }
            log(if (back) "Tiiny is unlocked. Load a model to start chatting." else "The Tiiny still doesn't answer after 4 minutes. Try again, or open TiinyOS on a computer.")
            _state.update { it.copy(unlocking = false) }
            toast(if (back) "Tiiny unlocked" else "Tiiny still locked")
        }
    }

    /** Logs in to the NAS and checks that the Tiiny CLI is there, without unlocking anything. */
    fun testNas() {
        val st = settings
        if (!st.nasConfigured) {
            toast("Enter the NAS address, username and password first.")
            return
        }
        viewModelScope.launch {
            log("Testing the NAS login…")
            val cmd = ". ~/.profile >/dev/null 2>&1; export PATH=\"\$HOME/.local/bin:\$PATH\"; " +
                "echo SSH login OK; command -v tiiny >/dev/null && echo Tiiny CLI found || echo Tiiny CLI NOT found"
            val res = withContext(Dispatchers.IO) { SshRunner.run(st, store, cmd, timeoutSec = 30) }
            if (res.output.isNotBlank()) res.output.lines().filter { it.isNotBlank() }.forEach { log("  $it") }
            res.error?.let { log("Test failed: $it") }
            toast(
                when {
                    res.error != null -> "NAS test failed: see the Unlock tab"
                    res.output.contains("NOT found") -> "SSH works, but the Tiiny CLI wasn't found"
                    else -> "NAS login works and the Tiiny CLI is there"
                }
            )
        }
    }

    fun clearLog() {
        _state.update { it.copy(log = emptyList()) }
    }

    private fun log(line: String) {
        val entry = "${time.format(Date())}  $line"
        _state.update { it.copy(log = (it.log + entry).takeLast(200)) }
    }

    // ---------- settings ----------

    fun saveSettings(s: AppSettings) {
        store.save(s)
        _state.update { it.copy(settings = s, device = DeviceState.CHECKING, deviceMessage = "") }
        lastAutoUnlock = 0L
        toast("Saved")
        refreshNow()
    }

    fun forgetTiinyLogin() {
        store.setAuthKey(null)
        toast("Saved Tiiny login key forgotten. The next unlock logs in with the password again.")
    }

    fun forgetHostKey() {
        store.clearHostKeys()
        toast("Saved NAS key forgotten. The next connection saves the new one.")
    }

    private fun toast(msg: String) {
        _state.update { it.copy(toast = msg) }
    }

    fun toastShown() {
        _state.update { it.copy(toast = null) }
    }
}

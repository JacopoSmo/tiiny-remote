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
    val unlocking: Boolean = false,
    val log: List<String> = emptyList(),
    val toast: String? = null,
    val updatedAt: Long = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(UiState(store.load()))
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
                TiinyException.Kind.HTTP -> DeviceState.ERROR
            }
            _state.update { it.copy(device = ds, deviceMessage = e.message ?: "") }
            if (ds == DeviceState.LOCKED) maybeAutoUnlock()
        }
    }

    private fun maybeAutoUnlock() {
        val s = _state.value
        val now = System.currentTimeMillis()
        // At most one automatic try every 5 minutes, so a wrong password doesn't hammer the NAS.
        if (s.settings.autoUnlock && s.settings.nasConfigured && !s.unlocking && now - lastAutoUnlock > 5 * 60_000) {
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
                toast(e.message ?: "Loading failed")
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
                toast(e.message ?: "Unloading failed")
            } finally {
                setWorking(model.id, false)
                refresh()
            }
        }
    }

    private fun setWorking(id: String, on: Boolean) {
        _state.update { it.copy(working = if (on) it.working + id else it.working - id) }
    }

    // ---------- unlock over SSH ----------

    fun unlock(automatic: Boolean = false) {
        if (_state.value.unlocking) return
        val st = settings
        if (!st.nasConfigured) {
            toast("Enter the NAS address, username and password in Settings first.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(unlocking = true) }
            log(if (automatic) "Tiiny is locked: unlocking automatically through the NAS…" else "Connecting to the NAS…")
            val res = withContext(Dispatchers.IO) { SshRunner.run(st, store, st.unlockCommand) }
            if (res.output.isNotBlank()) res.output.lines().filter { it.isNotBlank() }.forEach { log("  $it") }
            if (res.error != null) {
                log("Unlock failed: ${res.error}")
                _state.update { it.copy(unlocking = false) }
                if (!automatic) toast("Unlock failed: see the Unlock tab")
                return@launch
            }
            log("tiiny login done. Waiting for the Tiiny's services (usually 1–2 minutes)…")
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

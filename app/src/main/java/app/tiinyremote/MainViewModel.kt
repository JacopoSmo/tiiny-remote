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
    val toast: String? = null,
    val vpn: VpnStatus = VpnStatus(),
    /** The Model Store (null until opened), downloads in progress, and what the store tab is busy with. */
    val store: List<StoreModel>? = null,
    val storeError: String? = null,
    val storeLoading: Boolean = false,
    val downloads: List<Download> = emptyList(),
)

data class VpnStatus(
    val wireguardInstalled: Boolean = false,
    val permitted: Boolean = false,
    val active: Boolean = false,
    /** True on the home network, false elsewhere, null when the app can't tell. */
    val onHome: Boolean? = null,
    val switching: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(UiState(store.load(), unavailable = store.unavailable()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastAutoUnlock = 0L
    /** Automatic unlocks that failed in a row: each one doubles the wait (up to 15 min), so a wrong password doesn't retry forever. */
    private var autoUnlockFails = 0
    private var lastVpnSwitch = 0L
    /** After you switch the VPN yourself, automatic switching pauses for 10 minutes. */
    private var vpnPausedUntil = 0L
    private val app get() = getApplication<Application>()

    private val settings get() = _state.value.settings
    private fun api() = TiinyApi(settings.tiinyUrl, settings.apiKey)

    // ---------- live device stats (NPU, CPU/RAM, storage, and power/temperature from the :9109 dashboard) ----------

    private val _stats = MutableStateFlow<DeviceStats?>(null)
    val stats: StateFlow<DeviceStats?> = _stats.asStateFlow()

    @Volatile
    private var statsOpen = false

    /** The stats panel is open: read every 3 s instead of every 5 s. */
    fun statsPanel(open: Boolean) {
        statsOpen = open
        if (open) viewModelScope.launch { readStats() }
    }

    private suspend fun readStats() {
        val s = _state.value
        if (s.device != DeviceState.ONLINE || !s.settings.tiinyConfigured) {
            _stats.value = null // no old numbers while the Tiiny is away
            return
        }
        _stats.value = withContext(Dispatchers.IO) { Stats.read(s.settings) }
    }

    /** Runs while the app is on screen, beside pollLoop. */
    suspend fun statsLoop() {
        var last = 0L
        while (true) {
            val every = if (statsOpen) 3_000L else 5_000L
            if (System.currentTimeMillis() - last >= every) {
                readStats()
                last = System.currentTimeMillis()
            }
            delay(1_000)
        }
    }

    /** Runs while the app is on screen (see MainActivity): checks the Tiiny every 15 s, every 5 s while something changes. */
    suspend fun pollLoop() {
        while (true) {
            refresh()
            val s = _state.value
            val fast = s.device == DeviceState.LOCKED || s.unlocking || s.working.isNotEmpty() || s.vpn.switching ||
                s.models.any { it.status == "loading" }
            delay(if (fast) 5_000 else 15_000)
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        updateVpnStatus()
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
                    models = withAliases(cat.models),
                    npuUsed = cat.npuUsed,
                )
            }
            autoVpn(reachable = true)
        } catch (e: TiinyException) {
            val ds = when (e.kind) {
                TiinyException.Kind.LOCKED -> DeviceState.LOCKED
                TiinyException.Kind.UNAUTHORIZED -> DeviceState.UNAUTHORIZED
                TiinyException.Kind.UNREACHABLE -> DeviceState.UNREACHABLE
                TiinyException.Kind.NOT_FOUND, TiinyException.Kind.HTTP -> DeviceState.ERROR
            }
            _state.update { it.copy(device = ds, deviceMessage = e.message ?: "") }
            autoVpn(reachable = ds != DeviceState.UNREACHABLE)
            if (ds == DeviceState.LOCKED) maybeAutoUnlock()
        }
    }

    private fun maybeAutoUnlock() {
        val s = _state.value
        val now = System.currentTimeMillis()
        // Direct unlocking is one quick request to the Tiiny: retry every 30 s. Through the NAS only, at most
        // every 5 minutes, so a wrong NAS password doesn't hammer it.
        val base = if (s.settings.directConfigured) 30_000L else 5 * 60_000L
        val gap = minOf(base shl minOf(autoUnlockFails, 5), 15 * 60_000L)
        if (s.settings.autoUnlock && canUnlock(s.settings) && !s.unlocking && now - lastAutoUnlock > gap) {
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
                    toast("${model.name} isn't available on the Tiiny. If it's a leftover copy, delete it with the bin on its card.")
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

    /** Settings → Power off / Restart the Tiiny. Loaded models are unloaded and running jobs stop on the device. */
    fun power(restart: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { if (restart) api().reboot() else api().shutdown() }
                ApiLog.add(if (restart) "Tiiny restarting (asked from the app)" else "Tiiny switching off (asked from the app)", "Device")
                toast(
                    if (restart) "The Tiiny is restarting: it's back in a few minutes" +
                        (if (settings.autoUnlock && canUnlock(settings)) ", then the app unlocks it." else ". Then unlock it (Unlock screen).")
                    else "The Tiiny switches off in about 10 seconds. To switch it on again, press its power button.",
                )
                lastAutoUnlock = 0L // after a restart, unlock as soon as it answers
                autoUnlockFails = 0
            } catch (e: Exception) {
                ApiLog.add("${if (restart) "Restart" else "Power off"} failed: ${e.message}", "Device")
                toast("${if (restart) "Restart" else "Power off"} failed: ${e.message}")
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

    // ---------- unlocking: directly with the Tiiny's password, or through the NAS over SSH ----------

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
            try {
                unlockSteps(st, automatic)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log("Unlock stopped: ${e.message}")
            } finally {
                // Never leave the app stuck in "Unlocking…".
                _state.update { it.copy(unlocking = false) }
            }
        }
    }

    private suspend fun unlockSteps(st: AppSettings, automatic: Boolean) {
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
            if (automatic) {
                autoUnlockFails++
                if (autoUnlockFails == 3) log("Automatic unlocking keeps failing: it now tries less often (up to every 15 min). Check the password in Settings.")
            } else toast("Unlock failed: see the Activity tab")
            return
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
        if (back) autoUnlockFails = 0 else if (automatic) autoUnlockFails++
        log(if (back) "Tiiny is unlocked. Load a model to start chatting." else "The Tiiny still doesn't answer after 4 minutes. Try again, or open TiinyOS on a computer.")
        _state.update { it.copy(unlocking = false) }
        toast(if (back) "Tiiny unlocked" else "Tiiny still locked")
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
                    res.error != null -> "NAS test failed: see the Activity tab"
                    res.output.contains("NOT found") -> "SSH works, but the Tiiny CLI wasn't found"
                    else -> "NAS login works and the Tiiny CLI is there"
                }
            )
        }
    }

    /** Log → "Tiiny's request statistics": what the Tiiny itself counted, as one log entry. */
    fun logTiinyStats() {
        viewModelScope.launch {
            try {
                val (text, failures) = withContext(Dispatchers.IO) { TiinyInfo.requestStats(settings) }
                ApiLog.add(text, "Device", isProblem = failures)
            } catch (e: Exception) {
                ApiLog.add("Couldn't read the Tiiny's request statistics: ${e.message}", "Device")
            }
        }
    }

    fun clearLog() {
        ApiLog.clear()
    }

    private val vpnWords = Regex("VPN|WireGuard|tunnel", RegexOption.IGNORE_CASE)

    /** Unlock and VPN steps go into the app's one log, under VPN or Unlock. */
    private fun log(line: String) {
        ApiLog.add(line.trim(), if (vpnWords.containsMatchIn(line)) "VPN" else "Unlock")
    }

    // ---------- WireGuard VPN ----------

    fun updateVpnStatus() {
        val ctx = app
        val st = settings
        val status = VpnStatus(
            wireguardInstalled = Vpn.installed(ctx),
            permitted = Vpn.permitted(ctx),
            active = Vpn.active(ctx),
            onHome = if (st.tiinyUrl.isBlank()) null else Vpn.onHomeNetwork(ctx, st.tiinyUrl),
            switching = _state.value.vpn.switching,
        )
        _state.update { it.copy(vpn = status) }
    }

    /** The one-tap button: switches the tunnel and pauses automatic switching for 10 minutes. */
    fun toggleVpn() {
        val v = _state.value.vpn
        vpnPausedUntil = System.currentTimeMillis() + 10 * 60_000
        switchVpn(!v.active, reason = null)
    }

    /**
     * Automatic VPN (only while the app is open): on when you're away from home (or, if the app can't tell, when the
     * Tiiny can't be reached), off again at home if you chose that. At most one switch a minute.
     */
    private fun autoVpn(reachable: Boolean) {
        val st = settings
        val v = _state.value.vpn
        val now = System.currentTimeMillis()
        if (!st.autoVpn || !st.vpnConfigured || !v.permitted || v.switching) return
        if (now < vpnPausedUntil || now - lastVpnSwitch < 60_000) return
        // A new network (home, away, unknown) gives automatic switching a fresh try after it gave up.
        val net = v.onHome.toString()
        if (net != autoNet) {
            autoNet = net
            autoGaveUp = false
        }
        if (autoGaveUp) return
        when {
            v.onHome == true && v.active && st.vpnOffAtHome -> switchVpn(false, "you're on your home network")
            v.onHome == false && !v.active -> switchVpn(true, "you're away from your home network")
            v.onHome == null && !v.active && !reachable -> switchVpn(true, "the Tiiny isn't reachable directly")
        }
    }

    /** The network automatic switching last saw, and whether it gave up on it (so it doesn't retry every minute). */
    private var autoNet: String? = null
    private var autoGaveUp = false

    private fun switchVpn(up: Boolean, reason: String?) {
        val st = settings
        if (!st.vpnConfigured) {
            toast("Enter your WireGuard tunnel's name in Settings first.")
            return
        }
        if (!Vpn.installed(app)) {
            toast("The WireGuard app isn't installed.")
            return
        }
        if (!Vpn.permitted(app)) {
            toast("Allow Tiiny Remote to control WireGuard first (Settings → VPN).")
            return
        }
        lastVpnSwitch = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(vpn = it.vpn.copy(switching = true)) }
            val what = if (up) "on" else "off"
            log("Switching the VPN $what" + (reason?.let { " because $it" } ?: "") + "…")
            // WireGuard asleep in the background may miss the first command: send it up to three times.
            var done = false
            for (attempt in 1..3) {
                if (attempt > 1) log("  No reaction from WireGuard yet, asking again ($attempt/3)…")
                Vpn.setTunnel(app, st.wgTunnel, up)
                done = waitForVpn(up, seconds = 5)
                if (done) break
            }
            // WireGuard is asleep: wake it by opening it, ask once more, then come back here.
            if (!done) {
                // Seen: a just-woken WireGuard ignored the first request after opening and took the next one. So ask
                // up to three times over about 25 seconds before giving up.
                log("  Opening WireGuard to wake it up, then asking again…")
                Vpn.openApp(app)
                delay(2_000)
                for (again in 1..3) {
                    Vpn.setTunnel(app, st.wgTunnel, up)
                    done = waitForVpn(up, seconds = 8)
                    if (done) break
                    if (again < 3) log("  WireGuard is open but hasn't switched yet, asking again…")
                }
                if (reason != null) Vpn.backToApp(app)
            }
            _state.update { it.copy(vpn = it.vpn.copy(switching = false)) }
            updateVpnStatus()
            if (done) {
                log("VPN is $what.")
                if (reason == null) toast("VPN $what")
                refresh()
            } else {
                log(
                    "WireGuard didn't switch the VPN $what. Check that the tunnel name \"${st.wgTunnel}\" matches the " +
                        "WireGuard app exactly and that \"Allow remote control apps\" is on in WireGuard's settings. " +
                        "If it works only sometimes, Android is putting WireGuard to sleep: in Android's settings, " +
                        "set WireGuard's battery use to \"Unrestricted\" (and allow Autostart, if your phone has it)."
                )
                if (reason != null) {
                    // Don't keep trying every minute: wait until the network changes, or a tap on the VPN button.
                    autoGaveUp = true
                    log("Automatic VPN switching is paused until your network changes. Tap the VPN button to switch it yourself.")
                }
                toast("VPN didn't switch: see the Activity tab")
            }
        }
    }

    private suspend fun waitForVpn(up: Boolean, seconds: Int): Boolean {
        for (i in 0 until seconds * 2) {
            delay(500)
            if (Vpn.active(app) == up) return true
        }
        return false
    }

    fun openWireGuard() {
        Vpn.openApp(app)
    }

    fun permissionResult(granted: Boolean) {
        updateVpnStatus()
        toast(if (granted) "Tiiny Remote may now switch WireGuard tunnels" else "Permission not given: VPN control stays off")
    }

    // ---------- Model Store, downloads, updates, deleting ----------

    fun loadStore() {
        if (_state.value.storeLoading) return
        _state.update { it.copy(storeLoading = true, storeError = null) }
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { api().store() }
                _state.update { it.copy(store = list.sortedBy { m -> m.name.lowercase() }) }
                readDownloads()
            } catch (e: Exception) {
                _state.update { it.copy(storeError = e.message ?: "The Model Store didn't answer.") }
            } finally {
                _state.update { it.copy(storeLoading = false) }
            }
        }
    }

    @Volatile
    private var watchDownloadsUntil = 0L

    /** Downloads you cancelled: when they leave the list, that's not "downloaded" (an update cancelled keeps its old copy). */
    private val cancelledDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun readDownloads() {
        val before = _state.value.downloads.map { it.id }.toSet()
        val now = try {
            withContext(Dispatchers.IO) { api().downloads() }
        } catch (e: Exception) {
            return
        }
        _state.update { it.copy(downloads = now) }
        val active = now.count { !it.paused }
        Background.set(getApplication(), "downloads", if (active > 0) "Downloading ${if (active == 1) "a model" else "$active models"}" else null)
        val gone = before - now.map { it.id }.toSet()
        if (gone.isNotEmpty()) {
            refresh()
            // Gone from the list means finished only if it's now installed: a cancelled download is gone too.
            val installed = _state.value.models.map { it.id }.toSet()
            val finished = gone.filter { id -> val cancelled = cancelledDownloads.remove(id); id in installed && !cancelled }
            if (finished.isEmpty()) return
            val names = finished.map { id -> _state.value.store?.firstOrNull { it.id == id }?.name ?: id.substringAfterLast('/') }
            toast("Downloaded: ${names.joinToString(", ")}")
            Notifier.done(getApplication(), "Download finished", names.joinToString(", "))
            ApiLog.add("Download finished: ${finished.joinToString(", ")}", "Models")
        }
    }

    private fun modelAction(what: String, id: String, done: String, block: TiinyApi.() -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api().block() }
                ApiLog.add("$what $id: accepted", "Models")
                toast(done)
                watchDownloadsUntil = System.currentTimeMillis() + 60_000
                readDownloads()
                refresh()
            } catch (e: Exception) {
                ApiLog.add("$what $id failed: ${e.message}", "Models")
                toast("$what failed: ${e.message}")
            }
        }
    }

    fun download(id: String, name: String) {
        cancelledDownloads.remove(id)
        modelAction("Download", id, "Downloading $name on the Tiiny…") { this.download(id) }
    }

    fun updateModel(m: ModelInfo) {
        cancelledDownloads.remove(m.id)
        modelAction("Update", m.id, "Updating ${m.name} on the Tiiny…") { this.download(m.id) }
    }
    fun pauseDownload(d: Download) = modelAction("Pause download", d.id, "Paused ${d.name}") { this.pauseDownload(d.id) }
    fun resumeDownload(d: Download) {
        cancelledDownloads.remove(d.id)
        modelAction("Resume download", d.id, "Resuming ${d.name}…") { this.download(d.id) }
    }

    fun cancelDownload(d: Download) {
        // Cancelling deletes the model on the Tiiny. For a model that's installed (an update being fetched, or a
        // list that got it wrong), that would delete the installed copy too: pause instead and say why.
        val installed = _state.value.models.firstOrNull { it.id == d.id }
        if (installed != null) {
            toast("${installed.name} is installed: cancelling would delete it. Pausing instead; to remove it, use the bin on its card.")
            ApiLog.add("Cancel refused for ${d.id}: it's installed (status ${installed.status}); paused instead", "Models")
            if (!d.paused) pauseDownload(d)
            return
        }
        cancelledDownloads.add(d.id)
        modelAction("Cancel download", d.id, "Cancelled ${d.name}") { this.delete(d.id) }
    }
    fun deleteModel(m: ModelInfo) = modelAction("Delete", m.id, "Deleted ${m.name} from the Tiiny") { this.delete(m.id) }

    init {
        // With "Keep working in the background" on, downloads are still followed while the app is away (the loop
        // below runs only while it's on screen), so the finish is noticed and notified.
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val s = _state.value
                if (!Background.visible && s.settings.keepRunning && s.downloads.any { !it.paused }) readDownloads()
            }
        }
    }

    /** Runs beside the polling: follows downloads every 5 s while there are any (or just after one was started). */
    suspend fun downloadLoop() {
        while (true) {
            val s = _state.value
            if (s.device == DeviceState.ONLINE && (s.downloads.isNotEmpty() || System.currentTimeMillis() < watchDownloadsUntil)) readDownloads()
            delay(5_000)
        }
    }

    // ---------- your own names for models (this app only) ----------

    private val aliasPrefs = app.getSharedPreferences("model_names", android.content.Context.MODE_PRIVATE)

    private fun withAliases(list: List<ModelInfo>): List<ModelInfo> = list.map { m ->
        val full = m.fullName.ifBlank { m.name }
        aliasPrefs.getString(m.id, null)?.takeIf { it.isNotBlank() }?.let { m.copy(name = it, fullName = full) } ?: m.copy(fullName = full)
    }

    /** Gives a model a shorter name in this app; blank goes back to the Tiiny's name. */
    fun renameModel(id: String, name: String) {
        aliasPrefs.edit().apply { if (name.isBlank()) remove(id) else putString(id, name.trim().take(60)) }.apply()
        _state.update { st ->
            st.copy(models = withAliases(st.models.map { it.copy(name = it.fullName.ifBlank { it.name }) }))
        }
    }

    /** Display settings apply at once, without re-checking the Tiiny. */
    fun setDisplay(s: AppSettings) {
        store.save(s)
        _state.update { it.copy(settings = s) }
    }

    fun saveSettings(s: AppSettings) {
        store.save(s)
        _state.update { it.copy(settings = s, device = DeviceState.CHECKING, deviceMessage = "") }
        lastAutoUnlock = 0L
        autoUnlockFails = 0
        vpnPausedUntil = 0L
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

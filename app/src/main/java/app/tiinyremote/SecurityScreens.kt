package app.tiinyremote

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LOCK_AFTER = listOf("0" to "At once", "60" to "After 1 minute away", "300" to "After 5 minutes away", "900" to "After 15 minutes away")

/** Settings → Privacy: the app lock and the encrypted backup to the NAS. */
@Composable
fun PrivacySection(vm: MainViewModel, cur: AppSettings) {
    val ctx = LocalContext.current
    Text("Privacy and backup", style = MaterialTheme.typography.titleMedium)

    // ---- App lock
    var lockWhy by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = cur.appLock, onCheckedChange = { on ->
            if (!on) vm.setDisplay(cur.copy(appLock = false))
            else {
                val why = AppLock.cannot(ctx)
                if (why != null) lockWhy = why else vm.setDisplay(cur.copy(appLock = true))
            }
        })
        Spacer(Modifier.width(10.dp))
        Text("App lock: fingerprint, face or the phone's screen lock to open Tiiny Remote")
    }
    lockWhy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (cur.appLock) SettingDropdown("Lock", LOCK_AFTER, cur.lockAfter.toString()) { vm.setDisplay(cur.copy(lockAfter = it.toInt())) }

    // ---- Backup
    val c = remember { AutoStore(ctx).connectors() }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Backup.Entry>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var note by remember { mutableStateOf<String?>(null) }
    var ask by remember { mutableStateOf<Backup.Entry?>(null) } // restore: password for this one
    var makeAsk by remember { mutableStateOf(false) }
    var delAsk by remember { mutableStateOf<Backup.Entry?>(null) }
    var restored by remember { mutableStateOf(false) }
    val store = remember { Store(ctx) }

    fun refresh() = scope.launch {
        note = null
        list = try { withContext(Dispatchers.IO) { NasAgent.backups(c) } } catch (e: Exception) { note = e.message; null }
    }
    LaunchedEffect(c.nasReady) { if (c.nasReady) refresh() }

    fun make(pw: String) = scope.launch {
        busy = "Packing"; progress = 0f; note = null
        try {
            val file = withContext(Dispatchers.IO) { Backup.create(ctx, pw.toCharArray()) { what -> busy = "Packing: $what" } }
            busy = "Sending to the NAS"
            withContext(Dispatchers.IO) { NasAgent.uploadBackup(c, Backup.newName(), file) { progress = it } }
            file.delete()
            note = "Backed up (${file.length() / (1024 * 1024)} MB, encrypted)."
            refresh()
        } catch (e: Exception) {
            note = e.message ?: e.javaClass.simpleName
        } finally {
            busy = null
        }
    }

    fun restore(e: Backup.Entry, pw: String) = scope.launch {
        busy = "Fetching from the NAS"; progress = 0f; note = null
        val file = java.io.File(ctx.cacheDir, "restore.trb")
        try {
            withContext(Dispatchers.IO) { NasAgent.downloadBackup(c, e.name, file) { progress = it } }
            busy = "Unpacking"
            val stage = withContext(Dispatchers.IO) { Backup.unpack(ctx, file, pw.toCharArray()) { what -> busy = "Unpacking: $what" } }
            withContext(Dispatchers.IO) { Backup.swap(ctx, stage) }
            restored = true
        } catch (ex: Exception) {
            note = ex.message ?: ex.javaClass.simpleName
        } finally {
            file.delete()
            busy = null
        }
    }

    Text(
        "Backup: settings, chats, the Gallery, voices, Code projects and automations, encrypted on this phone with a " +
            "backup password and kept by the NAS agent (the newest five). The NAS can't read it; without the password nobody can.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!c.nasReady) {
        Text("Needs the NAS agent: Automate → Connectors → NAS agent.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { makeAsk = true }, enabled = busy == null) { BtnLabel("Back up now", symbol = "📦") }
        TextButton(onClick = { refresh() }, enabled = busy == null) { BtnLabel("Refresh", symbol = SIGN_REFRESH) }
    }
    busy?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
        if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    list?.let { l ->
        if (l.isEmpty()) Text("No backups on the NAS yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val fmt = remember { java.text.SimpleDateFormat("EEE d MMM yyyy, HH:mm", java.util.Locale.getDefault()) }
        for (e in l) Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmt.format(java.util.Date(e.time * 1000)), style = MaterialTheme.typography.bodyMedium)
                Text("${e.name.substringAfter('_').substringAfter('_')} · ${maxOf(1, e.size / (1024 * 1024))} MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { ask = e }, enabled = busy == null) { Text("Restore") }
            TextButton(onClick = { delAsk = e }, enabled = busy == null) { BtnLabel("Delete", androidx.compose.material.icons.Icons.Filled.Delete) }
        }
    }

    if (makeAsk) PasswordDialog(
        title = "Back up now",
        text = "The backup password locks the backup. Keep it safe: without it, the backup can't be opened (not even by you).",
        twice = store.backupPassword() == null,
        remembered = store.backupPassword(),
        onCancel = { makeAsk = false },
    ) { pw, remember ->
        makeAsk = false
        store.setBackupPassword(if (remember) pw else null)
        make(pw)
    }
    ask?.let { e ->
        PasswordDialog(
            title = "Restore this backup?",
            text = "Everything in Tiiny Remote on this phone is replaced by the backup. Then the app closes; open it again. " +
                "On another phone, Connectors may need their passwords once more.",
            twice = false, remembered = store.backupPassword(), onCancel = { ask = null },
        ) { pw, _ -> ask = null; restore(e, pw) }
    }
    delAsk?.let { e ->
        AlertDialog(
            onDismissRequest = { delAsk = null },
            title = { Text("Delete this backup from the NAS?") },
            text = { Text("It can't be brought back.") },
            confirmButton = {
                TextButton(onClick = {
                    delAsk = null
                    scope.launch {
                        try { withContext(Dispatchers.IO) { NasAgent.deleteBackup(c, e.name) } } catch (ex: Exception) { note = ex.message }
                        refresh()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { delAsk = null }) { Text("Cancel") } },
        )
    }
    if (restored) AlertDialog(
        onDismissRequest = {},
        title = { Text("Restored") },
        text = { Text("Tiiny Remote closes now. Open it again: everything is as it was in the backup.") },
        confirmButton = {
            TextButton(onClick = {
                (ctx as? Activity)?.finishAffinity()
                kotlin.system.exitProcess(0)
            }) { Text("Close the app") }
        },
    )
}

@Composable
private fun PasswordDialog(
    title: String, text: String, twice: Boolean, remembered: String?, onCancel: () -> Unit, onOk: (String, Boolean) -> Unit,
) {
    var pw by remember { mutableStateOf(remembered ?: "") }
    var pw2 by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(remembered != null) }
    val okEnabled = pw.length >= 8 && (!twice || pw == pw2)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(pw, { pw = it }, label = { Text("Backup password (8 or more characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (twice) OutlinedTextField(pw2, { pw2 = it }, label = { Text("The same again") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    isError = pw2.isNotEmpty() && pw2 != pw)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keep, { keep = it })
                    Text("Remember it on this phone (encrypted)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(pw, keep) }, enabled = okEnabled) { Text("OK") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

package app.tiinyremote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val day = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
private fun n1(v: Double) = if (v.isNaN()) "—" else String.format(Locale.ROOT, "%.1f", v)
private fun pc(v: Double) = if (v.isNaN()) "—" else "${Math.round(v)}%"
private fun short(name: String) = name.substringAfterLast('/')
private fun median(l: List<Double>) = l.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }

/** Settings → History: every speed test in a table, the best pairs, and each model's speed over all tests. */
@Composable
fun SpeedHistoryDialog(st: StudioViewModel, onClose: () -> Unit) {
    val all by st.tests.speedHistory.collectAsState()
    val chats by st.chatSpeeds.collectAsState()
    var sort by remember { mutableStateOf("new") }
    var confirmClear by remember { mutableStateOf(false) }
    val rows = when (sort) {
        "pair" -> all.sortedByDescending { it.bothGain }
        "speed" -> all.sortedByDescending { it.bigRate }
        else -> all
    }
    ScaledAlertDialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f).widthIn(max = 720.dp),
        title = { ClosableTitle(onClose) { Text("Speed statistics") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Measured in normal chats, no test needed: alone, and beside another model writing.
                val rated = chats.filter { !it.rate.isNaN() }
                if (rated.isNotEmpty()) {
                    Text("From your chats (tokens per second)", style = MaterialTheme.typography.titleMedium)
                    for ((m, l) in rated.groupBy { it.model }.entries.sortedByDescending { (_, l) -> l.map { it.rate }.average() }) {
                        val alone = l.filter { it.alone }.map { it.rate }
                        val beside = l.filter { !it.alone }.map { it.rate }
                        Text(
                            "${short(m)}: " + listOfNotNull(
                                alone.takeIf { it.isNotEmpty() }?.let { "${n1(median(it))} alone (${it.size})" },
                                beside.takeIf { it.isNotEmpty() }?.let { "${n1(median(it))} beside another model (${it.size})" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "Every chat answer of 50 tokens or more is measured (the middle value is shown, so one odd answer " +
                            "doesn't count much). Thinking counts as writing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                }
                // The wait before an answer, as you feel it: until anything appears, and until the answer itself.
                val waited = chats.filter { it.wait > 0 }
                if (waited.isNotEmpty()) {
                    Text("Waiting in your chats (seconds, middle value)", style = MaterialTheme.typography.titleMedium)
                    for ((m, l) in waited.groupBy { it.model }.entries.sortedBy { (_, l) -> median(l.map { it.wait }) }) {
                        val answers = l.filter { it.answerWait > 0 }.map { it.answerWait }
                        Text(
                            "${short(m)}: first word ${n1(median(l.map { it.wait }))} s" +
                                (answers.takeIf { it.isNotEmpty() }?.let { " · answer starts ${n1(median(it))} s" } ?: "") + " (${l.size})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // How the wait grows with the chat: short, medium and long chats (what was sent).
                        val bands = ChatWaits.bands(l.map { it.chars to it.wait })
                        if (bands.size > 1) Text(
                            bands.joinToString(" · ") { (label, secs, n) -> "$label: ${n1(secs)} s ($n)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "First word: until anything appears, thinking included. Answer starts: until the answer itself, after any thinking. " +
                            "Long chats wait longer because the Tiiny reads everything again; when the long-chat wait is much higher, a fresh chat is quicker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                }
                if (all.isEmpty()) {
                    Text("No speed tests yet. Run one in Settings → Fast helper model.")
                    return@Column
                }
                // Best pairs: averaged over all their tests.
                Text("Pairs, best first", style = MaterialTheme.typography.titleMedium)
                val pairs = all.groupBy { it.big to it.small }.map { (k, l) ->
                    Triple(k, l.map { it.bothGain }.filter { !it.isNaN() }.average(), l.size)
                }.sortedByDescending { it.second }
                for ((k, gain, count) in pairs) {
                    val pagesToo = all.firstOrNull { it.big == k.first && it.small == k.second }?.let { it.smallRate >= it.bigRate * 1.25 } == true
                    val jobs = all.filter { it.big == k.first && it.small == k.second && !it.smallJob.isNaN() }
                    Text(
                        "${short(k.first)} + ${short(k.second)}: ${pc(gain)} sooner together · $count test${if (count == 1) "" else "s"}" +
                            (if (jobs.isNotEmpty()) " · helper job ${n1(jobs.map { it.smallJob }.average())} s (main ${n1(jobs.map { it.bigJob }.average())} s)" else "") +
                            if (pagesToo) " · helper condenses pages" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // The helper job: seconds until a condensed chat is done, as the app asks for it (thinking included).
                val jobTimes = (all.map { it.small to it.smallJob } + all.map { it.big to it.bigJob })
                    .filter { !it.second.isNaN() }
                    .groupBy({ it.first }, { it.second })
                    .map { (m, l) -> Triple(m, l.average(), l.size) }
                    .sortedBy { it.second }
                if (jobTimes.isNotEmpty()) {
                    Text("Helper job, quickest first (seconds to condense a chat)", style = MaterialTheme.typography.titleMedium)
                    for ((m, secs, count) in jobTimes) {
                        Text("${short(m)}: ${n1(secs)} s · $count measurement${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Each model's speed alone, whichever role it had.
                Text("Models, fastest first (alone, tokens per second)", style = MaterialTheme.typography.titleMedium)
                val speeds = (all.map { it.big to it.bigRate } + all.map { it.small to it.smallRate })
                    .filter { !it.second.isNaN() }
                    .groupBy({ it.first }, { it.second })
                    .map { (m, l) -> Triple(m, l.average(), l.size) }
                    .sortedByDescending { it.second }
                for ((m, rate, count) in speeds) {
                    Text("${short(m)}: ${n1(rate)} tok/s · $count measurement${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("All tests:", style = MaterialTheme.typography.titleMedium)
                    FilterChip(selected = sort == "new", onClick = { sort = "new" }, label = { Text("Newest") })
                    FilterChip(selected = sort == "pair", onClick = { sort = "pair" }, label = { Text("Best together") })
                    FilterChip(selected = sort == "speed", onClick = { sort = "speed" }, label = { Text("Fastest main") })
                }
                // The table scrolls sideways on narrow screens.
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    val cols = listOf("When" to 92.dp, "Main" to 150.dp, "Helper" to 150.dp, "Main t/s" to 64.dp, "Helper t/s" to 72.dp,
                        "Together" to 70.dp, "Helper ×2" to 70.dp, "Helper job" to 72.dp, "Main job" to 64.dp, "Runs" to 44.dp)
                    Row { for ((h, w) in cols) Cell(h, w, bold = true) }
                    HorizontalDivider()
                    for (r in rows) Row {
                        Cell(day.format(Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault())), cols[0].second)
                        Cell(short(r.big), cols[1].second)
                        Cell(short(r.small), cols[2].second)
                        Cell(n1(r.bigRate), cols[3].second)
                        Cell(n1(r.smallRate), cols[4].second)
                        Cell(pc(r.bothGain), cols[5].second)
                        Cell(pc(r.helperGain), cols[6].second)
                        Cell(if (r.smallJob.isNaN()) "—" else "${n1(r.smallJob)} s", cols[7].second)
                        Cell(if (r.bigJob.isNaN()) "—" else "${n1(r.bigJob)} s", cols[8].second)
                        Cell(r.runs.toString() + if (r.outliers > 0) "*" else "", cols[9].second)
                    }
                }
                Text(
                    "Together: how much sooner both models finished at once than one after the other. Helper ×2: the same for " +
                        "the helper twice (usually 0%: the Tiiny takes one model's requests in turn). Helper job / Main job: seconds until " +
                        "each condenses the same chat, as the app asks (with any thinking): the real wait for a side job. * outliers were left out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (all.isNotEmpty() || chats.isNotEmpty()) {
                if (confirmClear) TextButton(onClick = { st.tests.clearSpeedHistory(); confirmClear = false }) {
                    Text("Really clear all?", color = MaterialTheme.colorScheme.error)
                } else TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
            }
        },
    )
}

@Composable
private fun Cell(text: String, width: Dp, bold: Boolean = false) = Text(
    text,
    modifier = Modifier.width(width).padding(vertical = 3.dp, horizontal = 2.dp),
    style = MaterialTheme.typography.bodySmall,
    fontWeight = if (bold) FontWeight.Bold else null,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

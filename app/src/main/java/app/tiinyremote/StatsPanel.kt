package app.tiinyremote

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun fmt(v: Double?, unit: String, digits: Int = 0): String =
    if (v == null) "—" else String.format(Locale.ROOT, "%.${digits}f", v) + unit

/** Utilisation colours: up to 50 % green, 51–75 % orange, 76 % and more red. */
@Composable
fun loadColor(v: Double?): Color = when {
    v == null -> MaterialTheme.colorScheme.onSurfaceVariant
    v >= 76 -> Color(0xFFE53935)
    v >= 51 -> Color(0xFFFB8C00)
    else -> Color(0xFF43A047)
}

/**
 * A slim strip under the top bar with the live numbers: NPU and CPU load (colour-coded), board power and the hottest
 * temperature. Tap it for the full panel.
 */
@Composable
fun StatsStrip(s: UiState, vm: MainViewModel) {
    val stats by vm.stats.collectAsState()
    var open by remember { mutableStateOf(false) }
    val st = stats
    val online = s.device == DeviceState.ONLINE
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = true; vm.statsPanel(true) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!online) {
                Text("Tiiny offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            } else {
                val mu = st?.npuMemUsedMb
                val mt = st?.npuMemTotalMb
                // TiinyOS 0.1.39 doesn't report the NPU's own memory: then show the share the loaded models take.
                val budget = s.models.filter { it.running }.sumOf { it.npuUsage ?: 0.0 }
                Meter("NPU", st?.npuUtil, Modifier.weight(1f), "Mem " + if (mu != null && mt != null) "${gbText(mu / 1024)} / ${gbText(mt / 1024)} GB" else "${fmt(budget, "%")} used by models")
                val ru = st?.ramUsedGb
                val rt = st?.ramTotalGb
                Meter("CPU", st?.cpuPct, Modifier.weight(1f), "RAM " + if (ru != null && rt != null) "${gbText(ru)} / ${gbText(rt)} GB" else "—")
                Text(
                    "⚡ " + fmt(st?.npuPowerW, " W", 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (st?.npuPowerW == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "🌡 " + fmt(st?.npuTempC, "°C"),
                    style = MaterialTheme.typography.labelMedium,
                    color = st?.npuTempC.let { t ->
                        when {
                            t == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            t >= 85 -> Color(0xFFE53935)
                            t >= 70 -> Color(0xFFFB8C00)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    },
                )
            }
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false; vm.statsPanel(false) }) {
            StatsPanel(s, vm, st)
        }
    }
}

private fun gbText(v: Double) = if (v < 10) String.format(Locale.ROOT, "%.1f", v) else Math.round(v).toString()

/** A label, a coloured percentage and a thin bar, with the memory in GB underneath. */
@Composable
private fun Meter(label: String, v: Double?, modifier: Modifier, sub: String) {
    val c = loadColor(v)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt(v, "%"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c)
            Spacer(Modifier.width(6.dp))
            LinearProgressIndicator(
                progress = { ((v ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
                color = c,
                modifier = Modifier.weight(1f).height(4.dp),
            )
        }
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The full panel: NPU memory per loaded model, live activity, temperatures and storage. */
@Composable
private fun StatsPanel(s: UiState, vm: MainViewModel, st: DeviceStats?) {
    val running = s.models.filter { it.running }
    val used = running.sumOf { it.npuUsage ?: 0.0 }
            Column(Modifier.width(330.dp).padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.device != DeviceState.ONLINE) {
                    Text("The Tiiny isn't online, so there are no live numbers.", style = MaterialTheme.typography.bodyMedium)
                }

                // NPU memory budget: what the loaded models take.
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NPU memory", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${fmt(used, "%")} used · ${fmt((100 - used).coerceAtLeast(0.0), "%")} free", style = MaterialTheme.typography.labelLarge)
                        }
                        LinearProgressIndicator(progress = { (used / 100).toFloat().coerceIn(0f, 1f) }, color = loadColor(used), modifier = Modifier.fillMaxWidth())
                        if (running.isEmpty()) Text("Nothing loaded", style = MaterialTheme.typography.bodySmall)
                        for (m in running) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(fmt(m.npuUsage, "%", 1), style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = { vm.unload(m) }, enabled = m.id !in s.working) { BtnLabel("Unload", symbol = "■") }
                            }
                        }
                    }
                }

                // Live activity
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Device activity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${fmt(st?.npuUtil, "%")} NPU busy", style = MaterialTheme.typography.labelLarge, color = loadColor(st?.npuUtil))
                        }
                        LinearProgressIndicator(progress = { ((st?.npuUtil ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) }, color = loadColor(st?.npuUtil), modifier = Modifier.fillMaxWidth())
                        val mu = st?.npuMemUsedMb
                        val mt = st?.npuMemTotalMb
                        val memGb = if (mu != null && mt != null) "${Math.round(mu / 1024)} / ${Math.round(mt / 1024)} GB" else "—"
                        Row {
                            Stat("NPU memory", memGb, Modifier.weight(1f))
                            Stat("NPU power", fmt(st?.npuPowerW, " W", 1), Modifier.weight(1f))
                        }
                        Row {
                            Stat("Hottest sensor", fmt(st?.npuTempC, " °C"), Modifier.weight(1f), hot = (st?.npuTempC ?: 0.0) > 80)
                            Stat("CPU / RAM", "${fmt(st?.cpuPct, "%")} / " + (st?.ramUsedGb?.let { u -> st?.ramTotalGb?.let { t -> "${gbText(u)} of ${gbText(t)} GB" } } ?: fmt(st?.memPct, "%")), Modifier.weight(1f))
                        }
                        val temps = st?.temps.orEmpty()
                        if (temps.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Temperatures", style = MaterialTheme.typography.labelLarge)
                            for ((name, t) in temps.take(8)) {
                                Row {
                                    Text(name.replace('_', ' '), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(
                                        fmt(t, " °C", 1),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (t > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        val df = st?.diskFreeGb
                        val dt = st?.diskTotalGb
                        if (df != null && dt != null) {
                            Text(
                                "Storage: ${Math.round(df)} GB free of ${Math.round(dt)} GB",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (df < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            when {
                                st == null -> "Reading…"
                                st.dashboard -> "Live from the on-device dashboard (port 9109) · every 3 s"
                                else -> "Power and temperature need the on-device dashboard (port 9109), a community add-on that isn't part of TiinyOS. NPU activity shows while a model is loaded."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Models load into the NPU's memory; loading takes roughly 30–60 s. The device can only hold what fits in 100% NPU.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, hot: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (hot) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

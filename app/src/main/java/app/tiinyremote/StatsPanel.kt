package app.tiinyremote

import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import java.util.Locale

private fun fmt(v: Double?, unit: String, digits: Int = 0): String =
    if (v == null) "—" else String.format(Locale.ROOT, "%.${digits}f", v) + unit

/** The colour of the n-th loaded model in the NPU memory bar and before its name. */
private fun modelColor(n: Int): Color = listOf(
    Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFF26A69A), Color(0xFFFFCA28), Color(0xFFEC407A), Color(0xFF8D6E63),
)[n % 6]

/** Utilisation colours: up to 50 % green, 51–75 % orange, 76 % and more red. */
@Composable
fun loadColor(v: Double?): Color = when {
    v == null -> MaterialTheme.colorScheme.onSurfaceVariant
    v >= 76 -> Color(0xFFE53935)
    v >= 51 -> Color(0xFFFB8C00)
    else -> Color(0xFF43A047)
}

/**
 * Which loaded chat model is the main one and which the fast helper (Settings): the helper is the chosen one when it's
 * loaded beside another chat model; the main one is the chat's pick, else the other loaded chat model.
 */
internal fun chatRoles(s: UiState, studio: StudioViewModel): Pair<ModelInfo?, ModelInfo?> {
    val chats = s.models.filter { it.running && it.isChat }
    val helper = chats.firstOrNull { it.id == s.settings.helperModel }
    val main = chats.firstOrNull { it.id == studio.pick("chat") && it != helper } ?: chats.firstOrNull { it != helper }
    return if (main == null) helper to null else main to helper
}

/**
 * The status bar under the top bar with the live numbers: NPU and CPU load (colour-coded), board power and the hottest
 * temperature. Tap it for the full panel.
 */
@Composable
fun StatsStrip(s: UiState, vm: MainViewModel, studio: StudioViewModel) {
    val stats by vm.stats.collectAsState()
    var open by remember { mutableStateOf(false) }
    val st = stats
    val online = s.device == DeviceState.ONLINE
    val loaded = s.models.count { it.running }
    // Light green while the Tiiny is online, amber while it's being checked, light red when it isn't reachable.
    val hue = when (s.device) {
        DeviceState.ONLINE -> Color(0xFF43A047)
        DeviceState.CHECKING -> Color(0xFFFFA000)
        else -> Color(0xFFE53935)
    }
    var barHeight by remember { mutableStateOf(0) }
    // A new NPU fault: the status bar pulses red until it's tapped (the fault itself stays in the panel).
    val seen by vm.faultSeen.collectAsState()
    val fault = st?.npuFaultNew?.takeIf { it != seen }
    val pulse = if (fault != null) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "fault").animateFloat(
            0.15f, 0.75f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "fault",
        ).value
    } else 0f
    val back = if (fault != null) Color(0xFFE53935).copy(alpha = pulse) else hue.copy(alpha = 0.22f)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { barHeight = it.height }
                .background(back.compositeOver(MaterialTheme.colorScheme.surface))
                .clickable { open = true; vm.statsPanel(true); if (fault != null) vm.sawFault(fault) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    when (s.device) {
                        DeviceState.ONLINE -> "Online"
                        DeviceState.LOCKED -> "Locked"
                        DeviceState.CHECKING -> "Checking"
                        else -> "Offline"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = hue,
                )
                if (online) {
                    // With a helper chosen in Settings: whether it's at work beside a main model.
                    val helperState = if (s.settings.helperModel.isBlank()) "" else if (chatRoles(s, studio).second != null) " · helper on" else " · helper off"
                    if (fault != null) Text("⚠ NPU fault: tap", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1)
                    else Text("$loaded loaded$helperState", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (!online) {
                Text(if (s.device == DeviceState.CHECKING) "One moment…" else "Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            } else {
                val mu = st?.npuMemUsedMb
                val mt = st?.npuMemTotalMb
                // TiinyOS 0.1.39 doesn't report the NPU's own memory: then show the share the loaded models take.
                val budget = s.models.filter { it.running }.sumOf { it.npuUsage ?: 0.0 }
                Meter("NPU", st?.npuUtil, Modifier.weight(1f), "Mem " + if (mu != null && mt != null) "${gbText(mu / 1024)} / ${gbText(mt / 1024)} GB" else "48 GB · ${fmt(budget, "%")} used")
                val ru = st?.ramUsedGb
                val rt = st?.ramTotalGb
                // The CPU's own memory (32 GB, the system and the apps); the models live in the NPU's 48 GB.
                Meter("CPU", st?.cpuPct, Modifier.weight(1f), "RAM " + if (ru != null && rt != null) "${gbText(ru)} / ${gbText(rt)} GB" else "—")
                // Power and temperature only when the Tiiny reports them (TiinyOS 0.1.39 doesn't).
                if (st?.npuPowerW != null) Text(
                    "⚡ " + fmt(st.npuPowerW, " W", 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                st?.npuTempC?.let { t ->
                    Text(
                        "🌡 " + fmt(t, "°C"),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            t >= 85 -> Color(0xFFE53935)
                            t >= 70 -> Color(0xFFFB8C00)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.labelMedium)
        }
        // The overlay under the status bar, as before, a bit wider (60% of the screen, 330–560 dp). Its own layout, not a
        // drop-down menu: the header stays in place and only the body scrolls, so nothing gets cut at the top.
        if (open) {
            val close = { open = false; vm.statsPanel(false) }
            val conf = androidx.compose.ui.platform.LocalConfiguration.current
            val width = (conf.screenWidthDp * 0.6f).coerceIn(330f, 560f).dp
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(0, barHeight),
                onDismissRequest = close,
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(width).heightIn(max = (conf.screenHeightDp * 0.85f).dp),
                ) {
                    AppScale { StatsPanel(s, vm, st, studio, close) }
                }
            }
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
private fun StatsPanel(s: UiState, vm: MainViewModel, st: DeviceStats?, studio: StudioViewModel, onClose: () -> Unit) {
    val running = s.models.filter { it.running }
    val used = running.sumOf { it.npuUsage ?: 0.0 }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        val on = s.device == DeviceState.ONLINE
        val loadedN = running.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (on) "Tiiny online" else if (s.device == DeviceState.LOCKED) "Tiiny locked" else "Tiiny offline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (on) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.weight(1f),
            )
            if (on) Text("$loadedN model${if (loadedN == 1) "" else "s"} loaded", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            androidx.compose.material3.IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Close")
            }
        }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!on) {
                    Text(
                        s.deviceMessage.ifBlank { "The Tiiny isn't reachable, so there are no live numbers." },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // NPU memory budget: what the loaded models take.
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NPU memory", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${fmt(used, "%")} used · ${fmt((100 - used).coerceAtLeast(0.0), "%")} free", style = MaterialTheme.typography.labelLarge)
                        }
                        // One bar, split per loaded model in its own colour (the dot before each name below), the rest free.
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Row(
                            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(track),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val total = running.sumOf { it.npuUsage ?: 0.0 }.coerceAtLeast(100.0)
                            for ((i, m) in running.withIndex()) {
                                val part = m.npuUsage ?: 0.0
                                if (part > 0) Box(Modifier.weight((part / total).toFloat()).fillMaxHeight().background(modelColor(i)))
                            }
                            val free = (1 - running.sumOf { it.npuUsage ?: 0.0 } / total).toFloat()
                            if (free > 0.001f) Spacer(Modifier.weight(free))
                        }
                        if (running.isEmpty()) Text("Nothing loaded", style = MaterialTheme.typography.bodySmall)
                        val (main, helper) = chatRoles(s, studio)
                        for ((idx, m) in running.withIndex()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(modelColor(idx), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    // The short name on one line: "org/" alone used to fill the row.
                                    Text(m.name.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val role = when (m) {
                                        helper -> "Helper"
                                        main -> if (helper != null) "Main chat model" else null
                                        else -> null
                                    }
                                    val labels = listOfNotNull(role, if (m.isMoE) "MoE" else null)
                                    if (labels.isNotEmpty() || m.busy) Row {
                                        if (labels.isNotEmpty()) SignText(tags(labels), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                        // As on the Models page: the model is answering or making something right now.
                                        if (m.busy) Text(
                                            (if (labels.isNotEmpty()) "  ·  " else "") + tag("working"),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100),
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Text(fmt(m.npuUsage, "%", 1), style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = { vm.unload(m) }, enabled = m.id !in s.working) { BtnLabel("Unload", symbol = "■") }
                            }
                        }
                        val chosen = s.settings.helperModel
                        val helperLine = when {
                            helper != null -> "Helper active: ${helper.name.substringAfterLast('/')} names chats, suggests follow-ups, condenses long chats (and web pages when it's at least 25% faster)."
                            chosen.isNotBlank() && running.any { it.id == chosen } -> "Helper loaded, but no other chat model: it works as the main model."
                            chosen.isNotBlank() -> "Helper not active: ${s.models.firstOrNull { it.id == chosen }?.name?.substringAfterLast('/') ?: "it"} isn't loaded, so the chat model does everything."
                            running.count { it.isChat } > 1 -> "Two chat models loaded, but no helper chosen (Settings → Fast helper model)."
                            else -> null
                        }
                        if (helperLine != null) Text(helperLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        // Short values in pairs, so nothing wraps or gets cut; power and temperature only when reported.
                        Row {
                            Stat("NPU memory", memGb, Modifier.weight(1f))
                            Stat("CPU", fmt(st?.cpuPct, "%"), Modifier.weight(1f))
                        }
                        Row {
                            Stat("CPU RAM", st?.ramUsedGb?.let { u -> st?.ramTotalGb?.let { t -> "${gbText(u)} / ${gbText(t)} GB" } } ?: fmt(st?.memPct, "%"), Modifier.weight(1f))
                            if (st?.npuPowerW != null) Stat("NPU power", fmt(st.npuPowerW, " W", 1), Modifier.weight(1f))
                            else Spacer(Modifier.weight(1f))
                        }
                        if (st?.npuTempC != null) Row {
                            Stat("Hottest sensor", fmt(st.npuTempC, " °C"), Modifier.weight(1f), hot = st.npuTempC > 80)
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
                        Text(
                            "The Tiiny's 80 GB are two parts: 48 GB on the NPU, where the models run, and 32 GB beside the CPU " +
                                "for TiinyOS and its services (shown as CPU RAM, about 33 GB as the system counts it).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        st?.npuIssue?.let { issue ->
                            Text(
                                "⚠ The NPU reported a fault: $issue. Once is usually harmless; if it keeps happening, tell Tiiny support.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
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
                                else -> "This TiinyOS doesn't report power or temperature, so they're hidden. NPU memory shows while a model is loaded."
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
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, hot: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (hot) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

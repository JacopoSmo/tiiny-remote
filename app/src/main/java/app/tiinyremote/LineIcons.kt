package app.tiinyremote

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Settings → Display → Pictograms: "Line icons" draws the app's pictograms as one family of thin line icons
 * instead of the phone's colour emoji. Every text in the app goes through [Text] below, which swaps a known emoji
 * for its line icon when this is on; with it off, texts show exactly as before.
 */
val LocalLineIcons = compositionLocalOf { false }

/** With line icons: true = each in its own colour (LINE_COLOURS), false = all in the style's accent colour. */
val LocalLineColour = compositionLocalOf { false }

/** The colour of each line pictogram in "Line icons (colourful)", after the emoji's own colours. */
val LINE_COLOURS: Map<String, Color> by lazy {
    fun c(v: Long) = Color(v)
    val blue = c(0xFF1E88E5); val teal = c(0xFF00897B); val amber = c(0xFFF9A825); val orange = c(0xFFEF6C00)
    val red = c(0xFFE53935); val green = c(0xFF43A047); val purple = c(0xFF8E24AA); val brown = c(0xFF8D6E63)
    val slate = c(0xFF607D8B); val pink = c(0xFFD81B60); val ice = c(0xFF4FC3F7)
    mapOf(
        "🧊" to ice, "💬" to blue, "🛠" to slate, "👁" to teal, "👂" to orange, "⚠" to amber, "🎬" to slate, "🖼" to teal,
        "📄" to slate, "📊" to blue, "💭" to purple, "🎓" to purple, "🧭" to red, "🔌" to amber, "📷" to slate, "🧠" to pink,
        "⚡" to amber, "💻" to slate, "🗂" to amber, "📁" to amber, "🎵" to purple, "🏷" to amber, "🔗" to blue, "📅" to red,
        "🧩" to green, "💡" to amber, "🤔" to amber, "🖥" to slate, "🐙" to purple, "🥇" to amber, "🗣" to orange, "🔒" to amber,
        "🔐" to amber, "🔓" to amber, "✉" to blue, "⚙" to slate, "⏳" to amber, "👋" to amber, "❓" to red, "🔎" to blue,
        "✍" to brown, "🙂" to amber, "🎯" to red, "📶" to green, "📬" to blue, "📍" to red, "🎙" to slate, "🖌" to orange,
        "🎞" to slate, "🌐" to blue, "🌡" to red, "🎲" to red, "📰" to slate, "🎧" to purple, "📝" to brown, "➰" to teal,
        "🗑" to red, "🧹" to brown, "📜" to brown, "🎤" to slate, "🕘" to blue, "💾" to blue, "🌙" to amber, "🔍" to blue,
        "👤" to slate, "🔊" to blue, "🎨" to pink, "✨" to amber, "🙋" to amber, "📦" to brown, "🗃" to brown, "📲" to green, "✋" to teal, "✂" to red, "👍" to green,
    )
}

/** A thin, round-capped line pictogram on a 24-unit grid, from SVG path data. */
private fun line(name: String, d: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        PathParser().parsePathString(d).toNodes(),
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
}.build()

/** Each colour emoji the app uses, and its line pictogram (drawn for this app). */
val LINE_ICONS: Map<String, ImageVector> by lazy {
    mapOf(
        "🧊" to "M12 3l8 4.5v9L12 21l-8-4.5v-9zM4 7.5l8 4.5 8-4.5M12 12v9",
        "💬" to "M4 5h16v11H9l-5 4z",
        "🛠" to "M14.5 6.5a4 4 0 0 0 5 5L12 19a2.1 2.1 0 0 1-3-3l7.5-7.5a4 4 0 0 1-2-2zM5 4l3 3M4 8l4-4",
        "👁" to "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
        "👂" to "M7 9a5 5 0 0 1 10 0c0 3-3 4-3 7a3 3 0 0 1-5.5 1.5M10 9a2 2 0 0 1 4 0c0 1.2-1 1.8-1.5 2.5",
        "⚠" to "M12 4l9 16H3zM12 10v4M12 17.2v.1",
        "🎬" to "M3 9h18v11H3zM3 9l2-5 15 0-2 5M8 4l-1.5 5M13.5 4L12 9",
        "🖼" to "M3 4h18v16H3zM8.5 8.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM21 16l-5-5-9 9",
        "📄" to "M6 3h8l4 4v14H6zM14 3v4h4M9 12h6M9 15.5h6",
        "📊" to "M4 20h16M7 20v-6M12 20V6M17 20v-9",
        "💭" to "M7 15a4 4 0 0 1 .5-8A5 5 0 0 1 17 7a4 4 0 0 1 0 8zM8 18.5a1 1 0 1 0 0 .1M5 21a.5.5 0 1 0 0 .1",
        "🎓" to "M2.5 9L12 4.5 21.5 9 12 13.5zM6.5 11v5c3 2.5 8 2.5 11 0v-5M21.5 9v5",
        "🧭" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM15.5 8.5l-2 5-5 2 2-5z",
        "🔌" to "M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0zM12 17v4",
        "📷" to "M3 8h4l2-3h6l2 3h4v11H3zM12 10a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z",
        "🧠" to "M12 5a3 3 0 0 0-5.5 1.5A3 3 0 0 0 5 12a3 3 0 0 0 2 5 3 3 0 0 0 5 1.5zM12 5a3 3 0 0 1 5.5 1.5A3 3 0 0 1 19 12a3 3 0 0 1-2 5 3 3 0 0 1-5 1.5M12 5v14.5",
        "⚡" to "M13 3L5 13.5h6L10 21l8-10.5h-6z",
        "💻" to "M5 5h14v10H5zM2.5 18.5h19",
        "🗂" to "M3 6h6l2 2h10v11H3zM3 10h18",
        "📁" to "M3 6h6l2 2h10v11H3z",
        "🎵" to "M9 18V5l11-2v13M6.5 15.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM17.5 13.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z",
        "🏷" to "M3 12V3h9l9 9-9 9zM7.5 6.5a1 1 0 1 0 0 2 1 1 0 0 0 0-2z",
        "🔗" to "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
        "📅" to "M4 5h16v15H4zM4 10h16M8 3v4M16 3v4",
        "🧩" to "M5 8h3.5a2 2 0 1 1 4 0H16v3.5a2 2 0 1 1 0 4V19H5v-3.5a2 2 0 1 0 0-4z",
        "💡" to "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z",
        "🤔" to "M4 5h16v11H9l-5 4zM9.8 8.6a2.3 2.3 0 0 1 4.4 1c0 1.5-2.2 1.6-2.2 3M12 14.2v.1",
        "🖥" to "M3 4h18v12H3zM8 20h8M12 16v4",
        "🐙" to "M6 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM6 7v10M6 21a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18 7c0 6-12 4-12 10",
        "🥇" to "M8 3l2.5 6M16 3l-2.5 6M12 9a6 6 0 1 0 0 12 6 6 0 0 0 0-12zM11 13.5l1.5-1V18",
        "🗣" to "M9 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM2.5 20c.5-4 3-6 6.5-6s6 2 6.5 6M17 7.5a4 4 0 0 1 0 5M19.5 5a7.5 7.5 0 0 1 0 10",
        "🔒" to "M5 11h14v10H5zM8 11V7.5a4 4 0 0 1 8 0V11M12 15v2.5",
        "🔐" to "M5 11h14v10H5zM8 11V7.5a4 4 0 0 1 8 0V11M12 15v2.5",
        "🔓" to "M5 11h14v10H5zM8 11V7.5a4 4 0 0 1 7.6-1.7M12 15v2.5",
        "✉" to "M3 5h18v14H3zM3 6l9 7 9-7",
        "⚙" to "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1",
        "⏳" to "M6 3h12M6 21h12M7 3c0 5 10 5 10 9s-10 4-10 9M17 3c0 5-10 5-10 9s10 4 10 9",
        "👋" to "M8 13V5.5a1.5 1.5 0 0 1 3 0V11M11 10V4.5a1.5 1.5 0 0 1 3 0V11M14 10.5V6a1.5 1.5 0 0 1 3 0v7.5a7 7 0 0 1-12.4 4.5L3 15a1.6 1.6 0 0 1 2.5-2L8 15",
        "❓" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9.6 9.3a2.5 2.5 0 0 1 4.8 1c0 1.7-2.4 1.9-2.4 3.4M12 16.8v.1",
        "🔎" to "M11 4.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM16 16l4.5 4.5",
        "✍" to "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3",
        "🙂" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8.5 14a4.5 4.5 0 0 0 7 0M9 9.5v.1M15 9.5v.1",
        "🎯" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM12 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2z",
        "📶" to "M5 20v-3M10 20v-7M15 20V9M20 20V5",
        "📬" to "M3 13l3-8h12l3 8v6H3zM3 13h5l1.5 2.5h5L16 13h5",
        "📍" to "M12 21s-6.5-6-6.5-11a6.5 6.5 0 0 1 13 0c0 5-6.5 11-6.5 11zM12 7.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z",
        "🎙" to "M9 3h6v11H9zM5 11a7 7 0 0 0 14 0M12 18v3",
        "🖌" to "M14 4l6 6-7 7-6-6zM7 11l-2.5 2.5c-1.5 1.5-1 4-2 6.5 2.5-1 5-.5 6.5-2L11 15.5",
        "🎞" to "M4 3h16v18H4zM8 3v18M16 3v18M4 7.5h4M4 12h4M4 16.5h4M16 7.5h4M16 12h4M16 16.5h4",
        "🌐" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c3 3.2 3 14.8 0 18M12 3c-3 3.2-3 14.8 0 18",
        "🌡" to "M10 4a2 2 0 0 1 4 0v10a4 4 0 1 1-4 0zM12 9v8",
        "🎲" to "M4 4h16v16H4zM8.5 8.5v.1M15.5 8.5v.1M12 12v.1M8.5 15.5v.1M15.5 15.5v.1",
        "📰" to "M4 5h13v14H6a2 2 0 0 1-2-2zM17 9h3v8a2 2 0 0 1-2 2M7 9h7M7 12.5h7M7 16h4",
        "🎧" to "M4 15v-3a8 8 0 0 1 16 0v3M4 14h3v6H4zM17 14h3v6h-3z",
        "📝" to "M6 3h8l4 4v6M6 3v18h7M9 9h5M9 12.5h4M15 21l1-3 4.5-4.5 2 2L18 20z",
        "➰" to "M3 16c4 0 6-9 10-9a4 4 0 0 1 0 8c-4 0-3-8 2-8 2.5 0 4.5 2 6 4",
        "🗑" to "M4 7h16M10 7V4h4v3M6 7l1 14h10l1-14M10 11v6M14 11v6",
        "🧹" to "M14 3l-3 9M8 12h8l2 9H6zM10 21l.5-4M14 21l-.5-4",
        "📜" to "M7 4h11v14a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-2h11v2a3 3 0 0 0 3 3M7 4a2 2 0 0 0-2 2v10M10 8h5M10 11.5h5",
        "🎤" to "M9 3h6v11H9zM5 11a7 7 0 0 0 14 0M12 18v3",
        "🕘" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3 2",
        "💾" to "M4 4h13l3 3v13H4zM8 4v5h7V4M8 20v-6h8v6",
        "🌙" to "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z",
        "🔍" to "M11 4.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM16 16l4.5 4.5",
        "👤" to "M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM4 21c.5-4.5 3.5-7 8-7s7.5 2.5 8 7",
        "🔊" to "M4 9.5h4l5-4.5v14l-5-4.5H4zM16.5 9a4 4 0 0 1 0 6M19 6.5a7.5 7.5 0 0 1 0 11",
        "🎨" to "M12 3a9 9 0 0 0 0 18c1.5 0 2-1 1.5-2.2-.6-1.4.4-2.8 1.9-2.8H18a3 3 0 0 0 3-3c0-5.5-4-10-9-10zM7.5 11.5v.1M10 7.5v.1M14.5 7.5v.1",
        "✨" to "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z",
        "🙋" to "M12 7a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 21c0-5 2.5-8 6-8s6 3 6 8M18 13l2-6",
        "📦" to "M12 3l8 4v10l-8 4-8-4V7zM4 7l8 4 8-4M12 11v10M8 5l8 4",
        "🗃" to "M3 4h18v5H3zM4 9v11h16V9M10 13h4",
        "📲" to "M9 3h10v18H9zM13 18h2M2 11h8M7 8l3 3-3 3",
        "✋" to "M12 3v18M3 12h18M9.5 5.5L12 3l2.5 2.5M9.5 18.5L12 21l2.5-2.5M5.5 9.5L3 12l2.5 2.5M18.5 9.5L21 12l-2.5 2.5",
        // Signs that were the font's own (buttons and notes): drawn in the same family too.
        "⟳" to "M20 12a8 8 0 1 1-2.3-5.6M20 4v5h-5",
        "✎" to "M4 20h4L19 9l-4-4L4 16zM13 7l4 4",
        "⤓" to "M12 4v11M7 10l5 5 5-5M5 20h14",
        "⧉" to "M8 8h12v12H8zM4 16V4h12",
        "▶" to "M7 4l13 8-13 8z",
        "⏸" to "M8.5 5v14M15.5 5v14",
        "⛶" to "M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5",
        "↩" to "M9 5L4 10l5 5M4 10h10a5 5 0 0 1 0 10h-3",
        "☰" to "M4 6h16M4 12h16M4 18h16",
        "⋮" to "M12 3.7a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2zM12 10.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2zM12 18.1a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2z",
        "＋" to "M12 5v14M5 12h14",
        "✕" to "M6 6l12 12M18 6L6 18",
        "✓" to "M5 12.5l4.5 4.5L19 7",
        "✦" to "M12 4l1.8 6.2L20 12l-6.2 1.8L12 20l-1.8-6.2L4 12l6.2-1.8z",
        "⇄" to "M4 8h15M15 4l4 4-4 4M20 16H5M9 12l-4 4 4 4",
        "↔" to "M3 12h18M7 8l-4 4 4 4M17 8l4 4-4 4",
        "⤴" to "M5 19v-6a4 4 0 0 1 4-4h10M15 5l4 4-4 4",
        "▤" to "M3 5h18v14H3zM3 9h18M7 13h10M7 16h6",
        "👍" to "M7 10v10H3.5V10zM7 10l4-7c1.6 0 2.6 1.1 2.3 2.7L12.6 9H19a2 2 0 0 1 2 2.3l-1.2 6.9a2 2 0 0 1-2 1.8H7",
        "✂" to "M6 3.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM6 15.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM8.2 7.2L20 18M8.2 16.8L20 6",
        "■" to "M6.5 6.5h11v11h-11z",
        "ⓘ" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5v.1",
    ).mapValues { (k, d) -> line(k, d) }
}

/**
 * Line versions of the app's other pictograms (Material's and the app's own filled ones: bin, share, back, history,
 * microphone…), by the picture's name. With "Line icons" every pictogram in the app is one family, not a mix of thin
 * and filled ones.
 */
val LINE_SUBST: Map<String, ImageVector> by lazy {
    mapOf(
        "Close" to "M6 6l12 12M18 6L6 18",
        "Clear" to "M6 6l12 12M18 6L6 18",
        "Share" to "M18 3a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM6 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM18 16a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM8.2 10.8l7.6-4.1M8.2 13.2l7.6 4.1",
        "Send" to "M3 11l18-8-7 18-3-7zM11 14L21 3",
        "Delete" to "M4 7h16M10 3h4M6 7l1 14h10l1-14M10 11v6M14 11v6",
        "Refresh" to "M20 12a8 8 0 1 1-2.3-5.6M20 4v5h-5",
        "Edit" to "M4 20h4L19 9l-4-4L4 16zM13 7l4 4",
        "Create" to "M4 20h4L19 9l-4-4L4 16zM13 7l4 4",
        "PlayArrow" to "M7 4l13 8-13 8z",
        "Info" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5v.1",
        "Settings" to "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 5a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9L7 7M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1",
        "Lock" to "M5 11h14v10H5zM8 11V7a4 4 0 0 1 8 0v4M12 15v2",
        "Add" to "M12 5v14M5 12h14",
        "ArrowBack" to "M20 12H4M10 6l-6 6 6 6",
        "Search" to "M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM15.5 15.5L21 21",
        "MoreVert" to "M12 3.7a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2zM12 10.9a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2zM12 18.1a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2z",
        "Menu" to "M4 6h16M4 12h16M4 18h16",
        "Check" to "M5 12.5l4.5 4.5L19 7",
        "List" to "M9 6h11M9 12h11M9 18h11M4.5 6v.1M4.5 12v.1M4.5 18v.1",
        "ExitToApp" to "M10 4H5v16h5M14 8l4 4-4 4M18 12H9",
        "KeyboardArrowDown" to "M6 9l6 6 6-6",
        "Home" to "M3 11l9-7 9 7M5 10v10h5v-6h4v6h5V10",
        // The app's own filled pictures (Layout.kt), by the names they're built with.
        "History" to "M3.5 12a8.5 8.5 0 1 0 2.5-6M3.5 4v4.5H8M12 7.5V12l3.5 2",
        "Save" to "M5 3h11l3 3v15H5zM8 3v5h7V3M9 21v-6h6v6",
        "Mic" to "M9 5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0zM5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M9 21h6",
        "Gallery" to "M7 3h14v14H7zM3 7v14h14M7 14l4-4 3 3 2-2 5 5",
        "Automate" to "M19.5 10A8 8 0 0 0 5.6 7M4.5 14a8 8 0 0 0 13.9 3M5.5 3v4h4M18.5 21v-4h-4",
        "Test" to "M9 3h6M10 3v7l-5.5 9.2A1.2 1.2 0 0 0 5.5 21h13a1.2 1.2 0 0 0 1-1.8L14 10V3M7.2 15h9.6",
        "Code" to "M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16",
        "Power" to "M12 3v9M7 6.5a7.5 7.5 0 1 0 10 0",
        "Translate" to "M3 5h10M8 3v2M10.5 5c-1 4-3.5 7-7 9M5.5 8.5c1.5 2.5 3.5 4.5 6 5.5M12 21l4.5-11 4.5 11M13.6 17h5.8",
        "Agent" to "M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2zM12 8V4.5M12 3.5v.1M9 13v.1M15 13v.1M9.5 16.5h5M2 13v3M22 13v3",
        "Process" to "M9 6h11M9 12h11M9 18h11M4 5l1-1v4M3.5 11h2l-2 3h2M3.5 17h2l-1 1.5 1 1.5h-2",
        "Web search" to "M20.5 10A9 9 0 1 0 11 21M3 12h8M12 3c-2.5 2.5-3.5 5.5-3.5 9s1 6.5 3.5 9M12 3c2 2 3.2 4.4 3.5 7M16.5 13a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM18.7 18.7L21.5 21.5",
    ).mapValues { (k, d) -> line(k, d) }
}

/** [v] in the line family when Settings → Display → Pictograms draws line icons, else [v] itself. */
fun lineVersion(v: ImageVector, lines: Boolean): ImageVector =
    if (!lines) v else LINE_SUBST[v.name.substringAfterLast('.')] ?: LINE_SUBST[v.name] ?: v

/** The app's Icon: Material's, drawn in the line family with "Line icons" on (files use this one, not Material's). */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(lineVersion(imageVector, LocalLineIcons.current), contentDescription, modifier, tint)

/** The emoji's base character: a following U+FE0F (emoji style) is dropped, so "✉️" finds "✉". */
private fun emojiAt(s: String, i: Int): Pair<String, Int> {
    val cp = s.codePointAt(i)
    val n = Character.charCount(cp)
    val key = String(Character.toChars(cp))
    val skip = if (i + n < s.length && s[i + n] == '️') n + 1 else n
    return key to skip
}

/** [text] with every known emoji (and the stand-in signs in [extra]) as inline icons; null when there are none. */
fun iconized(text: String, extra: Map<String, ImageVector> = emptyMap(), lines: Boolean): AnnotatedString? {
    if (!lines && extra.isEmpty()) return null
    var found = false
    val out = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val (k, n) = emojiAt(text, i)
            when {
                k in extra -> { appendInlineContent(k, k); found = true }
                lines && k in LINE_ICONS -> { appendInlineContent(k, k); found = true }
                else -> append(text, i, i + n)
            }
            i += n
        }
    }
    return if (found) out else null
}

/** Inline pictograms for the keys used in [text], tinted like the text around them. */
@Composable
fun iconContent(text: AnnotatedString, extra: Map<String, ImageVector>, tint: Color, size: Float = 1.2f, tintFor: (String) -> Color = { tint }): Map<String, InlineTextContent> =
    text.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, text.length).map { it.item }.distinct().let { keys ->
    val lines = LocalLineIcons.current
    keys.associateWith { k ->
        val icon = extra[k]?.let { lineVersion(it, lines) } ?: LINE_ICONS[k]
        InlineTextContent(Placeholder(size.em, size.em, PlaceholderVerticalAlign.TextCenter)) {
            if (icon != null) androidx.compose.material3.Icon(icon, contentDescription = null, tint = tintFor(k), modifier = Modifier)
        }
    }
    }

/** [iconContent], each pictogram tinted as Settings → Display → Pictograms says. */
@Composable
fun iconContentTinted(text: AnnotatedString, extra: Map<String, ImageVector>, tint: Color, size: Float = 1.2f, special: (String) -> Color? = { null }): Map<String, InlineTextContent> {
    val colourful = LocalLineColour.current
    val lines = LocalLineIcons.current
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    return iconContent(text, extra, tint, size) { k ->
        when {
            lines && !colourful -> accent // line icons in the style's colour: every pictogram, the web sign too
            else -> special(k) ?: if (lines) LINE_COLOURS[k] ?: tint else tint
        }
    }
}

/**
 * The app's Text: exactly Material's, except that with Settings → Display → Pictograms → "Line icons" the colour
 * emoji in it are drawn as the app's line pictograms. (Files use this one instead of importing Material's.)
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val icons = if (LocalLineIcons.current) iconized(text, lines = true) else null
    if (icons == null) {
        androidx.compose.material3.Text(
            text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign,
            lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style,
        )
    } else {
        val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
        androidx.compose.material3.Text(
            icons, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign,
            lineHeight, overflow, softWrap, maxLines, minLines, iconContentTinted(icons, emptyMap(), tint), onTextLayout ?: {}, style,
        )
    }
}

/** Material's Text for styled text, passed straight through (so files need only this package's Text). */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign,
    lineHeight, overflow, softWrap, maxLines, minLines, inlineContent, onTextLayout, style,
)

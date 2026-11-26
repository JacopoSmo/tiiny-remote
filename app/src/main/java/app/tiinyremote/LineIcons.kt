package app.tiinyremote

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
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
        "👤" to slate, "🔊" to blue, "🎨" to pink, "✨" to amber, "🙋" to amber, "📦" to brown, "🗃" to brown, "📲" to green,
    )
}

/** The tint of pictogram [k] in a text of colour [text]: its own (colourful), the style's accent, or the text's. */
@Composable
fun pictoTint(k: String, text: Color, accent: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary): Color = when {
    !LocalLineIcons.current -> text
    LocalLineColour.current -> LINE_COLOURS[k] ?: text
    else -> accent
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
    ).mapValues { (k, d) -> line(k, d) }
}

/** The emoji's base character: a following U+FE0F (emoji style) is dropped, so "✉️" finds "✉". */
private fun emojiAt(s: String, i: Int): Pair<String, Int> {
    val cp = s.codePointAt(i)
    val n = Character.charCount(cp)
    val key = String(Character.toChars(cp))
    val skip = if (i + n < s.length && s[i + n] == '️') n + 1 else n
    return key to skip
}

/** True when [text] holds a pictogram this setting would draw. */
fun hasLineIcons(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val (k, n) = emojiAt(text, i)
        if (k in LINE_ICONS) return true
        i += n
    }
    return false
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
    text.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 0, text.length).map { it.item }.distinct().associateWith { k ->
        val icon = extra[k] ?: LINE_ICONS[k]
        InlineTextContent(Placeholder(size.em, size.em, PlaceholderVerticalAlign.TextCenter)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = tintFor(k), modifier = Modifier)
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

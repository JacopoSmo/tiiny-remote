package app.tiinyremote

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Settings → Display → Style: the app's colours from the phone's wallpaper (Android's own, the standard), or one of
 * three designed looks, each in a light and a dark version:
 *  A · Ink & Paper: paper white and ink, a deep teal; serif titles.
 *  B · Graphite & Brass: graphite with brass; a clean sans-serif throughout.
 *  C · Alpine Slate: mist and slate with glacier blue; rounded titles.
 */
object Look {
    val STYLES = listOf(
        "wallpaper" to "Colours from your wallpaper (standard)",
        "a" to "A · Ink & Paper",
        "b" to "B · Graphite & Brass",
        "c" to "C · Alpine Slate",
    )

    private class Tones(
        val bg: Long, val c0: Long, val c1: Long, val c2: Long, val c3: Long, val c4: Long,
        val text: Long, val muted: Long, val outline: Long, val line: Long,
        val accent: Long, val onAccent: Long, val accentBox: Long, val onAccentBox: Long,
    )

    private fun scheme(dark: Boolean, t: Tones): ColorScheme {
        fun c(v: Long) = Color(v)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = c(t.accent), onPrimary = c(t.onAccent), primaryContainer = c(t.accentBox), onPrimaryContainer = c(t.onAccentBox),
            secondary = c(t.accent), onSecondary = c(t.onAccent), secondaryContainer = c(t.accentBox), onSecondaryContainer = c(t.onAccentBox),
            tertiary = c(t.accent), onTertiary = c(t.onAccent), tertiaryContainer = c(t.accentBox), onTertiaryContainer = c(t.onAccentBox),
            background = c(t.bg), onBackground = c(t.text), surface = c(t.bg), onSurface = c(t.text),
            surfaceVariant = c(t.c3), onSurfaceVariant = c(t.muted), surfaceTint = c(t.accent),
            inverseSurface = c(t.text), inverseOnSurface = c(t.bg), inversePrimary = c(t.accentBox),
            outline = c(t.outline), outlineVariant = c(t.line),
            surfaceBright = c(t.c1), surfaceDim = c(t.bg),
            surfaceContainerLowest = c(t.c0), surfaceContainerLow = c(t.c1), surfaceContainer = c(t.c2),
            surfaceContainerHigh = c(t.c3), surfaceContainerHighest = c(t.c4),
        )
    }

    /** The colours for [style] (null for the wallpaper's, which Android makes). */
    fun colors(style: String, dark: Boolean): ColorScheme? = when (style) {
        "a" -> if (dark) scheme(true, Tones(0xFF161514, 0xFF121110, 0xFF1C1B19, 0xFF211F1D, 0xFF282624, 0xFF2F2C29,
            0xFFECE8E1, 0xFFABA59B, 0xFF7C766C, 0xFF3A3733, 0xFF7FC1B6, 0xFF0B2E2A, 0xFF1F4A45, 0xFFCFE9E4))
        else scheme(false, Tones(0xFFF5F3EE, 0xFFFFFFFF, 0xFFFBFAF7, 0xFFF0EDE6, 0xFFEAE6DD, 0xFFE4DFD5,
            0xFF1C1B19, 0xFF625C53, 0xFF8C857A, 0xFFDDD8CE, 0xFF1F5C55, 0xFFFFFFFF, 0xFFCFE3DF, 0xFF0F3B36))
        "b" -> if (dark) scheme(true, Tones(0xFF121213, 0xFF0E0E0F, 0xFF1B1B1D, 0xFF202023, 0xFF26262A, 0xFF2D2D31,
            0xFFEEEBE5, 0xFFA29E97, 0xFF6E6B66, 0xFF2D2D31, 0xFFC9A86A, 0xFF1A1610, 0xFF3A3124, 0xFFEBD9B4))
        else scheme(false, Tones(0xFFF4F3F0, 0xFFFFFFFF, 0xFFFAF9F7, 0xFFEFEDE9, 0xFFE9E7E2, 0xFFE2DFD9,
            0xFF1A1A1B, 0xFF5E5B56, 0xFF8A867F, 0xFFDAD7D0, 0xFF7E6223, 0xFFFFFFFF, 0xFFEADDC0, 0xFF3A2C0E))
        "c" -> if (dark) scheme(true, Tones(0xFF0E1620, 0xFF0A111A, 0xFF16202C, 0xFF1B2633, 0xFF202C3A, 0xFF263342,
            0xFFE4EBF1, 0xFF9DAAB6, 0xFF6B7885, 0xFF26323F, 0xFF8FB4DB, 0xFF0B2440, 0xFF1E3F61, 0xFFD3E3F3))
        else scheme(false, Tones(0xFFEEF2F5, 0xFFFFFFFF, 0xFFF7F9FB, 0xFFE8EDF1, 0xFFE1E8ED, 0xFFDAE2E9,
            0xFF0F1B26, 0xFF4F5E6B, 0xFF7E8B96, 0xFFD2DBE3, 0xFF2D5D8B, 0xFFFFFFFF, 0xFFD3E3F3, 0xFF0E2D4A))
        else -> null
    }

    @OptIn(ExperimentalTextApi::class)
    private fun family(res: Int) = FontFamily(
        listOf(400, 500, 600, 700).map { w ->
            Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )

    private val inter by lazy { family(R.font.inter) }
    private val interTight by lazy { family(R.font.inter_tight) }
    private val manrope by lazy { family(R.font.manrope) }
    private val newsreader by lazy { family(R.font.newsreader) }

    val TYPEFACES = listOf(
        "system" to "Phone's font",
        "pair" to "Designed pair",
        "one" to "Inter everywhere",
    )

    /**
     * One type scale everywhere, five levels: every text style maps onto one of them, whatever the typeface.
     *  1 page titles 22 · 2 section headings 18 semibold · 3 card titles 16 semibold · 4 body (and buttons) 15 ·
     *  5 notes, captions and small labels 12.
     * Typefaces: the phone's font; the designed pair (Inter for text, titles and section headings in the style's own
     * face: Newsreader for A, Inter Tight for B and the wallpaper colours, Manrope for C); or Inter everywhere.
     */
    fun typography(style: String, faces: String): Typography {
        val base = Typography()
        val text: FontFamily? = if (faces == "system") null else inter
        val head: FontFamily? = when (faces) {
            "pair" -> when (style) { "a" -> newsreader; "c" -> manrope; else -> interTight }
            "one" -> inter
            else -> null
        }
        fun lvl(t: TextStyle, size: Int, line: Int, weight: FontWeight?, family: FontFamily?) =
            t.copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight ?: t.fontWeight, fontFamily = family ?: t.fontFamily, letterSpacing = 0.sp)
        val page = { t: TextStyle -> lvl(t, 22, 28, FontWeight.Medium, head) }
        val section = { t: TextStyle -> lvl(t, 18, 24, FontWeight.SemiBold, head) }
        val card = { t: TextStyle -> lvl(t, 16, 22, FontWeight.SemiBold, text) }
        val body = { t: TextStyle -> lvl(t, 15, 21, null, text) }
        val note = { t: TextStyle -> lvl(t, 12, 16, null, text) }
        return base.copy(
            displayLarge = page(base.displayLarge), displayMedium = page(base.displayMedium), displaySmall = page(base.displaySmall),
            headlineLarge = page(base.headlineLarge), headlineMedium = page(base.headlineMedium), headlineSmall = page(base.headlineSmall),
            titleLarge = page(base.titleLarge),
            titleMedium = section(base.titleMedium),
            titleSmall = card(base.titleSmall),
            bodyLarge = body(base.bodyLarge), bodyMedium = body(base.bodyMedium), labelLarge = body(base.labelLarge).copy(fontWeight = FontWeight.Medium),
            bodySmall = note(base.bodySmall), labelMedium = note(base.labelMedium), labelSmall = note(base.labelSmall),
        )
    }
}

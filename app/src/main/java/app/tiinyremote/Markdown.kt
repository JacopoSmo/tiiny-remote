package app.tiinyremote

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/**
 * Chat answers in light Markdown: headings, **bold**, *italics*, `code`, code blocks, lists, quotes and links.
 * Only http(s) links can be tapped, so an answer can't open another app through a link like intent://.
 */
@Composable
fun MarkdownText(text: String) {
    val link = MaterialTheme.colorScheme.primary
    val code = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val quote = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = remember(text, link, code, quote) { markdown(text, link, code, quote) }
    Text(shown)
}

private val heading = Regex("^(#{1,6})\\s+(.*)$")
private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val numbered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val rule = Regex("^\\s*([-*_])\\s*(\\1\\s*){2,}$")
private val inline = Regex(
    "\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\[([^\\]]+)]\\(([^)\\s]+)\\)|" +
        "(?<![\\w*])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![\\w*])|(https?://[^\\s<>()\\[\\]]+[^\\s<>()\\[\\].,;:!?'\"])",
)

private fun markdown(text: String, link: Color, code: Color, quote: Color): AnnotatedString = buildAnnotatedString {
    var inCode = false
    val lines = text.lines()
    for ((i, raw) in lines.withIndex()) {
        if (raw.trimStart().startsWith("```")) {
            inCode = !inCode
            continue
        }
        if (inCode) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = code, fontSize = 0.9.em)) { append(raw) }
        } else {
            val h = heading.find(raw)
            val b = bullet.find(raw)
            val n = numbered.find(raw)
            when {
                h != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (h.groupValues[1].length <= 2) 1.15.em else 1.05.em)) {
                    inlines(h.groupValues[2], link, code)
                }
                rule.matches(raw) -> append("────────")
                b != null -> {
                    append(b.groupValues[1].replace("\t", "  ") + "• ")
                    inlines(b.groupValues[2], link, code)
                }
                n != null -> {
                    append(n.groupValues[1] + n.groupValues[2] + ". ")
                    inlines(n.groupValues[3], link, code)
                }
                raw.startsWith(">") -> withStyle(SpanStyle(color = quote, fontStyle = FontStyle.Italic)) {
                    inlines(raw.removePrefix(">").trimStart(), link, code)
                }
                else -> inlines(raw, link, code)
            }
        }
        if (i < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.inlines(s: String, link: Color, code: Color) {
    var at = 0
    for (m in inline.findAll(s)) {
        append(s.substring(at, m.range.first))
        val g = m.groups
        when {
            g[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]!!.value) }
            g[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]!!.value) }
            g[3] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = code)) { append(g[3]!!.value) }
            g[4] != null -> linkOrText(g[4]!!.value, g[5]!!.value, link)
            g[6] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]!!.value) }
            else -> linkOrText(m.value, m.value, link)
        }
        at = m.range.last + 1
    }
    append(s.substring(at))
}

private fun AnnotatedString.Builder.linkOrText(label: String, url: String, color: Color) {
    if (url.startsWith("https://", true) || url.startsWith("http://", true)) {
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)))) {
            append(label)
        }
    } else {
        append(label)
    }
}

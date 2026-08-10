package com.open.note.render

import android.graphics.Color
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan

/**
 * TipTap HTML → Spannable 解析器
 * 支持：彩色文字(var(--xxxColor))、高亮(class="highlight_xxx")、
 *       实线/波浪下划线(<u class="underline_solid/wavy_color_xxx">)、粗体/斜体/删除线
 */
object HtmlToSpannableParser {

    /** CSS 变量名 → 浅色模式颜色（与 Web 版 COLOR_PRESETS 对齐） */
    private val colorVarMap = mapOf(
        "--blueColor" to Color.parseColor("#1A73E8"),
        "--redColor" to Color.parseColor("#EA4335"),
        "--greenColor" to Color.parseColor("#34A853"),
        "--orangeColor" to Color.parseColor("#FB9600"),
        "--yellowColor" to Color.parseColor("#F9AB00"),
        "--grayColor" to Color.parseColor("#5F6368")
    )

    /** CSS 变量名 → 深色模式颜色 */
    private val colorVarMapDark = mapOf(
        "--blueColor" to Color.parseColor("#8AB4F8"),
        "--redColor" to Color.parseColor("#F28B82"),
        "--greenColor" to Color.parseColor("#81C995"),
        "--orangeColor" to Color.parseColor("#FDD663"),
        "--yellowColor" to Color.parseColor("#FDE293"),
        "--grayColor" to Color.parseColor("#BDC1C6")
    )

    private val highlightColorMap = mapOf(
        "highlight_yellow" to Color.parseColor("#4DF7C600"),
        "highlight_red" to Color.parseColor("#4DFFADBE"),
        "highlight_blue" to Color.parseColor("#4D55B8F1"),
        "highlight_green" to Color.parseColor("#4D68D179")
    )

    private val COLOR_INLINE_REGEX = Regex(
        """<span[^>]*style="[^"]*color:\s*var\((--\w+)\)[^"]*"[^>]*>(.*?)</span>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    private val HIGHLIGHT_REGEX = Regex(
        """<span class="(highlight_\w+)">(.*?)</span>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val SOLID_UNDERLINE_REGEX = Regex(
        """<u class="underline_solid_(color_\w+)"[^>]*>(.*?)</u>""",
        RegexOption.DOT_MATCHES_ALL
    )

    private val WAVY_UNDERLINE_REGEX = Regex(
        """<u class="underline_wavy_(color_\w+)"[^>]*>(.*?)</u>""",
        RegexOption.DOT_MATCHES_ALL
    )

    /** color_xxx → 颜色（兼容旧 class 格式，不含 var()） */
    private val colorClassMap = mapOf(
        "color_default" to Color.parseColor("#CC000000"),
        "color_gray" to Color.parseColor("#42000000"),
        "color_red" to Color.parseColor("#D54933"),
        "color_orange" to Color.parseColor("#E18413"),
        "color_yellow" to Color.parseColor("#DB9A00"),
        "color_green" to Color.parseColor("#2C8848"),
        "color_blue" to Color.parseColor("#3258C5")
    )

    @Volatile
    private var darkMode = false

    fun setDarkMode(isDark: Boolean) {
        darkMode = isDark
    }

    fun parse(html: String, isDark: Boolean = darkMode): SpannableStringBuilder {
        val base = html ?: return SpannableStringBuilder("")
        val sb = SpannableStringBuilder(Html.fromHtml(base, 0))
        val plain = sb.toString()

        parseInlineColor(sb, plain, base, isDark)
        parseHighlight(sb, plain, base)
        parseUnderline(sb, plain, base)
        return sb
    }

    private fun parseInlineColor(sb: SpannableStringBuilder, plain: String, html: String, isDark: Boolean) {
        for (match in COLOR_INLINE_REGEX.findAll(html)) {
            val varName = match.groupValues[1]
            val innerText = match.groupValues[2].let { stripTags(it) }
            val color = (if (isDark) colorVarMapDark else colorVarMap)[varName] ?: continue
            applyFirstUnmarked(sb, plain, innerText) { idx, end ->
                sb.setSpan(ForegroundColorSpan(color), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun parseHighlight(sb: SpannableStringBuilder, plain: String, html: String) {
        for (match in HIGHLIGHT_REGEX.findAll(html)) {
            val className = match.groupValues[1]
            val innerText = match.groupValues[2].let { stripTags(it) }
            val bgColor = highlightColorMap[className] ?: continue
            applyFirstUnmarked(sb, plain, innerText) { idx, end ->
                sb.setSpan(CustomHighlightSpan(bgColor), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun parseUnderline(sb: SpannableStringBuilder, plain: String, html: String) {
        for (match in SOLID_UNDERLINE_REGEX.findAll(html)) {
            val innerText = match.groupValues[2].let { stripTags(it) }
            val color = colorClassMap[match.groupValues[1]] ?: Color.BLACK
            applyFirstUnmarked(sb, plain, innerText) { idx, end ->
                sb.setSpan(ColoredUnderlineSpan(color), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        for (match in WAVY_UNDERLINE_REGEX.findAll(html)) {
            val innerText = match.groupValues[2].let { stripTags(it) }
            val color = colorClassMap[match.groupValues[1]] ?: Color.BLACK
            applyFirstUnmarked(sb, plain, innerText) { idx, end ->
                sb.setSpan(WavyUnderlineSpan(color), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    /** 在 plain text 中找 innerText 首次未被目标 span 覆盖的位置并应用 */
    private fun applyFirstUnmarked(
        sb: SpannableStringBuilder, plain: String, innerText: String,
        apply: (Int, Int) -> Unit
    ) {
        if (innerText.isEmpty()) return
        var searchFrom = 0
        while (true) {
            val idx = plain.indexOf(innerText, searchFrom)
            if (idx == -1) break
            val end = idx + innerText.length
            val hasFormat = sb.getSpans(idx, end, Any::class.java)
                .any { it is ForegroundColorSpan || it is CustomHighlightSpan ||
                        it is ColoredUnderlineSpan || it is WavyUnderlineSpan }
            if (!hasFormat) {
                apply(idx, end)
                return
            }
            searchFrom = idx + 1
        }
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()

    /** 供外部直接构造 Span 工具：从 CSS 变量名取色（浅色） */
    fun colorForVar(varName: String): Int? = colorVarMap[varName]
}

package com.open.note.render

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Spanned → Compose AnnotatedString 转换
 * 支持颜色/高亮背景/粗体/斜体/删除线/下划线
 */
object SpannedToAnnotated {

    fun convert(text: Spanned): AnnotatedString {
        val builder = AnnotatedString.Builder(text.toString())

        // ForegroundColorSpan / StyleSpan / StrikethroughSpan / UnderlineSpan / BackgroundColorSpan
        for (span in text.getSpans(0, text.length, Any::class.java)) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start >= end || start < 0 || end > text.length) continue

            when (span) {
                is ForegroundColorSpan -> {
                    builder.addStyle(
                        SpanStyle(color = Color(span.foregroundColor)),
                        start, end
                    )
                }
                is BackgroundColorSpan -> {
                    builder.addStyle(
                        SpanStyle(background = Color(span.backgroundColor)),
                        start, end
                    )
                }
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> builder.addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold), start, end
                    )
                    Typeface.ITALIC -> builder.addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic), start, end
                    )
                    Typeface.BOLD_ITALIC -> builder.addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                        start, end
                    )
                }
                is StrikethroughSpan -> builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough), start, end
                )
                is UnderlineSpan -> builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline), start, end
                )
            }
        }
        return builder.toAnnotatedString()
    }
}

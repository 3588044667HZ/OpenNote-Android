package com.open.note.render

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.style.CharacterStyle

/**
 * 有色实线下划线 Span
 * 对应 Web: <u class="underline_solid_color_xxx">
 */
class ColoredUnderlineSpan(private val underlineColor: Int) : CharacterStyle() {

    override fun updateDrawState(tp: TextPaint) {
        tp.isUnderlineText = true
        tp.underlineColor = underlineColor
    }
}

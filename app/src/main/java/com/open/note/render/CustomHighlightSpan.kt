package com.open.note.render

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * 高亮背景 Span — 处理跨行背景色填充
 * 对应 Web: <span class="highlight_xxx">
 */
class CustomHighlightSpan(private val bgColor: Int) : ReplacementSpan() {

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val originalColor = paint.color
        val rectWidth = paint.measureText(text, start, end)

        paint.color = bgColor
        canvas.drawRect(x, top.toFloat(), x + rectWidth, bottom.toFloat(), paint)

        paint.color = originalColor
        canvas.drawText(text!!, start, end, x, y.toFloat(), paint)
    }
}

package com.open.note.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.style.ReplacementSpan

/**
 * 有色波浪线 Span
 * 对应 Web: <u class="underline_wavy_color_xxx">
 * 波形与 Web 版 SVG 一致 (M5 1 C3 1 2.5 3 0 3 ...)
 */
class WavyUnderlineSpan(private val color: Int) : ReplacementSpan() {

    private val wavePaint = Paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun getSize(
        paint: Paint, text: CharSequence?, start: Int, end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val width = paint.measureText(text, start, end)
        if (fm != null) {
            val origin = paint.fontMetricsInt
            fm.ascent = origin.ascent
            fm.descent = origin.descent + 8
            fm.top = origin.top
            fm.bottom = origin.bottom + 8
        }
        return width.toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        canvas.drawText(text!!, start, end, x, y.toFloat(), paint)

        val textWidth = paint.measureText(text, start, end)
        val waveY = y + 4f
        canvas.drawPath(buildWavyPath(x, waveY, textWidth), wavePaint)
    }

    private fun buildWavyPath(startX: Float, baseY: Float, totalWidth: Float): Path {
        val path = Path()
        val waveWidth = 10f
        val amplitude = 3f

        var x = startX
        while (x < startX + totalWidth) {
            path.moveTo(x, baseY)
            path.cubicTo(
                x + 1.75f, baseY - amplitude,
                x + 2.5f, baseY,
                x + 5f, baseY - amplitude
            )
            path.cubicTo(
                x + 7.5f, baseY - amplitude,
                x + 8.25f, baseY,
                x + 10f, baseY
            )
            x += waveWidth
        }
        return path
    }
}

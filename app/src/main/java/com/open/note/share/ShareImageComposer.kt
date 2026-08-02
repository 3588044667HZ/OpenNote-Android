package com.open.note.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class ShareImageComposer(private val context: Context) {

    private val density: Float = context.resources.displayMetrics.density

    fun build(
        capturePaths: List<String>,
        colors: ShareColors,
        targetWidth: Int,
        watermark: ShareWatermarkConfig = ShareWatermarkConfig()
    ): View {
        val root = FrameLayout(context)
        val skinContainer = RelativeLayout(context)
        skinContainer.setBackgroundColor(Color.parseColor(colors.backcloth))

        val captureLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }
        for (path in capturePaths) {
            captureLayout.addView(buildImageView(path, targetWidth - dp(48)))
        }
        skinContainer.addView(captureLayout, RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(24), dp(24), dp(24), 0)
            addRule(RelativeLayout.CENTER_HORIZONTAL)
        })

        addFooter(skinContainer, captureLayout.id, colors, watermark)
        root.addView(skinContainer)
        return root
    }

    fun render(view: View, width: Int, height: Int): Bitmap {
        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        view.measure(wSpec, hSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun buildImageView(path: String, maxW: Int): ImageView {
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            val ratio = (opts.outWidth / maxW).coerceAtLeast(1)
            setImageBitmap(BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = ratio }))
        }
    }

    private fun addFooter(container: RelativeLayout, aboveId: Int, colors: ShareColors, watermark: ShareWatermarkConfig) {
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(16)); id = View.generateViewId()
        }

        val divider = View(context).apply { setBackgroundColor(Color.parseColor(colors.borderColor)) }
        footer.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            setMargins(dp(24), 0, dp(24), dp(16))
        })

        if (watermark.showLogo) {
            footer.addView(TextView(context).apply {
                text = watermark.logoText
                setTextColor(watermark.customColor ?: Color.parseColor(colors.timeColor))
                textSize = 12f
            })
        }
        footer.addView(TextView(context).apply {
            text = watermark.textCn
            setTextColor(watermark.customColor ?: Color.parseColor(colors.timeColor))
            textSize = 11f; alpha = 0.7f
        })

        val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.addRule(RelativeLayout.BELOW, aboveId)
        container.addView(footer, params)
    }

    private fun dp(v: Int): Int = (v * density).toInt()
}

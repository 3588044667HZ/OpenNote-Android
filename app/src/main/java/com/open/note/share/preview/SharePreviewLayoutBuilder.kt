package com.open.note.share.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.open.note.share.ShareColors
import com.open.note.share.ShareWatermarkConfig
import java.io.File

class SharePreviewLayoutBuilder(private val ctx: Context) {

    lateinit var skinContainer: RelativeLayout
    lateinit var captureLayout: LinearLayout

    private val dp: Float get() = ctx.resources.displayMetrics.density

    fun build(
        colors: ShareColors, watermark: ShareWatermarkConfig, noteTitle: String,
        onSave: () -> Unit, onShare: () -> Unit, onBack: () -> Unit
    ): View {
        val root = FrameLayout(ctx).apply { setBackgroundColor(Color.parseColor(colors.backcloth)) }

        val topBar = buildTopBar(onBack)
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(56)).apply { gravity = Gravity.TOP })

        val scrollView = ScrollView(ctx).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        skinContainer = RelativeLayout(ctx).apply {
            setBackgroundColor(Color.parseColor(colors.contentBg))
            clipToPadding = false
        }

        captureLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; id = View.generateViewId()
        }
        skinContainer.addView(captureLayout, RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dpi(24), dpi(24), dpi(24), 0) })

        if (noteTitle.isNotEmpty()) {
            val titleView = TextView(ctx).apply {
                text = noteTitle; textSize = 24f; setTextColor(Color.parseColor(colors.titleColor))
                setPadding(dpi(24), dpi(16), dpi(24), dpi(14))
                setTypeface(Typeface.DEFAULT_BOLD)
                id = View.generateViewId()
            }
            skinContainer.addView(titleView, RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpi(24), dpi(12), dpi(24), dpi(8))
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            })
        }

        buildFooter(colors, watermark)
        scrollView.addView(skinContainer)
        root.addView(scrollView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP).apply {
            topMargin = dpi(56); bottomMargin = dpi(64)
        })

        val bottomBar = buildBottomBar(colors, onSave, onShare)
        root.addView(bottomBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(64)).apply { gravity = Gravity.BOTTOM })
        return root
    }

    fun addCaptures(paths: List<String>, maxWidth: Int) {
        captureLayout.removeAllViews()
        for (path in paths) {
            if (!File(path).exists()) continue
            val iv = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                val ratio = (opts.outWidth / maxWidth).coerceAtLeast(1)
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = ratio })?.let { setImageBitmap(it) }
            }
            captureLayout.addView(iv)
        }
    }

    fun applySkinBackground(contentBg: String) {
        skinContainer.setBackgroundColor(Color.parseColor(contentBg))
    }

    private fun buildTopBar(onBack: () -> Unit): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(4), 0, dpi(16), 0); setBackgroundColor(Color.WHITE)
            val backBtn = TextView(ctx).apply {
                text = "←"; textSize = 20f; setPadding(dpi(12), dpi(12), dpi(12), dpi(12))
                setOnClickListener { onBack() }
            }
            addView(backBtn)
            val title = TextView(ctx).apply { text = "分享预览"; textSize = 18f; setPadding(dpi(8), 0, 0, 0) }
            addView(title)
        }
    }

    private fun buildFooter(colors: ShareColors, watermark: ShareWatermarkConfig) {
        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(0, dpi(24), 0, dpi(16))
        }
        val divider = View(ctx).apply { setBackgroundColor(Color.parseColor(colors.borderColor)); alpha = 0.15f }
        footer.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            setMargins(dpi(24), 0, dpi(24), dpi(16))
        })
        if (watermark.showLogo) {
            footer.addView(TextView(ctx).apply {
                text = watermark.logoText; setTextColor(Color.parseColor(colors.timeColor)); textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        footer.addView(TextView(ctx).apply {
            text = watermark.textCn; setTextColor(Color.parseColor(colors.timeColor)); textSize = 11f; alpha = 0.7f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.addRule(RelativeLayout.BELOW, captureLayout.id)
        skinContainer.addView(footer, params)
    }

    private fun buildBottomBar(colors: ShareColors, onSave: () -> Unit, onShare: () -> Unit): LinearLayout {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor(colors.contentBg)); gravity = Gravity.CENTER
        }
        val sep = View(ctx).apply { setBackgroundColor(Color.parseColor(colors.borderColor)) }
        bar.addView(sep, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { gravity = Gravity.TOP })
        bar.addView(buildBtn("保存到本地", colors, onSave))
        bar.addView(buildBtn("分享", colors, onShare))
        return bar
    }

    private fun buildBtn(text: String, colors: ShareColors, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor(colors.textColor)); setPadding(dpi(16), dpi(8), dpi(16), dpi(8))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    fun dpi(v: Int): Int = (v * dp).toInt()
}

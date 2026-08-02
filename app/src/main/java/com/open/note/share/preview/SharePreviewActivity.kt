package com.open.note.share.preview

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.open.note.share.ImageExporter
import com.open.note.share.ShareColors
import com.open.note.share.ShareWatermarkConfig

class SharePreviewActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var layoutBuilder: SharePreviewLayoutBuilder
    private var capturePaths: List<String> = emptyList()
    private var colors: ShareColors = ShareColors.WHITE
    private var noteTitle: String = ""
    private var watermark: ShareWatermarkConfig = ShareWatermarkConfig()
    private var loadingDialog: Dialog? = null
    private var isCreatingImage = false
    private var generatedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parseIntent()
        layoutBuilder = SharePreviewLayoutBuilder(this)

        val rootView = layoutBuilder.build(colors, watermark, noteTitle,
            onSave = { onExportClicked(ExportType.SAVE_TO_GALLERY) },
            onShare = { onExportClicked(ExportType.SHARE) },
            onBack = { finish() }
        )
        setContentView(rootView)

        val maxWidth = resources.displayMetrics.widthPixels - layoutBuilder.dpi(48)
        layoutBuilder.addCaptures(capturePaths, maxWidth)
        layoutBuilder.applySkinBackground(colors.contentBg)
    }

    private fun parseIntent() {
        capturePaths = intent.getStringArrayListExtra(EXTRA_CAPTURE_PATHS) ?: emptyList()
        noteTitle = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: ""
        intent.getStringExtra(EXTRA_COLORS_JSON)?.let { json ->
            try {
                val map = org.json.JSONObject(json)
                colors = ShareColors(
                    contentBg = map.optString("contentBg", "#FFFFFF"),
                    backcloth = map.optString("backcloth", "#FAFAFA"),
                    textColor = map.optString("textColor", "#1A1A1A"),
                    titleColor = map.optString("titleColor", "#000000"),
                    timeColor = map.optString("timeColor", "#999999"),
                    borderColor = map.optString("borderColor", "#0000001F"))
            } catch (_: Exception) {}
        }
    }

    private fun onExportClicked(type: ExportType) {
        if (isCreatingImage) return
        isCreatingImage = true
        showLoading()
        handler.postDelayed({ renderAndExport(type) }, 50)
    }

    private fun renderAndExport(type: ExportType) {
        try {
            val container = layoutBuilder.skinContainer
            val w = if (container.width > 0) container.width else container.measuredWidth
            val h = if (container.height > 0) container.height else container.measuredHeight
            if (w <= 0 || h <= 0) { handler.postDelayed({ renderAndExport(type) }, 100); return }

            val bitmap = createBitmap(container, w, h)
            generatedBitmap = bitmap
            dismissLoading()

            when (type) {
                ExportType.SAVE_TO_GALLERY -> {
                    val uri = ImageExporter.saveToGallery(this, bitmap)
                    Toast.makeText(this, if (uri != null) "已保存到相册" else "保存失败", Toast.LENGTH_SHORT).show()
                }
                ExportType.SHARE -> {
                    ImageExporter.shareViaIntent(this, bitmap, "$packageName.fileprovider")
                }
            }
        } catch (e: Exception) {
            dismissLoading()
            Toast.makeText(this, "图片生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally { isCreatingImage = false }
    }

    private fun showLoading() {
        loadingDialog?.dismiss()
        loadingDialog = Dialog(this).apply {
            setContentView(LinearLayout(this@SharePreviewActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dpi(40), dpi(32), dpi(40), dpi(32))
                addView(ProgressBar(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                addView(TextView(context).apply { text = "正在生成图片..."; setPadding(0, dpi(12), 0, 0) })
            })
            setCancelable(false); setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        }
        loadingDialog?.show()
    }

    private fun dismissLoading() { loadingDialog?.dismiss(); loadingDialog = null }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        generatedBitmap?.recycle(); generatedBitmap = null
        loadingDialog?.dismiss()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun dpi(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun createBitmap(v: View, width: Int, height: Int): Bitmap {
            v.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, v.measuredWidth, v.measuredHeight)
            val bitmap = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bitmap))
            return bitmap
        }
        const val EXTRA_CAPTURE_PATHS = "capture_paths"
        const val EXTRA_COLORS_JSON = "colors_json"
        const val EXTRA_NOTE_TITLE = "note_title"
    }

    enum class ExportType { SAVE_TO_GALLERY, SHARE }
}

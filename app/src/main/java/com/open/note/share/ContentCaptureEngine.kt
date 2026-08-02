package com.open.note.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ContentCaptureEngine {

    fun captureView(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    fun captureFullWebView(webView: WebView): Bitmap? {
        val totalHeight = jsSync(webView, "document.body.scrollHeight")
            .toIntOrNull() ?: webView.height
        val width = webView.width
        if (width <= 0 || totalHeight <= 0) return null

        val fullBitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)
        var offset = 0
        while (offset < totalHeight) {
            webView.scrollTo(0, offset)
            try { Thread.sleep(200) } catch (_: InterruptedException) {}
            val clip = Bitmap.createBitmap(width, minOf(webView.height, totalHeight - offset), Bitmap.Config.ARGB_8888)
            webView.draw(Canvas(clip))
            canvas.drawBitmap(clip, 0f, offset.toFloat(), null)
            clip.recycle()
            offset += webView.height
        }
        return fullBitmap
    }

    private fun jsSync(webView: WebView, script: String): String {
        var result = ""
        val latch = CountDownLatch(1)
        webView.post {
            webView.evaluateJavascript(script) { s -> result = s ?: ""; latch.countDown() }
        }
        latch.await(5, TimeUnit.SECONDS)
        return result.trim('"')
    }
}

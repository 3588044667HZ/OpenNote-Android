package com.open.note.ui.editor

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

class EditorBridge {
    var onContentChanged: ((title: String, html: String, length: Int) -> Unit)? = null
    var onSelectionChanged: ((isBold: Boolean, isItalic: Boolean) -> Unit)? = null
    var onEditorReady: (() -> Unit)? = null

    @JavascriptInterface
    fun postMessage(json: String) {
        try {
            val msg = JSONObject(json)
            when (msg.getString("type")) {
                "contentChange" -> {
                    val title = msg.optString("title", "")
                    val html = msg.optString("html", msg.optString("markdown", ""))
                    val length = msg.optInt("length", html.length)
                    onContentChanged?.invoke(title, html, length)
                }
                "selectionChange" -> {
                    val isBold = msg.optBoolean("isBold", false)
                    val isItalic = msg.optBoolean("isItalic", false)
                    onSelectionChanged?.invoke(isBold, isItalic)
                }
                "editorReady" -> {
                    onEditorReady?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e("EditorBridge", "Failed to parse message: ${json.take(200)}", e)
        }
    }
}

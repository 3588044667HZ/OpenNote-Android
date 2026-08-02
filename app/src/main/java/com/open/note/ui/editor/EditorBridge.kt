package com.open.note.ui.editor

import android.webkit.JavascriptInterface
import org.json.JSONObject

class EditorBridge {
    var onContentChanged: ((markdown: String, length: Int) -> Unit)? = null
    var onSelectionChanged: ((isBold: Boolean, isItalic: Boolean) -> Unit)? = null
    var onEditorReady: (() -> Unit)? = null

    @JavascriptInterface
    fun postMessage(json: String) {
        try {
            val msg = JSONObject(json)
            when (msg.getString("type")) {
                "contentChange" -> {
                    val markdown = msg.getString("markdown")
                    val length = msg.optInt("length", markdown.length)
                    onContentChanged?.invoke(markdown, length)
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
        } catch (_: Exception) {
        }
    }
}

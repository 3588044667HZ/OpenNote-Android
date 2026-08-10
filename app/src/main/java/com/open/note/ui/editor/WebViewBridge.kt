package com.open.note.ui.editor

import android.webkit.WebView
import org.json.JSONObject

/**
 * Java → JS 调用封装（生产级）
 * 统一通过 WebViewJavascriptBridge._handleMessageFromJava 派发
 */
class WebViewBridge(private val webView: WebView) {

    private val pending = ArrayDeque<Pair<String, JSONObject>>()
    private var jsReady = false

    fun markReady() {
        jsReady = true
        while (pending.isNotEmpty()) {
            val (name, data) = pending.removeFirst()
            dispatch(name, data)
        }
    }

    fun callJs(handlerName: String, data: JSONObject, requiresReady: Boolean = true) {
        if (requiresReady && !jsReady) {
            pending.addLast(handlerName to data)
            return
        }
        dispatch(handlerName, data)
    }

    private fun dispatch(handlerName: String, data: JSONObject) {
        webView.post {
            val msg = JSONObject().apply {
                put("handlerName", handlerName)
                put("data", data.toString())
            }
            webView.evaluateJavascript(
                "window.__handleFromJava('$msg')"
            ) { r -> if (r == "null") android.util.Log.w("WebViewBridge", "$handlerName returned null") }
        }
    }

    fun initContent(title: String, contentHtml: String) {
        callJs("callInitContentFromJava", JSONObject().apply {
            put("title", title)
            put("content", contentHtml)
        })
    }

    fun setTextColor(colorVar: String) {
        callJs("callSetTextColorFromJava", JSONObject().apply { put("colorType", colorVar) })
    }

    fun setColorVars(darkMode: Boolean) {
        callJs("callSetColorVarsFromJava", JSONObject().apply { put("darkMode", darkMode) })
    }

    fun setReadonly(flag: Boolean) {
        callJs("callSetReadonlyFromJava", JSONObject().apply { put("flag", flag) })
    }

    fun setSkinCssParams(params: JSONObject) {
        callJs("callSetSkinCssParamsFromJava", params)
    }
}

package com.open.note.share

import android.content.Context
import android.util.Log
import com.open.note.data.local.AuthStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AttachmentResult(
    val bytes: ByteArray,
    val mime: String,
    /** X-Attachment-State：ready / pending / missing（永不 404 方案） */
    val state: String? = null
)

/**
 * 附件下载工具：GET {baseUrl}/attachments/{attachId}/download
 * 供 WebView shouldInterceptRequest 同步调用（内部 runBlocking）
 */
object AttachmentHttp {

    private const val TAG = "AttachmentHttp"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 下载附件二进制（含认证头）。失败返回 null。 */
    fun download(context: Context, attachId: String, size: String? = null): AttachmentResult? {
        val authStore = AuthStore(context)
        return try {
            val baseUrl = runBlocking {
                val stored = authStore.getServerUrlBlocking()
                (if (stored.isNullOrEmpty()) "http://10.0.2.2:5000/api/" else stored).let {
                    if (!it.endsWith("/")) "$it/" else it
                }
            }
            val token = runBlocking { authStore.getAccessTokenBlocking() }
            val url = buildString {
                append("${baseUrl}attachments/$attachId/download")
                if (size != null) append("?size=$size")
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token ?: ""}")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body ?: return@use null
                    val bytes = body.bytes()
                    val mime = body.contentType()?.toString()
                        ?: guessMime(bytes)
                    val state = resp.header("X-Attachment-State")
                    Log.d(TAG, "downloaded $attachId size=${bytes.size} mime=$mime state=$state")
                    AttachmentResult(bytes, mime, state)
                } else {
                    Log.w(TAG, "download $attachId failed: ${resp.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "download $attachId error", e)
            null
        }
    }

    /** 根据文件头猜测 MIME（服务端未返回 Content-Type 时） */
    private fun guessMime(bytes: ByteArray): String {
        if (bytes.size >= 4) {
            // PNG: 89 50 4E 47
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "image/png"
            // JPEG: FF D8 FF
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
            // WEBP: 52 49 46 46 ... 57 45 42 50
            if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()) return "image/webp"
            // GIF: 47 49 46
            if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte()) return "image/gif"
        }
        return "image/*"
    }
}

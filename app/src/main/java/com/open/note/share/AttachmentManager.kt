package com.open.note.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import com.open.note.data.local.dao.AttachmentDao
import com.open.note.data.local.entity.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AttachmentManager(
    private val context: Context,
    private val dao: AttachmentDao,
    private val webView: WebView?
) {

    companion object {
        private const val TAG = "AttachmentManager"
        const val MAX_WIDTH = 1920
    }

    fun getPlaceholderFile(noteId: String, attachId: String): File =
        File(context.filesDir, "$noteId/${attachId}_placeholder.png")

    suspend fun insertImage(uri: Uri, noteId: String): Attachment? =
        withContext(Dispatchers.IO) {
            try {
                val attachId = UUID.randomUUID().toString()

                // 1) 解码获取宽高
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (opts.outWidth <= 0) return@withContext null

                // 2) WEBP 压缩占位图 (quality=10)
                val dir = File(context.filesDir, noteId).apply { mkdirs() }
                val placeholder = File(dir, "${attachId}_placeholder.png")
                val sample = (opts.outWidth / MAX_WIDTH).coerceAtLeast(1)
                val bm = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@withContext null
                placeholder.outputStream().use { bm.compress(Bitmap.CompressFormat.WEBP, 10, it) }
                if (!bm.isRecycled) bm.recycle()

                // 3) 保留原图
                val orig = File(dir, "$attachId.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    orig.outputStream().use { input.copyTo(it) }
                }

                // 4) 存库
                val att = Attachment(
                    attachmentId = attachId,
                    richNoteId = noteId,
                    type = Attachment.TYPE_IMAGE,
                    width = opts.outWidth,
                    height = opts.outHeight,
                    md5 = "",
                    url = "",
                    state = Attachment.STATE_NEW,
                    fileName = orig.name
                )
                dao.upsert(att)

                // 5) 通知 WebView 插入
                notifyWebView(att, noteId)
                att
            } catch (e: Exception) {
                Log.e(TAG, "insertImage failed", e)
                null
            }
        }

    private fun notifyWebView(att: Attachment, noteId: String) {
        webView?.post {
            val data = JSONObject().apply {
                put("attachId", att.attachmentId)
                put("src", "/$noteId/${att.attachmentId}_placeholder.png")
                put("width", att.width)
                put("height", att.height)
                put("alt", "")
            }
            val msg = JSONObject().apply {
                put("handlerName", "callInsertImageFromJava")
                put("data", data.toString())
            }
            webView.evaluateJavascript("window.__insertImage($data)") { r ->
                if (r == "null") Log.w(TAG, "insertImage returned null")
            }
        }
    }

    suspend fun deleteByNoteId(noteId: String) {
        dao.deleteByNoteId(noteId)
        val dir = File(context.filesDir, noteId)
        dir.deleteRecursively()
    }
}

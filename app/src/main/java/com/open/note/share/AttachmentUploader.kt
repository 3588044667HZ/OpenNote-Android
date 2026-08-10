package com.open.note.share

import android.content.Context
import android.util.Log
import com.open.note.data.local.dao.AttachmentDao
import com.open.note.data.local.entity.Attachment
import com.open.note.data.local.AuthStore
import com.open.note.data.remote.api.AttachmentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest

class AttachmentUploader(
    private val context: Context,
    private val dao: AttachmentDao,
    private val authStore: AuthStore,
    private val api: AttachmentApi
) {

    companion object {
        private const val TAG = "AttachmentUploader"
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }

    /** 上传：POST /files/upload */
    suspend fun upload(att: Attachment, noteId: String): Boolean {
        val file = getPlaceholderFile(noteId, att.attachmentId)
        if (!file.exists() || file.length() <= 0) return false
        if (file.length() > MAX_IMAGE_BYTES) {
            Log.e(TAG, "File too large, rejected: ${att.attachmentId}")
            return false
        }
        return withContext(Dispatchers.IO) {
            try {
                val part = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody("image/webp".toMediaType()))
                val resp = api.uploadFile(part, noteId)
                if (resp.isSuccessful && resp.body()?.code == 0) {
                    val data = resp.body()!!.data!!
                    dao.updateSyncInfo(
                        att.attachmentId,
                        url = data.url ?: "",
                        md5 = data.md5.ifEmpty { file.md5() },
                        state = Attachment.STATE_SYNCED)
                    if (data.fileId != null) {
                        val updated = dao.getById(att.attachmentId)
                        if (updated != null) {
                            dao.upsert(updated.copy(fileId = data.fileId))
                        }
                    }
                    true
                } else {
                    Log.w(TAG, "Upload failed: ${resp.code()}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                false
            }
        }
    }

    /** 下载：GET /attachments/:id/download */
    suspend fun download(att: Attachment, noteId: String) {
        val dest = getPlaceholderFile(noteId, att.attachmentId)
        dest.parentFile?.mkdirs()
        withContext(Dispatchers.IO) {
            try {
                val resp = api.downloadFile(att.attachmentId)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: return@withContext
                    dest.outputStream().use { body.byteStream().copyTo(it) }
                    dao.updateSyncInfo(
                        att.attachmentId,
                        url = att.url,
                        md5 = dest.md5(),
                        state = Attachment.STATE_SYNCED)
                } else {
                    Log.w(TAG, "Download failed: ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
            }
        }
    }

    /** 同步决策树：md5 为空 → 有 url 下载 / 无 url 上传 */
    suspend fun syncAll(noteId: String): Int {
        val list = dao.getByNoteId(noteId).filter { it.md5.isEmpty() && it.type == Attachment.TYPE_IMAGE }
        var count = 0
        for (att in list) {
            when {
                att.url.isEmpty() -> if (upload(att, noteId)) count++
                else -> download(att, noteId)
            }
        }
        return count
    }

    private fun getPlaceholderFile(noteId: String, attachId: String): File =
        File(context.filesDir, "$noteId/${attachId}_placeholder.png")
}

private fun File.md5(): String {
    val digest = MessageDigest.getInstance("MD5")
    inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

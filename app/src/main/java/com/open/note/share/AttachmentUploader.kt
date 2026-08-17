package com.open.note.share

import android.content.Context
import android.util.Log
import com.open.note.data.local.dao.AttachmentDao
import com.open.note.data.local.dao.NoteDao
import com.open.note.data.local.entity.Attachment
import com.open.note.data.local.entity.Note
import com.open.note.data.remote.api.AttachmentApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AttachmentDao,
    private val noteDao: NoteDao,
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
                    // 上传成功后，把笔记 content 中的本地占位 URL 替换为服务端下载地址
                    replacePlaceholderInNote(noteId, att, data.url)
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
            // 附件始终以插入时记录的 richNoteId 为准（可能异于同步后的 serverId）
            val noteKey = att.richNoteId
            when {
                att.url.isEmpty() -> if (upload(att, noteKey)) count++
                else -> download(att, noteKey)
            }
        }
        return count
    }

    /**
     * 把笔记 content 中的本地占位图 URL（/{noteId}/{attachId}_placeholder.png?attachId=...）
     * 整体替换为服务端可访问的下载地址，供网页端等跨端显示。
     * 服务端 upload 已返回统一下载 URL（/api/attachments/{attachId}/download）。
     */
    private suspend fun replacePlaceholderInNote(
        noteId: String, att: Attachment, serverUrl: String?
    ) {
        if (serverUrl.isNullOrEmpty()) return
        // noteId 可能是服务端 id（已同步）或本地数字 id（未同步），两种都试
        val note = noteDao.getByServerId(noteId)
            ?: noteId.toLongOrNull()?.let { noteDao.getById(it) }
            ?: return
        // 占位 src 由插入时生成，带 ?attachId= 查询参数，替换时一并清掉
        val fullPlaceholder = Regex(
            Regex.escape("/$noteId/${att.attachmentId}_placeholder.png") +
                """(\?attachId=[^"'<>\s]*)?"""
        )
        if (!fullPlaceholder.containsMatchIn(note.content)) return

        // 预写缓存：把本地占位图复制为服务端 attachId 的缓存文件，
        // 替换后本端加载直接命中本地，无需首次网络下载。
        val serverAttachId = Regex("""/attachments/([^/]+)/download""")
            .find(serverUrl)?.groupValues?.get(1)
        if (serverAttachId != null) {
            val localFile = getPlaceholderFile(noteId, att.attachmentId)
            val cacheFile = File(context.cacheDir, "att_$serverAttachId.bin")
            if (localFile.exists() && (!cacheFile.exists() || cacheFile.length() == 0L)) {
                localFile.copyTo(cacheFile, overwrite = true)
                Log.d(TAG, "Pre-cached placeholder -> att_$serverAttachId.bin")
            }
        }

        val newContent = note.content.replaceFirst(fullPlaceholder, serverUrl)
        noteDao.upsert(note.copy(content = newContent, state = Note.STATE_MODIFIED))
        Log.d(TAG, "Replaced placeholder in note $noteId -> $serverUrl")
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

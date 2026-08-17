package com.open.note.share

import android.content.Context
import android.util.Log
import com.open.note.data.local.dao.AttachmentDao
import com.open.note.data.local.entity.Attachment
import com.open.note.data.remote.api.AttachmentApi
import com.open.note.data.remote.dto.AttachmentRegRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 附件两阶段上传（spec: ATTACHMENT_TWO_PHASE_UPLOAD_SPEC.md）。
 * 1) 注册槽位（attachId = 客户端 UUID，幂等）→ 2) PUT 文件内容（幂等覆盖）。
 * content 中的图片 URL 从插入起就是确定性压缩图地址（/api/attachments/{uuid}/download?size=thumb），
 * 与上传结果无关；上传失败由待传队列（md5 为空）重试。
 */
@Singleton
class AttachmentUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AttachmentDao,
    private val api: AttachmentApi
) {

    companion object {
        private const val TAG = "AttachmentUploader"
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        const val STATE_READY = "ready"
    }

    /** 压缩图相对地址（content 中写入、APP 内展示均用此格式） */
    fun thumbUrl(attachId: String): String = "/api/attachments/$attachId/download?size=thumb"

    /** 两阶段上传：先注册槽位（幂等），再 PUT 文件；槽位被回收（404）时重注册重试一次 */
    suspend fun upload(att: Attachment, noteId: String): Boolean {
        val file = getPlaceholderFile(noteId, att.attachmentId)
        if (!file.exists() || file.length() <= 0) return false
        if (file.length() > MAX_IMAGE_BYTES) {
            Log.e(TAG, "File too large, rejected: ${att.attachmentId}")
            return false
        }
        return withContext(Dispatchers.IO) {
            try {
                ensureRegistered(att, noteId, file.length())
                var resp = api.uploadFileContent(
                    att.attachmentId, file.asRequestBody("image/webp".toMediaType()))
                // 槽位已被回收（服务端重启/过期）→ 重新注册后再传一次
                if (resp.code() == 404 || !resp.isSuccessful) {
                    Log.d(TAG, "Slot missing (${resp.code()}), re-register ${att.attachmentId}")
                    ensureRegistered(att, noteId, file.length())
                    resp = api.uploadFileContent(
                        att.attachmentId, file.asRequestBody("image/webp".toMediaType()))
                }
                if (resp.isSuccessful && resp.body()?.code == 0) {
                    dao.updateSyncInfo(
                        att.attachmentId,
                        url = thumbUrl(att.attachmentId),
                        md5 = file.md5(),
                        state = Attachment.STATE_SYNCED)
                    // 预写缓存：服务器已 ready，本端直接命中本地，无需首次网络下载
                    prewriteCache(att.attachmentId, file)
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

    /** 注册槽位（幂等）。仅用于上传前的确保；content 不依赖其结果。 */
    private suspend fun ensureRegistered(att: Attachment, noteId: String, fileSize: Long): Boolean {
        return try {
            val resp = api.registerAttachment(
                AttachmentRegRequest(
                    attachId = att.attachmentId,
                    noteId = noteId,
                    type = att.type,
                    width = att.width,
                    height = att.height,
                    fileName = att.fileName,
                    size = fileSize
                )
            )
            if (resp.isSuccessful) {
                Log.d(TAG, "Registered slot ${att.attachmentId}")
                true
            } else {
                Log.w(TAG, "Register failed: ${resp.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            false
        }
    }

    /**
     * 下载压缩图到本地占位文件路径（用于"其它端插入的图"落地本机）。
     * 仅服务器 ready 时落盘；pending/missing 不覆盖本地（防占位图污染本地高清图）。
     */
    suspend fun download(att: Attachment, noteId: String) {
        val dest = getPlaceholderFile(noteId, att.attachmentId)
        dest.parentFile?.mkdirs()
        withContext(Dispatchers.IO) {
            try {
                val resp = api.downloadFile(att.attachmentId, "thumb")
                if (resp.isSuccessful) {
                    val state = resp.headers()["X-Attachment-State"]
                    if (state != STATE_READY) {
                        Log.d(TAG, "Skip download ${att.attachmentId}: state=$state")
                        return@withContext
                    }
                    val body = resp.body() ?: return@withContext
                    dest.outputStream().use { body.byteStream().copyTo(it) }
                    dao.updateSyncInfo(
                        att.attachmentId,
                        url = thumbUrl(att.attachmentId),
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

    /** 同步决策树：md5 为空 → 有 url 下载 / 无 url 上传（两阶段） */
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
     * 把 content 中所有旧格式本地占位 URL 清洗为确定性压缩图地址。
     * 旧格式：/{任意key}/{uuid}_placeholder.png?attachId={uuid}
     * 新格式：/api/attachments/{uuid}/download?size=thumb（attachId = 客户端 UUID，确定性）
     * 幂等：新格式不匹配，重复调用无副作用。
     */
    suspend fun resolvePlaceholders(noteKey: String, content: String): String {
        val oldPlaceholder = Regex(
            """/[^"'<>\s]*?/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})_placeholder\.png(?:\?attachId=[^"'<>\s]*)?"""
        )
        return oldPlaceholder.replace(content) { m ->
            val attachId = m.groupValues[1]
            thumbUrl(attachId)
        }
    }

    /** 预写缓存：上传成功后本端加载直接命中本地（仅 ready 语义下执行） */
    private fun prewriteCache(attachId: String, localFile: File) {
        val cacheFile = File(context.cacheDir, "att_$attachId.bin")
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            localFile.copyTo(cacheFile, overwrite = true)
            Log.d(TAG, "Pre-cached -> att_$attachId.bin")
        }
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

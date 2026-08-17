package com.open.note.data.repository

import android.util.Log
import com.open.note.data.local.AuthStore
import com.open.note.data.local.dao.NoteHistoryDao
import com.open.note.data.local.entity.Note
import com.open.note.data.local.entity.NoteHistory
import com.open.note.data.remote.api.NoteApi
import com.open.note.data.remote.dto.toEntity
import javax.inject.Inject
import javax.inject.Singleton

/** 一次同步中检测到的双端修改冲突（需用户决定，不自动裁决） */
data class SyncConflict(
    val local: Note,
    val remote: Note
)

/** 同步结果 */
data class SyncResult(
    val conflicts: List<SyncConflict> = emptyList()
)

/**
 * 双向同步：本地优先 + state 脏标记 + 链式冲突处理。
 * 流程：上传本地脏数据 → 拉取远端增量 → 合并（冲突进列表）→ 置锚点。
 * 双端都修改（内容不同）时**不自动裁决**，进入 conflicts 由 UI 弹窗决定。
 */
@Singleton
class SyncManager @Inject constructor(
    private val noteApi: NoteApi,
    private val authStore: AuthStore,
    private val noteRepository: NoteRepository,
    private val noteHistoryDao: NoteHistoryDao
) {

    companion object {
        private const val TAG = "SyncManager"
        private const val HISTORY_MAX = 500L
        private const val HISTORY_KEEP_DAYS = 90L
    }

    private val conflicts = mutableListOf<SyncConflict>()

    /** 全量同步入口：未登录直接跳过。返回本次冲突列表。 */
    suspend fun doSync(): Result<SyncResult> {
        val token = authStore.getAccessTokenBlocking()
        if (token.isNullOrBlank()) {
            Log.d(TAG, "Not logged in, skip sync")
            return Result.success(SyncResult())
        }
        conflicts.clear()
        return try {
            uploadDirty()
            mergeRemote()
            trimHistory()
            Result.success(SyncResult(conflicts.toList()))
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            Result.failure(e)
        }
    }

    /** 兼容旧调用（拉取合并阶段）。 */
    suspend fun incrementalSync(): Result<Unit> = mergeRemote()

    /** 兼容旧调用（上传阶段）。 */
    suspend fun syncPendingNotes(): Result<Unit> {
        uploadDirty()
        return Result.success(Unit)
    }

    // ===================== 上传阶段 =====================

    private suspend fun uploadDirty() {
        val dirty = noteRepository.getDirtyNotes()
        if (dirty.isEmpty()) {
            Log.d(TAG, "No dirty notes")
            return
        }
        Log.d(TAG, "Uploading ${dirty.size} dirty notes")
        dirty.forEach { note ->
            try {
                when {
                    // 本地已删除 → 上传删除
                    note.deletedAt != null -> uploadDelete(note)
                    // 从未上传（NEW）→ POST 创建
                    note.serverId == null -> uploadCreate(note)
                    // RESTORE（远程已删，本地胜）→ 恢复原 id
                    note.state == Note.STATE_RESTORE -> uploadRestore(note)
                    // MODIFIED → PUT（If-Match）
                    else -> uploadUpdate(note)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for note ${note.localId}", e)
            }
        }
    }

    private suspend fun uploadDelete(note: Note) {
        if (note.serverId == null) {
            // 从未上传的笔记被删除 → 本地直接清理
            noteRepository.permanentlyDeleteNoteByLocalId(note.localId)
            return
        }
        noteRepository.permanentlyDeleteNote(note.serverId!!)
        Log.d(TAG, "Uploaded delete for ${note.serverId}")
    }

    private suspend fun uploadCreate(note: Note) {
        val result = noteRepository.createNote(
            title = note.title, content = note.content,
            notebookId = note.notebookId, color = note.color, isPinned = note.isPinned
        )
        if (result.isSuccess) {
            Log.d(TAG, "Uploaded new note (local ${note.localId}) -> ${result.getOrNull()?.serverId}")
        }
        // 失败保留 NEW 状态，下次重试
    }

    private suspend fun uploadRestore(note: Note) {
        val result = noteRepository.restoreNoteWithContent(note)
        if (result.isSuccess) {
            noteRepository.updateState(note.localId, Note.STATE_SYNCED)
            Log.d(TAG, "Restored note ${note.serverId} from local")
        } else {
            val ex = result.exceptionOrNull()
            if (ex is NotFoundException) {
                // 已永久删除 → 回退 POST 重建
                Log.d(TAG, "Note ${note.serverId} permanently deleted, recreate as new")
                uploadCreate(note)
            } else {
                Log.e(TAG, "Restore failed for ${note.serverId}", ex)
            }
        }
    }

    private suspend fun uploadUpdate(note: Note) {
        val result = noteRepository.updateNote(note)
        if (result.isSuccess) {
            noteRepository.updateState(note.localId, Note.STATE_SYNCED)
            Log.d(TAG, "Updated note ${note.serverId}")
            return
        }
        val ex = result.exceptionOrNull()
        if (ex is ConflictException) {
            // 409：本地优先 → 不带 If-Match 强制覆盖远程
            Log.d(TAG, "Conflict on ${note.serverId}, local wins (force overwrite)")
            noteRepository.forceUpdateNote(note).onSuccess {
                noteRepository.updateState(note.localId, Note.STATE_SYNCED)
            }
        } else {
            Log.e(TAG, "Update failed for ${note.serverId}", ex)
        }
    }

    // ===================== 拉取 + 裁决合并 =====================

    private suspend fun mergeRemote(): Result<Unit> {
        val lastSync = authStore.getLastSyncTimeBlocking()
        Log.d(TAG, "Incremental sync since: $lastSync")

        val response = noteApi.syncNotes(since = lastSync)
        if (!response.isSuccessful || response.body()?.data == null) {
            val msg = response.body()?.msg ?: "Sync failed (${response.code()})"
            Log.e(TAG, msg)
            return Result.failure(Exception(msg))
        }
        val syncData = response.body()!!.data!!

        syncData.updated.forEach { dto ->
            try {
                mergeOne(dto.toEntity())
            } catch (e: Exception) {
                Log.e(TAG, "Merge failed for ${dto.id}", e)
            }
        }
        Log.d(TAG, "Merged ${syncData.updated.size} updated notes")

        syncData.deletedIds.forEach { id ->
            try {
                handleRemoteDelete(id)
            } catch (e: Exception) {
                Log.e(TAG, "Remote delete handling failed for $id", e)
            }
        }
        Log.d(TAG, "Processed ${syncData.deletedIds.size} deleted notes")

        syncData.serverTime?.let { authStore.setLastSyncTime(it) }
        Log.d(TAG, "Sync complete, server time: ${syncData.serverTime}")
        return Result.success(Unit)
    }

    /**
     * 裁决链（按序命中即止）：
     * ① 本地无 → 插入；② 内容相同 → 刷新元数据；
     * ③ 本地已删 → 远程胜；④ 本地未改 → 远程覆盖（归档）；
     * ⑤ 双改 → 时间裁决。
     */
    private suspend fun mergeOne(remote: Note) {
        val serverId = remote.serverId ?: return
        val local = noteRepository.getNoteByServerId(serverId)
            ?: run {
                noteRepository.upsertNote(remote)
                Log.d(TAG, "New remote note inserted: $serverId")
                return
            }

        // ③ 本地已删除 → 远程胜（本地版本归档后丢弃）
        if (local.deletedAt != null) {
            archiveIfNeeded(local, NoteHistory.CAUSE_DELETED)
            noteRepository.permanentlyDeleteNoteByLocalId(local.localId)
            Log.d(TAG, "Local deleted note removed: $serverId")
            return
        }

        // ② 内容相同 → 仅刷新元数据，保留本地状态
        if (local.title == remote.title && local.content == remote.content) {
            if (local.state == Note.STATE_SYNCED) {
                noteRepository.upsertNote(remote.copy(localId = local.localId))
            } else {
                local.lastServerUpdate = remote.lastServerUpdate
                noteRepository.upsertNote(local)
            }
            return
        }

        // ④ 本地未修改 → 远程直接覆盖（先归档）
        if (local.state == Note.STATE_SYNCED) {
            archiveIfNeeded(local, NoteHistory.CAUSE_CONFLICT)
            noteRepository.upsertNote(remote.copy(localId = local.localId))
            Log.d(TAG, "Remote overwrote clean local note: $serverId")
            return
        }

        // ⑤ 双端都修改 → 不自动裁决，进冲突列表由用户决定
        conflicts.add(SyncConflict(local = local, remote = remote))
        Log.d(TAG, "Conflict detected (both modified): $serverId")
    }

    /**
     * 远程删除 vs 本地：
     * 本地有未上传修改 → 本地胜（state=RESTORE，上传阶段 restore）；
     * 否则跟随删除。
     */
    private suspend fun handleRemoteDelete(serverId: String) {
        val local = noteRepository.getNoteByServerId(serverId) ?: return
        val hasLocalChanges = local.state == Note.STATE_MODIFIED ||
            local.state == Note.STATE_NEW ||
            local.state == Note.STATE_RESTORE
        if (hasLocalChanges) {
            local.state = Note.STATE_RESTORE
            noteRepository.upsertNote(local)
            Log.d(TAG, "Remote delete overridden by local changes: $serverId")
        } else {
            noteRepository.permanentlyDeleteNote(serverId)
            Log.d(TAG, "Followed remote delete: $serverId")
        }
    }

    // ===================== 版本归档 =====================

    private suspend fun archiveIfNeeded(local: Note, cause: Int) {
        if (local.state == Note.STATE_SYNCED) {
            // 未修改的版本无归档价值（与远程内容一致）
            return
        }
        noteHistoryDao.insert(
            NoteHistory(
                noteId = local.serverId ?: local.localId.toString(),
                title = local.title,
                content = local.content,
                version = local.version,
                cause = cause
            )
        )
        Log.d(TAG, "Archived local version of ${local.serverId} (cause=$cause)")
    }

    /** 归档清理：90 天过期 + 全库 500 条上限。 */
    private suspend fun trimHistory() {
        val cutoff = System.currentTimeMillis() - HISTORY_KEEP_DAYS * 24 * 3600 * 1000
        noteHistoryDao.deleteOlderThan(cutoff)
        while (noteHistoryDao.count() > HISTORY_MAX) {
            noteHistoryDao.trimToLimit(HISTORY_MAX.toInt())
        }
    }
}

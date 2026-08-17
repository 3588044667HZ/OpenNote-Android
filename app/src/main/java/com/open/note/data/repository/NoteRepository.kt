package com.open.note.data.repository

import com.open.note.data.local.dao.FolderDao
import com.open.note.data.local.dao.NoteDao
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import com.open.note.data.remote.api.NoteApi
import com.open.note.data.remote.api.NotebookApi
import com.open.note.data.remote.dto.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

class ConflictException(message: String) : Exception(message)

/** 笔记已被永久删除（restore 404 时抛出，调用方回退 POST 重建） */
class NotFoundException : Exception("Note permanently deleted")

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val noteApi: NoteApi,
    private val notebookApi: NotebookApi
) {

    fun getActiveNotes(): Flow<List<Note>> = noteDao.getActiveNotes()
    fun getTrashNotes(): Flow<List<Note>> = noteDao.getTrashNotes()
    fun getActiveFolders(): Flow<List<Folder>> = folderDao.getAllFolders()
    fun searchNotes(keyword: String): Flow<List<Note>> = noteDao.searchNotes(keyword)
    fun getNotesByNotebook(notebookId: String): Flow<List<Note>> = noteDao.getNotesByNotebook(notebookId)
    suspend fun getNoteByServerId(serverId: String): Note? = noteDao.getByServerId(serverId)
    suspend fun getNoteById(localId: Long): Note? = noteDao.getById(localId)

    /** 拉取单条笔记最新版本（冲突"重新检查/采用远程"用）。404 视为已永久删除。 */
    suspend fun getNoteFromServer(serverId: String): Result<Note> {
        return try {
            val response = noteApi.getNote(serverId)
            if (response.isSuccessful && response.body()?.code == 0 && response.body()?.data != null) {
                val note = response.body()!!.data!!.toEntity()
                safeUpsertNote(note)
                Result.success(note)
            } else if (response.code() == 404) {
                Result.failure(NotFoundException())
            } else {
                Result.failure(Exception(response.body()?.msg ?: "Failed to fetch note"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertNote(note: Note) = safeUpsertNote(note)
    suspend fun upsertFolder(folder: Folder) = safeUpsertFolder(folder)

    private suspend fun safeUpsertNote(note: Note): Long {
        val serverId = note.serverId
        if (serverId != null) {
            val existing = noteDao.getByServerId(serverId)
            if (existing != null) {
                return noteDao.upsert(note.copy(localId = existing.localId))
            }
        }
        return noteDao.upsert(note)
    }

    private suspend fun safeUpsertFolder(folder: Folder): Long {
        val serverId = folder.serverId
        if (serverId != null) {
            val existing = folderDao.getByServerId(serverId)
            if (existing != null) {
                return folderDao.upsert(folder.copy(localId = existing.localId))
            }
        }
        return folderDao.upsert(folder)
    }

    suspend fun fetchNotesFromServer(): Result<List<Note>> {
        return try {
            val response = noteApi.getNotes(size = 100)
            if (response.isSuccessful && response.body()?.code == 0) {
                val dtos = response.body()?.data ?: emptyList()
                val entities = dtos.map { it.toEntity() }
                entities.forEach { safeUpsertNote(it) }
                Result.success(entities)
            } else {
                Result.failure(Exception("Failed to fetch notes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchNotebooksFromServer(): Result<List<Folder>> {
        return try {
            val response = notebookApi.getNotebooks()
            if (response.isSuccessful && response.body()?.code == 0) {
                val dtos = response.body()?.data ?: emptyList()
                val entities = dtos.map { it.toEntity() }
                entities.forEach { safeUpsertFolder(it) }
                Result.success(entities)
            } else {
                Result.failure(Exception("Failed to fetch notebooks"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNote(
        title: String = "", content: String = "",
        notebookId: String? = null, color: String = "blue", isPinned: Boolean = false
    ): Result<Note> {
        val localNote = Note(title = title, content = content, notebookId = notebookId,
            color = color, isPinned = isPinned, state = Note.STATE_NEW)
        val localId = safeUpsertNote(localNote)

        return try {
            val response = noteApi.createNote(CreateNoteRequest(title, content, notebookId, color, isPinned))
            if (response.isSuccessful && response.body()?.code == 0) {
                val synced = response.body()!!.data!!.toEntity()
                safeUpsertNote(synced)
                noteDao.hardDeleteByLocalId(localId)
                Result.success(synced)
            } else {
                Result.success(localNote.copy(localId = localId))
            }
        } catch (e: Exception) {
            Result.success(localNote.copy(localId = localId))
        }
    }

    suspend fun updateNote(note: Note): Result<Note> {
        note.updatedAt = System.currentTimeMillis()
        note.state = Note.STATE_MODIFIED
        safeUpsertNote(note)
        val serverId = note.serverId ?: return Result.success(note)

        return try {
            val response = noteApi.updateNote(
                id = serverId,
                request = UpdateNoteRequest(note.title, note.content, note.notebookId, note.color, note.isPinned),
                ifMatch = note.lastServerUpdate
            )
            if (response.isSuccessful && response.body()?.code == 0) {
                val synced = response.body()!!.data!!.toEntity()
                safeUpsertNote(synced)
                Result.success(synced)
            } else if (response.code() == 409) {
                Result.failure(ConflictException(response.body()?.msg ?: "Conflict"))
            } else {
                Result.success(note)
            }
        } catch (e: Exception) {
            Result.success(note)
        }
    }

    /**
     * 删除冲突"本地胜"时调用：恢复被删笔记并覆盖为本地内容。
     * 服务端 RESTORE_API 接口；404（已永久删除）时回退 POST 重建。
     */
    suspend fun restoreNoteWithContent(note: Note): Result<Note> {
        val serverId = note.serverId ?: return Result.failure(IllegalStateException("No serverId"))
        return try {
            val response = noteApi.restoreNote(
                id = serverId,
                request = RestoreNoteRequest(
                    title = note.title,
                    content = note.content,
                    notebookId = note.notebookId,
                    color = note.color,
                    isPinned = note.isPinned
                )
            )
            if (response.isSuccessful && response.body()?.code == 0) {
                val synced = response.body()!!.data!!.toEntity()
                safeUpsertNote(synced)
                Result.success(synced)
            } else if (response.code() == 404) {
                // 已永久删除 → 回退 POST 重建
                Result.failure(NotFoundException())
            } else {
                Result.failure(Exception(response.body()?.msg ?: "Restore failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除笔记：支持本地未上传笔记（serverId == null）。
     * 本地软删 + 置脏，远端删除失败也不影响本地。
     */
    suspend fun deleteNote(localId: Long, serverId: String?) {
        val now = System.currentTimeMillis()
        if (serverId == null) {
            // 从未上传 → 直接本地软删（上传阶段会清理）
            noteDao.softDeleteByLocalId(localId, now)
        } else {
            noteDao.softDelete(serverId, now)
        }
        noteDao.updateState(localId, Note.STATE_MODIFIED)
        try {
            if (serverId != null) {
                noteApi.deleteNote(serverId)
            }
        } catch (e: Exception) {
            // 离线删除：保留脏标记，下次同步上传
        }
    }

    /** 409 冲突后"本地胜"强制覆盖：不带 If-Match。 */
    suspend fun forceUpdateNote(note: Note): Result<Note> {
        val serverId = note.serverId ?: return Result.failure(IllegalStateException("No serverId"))
        return try {
            val response = noteApi.updateNote(
                id = serverId,
                request = UpdateNoteRequest(note.title, note.content, note.notebookId, note.color, note.isPinned),
                ifMatch = null
            )
            if (response.isSuccessful && response.body()?.code == 0) {
                val synced = response.body()!!.data!!.toEntity()
                safeUpsertNote(synced)
                Result.success(synced)
            } else {
                Result.failure(Exception(response.body()?.msg ?: "Force update failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun permanentlyDeleteNoteByLocalId(localId: Long) {
        noteDao.hardDeleteByLocalId(localId)
    }

    suspend fun togglePin(serverId: String): Result<Note?> {
        return try {
            val response = noteApi.togglePin(serverId)
            if (response.isSuccessful && response.body()?.code == 0) {
                val synced = response.body()!!.data!!.toEntity()
                safeUpsertNote(synced)
                Result.success(synced)
            } else {
                Result.failure(Exception("Failed to toggle pin"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recoverNote(serverId: String): Result<Note> {
        return try {
            val response = noteApi.recoverNote(serverId)
            if (response.isSuccessful && response.body()?.code == 0) {
                val recovered = response.body()!!.data!!.toEntity()
                noteDao.hardDelete(serverId)
                safeUpsertNote(recovered)
                Result.success(recovered)
            } else {
                Result.failure(Exception("Failed to recover note"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 恢复从未上传的本地笔记（serverId == null） */
    suspend fun recoverLocalNote(localId: Long) {
        noteDao.recoverByLocalId(localId)
    }

    suspend fun permanentlyDeleteNote(serverId: String): Result<Unit> {
        noteDao.hardDelete(serverId)
        return try {
            noteApi.permanentlyDeleteNote(serverId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun createNotebook(name: String, color: String = "#4A90D9"): Result<Folder> {
        val localFolder = Folder(name = name, color = color)
        safeUpsertFolder(localFolder)
        return try {
            val response = notebookApi.createNotebook(NotebookDto(name = name, color = color))
            if (response.isSuccessful && response.body()?.code == 0) {
                val synced = response.body()!!.data!!.toEntity()
                safeUpsertFolder(synced)
                Result.success(synced)
            } else {
                Result.success(localFolder)
            }
        } catch (e: Exception) {
            Result.success(localFolder)
        }
    }

    suspend fun updateNotebook(serverId: String, name: String, color: String = "#4A90D9"): Result<Folder> {
        val existing = folderDao.getByServerId(serverId)
        if (existing != null) {
            existing.name = name
            existing.color = color
            safeUpsertFolder(existing)
        }
        return try {
            val response = notebookApi.updateNotebook(serverId, NotebookDto(name = name, color = color))
            if (response.isSuccessful && response.body()?.code == 0) {
                val synced = response.body()!!.data!!.toEntity()
                safeUpsertFolder(synced)
                Result.success(synced)
            } else {
                Result.success(existing ?: Folder(name = name, color = color))
            }
        } catch (e: Exception) {
            Result.success(existing ?: Folder(name = name, color = color))
        }
    }

    suspend fun deleteNotebook(serverId: String): Result<Unit> {
        folderDao.deleteByServerId(serverId)
        return try {
            notebookApi.deleteNotebook(serverId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun emptyTrash() { noteDao.emptyTrash() }
    suspend fun getDirtyNotes(): List<Note> = noteDao.getDirtyNotes()
    suspend fun updateState(localId: Long, state: Int) { noteDao.updateState(localId, state) }
}

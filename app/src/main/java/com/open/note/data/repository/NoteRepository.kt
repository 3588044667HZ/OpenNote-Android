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
            color = color, isPinned = isPinned, isPendingSync = true)
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
        note.isPendingSync = true
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

    suspend fun deleteNote(serverId: String): Result<Unit> {
        noteDao.softDelete(serverId, System.currentTimeMillis())
        return try {
            noteApi.deleteNote(serverId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
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
    suspend fun getPendingSyncNotes(): List<Note> = noteDao.getPendingSyncNotes()
    suspend fun markPendingSync(localId: Long, pending: Boolean = true) { noteDao.markPendingSync(localId, pending) }
}

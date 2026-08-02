package com.open.note.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.open.note.data.local.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY is_pinned DESC, updated_at DESC")
    fun getActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE server_id = :serverId")
    suspend fun getByServerId(serverId: String): Note?

    @Query("SELECT * FROM notes WHERE local_id = :localId")
    suspend fun getById(localId: Long): Note?

    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND (title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%') ORDER BY is_pinned DESC, updated_at DESC")
    fun searchNotes(keyword: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND notebook_id = :notebookId ORDER BY is_pinned DESC, updated_at DESC")
    fun getNotesByNotebook(notebookId: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE is_pending_sync = 1")
    suspend fun getPendingSyncNotes(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note): Long

    @Query("UPDATE notes SET is_pending_sync = :pending WHERE local_id = :id")
    suspend fun markPendingSync(id: Long, pending: Boolean = true)

    @Query("UPDATE notes SET deleted_at = :deletedAt WHERE server_id = :serverId")
    suspend fun softDelete(serverId: String, deletedAt: Long)

    @Query("DELETE FROM notes WHERE server_id = :serverId")
    suspend fun hardDelete(serverId: String)

    @Query("DELETE FROM notes WHERE local_id = :localId")
    suspend fun hardDeleteByLocalId(localId: Long)

    @Query("DELETE FROM notes WHERE deleted_at IS NOT NULL")
    suspend fun emptyTrash()
}

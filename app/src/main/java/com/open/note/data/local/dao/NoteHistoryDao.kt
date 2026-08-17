package com.open.note.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.open.note.data.local.entity.NoteHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteHistoryDao {

    @Insert
    suspend fun insert(history: NoteHistory): Long

    @Query("SELECT * FROM note_history ORDER BY archived_at DESC")
    fun getAll(): Flow<List<NoteHistory>>

    @Query("DELETE FROM note_history WHERE history_id = :historyId")
    suspend fun deleteById(historyId: Long)

    @Query("DELETE FROM note_history WHERE archived_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM note_history WHERE history_id NOT IN " +
        "(SELECT history_id FROM note_history ORDER BY archived_at DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int)

    @Query("SELECT COUNT(*) FROM note_history")
    suspend fun count(): Int
}

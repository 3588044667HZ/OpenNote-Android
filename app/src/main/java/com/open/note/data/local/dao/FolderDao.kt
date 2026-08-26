package com.open.note.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.open.note.data.local.entity.Folder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY created_at DESC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE server_id = :serverId")
    suspend fun getByServerId(serverId: String): Folder?

    @Query("SELECT * FROM folders WHERE server_id IS NULL")
    suspend fun getLocalOnly(): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: Folder): Long

    @Query("DELETE FROM folders WHERE server_id = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("DELETE FROM folders WHERE local_id = :localId")
    suspend fun deleteByLocalId(localId: Long)
}

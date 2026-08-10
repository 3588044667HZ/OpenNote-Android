package com.open.note.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.open.note.data.local.entity.Attachment

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE rich_note_id = :noteId ORDER BY rowid")
    suspend fun getByNoteId(noteId: String): List<Attachment>

    @Query("SELECT * FROM attachments WHERE attachment_id = :attachId")
    suspend fun getById(attachId: String): Attachment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: Attachment)

    @Query("DELETE FROM attachments WHERE attachment_id = :attachId")
    suspend fun deleteById(attachId: String)

    @Query("DELETE FROM attachments WHERE rich_note_id = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("UPDATE attachments SET url = :url, md5 = :md5, state = :state WHERE attachment_id = :attachId")
    suspend fun updateSyncInfo(attachId: String, url: String, md5: String, state: Int)
}

package com.open.note.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.open.note.data.local.dao.AttachmentDao
import com.open.note.data.local.dao.FolderDao
import com.open.note.data.local.dao.NoteDao
import com.open.note.data.local.dao.NoteHistoryDao
import com.open.note.data.local.entity.Attachment
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import com.open.note.data.local.entity.NoteHistory

@Database(
    entities = [Note::class, Folder::class, Attachment::class, NoteHistory::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun noteHistoryDao(): NoteHistoryDao
}

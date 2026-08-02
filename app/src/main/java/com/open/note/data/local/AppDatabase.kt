package com.open.note.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.open.note.data.local.dao.FolderDao
import com.open.note.data.local.dao.NoteDao
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note

@Database(
    entities = [Note::class, Folder::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
}

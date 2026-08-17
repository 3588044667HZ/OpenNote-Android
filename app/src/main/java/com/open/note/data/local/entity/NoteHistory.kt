package com.open.note.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 冲突覆盖前的版本归档（防静默丢失兜底）。
 * 远程覆盖本地前，将本地当前版本快照存入此表，用户可找回。
 */
@Entity(
    tableName = "note_history",
    indices = [Index(value = ["note_id"])]
)
data class NoteHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "history_id")
    val historyId: Long = 0,

    @ColumnInfo(name = "note_id")
    val noteId: String?,

    var title: String,

    var content: String,

    var version: Int,

    @ColumnInfo(name = "archived_at")
    val archivedAt: Long = System.currentTimeMillis(),

    // 0=冲突覆盖 1=删除 2=远程回滚
    var cause: Int = 0
) {
    companion object {
        const val CAUSE_CONFLICT = 0
        const val CAUSE_DELETED = 1
        const val CAUSE_REMOTE_ROLLBACK = 2
    }
}

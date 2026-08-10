package com.open.note.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey
    @ColumnInfo(name = "attachment_id")
    val attachmentId: String,

    @ColumnInfo(name = "rich_note_id")
    val richNoteId: String,

    val type: Int = 0,

    @ColumnInfo(name = "img_width")
    val width: Int = 0,

    @ColumnInfo(name = "img_height")
    val height: Int = 0,

    var md5: String = "",

    var url: String = "",

    @ColumnInfo(name = "file_id")
    var fileId: String = "",

    var state: Int = 0,

    @ColumnInfo(name = "file_name")
    var fileName: String? = null,

    @ColumnInfo(name = "created_at")
    var createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_IMAGE = 0
        const val STATE_NEW = 0
        const val STATE_SYNCED = 1
        const val STATE_MODIFIED = 2
    }
}

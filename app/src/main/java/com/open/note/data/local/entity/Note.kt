package com.open.note.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["server_id"]),
        Index(value = ["notebook_id"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    val localId: Long = 0,

    @ColumnInfo(name = "server_id")
    var serverId: String? = null,

    var title: String = "",

    var content: String = "",

    @ColumnInfo(name = "notebook_id")
    var notebookId: String? = null,

    var color: String = "blue",

    @ColumnInfo(name = "is_pinned")
    var isPinned: Boolean = false,

    var version: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "deleted_at")
    var deletedAt: Long? = null,

    // 同步状态: 0=NEW 未上传 1=SYNCED 已同步 2=MODIFIED 待上传 3=RESTORE 删除冲突恢复
    var state: Int = 0,

    @ColumnInfo(name = "last_server_update")
    var lastServerUpdate: String? = null
) {
    val isDeleted: Boolean get() = deletedAt != null

    companion object {
        const val STATE_NEW = 0
        const val STATE_SYNCED = 1
        const val STATE_MODIFIED = 2
        const val STATE_RESTORE = 3
    }
}

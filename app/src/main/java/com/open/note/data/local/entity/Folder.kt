package com.open.note.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["server_id"])
    ]
)
data class Folder(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    val localId: Long = 0,

    @ColumnInfo(name = "server_id")
    var serverId: String? = null,

    var name: String = "",

    var color: String = "#4A90D9",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

package com.open.note.data.remote.dto

import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import java.time.Instant

fun parseIsoToEpoch(isoString: String?): Long? {
    if (isoString.isNullOrBlank()) return null
    return try {
        Instant.parse(isoString).toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

fun NoteDto.toEntity(): Note {
    return Note(
        serverId = id,
        title = title ?: "",
        content = content ?: "",
        notebookId = notebookId,
        color = color ?: "blue",
        isPinned = isPinned ?: false,
        version = version ?: 0,
        updatedAt = parseIsoToEpoch(updatedAt) ?: System.currentTimeMillis(),
        lastServerUpdate = updatedAt
    )
}

fun Note.toDto(): NoteDto {
    return NoteDto(
        id = serverId,
        title = title,
        content = content,
        notebookId = notebookId,
        color = color,
        isPinned = isPinned,
        version = version,
        updatedAt = lastServerUpdate
    )
}

fun NotebookDto.toEntity(): Folder {
    return Folder(
        serverId = id,
        name = name ?: "",
        color = color ?: "#4A90D9"
    )
}

fun Folder.toDto(): NotebookDto {
    return NotebookDto(
        id = serverId,
        name = name,
        color = color
    )
}

package com.open.note.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: T? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class LoginResponse(
    val token: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Int? = null,
    val user: UserDto? = null
)

data class UserDto(
    val username: String? = null,
    val createdAt: String? = null
)

data class NoteDto(
    val id: String? = null,
    val title: String? = null,
    val content: String? = null,
    val notebookId: String? = null,
    val color: String? = null,
    val isPinned: Boolean? = null,
    val version: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null
)

data class NotebookDto(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null,
    val createdAt: String? = null
)

data class CreateNoteRequest(
    val title: String = "",
    val content: String = "",
    val notebookId: String? = null,
    val color: String = "blue",
    val isPinned: Boolean = false
)

data class UpdateNoteRequest(
    val title: String? = null,
    val content: String? = null,
    val notebookId: String? = null,
    val color: String? = null,
    val isPinned: Boolean? = null
)

data class SyncResponse(
    val updated: List<NoteDto> = emptyList(),
    val deletedIds: List<String> = emptyList(),
    val serverTime: String? = null
)

data class PaginatedListResponse<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: List<T> = emptyList(),
    val pagination: Pagination? = null
)

data class Pagination(
    val page: Int = 1,
    val size: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 0
)

typealias NoteListResponse = PaginatedListResponse<NoteDto>

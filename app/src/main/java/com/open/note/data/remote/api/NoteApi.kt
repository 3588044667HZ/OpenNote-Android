package com.open.note.data.remote.api

import com.open.note.data.remote.dto.ApiResponse
import com.open.note.data.remote.dto.CreateNoteRequest
import com.open.note.data.remote.dto.NoteDto
import com.open.note.data.remote.dto.NoteListResponse
import com.open.note.data.remote.dto.RestoreNoteRequest
import com.open.note.data.remote.dto.SyncResponse
import com.open.note.data.remote.dto.UpdateNoteRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NoteApi {

    @GET("notes")
    suspend fun getNotes(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sortBy") sortBy: String = "updatedAt",
        @Query("keyword") keyword: String? = null,
        @Query("notebookId") notebookId: String? = null,
        @Query("color") color: String? = null
    ): Response<NoteListResponse>

    @GET("notes/sync")
    suspend fun syncNotes(
        @Query("since") since: String? = null
    ): Response<ApiResponse<SyncResponse>>

    @GET("notes/{id}")
    suspend fun getNote(@Path("id") id: String): Response<ApiResponse<NoteDto>>

    @POST("notes")
    suspend fun createNote(@Body request: CreateNoteRequest): Response<ApiResponse<NoteDto>>

    @PUT("notes/{id}")
    suspend fun updateNote(
        @Path("id") id: String,
        @Body request: UpdateNoteRequest,
        @Header("If-Match") ifMatch: String? = null
    ): Response<ApiResponse<NoteDto>>

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): Response<ApiResponse<NoteDto>>

    @PUT("notes/{id}/pin")
    suspend fun togglePin(@Path("id") id: String): Response<ApiResponse<NoteDto>>

    @PUT("notes/{id}/restore")
    suspend fun restoreNote(
        @Path("id") id: String,
        @Body request: RestoreNoteRequest
    ): Response<ApiResponse<NoteDto>>

    @GET("notes/trash")
    suspend fun getTrashNotes(): Response<ApiResponse<List<NoteDto>>>

    @PUT("notes/{id}/recover")
    suspend fun recoverNote(@Path("id") id: String): Response<ApiResponse<NoteDto>>

    @DELETE("notes/{id}/permanent")
    suspend fun permanentlyDeleteNote(@Path("id") id: String): Response<ApiResponse<Unit>>
}

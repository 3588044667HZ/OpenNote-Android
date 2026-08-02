package com.open.note.data.remote.api

import com.open.note.data.remote.dto.ApiResponse
import com.open.note.data.remote.dto.NotebookDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface NotebookApi {

    @GET("notebooks")
    suspend fun getNotebooks(): Response<ApiResponse<List<NotebookDto>>>

    @POST("notebooks")
    suspend fun createNotebook(@Body body: NotebookDto): Response<ApiResponse<NotebookDto>>

    @PUT("notebooks/{id}")
    suspend fun updateNotebook(
        @Path("id") id: String,
        @Body body: NotebookDto
    ): Response<ApiResponse<NotebookDto>>

    @DELETE("notebooks/{id}")
    suspend fun deleteNotebook(@Path("id") id: String): Response<ApiResponse<Unit>>
}

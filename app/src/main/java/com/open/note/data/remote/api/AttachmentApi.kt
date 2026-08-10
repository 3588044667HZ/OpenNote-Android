package com.open.note.data.remote.api

import com.open.note.data.remote.dto.ApiResponse
import com.open.note.data.remote.dto.AttachmentDto
import com.open.note.data.remote.dto.FileUploadResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface AttachmentApi {

    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("noteId") noteId: String
    ): Response<ApiResponse<FileUploadResponse>>

    @GET("attachments/{attachId}/download")
    suspend fun downloadFile(
        @Path("attachId") attachId: String
    ): Response<ResponseBody>

    @GET("notes/{noteId}/attachments")
    suspend fun getNoteAttachments(
        @Path("noteId") noteId: String
    ): Response<ApiResponse<List<AttachmentDto>>>

    @PUT("attachments/{attachId}")
    suspend fun updateAttachment(
        @Path("attachId") attachId: String,
        @Body body: UpdateAttachmentRequest
    ): Response<ApiResponse<AttachmentDto>>

    @DELETE("attachments/{attachId}")
    suspend fun deleteAttachment(
        @Path("attachId") attachId: String
    ): Response<ApiResponse<Unit>>
}

data class UpdateAttachmentRequest(
    val md5: String = "",
    val state: Int = 0,
    val url: String = ""
)

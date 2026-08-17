package com.open.note.data.remote.api

import com.open.note.data.remote.dto.ApiResponse
import com.open.note.data.remote.dto.AttachmentDto
import com.open.note.data.remote.dto.AttachmentPutResponse
import com.open.note.data.remote.dto.AttachmentRegRequest
import com.open.note.data.remote.dto.AttachmentRegResponse
import com.open.note.data.remote.dto.FileUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface AttachmentApi {

    /** 注册附件槽位（两阶段回执）：POST /attachments，幂等 */
    @POST("attachments")
    suspend fun registerAttachment(
        @Body body: AttachmentRegRequest
    ): Response<ApiResponse<AttachmentRegResponse>>

    /** 凭回执上传文件内容：PUT /attachments/:attachId/file，幂等可覆盖 */
    @PUT("attachments/{attachId}/file")
    suspend fun uploadFileContent(
        @Path("attachId") attachId: String,
        @Body body: RequestBody,
        @Header("Content-MD5") md5: String? = null
    ): Response<ApiResponse<AttachmentPutResponse>>

    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("noteId") noteId: String
    ): Response<ApiResponse<FileUploadResponse>>

    /** size=thumb 为服务端压缩图（正文默认），缺省为原图。永不 404，X-Attachment-State 标识状态 */
    @GET("attachments/{attachId}/download")
    suspend fun downloadFile(
        @Path("attachId") attachId: String,
        @Query("size") size: String? = null
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

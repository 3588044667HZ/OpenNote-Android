package com.open.note.data.remote.api

import com.open.note.data.remote.dto.ApiResponse
import com.open.note.data.remote.dto.ShareSettingsDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Header

interface ShareSettingsApi {

    @GET("settings/share")
    suspend fun getShareSettings(): Response<ApiResponse<ShareSettingsDto>>

    @PUT("settings/share")
    suspend fun updateShareSettings(@Body settings: ShareSettingsDto): Response<ApiResponse<ShareSettingsDto>>
}

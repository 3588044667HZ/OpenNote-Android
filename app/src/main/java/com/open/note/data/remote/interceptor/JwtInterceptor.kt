package com.open.note.data.remote.interceptor

import com.open.note.data.local.AuthStore
import com.open.note.data.remote.api.AuthApi
import com.open.note.data.remote.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class JwtInterceptor @Inject constructor(
    private val authStore: AuthStore,
    private val authApiProvider: Provider<AuthApi>
) : Interceptor {

    private val lock = Any()

    @Volatile
    private var isRefreshing = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (originalRequest.url.encodedPath.contains("auth/login") ||
            originalRequest.url.encodedPath.contains("auth/register") ||
            originalRequest.url.encodedPath.contains("auth/refresh")
        ) {
            return chain.proceed(originalRequest)
        }

        val accessToken = runBlocking { authStore.getAccessTokenBlocking() }
        val requestWithAuth = if (!accessToken.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(requestWithAuth)
        if (response.code == 401 && !accessToken.isNullOrBlank()) {
            response.close()

            synchronized(lock) {
                if (isRefreshing) return response
                isRefreshing = true
            }

            try {
                val refreshToken = runBlocking { authStore.getRefreshTokenBlocking() }
                if (refreshToken.isNullOrBlank()) {
                    runBlocking { authStore.clearTokens() }
                    return response
                }

                val refreshResponse = runBlocking {
                    authApiProvider.get().refresh(RefreshRequest(refreshToken))
                }

                if (refreshResponse.isSuccessful && refreshResponse.body()?.data != null) {
                    val data = refreshResponse.body()!!.data!!
                    runBlocking {
                        authStore.saveTokens(
                            access = data.token ?: "",
                            refresh = data.refreshToken ?: ""
                        )
                    }

                    val newAccessToken = runBlocking { authStore.getAccessTokenBlocking() }
                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                    return chain.proceed(newRequest)
                } else {
                    runBlocking { authStore.clearTokens() }
                    return response
                }
            } catch (e: Exception) {
                runBlocking { authStore.clearTokens() }
                throw e
            } finally {
                synchronized(lock) {
                    isRefreshing = false
                }
            }
        }

        return response
    }
}

package com.open.note.data.remote.interceptor

import android.util.Log
import com.open.note.data.local.AuthStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val authStore: AuthStore
) : Interceptor {

    companion object {
        private const val TAG = "BaseUrlInterceptor"
        private const val PLACEHOLDER_URL = "http://placeholder/api/"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:5000/api/"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url.toString()

        if (!originalUrl.contains("placeholder")) {
            return chain.proceed(originalRequest)
        }

        val rawUrl = runBlocking { authStore.getServerUrlBlocking() } ?: DEFAULT_BASE_URL
        val serverUrl = if (!rawUrl.endsWith("/")) "$rawUrl/" else rawUrl
        val newUrl = originalUrl.replace(PLACEHOLDER_URL, serverUrl).toHttpUrlOrNull()
            ?: return chain.proceed(originalRequest)

        Log.d(TAG, "Resolved URL: $newUrl")

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }
}

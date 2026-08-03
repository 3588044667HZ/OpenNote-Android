package com.open.note.data.repository

import android.util.Log
import com.open.note.data.local.AuthStore
import com.open.note.data.remote.api.AuthApi
import com.open.note.data.remote.dto.LoginRequest
import com.open.note.data.remote.dto.RegisterRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val authStore: AuthStore
) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting login for user: $username")
            val response = authApi.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                authStore.saveTokens(
                    access = data.token ?: "",
                    refresh = data.refreshToken ?: ""
                )
                val user = data.user
                if (user != null) {
                    authStore.saveUserInfo(
                        username = user.username ?: username,
                        createdAt = user.createdAt ?: ""
                    )
                }
                Log.d(TAG, "Login successful")
                authStore.saveCredentials(username, password)
                Result.success(Unit)
            } else {
                val msg = response.body()?.msg ?: "Login failed"
                Log.e(TAG, "Login failed: $msg")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    suspend fun register(username: String, password: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting register for user: $username")
            val response = authApi.register(RegisterRequest(username, password))
            if (response.isSuccessful) {
                Log.d(TAG, "Register successful, auto-logging in")
                login(username, password)
            } else {
                val msg = response.body()?.msg ?: "Registration failed"
                Log.e(TAG, "Register failed: $msg")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            Result.failure(e)
        }
    }

    suspend fun getMe(): Result<String> {
        return try {
            Log.d(TAG, "Fetching current user")
            val response = authApi.getMe()
            if (response.isSuccessful && response.body()?.data != null) {
                val user = response.body()!!.data!!
                val username = user.username ?: ""
                Log.d(TAG, "getMe successful: $username")
                Result.success(username)
            } else {
                val msg = response.body()?.msg ?: "Failed to get user info"
                Log.e(TAG, "getMe failed: $msg")
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMe error", e)
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            Log.d(TAG, "Logging out")
            val response = authApi.logout()
            authStore.clearTokens()
            Log.d(TAG, "Logout successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Logout error", e)
            authStore.clearTokens()
            Result.failure(e)
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return !authStore.getAccessTokenBlocking().isNullOrBlank()
    }
}

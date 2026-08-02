package com.open.note.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        private val USERNAME = stringPreferencesKey("username")
        private val USER_CREATED_AT = stringPreferencesKey("user_created_at")
        private val PASSWORD = stringPreferencesKey("password")
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SKIN_ID = stringPreferencesKey("skin_id")
        private val SHARE_LOGO_TEXT = stringPreferencesKey("share_logo_text")
        private val SHARE_WATERMARK = stringPreferencesKey("share_watermark")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACCESS_TOKEN]
    }

    val refreshToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[REFRESH_TOKEN]
    }

    val lastSyncTime: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LAST_SYNC_TIME]
    }

    val username: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USERNAME]
    }

    val userCreatedAt: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_CREATED_AT]
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL]
    }

    val skinId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SKIN_ID]
    }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = access
            prefs[REFRESH_TOKEN] = refresh
        }
    }

    suspend fun saveUserInfo(username: String, createdAt: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[USER_CREATED_AT] = createdAt
        }
    }

    suspend fun saveCredentials(username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[PASSWORD] = password
        }
    }

    suspend fun getSavedUsername(): String? {
        return context.dataStore.data.first()[USERNAME]
    }

    suspend fun getSavedPassword(): String? {
        return context.dataStore.data.first()[PASSWORD]
    }

    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun setLastSyncTime(time: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SYNC_TIME] = time
        }
    }

    suspend fun getLastSyncTimeBlocking(): String? {
        return context.dataStore.data.first()[LAST_SYNC_TIME]
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = url
        }
    }

    suspend fun getServerUrlBlocking(): String? {
        return context.dataStore.data.first()[SERVER_URL]
    }

    suspend fun setSkinId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[SKIN_ID] = id
        }
    }

    suspend fun getSkinIdBlocking(): String? {
        return context.dataStore.data.first()[SKIN_ID]
    }

    suspend fun getAccessTokenBlocking(): String? {
        return context.dataStore.data.first()[ACCESS_TOKEN]
    }

    suspend fun getRefreshTokenBlocking(): String? {
        return context.dataStore.data.first()[REFRESH_TOKEN]
    }

    suspend fun setShareLogoText(text: String) {
        context.dataStore.edit { prefs -> prefs[SHARE_LOGO_TEXT] = text }
    }

    suspend fun getShareLogoText(): String {
        return context.dataStore.data.first()[SHARE_LOGO_TEXT] ?: "分享来自 Open Note"
    }

    suspend fun setShareWatermark(text: String) {
        context.dataStore.edit { prefs -> prefs[SHARE_WATERMARK] = text }
    }

    suspend fun getShareWatermark(): String {
        return context.dataStore.data.first()[SHARE_WATERMARK] ?: "备忘录"
    }
}

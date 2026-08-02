package com.open.note.share

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.shareDataStore: DataStore<Preferences> by preferencesDataStore(name = "share_prefs")

object ShareSettings {

    private val KEY_LOGO_TEXT = stringPreferencesKey("share_logo_text")
    private val KEY_WATERMARK = stringPreferencesKey("share_watermark")
    private val KEY_LAST_FETCH = longPreferencesKey("share_last_fetch")

    fun getLogoText(context: Context): String = runBlocking {
        context.shareDataStore.data.first()[KEY_LOGO_TEXT] ?: "分享来自 Open Note"
    }

    fun getWatermark(context: Context): String = runBlocking {
        context.shareDataStore.data.first()[KEY_WATERMARK] ?: "备忘录"
    }

    fun getLastFetchTime(context: Context): Long = runBlocking {
        context.shareDataStore.data.first()[KEY_LAST_FETCH] ?: 0L
    }

    suspend fun saveLocal(context: Context, logoText: String, watermark: String) {
        context.shareDataStore.edit { prefs: MutablePreferences ->
            prefs[KEY_LOGO_TEXT] = logoText
            prefs[KEY_WATERMARK] = watermark
        }
    }

    suspend fun applyServerData(context: Context, logoText: String, watermark: String) {
        context.shareDataStore.edit { prefs: MutablePreferences ->
            prefs[KEY_LOGO_TEXT] = logoText
            prefs[KEY_WATERMARK] = watermark
            prefs[KEY_LAST_FETCH] = System.currentTimeMillis()
        }
    }

    suspend fun fetchFromServer(api: com.open.note.data.remote.api.ShareSettingsApi): Boolean {
        return try {
            val response = api.getShareSettings()
            if (response.isSuccessful && response.body()?.code == 0) {
                val data = response.body()!!.data!!
                Log.d("ShareSettings", "Fetched from server: $data")
                true
            } else false
        } catch (e: Exception) {
            Log.e("ShareSettings", "Fetch failed", e)
            false
        }
    }
}

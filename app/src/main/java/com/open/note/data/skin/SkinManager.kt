package com.open.note.data.skin

import android.content.Context
import android.content.res.Configuration
import com.open.note.data.local.AuthStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authStore: AuthStore
) {
    private val embedSkins: Map<String, Skin> = EmbedSkinInitializer.createSkins()
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _selectedSkin = MutableStateFlow(embedSkins[SkinData.SKIN_WHITE]!!)
    val selectedSkin: StateFlow<Skin> = _selectedSkin.asStateFlow()

    init {
        loadSavedSkin()
    }

    private fun loadSavedSkin() {
        scope.launch {
            val savedId = authStore.getSkinIdBlocking()
            if (!savedId.isNullOrEmpty()) {
                applySkin(savedId)
            }
        }
    }

    fun applySkin(skinId: String) {
        val effectiveId = if (skinId == SkinData.SKIN_WHITE && isSystemDarkMode()) {
            SkinData.SKIN_BLACK
        } else {
            skinId
        }
        embedSkins[effectiveId]?.let { skin ->
            _selectedSkin.value = skin
            scope.launch { authStore.setSkinId(skinId) }
        }
    }

    fun toggleEyeProtection() {
        val current = getCurrentSkin()
        if (current.id == SkinData.SKIN_YELLOW) {
            applySkin(SkinData.SKIN_WHITE)
        } else {
            applySkin(SkinData.SKIN_YELLOW)
        }
    }

    fun isEyeProtectionActive(): Boolean =
        getCurrentSkin().id == SkinData.SKIN_YELLOW

    fun getCurrentSkin(): Skin = _selectedSkin.value

    fun getAllSkins(): List<Skin> = SkinData.colorSkinList
        .mapNotNull { embedSkins[it] }

    fun getSkin(skinId: String): Skin? = embedSkins[skinId]

    fun isSystemDarkMode(): Boolean {
        val nightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }
}

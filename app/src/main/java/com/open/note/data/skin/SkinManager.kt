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

    private var currentSkinId: String = SkinData.SKIN_WHITE

    fun applySkin(skinId: String) {
        currentSkinId = skinId
        val skin = getEffectiveSkin(currentSkinId) ?: return
        _selectedSkin.value = skin
        scope.launch { authStore.setSkinId(skinId) }
    }

    private fun getEffectiveSkin(requestedId: String): Skin? {
        if (isSystemDarkMode()) {
            return embedSkins[requestedId]?.darkModeOverride
                ?.let { embedSkins[SkinData.SKIN_BLACK] }
                ?: embedSkins[SkinData.SKIN_BLACK]
        }
        return embedSkins[requestedId]
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

    fun getCurrentSkin(): Skin = getEffectiveSkin(currentSkinId) ?: embedSkins[SkinData.SKIN_WHITE]!!

    fun refreshSkin() {
        val skin = getEffectiveSkin(currentSkinId) ?: return
        _selectedSkin.value = skin
    }

    fun getAllSkins(): List<Skin> = SkinData.colorSkinList
        .mapNotNull { embedSkins[it] }

    fun getSkin(skinId: String): Skin? = embedSkins[skinId]

    fun isSystemDarkMode(): Boolean {
        val nightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }
}

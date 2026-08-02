package com.open.note.ui.skin

import androidx.lifecycle.ViewModel
import com.open.note.data.skin.Skin
import com.open.note.data.skin.SkinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SkinViewModel @Inject constructor(
    private val skinManager: SkinManager
) : ViewModel() {

    val selectedSkin: StateFlow<Skin> = skinManager.selectedSkin

    fun applySkin(skinId: String) = skinManager.applySkin(skinId)

    fun toggleEyeProtection() = skinManager.toggleEyeProtection()

    fun isEyeProtectionActive(): Boolean = skinManager.isEyeProtectionActive()

    fun getAllSkins(): List<Skin> = skinManager.getAllSkins()

    fun getCurrentSkin(): Skin = skinManager.getCurrentSkin()
}

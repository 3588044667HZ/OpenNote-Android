package com.open.note.data.skin

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

object SkinColors {
    fun parseColor(hex: String): Color {
        val parsed = try {
            AndroidColor.parseColor(hex)
        } catch (e: Exception) {
            AndroidColor.parseColor("#FFFFFF")
        }
        return Color(
            red = AndroidColor.red(parsed) / 255f,
            green = AndroidColor.green(parsed) / 255f,
            blue = AndroidColor.blue(parsed) / 255f,
            alpha = AndroidColor.alpha(parsed) / 255f
        )
    }
}

data class SkinColorScheme(
    val contentBackground: Color,
    val backCloth: Color,
    val textColor: Color,
    val titleColor: Color,
    val cardBackground: Color,
    val timeColor: Color
)

@Composable
fun rememberSkinColors(skinManager: SkinManager): SkinColorScheme {
    val skin by skinManager.selectedSkin.collectAsState()
    return SkinColorScheme(
        contentBackground = SkinColors.parseColor(skin.contentBackground.color),
        backCloth = SkinColors.parseColor(skin.backCloth),
        textColor = SkinColors.parseColor(skin.textColor),
        titleColor = SkinColors.parseColor(skin.titleColor),
        cardBackground = SkinColors.parseColor(skin.cardBackground),
        timeColor = SkinColors.parseColor(skin.timeColor)
    )
}

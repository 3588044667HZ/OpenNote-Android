package com.open.note.data.skin

data class Skin(
    val id: String,
    val contentBackground: Background,
    val backCloth: String,
    val textColor: String,
    val titleColor: String,
    val cardBackground: String,
    val timeColor: String,
    val darkModeOverride: DarkModeOverride? = null
) {
    data class DarkModeOverride(
        val contentBackground: Background,
        val backCloth: String,
        val textColor: String,
        val titleColor: String,
        val cardBackground: String,
        val timeColor: String
    )
}

data class Background(
    val color: String,
    val type: BackgroundType,
    val imageUri: String? = null,
    val tileMode: TileMode? = null
)

enum class BackgroundType {
    PURE_COLOR, GRADIENT, PICTURE
}

enum class TileMode {
    CLAMP, REPEAT, MIRROR
}

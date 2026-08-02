package com.open.note.data.skin

object EmbedSkinInitializer {
    fun createSkins(): Map<String, Skin> = mapOf(
        SkinData.SKIN_WHITE to Skin(
            id = SkinData.SKIN_WHITE,
            contentBackground = Background("#FFFFFF", BackgroundType.PURE_COLOR),
            backCloth = "#F5F5F5",
            textColor = "#333333",
            titleColor = "#222222",
            cardBackground = "#FFFFFF",
            timeColor = "#999999",
            darkModeOverride = Skin.DarkModeOverride(
                contentBackground = Background("#1E1E1E", BackgroundType.PURE_COLOR),
                backCloth = "#2D2D2D",
                textColor = "#CCCCCC",
                titleColor = "#FFFFFF",
                cardBackground = "#252525",
                timeColor = "#888888"
            )
        ),
        SkinData.SKIN_YELLOW to Skin(
            id = SkinData.SKIN_YELLOW,
            contentBackground = Background("#FEF7E2", BackgroundType.PURE_COLOR),
            backCloth = "#EFE8D4",
            textColor = "#96826C",
            titleColor = "#7A5F3A",
            cardBackground = "#FFF9E6",
            timeColor = "#B8A88A"
        ),
        SkinData.SKIN_CYAN to Skin(
            id = SkinData.SKIN_CYAN,
            contentBackground = Background("#E0F7FA", BackgroundType.PURE_COLOR),
            backCloth = "#CCEBF0",
            textColor = "#006064",
            titleColor = "#004D40",
            cardBackground = "#E6F9FC",
            timeColor = "#80CBC4"
        ),
        SkinData.SKIN_BLUE to Skin(
            id = SkinData.SKIN_BLUE,
            contentBackground = Background("#E3F2FD", BackgroundType.PURE_COLOR),
            backCloth = "#CFE4F7",
            textColor = "#0D47A1",
            titleColor = "#0A2F6E",
            cardBackground = "#EBF5FB",
            timeColor = "#90CAF9"
        ),
        SkinData.SKIN_GREEN to Skin(
            id = SkinData.SKIN_GREEN,
            contentBackground = Background("#E8F5E9", BackgroundType.PURE_COLOR),
            backCloth = "#D0E8D2",
            textColor = "#2E7D32",
            titleColor = "#1B5E20",
            cardBackground = "#EDF7EE",
            timeColor = "#A5D6A7"
        ),
        SkinData.SKIN_RED to Skin(
            id = SkinData.SKIN_RED,
            contentBackground = Background("#FFEBEE", BackgroundType.PURE_COLOR),
            backCloth = "#F2D7DA",
            textColor = "#C62828",
            titleColor = "#B71C1C",
            cardBackground = "#FFF0F2",
            timeColor = "#EF9A9A"
        ),
        SkinData.SKIN_GREY to Skin(
            id = SkinData.SKIN_GREY,
            contentBackground = Background("#ECEFF1", BackgroundType.PURE_COLOR),
            backCloth = "#D8DDE0",
            textColor = "#455A64",
            titleColor = "#37474F",
            cardBackground = "#F0F2F4",
            timeColor = "#B0BEC5"
        ),
        SkinData.SKIN_BLACK to Skin(
            id = SkinData.SKIN_BLACK,
            contentBackground = Background("#1E1E1E", BackgroundType.PURE_COLOR),
            backCloth = "#2D2D2D",
            textColor = "#CCCCCC",
            titleColor = "#FFFFFF",
            cardBackground = "#252525",
            timeColor = "#888888"
        )
    )
}

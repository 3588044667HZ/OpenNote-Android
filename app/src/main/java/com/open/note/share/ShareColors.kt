package com.open.note.share

data class ShareColors(
    val contentBg: String = "#FFFFFF",
    val backcloth: String = "#FAFAFA",
    val textColor: String = "#1A1A1A",
    val titleColor: String = "#000000",
    val timeColor: String = "#999999",
    val cardBg: String = "#FFFFFF",
    val borderColor: String = "#0000001F"
) {
    companion object {
        val WHITE = ShareColors()

        val YELLOW = ShareColors(
            contentBg = "#FEF7E2", backcloth = "#EFE8D4",
            textColor = "#96826C", titleColor = "#96826C",
            timeColor = "#5F4A33", cardBg = "#FBF7E8"
        )

        val BLACK = ShareColors(
            contentBg = "#000000", backcloth = "#2E2E2E",
            textColor = "#FFFFFF", titleColor = "#FFFFFF",
            timeColor = "#8CFFFFFF", cardBg = "#1B1B1B",
            borderColor = "#FFFFFF33"
        )
    }
}

data class ShareWatermarkConfig(
    val textCn: String = "备忘录",
    val textEn: String = "Note",
    val logoText: String = "分享来自 Open Note",
    val showLogo: Boolean = true,
    val customColor: Int? = null
)

package com.open.note.data.skin

object SkinWebView {

    fun generateSkinCSS(skin: Skin): String {
        val dark = skin.darkModeOverride
        val darkCss = if (dark != null) """
            @media (prefers-color-scheme: dark) {
                :root {
                    --content-bg: ${dark.contentBackground.color};
                    --back-cloth: ${dark.backCloth};
                    --text-color: ${dark.textColor};
                    --title-color: ${dark.titleColor};
                    --card-bg: ${dark.cardBackground};
                    --time-color: ${dark.timeColor};
                }
            }
        """.trimIndent() else ""

        val css = """
            :root {
                --content-bg: ${skin.contentBackground.color};
                --back-cloth: ${skin.backCloth};
                --text-color: ${skin.textColor};
                --title-color: ${skin.titleColor};
                --card-bg: ${skin.cardBackground};
                --time-color: ${skin.timeColor};
            }
            $darkCss
            body {
                background-color: var(--content-bg);
                color: var(--text-color);
            }
            .ProseMirror {
                background-color: var(--content-bg);
                color: var(--text-color);
            }
        """.trimIndent()

        val escapedCss = css
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\$", "\\\$")

        return """
            (function() {
                var style = document.createElement('style');
                style.id = 'skin-colors';
                style.textContent = `${escapedCss}`;
                var existing = document.getElementById('skin-colors');
                if (existing) existing.remove();
                document.head.appendChild(style);
            })();
        """.trimIndent()
    }
}

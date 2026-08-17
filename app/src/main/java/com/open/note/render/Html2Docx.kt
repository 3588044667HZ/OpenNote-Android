package com.open.note.render

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * HTML → DOCX 转换器
 * 与 OPPO 便签 HtmlToDocVisitor 相同的 HTML → Word XML 映射规则。
 * 用 java.util.zip 替代 OPPO AI Unit 完成 docx 打包（零依赖）。
 */
object Html2Docx {

    private const val TAG = "Html2Docx"
    private const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun convert(
        html: String,
        title: String = "document",
        images: List<DocxImage> = emptyList(),
        outputFile: File
    ) {
        val documentXml = htmlToWordML(html)
        android.util.Log.d(TAG, "HTML in: ${html.take(300)}")
        android.util.Log.d(TAG, "WordML out: ${documentXml.take(600)}")
        writeDocx(documentXml, images, outputFile)
    }

    private fun htmlToWordML(html: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="$NS_W" xmlns:r="$NS_R">""")
        sb.append("<w:body>")
        walkNodes(HtmlParser.parse(html), sb, FormatStack())
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    /** 当前生效的行内格式（避免 <w:r> 嵌套，格式统一应用到 text 节点） */
    private class FormatStack {
        val color = ArrayDeque<String>()
        val highlight = ArrayDeque<String>()
        val underline = ArrayDeque<String>()  // "single" / "wave" / null
        val bold = ArrayDeque<Boolean>()
        val italic = ArrayDeque<Boolean>()
        val strike = ArrayDeque<Boolean>()

        fun currentColor(): String? = color.lastOrNull()
        fun currentHighlight(): String? = highlight.lastOrNull()
        fun currentUnderline(): String? = underline.lastOrNull()
        fun isBold(): Boolean = bold.lastOrNull() == true
        fun isItalic(): Boolean = italic.lastOrNull() == true
        fun isStrike(): Boolean = strike.lastOrNull() == true
    }

    private fun walkNodes(node: HtmlNode, sb: StringBuilder, fmt: FormatStack) {
        when (node.type) {
            "h1" -> writeParagraph(node, sb, fmt, headingLevel = "2")
            "h2" -> writeParagraph(node, sb, fmt, headingLevel = "3")
            "h3" -> writeParagraph(node, sb, fmt, headingLevel = "4")
            "h4" -> writeParagraph(node, sb, fmt, headingLevel = "5")
            "p", "div" -> writeParagraph(node, sb, fmt)
            "br" -> sb.append("<w:p/>")
            "b", "strong" -> {
                fmt.bold.addLast(true); walkChildren(node, sb, fmt); fmt.bold.removeLast()
            }
            "i", "em" -> {
                fmt.italic.addLast(true); walkChildren(node, sb, fmt); fmt.italic.removeLast()
            }
            "u" -> writeUnderline(node, sb, fmt)
            "s", "del" -> {
                fmt.strike.addLast(true); walkChildren(node, sb, fmt); fmt.strike.removeLast()
            }
            "span" -> writeSpan(node, sb, fmt)
            "a" -> writeLink(node, sb, fmt)
            "img" -> writeImage(node, sb)
            "table" -> writeTable(node, sb, fmt)
            "ul", "ol" -> writeList(node, sb, fmt)
            "li" -> {
                sb.append("<w:p><w:pPr><w:numPr/></w:pPr>")
                walkChildren(node, sb, fmt)
                sb.append("</w:p>")
            }
            "text" -> writeText(node.text, sb, fmt)
            "#root", "#document" -> walkChildren(node, sb, fmt)
        }
    }

    private fun writeText(text: String, sb: StringBuilder, fmt: FormatStack) {
        sb.append("<w:r><w:rPr>")
        fmt.currentColor()?.let { sb.append("<w:color w:val=\"$it\"/>") }
        fmt.currentHighlight()?.let { sb.append("<w:highlight w:val=\"$it\"/>") }
        fmt.currentUnderline()?.let { sb.append("<w:u w:val=\"$it\"/>") }
        if (fmt.isBold()) sb.append("<w:b/>")
        if (fmt.isItalic()) sb.append("<w:i/>")
        if (fmt.isStrike()) sb.append("<w:strike/>")
        sb.append("</w:rPr>")
        sb.append("<w:t xml:space=\"preserve\">${text.xmlEscape()}</w:t></w:r>")
    }

    private fun writeParagraph(node: HtmlNode, sb: StringBuilder, fmt: FormatStack, headingLevel: String? = null) {
        sb.append("<w:p>")
        sb.append("<w:pPr>")
        if (headingLevel != null) sb.append("<w:pStyle w:val=\"$headingLevel\"/>")

        node.attrs["align"]?.let { align ->
            val valign = when (align) { "center" -> "center"; "right" -> "end"; else -> "left" }
            sb.append("<w:jc w:val=\"$valign\"/>")
        }
        if (node.attrs["style"]?.contains("text-align:center") == true)
            sb.append("<w:jc w:val=\"center\"/>")
        if (node.attrs["style"]?.contains("text-align:right") == true)
            sb.append("<w:jc w:val=\"end\"/>")
        sb.append("</w:pPr>")
        walkChildren(node, sb, fmt)
        sb.append("</w:p>")
    }

    private fun writeSpan(node: HtmlNode, sb: StringBuilder, fmt: FormatStack) {
        val style = node.attrs["style"] ?: ""
        val cls = node.attrs["class"] ?: ""

        var pushed = false
        // 文字颜色：支持 var(--redColor) CSS 变量 和 内联 hex/rgb/name
        extractCssValue(style, "color")?.let { colorVal ->
            val hex = cssColorToHex(colorVal) ?: cssVarToHex(colorVal)
            android.util.Log.d(TAG, "span color: '$colorVal' -> '$hex'")
            if (hex != null) {
                fmt.color.addLast(hex); pushed = true
            }
        }
        // 高亮：支持 class="highlight_xxx" 和内联 background
        if (cls.startsWith("highlight_")) {
            val colorName = cls.removePrefix("highlight_")
            highlightToWord(colorName)?.let {
                android.util.Log.d(TAG, "span highlight class: '$cls' -> '$it'")
                fmt.highlight.addLast(it); pushed = true
            }
        } else {
            extractCssValue(style, "background")?.let { bg ->
                if (bg.lowercase() in listOf("yellow", "#ffff00")) {
                    fmt.highlight.addLast("yellow"); pushed = true
                }
            }
        }
        if (style.contains("font-weight:bold") || style.contains("font-weight: 700")) {
            fmt.bold.addLast(true); pushed = true
        }

        walkChildren(node, sb, fmt)

        // 弹出本 span 压入的格式
        if (pushed) {
            if (fmt.color.size > 0) fmt.color.removeLast()
            if (fmt.highlight.size > 0) fmt.highlight.removeLast()
            if (fmt.bold.size > 0) fmt.bold.removeLast()
        }
    }

    /** <u> 标签：支持 solid/wavy 类 + 颜色 */
    private fun writeUnderline(node: HtmlNode, sb: StringBuilder, fmt: FormatStack) {
        val cls = node.attrs["class"] ?: ""
        android.util.Log.d(TAG, "underline class: '$cls'")
        when {
            cls.contains("underline_wavy_") -> {
                val colorKey = cls.substringAfter("underline_wavy_")
                colorKeyToHex(colorKey)?.let { fmt.color.addLast(it) }
                // Word 原生波浪下划线
                fmt.underline.addLast("wave")
            }
            cls.contains("underline_solid_") -> {
                val colorKey = cls.substringAfter("underline_solid_")
                colorKeyToHex(colorKey)?.let { fmt.color.addLast(it) }
                fmt.underline.addLast("single")
            }
            else -> fmt.underline.addLast("single")
        }

        walkChildren(node, sb, fmt)

        if (fmt.underline.size > 0) fmt.underline.removeLast()
        if (fmt.color.size > 0) fmt.color.removeLast()
    }

    /** CSS 变量 → hex：var(--redColor) → EA4335 */
    private fun cssVarToHex(colorVal: String): String? {
        val m = Regex("""var\(--([a-zA-Z]+Color)\)""").find(colorVal)
            ?: Regex("""var\(--([a-zA-Z]+)\)""").find(colorVal)
        return m?.groupValues?.get(1)?.let { varName ->
            cssVarHexMap[varName]
        }
    }

    /** color_red / red 等 → hex */
    private fun colorKeyToHex(colorKey: String): String? = when {
        colorKey.startsWith("color_") -> colorClassHexMap[colorKey.removePrefix("color_")]
        else -> cssColorToHex(colorKey)
    }

    private val cssVarHexMap = mapOf(
        "blueColor" to "1A73E8", "redColor" to "EA4335",
        "greenColor" to "34A853", "orangeColor" to "FB9600",
        "yellowColor" to "F9AB00", "grayColor" to "5F6368",
        "default" to "000000"
    )

    private val colorClassHexMap = mapOf(
        "red" to "EA4335", "blue" to "1A73E8", "green" to "34A853",
        "yellow" to "F9AB00", "orange" to "FB9600", "gray" to "5F6368",
        "default" to "000000"
    )

    /** highlight_xxx → Word 高亮色名 */
    private fun highlightToWord(colorName: String): String? = when (colorName) {
        "yellow" -> "yellow"
        "red" -> "red"
        "blue" -> "cyan"
        "green" -> "green"
        else -> null
    }

    private fun writeLink(node: HtmlNode, sb: StringBuilder, fmt: FormatStack) {
        val href = node.attrs["href"] ?: ""
        sb.append("<w:hyperlink r:id=\"$href\">")
        fmt.color.addLast("1A73E8")
        fmt.underline.addLast("single")
        walkChildren(node, sb, fmt)
        if (fmt.underline.size > 0) fmt.underline.removeLast()
        if (fmt.color.size > 0) fmt.color.removeLast()
        sb.append("</w:hyperlink>")
    }

    private var imageCounter = 0

    private fun writeImage(node: HtmlNode, sb: StringBuilder) {
        imageCounter++
        val rId = "rIdImg${imageCounter}"
        val w = node.attrs["width"]?.toIntOrNull() ?: 500
        val h = node.attrs["height"]?.toIntOrNull() ?: 375
        val emuW = w * 9525
        val emuH = h * 9525
        sb.append("<w:p><w:r>")
        sb.append("<w:drawing><wp:inline>")
        sb.append("<wp:extent cx=\"$emuW\" cy=\"$emuH\"/>")
        sb.append("<wp:docPr id=\"${imageCounter}\" name=\"$rId\"/>")
        sb.append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
        sb.append("<pic:pic><pic:nvPicPr/><pic:blipFill><a:blip r:embed=\"$rId\"/></pic:blipFill>")
        sb.append("<pic:spPr><a:xfrm><a:ext cx=\"$emuW\" cy=\"$emuH\"/></a:xfrm></pic:spPr>")
        sb.append("</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing>")
        sb.append("</w:r></w:p>")
    }

    private fun writeTable(node: HtmlNode, sb: StringBuilder, fmt: FormatStack) {
        sb.append("<w:tbl><w:tblPr><w:tblLayout w:val=\"fixed\"/></w:tblPr>")
        walkChildren(node, sb, fmt)
        sb.append("</w:tbl>")
    }

    private fun writeList(node: HtmlNode, sb: StringBuilder, fmt: FormatStack) {
        walkChildren(node, sb, fmt)
    }

    private fun walkChildren(node: HtmlNode, sb: StringBuilder, fmt: FormatStack) {
        node.children.forEach { walkNodes(it, sb, fmt) }
    }

    private fun extractCssValue(style: String, property: String): String? {
        val re = Regex("""${Regex.escape(property)}\s*:\s*([^;]+)""")
        return re.find(style)?.groupValues?.get(1)?.trim()
    }

    private fun cssColorToHex(color: String): String? = when {
        color.startsWith("#") && color.length == 7 -> color.substring(1)
        color.startsWith("rgb") -> {
            val nums = Regex("\\d+").findAll(color).map { it.value.toInt() }.toList()
            if (nums.size >= 3) "%02X%02X%02X".format(nums[0], nums[1], nums[2]) else null
        }
        color.lowercase() in colorNames -> colorNames[color.lowercase()]
        else -> null
    }

    private val colorNames = mapOf(
        "red" to "EA4335", "blue" to "1A73E8", "green" to "34A853",
        "yellow" to "F9AB00", "orange" to "FB9600", "purple" to "A142F4",
        "black" to "000000", "white" to "FFFFFF", "gray" to "5F6368"
    )

    private fun writeDocx(documentXml: String, images: List<DocxImage>, output: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            val imageTypes = images.mapIndexed { i, img ->
                """<Override PartName="/word/media/image${i + 1}.${img.ext}" 
                  ContentType="${mimeType(img.ext)}"/>"""
            }.joinToString("")

            zip.putEntry("[Content_Types].xml", """
                <?xml version="1.0"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" 
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  ${imageTypes}
                </Types>""".trimIndent())

            zip.putEntry("_rels/.rels", """
                <?xml version="1.0"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent())

            zip.putEntry("word/document.xml", documentXml)

            val rels = StringBuilder("""<?xml version="1.0"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            images.forEachIndexed { i, img ->
                val name = "image${i + 1}.${img.ext}"
                rels.append("""<Relationship Id="rIdImg${i + 1}" 
                  Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                  Target="media/$name"/>""")
                zip.putEntry("word/media/$name", img.bytes)
            }
            rels.append("</Relationships>")
            zip.putEntry("word/_rels/document.xml.rels", rels.toString())
        }
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun mimeType(ext: String) = when (ext.lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"; "webp" -> "image/webp"; else -> "image/png"
    }

    private fun String.xmlEscape(): String = this
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}

data class DocxImage(
    val bytes: ByteArray,
    val ext: String
)

class HtmlNode(
    val type: String,
    val text: String = "",
    val attrs: Map<String, String> = emptyMap(),
    val children: List<HtmlNode> = emptyList()
)

object HtmlParser {
    fun parse(html: String): HtmlNode {
        val clean = html.replace(Regex("<!--.*?-->"), "").trim()
        val root = HtmlNode("#root", children = mutableListOf())
        parseNodes(clean, root.children as MutableList)
        return root
    }

    private fun parseNodes(html: String, into: MutableList<HtmlNode>, depth: Int = 0) {
        var pos = 0
        while (pos < html.length) {
            if (html[pos] == '<') {
                val end = html.indexOf('>', pos)
                if (end == -1) break
                val tag = html.substring(pos + 1, end)
                pos = end + 1
                if (tag.startsWith("/")) {
                    return
                }
                val space = tag.indexOf(' ')
                val name = if (space > 0) tag.substring(0, space) else tag
                val attrs = parseAttrs(tag)

                if (name in setOf("br", "hr", "img")) {
                    into.add(HtmlNode(name, attrs = attrs))
                } else {
                    val children = mutableListOf<HtmlNode>()
                    parseNodes(html.substring(pos), children, depth + 1)
                    val closeTag = "</$name>"
                    val closeIdx = html.indexOf(closeTag, pos)
                    if (closeIdx >= 0) pos = closeIdx + closeTag.length
                    into.add(HtmlNode(name, attrs = attrs, children = children))
                }
            } else {
                val nextTag = html.indexOf('<', pos)
                val textEnd = if (nextTag >= 0) nextTag else html.length
                val text = html.substring(pos, textEnd).trim()
                if (text.isNotEmpty())
                    into.add(HtmlNode("text", text = text))
                pos = textEnd
            }
        }
    }

    private fun parseAttrs(tag: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val re = Regex("""(\w+)=["']([^"']*)["']""")
        re.findAll(tag).forEach { map[it.groupValues[1]] = it.groupValues[2] }
        return map
    }
}

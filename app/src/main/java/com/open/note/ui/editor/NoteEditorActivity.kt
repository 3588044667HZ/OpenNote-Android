package com.open.note.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.open.note.R
import com.open.note.data.local.entity.Folder
import com.open.note.data.skin.SkinColors
import com.open.note.data.skin.SkinManager
import com.open.note.data.skin.SkinWebView
import com.open.note.ui.skin.SkinViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject

@AndroidEntryPoint
class NoteEditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        fun newIntent(context: Context, noteId: String? = null): Intent =
            Intent(context, NoteEditorActivity::class.java).apply { putExtra(EXTRA_NOTE_ID, noteId) }
    }

    @Inject lateinit var skinManager: SkinManager

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        skinManager.refreshSkin()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        setContent { MaterialTheme { NoteEditorScreen(noteId = noteId, onBack = { finish() }) } }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: String?,
    onBack: () -> Unit,
    editorViewModel: NoteEditorViewModel = hiltViewModel(),
    skinViewModel: SkinViewModel = hiltViewModel()
) {
    val title by editorViewModel.title.collectAsState()
    val content by editorViewModel.content.collectAsState()
    val contentLength by editorViewModel.contentLength.collectAsState()
    val isDirty by editorViewModel.isDirty.collectAsState()
    val isPinned by editorViewModel.isPinned.collectAsState()
    val selectedColor by editorViewModel.selectedColor.collectAsState()
    val notebooks by editorViewModel.notebooks.collectAsState()
    val selectedNotebookId by editorViewModel.selectedNotebookId.collectAsState()
    val skin by skinViewModel.selectedSkin.collectAsState()
    val skinBg = SkinColors.parseColor(skin.backCloth)
    val skinCardBg = SkinColors.parseColor(skin.cardBackground)

    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val editorBridge = remember { EditorBridge() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var colorPickerMode by remember { mutableStateOf<PickerMode?>(null) }
    val attachmentDao = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.open.note.di.AttachmentEntryPoint::class.java
        ).attachmentDao()
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val noteKey = noteId ?: "draft"
        MainScope().launch {
            com.open.note.share.AttachmentManager(
                context, attachmentDao, webView
            ).insertImage(uri, noteKey)
        }
    }

    LaunchedEffect(noteId) { editorViewModel.initialize(noteId) }
    LaunchedEffect(skin) {
        webView?.post {
            webView?.evaluateJavascript(SkinWebView.generateSkinCSS(skin)) { r -> if (r == "null") Log.w("Editor", "skinCSS null") }
        }
    }

    editorBridge.onContentChanged = { titleText, markdown, length ->
        editorViewModel.onContentChanged(titleText, markdown, length)
    }
    editorBridge.onEditorReady = {
        webView?.post {
            val currentNote = editorViewModel.note.value
            val data = JSONObject().apply {
                put("title", currentNote?.title ?: "")
                put("content", currentNote?.content ?: "")
            }.toString()
            webView?.evaluateJavascript("window.__setContent($data)") { r -> if (r == "null") Log.w("Editor", "setContent null") }
            webView?.evaluateJavascript(SkinWebView.generateSkinCSS(skin)) { r -> if (r == "null") Log.w("Editor", "skinCSS null") }
            editorViewModel.onContentLoadingComplete()
        }
    }

    LaunchedEffect(editorViewModel.note.value) {
        val currentNote = editorViewModel.note.value
        if (currentNote != null) {
            webView?.post {
                val data = JSONObject().apply {
                    put("title", currentNote.title)
                    put("content", currentNote.content)
                }.toString()
                webView?.evaluateJavascript("window.__setContent($data)") { r -> if (r == "null") Log.w("Editor", "retry null") }
            }
        }
    }

    Scaffold(
        containerColor = skinBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "$contentLength",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { editorViewModel.saveNow(); onBack() },
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Back",
                            modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    var showMore by remember { mutableStateOf(false) }

                    NotebookDropdown(notebooks, selectedNotebookId) { editorViewModel.onNotebookSelected(it) }
                    IconButton(onClick = { editorViewModel.saveNow() },
                        modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_check2), contentDescription = "Save",
                            tint = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        webView?.context?.let { ctx -> doShareAsImage(webView!!, skin, ctx, title) }
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                    }
                    Box {
                        IconButton(onClick = { showMore = true }, modifier = Modifier.size(32.dp)) {
                            Icon(painterResource(R.drawable.ic_more_vertical), contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false },
                            modifier = Modifier.background(skinCardBg)) {
                            DropdownMenuItem(
                                text = { Text("Color: ${
                                    mapOf("blue" to "Blue","green" to "Green","yellow" to "Yellow",
                                        "orange" to "Orange","red" to "Red","gray" to "Gray")[selectedColor] ?: selectedColor
                                }") },
                                onClick = { }
                            )
                            Divider()
                            listOf("blue" to "Blue", "green" to "Green", "yellow" to "Yellow",
                                "orange" to "Orange", "red" to "Red", "gray" to "Gray").forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        val c = mapOf("blue" to Color(0xFF4A90D9),"green" to Color(0xFF7ED321),
                                            "yellow" to Color(0xFFF5A623),"orange" to Color(0xFFF0984C),
                                            "red" to Color(0xFFD0021B),"gray" to Color(0xFF9B9B9B))[key]!!
                                        Box(Modifier.size(16.dp).clip(CircleShape).background(c))
                                    },
                                    onClick = { editorViewModel.onColorSelected(key) }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text(if (isPinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null,
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { editorViewModel.togglePin(); showMore = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showDeleteDialog = true; showMore = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = skinBg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .fillMaxSize()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.allowFileAccess = false
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        try {
                            val method = View::class.java.getMethod(
                                "setFocusHighlightColor", Int::class.javaPrimitiveType
                            )
                            method.invoke(this, android.graphics.Color.TRANSPARENT)
                        } catch (_: Exception) {}
                        @Suppress("JavascriptInterface")
                        addJavascriptInterface(editorBridge, "__nativeBridge")
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                Log.d("WebView", "[${msg.messageLevel()}] L${msg.lineNumber()}: ${msg.message()}")
                                return true
                            }
                        }
                        val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(ctx))
                            .build()
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: android.webkit.WebResourceRequest
                            ): android.webkit.WebResourceResponse? {
                                // 用 path 部分（不含域名），形如 /{noteId}/{attachId}_placeholder.png
                                val path = request.url.path ?: ""
                                val placeholderMarker = "_placeholder.png"
                                val idx = path.indexOf(placeholderMarker)
                                if (idx > 0) {
                                    val relative = path.substring(1, idx + placeholderMarker.length)
                                    val file = File(ctx.filesDir, relative)
                                    if (file.exists() && file.length() > 0) {
                                        return android.webkit.WebResourceResponse(
                                            "image/webp", "UTF-8", file.inputStream())
                                    }
                                }
                                return assetLoader.shouldInterceptRequest(request.url)
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                view.evaluateJavascript("typeof window.__setContent") { result ->
                                    if (result == "\"function\"") {
                                        editorBridge.onEditorReady?.invoke()
                                    } else {
                                        Log.e("WebView", "__setContent not ready: $result, retrying...")
                                        view.postDelayed({ editorBridge.onEditorReady?.invoke() }, 500)
                                    }
                                }
                            }
                            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest?,
                                                          error: android.webkit.WebResourceError?) {
                                Log.e("WebView", "Page load error: ${error?.description}")
                            }
                        }
                        loadUrl("https://appassets.androidplatform.net/assets/editor.html")
                    }.also { webView = it }
                },
                modifier = Modifier.weight(1f).fillMaxWidth())
            EditorToolbarRow(webView = webView, bgColor = skinBg, imeVisible = imeVisible,
                onInsertImage = {
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    ))
                },
                onTextColor = { colorPickerMode = PickerMode.TEXT_COLOR },
                onHighlight = { colorPickerMode = PickerMode.HIGHLIGHT },
                onSolidUnderline = { colorPickerMode = PickerMode.SOLID_UNDERLINE },
                onWavyUnderline = { colorPickerMode = PickerMode.WAVY_UNDERLINE })
        }
    }

    if (colorPickerMode != null) {
        TextColorPickerSheet(
            mode = colorPickerMode!!,
            onColorSelected = { colorVar ->
                val colorName = colorVar.removePrefix("--").removeSuffix("Color").lowercase()
                when (colorPickerMode) {
                    PickerMode.TEXT_COLOR ->
                        webView?.evaluateJavascript("window.editor.setTextColor('$colorVar')") { r ->
                            if (r == "null") Log.w("Editor", "setTextColor null")
                        }
                    PickerMode.HIGHLIGHT ->
                        webView?.evaluateJavascript("window.editor.setHighlight('$colorName')") { r ->
                            if (r == "null") Log.w("Editor", "setHighlight null")
                        }
                    PickerMode.SOLID_UNDERLINE ->
                        webView?.evaluateJavascript("window.editor.toggleColoredUnderline('solid','$colorName')") { r ->
                            if (r == "null") Log.w("Editor", "toggleColoredUnderline null")
                        }
                    PickerMode.WAVY_UNDERLINE ->
                        webView?.evaluateJavascript("window.editor.toggleColoredUnderline('wavy','$colorName')") { r ->
                            if (r == "null") Log.w("Editor", "toggleWavy null")
                        }
                    null -> {}
                }
                colorPickerMode = null
            },
            onClearColor = {
                when (colorPickerMode) {
                    PickerMode.TEXT_COLOR ->
                        webView?.evaluateJavascript("window.editor.unsetTextColor()") { r ->
                            if (r == "null") Log.w("Editor", "unsetTextColor null")
                        }
                    PickerMode.HIGHLIGHT ->
                        webView?.evaluateJavascript("window.editor.unsetHighlight()") { r ->
                            if (r == "null") Log.w("Editor", "unsetHighlight null")
                        }
                    else ->
                        webView?.evaluateJavascript("window.editor.unsetColoredUnderline()") { r ->
                            if (r == "null") Log.w("Editor", "unsetColoredUnderline null")
                        }
                }
                colorPickerMode = null
            },
            onDismiss = { colorPickerMode = null }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note? It will be moved to trash.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; editorViewModel.deleteNote(); onBack() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } })
    }
}

@Composable
fun EditorToolbarRow(
    webView: WebView?,
    onInsertImage: () -> Unit = {},
    onTextColor: () -> Unit = {},
    onHighlight: () -> Unit = {},
    onSolidUnderline: () -> Unit = {},
    onWavyUnderline: () -> Unit = {},
    bgColor: Color = Color.Transparent,
    imeVisible: Boolean = false
) {
    var showFormatBar by remember { mutableStateOf(false) }

    Surface(color = bgColor) {
        Column {
            // 横向格式栏：IME 存在时替换 IME，不存在时直接展开
            AnimatedVisibility(
                visible = showFormatBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                FormatBarRow(
                    webView = webView,
                    onTextColor = onTextColor,
                    onHighlight = onHighlight,
                    onSolidUnderline = onSolidUnderline,
                    onWavyUnderline = onWavyUnderline,
                    bgColor = bgColor
                )
            }

            // 主工具栏行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIconBtn(painterResource(R.drawable.ic_fonts)) {
                    // 如果 IME 弹出则先收起键盘（替换 IME 位置）
                    if (imeVisible) {
                        webView?.clearFocus()
                        webView?.postDelayed({ showFormatBar = !showFormatBar }, 150)
                    } else {
                        showFormatBar = !showFormatBar
                    }
                }
                ToolIconBtn(painterResource(R.drawable.ic_image_fill)) { onInsertImage() }
            }
        }
    }
}

@Composable
fun FormatBarRow(
    webView: WebView?,
    onTextColor: () -> Unit,
    onHighlight: () -> Unit,
    onSolidUnderline: () -> Unit,
    onWavyUnderline: () -> Unit,
    bgColor: Color
) {
    Surface(color = bgColor) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            FormatIconBtn(R.drawable.ic_format_bold) {
                webView?.evaluateJavascript("editor.chain().focus().toggleBold().run()", null)
            }
            FormatIconBtn(R.drawable.ic_format_italic) {
                webView?.evaluateJavascript("editor.chain().focus().toggleItalic().run()", null)
            }
            FormatIconBtn(R.drawable.ic_format_underline) {
                webView?.evaluateJavascript(
                    "editor.chain().focus().toggleColoredUnderline({type:'solid',color:'color_default'}).run()",
                    null)
            }
            FormatIconBtn(R.drawable.ic_format_strike) {
                webView?.evaluateJavascript("editor.chain().focus().toggleStrike().run()", null)
            }
            Divider(modifier = Modifier.height(24.dp).width(1.dp))
            FormatIconBtn(R.drawable.ic_format_h1) {
                webView?.evaluateJavascript("editor.chain().focus().toggleHeading({level:1}).run()", null)
            }
            FormatIconBtn(R.drawable.ic_format_h2) {
                webView?.evaluateJavascript("editor.chain().focus().toggleHeading({level:2}).run()", null)
            }
            Divider(modifier = Modifier.height(24.dp).width(1.dp))
            FormatIconBtn(R.drawable.ic_format_list_ul) {
                webView?.evaluateJavascript("editor.chain().focus().toggleBulletList().run()", null)
            }
            FormatIconBtn(R.drawable.ic_format_list_ol) {
                webView?.evaluateJavascript("editor.chain().focus().toggleOrderedList().run()", null)
            }
            FormatIconBtn(R.drawable.ic_format_quote) {
                webView?.evaluateJavascript("editor.chain().focus().toggleBlockquote().run()", null)
            }
            Divider(modifier = Modifier.height(24.dp).width(1.dp))
            FormatIconBtn(R.drawable.ic_fonts) { onTextColor() }
            FormatIconBtn(R.drawable.ic_format_highlight) { onHighlight() }
            FormatIconBtn(R.drawable.ic_format_underline_color) { onSolidUnderline() }
            FormatIconBtn(R.drawable.ic_format_wavy) { onWavyUnderline() }
        }
    }
}

@Composable
fun FormatIconBtn(iconRes: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ToolIconBtn(icon: androidx.compose.ui.graphics.painter.Painter, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 文字颜色选择 BottomSheet —— 全屏宽度网格，适配手机操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextColorPickerSheet(
    mode: PickerMode,
    onColorSelected: (String) -> Unit,
    onClearColor: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        "--blueColor" to Color(0xFF1A73E8),
        "--redColor" to Color(0xFFEA4335),
        "--greenColor" to Color(0xFF34A853),
        "--orangeColor" to Color(0xFFFB9600),
        "--yellowColor" to Color(0xFFF9AB00),
        "--grayColor" to Color(0xFF5F6368)
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                when (mode) {
                    PickerMode.TEXT_COLOR -> "Text Color"
                    PickerMode.HIGHLIGHT -> "Highlight Color"
                    PickerMode.SOLID_UNDERLINE -> "Underline Color"
                    PickerMode.WAVY_UNDERLINE -> "Wavy Underline Color"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                colors.forEach { (name, color) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onColorSelected(name) }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            name.removePrefix("--").removeSuffix("Color"),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClearColor, modifier = Modifier.fillMaxWidth()) {
                Text("No Color")
            }
        }
    }
}

@Composable
fun NotebookDropdown(notebooks: List<Folder>, selectedNotebookId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = notebooks.find { it.serverId == selectedNotebookId }?.name ?: "Notebook"
    Box {
        TextButton(onClick = { expanded = true }) { Text(label, style = MaterialTheme.typography.bodySmall) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("No Notebook") }, onClick = { onSelect(null); expanded = false })
            notebooks.forEach { nb ->
                DropdownMenuItem(text = { Text(nb.name) }, onClick = { onSelect(nb.serverId); expanded = false }) }
        }
    }
}

@Composable
fun ColorDots(selectedColor: String, onColorSelected: (String) -> Unit) {
    val colors = mapOf(
        "blue" to Color(0xFF4A90D9), "green" to Color(0xFF7ED321),
        "yellow" to Color(0xFFF5A623), "orange" to Color(0xFFF0984C),
        "red" to Color(0xFFD0021B), "gray" to Color(0xFF9B9B9B))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.entries.take(6).forEach { (key, color) ->
            val isSelected = key == selectedColor
            Box(Modifier.size(if (isSelected) 24.dp else 20.dp).clip(CircleShape)
                .background(color).clickable { onColorSelected(key) }) }
    }
}

fun doShareAsImage(webView: WebView, skin: com.open.note.data.skin.Skin, ctx: Context, noteTitle: String) {
    MainScope().launch {
        val bitmap = com.open.note.share.ContentCaptureEngine.captureFullWebView(webView) ?: return@launch
        val cacheFile = java.io.File(ctx.cacheDir, "share_${System.currentTimeMillis()}.png")
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, java.io.FileOutputStream(cacheFile))
        val colorsJson = org.json.JSONObject().apply {
            put("contentBg", skin.contentBackground.color)
            put("backcloth", skin.backCloth)
            put("textColor", skin.textColor)
            put("titleColor", skin.titleColor)
            put("timeColor", skin.timeColor)
        }.toString()
        val intent = android.content.Intent(ctx, com.open.note.share.preview.SharePreviewActivity::class.java).apply {
            putStringArrayListExtra("capture_paths", arrayListOf(cacheFile.absolutePath))
            putExtra("colors_json", colorsJson)
            putExtra("note_title", noteTitle)
            putExtra("logo_text", com.open.note.share.ShareSettings.getLogoText(ctx))
            putExtra("watermark", com.open.note.share.ShareSettings.getWatermark(ctx))
        }
        ctx.startActivity(intent)
    }
}

/** 颜色选择面板模式 */
enum class PickerMode {
    TEXT_COLOR,        // 文字颜色
    HIGHLIGHT,         // 高亮背景
    SOLID_UNDERLINE,   // 有色实线下划线
    WAVY_UNDERLINE     // 有色波浪线
}

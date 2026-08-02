package com.open.note.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.open.note.R
import com.open.note.data.local.entity.Folder
import com.open.note.data.skin.SkinColors
import com.open.note.data.skin.SkinWebView
import com.open.note.ui.skin.SkinViewModel
import dagger.hilt.android.AndroidEntryPoint
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        setContent { MaterialTheme { NoteEditorScreen(noteId = noteId, onBack = { finish() }) } }
    }
}

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

    val editorBridge = remember { EditorBridge() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) { editorViewModel.initialize(noteId) }
    LaunchedEffect(skin) { webView?.evaluateJavascript(SkinWebView.generateSkinCSS(skin), null) }

    editorBridge.onContentChanged = { markdown, length ->
        editorViewModel.onContentChanged(markdown, length)
    }
    editorBridge.onEditorReady = {
        if (noteId != null) {
            val data = JSONObject().apply { put("content", content) }.toString()
            webView?.evaluateJavascript("window.__setContent($data)", null)
        }
        webView?.evaluateJavascript(SkinWebView.generateSkinCSS(skin), null)
        editorViewModel.onContentLoadingComplete()
    }

    Scaffold(
        containerColor = skinBg,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { editorViewModel.saveNow(); onBack() }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    var showMore by remember { mutableStateOf(false) }

                    NotebookDropdown(notebooks, selectedNotebookId) { editorViewModel.onNotebookSelected(it) }
                    IconButton(onClick = { editorViewModel.saveNow() }) {
                        Icon(painterResource(R.drawable.ic_check2), contentDescription = "Save",
                            tint = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        webView?.context?.let { ctx -> doShareAsImage(webView!!, skin, ctx, title) }
                    }) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(painterResource(R.drawable.ic_more_vertical), contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
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
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$contentLength/10000", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isDirty) Text("Unsaved", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = title, onValueChange = { editorViewModel.onTitleChanged(it) },
                placeholder = { Text("Title", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp),
                singleLine = true, modifier = Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)))
            Spacer(Modifier.height(4.dp))
            EditorToolbarRow(webView = webView)
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.allowFileAccess = true
                        addJavascriptInterface(editorBridge, "__nativeBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                editorBridge.onEditorReady?.invoke()
                            }
                        }
                        loadUrl("file:///android_asset/editor.html")
                    }.also { webView = it }
                },
                modifier = Modifier.weight(1f).fillMaxWidth())
        }
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
fun EditorToolbarRow(webView: WebView?) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        ToolBtn("B") { webView?.evaluateJavascript("editor.chain().focus().toggleBold().run()", null) }
        ToolBtn("I") { webView?.evaluateJavascript("editor.chain().focus().toggleItalic().run()", null) }
        ToolBtn("U") { webView?.evaluateJavascript("editor.chain().focus().toggleUnderline().run()", null) }
        ToolBtn("S") { webView?.evaluateJavascript("editor.chain().focus().toggleStrike().run()", null) }
        ToolBtn("H1") { webView?.evaluateJavascript("editor.chain().focus().toggleHeading({level:1}).run()", null) }
        ToolBtn("H2") { webView?.evaluateJavascript("editor.chain().focus().toggleHeading({level:2}).run()", null) }
        ToolBtn("\u2022") { webView?.evaluateJavascript("editor.chain().focus().toggleBulletList().run()", null) }
        ToolBtn("1.") { webView?.evaluateJavascript("editor.chain().focus().toggleOrderedList().run()", null) }
        ToolBtn("\"") { webView?.evaluateJavascript("editor.chain().focus().toggleBlockquote().run()", null) }
    }
}

@Composable
fun ToolBtn(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall) }
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
        }
        ctx.startActivity(intent)
    }
}

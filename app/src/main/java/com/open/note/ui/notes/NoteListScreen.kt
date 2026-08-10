package com.open.note.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import com.open.note.data.skin.SkinColors
import com.open.note.ui.skin.SkinViewModel
import java.text.SimpleDateFormat
import java.util.*

val NOTE_COLORS = mapOf(
    "blue" to Color(0xFF4A90D9),
    "green" to Color(0xFF7ED321),
    "yellow" to Color(0xFFF5A623),
    "orange" to Color(0xFFF0984C),
    "red" to Color(0xFFD0021B),
    "gray" to Color(0xFF9B9B9B)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    onNoteClick: (String?) -> Unit,
    onNewNote: () -> Unit,
    viewModel: NoteListViewModel = hiltViewModel(),
    skinViewModel: SkinViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchKeyword by viewModel.searchKeyword.collectAsState()
    val selectedNotebookId by viewModel.selectedNotebookId.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val skin by skinViewModel.selectedSkin.collectAsState()
    val titleColor = SkinColors.parseColor(skin.titleColor)
    val textColor = SkinColors.parseColor(skin.textColor)
    val timeColor = SkinColors.parseColor(skin.timeColor)

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            keyword = searchKeyword,
            onKeywordChange = { viewModel.onSearchKeywordChanged(it) }
        )

        FilterRow(
            notebooks = notebooks,
            selectedNotebookId = selectedNotebookId,
            selectedColor = selectedColor,
            onNotebookSelected = { viewModel.onNotebookSelected(it) },
            onColorSelected = { viewModel.onColorSelected(it) }
        )

        if (notes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.NoteAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No notes yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(notes, key = { it.localId }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { onNoteClick(note.serverId) },
                        onDelete = { viewModel.deleteNote(note.serverId ?: return@NoteCard) },
                        onTogglePin = { viewModel.togglePin(note.serverId ?: return@NoteCard) },
                        titleColor = titleColor,
                        textColor = textColor,
                        timeColor = timeColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        placeholder = { Text("Search notes...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = { onKeywordChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun FilterRow(
    notebooks: List<Folder>,
    selectedNotebookId: String?,
    selectedColor: String?,
    onNotebookSelected: (String?) -> Unit,
    onColorSelected: (String?) -> Unit
) {
    var showNotebookDropdown by remember { mutableStateOf(false) }
    val selectedNotebookName = if (selectedNotebookId == null) "All Notes"
    else notebooks.find { it.serverId == selectedNotebookId }?.name ?: "All Notes"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            FilterChip(
                selected = selectedNotebookId != null,
                onClick = { showNotebookDropdown = true },
                label = { Text(selectedNotebookName, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = showNotebookDropdown, onDismissRequest = { showNotebookDropdown = false }) {
                DropdownMenuItem(text = { Text("All Notes") }, onClick = { onNotebookSelected(null); showNotebookDropdown = false })
                notebooks.forEach { nb ->
                    DropdownMenuItem(text = { Text(nb.name) }, onClick = { onNotebookSelected(nb.serverId); showNotebookDropdown = false })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NOTE_COLORS.keys.take(6).forEach { colorKey ->
                val color = NOTE_COLORS[colorKey] ?: Color.Gray
                val isSelected = selectedColor == colorKey
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 28.dp else 24.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    titleColor: Color = Color(0xFF1A1A1A),
    textColor: Color = Color(0xFF666666),
    timeColor: Color = Color(0xFF999999)
) {
    var showMenu by remember { mutableStateOf(false) }
    val color = NOTE_COLORS[note.color] ?: NOTE_COLORS["blue"]!!
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val timeText = remember(note.updatedAt) { dateFormat.format(Date(note.updatedAt)) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.width(4.dp).fillMaxHeight().defaultMinSize(minHeight = 72.dp).background(color)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.title.ifEmpty { "Untitled" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.isPinned) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(16.dp), tint = titleColor)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(16.dp), tint = timeColor)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                onClick = { showMenu = false; onTogglePin() }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }

                if (note.content.isNotEmpty()) {
                    val preview = remember(note.content, textColor) {
                        val spannable = com.open.note.render.HtmlToSpannableParser
                            .parse(note.content.take(500))
                        com.open.note.render.SpannedToAnnotated.convert(spannable)
                    }
                    Text(
                        text = preview,
                        fontSize = 14.sp,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = timeText,
                    fontSize = 14.sp,
                    color = timeColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

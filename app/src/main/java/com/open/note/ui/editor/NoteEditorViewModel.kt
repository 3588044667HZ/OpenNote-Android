package com.open.note.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import com.open.note.data.repository.ConflictException
import com.open.note.data.repository.NoteRepository
import com.open.note.data.repository.NotFoundException
import com.open.note.share.AttachmentUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 编辑器保存冲突状态 */
data class EditorConflict(
    val noteId: String,
    val message: String,
    val remoteUpdatedAt: Long? = null
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val attachmentUploader: AttachmentUploader
) : ViewModel() {

    private var noteId: String? = null
    private var localNoteId: Long? = null
    private var initialized = false

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _contentLength = MutableStateFlow(0)
    val contentLength: StateFlow<Int> = _contentLength

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _selectedColor = MutableStateFlow("blue")
    val selectedColor: StateFlow<String> = _selectedColor

    private val _selectedNotebookId = MutableStateFlow<String?>(null)
    val selectedNotebookId: StateFlow<String?> = _selectedNotebookId

    private val _isPinned = MutableStateFlow(false)
    val isPinned: StateFlow<Boolean> = _isPinned

    private val _lastUpdatedAt = MutableStateFlow<String?>(null)
    val lastUpdatedAt: StateFlow<String?> = _lastUpdatedAt

    private val _conflict = MutableStateFlow<EditorConflict?>(null)
    val conflict: StateFlow<EditorConflict?> = _conflict

    /** 重新检查发现笔记已在服务端永久删除 */
    private val _conflictNoteMissing = MutableStateFlow(false)
    val conflictNoteMissing: StateFlow<Boolean> = _conflictNoteMissing

    val notebooks: StateFlow<List<Folder>> = noteRepository.getActiveFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var saveJob: Job? = null
    private var contentLoaded = false

    fun initialize(noteId: String?, localNoteId: Long? = null) {
        if (initialized) return
        initialized = true
        this.noteId = noteId
        this.localNoteId = localNoteId

        viewModelScope.launch {
            // 优先按 localId（本地未上传笔记），否则按 serverId
            val existing = if (localNoteId != null) {
                noteRepository.getNoteById(localNoteId)
            } else {
                noteId?.let { noteRepository.getNoteByServerId(it) }
            }
            if (existing != null) {
                _note.value = existing
                _title.value = existing.title
                _content.value = existing.content
                _contentLength.value = existing.content.length
                _selectedColor.value = existing.color
                _selectedNotebookId.value = existing.notebookId
                _isPinned.value = existing.isPinned
                _lastUpdatedAt.value = existing.updatedAt.toString()
                this@NoteEditorViewModel.noteId = existing.serverId
                this@NoteEditorViewModel.localNoteId = existing.localId
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        _title.value = newTitle
        markDirty()
    }

    fun onContentChanged(title: String, markdown: String, length: Int) {
        if (title.isNotEmpty() && title != _title.value) {
            _title.value = title
        }
        _content.value = markdown
        _contentLength.value = length
        if (contentLoaded) {
            markDirty()
            scheduleAutoSave()
        }
    }

    fun onContentLoadingComplete() {
        viewModelScope.launch {
            delay(500)
            contentLoaded = true
        }
    }

    fun onColorSelected(color: String) {
        _selectedColor.value = color
        markDirty()
        scheduleAutoSave()
    }

    fun onNotebookSelected(notebookId: String?) {
        _selectedNotebookId.value = notebookId
        markDirty()
        scheduleAutoSave()
    }

    fun togglePin() {
        _isPinned.value = !_isPinned.value
        markDirty()
        saveNow()
    }

    private fun markDirty() {
        _isDirty.value = true
    }

    private fun scheduleAutoSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(2000)
            saveNote()
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        viewModelScope.launch {
            saveNote()
        }
    }

    private suspend fun saveNote() {
        _isSaving.value = true
        try {
            // 先上传图片附件，并把待保存 content 中的占位 URL 替换为服务端地址，
            // 保证服务器端从第一次保存起就能正常显示（否则网页端拿到死链）
            val noteKey = noteId ?: (localNoteId?.toString() ?: "draft")
            attachmentUploader.syncAll(noteKey)
            val contentToSave = attachmentUploader.resolvePlaceholders(noteKey, _content.value)

            val currentNote = _note.value
            if (currentNote != null) {
                currentNote.title = _title.value
                currentNote.content = contentToSave
                currentNote.color = _selectedColor.value
                currentNote.notebookId = _selectedNotebookId.value
                currentNote.isPinned = _isPinned.value

                val result = noteRepository.updateNote(currentNote)
                result.onSuccess { updated ->
                    _note.value = updated
                    _isDirty.value = false
                    _lastUpdatedAt.value = updated.updatedAt.toString()
                }
                result.onFailure { ex ->
                    if (ex is ConflictException) {
                        // 409：弹冲突对话框（保留本地/采用远程/重新检查）
                        _conflict.value = EditorConflict(
                            noteId = currentNote.serverId ?: "",
                            message = ex.message ?: "Conflict"
                        )
                    }
                }
            } else {
                val result = noteRepository.createNote(
                    title = _title.value,
                    content = contentToSave,
                    notebookId = _selectedNotebookId.value,
                    color = _selectedColor.value,
                    isPinned = _isPinned.value
                )
                result.onSuccess { created ->
                    _note.value = created
                    _isDirty.value = false
                    noteId = created.serverId
                }
            }
        } finally {
            _isSaving.value = false
        }
    }

    // ============ 冲突解决动作 ============

    fun dismissConflict() {
        _conflict.value = null
    }

    fun dismissMissing() {
        _conflictNoteMissing.value = false
    }

    /** 保留本地：不带 If-Match 强制覆盖远程 */
    fun keepLocal() {
        val note = _note.value ?: return
        _conflict.value = null
        viewModelScope.launch {
            noteRepository.forceUpdateNote(note)
                .onSuccess { updated ->
                    _note.value = updated
                    _isDirty.value = false
                }
                .onFailure { ex ->
                    _conflict.value = EditorConflict(note.serverId ?: "", "覆盖失败: ${ex.message}")
                }
        }
    }

    /** 采用远程：拉取最新并覆盖本地（未保存内容丢弃） */
    fun useRemote() {
        val note = _note.value ?: return
        viewModelScope.launch {
            noteRepository.getNoteFromServer(note.serverId ?: "")
                .onSuccess { remote ->
                    _note.value = remote
                    _title.value = remote.title
                    _content.value = remote.content
                    _contentLength.value = remote.content.length
                    _selectedColor.value = remote.color
                    _selectedNotebookId.value = remote.notebookId
                    _isPinned.value = remote.isPinned
                    _isDirty.value = false
                    _conflict.value = null
                }
                .onFailure { ex ->
                    _conflict.value = EditorConflict(note.serverId ?: "", "拉取失败: ${ex.message}")
                }
        }
    }

    /** 重新检查：只查当前笔记。404 → 切换"已永久删除"弹窗；内容同 → 自动采用；异 → 保持弹窗 */
    fun recheckConflict() {
        val note = _note.value ?: return
        viewModelScope.launch {
            noteRepository.getNoteFromServer(note.serverId ?: "")
                .onSuccess { remote ->
                    if (remote.title == _title.value && remote.content == _content.value) {
                        _note.value = remote
                        _isDirty.value = false
                        _conflict.value = null
                    } else {
                        _conflict.value = EditorConflict(
                            note.serverId ?: "",
                            "此笔记在其他设备被修改",
                            remote.updatedAt
                        )
                    }
                }
                .onFailure { ex ->
                    if (ex is NotFoundException) {
                        _conflict.value = null
                        _conflictNoteMissing.value = true
                    } else {
                        _conflict.value = EditorConflict(note.serverId ?: "", "检查失败: ${ex.message}")
                    }
                }
        }
    }

    /** 保留重传：服务端已永久删除 → 本地内容重建为新笔记 */
    fun keepLocalAsNew() {
        _conflictNoteMissing.value = false
        viewModelScope.launch {
            val result = noteRepository.createNote(
                title = _title.value,
                content = _content.value,
                notebookId = _selectedNotebookId.value,
                color = _selectedColor.value,
                isPinned = _isPinned.value
            )
            result.onSuccess { created ->
                _note.value = created
                noteId = created.serverId
                _isDirty.value = false
            }
        }
    }

    /** 从本地删除：跟随服务端删除 */
    fun discardLocal() {
        val note = _note.value ?: return
        _conflictNoteMissing.value = false
        viewModelScope.launch {
            noteRepository.deleteNote(note.localId, note.serverId)
        }
    }

    fun deleteNote() {
        viewModelScope.launch {
            val note = _note.value ?: return@launch
            noteRepository.deleteNote(note.localId, note.serverId)
        }
    }
}

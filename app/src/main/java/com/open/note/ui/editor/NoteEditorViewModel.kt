package com.open.note.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import com.open.note.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private var noteId: String? = null
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

    val notebooks: StateFlow<List<Folder>> = noteRepository.getActiveFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var saveJob: Job? = null
    private var contentLoaded = false

    fun initialize(noteId: String?) {
        if (initialized) return
        initialized = true
        this.noteId = noteId

        if (noteId != null) {
            viewModelScope.launch {
                val existing = noteRepository.getNoteByServerId(noteId)
                if (existing != null) {
                    _note.value = existing
                    _title.value = existing.title
                    _content.value = existing.content
                    _contentLength.value = existing.content.length
                    _selectedColor.value = existing.color
                    _selectedNotebookId.value = existing.notebookId
                    _isPinned.value = existing.isPinned
                    _lastUpdatedAt.value = existing.updatedAt.toString()
                }
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
            val currentNote = _note.value
            if (currentNote != null) {
                currentNote.title = _title.value
                currentNote.content = _content.value
                currentNote.color = _selectedColor.value
                currentNote.notebookId = _selectedNotebookId.value
                currentNote.isPinned = _isPinned.value

                val result = noteRepository.updateNote(currentNote)
                result.onSuccess { updated ->
                    _note.value = updated
                    _isDirty.value = false
                    _lastUpdatedAt.value = updated.updatedAt.toString()
                }
            } else {
                val result = noteRepository.createNote(
                    title = _title.value,
                    content = _content.value,
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

    fun deleteNote() {
        viewModelScope.launch {
            val serverId = _note.value?.serverId ?: return@launch
            noteRepository.deleteNote(serverId)
        }
    }
}

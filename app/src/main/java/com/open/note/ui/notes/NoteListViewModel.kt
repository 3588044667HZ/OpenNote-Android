package com.open.note.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import com.open.note.data.repository.NoteRepository
import com.open.note.data.repository.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _sortBy = MutableStateFlow("updatedAt")
    val sortBy: StateFlow<String> = _sortBy

    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword

    private val _selectedNotebookId = MutableStateFlow<String?>(null)
    val selectedNotebookId: StateFlow<String?> = _selectedNotebookId

    private val _selectedColor = MutableStateFlow<String?>(null)
    val selectedColor: StateFlow<String?> = _selectedColor

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val notes: StateFlow<List<Note>> = combine(
        noteRepository.getActiveNotes(),
        _searchKeyword,
        _selectedNotebookId,
        _selectedColor
    ) { notes, keyword, notebookId, color ->
        var result = notes
        if (keyword.isNotBlank()) {
            val kw = keyword.lowercase()
            result = result.filter {
                it.title.lowercase().contains(kw) || it.content.lowercase().contains(kw)
            }
        }
        if (notebookId != null) {
            result = result.filter { it.notebookId == notebookId }
        }
        if (color != null) {
            result = result.filter { it.color == color }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notebooks: StateFlow<List<Folder>> = noteRepository.getActiveFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            noteRepository.fetchNotesFromServer()
            noteRepository.fetchNotebooksFromServer()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncManager.syncPendingNotes()
                syncManager.incrementalSync()
                noteRepository.fetchNotesFromServer()
                noteRepository.fetchNotebooksFromServer()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onSearchKeywordChanged(keyword: String) {
        _searchKeyword.value = keyword
    }

    fun onNotebookSelected(notebookId: String?) {
        _selectedNotebookId.value = notebookId
    }

    fun onColorSelected(color: String?) {
        _selectedColor.value = color
    }

    fun onSortByChanged(sortBy: String) {
        _sortBy.value = sortBy
    }

    fun deleteNote(serverId: String) {
        viewModelScope.launch {
            noteRepository.deleteNote(serverId)
        }
    }

    fun togglePin(serverId: String) {
        viewModelScope.launch {
            noteRepository.togglePin(serverId)
        }
    }
}

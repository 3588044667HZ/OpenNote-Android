package com.open.note.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.AuthStore
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
    private val syncManager: SyncManager,
    private val authStore: AuthStore
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

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

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
            if (!isLoggedIn()) return@launch
            noteRepository.fetchNotesFromServer()
            noteRepository.fetchNotebooksFromServer()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (!isLoggedIn()) {
                    _syncMessage.value = "未登录，无法同步。请到设置页登录"
                    return@launch
                }
                syncManager.doSync()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    private suspend fun isLoggedIn(): Boolean =
        !authStore.getAccessTokenBlocking().isNullOrBlank()

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

    fun deleteNote(localId: Long, serverId: String?) {
        viewModelScope.launch {
            noteRepository.deleteNote(localId, serverId)
        }
    }

    fun togglePin(serverId: String?) {
        viewModelScope.launch {
            serverId?.let { noteRepository.togglePin(it) }
        }
    }
}

package com.open.note.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.AuthStore
import com.open.note.data.local.entity.Folder
import com.open.note.data.local.entity.Note
import com.open.note.data.repository.NoteRepository
import com.open.note.data.repository.NotFoundException
import com.open.note.data.repository.SyncConflict
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

    /** 待用户解决的同步冲突列表（下拉刷新后逐个弹窗） */
    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts: StateFlow<List<SyncConflict>> = _conflicts

    /** 重新检查发现笔记已在服务端永久删除 */
    private val _conflictNoteMissing = MutableStateFlow(false)
    val conflictNoteMissing: StateFlow<Boolean> = _conflictNoteMissing

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
                val result = syncManager.doSync()
                result.onSuccess { sync ->
                    _conflicts.value = sync.conflicts
                }
                result.onFailure { ex ->
                    _syncMessage.value = "同步失败: ${ex.message}"
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    // ============ 冲突解决动作 ============

    fun dismissConflict(conflict: SyncConflict) {
        _conflicts.value = _conflicts.value.filter { it !== conflict }
    }

    fun dismissMissing() {
        _conflictNoteMissing.value = false
    }

    /** 保留本地：不带 If-Match 强制覆盖远程 */
    fun keepLocal(conflict: SyncConflict) {
        viewModelScope.launch {
            noteRepository.forceUpdateNote(conflict.local).onSuccess {
                dismissConflict(conflict)
            }.onFailure { ex ->
                _syncMessage.value = "覆盖失败: ${ex.message}"
            }
        }
    }

    /** 采用远程：远程版本覆盖本地 */
    fun useRemote(conflict: SyncConflict) {
        viewModelScope.launch {
            val remote = conflict.remote
            noteRepository.upsertNote(remote.copy(localId = conflict.local.localId))
            dismissConflict(conflict)
        }
    }

    /** 重新检查：GET 单条。404 → 切换"已删除"；内容同 → 自动采用；异 → 保持弹窗 */
    fun recheckConflict(conflict: SyncConflict) {
        viewModelScope.launch {
            val serverId = conflict.local.serverId ?: return@launch
            noteRepository.getNoteFromServer(serverId)
                .onSuccess { remote ->
                    val local = conflict.local
                    if (remote.title == local.title && remote.content == local.content) {
                        noteRepository.upsertNote(remote.copy(localId = local.localId))
                        dismissConflict(conflict)
                    } else {
                        // 冲突仍存在 → 用最新远程替换弹窗数据
                        val updated = SyncConflict(local = local, remote = remote)
                        _conflicts.value = _conflicts.value.map {
                            if (it === conflict) updated else it
                        }
                    }
                }
                .onFailure { ex ->
                    if (ex is NotFoundException) {
                        dismissConflict(conflict)
                        _conflictNoteMissing.value = true
                    } else {
                        _syncMessage.value = "检查失败: ${ex.message}"
                    }
                }
        }
    }

    /** 保留重传：服务端已永久删除 → 本地内容重建为新笔记 */
    fun keepLocalAsNew() {
        _conflictNoteMissing.value = false
        viewModelScope.launch {
            noteRepository.createNote(
                title = _conflicts.value.firstOrNull()?.local?.title ?: "",
                content = _conflicts.value.firstOrNull()?.local?.content ?: "",
                notebookId = _conflicts.value.firstOrNull()?.local?.notebookId,
                color = _conflicts.value.firstOrNull()?.local?.color ?: "blue",
                isPinned = _conflicts.value.firstOrNull()?.local?.isPinned ?: false
            )
        }
    }

    /** 从本地删除：跟随服务端删除 */
    fun discardLocal() {
        val conflict = _conflicts.value.firstOrNull() ?: return
        _conflictNoteMissing.value = false
        viewModelScope.launch {
            noteRepository.deleteNote(conflict.local.localId, conflict.local.serverId)
            dismissConflict(conflict)
        }
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

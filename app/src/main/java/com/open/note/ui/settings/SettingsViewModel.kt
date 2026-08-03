package com.open.note.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.AuthStore
import com.open.note.data.local.entity.Folder
import com.open.note.data.repository.AuthRepository
import com.open.note.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val authRepository: AuthRepository,
    private val authStore: AuthStore
) : ViewModel() {

    val notebooks: StateFlow<List<Folder>> = noteRepository.getActiveFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverUrl: StateFlow<String> = authStore.serverUrl.map { url ->
        url ?: "http://10.0.2.2:5000/api/"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "http://10.0.2.2:5000/api/")

    val username: StateFlow<String> = authStore.username.map { it ?: "Open Note User" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Open Note User")

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent

    fun createNotebook(name: String) {
        viewModelScope.launch {
            noteRepository.createNotebook(name)
        }
    }

    fun deleteNotebook(serverId: String) {
        viewModelScope.launch {
            noteRepository.deleteNotebook(serverId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _logoutEvent.emit(Unit)
        }
    }
}

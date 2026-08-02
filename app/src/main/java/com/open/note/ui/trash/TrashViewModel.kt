package com.open.note.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.entity.Note
import com.open.note.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    val trashNotes: StateFlow<List<Note>> = noteRepository.getTrashNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recoverNote(serverId: String) {
        viewModelScope.launch {
            noteRepository.recoverNote(serverId)
        }
    }

    fun permanentlyDeleteNote(serverId: String) {
        viewModelScope.launch {
            noteRepository.permanentlyDeleteNote(serverId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            noteRepository.emptyTrash()
        }
    }
}

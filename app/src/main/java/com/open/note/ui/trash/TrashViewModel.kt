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

    fun recoverNote(localId: Long, serverId: String?) {
        viewModelScope.launch {
            if (serverId == null) {
                noteRepository.recoverLocalNote(localId)
            } else {
                noteRepository.recoverNote(serverId)
            }
        }
    }

    fun permanentlyDeleteNote(localId: Long, serverId: String?) {
        viewModelScope.launch {
            if (serverId == null) {
                noteRepository.permanentlyDeleteNoteByLocalId(localId)
            } else {
                noteRepository.permanentlyDeleteNote(serverId)
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            noteRepository.emptyTrash()
        }
    }
}

package com.open.note.data.repository

import android.util.Log
import com.open.note.data.local.AuthStore
import com.open.note.data.remote.api.NoteApi
import com.open.note.data.remote.dto.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val noteApi: NoteApi,
    private val authStore: AuthStore,
    private val noteRepository: NoteRepository
) {

    companion object {
        private const val TAG = "SyncManager"
    }

    suspend fun incrementalSync(): Result<Unit> {
        return try {
            val lastSync = authStore.getLastSyncTimeBlocking()
            Log.d(TAG, "Starting incremental sync since: $lastSync")

            val response = noteApi.syncNotes(since = lastSync)
            if (response.isSuccessful && response.body()?.data != null) {
                val syncData = response.body()!!.data!!

                syncData.updated.forEach { dto ->
                    noteRepository.upsertNote(dto.toEntity())
                }
                Log.d(TAG, "Merged ${syncData.updated.size} updated notes")

                syncData.deletedIds.forEach { id ->
                    try {
                        noteRepository.permanentlyDeleteNote(id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to remove deleted note: $id", e)
                    }
                }
                Log.d(TAG, "Removed ${syncData.deletedIds.size} deleted notes")

                syncData.serverTime?.let { authStore.setLastSyncTime(it) }
                Log.d(TAG, "Incremental sync complete, server time: ${syncData.serverTime}")

                Result.success(Unit)
            } else {
                val msg = response.body()?.msg ?: "Sync failed"
                Log.e(TAG, msg)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Incremental sync error", e)
            Result.failure(e)
        }
    }

    suspend fun syncPendingNotes(): Result<Unit> {
        return try {
            val pendingNotes = noteRepository.getPendingSyncNotes()
            if (pendingNotes.isEmpty()) {
                Log.d(TAG, "No pending notes to sync")
                return Result.success(Unit)
            }

            Log.d(TAG, "Syncing ${pendingNotes.size} pending notes")
            pendingNotes.forEach { note ->
                try {
                    if (note.serverId != null) {
                        val result = noteRepository.updateNote(note)
                        if (result.isSuccess) {
                            noteRepository.markPendingSync(note.localId, false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync note: ${note.localId}", e)
                }
            }

            Log.d(TAG, "Pending notes sync complete")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync pending notes error", e)
            Result.failure(e)
        }
    }
}

package com.open.note.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.open.note.data.local.AuthStore
import com.open.note.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authStore: AuthStore
) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val serverUrl: StateFlow<String> = authStore.serverUrl.map { url ->
        url ?: "http://10.0.2.2:5000/api/"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "http://10.0.2.2:5000/api/")

    private val _navigateToMain = MutableSharedFlow<Unit>()
    val navigateToMain: SharedFlow<Unit> = _navigateToMain

    private val _isRegisterMode = MutableStateFlow(false)
    val isRegisterMode: StateFlow<Boolean> = _isRegisterMode

    private val _savedUsername = MutableStateFlow("")
    val savedUsername: StateFlow<String> = _savedUsername

    private val _savedPassword = MutableStateFlow("")
    val savedPassword: StateFlow<String> = _savedPassword

    init {
        viewModelScope.launch {
            _savedUsername.value = authStore.getSavedUsername() ?: ""
            _savedPassword.value = authStore.getSavedPassword() ?: ""
        }
    }

    fun toggleMode() {
        _isRegisterMode.value = !_isRegisterMode.value
        _error.value = null
    }

    fun submit(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _error.value = "Username and password are required"
            return
        }

        _isLoading.value = true
        _error.value = null
        Log.d(TAG, "Submitting login, server=${serverUrl.value}")

        viewModelScope.launch {
            try {
                if (_isRegisterMode.value) {
                    val result = authRepository.register(username, password)
                    if (result.isSuccess) {
                        val loginResult = authRepository.login(username, password)
                        if (loginResult.isSuccess) {
                            _navigateToMain.emit(Unit)
                        } else {
                            _error.value = loginResult.exceptionOrNull()?.message ?: "Login after registration failed"
                        }
                    } else {
                        _error.value = result.exceptionOrNull()?.message ?: "Registration failed"
                    }
                } else {
                    val result = authRepository.login(username, password)
                    if (result.isSuccess) {
                        _navigateToMain.emit(Unit)
                    } else {
                        _error.value = result.exceptionOrNull()?.message ?: "Login failed"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Submit error", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}

package com.redline.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.github.DeviceAuth
import com.redline.viewer.data.github.DeviceCode
import com.redline.viewer.data.github.PollResult
import com.redline.viewer.data.github.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Requesting : AuthState()
    data class Verifying(val code: DeviceCode) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = TokenStore(app)
    private val clientId: String = BuildConfig.GITHUB_CLIENT_ID
    private val auth: DeviceAuth? = if (clientId.isNotBlank()) DeviceAuth(clientId) else null

    private val _token = MutableStateFlow(tokenStore.accessToken)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingComment>>(emptyList())
    val pending: StateFlow<List<PendingComment>> = _pending.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val hasClientId: Boolean get() = auth != null

    private var authJob: Job? = null

    fun startDeviceFlow() {
        val a = auth ?: run {
            _authState.value = AuthState.Error("GITHUB_CLIENT_ID is not set. See README.")
            return
        }
        authJob?.cancel()
        authJob = viewModelScope.launch {
            _authState.value = AuthState.Requesting
            try {
                val code = a.requestCode()
                _authState.value = AuthState.Verifying(code)
                when (val result = a.poll(code)) {
                    is PollResult.Success -> {
                        tokenStore.accessToken = result.accessToken
                        _token.value = result.accessToken
                        _authState.value = AuthState.Idle
                    }
                    is PollResult.Error -> {
                        _authState.value = AuthState.Error(result.message.ifBlank { result.code })
                    }
                }
            } catch (t: Throwable) {
                _authState.value = AuthState.Error(t.message ?: "auth failed")
            }
        }
    }

    fun cancelDeviceFlow() {
        authJob?.cancel()
        _authState.value = AuthState.Idle
    }

    fun signOut() {
        tokenStore.clear()
        _token.value = null
    }

    fun addPending(comment: PendingComment) {
        _pending.value = _pending.value + comment
    }

    fun clearPending() {
        _pending.value = emptyList()
    }

    fun flash(msg: String) {
        _toast.value = msg
    }

    fun consumeToast() {
        _toast.value = null
    }

    override fun onCleared() {
        super.onCleared()
        auth?.close()
    }
}

package com.redline.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.github.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = TokenStore(app)

    private val _token = MutableStateFlow(tokenStore.accessToken)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingComment>>(emptyList())
    val pending: StateFlow<List<PendingComment>> = _pending.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun setToken(value: String) {
        tokenStore.accessToken = value
        _token.value = value
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
}

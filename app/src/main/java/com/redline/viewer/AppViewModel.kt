package com.redline.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.github.DeviceAuth
import com.redline.viewer.data.github.DeviceCode
import com.redline.viewer.data.github.GhPull
import com.redline.viewer.data.github.GhRepo
import com.redline.viewer.data.github.GhUser
import com.redline.viewer.data.github.GitHubApi
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

sealed class Loadable<out T> {
    object Idle : Loadable<Nothing>()
    object Loading : Loadable<Nothing>()
    data class Ok<T>(val value: T) : Loadable<T>()
    data class Err(val message: String) : Loadable<Nothing>()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = TokenStore(app)
    private val clientId: String = BuildConfig.GITHUB_CLIENT_ID
    private val auth: DeviceAuth? = if (clientId.isNotBlank()) DeviceAuth(clientId) else null

    private val _token = MutableStateFlow(tokenStore.accessToken)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _viewer = MutableStateFlow<Loadable<GhUser>>(Loadable.Idle)
    val viewer: StateFlow<Loadable<GhUser>> = _viewer.asStateFlow()

    private val _repos = MutableStateFlow<Loadable<List<GhRepo>>>(Loadable.Idle)
    val repos: StateFlow<Loadable<List<GhRepo>>> = _repos.asStateFlow()

    private val _pulls = MutableStateFlow<Loadable<List<GhPull>>>(Loadable.Idle)
    val pulls: StateFlow<Loadable<List<GhPull>>> = _pulls.asStateFlow()

    private val _activeRepo = MutableStateFlow<GhRepo?>(null)
    val activeRepo: StateFlow<GhRepo?> = _activeRepo.asStateFlow()

    private val _activePull = MutableStateFlow<GhPull?>(null)
    val activePull: StateFlow<GhPull?> = _activePull.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingComment>>(emptyList())
    val pending: StateFlow<List<PendingComment>> = _pending.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val hasClientId: Boolean get() = auth != null

    private var authJob: Job? = null
    private var reposJob: Job? = null
    private var pullsJob: Job? = null
    private var api: GitHubApi? = null

    init {
        rebuildApiClient()
    }

    private fun rebuildApiClient() {
        api?.close()
        api = _token.value?.takeIf { it.isNotBlank() }?.let { GitHubApi(it) }
    }

    // ─── Device flow ───────────────────────────────────────────

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
                        rebuildApiClient()
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
        _viewer.value = Loadable.Idle
        _repos.value = Loadable.Idle
        _pulls.value = Loadable.Idle
        _activeRepo.value = null
        _activePull.value = null
        rebuildApiClient()
    }

    // ─── Data loading ─────────────────────────────────────────

    fun ensureReposLoaded(force: Boolean = false) {
        val a = api ?: return
        if (!force && _repos.value is Loadable.Ok) return
        reposJob?.cancel()
        reposJob = viewModelScope.launch {
            _viewer.value = Loadable.Loading
            _repos.value = Loadable.Loading
            try {
                _viewer.value = Loadable.Ok(a.viewer())
                _repos.value = Loadable.Ok(a.repos())
            } catch (t: Throwable) {
                val msg = t.message ?: "request failed"
                if (_viewer.value !is Loadable.Ok) _viewer.value = Loadable.Err(msg)
                _repos.value = Loadable.Err(msg)
            }
        }
    }

    fun setActiveRepo(repo: GhRepo) {
        _activeRepo.value = repo
        _pulls.value = Loadable.Idle
        ensurePullsLoaded(repo, force = true)
    }

    fun ensurePullsLoaded(repo: GhRepo, force: Boolean = false) {
        val a = api ?: return
        if (!force && _pulls.value is Loadable.Ok) return
        pullsJob?.cancel()
        pullsJob = viewModelScope.launch {
            _pulls.value = Loadable.Loading
            try {
                _pulls.value = Loadable.Ok(a.pulls(repo.owner.login, repo.name))
            } catch (t: Throwable) {
                _pulls.value = Loadable.Err(t.message ?: "request failed")
            }
        }
    }

    fun setActivePull(pull: GhPull) {
        _activePull.value = pull
    }

    // ─── Comments / toast ─────────────────────────────────────

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
        api?.close()
    }
}

package com.redline.viewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redline.viewer.data.Loadable
import com.redline.viewer.data.PullDetailBundle
import com.redline.viewer.data.github.AuthState
import com.redline.viewer.data.github.GhPull
import com.redline.viewer.data.github.GhRepo
import com.redline.viewer.data.github.GhUser
import com.redline.viewer.data.github.Github
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state holder. Holds the user's current selection (active repo / pull)
 * and the [Loadable] caches the screens render. All actual GitHub talk
 * lives behind [Github].
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val github = Github(app)

    val token: StateFlow<String?> = github.token
    val authState: StateFlow<AuthState> = github.authState
    val hasClientId: Boolean get() = github.hasClientId

    private val _viewer = MutableStateFlow<Loadable<GhUser>>(Loadable.Idle)
    val viewer: StateFlow<Loadable<GhUser>> = _viewer.asStateFlow()

    private val _repos = MutableStateFlow<Loadable<List<GhRepo>>>(Loadable.Idle)
    val repos: StateFlow<Loadable<List<GhRepo>>> = _repos.asStateFlow()

    private val _pulls = MutableStateFlow<Loadable<List<GhPull>>>(Loadable.Idle)
    val pulls: StateFlow<Loadable<List<GhPull>>> = _pulls.asStateFlow()

    private val _detail = MutableStateFlow<Loadable<PullDetailBundle>>(Loadable.Idle)
    val detail: StateFlow<Loadable<PullDetailBundle>> = _detail.asStateFlow()

    private val _activeRepo = MutableStateFlow<GhRepo?>(null)
    val activeRepo: StateFlow<GhRepo?> = _activeRepo.asStateFlow()

    private val _activePull = MutableStateFlow<GhPull?>(null)
    val activePull: StateFlow<GhPull?> = _activePull.asStateFlow()

    private var authJob: Job? = null
    private var reposJob: Job? = null
    private var pullsJob: Job? = null
    private var detailJob: Job? = null

    // ─── Auth (web flow) ──────────────────────────────────────

    /** Returns the URL to open in a Chrome Custom Tab, or null if no Client ID. */
    fun beginSignIn(): Uri? = github.beginSignIn()

    /** Called by MainActivity when the `redline://oauth?...` redirect lands. */
    fun handleCallback(uri: Uri) {
        authJob?.cancel()
        authJob = viewModelScope.launch { github.completeSignIn(uri) }
    }

    fun cancelSignIn() {
        authJob?.cancel()
        github.cancelSignIn()
    }

    fun signOut() {
        github.signOut()
        _viewer.value = Loadable.Idle
        _repos.value = Loadable.Idle
        _pulls.value = Loadable.Idle
        _detail.value = Loadable.Idle
        _activeRepo.value = null
        _activePull.value = null
    }

    // ─── Loads ────────────────────────────────────────────────

    fun ensureReposLoaded(force: Boolean = false) {
        if (!force && _repos.value is Loadable.Ok) return
        reposJob?.cancel()
        reposJob = viewModelScope.launch {
            _viewer.value = Loadable.Loading
            _repos.value = Loadable.Loading
            try {
                _viewer.value = Loadable.Ok(github.viewer())
                _repos.value = Loadable.Ok(github.repos())
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
        ensurePullsLoaded(force = true)
    }

    fun ensurePullsLoaded(force: Boolean = false) {
        val repo = _activeRepo.value ?: return
        if (!force && _pulls.value is Loadable.Ok) return
        pullsJob?.cancel()
        pullsJob = viewModelScope.launch {
            _pulls.value = Loadable.Loading
            try {
                _pulls.value = Loadable.Ok(github.pulls(repo))
            } catch (t: Throwable) {
                _pulls.value = Loadable.Err(t.message ?: "request failed")
            }
        }
    }

    fun setActivePull(pull: GhPull) {
        _activePull.value = pull
        _detail.value = Loadable.Idle
        loadDetail(force = true)
    }

    fun loadDetail(force: Boolean = false) {
        val repo = _activeRepo.value ?: return
        val pull = _activePull.value ?: return
        if (!force && _detail.value is Loadable.Ok) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _detail.value = Loadable.Loading
            try {
                _detail.value = Loadable.Ok(github.pullBundle(repo, pull))
            } catch (t: Throwable) {
                _detail.value = Loadable.Err(t.message ?: "request failed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        github.close()
    }
}

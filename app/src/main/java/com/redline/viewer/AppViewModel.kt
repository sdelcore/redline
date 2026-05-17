package com.redline.viewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redline.viewer.data.Loadable
import com.redline.viewer.data.PullDetailBundle
import com.redline.viewer.data.github.AuthState
import com.redline.viewer.data.github.GhLabel
import com.redline.viewer.data.github.GhMilestone
import com.redline.viewer.data.github.GhPull
import com.redline.viewer.data.github.GhPullSearchItem
import com.redline.viewer.data.github.GhPullUser
import com.redline.viewer.data.github.GhRepo
import com.redline.viewer.data.github.GhRepoOwner
import com.redline.viewer.data.github.GhUser
import com.redline.viewer.data.github.Github
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val _searchedPulls = MutableStateFlow<Loadable<List<GhPullSearchItem>>>(Loadable.Idle)
    val searchedPulls: StateFlow<Loadable<List<GhPullSearchItem>>> = _searchedPulls.asStateFlow()

    private val _activeRepo = MutableStateFlow<GhRepo?>(null)
    val activeRepo: StateFlow<GhRepo?> = _activeRepo.asStateFlow()

    private val _activePull = MutableStateFlow<GhPull?>(null)
    val activePull: StateFlow<GhPull?> = _activePull.asStateFlow()

    private val _resolvingPullId = MutableStateFlow<Long?>(null)
    val resolvingPullId: StateFlow<Long?> = _resolvingPullId.asStateFlow()

    private val _metadataOptions = MutableStateFlow<Loadable<MetadataOptions>>(Loadable.Idle)
    val metadataOptions: StateFlow<Loadable<MetadataOptions>> = _metadataOptions.asStateFlow()

    private val _metadataSaving = MutableStateFlow(false)
    val metadataSaving: StateFlow<Boolean> = _metadataSaving.asStateFlow()

    private var authJob: Job? = null
    private var reposJob: Job? = null
    private var pullsJob: Job? = null
    private var detailJob: Job? = null
    private var searchedPullsJob: Job? = null
    private var resolvePullJob: Job? = null
    private var metadataOptionsJob: Job? = null
    private var metadataMutationJob: Job? = null
    private var metadataOptionsRepoKey: String? = null

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
        _searchedPulls.value = Loadable.Idle
        _activeRepo.value = null
        _activePull.value = null
        _resolvingPullId.value = null
        _metadataOptions.value = Loadable.Idle
        _metadataSaving.value = false
        metadataOptionsRepoKey = null
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

    fun ensureSearchedPullsLoaded(force: Boolean = false) {
        if (!force && _searchedPulls.value is Loadable.Ok) return
        val login = (_viewer.value as? Loadable.Ok)?.value?.login
        if (login.isNullOrBlank()) {
            // Viewer not loaded yet — kick off ensureReposLoaded and wait for it.
            searchedPullsJob?.cancel()
            searchedPullsJob = viewModelScope.launch {
                _searchedPulls.value = Loadable.Loading
                try {
                    val viewer = github.viewer()
                    _viewer.value = Loadable.Ok(viewer)
                    _searchedPulls.value = Loadable.Ok(github.searchInvolvedPulls(viewer.login))
                } catch (t: Throwable) {
                    _searchedPulls.value = Loadable.Err(t.message ?: "request failed")
                }
            }
            return
        }
        searchedPullsJob?.cancel()
        searchedPullsJob = viewModelScope.launch {
            _searchedPulls.value = Loadable.Loading
            try {
                _searchedPulls.value = Loadable.Ok(github.searchInvolvedPulls(login))
            } catch (t: Throwable) {
                _searchedPulls.value = Loadable.Err(t.message ?: "request failed")
            }
        }
    }

    /**
     * Resolve a cross-repo search item into a full [GhPull] + minimal [GhRepo],
     * then invoke [onReady] on the main thread so the caller can navigate. If
     * the fetch fails, [onReady] is not invoked and we leave the existing
     * active selection alone.
     */
    fun openSearchedPull(item: GhPullSearchItem, onReady: () -> Unit) {
        val parsed = parseRepositoryUrl(item.repository_url) ?: return
        val (owner, name) = parsed
        resolvePullJob?.cancel()
        _resolvingPullId.value = item.number.toLong()
        resolvePullJob = viewModelScope.launch {
            try {
                val full = github.pull(owner, name, item.number)
                val repo = GhRepo(
                    id = 0L,
                    name = name,
                    full_name = "$owner/$name",
                    owner = GhRepoOwner(login = owner),
                )
                _activeRepo.value = repo
                _activePull.value = full
                _detail.value = Loadable.Idle
                loadDetail(force = true)
                _resolvingPullId.value = null
                onReady()
            } catch (t: Throwable) {
                _resolvingPullId.value = null
            }
        }
    }

    private fun parseRepositoryUrl(url: String): Pair<String, String>? {
        // e.g. https://api.github.com/repos/foo/bar
        val marker = "/repos/"
        val i = url.indexOf(marker)
        if (i < 0) return null
        val tail = url.substring(i + marker.length).trimEnd('/')
        val parts = tail.split('/')
        if (parts.size < 2) return null
        return parts[0] to parts[1]
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

    // ─── PR metadata ──────────────────────────────────────────

    fun ensureMetadataOptionsLoaded(force: Boolean = false) {
        val repo = _activeRepo.value ?: return
        val key = "${repo.owner.login}/${repo.name}"
        val sameRepo = metadataOptionsRepoKey == key
        if (!force && sameRepo && _metadataOptions.value is Loadable.Ok) return
        metadataOptionsJob?.cancel()
        metadataOptionsRepoKey = key
        metadataOptionsJob = viewModelScope.launch {
            _metadataOptions.value = Loadable.Loading
            try {
                val opts = coroutineScope {
                    val dUsers = async { github.assignableUsers(repo.owner.login, repo.name) }
                    val dLabels = async { github.repoLabels(repo.owner.login, repo.name) }
                    val dMilestones = async { github.repoMilestones(repo.owner.login, repo.name) }
                    MetadataOptions(
                        assignableUsers = dUsers.await(),
                        labels = dLabels.await(),
                        milestones = dMilestones.await(),
                    )
                }
                _metadataOptions.value = Loadable.Ok(opts)
            } catch (t: Throwable) {
                _metadataOptions.value = Loadable.Err(t.message ?: "request failed")
            }
        }
    }

    fun setPullAssignees(logins: List<String>, onDone: (Throwable?) -> Unit = {}) =
        mutateMetadata(onDone) { repo, pull ->
            github.setAssignees(repo.owner.login, repo.name, pull.number, logins)
        }

    fun setPullReviewers(logins: List<String>, onDone: (Throwable?) -> Unit = {}) =
        mutateMetadata(onDone) { repo, pull ->
            val current = pull.requested_reviewers.map { it.login }.toSet()
            val target = logins.toSet()
            val toAdd = (target - current).toList()
            val toRemove = (current - target).toList()
            github.addReviewers(repo.owner.login, repo.name, pull.number, toAdd)
            github.removeReviewers(repo.owner.login, repo.name, pull.number, toRemove)
        }

    fun setPullLabels(labels: List<String>, onDone: (Throwable?) -> Unit = {}) =
        mutateMetadata(onDone) { repo, pull ->
            github.setLabels(repo.owner.login, repo.name, pull.number, labels)
        }

    fun setPullMilestone(milestoneNumber: Int?, onDone: (Throwable?) -> Unit = {}) =
        mutateMetadata(onDone) { repo, pull ->
            github.setMilestone(repo.owner.login, repo.name, pull.number, milestoneNumber)
        }

    private fun mutateMetadata(
        onDone: (Throwable?) -> Unit,
        block: suspend (GhRepo, GhPull) -> Unit,
    ) {
        val repo = _activeRepo.value ?: return
        val pull = _activePull.value ?: return
        metadataMutationJob?.cancel()
        _metadataSaving.value = true
        metadataMutationJob = viewModelScope.launch {
            try {
                block(repo, pull)
                refreshActivePull()
                _metadataSaving.value = false
                onDone(null)
            } catch (t: Throwable) {
                _metadataSaving.value = false
                onDone(t)
            }
        }
    }

    private suspend fun refreshActivePull() {
        val repo = _activeRepo.value ?: return
        val pull = _activePull.value ?: return
        val updated = github.pull(repo.owner.login, repo.name, pull.number)
        _activePull.value = updated
        val current = _detail.value
        if (current is Loadable.Ok) {
            _detail.value = Loadable.Ok(current.value.copy(pull = updated))
        }
    }

    override fun onCleared() {
        super.onCleared()
        github.close()
    }
}

data class MetadataOptions(
    val assignableUsers: List<GhPullUser>,
    val labels: List<GhLabel>,
    val milestones: List<GhMilestone>,
)

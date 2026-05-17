package com.redline.viewer.data.github

import android.content.Context
import android.net.Uri
import com.redline.viewer.BuildConfig
import com.redline.viewer.data.ChangedFile
import com.redline.viewer.data.Check
import com.redline.viewer.data.CheckSummary
import com.redline.viewer.data.Comment
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.ConversationItem
import com.redline.viewer.data.ConversationKind
import com.redline.viewer.data.FileStatus
import com.redline.viewer.data.PullDetailBundle
import com.redline.viewer.data.ReviewVerdict
import com.redline.viewer.data.Thread
import com.redline.viewer.data.avatarColorFor
import com.redline.viewer.data.parseUnifiedDiff
import com.redline.viewer.data.timeAgo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

sealed class AuthState {
    object Idle : AuthState()
    /** Custom Tab is open in the system browser; we're waiting for the redirect. */
    data class WaitingForCallback(val authorizeUri: Uri) : AuthState()
    /** Got the auth code from the redirect; calling /login/oauth/access_token. */
    object Exchanging : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Everything the app needs to talk to GitHub. Owns the OAuth token,
 * the web-flow lifecycle (PKCE + Chrome Custom Tabs), and the http
 * client(s). Callers see one surface; rebuilding the http client on
 * token change, parsing diff patches, and grouping review comments
 * into threads all happen behind the seam.
 */
class Github(context: Context) {

    private val clientId: String = BuildConfig.GITHUB_CLIENT_ID
    private val clientSecret: String = BuildConfig.GITHUB_CLIENT_SECRET
    private val tokenStore = TokenStore(context.applicationContext)
    private val redirectUri: String = REDIRECT_URI

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _token = MutableStateFlow(tokenStore.accessToken)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val hasClientId: Boolean get() = clientId.isNotBlank()

    @Volatile
    private var api: HttpClient = buildApiClient(_token.value)

    private val oauthClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
    }

    private var pendingVerifier: String? = null
    private var pendingState: String? = null

    // ─── Auth: web flow ───────────────────────────────────────

    /**
     * Begin the OAuth web flow. Returns the URL to open in a Chrome
     * Custom Tab, or `null` if the Client ID isn't configured. Sets
     * [authState] to [AuthState.WaitingForCallback] so the UI knows
     * the browser is in charge.
     */
    fun beginSignIn(): Uri? {
        if (clientId.isBlank()) {
            _authState.value = AuthState.Error("GITHUB_CLIENT_ID is not set. See README.")
            return null
        }
        val pkce = generatePkce()
        val state = randomState()
        pendingVerifier = pkce.verifier
        pendingState = state

        val uri = Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .path("/login/oauth/authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "repo")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        _authState.value = AuthState.WaitingForCallback(uri)
        return uri
    }

    /**
     * Handle the `redline://oauth?...` redirect that GitHub sends back
     * to the app once the user has authorized. Exchanges `code` for an
     * access token using the stashed PKCE verifier, validates `state`.
     */
    suspend fun completeSignIn(callback: Uri) {
        val error = callback.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val desc = callback.getQueryParameter("error_description") ?: error
            _authState.value = AuthState.Error(desc)
            return
        }
        val code = callback.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            _authState.value = AuthState.Error("no code in redirect")
            return
        }
        val receivedState = callback.getQueryParameter("state")
        val expectedState = pendingState
        val verifier = pendingVerifier
        if (expectedState == null || verifier == null) {
            _authState.value = AuthState.Error("no pending sign-in")
            return
        }
        if (receivedState != expectedState) {
            _authState.value = AuthState.Error("state mismatch")
            return
        }

        _authState.value = AuthState.Exchanging
        try {
            val resp = oauthClient.post("https://github.com/login/oauth/access_token") {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.FormUrlEncoded)
                parameter("client_id", clientId)
                parameter("client_secret", clientSecret)
                parameter("code", code)
                parameter("redirect_uri", redirectUri)
                parameter("code_verifier", verifier)
            }
            if (resp.status != HttpStatusCode.OK) {
                _authState.value = AuthState.Error("token endpoint: HTTP ${resp.status.value}")
                return
            }
            val body = resp.bodyAsText()
            val ok = runCatching { json.decodeFromString(TokenSuccess.serializer(), body) }.getOrNull()
            if (ok != null && ok.access_token.isNotBlank()) {
                tokenStore.accessToken = ok.access_token
                replaceApiClient(ok.access_token)
                _token.value = ok.access_token
                _authState.value = AuthState.Idle
                pendingVerifier = null
                pendingState = null
                return
            }
            val err = runCatching { json.decodeFromString(TokenError.serializer(), body) }.getOrNull()
            _authState.value = AuthState.Error(err?.error_description ?: err?.error ?: "token exchange failed")
        } catch (t: Throwable) {
            _authState.value = AuthState.Error(t.message ?: "token exchange failed")
        }
    }

    fun cancelSignIn() {
        pendingVerifier = null
        pendingState = null
        _authState.value = AuthState.Idle
    }

    fun signOut() {
        tokenStore.clear()
        replaceApiClient(null)
        _token.value = null
        _authState.value = AuthState.Idle
        pendingVerifier = null
        pendingState = null
    }

    // ─── Data ─────────────────────────────────────────────────

    suspend fun viewer(): GhUser =
        api.get("user").body()

    suspend fun repos(perPage: Int = 50): List<GhRepo> =
        api.get("user/repos") {
            parameter("affiliation", "owner,collaborator,organization_member")
            parameter("sort", "pushed")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }.body()

    suspend fun pulls(repo: GhRepo, state: String = "open", perPage: Int = 30): List<GhPull> =
        api.get("repos/${repo.owner.login}/${repo.name}/pulls") {
            parameter("state", state)
            parameter("sort", "updated")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }.body()

    /** Cross-repo: every open PR the viewer is involved in (author/assignee/reviewer/mentioned). */
    suspend fun searchInvolvedPulls(login: String, perPage: Int = 50): List<GhPullSearchItem> =
        api.get("search/issues") {
            parameter("q", "is:open is:pr involves:$login archived:false")
            parameter("sort", "updated")
            parameter("order", "desc")
            parameter("per_page", perPage)
        }.body<GhPullSearchResponse>().items

    suspend fun pull(owner: String, name: String, number: Int): GhPull =
        api.get("repos/$owner/$name/pulls/$number").body()

    // ─── PR metadata: list available options ─────────────────────

    suspend fun assignableUsers(owner: String, name: String): List<GhPullUser> =
        api.get("repos/$owner/$name/assignees") { parameter("per_page", 100) }.body()

    suspend fun repoLabels(owner: String, name: String): List<GhLabel> =
        api.get("repos/$owner/$name/labels") { parameter("per_page", 100) }.body()

    suspend fun repoMilestones(owner: String, name: String): List<GhMilestone> =
        api.get("repos/$owner/$name/milestones") {
            parameter("state", "open")
            parameter("per_page", 100)
        }.body()

    // ─── PR metadata: mutations ──────────────────────────────────

    suspend fun setAssignees(owner: String, name: String, number: Int, logins: List<String>) {
        api.patch("repos/$owner/$name/issues/$number") {
            contentType(ContentType.Application.Json)
            setBody(AssigneesBody(logins))
        }
    }

    suspend fun addReviewers(owner: String, name: String, number: Int, logins: List<String>) {
        if (logins.isEmpty()) return
        api.post("repos/$owner/$name/pulls/$number/requested_reviewers") {
            contentType(ContentType.Application.Json)
            setBody(ReviewersBody(logins))
        }
    }

    suspend fun removeReviewers(owner: String, name: String, number: Int, logins: List<String>) {
        if (logins.isEmpty()) return
        api.delete("repos/$owner/$name/pulls/$number/requested_reviewers") {
            contentType(ContentType.Application.Json)
            setBody(ReviewersBody(logins))
        }
    }

    suspend fun setLabels(owner: String, name: String, number: Int, labels: List<String>) {
        api.put("repos/$owner/$name/issues/$number/labels") {
            contentType(ContentType.Application.Json)
            setBody(LabelsBody(labels))
        }
    }

    suspend fun setMilestone(owner: String, name: String, number: Int, milestoneNumber: Int?) {
        api.patch("repos/$owner/$name/issues/$number") {
            contentType(ContentType.Application.Json)
            setBody(MilestoneBody(milestoneNumber))
        }
    }

    suspend fun pullBundle(repo: GhRepo, pull: GhPull): PullDetailBundle = coroutineScope {
        val owner = repo.owner.login
        val name = repo.name
        val number = pull.number

        val dDetail = async { api.get("repos/$owner/$name/pulls/$number").body<GhPull>() }
        val dFiles = async {
            api.get("repos/$owner/$name/pulls/$number/files") {
                parameter("per_page", 100)
            }.body<List<GhFile>>()
        }
        val dChecks = async {
            runCatching {
                api.get("repos/$owner/$name/commits/${pull.head.sha}/check-runs") {
                    parameter("per_page", 100)
                }.body<GhCheckRunsResponse>()
            }.getOrNull()
        }
        val dComments = async {
            runCatching {
                api.get("repos/$owner/$name/pulls/$number/comments") {
                    parameter("per_page", 100)
                }.body<List<GhReviewComment>>()
            }.getOrNull().orEmpty()
        }
        val dIssueComments = async {
            runCatching {
                api.get("repos/$owner/$name/issues/$number/comments") {
                    parameter("per_page", 100)
                }.body<List<GhIssueComment>>()
            }.getOrNull().orEmpty()
        }
        val dReviews = async {
            runCatching {
                api.get("repos/$owner/$name/pulls/$number/reviews") {
                    parameter("per_page", 100)
                }.body<List<GhReview>>()
            }.getOrNull().orEmpty()
        }

        val detail = dDetail.await()
        val ghFiles = dFiles.await()
        val checkRuns = dChecks.await()?.check_runs.orEmpty()
        val ghComments = dComments.await()
        val ghIssueComments = dIssueComments.await()
        val ghReviews = dReviews.await()

        val files = ghFiles.map { it.toChangedFile() }
        val diffs = ghFiles.associate { gf ->
            gf.filename.substringAfterLast('/') to parseUnifiedDiff(gf.patch)
        }
        val checks = checkRuns.map { it.toCheck() }
        val commentsByShort = ghComments.toThreadsByPath()
            .mapKeys { (path, _) -> path.substringAfterLast('/') }
        val conversation = buildConversation(ghIssueComments, ghReviews)

        PullDetailBundle(detail, files, diffs, checks, commentsByShort, conversation)
    }

    // ─── Internals ────────────────────────────────────────────

    private fun buildApiClient(token: String?): HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.github.com"
                }
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header(HttpHeaders.UserAgent, "redline-android")
            }
        }

    private fun replaceApiClient(newToken: String?) {
        val old = api
        api = buildApiClient(newToken)
        old.close()
    }

    fun close() {
        api.close()
        oauthClient.close()
    }

    companion object {
        const val REDIRECT_URI: String = "redline://oauth"
    }
}

@Serializable
private data class TokenSuccess(
    val access_token: String,
    val token_type: String? = null,
    val scope: String? = null,
)

@Serializable
private data class TokenError(
    val error: String,
    val error_description: String? = null,
)

// ─── Mapping helpers (kept private; only the bundle leaves) ──────

private fun GhFile.toChangedFile(): ChangedFile = ChangedFile(
    path = filename,
    short = filename.substringAfterLast('/'),
    additions = additions,
    deletions = deletions,
    status = when (status) {
        "added" -> FileStatus.Added
        "removed" -> FileStatus.Deleted
        else -> FileStatus.Modified
    },
)

private fun GhCheckRun.toCheck(): Check {
    val summary = when (status) {
        "completed" -> when (conclusion) {
            "success", "neutral", "skipped" -> CheckSummary.Pass
            "failure", "cancelled", "timed_out", "action_required" -> CheckSummary.Fail
            else -> CheckSummary.Pending
        }
        else -> CheckSummary.Pending
    }
    return Check(
        name = name,
        status = summary,
        duration = formatRunDuration(started_at, completed_at),
        required = false,
        workflow = app?.name?.takeIf { it.isNotBlank() } ?: "CI",
        failNote = if (summary == CheckSummary.Fail) conclusion?.replaceFirstChar { it.uppercase() } else null,
    )
}

private fun formatRunDuration(start: String?, end: String?): String {
    if (start.isNullOrBlank()) return "—"
    val s = runCatching { Instant.parse(start) }.getOrNull() ?: return "—"
    val e = end?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
    val secs = Duration.between(s, e).seconds.coerceAtLeast(0)
    return when {
        secs < 60 -> "${secs}s"
        secs < 3600 -> "${secs / 60}m ${secs % 60}s"
        else -> "${secs / 3600}h ${(secs % 3600) / 60}m"
    }
}

private fun List<GhReviewComment>.toThreadsByPath(): Map<String, List<Thread>> =
    groupBy { it.path }.mapValues { (_, comments) ->
        comments
            .filter { it.line != null }
            .groupBy { Triple(it.path, it.line!!, sideOf(it)) }
            .map { (key, group) ->
                val ordered = group.sortedBy { it.created_at }
                Thread(
                    side = key.third,
                    line = key.second,
                    comments = ordered.map { it.toComment() },
                )
            }
    }

private fun sideOf(c: GhReviewComment): CommentSide =
    if ((c.side ?: "RIGHT").equals("LEFT", ignoreCase = true)) CommentSide.Old else CommentSide.New

private fun buildConversation(
    issues: List<GhIssueComment>,
    reviews: List<GhReview>,
): List<ConversationItem> {
    val issueItems = issues.map { ic ->
        val login = ic.user?.login.orEmpty()
        ConversationItem(
            author = login.ifEmpty { "?" },
            avatar = avatarColorFor(login),
            whenLabel = timeAgo(ic.created_at),
            createdAt = ic.created_at,
            kind = ConversationKind.Comment,
            body = ic.body,
        )
    }
    val reviewItems = reviews
        .filter { it.state != null && it.state != "PENDING" }
        .map { rv ->
            val login = rv.user?.login.orEmpty()
            val submitted = rv.submitted_at ?: ""
            ConversationItem(
                author = login.ifEmpty { "?" },
                avatar = avatarColorFor(login),
                whenLabel = timeAgo(submitted.ifEmpty { null }),
                createdAt = submitted,
                kind = ConversationKind.Review,
                body = rv.body.orEmpty(),
                verdict = when (rv.state) {
                    "APPROVED" -> ReviewVerdict.Approve
                    "CHANGES_REQUESTED" -> ReviewVerdict.RequestChanges
                    else -> ReviewVerdict.Comment
                },
            )
        }
        .filter { it.body.isNotBlank() || it.verdict != ReviewVerdict.Comment }
    return (issueItems + reviewItems).sortedBy { it.createdAt }
}

private fun GhReviewComment.toComment(): Comment {
    val login = user?.login.orEmpty()
    return Comment(
        author = login.ifEmpty { "?" },
        avatar = avatarColorFor(login),
        whenLabel = timeAgo(created_at),
        body = body,
        resolved = false,
    )
}

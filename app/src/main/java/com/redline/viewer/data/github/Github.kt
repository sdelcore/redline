package com.redline.viewer.data.github

import android.content.Context
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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

sealed class AuthState {
    object Idle : AuthState()
    object Requesting : AuthState()
    data class Verifying(val userCode: String, val verificationUri: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Everything the app needs to talk to GitHub. Owns the OAuth token,
 * the device-flow lifecycle, and the http client(s). Callers see one
 * surface; rebuilding the http client when the token changes, parsing
 * diff patches, and grouping review comments into threads all happen
 * behind the seam.
 */
class Github(context: Context) {

    private val clientId: String = BuildConfig.GITHUB_CLIENT_ID
    private val tokenStore = TokenStore(context.applicationContext)
    private val deviceAuth: DeviceAuth? =
        if (clientId.isNotBlank()) DeviceAuth(clientId) else null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _token = MutableStateFlow(tokenStore.accessToken)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val hasClientId: Boolean get() = deviceAuth != null

    @Volatile
    private var api: HttpClient = buildApiClient(_token.value)

    // ─── Auth ─────────────────────────────────────────────────

    /**
     * Run the GitHub device-flow handshake from start to finish, surfacing
     * progress via [authState]. On success the token is persisted and
     * [token] flips to the new value.
     *
     * Cancellation is co-operative: cancel the launching job to abort.
     * The function returns normally in every terminal case; failures land
     * in `authState` as [AuthState.Error] rather than throwing.
     */
    suspend fun signIn() {
        val da = deviceAuth ?: run {
            _authState.value = AuthState.Error("GITHUB_CLIENT_ID is not set. See README.")
            return
        }
        _authState.value = AuthState.Requesting
        try {
            val code = da.requestCode()
            _authState.value = AuthState.Verifying(code.user_code, code.verification_uri)
            when (val r = da.poll(code)) {
                is PollResult.Success -> {
                    tokenStore.accessToken = r.accessToken
                    replaceApiClient(r.accessToken)
                    _token.value = r.accessToken
                    _authState.value = AuthState.Idle
                }
                is PollResult.Error -> {
                    _authState.value = AuthState.Error(r.message.ifBlank { r.code })
                }
            }
        } catch (c: CancellationException) {
            _authState.value = AuthState.Idle
            throw c
        } catch (t: Throwable) {
            _authState.value = AuthState.Error(t.message ?: "auth failed")
        }
    }

    fun cancelSignInState() {
        _authState.value = AuthState.Idle
    }

    fun signOut() {
        tokenStore.clear()
        replaceApiClient(null)
        _token.value = null
        _authState.value = AuthState.Idle
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

    /**
     * Fetch every piece the file-browser and diff-view need for a single PR
     * in parallel and stitch the result into a [PullDetailBundle]:
     * - detail (additions/deletions/changed_files/mergeable)
     * - files (filename, status, additions, deletions, patch)
     * - check-runs for the head SHA
     * - review comments grouped by file path → inline threads
     */
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
        deviceAuth?.close()
    }
}

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

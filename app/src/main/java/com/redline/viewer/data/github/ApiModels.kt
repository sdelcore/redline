package com.redline.viewer.data.github

import kotlinx.serialization.Serializable

@Serializable
data class GhUser(
    val login: String,
    val name: String? = null,
    val avatar_url: String = "",
)

@Serializable
data class GhRepoOwner(
    val login: String,
    val avatar_url: String? = null,
)

@Serializable
data class GhRepo(
    val id: Long,
    val name: String,
    val full_name: String,
    val owner: GhRepoOwner,
    val private: Boolean = false,
    val description: String? = null,
    val stargazers_count: Int = 0,
    val language: String? = null,
    val open_issues_count: Int = 0,
    val pushed_at: String? = null,
    val updated_at: String? = null,
    val fork: Boolean = false,
)

@Serializable
data class GhPullUser(
    val login: String,
    val avatar_url: String? = null,
)

@Serializable
data class GhPullRef(
    val ref: String,
    val sha: String = "",
)

@Serializable
data class GhLabel(
    val id: Long = 0,
    val name: String,
    val color: String = "",
    val description: String? = null,
)

@Serializable
data class GhMilestone(
    val number: Int,
    val title: String,
    val state: String = "open",
    val description: String? = null,
    val due_on: String? = null,
)

@Serializable
data class GhPull(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean = false,
    val user: GhPullUser? = null,
    val head: GhPullRef,
    val base: GhPullRef,
    val comments: Int = 0,
    val review_comments: Int = 0,
    val created_at: String,
    val updated_at: String,
    val additions: Int? = null,
    val deletions: Int? = null,
    val changed_files: Int? = null,
    val mergeable: Boolean? = null,
    val mergeable_state: String? = null,
    val assignees: List<GhPullUser> = emptyList(),
    val requested_reviewers: List<GhPullUser> = emptyList(),
    val labels: List<GhLabel> = emptyList(),
    val milestone: GhMilestone? = null,
)

@Serializable
data class GhPullSearchItem(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean = false,
    val user: GhPullUser? = null,
    val comments: Int = 0,
    val updated_at: String,
    val created_at: String,
    val repository_url: String,
)

@Serializable
data class GhPullSearchResponse(
    val total_count: Int = 0,
    val incomplete_results: Boolean = false,
    val items: List<GhPullSearchItem> = emptyList(),
)

@Serializable
data class GhFile(
    val sha: String? = null,
    val filename: String,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val blob_url: String? = null,
    val raw_url: String? = null,
    val contents_url: String? = null,
    val patch: String? = null,
    val previous_filename: String? = null,
)

@Serializable
data class GhCheckRunApp(
    val id: Long = 0,
    val name: String = "",
    val slug: String = "",
)

@Serializable
data class GhCheckRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val started_at: String? = null,
    val completed_at: String? = null,
    val details_url: String? = null,
    val head_sha: String = "",
    val app: GhCheckRunApp? = null,
)

@Serializable
data class GhCheckRunsResponse(
    val total_count: Int = 0,
    val check_runs: List<GhCheckRun> = emptyList(),
)

@Serializable
data class GhReviewComment(
    val id: Long,
    val path: String,
    val line: Int? = null,
    val original_line: Int? = null,
    val side: String? = null, // "LEFT" (deletion) | "RIGHT" (addition/context)
    val in_reply_to_id: Long? = null,
    val body: String,
    val user: GhPullUser? = null,
    val created_at: String,
    val updated_at: String,
    val diff_hunk: String? = null,
)

@Serializable
data class GhIssueComment(
    val id: Long,
    val body: String = "",
    val user: GhPullUser? = null,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class GhReview(
    val id: Long,
    val body: String? = null,
    val state: String? = null, // "APPROVED" | "CHANGES_REQUESTED" | "COMMENTED" | "DISMISSED" | "PENDING"
    val user: GhPullUser? = null,
    val submitted_at: String? = null,
)

@Serializable
data class GhRequestedReviewers(
    val users: List<GhPullUser> = emptyList(),
    val teams: List<GhTeam> = emptyList(),
)

@Serializable
data class GhTeam(
    val slug: String,
    val name: String = "",
)

// ─── Mutation request bodies ──────────────────────────────────────

@Serializable
internal data class AssigneesBody(val assignees: List<String>)

@Serializable
internal data class ReviewersBody(val reviewers: List<String>)

@Serializable
internal data class LabelsBody(val labels: List<String>)

@Serializable
internal data class MilestoneBody(val milestone: Int?)

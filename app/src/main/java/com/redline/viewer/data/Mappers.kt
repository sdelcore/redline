package com.redline.viewer.data

import androidx.compose.ui.graphics.Color
import com.redline.viewer.data.github.GhCheckRun
import com.redline.viewer.data.github.GhFile
import com.redline.viewer.data.github.GhReviewComment
import java.time.Duration
import java.time.Instant

fun GhFile.toChangedFile(): ChangedFile {
    val short = filename.substringAfterLast('/')
    val status = when (status) {
        "added" -> FileStatus.Added
        "removed" -> FileStatus.Deleted
        else -> FileStatus.Modified
    }
    return ChangedFile(
        path = filename,
        short = short,
        additions = additions,
        deletions = deletions,
        status = status,
    )
}

fun GhCheckRun.toCheck(): Check {
    val summary = when (status) {
        "completed" -> when (conclusion) {
            "success", "neutral", "skipped" -> CheckSummary.Pass
            "failure", "cancelled", "timed_out", "action_required" -> CheckSummary.Fail
            else -> CheckSummary.Pending
        }
        else -> CheckSummary.Pending
    }
    val duration = formatDuration(started_at, completed_at)
    val workflow = app?.name?.takeIf { it.isNotBlank() } ?: "CI"
    val failNote = if (summary == CheckSummary.Fail) conclusion?.replaceFirstChar { it.uppercase() } else null
    return Check(
        name = name,
        status = summary,
        duration = duration,
        required = false, // not available from the check-runs endpoint
        workflow = workflow,
        failNote = failNote,
    )
}

private fun formatDuration(start: String?, end: String?): String {
    if (start.isNullOrBlank()) return "—"
    val s = runCatching { Instant.parse(start) }.getOrNull() ?: return "—"
    val e = end?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
    val d = Duration.between(s, e)
    val secs = d.seconds.coerceAtLeast(0)
    return when {
        secs < 60 -> "${secs}s"
        secs < 3600 -> "${secs / 60}m ${secs % 60}s"
        else -> "${secs / 3600}h ${(secs % 3600) / 60}m"
    }
}

/**
 * Group review comments into per-file inline threads keyed by file path.
 * Comments at the same (line, side) are merged into one thread in
 * created_at order — replies fall in naturally without us needing to
 * follow `in_reply_to_id`.
 */
fun List<GhReviewComment>.toThreadsByPath(): Map<String, List<Thread>> {
    return groupBy { it.path }
        .mapValues { (_, comments) ->
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
}

private fun sideOf(c: GhReviewComment): CommentSide =
    if ((c.side ?: "RIGHT").equals("LEFT", ignoreCase = true)) CommentSide.Old else CommentSide.New

private fun GhReviewComment.toComment(): Comment {
    val login = user?.login.orEmpty()
    return Comment(
        author = login.ifEmpty { "?" },
        avatar = avatarColorFor(login),
        whenLabel = timeAgo(created_at),
        body = body,
        resolved = false, // GraphQL-only; default to false in REST
    )
}

private val AVATAR_PALETTE = listOf(
    0xFFF97316, 0xFF22D3EE, 0xFF3B82F6, 0xFFA855F7, 0xFFEC4899,
    0xFF14B8A6, 0xFFFBBF24, 0xFFEF4444, 0xFF8B5CF6, 0xFF10B981,
)

fun avatarColorFor(login: String): Color {
    if (login.isEmpty()) return Color(0xFF6E7681)
    val idx = (login.hashCode().toUInt().toInt() and 0x7FFFFFFF) % AVATAR_PALETTE.size
    return Color(AVATAR_PALETTE[idx])
}

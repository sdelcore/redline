package com.redline.viewer.data

import androidx.compose.ui.graphics.Color

data class Repo(val id: String, val owner: String, val name: String, val stars: String)

data class PR(
    val id: Int,
    val repo: String,
    val title: String,
    val author: String,
    val avatar: Color,
    val branch: String,
    val base: String,
    val files: Int,
    val additions: Int,
    val deletions: Int,
    val opened: String,
    val draft: Boolean,
    val checks: CheckSummary,
    val comments: Int,
)

enum class CheckSummary { Pass, Fail, Pending }

enum class FileStatus { Added, Modified, Deleted }

data class ChangedFile(
    val path: String,
    val short: String,
    val additions: Int,
    val deletions: Int,
    val status: FileStatus,
)

enum class DiffRowType { Hunk, Context, Add, Del }

data class DiffLine(val n: Int, val text: String)

data class DiffRow(
    val type: DiffRowType,
    val old: DiffLine? = null,
    val newer: DiffLine? = null,
    val hunkText: String? = null,
)

data class Check(
    val name: String,
    val status: CheckSummary,
    val duration: String,
    val required: Boolean,
    val workflow: String,
    val delta: String? = null,
    val url: String? = null,
    val failNote: String? = null,
)

data class Comment(
    val author: String,
    val avatar: Color,
    val whenLabel: String,
    val body: String,
    val resolved: Boolean,
)

enum class CommentSide { Old, New }

data class Thread(val side: CommentSide, val line: Int, val comments: List<Comment>)

data class PendingComment(
    val fileShort: String,
    val side: CommentSide,
    val line: Int,
    val body: String,
)

enum class ReviewVerdict { Comment, Approve, RequestChanges }

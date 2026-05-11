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
data class GhPullRef(val ref: String)

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
)

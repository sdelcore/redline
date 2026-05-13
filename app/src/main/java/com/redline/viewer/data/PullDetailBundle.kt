package com.redline.viewer.data

import com.redline.viewer.data.github.GhPull

data class PullDetailBundle(
    val pull: GhPull,
    val files: List<ChangedFile>,
    val diffs: Map<String, List<DiffRow>>,
    val checks: List<Check>,
    val commentsByPath: Map<String, List<Thread>>,
    val conversation: List<ConversationItem>,
)

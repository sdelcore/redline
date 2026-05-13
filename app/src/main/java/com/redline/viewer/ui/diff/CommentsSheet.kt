package com.redline.viewer.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.ChangedFile
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.Thread
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

data class CommentJump(
    val fileShort: String,
    val line: Int,
    val side: CommentSide,
)

private enum class CommentFilter(val label: String) {
    Unresolved("unresolved"),
    Drafts("drafts"),
    Resolved("resolved"),
    All("all"),
}

private data class FlatThread(
    val file: ChangedFile,
    val thread: Thread,
    val isResolved: Boolean,
    val isDraft: Boolean,
    val previewBody: String,
    val replyCount: Int,
    val lastWhen: String,
    val authorAvatar: Color,
    val authorInitial: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    files: List<ChangedFile>,
    commentsByPath: Map<String, List<Thread>>,
    pendingComments: List<PendingComment>,
    activeFileShort: String,
    onJump: (CommentJump) -> Unit,
    onCancel: () -> Unit,
) {
    var filter by remember { mutableStateOf(CommentFilter.Unresolved) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val draftThreads = remember(pendingComments, files) {
        pendingComments.map { p ->
            val file = files.firstOrNull { it.short == p.fileShort }
                ?: ChangedFile(p.fileShort, p.fileShort, 0, 0, com.redline.viewer.data.FileStatus.Modified)
            FlatThread(
                file = file,
                thread = Thread(
                    side = p.side,
                    line = p.line,
                    comments = listOf(
                        com.redline.viewer.data.Comment(
                            author = "you",
                            avatar = RedlineColors.Accent,
                            whenLabel = "just now",
                            body = p.body,
                            resolved = false,
                        )
                    ),
                ),
                isResolved = false,
                isDraft = true,
                previewBody = p.body,
                replyCount = 0,
                lastWhen = "just now",
                authorAvatar = RedlineColors.Accent,
                authorInitial = "Y",
            )
        }
    }
    val serverThreads = remember(commentsByPath, files) {
        files.flatMap { file ->
            (commentsByPath[file.short] ?: emptyList()).map { thread ->
                val resolved = thread.comments.any { it.resolved }
                val last = thread.comments.last()
                FlatThread(
                    file = file,
                    thread = thread,
                    isResolved = resolved,
                    isDraft = false,
                    previewBody = last.body,
                    replyCount = (thread.comments.size - 1).coerceAtLeast(0),
                    lastWhen = last.whenLabel,
                    authorAvatar = last.avatar,
                    authorInitial = last.author.take(1).uppercase().ifEmpty { "?" },
                )
            }
        }
    }
    val all = draftThreads + serverThreads

    val unresolvedCount = all.count { !it.isResolved && !it.isDraft }
    val resolvedCount = all.count { it.isResolved }
    val draftCount = draftThreads.size

    val filtered = all.filter { ft ->
        when (filter) {
            CommentFilter.Unresolved -> !ft.isResolved && !ft.isDraft
            CommentFilter.Drafts -> ft.isDraft
            CommentFilter.Resolved -> ft.isResolved
            CommentFilter.All -> true
        }
    }
    val groupedByFile = filtered.groupBy { it.file.short }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = RedlineColors.Bg,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RedlineColors.Border)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "comments",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = RedlineColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.clickable(onClick = onCancel).padding(4.dp)) {
                    Text("×", fontFamily = JetBrainsMono, fontSize = 18.sp, color = RedlineColors.TextMute)
                }
            }
            HDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(CommentFilter.Unresolved, unresolvedCount, filter) { filter = it }
                FilterChip(CommentFilter.Drafts, draftCount, filter) { filter = it }
                FilterChip(CommentFilter.Resolved, resolvedCount, filter) { filter = it }
                FilterChip(CommentFilter.All, all.size, filter) { filter = it }
            }
            HDivider()

            if (groupedByFile.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "no ${filter.label} comments",
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        color = RedlineColors.TextMute,
                    )
                }
            } else {
                LazyColumn {
                    groupedByFile.forEach { (fileShort, threads) ->
                        item(key = "h:$fileShort") {
                            FileHeader(name = fileShort, active = fileShort == activeFileShort)
                        }
                        items(threads, key = { "t:${it.thread.side}:${it.thread.line}:${it.isDraft}" }) { ft ->
                            ThreadCard(ft) {
                                onJump(CommentJump(ft.file.short, ft.thread.line, ft.thread.side))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(value: CommentFilter, count: Int, current: CommentFilter, onSelect: (CommentFilter) -> Unit) {
    val active = value == current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) RedlineColors.Surface2 else Color.Transparent)
            .border(1.dp, if (active) RedlineColors.Border else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { onSelect(value) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            value.label,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = if (active) RedlineColors.Text else RedlineColors.TextDim,
        )
        Spacer(Modifier.width(5.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (active) RedlineColors.Bg else Color.Transparent)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                count.toString(),
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = RedlineColors.TextMute,
            )
        }
    }
}

@Composable
private fun FileHeader(name: String, active: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedlineColors.Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            name,
            fontFamily = JetBrainsMono,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp,
            color = if (active) RedlineColors.Accent else RedlineColors.TextDim,
        )
    }
}

@Composable
private fun ThreadCard(ft: FlatThread, onClick: () -> Unit) {
    val barColor = when {
        ft.isDraft -> RedlineColors.Accent
        ft.isResolved -> RedlineColors.Green
        else -> RedlineColors.Blue
    }
    val sidePrefix = if (ft.thread.side == CommentSide.Old) "−" else "+"
    val sideColor = if (ft.thread.side == CommentSide.Old) RedlineColors.Accent else RedlineColors.Green
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier
            .width(3.dp)
            .fillMaxHeight()
            .background(barColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(ft.authorAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ft.authorInitial,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(sideColor.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "$sidePrefix${ft.thread.line}",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = sideColor,
                    )
                }
                if (ft.isDraft) {
                    Spacer(Modifier.width(6.dp))
                    Pill("DRAFT", RedlineColors.Accent)
                }
                if (ft.isResolved) {
                    Spacer(Modifier.width(6.dp))
                    Pill("RESOLVED", RedlineColors.Green)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    ft.lastWhen,
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextMute,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                ft.previewBody,
                fontFamily = Inter,
                fontSize = 12.sp,
                color = RedlineColors.Text,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ft.replyCount > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "${ft.replyCount} repl${if (ft.replyCount == 1) "y" else "ies"} in thread",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextMute,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            color = color,
        )
    }
}

@Composable
private fun HDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RedlineColors.BorderSoft)
    )
}

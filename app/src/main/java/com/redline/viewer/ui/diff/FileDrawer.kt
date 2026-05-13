package com.redline.viewer.ui.diff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.ChangedFile
import com.redline.viewer.data.FileStatus
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.Thread
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun FileDrawerOverlay(
    open: Boolean,
    files: List<ChangedFile>,
    fileIdx: Int,
    viewed: Set<String>,
    commentsByPath: Map<String, List<Thread>>,
    pendingComments: List<PendingComment>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onClose)
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .fillMaxHeight()
                    .background(RedlineColors.Bg)
                    .border(width = 1.dp, color = RedlineColors.Border)
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                FileDrawerContent(
                    files = files,
                    fileIdx = fileIdx,
                    viewed = viewed,
                    commentsByPath = commentsByPath,
                    pendingComments = pendingComments,
                    onPick = { idx -> onPick(idx); onClose() },
                )
            }
        }
    }
}

@Composable
private fun FileDrawerContent(
    files: List<ChangedFile>,
    fileIdx: Int,
    viewed: Set<String>,
    commentsByPath: Map<String, List<Thread>>,
    pendingComments: List<PendingComment>,
    onPick: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "files",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = RedlineColors.Text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${files.size}",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = RedlineColors.TextMute,
            )
            Spacer(Modifier.weight(1f))
            val viewedCount = files.count { viewed.contains(it.path) }
            Text(
                "$viewedCount/${files.size} viewed",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = RedlineColors.TextMute,
            )
        }
        HDivider()
        LazyColumn {
            items(files.withIndex().toList(), key = { it.value.path }) { (idx, file) ->
                FileDrawerRow(
                    file = file,
                    active = idx == fileIdx,
                    viewed = viewed.contains(file.path),
                    threadCount = commentsByPath[file.short]?.size ?: 0,
                    draftCount = pendingComments.count { it.fileShort == file.short },
                    onClick = { onPick(idx) },
                )
            }
        }
    }
}

@Composable
private fun FileDrawerRow(
    file: ChangedFile,
    active: Boolean,
    viewed: Boolean,
    threadCount: Int,
    draftCount: Int,
    onClick: () -> Unit,
) {
    val statusColor = when (file.status) {
        FileStatus.Added -> RedlineColors.Green
        FileStatus.Deleted -> RedlineColors.Accent
        FileStatus.Modified -> RedlineColors.Yellow
    }
    val statusLabel = when (file.status) {
        FileStatus.Added -> "A"
        FileStatus.Deleted -> "D"
        FileStatus.Modified -> "M"
    }
    val dir = file.path.substringBeforeLast('/', missingDelimiterValue = "")
    val name = file.path.substringAfterLast('/')
    val nameColor = if (viewed) RedlineColors.TextMute else RedlineColors.Text
    val decoration = if (viewed) TextDecoration.LineThrough else null

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier
            .width(3.dp)
            .fillMaxHeight()
            .background(if (active) RedlineColors.Accent else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(RedlineColors.Surface2)
                    .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    statusLabel,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = statusColor,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (dir.isNotEmpty()) {
                    Text(
                        "$dir/",
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        color = RedlineColors.TextMute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    name,
                    fontFamily = JetBrainsMono,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp,
                    color = nameColor,
                    textDecoration = decoration,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (file.additions > 0) {
                        Text("+${file.additions}", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.Green)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (file.deletions > 0) {
                        Text("−${file.deletions}", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.Accent)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (threadCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0x1F58A6FF))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "💬 $threadCount",
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.Blue,
                        )
                    }
                }
                if (draftCount > 0) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(RedlineColors.Accent.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "+$draftCount draft",
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.Accent,
                        )
                    }
                }
            }
        }
    }
    HDivider()
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

@Suppress("UNUSED")
private val _inter = Inter

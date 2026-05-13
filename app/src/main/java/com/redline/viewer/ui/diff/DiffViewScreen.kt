package com.redline.viewer.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.Loadable
import com.redline.viewer.data.PullDetailBundle
import com.redline.viewer.data.ChangedFile
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.github.GhPull
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors
import kotlinx.coroutines.launch

data class CommentRequest(
    val side: CommentSide,
    val line: Int,
    val text: String,
    val fileShort: String,
)

@Composable
fun DiffViewScreen(
    pull: GhPull,
    detail: Loadable<PullDetailBundle>,
    fileIdx: Int,
    setFileIdx: (Int) -> Unit,
    onBack: () -> Unit,
    onCommentLine: (CommentRequest) -> Unit,
    onReview: () -> Unit,
    pendingComments: List<PendingComment>,
    onRetry: () -> Unit,
    viewedFiles: Set<String>,
    onToggleViewed: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        when (detail) {
            Loadable.Idle, Loadable.Loading -> {
                MinimalHeader(pull = pull, onBack = onBack, subtitle = "loading…")
                Center("loading diff…")
            }
            is Loadable.Err -> {
                MinimalHeader(pull = pull, onBack = onBack, subtitle = "error")
                ErrorBlock(detail.message, onRetry)
            }
            is Loadable.Ok -> {
                val bundle = detail.value
                if (bundle.files.isEmpty()) {
                    MinimalHeader(pull = pull, onBack = onBack, subtitle = "no files")
                    Center("this pull request has no files")
                } else {
                    val safeIdx = fileIdx.coerceIn(0, bundle.files.lastIndex)
                    Loaded(
                        pull = pull,
                        bundle = bundle,
                        fileIdx = safeIdx,
                        setFileIdx = setFileIdx,
                        onBack = onBack,
                        onCommentLine = onCommentLine,
                        onReview = onReview,
                        pendingComments = pendingComments,
                        viewedFiles = viewedFiles,
                        onToggleViewed = onToggleViewed,
                    )
                }
            }
        }
    }
}

@Composable
private fun Loaded(
    pull: GhPull,
    bundle: PullDetailBundle,
    fileIdx: Int,
    setFileIdx: (Int) -> Unit,
    onBack: () -> Unit,
    onCommentLine: (CommentRequest) -> Unit,
    onReview: () -> Unit,
    pendingComments: List<PendingComment>,
    viewedFiles: Set<String>,
    onToggleViewed: (String) -> Unit,
) {
    val file = bundle.files[fileIdx]
    val diff = bundle.diffs[file.short].orEmpty()
    val threads = bundle.commentsByPath[file.short].orEmpty()
    val pending = pendingComments.filter { it.fileShort == file.short }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var scale by remember { mutableFloatStateOf(1f) }

    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    var drawerOpen by remember { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var jumpTarget by remember { mutableStateOf<JumpTarget?>(null) }

    val totalUnresolved = bundle.commentsByPath.values.flatten()
        .count { thread -> thread.comments.none { it.resolved } }
    val totalUnresolvedAndDraft = totalUnresolved + pendingComments.size

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DiffHeader(
                pull = pull,
                file = file,
                fileIdx = fileIdx,
                fileCount = bundle.files.size,
                additions = file.additions,
                deletions = file.deletions,
                commentBadge = totalUnresolvedAndDraft,
                viewed = viewedFiles.contains(file.path),
                onBack = onBack,
                onOpenDrawer = { drawerOpen = true },
                onOpenComments = { commentsOpen = true },
                onToggleViewed = { onToggleViewed(file.path) },
            )
            ModeIndicator(
                mode = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 2,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> CodePane(
                            side = CommentSide.Old, diff = diff, threads = threads,
                            pending = pending, fileShort = file.short,
                            scale = scale, vScroll = vScroll, hScroll = hScroll,
                            onScaleChange = { scale = it },
                            onCommentLine = onCommentLine,
                            label = "old", sublabel = "── ${pull.base.ref}",
                            jumpTarget = jumpTarget?.takeIf { it.side == CommentSide.Old },
                            onJumpConsumed = { jumpTarget = null },
                        )
                        1 -> SplitPane(
                            diff = diff, threads = threads, pending = pending,
                            fileShort = file.short, scale = scale,
                            vScroll = vScroll, hScroll = hScroll,
                            splitFraction = splitFraction,
                            onSplitChange = { splitFraction = it.coerceIn(0.15f, 0.85f) },
                            onScaleChange = { scale = it },
                            onCommentLine = onCommentLine,
                            oldSublabel = "── ${pull.base.ref}",
                            newSublabel = "── ${pull.head.ref}",
                            jumpTarget = jumpTarget,
                            onJumpConsumed = { jumpTarget = null },
                        )
                        2 -> CodePane(
                            side = CommentSide.New, diff = diff, threads = threads,
                            pending = pending, fileShort = file.short,
                            scale = scale, vScroll = vScroll, hScroll = hScroll,
                            onScaleChange = { scale = it },
                            onCommentLine = onCommentLine,
                            label = "new", sublabel = "── ${pull.head.ref}",
                            jumpTarget = jumpTarget?.takeIf { it.side == CommentSide.New },
                            onJumpConsumed = { jumpTarget = null },
                        )
                    }
                }

                // Floating prev/next chevrons (bottom-left)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChevronButton(
                        direction = ChevronDir.Prev,
                        enabled = fileIdx > 0,
                        onClick = { setFileIdx(fileIdx - 1) },
                    )
                    ChevronButton(
                        direction = ChevronDir.Next,
                        enabled = fileIdx < bundle.files.lastIndex,
                        onClick = { setFileIdx(fileIdx + 1) },
                    )
                }

                // Floating REVIEW pill (bottom-right)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(RedlineColors.Accent)
                        .clickable(onClick = onReview)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(13.dp).drawBehind {
                        val p = Path().apply {
                            moveTo(size.width * 0.15f, size.height * 0.5f)
                            lineTo(size.width * 0.4f, size.height * 0.75f)
                            lineTo(size.width * 0.9f, size.height * 0.25f)
                        }
                        drawPath(p, Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    })
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "REVIEW",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White,
                    )
                    if (pending.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                pending.size.toString(),
                                fontFamily = JetBrainsMono,
                                fontSize = 10.sp,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        FileDrawerOverlay(
            open = drawerOpen,
            files = bundle.files,
            fileIdx = fileIdx,
            viewed = viewedFiles,
            commentsByPath = bundle.commentsByPath,
            pendingComments = pendingComments,
            onPick = { setFileIdx(it) },
            onClose = { drawerOpen = false },
        )

        if (commentsOpen) {
            CommentsSheet(
                files = bundle.files,
                commentsByPath = bundle.commentsByPath,
                pendingComments = pendingComments,
                activeFileShort = file.short,
                onCancel = { commentsOpen = false },
                onJump = { jump ->
                    commentsOpen = false
                    val idx = bundle.files.indexOfFirst { it.short == jump.fileShort }
                    if (idx >= 0 && idx != fileIdx) setFileIdx(idx)
                    jumpTarget = JumpTarget(line = jump.line, side = jump.side)
                    // Auto-switch to split if the comment is on the opposite side
                    val newPage = if (jump.side == CommentSide.Old) 1 else 1
                    if (pagerState.currentPage != newPage) {
                        scope.launch { pagerState.animateScrollToPage(newPage) }
                    }
                },
            )
        }
    }
}

@Composable
private fun DiffHeader(
    pull: GhPull,
    file: ChangedFile,
    fileIdx: Int,
    fileCount: Int,
    additions: Int,
    deletions: Int,
    commentBadge: Int,
    viewed: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleViewed: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(onClick = onBack) {
            val p = Path().apply {
                moveTo(size.width * 0.6f, size.height * 0.3f)
                lineTo(size.width * 0.35f, size.height * 0.5f)
                lineTo(size.width * 0.6f, size.height * 0.7f)
            }
            drawPath(p, RedlineColors.Text, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        IconBox(onClick = onOpenDrawer) {
            // Hamburger
            val xPad = size.width * 0.25f
            val w = size.width - xPad * 2
            val y1 = size.height * 0.35f
            val y2 = size.height * 0.5f
            val y3 = size.height * 0.65f
            val stroke = 1.6.dp.toPx()
            drawLine(RedlineColors.Text, Offset(xPad, y1), Offset(xPad + w, y1), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(RedlineColors.Text, Offset(xPad, y2), Offset(xPad + w, y2), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(RedlineColors.Text, Offset(xPad, y3), Offset(xPad + w, y3), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    "#${pull.number}",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextMute,
                )
                Spacer(Modifier.width(6.dp))
                Text("·", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${fileIdx + 1}/$fileCount",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextDim,
                )
                Spacer(Modifier.width(6.dp))
                if (additions > 0) {
                    Text("+$additions", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.Green)
                    Spacer(Modifier.width(4.dp))
                }
                if (deletions > 0) {
                    Text("−$deletions", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.Accent)
                }
            }
            Text(
                file.short,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = if (viewed) RedlineColors.TextMute else RedlineColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // viewed checkbox
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onToggleViewed),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (viewed) RedlineColors.Green else Color.Transparent)
                    .drawBehind {
                        if (!viewed) {
                            // outline
                            val s = 1.2.dp.toPx()
                            drawLine(RedlineColors.TextDim, Offset(0f, 0f), Offset(size.width, 0f), s)
                            drawLine(RedlineColors.TextDim, Offset(size.width, 0f), Offset(size.width, size.height), s)
                            drawLine(RedlineColors.TextDim, Offset(size.width, size.height), Offset(0f, size.height), s)
                            drawLine(RedlineColors.TextDim, Offset(0f, size.height), Offset(0f, 0f), s)
                        } else {
                            val w = size.width
                            val h = size.height
                            val p = Path().apply {
                                moveTo(w * 0.2f, h * 0.55f)
                                lineTo(w * 0.42f, h * 0.75f)
                                lineTo(w * 0.82f, h * 0.3f)
                            }
                            drawPath(p, Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                        }
                    },
            )
        }
        // comments-jump button with badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onOpenComments),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(18.dp).drawBehind {
                val p = Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.18f)
                    lineTo(size.width * 0.86f, size.height * 0.18f)
                    lineTo(size.width * 0.86f, size.height * 0.65f)
                    lineTo(size.width * 0.45f, size.height * 0.65f)
                    lineTo(size.width * 0.25f, size.height * 0.85f)
                    lineTo(size.width * 0.25f, size.height * 0.65f)
                    lineTo(size.width * 0.14f, size.height * 0.65f)
                    close()
                }
                drawPath(p, RedlineColors.TextDim, style = Stroke(width = 1.4.dp.toPx()))
            })
            if (commentBadge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .clip(CircleShape)
                        .background(RedlineColors.Blue)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        commentBadge.coerceAtMost(99).toString(),
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
    HDivider()
}

@Composable
private fun MinimalHeader(pull: GhPull, onBack: () -> Unit, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(onClick = onBack) {
            val p = Path().apply {
                moveTo(size.width * 0.6f, size.height * 0.3f)
                lineTo(size.width * 0.35f, size.height * 0.5f)
                lineTo(size.width * 0.6f, size.height * 0.7f)
            }
            drawPath(p, RedlineColors.Text, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "#${pull.number}",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = RedlineColors.TextMute,
            )
            Text(
                subtitle,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                color = RedlineColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HDivider()
}

@Composable
private fun IconBox(
    onClick: () -> Unit,
    drawing: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
            .drawBehind(drawing),
    )
}

private enum class ChevronDir { Prev, Next }

@Composable
private fun ChevronButton(direction: ChevronDir, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) RedlineColors.Surface else RedlineColors.Surface.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                val color = if (enabled) RedlineColors.Text else RedlineColors.TextMute
                val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                val p = when (direction) {
                    ChevronDir.Prev -> Path().apply {
                        moveTo(size.width * 0.6f, size.height * 0.3f)
                        lineTo(size.width * 0.4f, size.height * 0.5f)
                        lineTo(size.width * 0.6f, size.height * 0.7f)
                    }
                    ChevronDir.Next -> Path().apply {
                        moveTo(size.width * 0.4f, size.height * 0.3f)
                        lineTo(size.width * 0.6f, size.height * 0.5f)
                        lineTo(size.width * 0.4f, size.height * 0.7f)
                    }
                }
                drawPath(p, color, style = stroke)
            },
    )
}

@Composable
private fun ModeIndicator(mode: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("old", "split", "new")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedlineColors.Bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("view:", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextDim)
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { i, label ->
                val active = i == mode
                val accent = when (i) {
                    0 -> RedlineColors.Accent
                    2 -> RedlineColors.Green
                    else -> RedlineColors.Text
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) RedlineColors.Surface2 else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        label,
                        fontFamily = JetBrainsMono,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 10.sp,
                        color = if (active) accent else RedlineColors.TextMute,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("swipe ↔", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
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

@Composable
private fun Center(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = JetBrainsMono, fontSize = 12.sp, color = RedlineColors.TextMute)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, fontFamily = JetBrainsMono, fontSize = 12.sp, color = RedlineColors.Accent)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(RedlineColors.Surface2)
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("retry", fontFamily = JetBrainsMono, fontSize = 12.sp, color = RedlineColors.TextDim)
        }
    }
}

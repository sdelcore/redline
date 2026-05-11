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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.redline.viewer.Loadable
import com.redline.viewer.PullDetailBundle
import com.redline.viewer.data.ChangedFile
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.FileStatus
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

    DiffHeader(pull = pull, file = file, onBack = onBack)
    FileTabs(files = bundle.files, fileIdx = fileIdx, setFileIdx = setFileIdx)
    ModeIndicator(
        mode = pagerState.currentPage,
        onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
    )

    Box(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> CodePane(
                    side = CommentSide.Old,
                    diff = diff,
                    threads = threads,
                    pending = pending,
                    fileShort = file.short,
                    scale = scale,
                    vScroll = vScroll,
                    hScroll = hScroll,
                    onScaleChange = { scale = it },
                    onCommentLine = onCommentLine,
                    label = "old",
                    sublabel = "── ${pull.base.ref}",
                )
                1 -> SplitPane(
                    diff = diff,
                    threads = threads,
                    pending = pending,
                    fileShort = file.short,
                    scale = scale,
                    vScroll = vScroll,
                    hScroll = hScroll,
                    splitFraction = splitFraction,
                    onSplitChange = { splitFraction = it.coerceIn(0.15f, 0.85f) },
                    onScaleChange = { scale = it },
                    onCommentLine = onCommentLine,
                    oldSublabel = "── ${pull.base.ref}",
                    newSublabel = "── ${pull.head.ref}",
                )
                2 -> CodePane(
                    side = CommentSide.New,
                    diff = diff,
                    threads = threads,
                    pending = pending,
                    fileShort = file.short,
                    scale = scale,
                    vScroll = vScroll,
                    hScroll = hScroll,
                    onScaleChange = { scale = it },
                    onCommentLine = onCommentLine,
                    label = "new",
                    sublabel = "── ${pull.head.ref}",
                )
            }
        }

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

@Composable
private fun DiffHeader(pull: GhPull, file: ChangedFile, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackBox(onBack)
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
                if (file.additions > 0) {
                    Text("+${file.additions}", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.Green)
                    Spacer(Modifier.width(4.dp))
                }
                if (file.deletions > 0) {
                    Text("−${file.deletions}", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.Accent)
                }
            }
            Text(
                file.short,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
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
private fun MinimalHeader(pull: GhPull, onBack: () -> Unit, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackBox(onBack)
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
private fun BackBox(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onBack)
            .drawBehind {
                val p = Path().apply {
                    moveTo(size.width * 0.6f, size.height * 0.3f)
                    lineTo(size.width * 0.35f, size.height * 0.5f)
                    lineTo(size.width * 0.6f, size.height * 0.7f)
                }
                drawPath(p, RedlineColors.Text, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
            },
    )
}

@Composable
private fun FileTabs(files: List<ChangedFile>, fileIdx: Int, setFileIdx: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(fileIdx) { state.animateScrollToItem(fileIdx) }

    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .background(RedlineColors.Surface),
    ) {
        items(files.withIndex().toList(), key = { it.value.path }) { (idx, f) ->
            val active = idx == fileIdx
            val statusColor = when (f.status) {
                FileStatus.Added -> RedlineColors.Green
                FileStatus.Deleted -> RedlineColors.Accent
                FileStatus.Modified -> RedlineColors.Yellow
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { setFileIdx(idx) }
                    .background(if (active) RedlineColors.Bg else Color.Transparent)
                    .drawBehind {
                        if (active) {
                            drawLine(
                                color = RedlineColors.Accent,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    f.short,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = if (active) RedlineColors.Text else RedlineColors.TextDim,
                )
            }
        }
    }
    HDivider()
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

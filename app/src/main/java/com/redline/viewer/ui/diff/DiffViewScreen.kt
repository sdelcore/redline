package com.redline.viewer.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.ChangedFile
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.DiffRow
import com.redline.viewer.data.DiffRowType
import com.redline.viewer.data.FileStatus
import com.redline.viewer.data.PR
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.SampleData
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CommentRequest(
    val side: CommentSide,
    val line: Int,
    val text: String,
    val fileShort: String,
)

@Composable
fun DiffViewScreen(
    pr: PR,
    fileIdx: Int,
    setFileIdx: (Int) -> Unit,
    onBack: () -> Unit,
    onCommentLine: (CommentRequest) -> Unit,
    onReview: () -> Unit,
    pendingComments: List<PendingComment>,
) {
    val files = SampleData.Files
    val file = files[fileIdx]
    val diff = SampleData.Diffs[file.short] ?: emptyList()
    val threads = SampleData.Comments[file.short].orEmpty()
    val pending = pendingComments.filter { it.fileShort == file.short }

    // Shared scroll state across all three panes
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var scale by remember { mutableFloatStateOf(1f) }

    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()

    // Split ratio for split mode (top pane fraction)
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        DiffHeader(pr = pr, file = file, onBack = onBack)
        FileTabs(files = files, fileIdx = fileIdx, setFileIdx = setFileIdx)
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
                        sublabel = "── ${pr.base}",
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
                        oldSublabel = "── ${pr.base}",
                        newSublabel = "── ${pr.branch}",
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
                        sublabel = "── ${pr.branch}",
                    )
                }
            }

            // Floating REVIEW pill
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
}

@Composable
private fun DiffHeader(pr: PR, file: ChangedFile, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    "#${pr.id}",
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

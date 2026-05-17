package com.redline.viewer.ui.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
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
import com.redline.viewer.data.Check
import com.redline.viewer.data.CheckSummary
import com.redline.viewer.data.FileStatus
import com.redline.viewer.data.github.GhPull
import com.redline.viewer.ui.components.CheckIcon
import com.redline.viewer.ui.components.LoadableContent
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun FileBrowserScreen(
    pull: GhPull,
    detail: Loadable<PullDetailBundle>,
    onOpenFile: (ChangedFile, Int) -> Unit,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onRetry: () -> Unit,
    onEditMetadata: (MetadataKind) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(pull = pull, onBack = onBack)

            LoadableContent(
                state = detail,
                onRetry = onRetry,
                loadingMessage = "loading pull request…",
            ) { bundle ->
                Body(
                    bundle = bundle,
                    onOpenFile = onOpenFile,
                    onReview = onReview,
                    onEditMetadata = onEditMetadata,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(RedlineColors.Accent)
                .clickable(onClick = onReview)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(14.dp).drawBehind {
                val p = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.5f)
                    lineTo(size.width * 0.4f, size.height * 0.75f)
                    lineTo(size.width * 0.9f, size.height * 0.25f)
                }
                drawPath(p, Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            })
            Spacer(Modifier.width(8.dp))
            Text(
                "review",
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun Body(
    bundle: PullDetailBundle,
    onOpenFile: (ChangedFile, Int) -> Unit,
    onReview: () -> Unit,
    onEditMetadata: (MetadataKind) -> Unit,
) {
    val pr = bundle.pull
    val additions = pr.additions ?: bundle.files.sumOf { it.additions }
    val deletions = pr.deletions ?: bundle.files.sumOf { it.deletions }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item { MergeBlock(checks = bundle.checks, onReview = onReview) }
        item {
            MetadataBlock(
                assignees = pr.assignees,
                reviewers = pr.requested_reviewers,
                labels = pr.labels,
                milestone = pr.milestone,
                onEdit = onEditMetadata,
            )
        }
        item { ChecksBlock(checks = bundle.checks) }
        item { ConversationBlock(items = bundle.conversation) }
        item {
            SummaryBar(
                fileCount = bundle.files.size,
                additions = additions,
                deletions = deletions,
            )
        }
        items(bundle.files.withIndex().toList(), key = { it.value.path }) { (idx, file) ->
            val threadCount = bundle.commentsByPath[file.short]?.sumOf { it.comments.size } ?: 0
            FileRow(file = file, threadCount = threadCount, onClick = { onOpenFile(file, idx) })
        }
    }
}

@Composable
private fun Header(pull: GhPull, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackIcon(onBack = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "#${pull.number} · ${pull.user?.login ?: "?"}",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = RedlineColors.TextMute,
            )
            Text(
                pull.title,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = RedlineColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Divider()
}

@Composable
private fun BackIcon(onBack: () -> Unit) {
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
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RedlineColors.BorderSoft)
    )
}

@Composable
private fun MergeBlock(checks: List<Check>, onReview: () -> Unit) {
    val counts = checks.groupingBy { it.status }.eachCount()
    val canMerge = (counts[CheckSummary.Fail] ?: 0) == 0 && (counts[CheckSummary.Pending] ?: 0) == 0
    val statusColor = if (canMerge) RedlineColors.Green else RedlineColors.Yellow
    val statusBg = if (canMerge) Color(0x1443A047) else Color(0x14F59E0B)
    val statusBorder = if (canMerge) Color(0x4D43A047) else Color(0x4DF59E0B)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(statusBg)
                .border(1.dp, statusBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .drawBehind {
                        drawCircle(statusColor.copy(alpha = 0.5f), radius = size.minDimension)
                        drawCircle(statusColor, radius = size.minDimension / 2f)
                    },
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        checks.isEmpty() -> "no required checks"
                        canMerge -> "ready to merge"
                        else -> "waiting on checks"
                    },
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = statusColor,
                )
                if (checks.isNotEmpty()) {
                    val pass = counts[CheckSummary.Pass] ?: 0
                    val fail = counts[CheckSummary.Fail] ?: 0
                    val pending = counts[CheckSummary.Pending] ?: 0
                    val parts = buildList {
                        if (pass > 0) add("$pass passing")
                        if (fail > 0) add("$fail failing")
                        if (pending > 0) add("$pending running")
                    }
                    Text(
                        parts.joinToString(" · "),
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        color = RedlineColors.TextMute,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = "review",
                background = RedlineColors.Surface2,
                border = RedlineColors.Border,
                contentColor = RedlineColors.Text,
                modifier = Modifier.weight(1f),
                onClick = onReview,
            )
            ActionButton(
                text = "merge",
                background = if (canMerge) RedlineColors.Green else RedlineColors.Surface2,
                border = if (canMerge) RedlineColors.Green else RedlineColors.Border,
                contentColor = if (canMerge) RedlineColors.Bg else RedlineColors.TextMute,
                modifier = Modifier.weight(1f),
                onClick = { /* not wired yet */ },
                enabled = canMerge,
            )
        }
    }
    Divider()
}

@Composable
private fun ActionButton(
    text: String,
    background: Color,
    border: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = contentColor,
        )
    }
}

@Composable
private fun ChecksBlock(checks: List<Check>) {
    if (checks.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val counts = checks.groupingBy { it.status }.eachCount()
    val fail = counts[CheckSummary.Fail] ?: 0
    val pass = counts[CheckSummary.Pass] ?: 0
    val pending = counts[CheckSummary.Pending] ?: 0
    val summary = when {
        fail > 0 -> "$fail failing · $pass passing"
        pending > 0 -> "$pending running · $pass passing"
        else -> "all $pass checks passed"
    }
    val summaryColor = when {
        fail > 0 -> RedlineColors.Accent
        pending > 0 -> RedlineColors.Yellow
        else -> RedlineColors.Green
    }
    val overallStatus = when {
        fail > 0 -> CheckSummary.Fail
        pending > 0 -> CheckSummary.Pending
        else -> CheckSummary.Pass
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .background(RedlineColors.Surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckIcon(status = overallStatus, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "pipeline",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = RedlineColors.Text,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    summary,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = summaryColor,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(RedlineColors.Bg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "${checks.size} jobs",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextMute,
                )
            }
            Spacer(Modifier.width(8.dp))
            Chevron(rotated = expanded)
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RedlineColors.Bg)
            ) {
                Divider()
                checks.forEach { check -> CheckRow(check) }
            }
        }
    }
    Divider()
}

@Composable
private fun Chevron(rotated: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .rotate(if (rotated) 180f else 0f)
            .drawBehind {
                val p = Path().apply {
                    moveTo(size.width * 0.25f, size.height * 0.4f)
                    lineTo(size.width * 0.5f, size.height * 0.65f)
                    lineTo(size.width * 0.75f, size.height * 0.4f)
                }
                drawPath(p, RedlineColors.TextDim, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
            },
    )
}

@Composable
private fun CheckRow(check: Check) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckIcon(status = check.status, size = 14.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        check.name,
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        color = RedlineColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (check.required) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(RedlineColors.Surface2)
                                .border(1.dp, RedlineColors.Border, RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "required",
                                fontFamily = JetBrainsMono,
                                fontSize = 9.sp,
                                color = RedlineColors.TextDim,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(check.workflow, fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                    Spacer(Modifier.width(6.dp))
                    Text("·", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                    Spacer(Modifier.width(6.dp))
                    Text(check.duration, fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                    check.delta?.let {
                        Spacer(Modifier.width(6.dp))
                        Text("·", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                        Spacer(Modifier.width(6.dp))
                        Text(it, fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.Yellow)
                    }
                }
                check.failNote?.let { note ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .background(Color(0x14E53935))
                            .drawBehind {
                                drawLine(
                                    color = RedlineColors.Accent,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            note,
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.Accent,
                        )
                    }
                }
            }
        }
        Divider()
    }
}

@Composable
private fun SummaryBar(fileCount: Int, additions: Int, deletions: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedlineColors.Surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$fileCount files changed",
            fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.TextDim)
        Spacer(Modifier.width(12.dp))
        Text("+$additions", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.Green)
        Spacer(Modifier.width(12.dp))
        Text("−$deletions", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.Accent)
    }
    Divider()
}

@Composable
private fun FileRow(file: ChangedFile, threadCount: Int, onClick: () -> Unit) {
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
    val total = (file.additions + file.deletions).coerceAtLeast(1)
    val green = file.additions * 5 / total

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (dir.isNotEmpty()) {
                Text(
                    "$dir/",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                name,
                fontFamily = JetBrainsMono,
                fontSize = 13.sp,
                color = RedlineColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.width(8.dp))
                }
                if (file.additions > 0) {
                    Text("+${file.additions}", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.Green)
                    Spacer(Modifier.width(8.dp))
                }
                if (file.deletions > 0) {
                    Text("−${file.deletions}", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.Accent)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                for (i in 0 until 5) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                when {
                                    i < green -> RedlineColors.Green
                                    file.deletions > 0 -> RedlineColors.Accent
                                    else -> RedlineColors.Border
                                }
                            ),
                    )
                }
            }
        }
    }
    Divider()
}


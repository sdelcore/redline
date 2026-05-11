package com.redline.viewer.ui.prlist

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.CheckSummary
import com.redline.viewer.data.PR
import com.redline.viewer.data.Repo
import com.redline.viewer.data.SampleData
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun PRListScreen(
    repoIdx: Int,
    onSelectRepo: (Int) -> Unit,
    onOpenPR: (PR) -> Unit,
) {
    val repos = SampleData.Repos
    val repo = repos[repoIdx]
    val prs = remember(repo.id) { SampleData.PRs.filter { it.repo == repo.id } }
    var filter by remember { mutableStateOf("open") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Header(repo = repo)
        FilterTabs(filter = filter, prCount = prs.size, onFilter = { filter = it })

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(prs) { pr -> PRCard(pr, onOpen = { onOpenPR(pr) }) }
        }

        BottomRepoNav(repos = repos, activeIdx = repoIdx, onSelect = onSelectRepo)
    }
}

@Composable
private fun Header(repo: Repo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.dp, RedlineColors.BorderSoft)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(RedlineColors.Surface2)
                .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                .drawBehind {
                    val pad = size.width * 0.18f
                    drawLine(
                        color = RedlineColors.Accent,
                        start = Offset(pad, size.height - pad),
                        end = Offset(size.width - pad, pad),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                },
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${repo.owner}/",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = RedlineColors.TextMute,
            )
            Text(
                repo.name,
                fontFamily = JetBrainsMono,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = RedlineColors.Text,
            )
        }
    }
    BorderRow()
}

@Composable
private fun BorderRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RedlineColors.BorderSoft)
    )
}

@Composable
private fun FilterTabs(filter: String, prCount: Int, onFilter: (String) -> Unit) {
    val tabs = listOf(
        "open" to prCount,
        "review" to 2,
        "mine" to 1,
        "closed" to 84,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((label, count) in tabs) {
            val active = filter == label
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) RedlineColors.Surface2 else Color.Transparent)
                    .clickable { onFilter(label) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    color = if (active) RedlineColors.Text else RedlineColors.TextDim,
                )
                Spacer(Modifier.width(6.dp))
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
    }
    BorderRow()
}

@Composable
private fun PRCard(pr: PR, onOpen: () -> Unit) {
    val checkColor = when (pr.checks) {
        CheckSummary.Pass -> RedlineColors.Green
        CheckSummary.Fail -> RedlineColors.Accent
        CheckSummary.Pending -> RedlineColors.Yellow
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(pr.avatar),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    pr.author.take(1).uppercase(),
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // top meta row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${pr.id}",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = RedlineColors.TextMute,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (pr.draft) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(RedlineColors.Surface2)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "DRAFT",
                                fontFamily = JetBrainsMono,
                                fontSize = 9.sp,
                                color = RedlineColors.TextDim,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    GlowDot(color = checkColor)
                    Spacer(Modifier.weight(1f))
                    Text(
                        pr.opened,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = RedlineColors.TextMute,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    pr.title,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = RedlineColors.Text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                // branch row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BranchChip(pr.branch, color = RedlineColors.Blue)
                    Spacer(Modifier.width(6.dp))
                    Text("→", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.TextMute)
                    Spacer(Modifier.width(6.dp))
                    BranchChip(pr.base, color = RedlineColors.TextDim)
                }
                Spacer(Modifier.height(8.dp))
                // stats row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "[${pr.files}]",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = RedlineColors.TextMute,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("+${pr.additions}", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.Green)
                    Spacer(Modifier.width(14.dp))
                    Text("−${pr.deletions}", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.Accent)
                    Spacer(Modifier.weight(1f))
                    if (pr.comments > 0) {
                        Text(
                            "💬 ${pr.comments}",
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = RedlineColors.TextMute,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    RatioBar(pr.additions, pr.deletions)
                }
            }
        }
    }
    BorderRow()
}

@Composable
private fun GlowDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .drawBehind {
                drawCircle(
                    color = color.copy(alpha = 0.5f),
                    radius = size.minDimension,
                )
                drawCircle(color = color, radius = size.minDimension / 2f)
            },
    )
}

@Composable
private fun BranchChip(name: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(RedlineColors.Surface2)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            name,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = color,
        )
    }
}

@Composable
private fun RatioBar(additions: Int, deletions: Int) {
    val total = (additions + deletions).coerceAtLeast(1)
    val green = (additions * 5 / total)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 5) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        when {
                            i < green -> RedlineColors.Green
                            deletions > 0 -> RedlineColors.Accent
                            else -> RedlineColors.Border
                        }
                    ),
            )
        }
    }
}

@Composable
private fun BottomRepoNav(repos: List<Repo>, activeIdx: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RedlineColors.Border)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedlineColors.Surface),
    ) {
        repos.forEachIndexed { i, r ->
            val active = i == activeIdx
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(i) }
                    .drawBehind {
                        if (active) {
                            drawLine(
                                color = RedlineColors.Accent,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${r.owner}/",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = if (active) RedlineColors.TextDim else RedlineColors.TextMute,
                )
                Text(
                    r.name,
                    fontFamily = JetBrainsMono,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.sp,
                    color = if (active) RedlineColors.Text else RedlineColors.TextMute,
                )
            }
        }
    }
}


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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.Loadable
import com.redline.viewer.data.github.GhPull
import com.redline.viewer.data.github.GhRepo
import com.redline.viewer.data.timeAgo
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun PRListScreen(
    repo: GhRepo,
    pulls: Loadable<List<GhPull>>,
    onBack: () -> Unit,
    onOpenPR: (GhPull) -> Unit,
    onRetry: () -> Unit,
) {
    var filter by remember { mutableStateOf("open") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Header(repo = repo, onBack = onBack)
        FilterTabs(
            filter = filter,
            prCount = (pulls as? Loadable.Ok)?.value?.size ?: 0,
            onFilter = { filter = it },
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (pulls) {
                Loadable.Idle, Loadable.Loading -> Center("loading pull requests…")
                is Loadable.Err -> ErrorBlock(pulls.message, onRetry)
                is Loadable.Ok -> if (pulls.value.isEmpty()) {
                    Center("no open pull requests")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(pulls.value, key = { it.number }) { pr ->
                            PRCard(pr = pr, onOpen = { onOpenPR(pr) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(repo: GhRepo, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackIcon(onClick = onBack)
        Spacer(Modifier.width(2.dp))
        // mini red-slash badge — tap returns to repos
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(RedlineColors.Surface2)
                .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                .clickable(onClick = onBack)
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
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onBack),
        ) {
            Text(
                "${repo.owner.login}/",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = RedlineColors.TextMute,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    repo.name,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = RedlineColors.Text,
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.size(10.dp).drawBehind {
                    val p = Path().apply {
                        moveTo(size.width * 0.3f, size.height * 0.2f)
                        lineTo(size.width * 0.6f, size.height * 0.5f)
                        lineTo(size.width * 0.3f, size.height * 0.8f)
                    }
                    drawPath(p, RedlineColors.TextMute, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                })
            }
        }
    }
    HDivider()
}

@Composable
private fun BackIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
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
private fun FilterTabs(filter: String, prCount: Int, onFilter: (String) -> Unit) {
    val tabs = listOf(
        "open" to prCount,
        "review" to 0,
        "mine" to 0,
        "closed" to 0,
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
    HDivider()
}

@Composable
private fun PRCard(pr: GhPull, onOpen: () -> Unit) {
    val author = pr.user?.login.orEmpty()
    val avatarColor = avatarColorFor(author)
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
                    .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    author.take(1).uppercase().ifEmpty { "?" },
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${pr.number}",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = RedlineColors.TextMute,
                    )
                    if (pr.draft) {
                        Spacer(Modifier.width(8.dp))
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
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        timeAgo(pr.updated_at),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BranchChip(pr.head.ref, color = RedlineColors.Blue)
                    Spacer(Modifier.width(6.dp))
                    Text("→", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.TextMute)
                    Spacer(Modifier.width(6.dp))
                    BranchChip(pr.base.ref, color = RedlineColors.TextDim)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@$author",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = RedlineColors.TextMute,
                    )
                    if ((pr.review_comments + pr.comments) > 0) {
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "💬 ${pr.review_comments + pr.comments}",
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = RedlineColors.TextMute,
                        )
                    }
                }
            }
        }
    }
    HDivider()
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
private fun Center(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
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
                .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("retry", fontFamily = JetBrainsMono, fontSize = 12.sp, color = RedlineColors.TextDim)
        }
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

private fun avatarColorFor(login: String): Color {
    if (login.isEmpty()) return Color(0xFF6E7681)
    val palette = listOf(
        0xFFF97316, 0xFF22D3EE, 0xFF3B82F6, 0xFFA855F7, 0xFFEC4899,
        0xFF14B8A6, 0xFFFBBF24, 0xFFEF4444, 0xFF8B5CF6, 0xFF10B981,
    )
    return Color(palette[(login.hashCode().toUInt().toInt() and 0x7FFFFFFF) % palette.size])
}

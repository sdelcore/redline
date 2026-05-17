package com.redline.viewer.ui.repos

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.Loadable
import com.redline.viewer.data.avatarColorFor
import com.redline.viewer.data.github.GhPullSearchItem
import com.redline.viewer.data.github.GhRepo
import com.redline.viewer.data.github.GhUser
import com.redline.viewer.data.languageColor
import com.redline.viewer.data.timeAgo
import com.redline.viewer.ui.components.LoadableContent
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun RepoPickerScreen(
    viewer: Loadable<GhUser>,
    repos: Loadable<List<GhRepo>>,
    searchedPulls: Loadable<List<GhPullSearchItem>>,
    resolvingPullNumber: Int?,
    onPickRepo: (GhRepo) -> Unit,
    onPickSearchedPull: (GhPullSearchItem) -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onLoadPulls: () -> Unit,
    onRetryPulls: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(RepoScope.All) }
    var topTab by remember { mutableStateOf(TopTab.Repos) }
    val viewerLogin = (viewer as? Loadable.Ok)?.value?.login

    LaunchedEffect(topTab) {
        if (topTab == TopTab.Pulls) onLoadPulls()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Header(viewer = viewer, onSignOut = onSignOut)
        TopTabs(
            tab = topTab,
            reposCount = (repos as? Loadable.Ok)?.value?.size,
            pullsCount = (searchedPulls as? Loadable.Ok)?.value?.size,
            onSelect = { topTab = it },
        )
        when (topTab) {
            TopTab.Repos -> {
                SearchBar(
                    query = query,
                    count = (repos as? Loadable.Ok)?.value?.size,
                    onChange = { query = it },
                )
                if (viewerLogin != null) {
                    ScopeTabs(scope = scope, viewerLogin = viewerLogin, onSelect = { scope = it })
                }

                LoadableContent(
                    state = repos,
                    onRetry = onRetry,
                    loadingMessage = "loading repos…",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { list ->
                    val filtered = list.filter { r ->
                        val matches = query.isBlank() || "${r.owner.login}/${r.name}"
                            .contains(query, ignoreCase = true)
                        val inScope = when (scope) {
                            RepoScope.All -> true
                            RepoScope.Mine -> viewerLogin != null && r.owner.login == viewerLogin
                        }
                        matches && inScope
                    }
                    if (filtered.isEmpty()) {
                        StatusRow(if (query.isBlank()) "no repos" else "no repos match \"$query\"")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp)) {
                            item {
                                SectionLabel(
                                    label = if (scope == RepoScope.Mine) "your repos" else "all repos",
                                    count = filtered.size,
                                )
                            }
                            items(filtered, key = { it.id }) { r ->
                                RepoRow(r, onClick = { onPickRepo(r) })
                            }
                        }
                    }
                }
            }
            TopTab.Pulls -> {
                LoadableContent(
                    state = searchedPulls,
                    onRetry = onRetryPulls,
                    loadingMessage = "loading open PRs…",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { list ->
                    if (list.isEmpty()) {
                        StatusRow("no open PRs involving you")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 60.dp)) {
                            item { SectionLabel(label = "open PRs", count = list.size) }
                            items(list, key = { it.number.toLong() * 1_000_003 + it.repository_url.hashCode() }) { pr ->
                                SearchedPullRow(
                                    pr = pr,
                                    busy = resolvingPullNumber == pr.number,
                                    onClick = { onPickSearchedPull(pr) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class RepoScope { All, Mine }
private enum class TopTab { Repos, Pulls }

@Composable
private fun TopTabs(
    tab: TopTab,
    reposCount: Int?,
    pullsCount: Int?,
    onSelect: (TopTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TopTabPill("repos", reposCount, tab == TopTab.Repos) { onSelect(TopTab.Repos) }
        TopTabPill("PRs", pullsCount, tab == TopTab.Pulls) { onSelect(TopTab.Pulls) }
    }
    HDivider()
}

@Composable
private fun TopTabPill(label: String, count: Int?, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) RedlineColors.Surface2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = JetBrainsMono,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.sp,
            color = if (active) RedlineColors.Text else RedlineColors.TextDim,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
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

@Composable
private fun SearchedPullRow(pr: GhPullSearchItem, busy: Boolean, onClick: () -> Unit) {
    val author = pr.user?.login.orEmpty()
    val repoLabel = pr.repository_url.substringAfter("/repos/").trimEnd('/')
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(avatarColorFor(author)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    author.take(1).uppercase().ifEmpty { "?" },
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repoLabel,
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = RedlineColors.TextMute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("·", fontFamily = JetBrainsMono, fontSize = 11.sp, color = RedlineColors.TextMute)
                    Spacer(Modifier.width(6.dp))
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
                                .padding(horizontal = 5.dp, vertical = 1.dp),
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
                        fontSize = 10.sp,
                        color = RedlineColors.TextMute,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    pr.title,
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (busy) RedlineColors.TextMute else RedlineColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@$author",
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        color = RedlineColors.TextMute,
                    )
                    if (pr.comments > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "💬 ${pr.comments}",
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.TextMute,
                        )
                    }
                    if (busy) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "opening…",
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.Blue,
                        )
                    }
                }
            }
        }
    }
    HDivider()
}

@Composable
private fun Header(viewer: Loadable<GhUser>, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val initial = (viewer as? Loadable.Ok)?.value?.login?.firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(RedlineColors.Accent.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "signed in as",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = RedlineColors.TextMute,
            )
            val login = when (viewer) {
                is Loadable.Ok -> "@${viewer.value.login}"
                Loadable.Loading -> "loading…"
                is Loadable.Err -> "error"
                Loadable.Idle -> "—"
            }
            Text(
                login,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = RedlineColors.Text,
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSignOut)
                .drawBehind {
                    val p = Path().apply {
                        moveTo(size.width * 0.4f, size.height * 0.3f)
                        lineTo(size.width * 0.25f, size.height * 0.3f)
                        lineTo(size.width * 0.25f, size.height * 0.7f)
                        lineTo(size.width * 0.4f, size.height * 0.7f)
                    }
                    drawPath(p, RedlineColors.TextDim, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                    val arrow = Path().apply {
                        moveTo(size.width * 0.55f, size.height * 0.4f)
                        lineTo(size.width * 0.75f, size.height * 0.5f)
                        lineTo(size.width * 0.55f, size.height * 0.6f)
                    }
                    drawPath(arrow, RedlineColors.TextDim, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                    drawLine(
                        color = RedlineColors.TextDim,
                        start = Offset(size.width * 0.35f, size.height * 0.5f),
                        end = Offset(size.width * 0.75f, size.height * 0.5f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                },
        )
    }
    HDivider()
}

@Composable
private fun SearchBar(query: String, count: Int?, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(RedlineColors.Surface)
                .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // search glass icon
            Box(modifier = Modifier.size(13.dp).drawBehind {
                drawCircle(
                    color = Color.Transparent,
                )
                drawArc(
                    color = RedlineColors.TextMute,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = 1.5.dp.toPx()),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.7f),
                )
                drawLine(
                    color = RedlineColors.TextMute,
                    start = Offset(size.width * 0.65f, size.height * 0.65f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            })
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "find a repo…",
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        color = RedlineColors.TextMute,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        color = RedlineColors.Text,
                    ),
                    cursorBrush = SolidColor(RedlineColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, RedlineColors.Border, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        count.toString(),
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        color = RedlineColors.TextMute,
                    )
                }
            }
        }
    }
    HDivider()
}

@Composable
private fun ScopeTabs(scope: RepoScope, viewerLogin: String, onSelect: (RepoScope) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScopeTab("all repos", scope == RepoScope.All) { onSelect(RepoScope.All) }
        ScopeTab("@$viewerLogin", scope == RepoScope.Mine) { onSelect(RepoScope.Mine) }
    }
    HDivider()
}

@Composable
private fun ScopeTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) RedlineColors.Surface2 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = if (active) RedlineColors.Text else RedlineColors.TextDim,
        )
    }
}

@Composable
private fun SectionLabel(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = RedlineColors.TextMute,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "// $count",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = RedlineColors.TextMute.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun RepoRow(r: GhRepo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // icon
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(RedlineColors.Surface2)
                .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                    if (r.private) {
                        // lock body
                        drawRect(
                            color = RedlineColors.TextDim,
                            topLeft = Offset(w * 0.3f, h * 0.5f),
                            size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.3f),
                            style = stroke,
                        )
                        // shackle
                        val arc = Path().apply {
                            moveTo(w * 0.38f, h * 0.5f)
                            lineTo(w * 0.38f, h * 0.38f)
                            lineTo(w * 0.62f, h * 0.38f)
                            lineTo(w * 0.62f, h * 0.5f)
                        }
                        drawPath(arc, RedlineColors.TextDim, style = stroke)
                    } else {
                        // doc shape
                        val p = Path().apply {
                            moveTo(w * 0.3f, h * 0.25f)
                            lineTo(w * 0.6f, h * 0.25f)
                            lineTo(w * 0.75f, h * 0.4f)
                            lineTo(w * 0.75f, h * 0.78f)
                            lineTo(w * 0.3f, h * 0.78f)
                            close()
                        }
                        drawPath(p, RedlineColors.TextDim, style = stroke)
                        val flap = Path().apply {
                            moveTo(w * 0.6f, h * 0.25f)
                            lineTo(w * 0.6f, h * 0.4f)
                            lineTo(w * 0.75f, h * 0.4f)
                        }
                        drawPath(flap, RedlineColors.TextDim, style = stroke)
                    }
                },
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${r.owner.login}/",
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    color = RedlineColors.TextMute,
                )
                Text(
                    r.name,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = RedlineColors.Text,
                )
                if (r.private) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(RedlineColors.Surface2)
                            .border(1.dp, RedlineColors.Border, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "private",
                            fontFamily = JetBrainsMono,
                            fontSize = 9.sp,
                            color = RedlineColors.TextDim,
                        )
                    }
                }
            }
            r.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(3.dp))
                Text(
                    desc,
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = RedlineColors.TextDim,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                r.language?.let { lang ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(languageColor(lang)))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(lang, fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                    Spacer(Modifier.width(3.dp))
                    Text(formatCount(r.stargazers_count), fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
                }
                if (r.open_issues_count > 0) {
                    val color = if (r.open_issues_count > 50) RedlineColors.Yellow else RedlineColors.Green
                    Text(
                        "${r.open_issues_count} open",
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        color = color,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    timeAgo(r.pushed_at),
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextMute,
                )
            }
        }
    }
    HDivider()
}

private fun formatCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "%.1fk".format(n / 1_000.0)
    n < 1_000_000 -> "${n / 1_000}k"
    else -> "%.1fm".format(n / 1_000_000.0)
}

@Composable
private fun StatusRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = RedlineColors.TextMute,
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

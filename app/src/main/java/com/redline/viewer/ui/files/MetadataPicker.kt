package com.redline.viewer.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.MetadataOptions
import com.redline.viewer.data.Loadable
import com.redline.viewer.data.avatarColorFor
import com.redline.viewer.data.github.GhLabel
import com.redline.viewer.data.github.GhMilestone
import com.redline.viewer.data.github.GhPullUser
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataPicker(
    kind: MetadataKind,
    options: Loadable<MetadataOptions>,
    currentAssignees: List<String>,
    currentReviewers: List<String>,
    currentLabels: List<String>,
    currentMilestone: Int?,
    saving: Boolean,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onSaveAssignees: (List<String>) -> Unit,
    onSaveReviewers: (List<String>) -> Unit,
    onSaveLabels: (List<String>) -> Unit,
    onSaveMilestone: (Int?) -> Unit,
) {
    LaunchedEffect(kind) { onLoad() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val title = when (kind) {
        MetadataKind.Assignees -> "assignees"
        MetadataKind.Reviewers -> "request reviewers"
        MetadataKind.Labels -> "labels"
        MetadataKind.Milestone -> "milestone"
    }

    // Local selection state, seeded from current. Resets when kind changes.
    var selectedUsers by remember(kind, currentAssignees, currentReviewers) {
        mutableStateOf(
            when (kind) {
                MetadataKind.Assignees -> currentAssignees.toSet()
                MetadataKind.Reviewers -> currentReviewers.toSet()
                else -> emptySet()
            }
        )
    }
    var selectedLabels by remember(kind, currentLabels) {
        mutableStateOf(if (kind == MetadataKind.Labels) currentLabels.toSet() else emptySet())
    }
    var selectedMilestone by remember(kind, currentMilestone) {
        mutableStateOf(if (kind == MetadataKind.Milestone) currentMilestone else null)
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = RedlineColors.Bg,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RedlineColors.Border)
            )
            HeaderRow(
                title = title,
                saving = saving,
                onCancel = onCancel,
                onSave = {
                    when (kind) {
                        MetadataKind.Assignees -> onSaveAssignees(selectedUsers.toList())
                        MetadataKind.Reviewers -> onSaveReviewers(selectedUsers.toList())
                        MetadataKind.Labels -> onSaveLabels(selectedLabels.toList())
                        MetadataKind.Milestone -> onSaveMilestone(selectedMilestone)
                    }
                },
            )
            Divider()

            when (options) {
                Loadable.Idle, Loadable.Loading -> StatusBox("loading…")
                is Loadable.Err -> ErrorBox(options.message, onRetry)
                is Loadable.Ok -> {
                    val opts = options.value
                    when (kind) {
                        MetadataKind.Assignees, MetadataKind.Reviewers -> {
                            if (opts.assignableUsers.isEmpty()) {
                                StatusBox("no assignable users")
                            } else {
                                UserList(
                                    users = opts.assignableUsers,
                                    selected = selectedUsers,
                                    onToggle = { login ->
                                        selectedUsers = if (login in selectedUsers) selectedUsers - login else selectedUsers + login
                                    },
                                )
                            }
                        }
                        MetadataKind.Labels -> {
                            if (opts.labels.isEmpty()) {
                                StatusBox("no labels in this repo")
                            } else {
                                LabelList(
                                    labels = opts.labels,
                                    selected = selectedLabels,
                                    onToggle = { name ->
                                        selectedLabels = if (name in selectedLabels) selectedLabels - name else selectedLabels + name
                                    },
                                )
                            }
                        }
                        MetadataKind.Milestone -> {
                            MilestoneList(
                                milestones = opts.milestones,
                                selected = selectedMilestone,
                                onSelect = { selectedMilestone = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(title: String, saving: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = RedlineColors.Text,
            modifier = Modifier.weight(1f),
        )
        TextButton(label = "cancel", enabled = !saving, onClick = onCancel)
        Spacer(Modifier.width(8.dp))
        TextButton(
            label = if (saving) "saving…" else "save",
            enabled = !saving,
            primary = true,
            onClick = onSave,
        )
    }
}

@Composable
private fun TextButton(label: String, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    val bg = if (primary) RedlineColors.Accent else RedlineColors.Surface2
    val fg = if (primary) Color.White else RedlineColors.Text
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = if (enabled) fg else fg.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun UserList(users: List<GhPullUser>, selected: Set<String>, onToggle: (String) -> Unit) {
    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
        items(users, key = { it.login }) { u ->
            val isChecked = u.login in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(u.login) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(checked = isChecked)
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(avatarColorFor(u.login)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        u.login.take(1).uppercase(),
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "@${u.login}",
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    color = RedlineColors.Text,
                )
            }
            Divider()
        }
    }
}

@Composable
private fun LabelList(labels: List<GhLabel>, selected: Set<String>, onToggle: (String) -> Unit) {
    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
        items(labels, key = { it.id.takeIf { it != 0L } ?: it.name.hashCode().toLong() }) { l ->
            val isChecked = l.name in selected
            val color = parseHexColor(l.color)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(l.name) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(checked = isChecked)
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, color.copy(alpha = 0.6f), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        l.name,
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        color = RedlineColors.Text,
                    )
                    l.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            desc,
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.TextMute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Divider()
        }
    }
}

@Composable
private fun MilestoneList(milestones: List<GhMilestone>, selected: Int?, onSelect: (Int?) -> Unit) {
    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(null) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Radio(checked = selected == null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "no milestone",
                    fontFamily = JetBrainsMono,
                    fontSize = 13.sp,
                    color = RedlineColors.TextMute,
                )
            }
            Divider()
        }
        if (milestones.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "no open milestones",
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = RedlineColors.TextMute,
                    )
                }
            }
        } else {
            items(milestones, key = { it.number }) { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(m.number) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Radio(checked = selected == m.number)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            m.title,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                            color = RedlineColors.Text,
                        )
                        m.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(
                                desc,
                                fontFamily = JetBrainsMono,
                                fontSize = 10.sp,
                                color = RedlineColors.TextMute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    m.due_on?.let { due ->
                        Text(
                            due.take(10),
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.TextMute,
                        )
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (checked) RedlineColors.Green else Color.Transparent)
            .border(1.dp, if (checked) RedlineColors.Green else RedlineColors.Border, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(modifier = Modifier.size(14.dp).drawBehind {
                val p = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.55f)
                    lineTo(size.width * 0.42f, size.height * 0.75f)
                    lineTo(size.width * 0.82f, size.height * 0.3f)
                }
                drawPath(p, Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            })
        }
    }
}

@Composable
private fun Radio(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (checked) RedlineColors.Blue else RedlineColors.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(RedlineColors.Blue),
            )
        }
    }
}

@Composable
private fun StatusBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, fontFamily = JetBrainsMono, fontSize = 12.sp, color = RedlineColors.TextMute)
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, fontFamily = JetBrainsMono, fontSize = 12.sp, color = RedlineColors.Accent)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(RedlineColors.Surface2)
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("retry", fontFamily = JetBrainsMono, fontSize = 12.sp, color = RedlineColors.Text)
        }
    }
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

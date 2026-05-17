package com.redline.viewer.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.avatarColorFor
import com.redline.viewer.data.github.GhLabel
import com.redline.viewer.data.github.GhMilestone
import com.redline.viewer.data.github.GhPullUser
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

enum class MetadataKind { Assignees, Reviewers, Labels, Milestone }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetadataBlock(
    assignees: List<GhPullUser>,
    reviewers: List<GhPullUser>,
    labels: List<GhLabel>,
    milestone: GhMilestone?,
    onEdit: (MetadataKind) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedlineColors.Surface),
    ) {
        MetaRow(label = "assignees", onClick = { onEdit(MetadataKind.Assignees) }) {
            if (assignees.isEmpty()) NoneText() else FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { assignees.forEach { UserChip(it) } }
        }
        Divider()
        MetaRow(label = "reviewers", onClick = { onEdit(MetadataKind.Reviewers) }) {
            if (reviewers.isEmpty()) NoneText() else FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { reviewers.forEach { UserChip(it) } }
        }
        Divider()
        MetaRow(label = "labels", onClick = { onEdit(MetadataKind.Labels) }) {
            if (labels.isEmpty()) NoneText() else FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { labels.forEach { LabelChip(it) } }
        }
        Divider()
        MetaRow(label = "milestone", onClick = { onEdit(MetadataKind.Milestone) }) {
            if (milestone == null) NoneText() else MilestoneChip(milestone)
        }
    }
    Divider()
}

@Composable
private fun MetaRow(label: String, onClick: () -> Unit, body: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label.uppercase(),
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            color = RedlineColors.TextMute,
            letterSpacing = 1.sp,
            modifier = Modifier.width(78.dp).padding(top = 3.dp),
        )
        Box(modifier = Modifier.weight(1f)) { body() }
        Spacer(Modifier.width(8.dp))
        EditDot()
    }
}

@Composable
private fun NoneText() {
    Text(
        "none",
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        color = RedlineColors.TextMute,
    )
}

@Composable
private fun EditDot() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, RedlineColors.Border, RoundedCornerShape(4.dp))
            .drawBehind {
                val s = 1.4.dp.toPx()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val len = size.width * 0.28f
                drawLine(
                    color = RedlineColors.TextDim,
                    start = androidx.compose.ui.geometry.Offset(cx - len, cy),
                    end = androidx.compose.ui.geometry.Offset(cx + len, cy),
                    strokeWidth = s,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = RedlineColors.TextDim,
                    start = androidx.compose.ui.geometry.Offset(cx, cy - len),
                    end = androidx.compose.ui.geometry.Offset(cx, cy + len),
                    strokeWidth = s,
                    cap = StrokeCap.Round,
                )
            },
    )
}

@Composable
private fun UserChip(u: GhPullUser) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RedlineColors.Surface2)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(avatarColorFor(u.login)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                u.login.take(1).uppercase(),
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            u.login,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = RedlineColors.Text,
        )
    }
}

@Composable
private fun LabelChip(l: GhLabel) {
    val color = parseHexColor(l.color)
    val bg = color.copy(alpha = 0.18f)
    val border = color.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            l.name,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = RedlineColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MilestoneChip(m: GhMilestone) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(RedlineColors.Surface2)
            .border(1.dp, RedlineColors.Border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).drawBehind {
            val s = 1.2.dp.toPx()
            val r = (size.minDimension - s) / 2f
            drawCircle(RedlineColors.Blue, radius = r, style = Stroke(width = s))
            drawCircle(RedlineColors.Blue, radius = r * 0.35f)
        })
        Spacer(Modifier.width(6.dp))
        Text(
            m.title,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = RedlineColors.Text,
        )
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

fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#").take(6)
    if (cleaned.length != 6) return RedlineColors.TextMute
    return try {
        val rgb = cleaned.toLong(16)
        Color(0xFF000000 or rgb)
    } catch (_: NumberFormatException) {
        RedlineColors.TextMute
    }
}

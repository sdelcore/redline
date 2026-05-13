package com.redline.viewer.ui.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.ConversationItem
import com.redline.viewer.data.ConversationKind
import com.redline.viewer.data.ReviewVerdict
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun ConversationBlock(items: List<ConversationItem>) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }
    val reviewCount = items.count { it.kind == ConversationKind.Review }

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
            ChatBubbleIcon(color = RedlineColors.Blue)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "conversation",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = RedlineColors.Text,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "${items.size} comment${if (items.size == 1) "" else "s"} · " +
                        "$reviewCount review${if (reviewCount == 1) "" else "s"}",
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    color = RedlineColors.TextMute,
                )
            }
            Chevron(rotated = expanded)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .background(RedlineColors.Bg)
                .padding(vertical = 4.dp)
            ) {
                items.forEach { ConversationCard(it) }
            }
        }
    }
    HDivider()
}

@Composable
private fun ConversationCard(item: ConversationItem) {
    val verdictColor = when (item.verdict) {
        ReviewVerdict.Approve -> RedlineColors.Green
        ReviewVerdict.RequestChanges -> RedlineColors.Accent
        ReviewVerdict.Comment -> RedlineColors.Blue
        null -> RedlineColors.Border
    }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(RedlineColors.Surface)
            .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier
                    .width(3.dp)
                    .background(if (item.kind == ConversationKind.Review) verdictColor else RedlineColors.Border)
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RedlineColors.Bg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(item.avatar),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                item.author.take(1).uppercase().ifEmpty { "?" },
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.author,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = RedlineColors.Text,
                        )
                        if (item.kind == ConversationKind.Review) {
                            Spacer(Modifier.width(8.dp))
                            VerdictPill(verdict = item.verdict, color = verdictColor)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            item.whenLabel,
                            fontFamily = JetBrainsMono,
                            fontSize = 10.sp,
                            color = RedlineColors.TextMute,
                        )
                    }
                    Text(
                        item.body.ifBlank { "(no body)" },
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = RedlineColors.Text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictPill(verdict: ReviewVerdict?, color: Color) {
    val label = when (verdict) {
        ReviewVerdict.Approve -> "approved"
        ReviewVerdict.RequestChanges -> "requested changes"
        ReviewVerdict.Comment -> "commented"
        null -> "review"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            label.uppercase(),
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            color = color,
        )
    }
}

@Composable
private fun ChatBubbleIcon(color: Color) {
    Box(modifier = Modifier.size(18.dp).drawBehind {
        val p = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.17f)
            lineTo(size.width * 0.86f, size.height * 0.17f)
            lineTo(size.width * 0.86f, size.height * 0.67f)
            lineTo(size.width * 0.47f, size.height * 0.67f)
            lineTo(size.width * 0.25f, size.height * 0.83f)
            lineTo(size.width * 0.25f, size.height * 0.67f)
            lineTo(size.width * 0.14f, size.height * 0.67f)
            close()
        }
        drawPath(p, color, style = Stroke(width = 1.4.dp.toPx()))
    })
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
private fun HDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RedlineColors.BorderSoft)
    )
}

@Suppress("UNUSED_PARAMETER")
private fun unusedOffset(o: Offset) = Unit

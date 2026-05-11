package com.redline.viewer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.redline.viewer.data.CheckSummary
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun CheckIcon(status: CheckSummary, size: Dp = 16.dp) {
    when (status) {
        CheckSummary.Pass -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(RedlineColors.Green),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(size * 0.6f)) {
                val w = this.size.width
                val h = this.size.height
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.5f)
                    lineTo(w * 0.4f, h * 0.7f)
                    lineTo(w * 0.8f, h * 0.3f)
                }
                drawPath(
                    path,
                    color = RedlineColors.Bg,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        CheckSummary.Fail -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(RedlineColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(size * 0.55f)) {
                val w = this.size.width
                val h = this.size.height
                drawLine(
                    color = RedlineColors.Bg,
                    start = Offset(w * 0.2f, h * 0.2f),
                    end = Offset(w * 0.8f, h * 0.8f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = RedlineColors.Bg,
                    start = Offset(w * 0.8f, h * 0.2f),
                    end = Offset(w * 0.2f, h * 0.8f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        CheckSummary.Pending -> {
            val transition = rememberInfiniteTransition(label = "spin")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "angle",
            )
            Canvas(
                modifier = Modifier
                    .size(size)
                    .rotate(angle),
            ) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = RedlineColors.Yellow,
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(this.size.width - stroke, this.size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

fun colorForStatus(status: CheckSummary): Color = when (status) {
    CheckSummary.Pass -> RedlineColors.Green
    CheckSummary.Fail -> RedlineColors.Accent
    CheckSummary.Pending -> RedlineColors.Yellow
}

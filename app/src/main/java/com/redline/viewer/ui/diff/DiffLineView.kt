package com.redline.viewer.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.DiffRow
import com.redline.viewer.data.DiffRowType
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

private val GUTTER_W = 44.dp

@Composable
fun DiffLineView(
    row: DiffRow,
    side: CommentSide,
    fontSize: TextUnit,
    lineHeight: Dp,
    onClick: (() -> Unit)? = null,
) {
    if (row.type == DiffRowType.Hunk) {
        Row(
            modifier = Modifier
                .height(lineHeight)
                .background(RedlineColors.HunkBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(GUTTER_W)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    "···",
                    fontFamily = JetBrainsMono,
                    fontSize = (fontSize.value - 1f).sp,
                    color = RedlineColors.Blue.copy(alpha = 0.5f),
                )
            }
            Text(
                row.hunkText.orEmpty(),
                fontFamily = JetBrainsMono,
                fontSize = (fontSize.value - 1f).sp,
                color = RedlineColors.Blue.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        return
    }

    val lineData = if (side == CommentSide.Old) row.old else row.newer
    val isAdd = row.type == DiffRowType.Add
    val isDel = row.type == DiffRowType.Del

    // Blank placeholder
    if (lineData == null) {
        val bg = if (side == CommentSide.Old) RedlineColors.GreenBgFaint else RedlineColors.RedBgFaint
        val dotColor = if (side == CommentSide.Old) RedlineColors.Green else RedlineColors.Accent
        Row(
            modifier = Modifier
                .height(lineHeight)
                .background(bg),
        ) {
            Box(
                modifier = Modifier
                    .width(GUTTER_W)
                    .fillMaxHeight()
                    .background(Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(RedlineColors.BorderSoft)
                )
            }
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "·",
                    fontFamily = JetBrainsMono,
                    fontSize = (fontSize.value - 2f).sp,
                    color = dotColor.copy(alpha = 0.4f),
                )
            }
        }
        return
    }

    val bg: Color
    val gutterBg: Color
    val marker: String
    val markerColor: Color
    when {
        side == CommentSide.Old && isDel -> {
            bg = RedlineColors.RedBg
            gutterBg = RedlineColors.RedGutter
            marker = "−"
            markerColor = RedlineColors.Accent
        }
        side == CommentSide.New && isAdd -> {
            bg = RedlineColors.GreenBg
            gutterBg = RedlineColors.GreenGutter
            marker = "+"
            markerColor = RedlineColors.Green
        }
        else -> {
            bg = Color.Transparent
            gutterBg = Color.Transparent
            marker = " "
            markerColor = RedlineColors.TextMute
        }
    }
    val isPlain = bg == Color.Transparent

    Row(
        modifier = Modifier
            .height(lineHeight)
            .background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Box(
            modifier = Modifier
                .width(GUTTER_W)
                .fillMaxHeight()
                .background(gutterBg),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row {
                Text(
                    lineData.n.toString(),
                    fontFamily = JetBrainsMono,
                    fontSize = (fontSize.value - 1f).sp,
                    color = if (isPlain) RedlineColors.TextMute else markerColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(RedlineColors.BorderSoft)
            )
        }
        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                marker,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                color = markerColor,
            )
        }
        Text(
            highlight(lineData.text),
            fontFamily = JetBrainsMono,
            fontSize = fontSize,
            color = RedlineColors.Text,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}

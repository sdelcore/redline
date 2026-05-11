package com.redline.viewer.ui.diff

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.DiffRow
import com.redline.viewer.data.DiffRowType
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.Thread
import com.redline.viewer.ui.review.InlineThread
import com.redline.viewer.ui.review.pendingThread
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun CodePane(
    side: CommentSide,
    diff: List<DiffRow>,
    threads: List<Thread>,
    pending: List<PendingComment>,
    fileShort: String,
    scale: Float,
    vScroll: ScrollState,
    hScroll: ScrollState,
    onScaleChange: (Float) -> Unit,
    onCommentLine: (CommentRequest) -> Unit,
    label: String,
    sublabel: String,
) {
    val fontSize = (12 * scale).sp
    val lineHeight = (18 * scale).dp
    val labelColor = if (side == CommentSide.Old) RedlineColors.Accent else RedlineColors.Green
    val labelBg = if (side == CommentSide.Old) Color(0x14E53935) else Color(0x1443A047)
    val labelBorder = if (side == CommentSide.Old) Color(0x33E53935) else Color(0x3343A047)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) onScaleChange((scale * zoom).coerceIn(0.6f, 2.2f))
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vScroll),
        ) {
            // Sticky-ish side label band — pinned at top of pane (visible row before scroll content)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(labelBg),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label.uppercase(),
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = labelColor,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        sublabel,
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        color = RedlineColors.TextMute,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(labelBorder),
                )
            }

            // Horizontal-scroll body — width grows to longest line; rows below render at that width
            Column(
                modifier = Modifier
                    .horizontalScroll(hScroll)
                    .widthIn(min = 0.dp),
            ) {
                diff.forEachIndexed { _, row ->
                    val lineNum = when {
                        row.type == DiffRowType.Hunk -> null
                        side == CommentSide.Old -> row.old?.n
                        else -> row.newer?.n
                    }
                    val thread = if (lineNum != null) threads.find { it.side == side && it.line == lineNum } else null
                    val localPending = if (lineNum != null) {
                        pending.filter { it.side == side && it.line == lineNum }
                    } else emptyList()

                    DiffLineView(
                        row = row,
                        side = side,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        onClick = if (lineNum != null) {
                            {
                                val text = if (side == CommentSide.Old) row.old?.text else row.newer?.text
                                onCommentLine(CommentRequest(side, lineNum, text ?: "", fileShort))
                            }
                        } else null,
                    )
                    if (thread != null) InlineThread(thread.comments)
                    if (localPending.isNotEmpty()) {
                        InlineThread(localPending.map { pendingThread(it.body) })
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

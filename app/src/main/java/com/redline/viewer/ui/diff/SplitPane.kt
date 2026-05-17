package com.redline.viewer.ui.diff

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.DiffRow
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.Thread
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors
import kotlin.math.roundToInt

private val HANDLE_H = 14.dp

@Composable
fun SplitPane(
    diff: List<DiffRow>,
    threads: List<Thread>,
    pending: List<PendingComment>,
    fileShort: String,
    scale: Float,
    vScroll: ScrollState,
    hScroll: ScrollState,
    splitFraction: Float,
    onSplitChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onCommentLine: (CommentRequest) -> Unit,
    oldSublabel: String,
    newSublabel: String,
    swap: Boolean = false,
    wordWrap: Boolean = false,
    jumpTarget: JumpTarget? = null,
    onJumpConsumed: () -> Unit = {},
) {
    val topSide = if (swap) CommentSide.New else CommentSide.Old
    val botSide = if (swap) CommentSide.Old else CommentSide.New
    val topLabel = if (swap) "new" else "old"
    val botLabel = if (swap) "old" else "new"
    val topSub = if (swap) newSublabel else oldSublabel
    val botSub = if (swap) oldSublabel else newSublabel
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg),
    ) {
        val totalH = maxHeight
        val handlePx = with(LocalDensity.current) { HANDLE_H.toPx() }
        val totalPx = with(LocalDensity.current) { totalH.toPx() }
        val usable = totalPx - handlePx
        val topPx = usable * splitFraction
        val botPx = usable - topPx

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { topPx.toDp() })) {
                CodePane(
                    side = topSide,
                    diff = diff,
                    threads = threads,
                    pending = pending,
                    fileShort = fileShort,
                    scale = scale,
                    vScroll = vScroll,
                    hScroll = hScroll,
                    onScaleChange = onScaleChange,
                    onCommentLine = onCommentLine,
                    label = topLabel,
                    sublabel = topSub,
                    wordWrap = wordWrap,
                    jumpTarget = jumpTarget?.takeIf { it.side == topSide },
                    onJumpConsumed = onJumpConsumed,
                )
            }
            // Drag handle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HANDLE_H)
                    .background(RedlineColors.Surface)
                    .border(width = 1.dp, color = RedlineColors.Border)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { dy ->
                            val newFraction = ((topPx + dy) / usable).coerceIn(0.15f, 0.85f)
                            onSplitChange(newFraction)
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Row {
                    Box(modifier = Modifier.width(18.dp).height(2.dp).background(RedlineColors.TextMute))
                    Spacer(Modifier.width(3.dp))
                    Box(modifier = Modifier.width(18.dp).height(2.dp).background(RedlineColors.TextMute))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${(splitFraction * 100).roundToInt()}%",
                    fontFamily = JetBrainsMono,
                    fontSize = 9.sp,
                    color = RedlineColors.TextMute,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) { botPx.toDp() })) {
                CodePane(
                    side = botSide,
                    diff = diff,
                    threads = threads,
                    pending = pending,
                    fileShort = fileShort,
                    scale = scale,
                    vScroll = vScroll,
                    hScroll = hScroll,
                    onScaleChange = onScaleChange,
                    onCommentLine = onCommentLine,
                    label = botLabel,
                    sublabel = botSub,
                    wordWrap = wordWrap,
                    jumpTarget = jumpTarget?.takeIf { it.side == botSide },
                    onJumpConsumed = onJumpConsumed,
                )
            }
        }
    }
}

package com.redline.viewer.ui.diff

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

private val INLINE_NUM_W = 32.dp

@Composable
fun InlinePane(
    diff: List<DiffRow>,
    threads: List<Thread>,
    pending: List<PendingComment>,
    fileShort: String,
    scale: Float,
    vScroll: ScrollState,
    hScroll: ScrollState,
    onScaleChange: (Float) -> Unit,
    onCommentLine: (CommentRequest) -> Unit,
    sublabel: String,
    wordWrap: Boolean = false,
    jumpTarget: JumpTarget? = null,
    onJumpConsumed: () -> Unit = {},
) {
    val fontSize = (12 * scale).sp
    val lineHeight = (18 * scale).dp
    val labelBandHeight = 18.dp

    val density = LocalDensity.current
    LaunchedEffect(jumpTarget) {
        val target = jumpTarget ?: return@LaunchedEffect
        val idx = diff.indexOfFirst {
            it.type != DiffRowType.Hunk &&
                (if (target.side == CommentSide.Old) it.old?.n else it.newer?.n) == target.line
        }
        if (idx < 0) {
            onJumpConsumed()
            return@LaunchedEffect
        }
        val offsetPx = with(density) {
            (labelBandHeight.toPx() + idx * lineHeight.toPx()).toInt()
        }
        val target0 = (offsetPx - (lineHeight.value * density.density * 4)).toInt().coerceAtLeast(0)
        vScroll.animateScrollTo(target0)
        onJumpConsumed()
    }

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(labelBandHeight)
                    .background(Color(0x141976D2)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "INLINE",
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RedlineColors.Blue,
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
                        .background(Color(0x331976D2)),
                )
            }

            val rowsMod = if (wordWrap) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.horizontalScroll(hScroll).widthIn(min = 0.dp)
            }
            Column(modifier = rowsMod) {
                diff.forEach { row ->
                    InlineDiffLineView(
                        row = row,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        wordWrap = wordWrap,
                        onClick = inlineClickFor(row, fileShort, onCommentLine),
                    )
                    InlineThreadsFor(row, threads, pending)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private fun inlineClickFor(
    row: DiffRow,
    fileShort: String,
    onCommentLine: (CommentRequest) -> Unit,
): (() -> Unit)? {
    return when (row.type) {
        DiffRowType.Hunk -> null
        DiffRowType.Del -> {
            val n = row.old?.n ?: return null
            val text = row.old.text
            ({ onCommentLine(CommentRequest(CommentSide.Old, n, text, fileShort)) })
        }
        DiffRowType.Add -> {
            val n = row.newer?.n ?: return null
            val text = row.newer.text
            ({ onCommentLine(CommentRequest(CommentSide.New, n, text, fileShort)) })
        }
        DiffRowType.Context -> {
            val n = row.newer?.n ?: return null
            val text = row.newer.text
            ({ onCommentLine(CommentRequest(CommentSide.New, n, text, fileShort)) })
        }
    }
}

@Composable
private fun InlineThreadsFor(
    row: DiffRow,
    threads: List<Thread>,
    pending: List<PendingComment>,
) {
    val oldN = row.old?.n
    val newN = row.newer?.n
    val showsOld = row.type == DiffRowType.Del || row.type == DiffRowType.Context
    val showsNew = row.type == DiffRowType.Add || row.type == DiffRowType.Context

    if (showsOld && oldN != null) {
        threads.filter { it.side == CommentSide.Old && it.line == oldN }
            .forEach { InlineThread(it.comments) }
        val p = pending.filter { it.side == CommentSide.Old && it.line == oldN }
        if (p.isNotEmpty()) InlineThread(p.map { pendingThread(it.body) })
    }
    if (showsNew && newN != null) {
        threads.filter { it.side == CommentSide.New && it.line == newN }
            .forEach { InlineThread(it.comments) }
        val p = pending.filter { it.side == CommentSide.New && it.line == newN }
        if (p.isNotEmpty()) InlineThread(p.map { pendingThread(it.body) })
    }
}

@Composable
private fun InlineDiffLineView(
    row: DiffRow,
    fontSize: TextUnit,
    lineHeight: Dp,
    wordWrap: Boolean,
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
                    .width(INLINE_NUM_W * 2 + 12.dp)
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

    val isAdd = row.type == DiffRowType.Add
    val isDel = row.type == DiffRowType.Del
    val text = (row.newer ?: row.old)?.text.orEmpty()

    val bg: Color
    val gutterBg: Color
    val marker: String
    val markerColor: Color
    when {
        isDel -> {
            bg = RedlineColors.RedBg
            gutterBg = RedlineColors.RedGutter
            marker = "−"
            markerColor = RedlineColors.Accent
        }
        isAdd -> {
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

    val rowHeightMod = if (wordWrap) Modifier.heightIn(min = lineHeight) else Modifier.height(lineHeight)
    Row(
        modifier = Modifier
            .then(rowHeightMod)
            .background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        // Old gutter
        GutterCell(
            num = row.old?.n,
            show = !isAdd,
            fontSize = fontSize,
            color = if (isPlain) RedlineColors.TextMute else markerColor,
            bg = gutterBg,
        )
        // New gutter
        GutterCell(
            num = row.newer?.n,
            show = !isDel,
            fontSize = fontSize,
            color = if (isPlain) RedlineColors.TextMute else markerColor,
            bg = gutterBg,
        )
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
        val textMod = if (wordWrap) Modifier.weight(1f).padding(end = 16.dp) else Modifier.padding(end = 16.dp)
        Text(
            highlight(text),
            fontFamily = JetBrainsMono,
            fontSize = fontSize,
            color = RedlineColors.Text,
            softWrap = wordWrap,
            maxLines = if (wordWrap) Int.MAX_VALUE else 1,
            modifier = textMod,
        )
    }
}

@Composable
private fun GutterCell(
    num: Int?,
    show: Boolean,
    fontSize: TextUnit,
    color: Color,
    bg: Color,
) {
    Box(
        modifier = Modifier
            .width(INLINE_NUM_W)
            .fillMaxHeight()
            .background(bg),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (show && num != null) {
            Text(
                num.toString(),
                fontFamily = JetBrainsMono,
                fontSize = (fontSize.value - 1f).sp,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(end = 6.dp),
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
}

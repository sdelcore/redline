package com.redline.viewer.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.CommentSide
import com.redline.viewer.data.PendingComment
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

data class ComposerContext(
    val side: CommentSide,
    val line: Int,
    val text: String,
    val fileShort: String,
)

enum class ComposerKind { Single, StartReview }

data class ComposerSubmit(val context: ComposerContext, val body: String, val kind: ComposerKind) {
    fun toPending(): PendingComment = PendingComment(
        fileShort = context.fileShort,
        side = context.side,
        line = context.line,
        body = body,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentComposer(
    context: ComposerContext,
    onCancel: () -> Unit,
    onSubmit: (ComposerSubmit) -> Unit,
) {
    var body by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = RedlineColors.Bg,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(modifier = Modifier.imePadding()) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RedlineColors.Border)
            )

            // Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "comment on line ${context.line}",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = RedlineColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(4.dp),
                ) {
                    Text("×",
                        fontFamily = JetBrainsMono,
                        fontSize = 18.sp,
                        color = RedlineColors.TextMute)
                }
            }
            Divider()
            Spacer(Modifier.height(12.dp))

            // Code preview
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(RedlineColors.Bg)
                    .border(1.dp, RedlineColors.BorderSoft, RoundedCornerShape(4.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    context.line.toString().padStart(4),
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = RedlineColors.TextMute,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (context.side == CommentSide.Old) "−" else "+",
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = if (context.side == CommentSide.Old) RedlineColors.Accent else RedlineColors.Green,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    context.text,
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = RedlineColors.Text,
                    softWrap = false,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(12.dp))

            // Composer textarea
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(RedlineColors.Surface)
                    .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                    .padding(10.dp),
            ) {
                if (body.isEmpty()) {
                    Text(
                        "Leave a review comment…",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = RedlineColors.TextMute,
                    )
                }
                BasicTextField(
                    value = body,
                    onValueChange = { body = it },
                    textStyle = TextStyle(
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = RedlineColors.Text,
                    ),
                    cursorBrush = SolidColor(RedlineColors.Accent),
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                )
            }

            // Markdown toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("**bold**", "`code`", "```block```", "🙂").forEach { tag ->
                    MdChip(tag)
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("md", fontFamily = JetBrainsMono, fontSize = 10.sp, color = RedlineColors.TextMute)
            }

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CancelButton(modifier = Modifier.weight(1f), onClick = onCancel)
                SheetButton(
                    text = "comment",
                    modifier = Modifier.weight(1f),
                    background = if (body.isNotBlank()) RedlineColors.Text else RedlineColors.Surface2,
                    contentColor = if (body.isNotBlank()) RedlineColors.Bg else RedlineColors.TextMute,
                    enabled = body.isNotBlank(),
                    onClick = { onSubmit(ComposerSubmit(context, body, ComposerKind.Single)) },
                )
                SheetButton(
                    text = "start review",
                    modifier = Modifier.weight(1.4f),
                    background = if (body.isNotBlank()) RedlineColors.Accent else RedlineColors.Surface2,
                    contentColor = if (body.isNotBlank()) Color.White else RedlineColors.TextMute,
                    enabled = body.isNotBlank(),
                    onClick = { onSubmit(ComposerSubmit(context, body, ComposerKind.StartReview)) },
                )
            }
        }
    }
}

@Composable
private fun MdChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(RedlineColors.Surface2)
            .border(1.dp, RedlineColors.Border, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = RedlineColors.TextDim,
        )
    }
}

@Composable
internal fun SheetButton(
    text: String,
    background: Color,
    contentColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = contentColor,
        )
    }
}

@Composable
internal fun CancelButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Transparent)
            .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "cancel",
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = RedlineColors.TextDim,
        )
    }
}

@Composable
internal fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RedlineColors.BorderSoft)
    )
}

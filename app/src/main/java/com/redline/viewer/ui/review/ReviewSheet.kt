package com.redline.viewer.ui.review

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.redline.viewer.data.ReviewVerdict
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

data class ReviewSubmission(val verdict: ReviewVerdict, val body: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    prId: Int,
    pendingCount: Int,
    onCancel: () -> Unit,
    onSubmit: (ReviewSubmission) -> Unit,
) {
    var verdict by remember { mutableStateOf(ReviewVerdict.Comment) }
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
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RedlineColors.Border)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "finish your review",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = RedlineColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.clickable(onClick = onCancel).padding(4.dp)) {
                    Text("×",
                        fontFamily = JetBrainsMono,
                        fontSize = 18.sp,
                        color = RedlineColors.TextMute)
                }
            }
            Divider()
            Spacer(Modifier.height(12.dp))

            // Pending count chip
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(RedlineColors.Surface)
                    .border(1.dp, RedlineColors.BorderSoft, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(RedlineColors.Blue)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$pendingCount pending comment${if (pendingCount == 1) "" else "s"} · #$prId",
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = RedlineColors.TextDim,
                )
            }

            // Summary textarea
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(RedlineColors.Surface)
                    .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                    .padding(10.dp),
            ) {
                if (body.isEmpty()) {
                    Text(
                        "Review summary (optional)…",
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
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
            }

            // Verdict options
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VerdictOption(ReviewVerdict.Comment,         "Comment",         "Submit general feedback without explicit approval.", RedlineColors.Text, verdict) { verdict = it }
                VerdictOption(ReviewVerdict.Approve,         "Approve",         "Approve these changes and allow merging.",            RedlineColors.Green, verdict) { verdict = it }
                VerdictOption(ReviewVerdict.RequestChanges,  "Request changes", "Submit feedback that must be addressed before merge.", RedlineColors.Accent, verdict) { verdict = it }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CancelButton(modifier = Modifier.weight(1f), onClick = onCancel)
                val submitBg = when (verdict) {
                    ReviewVerdict.Approve -> RedlineColors.Green
                    ReviewVerdict.RequestChanges -> RedlineColors.Accent
                    ReviewVerdict.Comment -> RedlineColors.Text
                }
                val submitFg = if (verdict == ReviewVerdict.Comment) RedlineColors.Bg else Color.White
                SheetButton(
                    text = "submit review",
                    modifier = Modifier.weight(1.6f),
                    background = submitBg,
                    contentColor = submitFg,
                    enabled = true,
                    onClick = { onSubmit(ReviewSubmission(verdict, body)) },
                )
            }
        }
    }
}

@Composable
private fun VerdictOption(
    value: ReviewVerdict,
    label: String,
    desc: String,
    accent: Color,
    current: ReviewVerdict,
    onSelect: (ReviewVerdict) -> Unit,
) {
    val active = current == value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) RedlineColors.Surface2 else Color.Transparent)
            .border(1.dp, if (active) accent else RedlineColors.Border, RoundedCornerShape(6.dp))
            .clickable { onSelect(value) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (active) accent else Color.Transparent)
                .border(1.5.dp, if (active) accent else RedlineColors.BorderSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(RedlineColors.Bg)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                fontFamily = Inter,
                fontSize = 11.sp,
                color = RedlineColors.TextDim,
                lineHeight = 15.sp,
            )
        }
    }
}

package com.redline.viewer.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.Comment
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

@Composable
fun InlineThread(comments: List<Comment>) {
    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(RedlineColors.Surface)
            .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
    ) {
        // Left blue border
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(RedlineColors.Blue)
            )
            Column(modifier = Modifier.fillMaxWidth().padding(start = 3.dp)) {
                comments.forEachIndexed { i, c ->
                    if (i > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(RedlineColors.BorderSoft)
                        )
                    }
                    CommentItem(c)
                }
                ThreadFooter()
            }
        }
    }
}

@Composable
private fun CommentItem(c: Comment) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(c.avatar),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    c.author.take(1).uppercase(),
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                c.author,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = RedlineColors.Text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                c.whenLabel,
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = RedlineColors.TextMute,
            )
            if (c.resolved) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x2643A047))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "resolved",
                        fontFamily = JetBrainsMono,
                        fontSize = 9.sp,
                        color = RedlineColors.Green,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            c.body,
            fontFamily = Inter,
            fontSize = 12.sp,
            color = RedlineColors.Text,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 26.dp),
        )
    }
}

@Composable
private fun ThreadFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RedlineColors.Bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThreadBtn("reply")
        Spacer(Modifier.width(6.dp))
        ThreadBtn("resolve")
        Spacer(Modifier.weight(1f))
        ThreadBtn("···", color = RedlineColors.TextMute)
    }
}

@Composable
private fun ThreadBtn(text: String, color: Color = RedlineColors.TextDim) {
    Box(modifier = Modifier
        .clickable { /* mock */ }
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(
            text,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = color,
        )
    }
}

fun pendingThread(body: String): Comment = Comment(
    author = "you",
    avatar = RedlineColors.Accent,
    whenLabel = "just now",
    body = body,
    resolved = false,
)

package com.redline.viewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redline.viewer.data.Loadable
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors

/**
 * Renders the Idle / Loading / Error / Ok lifecycle of a [Loadable]
 * with one consistent look. The content slot only ever sees the loaded
 * value; "empty" is the content's concern (each screen says
 * "no repos" / "no open pull requests" / etc. in its own words).
 */
@Composable
fun <T> LoadableContent(
    state: Loadable<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadingMessage: String = "loading…",
    content: @Composable (T) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            Loadable.Idle, Loadable.Loading -> CenteredMessage(loadingMessage)
            is Loadable.Err -> ErrorWithRetry(state.message, onRetry)
            is Loadable.Ok<T> -> content(state.value)
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = RedlineColors.TextMute,
        )
    }
}

@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            color = RedlineColors.Accent,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(RedlineColors.Surface2)
                .border(1.dp, RedlineColors.Border, RoundedCornerShape(6.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                "retry",
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = RedlineColors.TextDim,
            )
        }
    }
}

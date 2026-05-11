package com.redline.viewer.ui.login

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.redline.viewer.BuildConfig
import com.redline.viewer.data.github.DeviceAuth
import com.redline.viewer.data.github.DeviceCode
import com.redline.viewer.data.github.PollResult
import com.redline.viewer.ui.components.GitHubMark
import com.redline.viewer.ui.components.RedlineButton
import com.redline.viewer.ui.components.SubtleOutlinedButton
import com.redline.viewer.ui.theme.Inter
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed class LoginState {
    object Idle : LoginState()
    object Requesting : LoginState()
    data class Verifying(val code: DeviceCode) : LoginState()
    data class Error(val message: String) : LoginState()
}

@Composable
fun LoginScreen(onAuthenticated: (token: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clientId = BuildConfig.GITHUB_CLIENT_ID

    var state by remember { mutableStateOf<LoginState>(LoginState.Idle) }
    val auth = remember { if (clientId.isNotBlank()) DeviceAuth(clientId) else null }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pollJob?.cancel()
            auth?.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RedlineColors.Bg)
            .gridBackdrop()
            .redGlow(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.systemBars.asPaddingValues())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(120.dp))
            RedlineLogo()
            Spacer(Modifier.height(24.dp))
            Row {
                Text("redline", fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp, color = RedlineColors.Text)
                Text("_", fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp, color = RedlineColors.Accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "// diff viewer for engineers",
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = RedlineColors.TextMute,
            )

            Spacer(Modifier.weight(1f))

            when (val s = state) {
                is LoginState.Idle -> IdleBlock(
                    enabled = auth != null,
                    onStart = {
                        if (auth == null) return@IdleBlock
                        state = LoginState.Requesting
                        scope.launch {
                            try {
                                val code = auth.requestCode()
                                state = LoginState.Verifying(code)
                                pollJob = scope.launch {
                                    when (val r = auth.poll(code)) {
                                        is PollResult.Success -> onAuthenticated(r.accessToken)
                                        is PollResult.Error -> state = LoginState.Error(r.message.ifBlank { r.code })
                                    }
                                }
                            } catch (t: Throwable) {
                                state = LoginState.Error(t.message ?: "request failed")
                            }
                        }
                    },
                )
                LoginState.Requesting -> StatusBlock("requesting device code…")
                is LoginState.Verifying -> VerifyingBlock(
                    code = s.code,
                    onOpenBrowser = {
                        val intent = Intent(Intent.ACTION_VIEW, s.code.verification_uri.toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    onCancel = {
                        pollJob?.cancel()
                        state = LoginState.Idle
                    },
                )
                is LoginState.Error -> ErrorBlock(s.message, onRetry = { state = LoginState.Idle })
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "review · comment · approve · merge",
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                color = RedlineColors.TextMute,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun IdleBlock(enabled: Boolean, onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RedlineButton(
            text = if (enabled) "Sign in with GitHub" else "Set GITHUB_CLIENT_ID in local.properties",
            enabled = enabled,
            onClick = onStart,
            iconBefore = { GitHubMark(color = RedlineColors.Bg) },
        )
    }
}

@Composable
private fun StatusBlock(text: String) {
    Text(
        text,
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        color = RedlineColors.TextDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun VerifyingBlock(code: DeviceCode, onOpenBrowser: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "go to ${code.verification_uri}",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = RedlineColors.TextDim,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "enter this code:",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = RedlineColors.TextMute,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(RedlineColors.Surface2)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                code.user_code,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = RedlineColors.Accent,
            )
        }
        Spacer(Modifier.height(16.dp))
        RedlineButton(
            text = "Open ${code.verification_uri.removePrefix("https://").removePrefix("http://")}",
            onClick = onOpenBrowser,
        )
        Spacer(Modifier.height(8.dp))
        SubtleOutlinedButton(text = "cancel", onClick = onCancel)
        Spacer(Modifier.height(10.dp))
        Text(
            "waiting for authorization…",
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = RedlineColors.TextMute,
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message,
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = RedlineColors.Accent,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        RedlineButton(text = "try again", onClick = onRetry)
    }
}

@Composable
private fun RedlineLogo() {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(RedlineColors.Surface2, RedlineColors.Bg),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .drawBehind {
                val pad = size.width * 0.18f
                drawLine(
                    color = RedlineColors.Accent.copy(alpha = 0.25f),
                    start = Offset(pad, size.height - pad),
                    end = Offset(size.width - pad, pad),
                    strokeWidth = 24f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = RedlineColors.Accent,
                    start = Offset(pad, size.height - pad),
                    end = Offset(size.width - pad, pad),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round,
                )
            },
    )
}

private fun Modifier.gridBackdrop(): Modifier = this.drawBehind {
    val step = 24.dp.toPx()
    val color = RedlineColors.Border.copy(alpha = 0.4f)
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun Modifier.redGlow(): Modifier = this.drawBehind {
    val r = 320.dp.toPx() / 2f
    val center = Offset(size.width / 2f, size.height * 0.28f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(RedlineColors.Accent.copy(alpha = 0.18f), Color.Transparent),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )
}

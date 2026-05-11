package com.redline.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.redline.viewer.data.SampleData
import com.redline.viewer.ui.diff.DiffViewScreen
import com.redline.viewer.ui.files.FileBrowserScreen
import com.redline.viewer.ui.login.LoginScreen
import com.redline.viewer.ui.prlist.PRListScreen
import com.redline.viewer.ui.review.CommentComposer
import com.redline.viewer.ui.review.ComposerContext
import com.redline.viewer.ui.review.ComposerKind
import com.redline.viewer.ui.review.ReviewSheet
import com.redline.viewer.ui.theme.JetBrainsMono
import com.redline.viewer.ui.theme.RedlineColors
import com.redline.viewer.ui.theme.RedlineTheme
import kotlinx.coroutines.delay

private object Routes {
    const val Login = "login"
    const val PRList = "prList"
    const val Files = "files/{prId}"
    const val Diff = "diff/{prId}/{fileIdx}"

    fun files(prId: Int) = "files/$prId"
    fun diff(prId: Int, fileIdx: Int) = "diff/$prId/$fileIdx"
}

@Composable
fun RedlineApp() {
    val vm: AppViewModel = viewModel()
    val token by vm.token.collectAsState()
    val authState by vm.authState.collectAsState()
    val pending by vm.pending.collectAsState()
    val toast by vm.toast.collectAsState()

    var repoIdx by remember { mutableIntStateOf(0) }
    var composer by remember { mutableStateOf<ComposerContext?>(null) }
    var reviewOpen by remember { mutableStateOf(false) }
    var reviewPrId by remember { mutableIntStateOf(0) }

    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

    // Single source of truth: drive navigation off token state.
    LaunchedEffect(token) {
        val authed = !token.isNullOrBlank()
        when {
            authed && currentRoute == Routes.Login -> {
                nav.navigate(Routes.PRList) {
                    popUpTo(Routes.Login) { inclusive = true }
                }
            }
            !authed && currentRoute != Routes.Login -> {
                nav.navigate(Routes.Login) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    RedlineTheme {
        Box(modifier = Modifier.fillMaxSize().background(RedlineColors.Bg)) {
            NavHost(
                navController = nav,
                startDestination = if (token.isNullOrBlank()) Routes.Login else Routes.PRList,
            ) {
                composable(Routes.Login) {
                    LoginScreen(
                        state = authState,
                        hasClientId = vm.hasClientId,
                        onStart = { vm.startDeviceFlow() },
                        onCancel = { vm.cancelDeviceFlow() },
                    )
                }

                composable(Routes.PRList) {
                    PRListScreen(
                        repoIdx = repoIdx,
                        onSelectRepo = { repoIdx = it },
                        onOpenPR = { pr -> nav.navigate(Routes.files(pr.id)) },
                    )
                }

                composable(
                    Routes.Files,
                    arguments = listOf(navArgument("prId") { type = NavType.IntType }),
                ) { entry ->
                    val prId = entry.arguments?.getInt("prId") ?: return@composable
                    val pr = SampleData.PRs.firstOrNull { it.id == prId } ?: return@composable
                    FileBrowserScreen(
                        pr = pr,
                        onOpenFile = { _, idx -> nav.navigate(Routes.diff(prId, idx)) },
                        onBack = { nav.popBackStack() },
                        onReview = { reviewPrId = prId; reviewOpen = true },
                    )
                }

                composable(
                    Routes.Diff,
                    arguments = listOf(
                        navArgument("prId") { type = NavType.IntType },
                        navArgument("fileIdx") { type = NavType.IntType },
                    ),
                ) { entry ->
                    val prId = entry.arguments?.getInt("prId") ?: return@composable
                    val pr = SampleData.PRs.firstOrNull { it.id == prId } ?: return@composable
                    var fileIdx by remember(prId) { mutableIntStateOf(entry.arguments?.getInt("fileIdx") ?: 0) }
                    DiffViewScreen(
                        pr = pr,
                        fileIdx = fileIdx,
                        setFileIdx = { fileIdx = it },
                        onBack = { nav.popBackStack() },
                        onCommentLine = { req ->
                            composer = ComposerContext(req.side, req.line, req.text, req.fileShort)
                        },
                        onReview = { reviewPrId = prId; reviewOpen = true },
                        pendingComments = pending,
                    )
                }
            }

            composer?.let { ctx ->
                CommentComposer(
                    context = ctx,
                    onCancel = { composer = null },
                    onSubmit = { submit ->
                        vm.addPending(submit.toPending())
                        composer = null
                        vm.flash(
                            if (submit.kind == ComposerKind.StartReview)
                                "Comment added · review started"
                            else "Comment added to review"
                        )
                        if (submit.kind == ComposerKind.StartReview) {
                            reviewPrId = SampleData.PRs.firstOrNull { it.id != 0 }?.id ?: 0
                            reviewOpen = true
                        }
                    },
                )
            }

            if (reviewOpen) {
                ReviewSheet(
                    prId = reviewPrId,
                    pendingCount = pending.size,
                    onCancel = { reviewOpen = false },
                    onSubmit = { sub ->
                        reviewOpen = false
                        vm.clearPending()
                        val label = when (sub.verdict) {
                            com.redline.viewer.data.ReviewVerdict.Approve -> "approved"
                            com.redline.viewer.data.ReviewVerdict.RequestChanges -> "requested changes"
                            com.redline.viewer.data.ReviewVerdict.Comment -> "commented"
                        }
                        vm.flash("Review submitted · $label")
                    },
                )
            }

            toast?.let { msg ->
                LaunchedEffect(msg) {
                    delay(2200)
                    vm.consumeToast()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(RedlineColors.Text)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        msg,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = RedlineColors.Bg,
                    )
                }
            }
        }
    }
}

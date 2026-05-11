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
import androidx.compose.ui.graphics.Color
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
import com.redline.viewer.data.CheckSummary
import com.redline.viewer.data.PR
import com.redline.viewer.data.SampleData
import com.redline.viewer.data.github.GhPull
import com.redline.viewer.data.timeAgo
import com.redline.viewer.ui.diff.DiffViewScreen
import com.redline.viewer.ui.files.FileBrowserScreen
import com.redline.viewer.ui.login.LoginScreen
import com.redline.viewer.ui.prlist.PRListScreen
import com.redline.viewer.ui.repos.RepoPickerScreen
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
    const val Repos = "repos"
    const val PRList = "prList"
    const val Files = "files"
    const val Diff = "diff/{fileIdx}"

    fun diff(fileIdx: Int) = "diff/$fileIdx"
}

@Composable
fun RedlineApp() {
    val vm: AppViewModel = viewModel()
    val token by vm.token.collectAsState()
    val authState by vm.authState.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val repos by vm.repos.collectAsState()
    val pulls by vm.pulls.collectAsState()
    val activeRepo by vm.activeRepo.collectAsState()
    val activePull by vm.activePull.collectAsState()
    val pending by vm.pending.collectAsState()
    val toast by vm.toast.collectAsState()

    var composer by remember { mutableStateOf<ComposerContext?>(null) }
    var reviewOpen by remember { mutableStateOf(false) }

    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

    // Login ↔ everything-else routing follows token state.
    LaunchedEffect(token) {
        val authed = !token.isNullOrBlank()
        when {
            authed && currentRoute == Routes.Login -> {
                nav.navigate(Routes.Repos) {
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
                startDestination = if (token.isNullOrBlank()) Routes.Login else Routes.Repos,
            ) {
                composable(Routes.Login) {
                    LoginScreen(
                        state = authState,
                        hasClientId = vm.hasClientId,
                        onStart = { vm.startDeviceFlow() },
                        onCancel = { vm.cancelDeviceFlow() },
                    )
                }

                composable(Routes.Repos) {
                    LaunchedEffect(Unit) { vm.ensureReposLoaded() }
                    RepoPickerScreen(
                        viewer = viewer,
                        repos = repos,
                        onPickRepo = { r ->
                            vm.setActiveRepo(r)
                            nav.navigate(Routes.PRList)
                        },
                        onSignOut = { vm.signOut() },
                        onRetry = { vm.ensureReposLoaded(force = true) },
                    )
                }

                composable(Routes.PRList) {
                    val repo = activeRepo
                    if (repo == null) {
                        LaunchedEffect(Unit) { nav.popBackStack(Routes.Repos, inclusive = false) }
                    } else {
                        PRListScreen(
                            repo = repo,
                            pulls = pulls,
                            onBack = { nav.popBackStack() },
                            onOpenPR = { pull ->
                                vm.setActivePull(pull)
                                nav.navigate(Routes.Files)
                            },
                            onRetry = { vm.ensurePullsLoaded(repo, force = true) },
                        )
                    }
                }

                composable(Routes.Files) {
                    val pull = activePull
                    if (pull == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        FileBrowserScreen(
                            pr = pull.toSamplePR(),
                            onOpenFile = { _, idx -> nav.navigate(Routes.diff(idx)) },
                            onBack = { nav.popBackStack() },
                            onReview = { reviewOpen = true },
                        )
                    }
                }

                composable(
                    Routes.Diff,
                    arguments = listOf(navArgument("fileIdx") { type = NavType.IntType }),
                ) { entry ->
                    val pull = activePull
                    if (pull == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        var fileIdx by remember(pull.number) {
                            mutableIntStateOf(entry.arguments?.getInt("fileIdx") ?: 0)
                        }
                        DiffViewScreen(
                            pr = pull.toSamplePR(),
                            fileIdx = fileIdx,
                            setFileIdx = { fileIdx = it },
                            onBack = { nav.popBackStack() },
                            onCommentLine = { req ->
                                composer = ComposerContext(req.side, req.line, req.text, req.fileShort)
                            },
                            onReview = { reviewOpen = true },
                            pendingComments = pending,
                        )
                    }
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
                        if (submit.kind == ComposerKind.StartReview) reviewOpen = true
                    },
                )
            }

            if (reviewOpen) {
                ReviewSheet(
                    prId = activePull?.number ?: 0,
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

/**
 * Wrap a real GhPull in the sample-data PR struct so the existing
 * FileBrowserScreen / DiffViewScreen can render its header. The file
 * list, checks, diffs and inline comments below the header still
 * come from SampleData — those screens haven't been ported to live
 * GitHub data yet.
 */
private fun GhPull.toSamplePR(): PR {
    val palette = listOf(
        0xFFF97316, 0xFF22D3EE, 0xFF3B82F6, 0xFFA855F7, 0xFFEC4899,
        0xFF14B8A6, 0xFFFBBF24, 0xFFEF4444, 0xFF8B5CF6, 0xFF10B981,
    )
    val login = user?.login.orEmpty()
    val avatar = Color(palette[(login.hashCode().toUInt().toInt() and 0x7FFFFFFF) % palette.size])
    val sampleStats = SampleData.PRs.first()
    return PR(
        id = number,
        repo = "live",
        title = title,
        author = login.ifEmpty { "?" },
        avatar = avatar,
        branch = head.ref,
        base = base.ref,
        files = changed_files ?: sampleStats.files,
        additions = additions ?: sampleStats.additions,
        deletions = deletions ?: sampleStats.deletions,
        opened = timeAgo(updated_at),
        draft = draft,
        checks = CheckSummary.Pass,
        comments = comments + review_comments,
    )
}

package com.redline.viewer

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import com.redline.viewer.data.PendingComment
import com.redline.viewer.data.ReviewVerdict
import com.redline.viewer.ui.diff.DiffViewScreen
import com.redline.viewer.ui.files.FileBrowserScreen
import com.redline.viewer.ui.files.MetadataKind
import com.redline.viewer.ui.files.MetadataPicker
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
fun RedlineApp(
    oauthCallback: Uri? = null,
    onOauthCallbackConsumed: () -> Unit = {},
) {
    val vm: AppViewModel = viewModel()
    val token by vm.token.collectAsState()
    val authState by vm.authState.collectAsState()
    val context = LocalContext.current

    // Hand any incoming `redline://oauth?…` redirect off to the VM. The
    // MainActivity captures it; we just forward it.
    LaunchedEffect(oauthCallback) {
        val cb = oauthCallback ?: return@LaunchedEffect
        vm.handleCallback(cb)
        onOauthCallbackConsumed()
    }

    val launchSignIn: () -> Unit = launch@{
        val current = authState
        val uri = if (current is com.redline.viewer.data.github.AuthState.WaitingForCallback) {
            current.authorizeUri
        } else {
            vm.beginSignIn() ?: return@launch
        }
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tab.launchUrl(context, uri)
    }

    val viewer by vm.viewer.collectAsState()
    val repos by vm.repos.collectAsState()
    val pulls by vm.pulls.collectAsState()
    val searchedPulls by vm.searchedPulls.collectAsState()
    val resolvingPullId by vm.resolvingPullId.collectAsState()
    val activeRepo by vm.activeRepo.collectAsState()
    val activePull by vm.activePull.collectAsState()
    val detail by vm.detail.collectAsState()
    val metadataOptions by vm.metadataOptions.collectAsState()
    val metadataSaving by vm.metadataSaving.collectAsState()

    // UI ephemera — owned here, not the VM. Dying on process death is the
    // desired behaviour (pending review drafts aren't on the GitHub server
    // yet, and a half-open bottom sheet shouldn't survive a relaunch).
    var pendingComments by remember { mutableStateOf<List<PendingComment>>(emptyList()) }
    var composer by remember { mutableStateOf<ComposerContext?>(null) }
    var reviewOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var viewedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var metadataPicker by remember { mutableStateOf<MetadataKind?>(null) }

    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

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
                        onStart = launchSignIn,
                        onCancel = { vm.cancelSignIn() },
                    )
                }

                composable(Routes.Repos) {
                    LaunchedEffect(Unit) { vm.ensureReposLoaded() }
                    RepoPickerScreen(
                        viewer = viewer,
                        repos = repos,
                        searchedPulls = searchedPulls,
                        resolvingPullNumber = resolvingPullId?.toInt(),
                        onPickRepo = { r ->
                            vm.setActiveRepo(r)
                            nav.navigate(Routes.PRList)
                        },
                        onPickSearchedPull = { item ->
                            vm.openSearchedPull(item) {
                                nav.navigate(Routes.Files)
                            }
                        },
                        onSignOut = { vm.signOut() },
                        onRetry = { vm.ensureReposLoaded(force = true) },
                        onLoadPulls = { vm.ensureSearchedPullsLoaded() },
                        onRetryPulls = { vm.ensureSearchedPullsLoaded(force = true) },
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
                            onRetry = { vm.ensurePullsLoaded(force = true) },
                        )
                    }
                }

                composable(Routes.Files) {
                    val pull = activePull
                    if (pull == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        FileBrowserScreen(
                            pull = pull,
                            detail = detail,
                            onOpenFile = { _, idx -> nav.navigate(Routes.diff(idx)) },
                            onBack = { nav.popBackStack() },
                            onReview = { reviewOpen = true },
                            onRetry = { vm.loadDetail(force = true) },
                            onEditMetadata = { kind -> metadataPicker = kind },
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
                            pull = pull,
                            detail = detail,
                            fileIdx = fileIdx,
                            setFileIdx = { fileIdx = it },
                            onBack = { nav.popBackStack() },
                            onCommentLine = { req ->
                                composer = ComposerContext(req.side, req.line, req.text, req.fileShort)
                            },
                            onReview = { reviewOpen = true },
                            pendingComments = pendingComments,
                            onRetry = { vm.loadDetail(force = true) },
                            viewedFiles = viewedFiles,
                            onToggleViewed = { path ->
                                viewedFiles = if (viewedFiles.contains(path)) viewedFiles - path else viewedFiles + path
                            },
                        )
                    }
                }
            }

            composer?.let { ctx ->
                CommentComposer(
                    context = ctx,
                    onCancel = { composer = null },
                    onSubmit = { submit ->
                        pendingComments = pendingComments + submit.toPending()
                        composer = null
                        toast = if (submit.kind == ComposerKind.StartReview)
                            "Comment added · review started"
                        else "Comment added to review"
                        if (submit.kind == ComposerKind.StartReview) reviewOpen = true
                    },
                )
            }

            metadataPicker?.let { kind ->
                val currentPull = activePull
                MetadataPicker(
                    kind = kind,
                    options = metadataOptions,
                    currentAssignees = currentPull?.assignees?.map { it.login }.orEmpty(),
                    currentReviewers = currentPull?.requested_reviewers?.map { it.login }.orEmpty(),
                    currentLabels = currentPull?.labels?.map { it.name }.orEmpty(),
                    currentMilestone = currentPull?.milestone?.number,
                    saving = metadataSaving,
                    onLoad = { vm.ensureMetadataOptionsLoaded() },
                    onRetry = { vm.ensureMetadataOptionsLoaded(force = true) },
                    onCancel = { metadataPicker = null },
                    onSaveAssignees = { logins ->
                        vm.setPullAssignees(logins) { err ->
                            if (err == null) {
                                metadataPicker = null
                                toast = "Assignees updated"
                            } else {
                                toast = "Save failed: ${err.message ?: "unknown"}"
                            }
                        }
                    },
                    onSaveReviewers = { logins ->
                        vm.setPullReviewers(logins) { err ->
                            if (err == null) {
                                metadataPicker = null
                                toast = "Reviewers updated"
                            } else {
                                toast = "Save failed: ${err.message ?: "unknown"}"
                            }
                        }
                    },
                    onSaveLabels = { labels ->
                        vm.setPullLabels(labels) { err ->
                            if (err == null) {
                                metadataPicker = null
                                toast = "Labels updated"
                            } else {
                                toast = "Save failed: ${err.message ?: "unknown"}"
                            }
                        }
                    },
                    onSaveMilestone = { num ->
                        vm.setPullMilestone(num) { err ->
                            if (err == null) {
                                metadataPicker = null
                                toast = "Milestone updated"
                            } else {
                                toast = "Save failed: ${err.message ?: "unknown"}"
                            }
                        }
                    },
                )
            }

            if (reviewOpen) {
                ReviewSheet(
                    prId = activePull?.number ?: 0,
                    pendingCount = pendingComments.size,
                    onCancel = { reviewOpen = false },
                    onSubmit = { sub ->
                        reviewOpen = false
                        pendingComments = emptyList()
                        toast = when (sub.verdict) {
                            ReviewVerdict.Approve -> "Review submitted · approved"
                            ReviewVerdict.RequestChanges -> "Review submitted · requested changes"
                            ReviewVerdict.Comment -> "Review submitted · commented"
                        }
                    },
                )
            }

            toast?.let { msg ->
                LaunchedEffect(msg) {
                    delay(2200)
                    toast = null
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

package com.redline.viewer.data

import androidx.compose.ui.graphics.Color

object SampleData {

    val Repos = listOf(
        Repo("core", "vercel", "next.js", "124k"),
        Repo("tools", "redline", "cli", "2.1k"),
        Repo("infra", "redline", "edge-runtime", "847"),
    )

    val PRs = listOf(
        PR(1842, "core",
            "fix(router): preserve scroll restoration when navigating back to dynamic route",
            "shu-vercel", Color(0xFFF97316),
            "fix/scroll-restoration-dynamic", "canary",
            7, 184, 92, "2h", false, CheckSummary.Pass, 4),
        PR(1839, "core",
            "feat(turbopack): incremental css module resolution",
            "tobiaskoppers", Color(0xFF22D3EE),
            "feat/turbo-css-incremental", "canary",
            23, 1247, 416, "5h", false, CheckSummary.Pending, 18),
        PR(1835, "core",
            "chore(deps): bump @swc/core to 1.7.42",
            "renovate-bot", Color(0xFF3B82F6),
            "renovate/swc-core", "canary",
            2, 18, 18, "1d", false, CheckSummary.Pass, 0),
        PR(1830, "core",
            "WIP: experiment with partial prerendering for app router metadata",
            "leerob", Color(0xFFA855F7),
            "experiment/ppr-metadata", "canary",
            14, 612, 88, "2d", true, CheckSummary.Fail, 7),
        PR(1828, "core",
            "docs: clarify caching semantics in fetch() override section",
            "molefrog", Color(0xFFEC4899),
            "docs/fetch-caching-clarify", "canary",
            1, 42, 11, "3d", false, CheckSummary.Pass, 2),
    )

    val Files = listOf(
        ChangedFile("packages/next/src/client/components/router-reducer/reducers/navigate-reducer.ts",
            "navigate-reducer.ts", 47, 23, FileStatus.Modified),
        ChangedFile("packages/next/src/client/components/app-router.tsx",
            "app-router.tsx", 38, 19, FileStatus.Modified),
        ChangedFile("packages/next/src/client/components/scroll-restorer.ts",
            "scroll-restorer.ts", 64, 0, FileStatus.Added),
        ChangedFile("packages/next/src/shared/lib/router/utils/get-scroll-position.ts",
            "get-scroll-position.ts", 21, 8, FileStatus.Modified),
        ChangedFile("test/e2e/app-dir/scroll-restoration/index.test.ts",
            "index.test.ts", 14, 0, FileStatus.Added),
        ChangedFile("packages/next/src/client/components/router-reducer/reducers/refresh-reducer.ts",
            "refresh-reducer.ts", 0, 42, FileStatus.Deleted),
        ChangedFile("pnpm-lock.yaml", "pnpm-lock.yaml", 0, 0, FileStatus.Modified),
    )

    private fun ctx(o: Int, oT: String, n: Int, nT: String) =
        DiffRow(DiffRowType.Context, DiffLine(o, oT), DiffLine(n, nT))
    private fun del(o: Int, oT: String) =
        DiffRow(DiffRowType.Del, old = DiffLine(o, oT))
    private fun add(n: Int, nT: String) =
        DiffRow(DiffRowType.Add, newer = DiffLine(n, nT))
    private fun hunk(text: String) = DiffRow(DiffRowType.Hunk, hunkText = text)

    private val DiffNavigate = listOf(
        hunk("@@ -142,16 +142,28 @@ function navigateReducer("),
        ctx(142, "export function navigateReducer(", 142, "export function navigateReducer("),
        ctx(143, "  state: ReadonlyReducerState,",   143, "  state: ReadonlyReducerState,"),
        ctx(144, "  action: NavigateAction",          144, "  action: NavigateAction"),
        ctx(145, "): ReducerState {",                  145, "): ReducerState {"),
        del(146, "  const { url, isExternalUrl, navigateType, shouldScroll } = action"),
        add(146, "  const {"),
        add(147, "    url,"),
        add(148, "    isExternalUrl,"),
        add(149, "    navigateType,"),
        add(150, "    shouldScroll,"),
        add(151, "    scrollRestorationKey,"),
        add(152, "  } = action"),
        ctx(147, "",                                   153, ""),
        del(148, "  const mutable = state.nextUrl ? { ...state } : state"),
        add(154, "  const mutable: Mutable = state.nextUrl"),
        add(155, "    ? { ...state, prevScrollY: getScrollY() }"),
        add(156, "    : state"),
        ctx(149, "  const href = createHrefFromUrl(url)", 157, "  const href = createHrefFromUrl(url)"),
        ctx(150, "",                                   158, ""),
        ctx(151, "  if (isExternalUrl) {",            159, "  if (isExternalUrl) {"),
        ctx(152, "    return handleExternalUrl(state, mutable, url.toString(), pendingPush)",
            160, "    return handleExternalUrl(state, mutable, url.toString(), pendingPush)"),
        ctx(153, "  }",                                161, "  }"),
        ctx(154, "",                                   162, ""),
        hunk("@@ -201,12 +213,22 @@ function navigateReducer("),
        ctx(201, "  if (shouldScroll) {",             213, "  if (shouldScroll) {"),
        del(202, "    mutable.scrollableSegments = []"),
        del(203, "    mutable.hashFragment = hash"),
        add(214, "    const restored = scrollRestorationKey"),
        add(215, "      ? readScrollPosition(scrollRestorationKey)"),
        add(216, "      : null"),
        add(217, ""),
        add(218, "    if (restored) {"),
        add(219, "      mutable.pendingScroll = restored"),
        add(220, "    } else {"),
        add(221, "      mutable.scrollableSegments = []"),
        add(222, "      mutable.hashFragment = hash"),
        add(223, "    }"),
        ctx(204, "  }",                                224, "  }"),
        ctx(205, "",                                   225, ""),
        ctx(206, "  return mutable",                  226, "  return mutable"),
        ctx(207, "}",                                  227, "}"),
    )

    private val DiffAppRouter = listOf(
        hunk("@@ -88,7 +88,12 @@ export default function AppRouter("),
        ctx(88, "  const initialCanonicalUrl = useRef(canonicalUrl)", 88, "  const initialCanonicalUrl = useRef(canonicalUrl)"),
        ctx(89, "  const initialTree = useRef(initialTreeFromProps)", 89, "  const initialTree = useRef(initialTreeFromProps)"),
        ctx(90, "",                                                    90, ""),
        del(91, "  const [reducerState, dispatch] = useReducer(reducer, initialState)"),
        add(91, "  const scrollRestorer = useRef(new ScrollRestorer())"),
        add(92, "  const [reducerState, dispatch] = useReducer("),
        add(93, "    reducer,"),
        add(94, "    initialState,"),
        add(95, "    (s) => withScrollRestoration(s, scrollRestorer.current)"),
        add(96, "  )"),
        ctx(92, "",                                                    97, ""),
        ctx(93, "  useEffect(() => {",                                 98, "  useEffect(() => {"),
        ctx(94, "    return () => mountedRef.current = false",        99, "    return () => mountedRef.current = false"),
        ctx(95, "  }, [])",                                           100, "  }, [])"),
    )

    private val DiffScrollRestorer = listOf(
        hunk("@@ -0,0 +1,64 @@"),
        add(1,  "// Tracks scroll positions per navigation entry so we can restore them"),
        add(2,  "// when the user navigates back via popstate, even into dynamic routes."),
        add(3,  ""),
        add(4,  "export interface ScrollPosition {"),
        add(5,  "  x: number"),
        add(6,  "  y: number"),
        add(7,  "  capturedAt: number"),
        add(8,  "}"),
        add(9,  ""),
        add(10, "export class ScrollRestorer {"),
        add(11, "  private positions = new Map<string, ScrollPosition>()"),
        add(12, ""),
        add(13, "  remember(key: string, position: ScrollPosition) {"),
        add(14, "    this.positions.set(key, position)"),
        add(15, "  }"),
        add(16, ""),
        add(17, "  recall(key: string): ScrollPosition | null {"),
        add(18, "    return this.positions.get(key) ?? null"),
        add(19, "  }"),
        add(20, ""),
        add(21, "  forget(key: string) {"),
        add(22, "    this.positions.delete(key)"),
        add(23, "  }"),
        add(24, "}"),
    )

    private val DiffGetScroll = listOf(
        hunk("@@ -12,8 +12,21 @@ export function getScrollPosition("),
        ctx(12, "export function getScrollPosition(): ScrollPosition {", 12, "export function getScrollPosition("),
        add(13, "  target?: HTMLElement | null"),
        add(14, "): ScrollPosition {"),
        ctx(13, "  if (typeof window === \"undefined\") {", 15, "  if (typeof window === \"undefined\") {"),
        del(14, "    return { x: 0, y: 0 }"),
        add(16, "    return { x: 0, y: 0, capturedAt: 0 }"),
        ctx(15, "  }",  17, "  }"),
        ctx(16, "",     18, ""),
        ctx(17, "  return {", 19, "  if (target) {"),
        del(18, "    x: window.scrollX,"),
        del(19, "    y: window.scrollY,"),
        add(20, "    return {"),
        add(21, "      x: target.scrollLeft,"),
        add(22, "      y: target.scrollTop,"),
        add(23, "      capturedAt: performance.now(),"),
        ctx(20, "  }", 24, "    }"),
        add(25, "  }"),
        ctx(21, "}",   26, "}"),
    )

    val Diffs: Map<String, List<DiffRow>> = mapOf(
        "navigate-reducer.ts"     to DiffNavigate,
        "app-router.tsx"          to DiffAppRouter,
        "scroll-restorer.ts"      to DiffScrollRestorer,
        "get-scroll-position.ts"  to DiffGetScroll,
        "index.test.ts"           to DiffScrollRestorer,
        "refresh-reducer.ts"      to DiffNavigate,
        "pnpm-lock.yaml"          to listOf(hunk("@@ Large diff suppressed @@")),
    )

    val Checks: Map<Int, List<Check>> = mapOf(
        1842 to listOf(
            Check("build",          CheckSummary.Pass,    "2m 14s", true,  "CI / Build"),
            Check("lint",           CheckSummary.Pass,    "34s",    true,  "CI / Lint"),
            Check("test (node 20)", CheckSummary.Pass,    "4m 02s", true,  "CI / Test Suite"),
            Check("test (node 22)", CheckSummary.Pass,    "4m 08s", false, "CI / Test Suite"),
            Check("e2e (chrome)",   CheckSummary.Pass,    "6m 41s", true,  "E2E / Playwright"),
            Check("e2e (firefox)",  CheckSummary.Fail,    "7m 12s", false, "E2E / Playwright",
                failNote = "1 flaky test: scroll-restoration > deep-link"),
            Check("bundle size",    CheckSummary.Pass,    "1m 03s", true,  "Bundle Analyzer", delta = "+0.4kb"),
            Check("deploy preview", CheckSummary.Pass,    "1m 48s", false, "Vercel",          url   = "next-pr-1842.vercel.app"),
            Check("changeset",      CheckSummary.Pending, "—",      true,  "Release / Changeset"),
        ),
        1839 to listOf(
            Check("build",          CheckSummary.Pending, "1m 22s", true, "CI / Build"),
            Check("lint",           CheckSummary.Pass,    "41s",    true, "CI / Lint"),
            Check("test (node 20)", CheckSummary.Pending, "—",      true, "CI / Test Suite"),
        ),
    )

    val Comments: Map<String, List<Thread>> = mapOf(
        "navigate-reducer.ts" to listOf(
            Thread(CommentSide.New, 156, listOf(
                Comment("leerob",     Color(0xFFA855F7), "1h ago",
                    "Nit: do we still need the ternary here now that `prevScrollY` is always captured upstream? Could simplify to a plain spread.",
                    resolved = false),
                Comment("shu-vercel", Color(0xFFF97316), "47m ago",
                    "Good catch. The upstream capture is conditional (only on push). Will leave for the followup PR — adding a TODO.",
                    resolved = false),
            )),
            Thread(CommentSide.New, 221, listOf(
                Comment("tobiaskoppers", Color(0xFF22D3EE), "2h ago",
                    "Worth pulling this branch into a small helper — `applyScrollFallback(mutable, hash)` — same pattern shows up in refresh-reducer too.",
                    resolved = true),
            )),
        ),
        "scroll-restorer.ts" to listOf(
            Thread(CommentSide.New, 11, listOf(
                Comment("molefrog", Color(0xFFEC4899), "3h ago",
                    "Map will grow unbounded on long sessions. Consider an LRU cap (256 entries?) or evict on `popstate` past the back-forward distance.",
                    resolved = false),
            )),
        ),
    )
}

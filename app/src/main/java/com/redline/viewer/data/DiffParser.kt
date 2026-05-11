package com.redline.viewer.data

/**
 * Parse a GitHub-style unified diff `patch` string into our split-view
 * [DiffRow] model.
 *
 * The patch shape per hunk:
 *
 *     @@ -oldStart,oldCount +newStart,newCount @@ optional_section_header
 *      context line
 *     -removed line
 *     +added line
 *     \ No newline at end of file        ← marker, dropped
 *
 * For split rendering we emit one row per source line. Pure deletions
 * get a null `newer`; pure additions get a null `old`. Adjacent
 * del/add lines are not paired into the same row — the prototype's
 * `screens.jsx` doesn't either; the column layout aligns them visually
 * via blank placeholders.
 */
fun parseUnifiedDiff(patch: String?): List<DiffRow> {
    if (patch.isNullOrEmpty()) return emptyList()

    val rows = mutableListOf<DiffRow>()
    var oldN = 0
    var newN = 0

    for (raw in patch.split('\n')) {
        if (raw.isEmpty()) continue
        when {
            raw.startsWith("@@") -> {
                val parsed = parseHunkHeader(raw)
                if (parsed != null) {
                    oldN = parsed.first
                    newN = parsed.second
                }
                rows += DiffRow(DiffRowType.Hunk, hunkText = raw)
            }
            raw.startsWith("\\") -> { /* "\ No newline at end of file" — drop */ }
            raw.startsWith("+") -> {
                rows += DiffRow(DiffRowType.Add, newer = DiffLine(newN, raw.substring(1)))
                newN++
            }
            raw.startsWith("-") -> {
                rows += DiffRow(DiffRowType.Del, old = DiffLine(oldN, raw.substring(1)))
                oldN++
            }
            raw.startsWith(" ") -> {
                val text = raw.substring(1)
                rows += DiffRow(DiffRowType.Context, old = DiffLine(oldN, text), newer = DiffLine(newN, text))
                oldN++
                newN++
            }
            else -> {
                // Some patches (e.g. binary files) include lines that don't
                // start with one of the standard prefixes. Treat as context.
                rows += DiffRow(DiffRowType.Context, old = DiffLine(oldN, raw), newer = DiffLine(newN, raw))
                oldN++
                newN++
            }
        }
    }
    return rows
}

private val HUNK_RE = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

private fun parseHunkHeader(line: String): Pair<Int, Int>? {
    val m = HUNK_RE.find(line) ?: return null
    val oldStart = m.groupValues[1].toIntOrNull() ?: return null
    val newStart = m.groupValues[2].toIntOrNull() ?: return null
    return oldStart to newStart
}

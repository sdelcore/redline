package com.redline.viewer.data

import java.time.Instant
import java.time.temporal.ChronoUnit

fun timeAgo(iso: String?, now: Instant = Instant.now()): String {
    if (iso.isNullOrBlank()) return ""
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val secs = ChronoUnit.SECONDS.between(then, now).coerceAtLeast(0)
    return when {
        secs < 60 -> "${secs}s"
        secs < 3_600 -> "${secs / 60}m"
        secs < 86_400 -> "${secs / 3_600}h"
        secs < 86_400 * 7 -> "${secs / 86_400}d"
        secs < 86_400 * 30 -> "${secs / (86_400 * 7)}w"
        secs < 86_400 * 365 -> "${secs / (86_400 * 30)}mo"
        else -> "${secs / (86_400 * 365)}y"
    }
}

private val LANG_COLORS: Map<String, Long> = mapOf(
    "TypeScript" to 0xFF3178C6,
    "JavaScript" to 0xFFF1E05A,
    "Kotlin" to 0xFFA97BFF,
    "Rust" to 0xFFDEA584,
    "Python" to 0xFF3572A5,
    "Swift" to 0xFFF05138,
    "Go" to 0xFF00ADD8,
    "Java" to 0xFFB07219,
    "Ruby" to 0xFF701516,
    "C" to 0xFF555555,
    "C++" to 0xFFF34B7D,
    "C#" to 0xFF178600,
    "PHP" to 0xFF4F5D95,
    "Shell" to 0xFF89E051,
    "HTML" to 0xFFE34C26,
    "CSS" to 0xFF563D7C,
    "MDX" to 0xFFFCB32C,
    "Nix" to 0xFF7E7EFF,
    "Markdown" to 0xFF083FA1,
    "Dart" to 0xFF00B4AB,
    "Lua" to 0xFF000080,
)

fun languageColor(language: String?): Long =
    language?.let { LANG_COLORS[it] } ?: 0xFF6E7681

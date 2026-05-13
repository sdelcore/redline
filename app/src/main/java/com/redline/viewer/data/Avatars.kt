package com.redline.viewer.data

import androidx.compose.ui.graphics.Color

private val AVATAR_PALETTE = listOf(
    0xFFF97316, 0xFF22D3EE, 0xFF3B82F6, 0xFFA855F7, 0xFFEC4899,
    0xFF14B8A6, 0xFFFBBF24, 0xFFEF4444, 0xFF8B5CF6, 0xFF10B981,
)

fun avatarColorFor(login: String): Color {
    if (login.isEmpty()) return Color(0xFF6E7681)
    val idx = (login.hashCode().toUInt().toInt() and 0x7FFFFFFF) % AVATAR_PALETTE.size
    return Color(AVATAR_PALETTE[idx])
}

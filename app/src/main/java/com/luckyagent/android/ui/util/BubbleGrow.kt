package com.luckyagent.android.ui.util

/** Stable pieces of one bubble. A streaming append only changes the last piece. */
fun bubblePieces(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val parts = text.split("\n\n")
    return if (parts.size == 1) listOf(text) else parts
}

fun pieceHash(text: String): Int = text.hashCode()

/**
 * Height to draw while a bubble grows.
 * A drag, a zero delta, or a finished transition uses the new height.
 */
fun bubbleDrawHeight(previous: Int, next: Int, progress: Float, dragging: Boolean): Int {
    if (dragging || previous <= 0 || previous == next || progress >= 1f) return next
    return (previous + (next - previous) * progress.coerceIn(0f, 1f)).toInt()
}

fun bubbleTransitionDone(progress: Float): Boolean = progress >= 1f

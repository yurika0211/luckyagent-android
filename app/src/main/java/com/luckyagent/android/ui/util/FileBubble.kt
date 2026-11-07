package com.luckyagent.android.ui.util

/** Where a hold-to-talk finger is, in pixels from the press point. */
data class HoldTalkGesture(
    val dx: Float,
    val dy: Float,
    val slopPx: Float,
    val cancelPx: Float,
    val lockPx: Float,
)

enum class HoldTalkPhase { Idle, Holding, CancelArmed, Locking, Locked }

/** Past touch slop starts the recorder. Left arms cancel. Up moves toward lock. */
fun holdTalkPhase(gesture: HoldTalkGesture, locked: Boolean): HoldTalkPhase {
    if (locked) return HoldTalkPhase.Locked
    val moved = kotlin.math.hypot(gesture.dx.toDouble(), gesture.dy.toDouble()) >= gesture.slopPx
    if (!moved && gesture.dx == 0f && gesture.dy == 0f) return HoldTalkPhase.Idle
    if (!moved) return HoldTalkPhase.Holding
    if (gesture.dx <= -gesture.cancelPx && gesture.dx < gesture.dy) return HoldTalkPhase.CancelArmed
    if (gesture.dy <= -gesture.slopPx && gesture.dy < gesture.dx) return HoldTalkPhase.Locking
    return HoldTalkPhase.Holding
}

/** 0 at rest, 1 once the finger has travelled the full lock distance. */
fun holdTalkLockProgress(dy: Float, lockPx: Float): Float {
    if (lockPx <= 0f) return 0f
    return ((-dy) / lockPx).coerceIn(0f, 1f)
}

/** Release while cancel is armed drops the file. Otherwise the voice is sent. */
fun holdTalkReleaseSends(phase: HoldTalkPhase): Boolean = phase != HoldTalkPhase.CancelArmed

/** A tap that never crossed the slop does not create a file or a cancel toast. */
fun holdTalkTapWasAccidental(phase: HoldTalkPhase, movedPastSlop: Boolean): Boolean =
    !movedPastSlop && phase != HoldTalkPhase.Locked && phase != HoldTalkPhase.CancelArmed

fun nextLockHintCount(shown: Int): Int? = if (shown < 3) shown + 1 else null

fun nextPauseHintSeen(seen: Boolean): Boolean = !seen

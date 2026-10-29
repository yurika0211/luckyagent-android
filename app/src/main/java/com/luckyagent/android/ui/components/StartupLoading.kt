package com.luckyagent.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import kotlin.math.cos
import kotlin.math.sin

/** How long a startup wait may stay on the clover before the caller should offer a retry. */
const val StartupWaitLimitMs = 8_000L

/**
 * One sentence for each thing the cold start is actually waiting on.
 * These stay stable so a reconnect does not invent a new status vocabulary.
 */
object StartupPhase {
    const val Connecting = "正在连接…"
    const val Sessions = "正在读取会话"
    const val History = "正在读取这轮对话"
    const val SessionsNext = "连上后会显示上次的会话"
    const val HistoryNext = "连上后会显示上次的对话"
}

/** After the wait limit, name the same step and the existing retry. Do not invent a connection failure for a history read. */
fun startupTimedOutCopy(phase: String): String = when (phase) {
    StartupPhase.Connecting -> "还没连上。可以重试、改地址或重新扫码。"
    StartupPhase.Sessions -> "会话还没读到。可以重试。"
    StartupPhase.History -> "这轮对话还没读到。可以重试。"
    else -> "还没完成。可以重试。"
}

/** Whether the system asked this view to reduce motion. The clover then stays still. */
fun startupMotionReduced(animatorScale: Float): Boolean = animatorScale == 0f

/** The visible sentence after the wait limit. Timed-out copy replaces the phase, it does not stack on it. */
fun startupSpokenPhase(phase: String, timedOut: Boolean): String =
    if (timedOut) startupTimedOutCopy(phase) else phase

/**
 * Three leaves, 120 degrees apart. While waiting, one leaf is bright and the
 * highlight walks around. Reduced motion keeps every leaf at the same weight.
 */
@Composable
fun CloverMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    animate: Boolean = true,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val still = !animate || startupMotionReduced(scale)
    val transition = rememberInfiniteTransition(label = "clover")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "clover-step",
    )
    Canvas(
        modifier
            .size(size)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription },
            ),
    ) {
        val radius = this.size.minDimension * 0.16f
        val orbit = this.size.minDimension * 0.22f
        val active = if (still) -1 else step.toInt().mod(3)
        repeat(3) { index ->
            val angle = Math.toRadians(-90.0 + index * 120.0)
            val cx = center.x + cos(angle).toFloat() * orbit
            val cy = center.y + sin(angle).toFloat() * orbit
            drawOval(
                color = if (still || index == active) CloverAccent else CloverLeaf,
                topLeft = Offset(cx - radius, cy - radius * 1.35f),
                size = Size(radius * 2f, radius * 2.7f),
            )
        }
        drawLine(
            color = CloverAccent,
            start = Offset(center.x, center.y),
            end = Offset(center.x, center.y + orbit * 1.15f),
            strokeWidth = this.size.minDimension * 0.06f,
        )
    }
}

/** A thin reconciliation strip. Cached content stays underneath. */
@Composable
fun StartupReconcileBar(
    phase: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = phase
                liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CloverMark(size = 18.dp)
        Text(phase, color = CloverText2, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Centered wait used only when there is no cached content to paint.
 * The next line says what appears after this step, instead of a bare spinner.
 */
@Composable
fun StartupWait(
    phase: String,
    next: String,
    modifier: Modifier = Modifier,
    waitLimitMs: Long = StartupWaitLimitMs,
    onTimedOut: (@Composable () -> Unit)? = null,
) {
    var timedOut by remember(phase) { mutableStateOf(false) }
    LaunchedEffect(phase) {
        timedOut = false
        delay(waitLimitMs)
        timedOut = true
    }
    val spoken = startupSpokenPhase(phase, timedOut)
    Column(
        modifier
            .widthIn(max = 420.dp)
            .semantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CloverMark(size = 42.dp, animate = !timedOut)
        Text(spoken, color = CloverText2, style = MaterialTheme.typography.bodyMedium)
        if (!timedOut) {
            Text(next, color = CloverText3, style = MaterialTheme.typography.bodySmall)
        } else {
            onTimedOut?.invoke()
        }
    }
}

package com.luckyagent.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

val LocalOpenNavigationDrawer = compositionLocalOf<(() -> Unit)?> { null }

data class SurfaceOpacity(
    val chrome: Float = 0.62f,
    val card: Float = 0.82f,
    val drawer: Float = 0.90f,
)

val LocalSurfaceOpacity = compositionLocalOf { SurfaceOpacity() }

/**
 * Shared top bar for every non-chat page: [nav or back] title/subtitle [actions].
 * The leading button is always on the left so it sits where the chat page's menu sits.
 * [eyebrow] is kept for call-site compatibility but no longer rendered.
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val openNavigation = LocalOpenNavigationDrawer.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalSurfaceOpacity.current.chrome),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    onBack != null -> IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    openNavigation != null -> IconButton(onClick = openNavigation) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Open navigation", tint = CloverText2)
                    }
                    else -> Spacer(Modifier.width(12.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = CloverText3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        }
    }
}

/**
 * Standard list card. Pass [onClick] instead of Modifier.clickable so the ripple
 * follows the rounded shape.
 */
@Composable
fun CloverCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surface.copy(alpha = LocalSurfaceOpacity.current.card)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    val body: @Composable () -> Unit = {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border, content = body)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border, content = body)
    }
}

/** Title row used at the top of list cards: optional leading mark, title + subtitle, trailing status. */
@Composable
fun CardTitleRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = CloverText2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    centered: Boolean = false,
) {
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 24.dp)
    Column(
        modifier = if (centered) base.fillMaxSize() else base,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (centered) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        if (icon != null) {
            icon()
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else null,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = CloverText2,
            textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else null,
        )
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        if (centered) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
    }
}

/** Collapsed by default; the full value remains selectable and copyable. */
@Composable
fun DetailDisclosure(label: String, value: String?, json: Boolean = false, previewLines: Int = 0) {
    if (value.isNullOrBlank()) return
    var expanded by remember(label, value) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val formatted = remember(value, json) {
        if (json) runCatching {
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(value))
        }.getOrDefault(value) else value
    }
    Column(Modifier.fillMaxWidth()) {
        if (!expanded && previewLines > 0) Text(value, maxLines = previewLines, overflow = TextOverflow.Ellipsis, color = CloverText2)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                Text(if (expanded) "收起 $label" else "查看$label")
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(formatted)) }) { Text("复制") }
        }
        if (expanded) SelectionContainer {
            Text(
                formatted,
                modifier = Modifier.fillMaxWidth(),
                style = if (json) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val color = when (status.lowercase()) {
        "failed", "failure", "error", "blocked" -> MaterialTheme.colorScheme.error
        "retrying", "warning", "reconnecting" -> com.luckyagent.android.ui.theme.CloverWarning
        "connected", "success", "completed", "done", "running", "in_progress", "active", "enabled", "healthy" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = .1f), shape = RoundedCornerShape(999.dp)) {
        Text(status, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Human label for the socket state, shared by the chat top bar and the navigation drawer. */
fun socketStateLabel(state: com.luckyagent.android.data.api.SocketState): String = when (state) {
    com.luckyagent.android.data.api.SocketState.Connected -> "已连接"
    com.luckyagent.android.data.api.SocketState.Running -> "运行中"
    com.luckyagent.android.data.api.SocketState.Connecting -> "连接中"
    com.luckyagent.android.data.api.SocketState.Reconnecting -> "重连中"
    com.luckyagent.android.data.api.SocketState.Error -> "连接出错"
    com.luckyagent.android.data.api.SocketState.Idle,
    com.luckyagent.android.data.api.SocketState.Closed -> "未连接"
}

fun socketStateLive(state: com.luckyagent.android.data.api.SocketState): Boolean =
    state == com.luckyagent.android.data.api.SocketState.Connected ||
        state == com.luckyagent.android.data.api.SocketState.Running

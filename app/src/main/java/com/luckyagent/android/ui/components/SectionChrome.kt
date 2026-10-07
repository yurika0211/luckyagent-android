package com.luckyagent.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
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

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val openNavigation = LocalOpenNavigationDrawer.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    ) {
        BoxWithConstraints {
        val compact = maxWidth < 480.dp || LocalDensity.current.fontScale > 1.2f
        Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(width = 3.dp, height = 38.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(999.dp),
            ) {}
            Column(Modifier.weight(1f)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 34.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CloverText2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!compact) Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
            if (openNavigation != null) {
                IconButton(onClick = openNavigation) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Open navigation")
                }
            }
        }
        if (compact) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
        }
        }
    }
}

@Composable
fun CloverCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
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
        "retrying", "warning" -> com.luckyagent.android.ui.theme.CloverWarning
        "connected", "success", "completed", "done", "running", "active", "enabled", "healthy" -> MaterialTheme.colorScheme.primary
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

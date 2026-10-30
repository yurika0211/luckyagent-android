package com.luckyagent.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.openui.OpenUiDelta
import com.luckyagent.android.data.openui.OpenUiNode
import com.luckyagent.android.data.openui.chartSeries
import com.luckyagent.android.data.openui.listItems
import com.luckyagent.android.data.openui.mapPoints
import com.luckyagent.android.data.openui.string
import com.luckyagent.android.data.openui.stringList
import com.luckyagent.android.data.openui.tableGrid
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun OpenUiTree(
    tree: OpenUiDelta,
    interactive: Boolean,
    onAction: (widgetId: String, label: String) -> Unit,
    imageHeaders: Map<String, String> = emptyMap(),
    imageBaseUrl: String = "",
) {
    if (tree.nodes.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (tree.state) {
                "streaming" -> "结构预览（生成中，不可点）"
                "waiting_input" -> "请选择"
                else -> "结构"
            },
            style = MaterialTheme.typography.labelSmall,
            color = CloverText3,
        )
        tree.nodes.forEach { node ->
            OpenUiNodeView(
                node = node,
                interactive = interactive && tree.state != "streaming",
                onAction = onAction,
                imageHeaders = imageHeaders,
                imageBaseUrl = imageBaseUrl,
            )
        }
    }
}

@Composable
private fun OpenUiNodeView(
    node: OpenUiNode,
    interactive: Boolean,
    onAction: (String, String) -> Unit,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
) {
    when (node.type) {
        "Table" -> OpenUiTable(node)
        "List" -> OpenUiList(node)
        "Chart" -> OpenUiChart(node)
        "Map" -> OpenUiMap(node)
        "Citation" -> OpenUiCitation(node)
        "Image" -> MarkdownImage(
            source = node.props.string("src").orEmpty(),
            alt = node.props.string("alt").orEmpty(),
            headers = imageHeaders,
            baseUrl = imageBaseUrl,
        )
        "Svg" -> {
            val markup = node.props.string("markup").orEmpty()
            val svg = staticSvgDocument(markup)
            if (svg != null) InlineSvgPreview(source = markup, svg = svg)
            else Text("示意图", color = CloverText2, style = MaterialTheme.typography.bodySmall)
        }
        "Markdown" -> MarkdownText(markdown = node.props.string("text").orEmpty())
        "Actions" -> OpenUiActions(node, interactive, onAction)
        else -> {}
    }
}

@Composable
private fun OpenUiTable(node: OpenUiNode) {
    val columns = node.props.stringList("columns")
    val (grid, extra) = node.tableGrid()
    if (grid.isEmpty()) return
    val colCount = grid.maxOf { it.size }
    val widths = List(colCount) { col ->
        ((grid.maxOf { it.getOrNull(col).orEmpty().length } * 8 + 16).coerceIn(64, 280)).dp
    }
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        grid.forEachIndexed { row, cells ->
            Row {
                repeat(colCount) { col ->
                    Text(
                        cells.getOrNull(col).orEmpty(),
                        color = CloverText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (row == 0 && columns.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .width(widths[col])
                            .border(1.dp, CloverLine)
                            .padding(8.dp),
                    )
                }
            }
        }
        if (extra > 0) {
            Text("另有 $extra 行", color = CloverText3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OpenUiList(node: OpenUiNode) {
    val (items, extra) = node.listItems()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                Text("${i + 1}. ", color = CloverText, style = MaterialTheme.typography.bodySmall)
                Text(item, color = CloverText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        if (extra > 0) {
            Text("另有 $extra 项", color = CloverText3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OpenUiCitation(node: OpenUiNode) {
    val source = node.props.string("source").orEmpty()
    val title = node.props.string("title") ?: node.props.string("label")
    val label = listOfNotNull(title, source.takeIf { it.isNotBlank() }).distinct().joinToString(" · ").ifBlank { "来源" }
    val url = source.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    val handler = LocalUriHandler.current
    Text(
        label,
        color = CloverText3,
        style = MaterialTheme.typography.bodySmall,
        modifier = if (url != null) Modifier.clickable { runCatching { handler.openUri(url) } } else Modifier,
    )
}

@Composable
private fun OpenUiChart(node: OpenUiNode) {
    val kind = node.props.string("kind") ?: "chart"
    val series = node.chartSeries().filter { it.values.isNotEmpty() }
    if (series.isEmpty()) {
        Text("图：$kind", color = CloverText2, style = MaterialTheme.typography.bodySmall)
        return
    }
    val max = series.maxOf { it.values.max() }.coerceAtLeast(0f)
    val min = series.minOf { it.values.min() }.coerceAtMost(0f)
    val span = (max - min).let { if (it == 0f) 1f else it }
    val bar = kind.contains("bar", ignoreCase = true) || kind.contains("柱", ignoreCase = true)
    val colors = listOf(CloverText, CloverText2, CloverText3)
    val groups = series.maxOf { it.values.size }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            series.forEachIndexed { i, ser ->
                Text(ser.name, color = colors[i % colors.size], style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(40.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(formatChartTick(max), color = CloverText3, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(formatChartTick(min), color = CloverText3, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp)) {
                val pad = 8.dp.toPx()
                val w = size.width - pad
                val h = size.height - pad * 2
                val originX = pad
                val originY = pad + h
                val axis = CloverLine
                drawLine(axis, Offset(originX, pad), Offset(originX, originY), strokeWidth = 1.dp.toPx())
                drawLine(axis, Offset(originX, originY), Offset(originX + w, originY), strokeWidth = 1.dp.toPx())
                if (bar) {
                    val slot = w / groups
                    val inner = slot * 0.8f
                    val barW = (inner / series.size).coerceAtLeast(3f)
                    series.forEachIndexed { s, ser ->
                        ser.values.forEachIndexed { i, v ->
                            val bh = ((v - min) / span) * h
                            val x = originX + i * slot + (slot - inner) / 2 + s * barW
                            drawRect(
                                color = colors[s % colors.size],
                                topLeft = Offset(x, originY - bh),
                                size = Size(barW, bh),
                            )
                        }
                    }
                } else {
                    val denom = (groups - 1).coerceAtLeast(1)
                    series.forEachIndexed { s, ser ->
                        val path = Path()
                        ser.values.forEachIndexed { i, v ->
                            val x = originX + w * i / denom
                            val y = originY - ((v - min) / span) * h
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(color = colors[s % colors.size], radius = 3.dp.toPx(), center = Offset(x, y))
                        }
                        drawPath(path, color = colors[s % colors.size], style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

private fun formatChartTick(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else "%.1f".format(value)

@Composable
private fun OpenUiMap(node: OpenUiNode) {
    val points = node.mapPoints()
    if (points.isEmpty()) {
        Text("地图", color = CloverText2, style = MaterialTheme.typography.bodySmall)
        return
    }
    val located = points.filter { it.lat != null && it.lng != null }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (located.size >= 1) {
            val minLat = located.minOf { it.lat!! }
            val maxLat = located.maxOf { it.lat!! }
            val minLng = located.minOf { it.lng!! }
            val maxLng = located.maxOf { it.lng!! }
            val dLat = (maxLat - minLat).let { if (it == 0.0) 1.0 else it }
            val dLng = (maxLng - minLng).let { if (it == 0.0) 1.0 else it }
            val color = CloverText
            Canvas(Modifier.fillMaxWidth().height(120.dp).border(1.dp, CloverLine, RoundedCornerShape(8.dp))) {
                val pad = 12.dp.toPx()
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                located.forEach { p ->
                    val x = pad + (((p.lng!! - minLng) / dLng) * w).toFloat()
                    val y = pad + h - (((p.lat!! - minLat) / dLat) * h).toFloat()
                    drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(x, y))
                }
            }
        }
        val handler = LocalUriHandler.current
        points.take(8).forEach { p ->
            val coord = listOfNotNull(p.lat, p.lng).joinToString(", ")
            val label = if (coord.isBlank()) p.name else "${p.name}  $coord"
            val geo = if (p.lat != null && p.lng != null) "geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${p.name})" else null
            Text(
                label,
                color = CloverText,
                style = MaterialTheme.typography.bodySmall,
                modifier = if (geo != null) Modifier.clickable { runCatching { handler.openUri(geo) } } else Modifier,
            )
        }
        if (points.size > 8) {
            Text("另有 ${points.size - 8} 个点", color = CloverText3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OpenUiActions(node: OpenUiNode, interactive: Boolean, onAction: (String, String) -> Unit) {
    val items = node.props["items"]?.jsonArray.orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(4).forEach { el ->
            val obj = runCatching { el.jsonObject }.getOrNull()
            val id = obj?.get("id")?.jsonPrimitive?.content ?: node.id
            val label = obj?.get("label")?.jsonPrimitive?.content ?: id
            OutlinedButton(onClick = { onAction(id, label) }, enabled = interactive) {
                Text(label)
            }
        }
    }
}

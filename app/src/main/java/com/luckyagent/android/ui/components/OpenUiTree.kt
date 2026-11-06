package com.luckyagent.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.openui.OpenUiDelta
import com.luckyagent.android.data.openui.OpenUiNode
import com.luckyagent.android.data.openui.caption
import com.luckyagent.android.data.openui.chartSeries
import com.luckyagent.android.data.openui.heading
import com.luckyagent.android.data.openui.OpenUiActionCollapsedItems
import com.luckyagent.android.data.openui.OpenUiActionExpandedItems
import com.luckyagent.android.data.openui.OpenUiListCollapsedItems
import com.luckyagent.android.data.openui.OpenUiListExpandedItems
import com.luckyagent.android.data.openui.OpenUiTableCollapsedRows
import com.luckyagent.android.data.openui.OpenUiTableExpandedRows
import com.luckyagent.android.data.openui.listItems
import com.luckyagent.android.data.openui.openUiActionItems
import com.luckyagent.android.data.openui.mapPoints
import com.luckyagent.android.data.openui.openUiColumnWidthDp
import com.luckyagent.android.data.openui.openUiDisplayUnits
import com.luckyagent.android.data.openui.string
import com.luckyagent.android.data.openui.stringList
import com.luckyagent.android.data.openui.tableGrid
import com.luckyagent.android.ui.theme.IonClay
import com.luckyagent.android.ui.theme.IonClayEmphasis
import com.luckyagent.android.ui.theme.IonDarkHeader
import com.luckyagent.android.ui.theme.IonDarkLine
import com.luckyagent.android.ui.theme.IonDarkLineStrong
import com.luckyagent.android.ui.theme.IonDarkSurface
import com.luckyagent.android.ui.theme.IonDarkSurfaceRaised
import com.luckyagent.android.ui.theme.IonDarkText
import com.luckyagent.android.ui.theme.IonDarkText2
import com.luckyagent.android.ui.theme.IonDarkText3
import com.luckyagent.android.ui.theme.IonHeader
import com.luckyagent.android.ui.theme.IonLine
import com.luckyagent.android.ui.theme.IonLineStrong
import com.luckyagent.android.ui.theme.IonOnClay
import com.luckyagent.android.ui.theme.IonSeries
import com.luckyagent.android.ui.theme.IonSurface
import com.luckyagent.android.ui.theme.IonSurfaceRaised
import com.luckyagent.android.ui.theme.IonText
import com.luckyagent.android.ui.theme.IonText2
import com.luckyagent.android.ui.theme.IonText3
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val IonControlShape = RoundedCornerShape(8.dp)

@Immutable
private data class IonPalette(
    val surface: Color,
    val raised: Color,
    val header: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val clay: Color,
    val clayEmphasis: Color,
    val onClay: Color,
    val series: List<Color>,
)

@Composable
private fun ionPalette(): IonPalette {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        IonPalette(
            surface = IonDarkSurface,
            raised = IonDarkSurfaceRaised,
            header = IonDarkHeader,
            line = IonDarkLine,
            lineStrong = IonDarkLineStrong,
            text = IonDarkText,
            text2 = IonDarkText2,
            text3 = IonDarkText3,
            clay = IonClay,
            clayEmphasis = IonClayEmphasis,
            onClay = IonOnClay,
            series = IonSeries,
        )
    } else {
        IonPalette(
            surface = IonSurface,
            raised = IonSurfaceRaised,
            header = IonHeader,
            line = IonLine,
            lineStrong = IonLineStrong,
            text = IonText,
            text2 = IonText2,
            text3 = IonText3,
            clay = IonClay,
            clayEmphasis = IonClayEmphasis,
            onClay = IonOnClay,
            series = IonSeries,
        )
    }
}

@Composable
fun OpenUiTree(
    tree: OpenUiDelta,
    interactive: Boolean,
    onAction: (widgetId: String, label: String) -> Unit,
    selectedWidgetId: String? = null,
    imageHeaders: Map<String, String> = emptyMap(),
    imageBaseUrl: String = "",
) {
    if (tree.nodes.isEmpty()) return
    val ion = ionPalette()
    val canAct = interactive && tree.state != "streaming"
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (tree.state == "streaming") {
            Text(
                "生成中",
                style = MaterialTheme.typography.labelSmall,
                color = ion.text3,
            )
        }
        tree.nodes.forEachIndexed { index, node ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(ion.line))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NodeHeading(node, ion)
                val prompt = node.props.string("prompt") ?: node.props.string("question")
                if (node.type == "Actions" && !prompt.isNullOrBlank() && prompt != node.heading()) {
                    Text(prompt, color = ion.text3, style = MaterialTheme.typography.labelSmall)
                }
                val actionsWaiting = tree.state == "waiting_input" && node.type == "Actions"
                val body: @Composable () -> Unit = {
                    OpenUiNodeView(
                        node = node,
                        interactive = canAct,
                        selectedWidgetId = selectedWidgetId,
                        onAction = onAction,
                        imageHeaders = imageHeaders,
                        imageBaseUrl = imageBaseUrl,
                        ion = ion,
                    )
                }
                if (actionsWaiting) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp)) {
                        Box(
                            Modifier
                                .padding(top = 2.dp, bottom = 2.dp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(ion.clay),
                        )
                        Box(Modifier.weight(1f).padding(start = 12.dp)) { body() }
                    }
                } else {
                    body()
                }
            }
        }
    }
}

@Composable
private fun NodeHeading(node: OpenUiNode, ion: IonPalette) {
    val title = node.heading()
    val note = node.caption()
    if (title == null && note == null) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (title != null) {
            Text(title, color = ion.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
        if (note != null) {
            Text(note, color = ion.text3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OpenUiNodeView(
    node: OpenUiNode,
    interactive: Boolean,
    selectedWidgetId: String?,
    onAction: (String, String) -> Unit,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    ion: IonPalette,
) {
    when (node.type) {
        "Table" -> OpenUiTable(node, ion)
        "List" -> OpenUiList(node, ion)
        "Chart" -> OpenUiChart(node, ion)
        "Map" -> OpenUiMap(node, ion)
        "Citation" -> OpenUiCitation(node, ion)
        "Image" -> {
            val alt = node.props.string("alt").orEmpty()
            MarkdownImage(
                source = node.props.string("src").orEmpty(),
                alt = alt,
                headers = imageHeaders,
                baseUrl = imageBaseUrl,
            )
            val credit = node.props.string("credit") ?: node.props.string("source")
            if (alt.isNotBlank() || !credit.isNullOrBlank()) {
                Text(
                    listOfNotNull(alt.takeIf { it.isNotBlank() }, credit).joinToString(" · "),
                    color = ion.text3,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        "Svg" -> {
            val markup = node.props.string("markup").orEmpty()
            val svg = staticSvgDocument(markup)
            if (svg != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(IonControlShape)
                        .background(ion.header)
                        .padding(8.dp),
                ) {
                    InlineSvgPreview(source = markup, svg = svg)
                }
            } else {
                Text("示意图", color = ion.text2, style = MaterialTheme.typography.bodySmall)
            }
        }
        "Markdown" -> MarkdownText(markdown = node.props.string("text").orEmpty())
        "Actions" -> OpenUiActions(node, interactive, selectedWidgetId, onAction, ion)
        else -> {}
    }
}

@Composable
private fun OpenUiTable(node: OpenUiNode, ion: IonPalette) {
    val columns = node.props.stringList("columns")
    var expanded by remember(node.id) { mutableStateOf(false) }
    val (grid, extra) = node.tableGrid(if (expanded) OpenUiTableExpandedRows else OpenUiTableCollapsedRows)
    if (grid.isEmpty()) return
    val colCount = grid.maxOf { it.size }
    val numeric = List(colCount) { col ->
        val body = if (columns.isNotEmpty()) grid.drop(1) else grid
        body.count { it.getOrNull(col)?.trim()?.toFloatOrNull() != null } >= (body.size / 2).coerceAtLeast(1)
    }
    val widths = List(colCount) { col ->
        openUiColumnWidthDp(grid, col).dp
    }
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        grid.forEachIndexed { row, cells ->
            val header = row == 0 && columns.isNotEmpty()
            Row {
                repeat(colCount) { col ->
                    Text(
                        cells.getOrNull(col).orEmpty(),
                        color = if (header) ion.text3 else ion.text,
                        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                        textAlign = if (numeric[col] && !header) TextAlign.End else TextAlign.Start,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(widths[col])
                            .padding(end = 12.dp, top = if (header) 0.dp else 11.dp, bottom = if (header) 8.dp else 11.dp),
                    )
                }
            }
            val rule = if (header) ion.lineStrong else ion.line
            val lastBody = row == grid.lastIndex
            if (!lastBody) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
            }
        }
        if (extra > 0 && !expanded) {
            Text(
                "其余 $extra 行",
                color = ion.clayEmphasis,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { expanded = true },
            )
        } else if (extra > 0) {
            Text(
                "仍有 $extra 行未显示",
                color = ion.text3,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun OpenUiList(node: OpenUiNode, ion: IonPalette) {
    var expanded by remember(node.id) { mutableStateOf(false) }
    val (items, extra) = node.listItems(if (expanded) OpenUiListExpandedItems else OpenUiListCollapsedItems)
    val details = node.props["items"]?.jsonArray.orEmpty()
    Column {
        items.forEachIndexed { i, item ->
            val obj = details.getOrNull(i)?.let { runCatching { it.jsonObject }.getOrNull() }
            val detail = obj?.string("detail") ?: obj?.string("description") ?: obj?.string("text")?.takeIf { it != item }
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ion.line))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ion.text3),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item, color = ion.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    if (!detail.isNullOrBlank()) {
                        Text(detail, color = ion.text2, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (extra > 0 && !expanded) {
            Text(
                "其余 $extra 项",
                color = ion.clayEmphasis,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = true },
            )
        } else if (extra > 0) {
            Text("仍有 $extra 项未显示", color = ion.text3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OpenUiCitation(node: OpenUiNode, ion: IonPalette) {
    val source = node.props.string("source").orEmpty()
    val title = node.props.string("title") ?: node.props.string("label")
    val label = title ?: source.ifBlank { "来源" }
    val host = sourceHost(source)
    val url = source.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    val handler = LocalUriHandler.current
    val line = if (host != null && host != label) "$label · $host" else label
    Row(
        Modifier
            .then(if (url != null) Modifier.clickable { runCatching { handler.openUri(url) } } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ion.clay),
            contentAlignment = Alignment.Center,
        ) {
            Text((host ?: label).take(1).uppercase(), color = ion.onClay, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(
            line,
            color = ion.text2,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun sourceHost(source: String): String? {
    if (!source.startsWith("http://") && !source.startsWith("https://")) return null
    return source.substringAfter("://").substringBefore("/").substringBefore("?").ifBlank { null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenUiChart(node: OpenUiNode, ion: IonPalette) {
    val kind = node.props.string("kind") ?: "chart"
    val series = node.chartSeries().filter { it.values.isNotEmpty() }
    if (series.isEmpty()) {
        Text("图：$kind", color = ion.text2, style = MaterialTheme.typography.bodySmall)
        return
    }
    val max = series.maxOf { it.values.max() }.coerceAtLeast(0f)
    val min = series.minOf { it.values.min() }.coerceAtMost(0f)
    val span = (max - min).let { if (it == 0f) 1f else it }
    val bar = kind.contains("bar", ignoreCase = true) || kind.contains("柱", ignoreCase = true)
    val labels = node.props.stringList("labels").ifEmpty { node.props.stringList("categories") }
    val colors = ion.series
    val groups = series.maxOf { it.values.size }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            series.forEachIndexed { i, ser ->
                val total = ser.values.sum()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors[i % colors.size]),
                    )
                    Text(
                        "${ser.name} ${formatChartTick(total)}",
                        color = ion.text2,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(148.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(40.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(formatChartTick(max), color = ion.text3, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(formatChartTick(min), color = ion.text3, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
                val pad = 8.dp.toPx()
                val w = size.width - pad
                val h = size.height - pad * 2
                val originX = pad
                val originY = pad + h
                val axis = ion.lineStrong
                drawLine(axis, Offset(originX, pad), Offset(originX, originY), strokeWidth = 1.dp.toPx())
                drawLine(axis, Offset(originX, originY), Offset(originX + w, originY), strokeWidth = 1.dp.toPx())
                if (bar) {
                    val slot = w / groups
                    val inner = slot * 0.72f
                    val gap = 2.dp.toPx()
                    val barW = ((inner - gap * (series.size - 1)) / series.size).coerceAtLeast(3f)
                    val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    series.forEachIndexed { s, ser ->
                        ser.values.forEachIndexed { i, v ->
                            val bh = ((v - min) / span) * h
                            val x = originX + i * slot + (slot - inner) / 2 + s * (barW + gap)
                            drawRoundRect(
                                color = colors[s % colors.size],
                                topLeft = Offset(x, originY - bh),
                                size = Size(barW, bh),
                                cornerRadius = radius,
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
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.take(6).forEach { label ->
                    Text(label, color = ion.text3, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatChartTick(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else "%.1f".format(value)

private fun formatCoord(value: Double): String = "%.2f".format(value)

@Composable
private fun OpenUiMap(node: OpenUiNode, ion: IonPalette) {
    val points = node.mapPoints()
    if (points.isEmpty()) {
        Text("地图", color = ion.text2, style = MaterialTheme.typography.bodySmall)
        return
    }
    val located = points.filter { it.lat != null && it.lng != null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (located.size >= 1) {
            val minLat = located.minOf { it.lat!! }
            val maxLat = located.maxOf { it.lat!! }
            val minLng = located.minOf { it.lng!! }
            val maxLng = located.maxOf { it.lng!! }
            val dLat = (maxLat - minLat).let { if (it == 0.0) 1.0 else it }
            val dLng = (maxLng - minLng).let { if (it == 0.0) 1.0 else it }
            val pin = ion.clay
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                val pad = 14.dp.toPx()
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                located.forEach { p ->
                    val x = pad + (((p.lng!! - minLng) / dLng) * w).toFloat()
                    val y = pad + h - (((p.lat!! - minLat) / dLat) * h).toFloat()
                    drawCircle(color = pin.copy(alpha = 0.22f), radius = 9.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = pin, radius = 4.dp.toPx(), center = Offset(x, y))
                }
            }
            Text(
                "${located.size} 个点 · ${formatCoord(minLat)}–${formatCoord(maxLat)}",
                color = ion.text3,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val handler = LocalUriHandler.current
        points.take(8).forEach { p ->
            val coord = listOfNotNull(p.lat, p.lng).joinToString(", ")
            val label = if (coord.isBlank()) p.name else "${p.name}  $coord"
            val geo = if (p.lat != null && p.lng != null) "geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${p.name})" else null
            Text(
                label,
                color = ion.text,
                style = MaterialTheme.typography.bodySmall,
                modifier = if (geo != null) Modifier.clickable { runCatching { handler.openUri(geo) } } else Modifier,
            )
        }
        if (points.size > 8) {
            Text("另有 ${points.size - 8} 个点", color = ion.text3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenUiActions(
    node: OpenUiNode,
    interactive: Boolean,
    selectedWidgetId: String?,
    onAction: (String, String) -> Unit,
    ion: IonPalette,
) {
    var expanded by remember(node.id) { mutableStateOf(false) }
    val allItems = node.props["items"]?.jsonArray.orEmpty()
    val (items, extra) = openUiActionItems(allItems, if (expanded) OpenUiActionExpandedItems else OpenUiActionCollapsedItems)
    val chips = items.isNotEmpty() && items.all { el ->
        val obj = runCatching { el.jsonObject }.getOrNull()
        val label = obj?.get("label")?.jsonPrimitive?.content ?: obj?.get("id")?.jsonPrimitive?.content ?: node.id
        !label.contains('\n') && openUiDisplayUnits(label) <= 10
    }
    val locked = selectedWidgetId != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (chips) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull()
                    val id = obj?.get("id")?.jsonPrimitive?.content ?: node.id
                    val label = obj?.get("label")?.jsonPrimitive?.content ?: id
                    val selected = selectedWidgetId == id
                    val canSend = interactive && node.policy == "action"
                    Button(
                        onClick = { if (canSend) onAction(id, label) },
                        enabled = canSend && !locked,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) ion.clay else ion.raised,
                            contentColor = if (selected) ion.onClay else ion.text,
                            disabledContainerColor = if (selected) ion.clay else ion.raised,
                            disabledContentColor = if (selected) ion.onClay else ion.text3,
                        ),
                        border = if (selected) BorderStroke(1.dp, ion.clay) else BorderStroke(1.dp, ion.lineStrong),
                    ) {
                        Text(
                            if (selected) "✓ $label" else label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull()
                    val id = obj?.get("id")?.jsonPrimitive?.content ?: node.id
                    val label = obj?.get("label")?.jsonPrimitive?.content ?: id
                    val hint = obj?.string("description") ?: obj?.string("detail")
                    val selected = selectedWidgetId == id
                    val canSend = interactive && node.policy == "action"
                    Button(
                        onClick = { if (canSend) onAction(id, label) },
                        enabled = canSend && !locked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) ion.clay else ion.raised,
                            contentColor = if (selected) ion.onClay else ion.text,
                            disabledContainerColor = if (selected) ion.clay else ion.raised,
                            disabledContentColor = if (selected) ion.onClay else ion.text3,
                        ),
                        border = if (selected) BorderStroke(1.dp, ion.clay) else BorderStroke(1.dp, ion.lineStrong),
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            Text(
                                if (selected) "✓ $label" else label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!hint.isNullOrBlank()) {
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) ion.onClay.copy(alpha = 0.82f) else ion.text3,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (extra > 0 && !expanded) {
            Text(
                "其余 $extra 项",
                color = ion.clayEmphasis,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = true },
            )
        } else if (extra > 0) {
            Text("仍有 $extra 项未显示", color = ion.text3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

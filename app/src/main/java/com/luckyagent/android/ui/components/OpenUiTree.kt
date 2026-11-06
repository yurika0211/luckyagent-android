package com.luckyagent.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.openui.OpenUiDelta
import com.luckyagent.android.data.openui.OpenUiNode
import com.luckyagent.android.data.openui.canSendAction
import com.luckyagent.android.data.openui.caption
import com.luckyagent.android.data.openui.chartRange
import com.luckyagent.android.data.openui.chartSeries
import com.luckyagent.android.data.openui.heading
import com.luckyagent.android.data.openui.openUiChartApplyValues
import com.luckyagent.android.data.openui.openUiChartBrush
import com.luckyagent.android.data.openui.openUiChartHit
import com.luckyagent.android.data.openui.openUiChartKind
import com.luckyagent.android.data.openui.openUiChartMixHit
import com.luckyagent.android.data.openui.openUiChartReadout
import com.luckyagent.android.data.openui.openUiChartWindow
import com.luckyagent.android.data.openui.openUiListDetail
import com.luckyagent.android.data.openui.openUiMixKinds
import com.luckyagent.android.data.openui.openUiTableSort
import com.luckyagent.android.data.openui.openUiTableSortCycle
import com.luckyagent.android.data.openui.OpenUiTableSortMode
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
    onNotice: (String) -> Unit = {},
    onChartApply: (widgetId: String, values: Map<String, String>) -> Boolean = { _, _ -> false },
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
                        onNotice = onNotice,
                        onChartApply = onChartApply,
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
    onNotice: (String) -> Unit = {},
    onChartApply: (widgetId: String, values: Map<String, String>) -> Boolean = { _, _ -> false },
) {
    when (node.type) {
        "Table" -> OpenUiTable(node, ion)
        "List" -> OpenUiList(node, ion)
        "Chart" -> OpenUiChart(node, ion, onNotice, onChartApply)
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
    var sortColumn by remember(node.id) { mutableStateOf(-1) }
    var sortMode by remember(node.id) { mutableStateOf(OpenUiTableSortMode.None) }
    val (rawGrid, extra) = node.tableGrid(if (expanded) OpenUiTableExpandedRows else OpenUiTableCollapsedRows)
    val grid = openUiTableSort(rawGrid, headerRow = columns.isNotEmpty(), column = sortColumn, mode = sortMode)
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
                    val mark = if (header && sortColumn == col) {
                        when (sortMode) {
                            OpenUiTableSortMode.Asc -> " ↑"
                            OpenUiTableSortMode.Desc -> " ↓"
                            OpenUiTableSortMode.None -> ""
                        }
                    } else ""
                    Text(
                        cells.getOrNull(col).orEmpty() + mark,
                        color = if (header && sortColumn == col && sortMode != OpenUiTableSortMode.None) ion.clayEmphasis else if (header) ion.text3 else ion.text,
                        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                        textAlign = if (numeric[col] && !header) TextAlign.End else TextAlign.Start,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(widths[col])
                            .then(if (header) Modifier.clickable {
                                if (sortColumn == col) sortMode = openUiTableSortCycle(sortMode) else {
                                    sortColumn = col
                                    sortMode = OpenUiTableSortMode.Asc
                                }
                            } else Modifier)
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
    var openDetail by remember(node.id) { mutableStateOf(setOf<Int>()) }
    val (items, extra) = node.listItems(if (expanded) OpenUiListExpandedItems else OpenUiListCollapsedItems)
    val details = node.props["items"]?.jsonArray.orEmpty()
    Column {
        items.forEachIndexed { i, item ->
            val detail = openUiListDetail(details.getOrNull(i), item)
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(ion.line))
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (!detail.isNullOrBlank()) Modifier.clickable {
                        openDetail = if (i in openDetail) openDetail - i else openDetail + i
                    } else Modifier)
                    .padding(vertical = 10.dp),
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
                    if (!detail.isNullOrBlank() && i in openDetail) {
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

private val chartKindChips = listOf(
    "bar" to "柱",
    "line" to "折",
    "area" to "面积",
    "pie" to "饼",
    "scatter" to "散点",
    "mix" to "混",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenUiChart(
    node: OpenUiNode,
    ion: IonPalette,
    onNotice: (String) -> Unit,
    onChartApply: (widgetId: String, values: Map<String, String>) -> Boolean,
) {
    val rawSeries = node.chartSeries().filter { it.values.isNotEmpty() }
    val unit = node.props.string("unit").orEmpty()
    val fullLabels = node.props.stringList("labels").ifEmpty { node.props.stringList("categories") }
    val pointCount = rawSeries.maxOfOrNull { it.values.size } ?: 0
    val sliceCount = rawSeries.firstOrNull()?.values?.size ?: 0
    val pieSum = rawSeries.firstOrNull()?.values?.sum() ?: 0f
    val resolvedKind = openUiChartKind(node.props.string("kind").orEmpty(), sliceCount, pieSum)
    var chosenKind by remember(node.id) { mutableStateOf(resolvedKind) }
    val usable = rawSeries.any { ser -> ser.values.any { !it.isNaN() && it != 0f } }
    if (rawSeries.isEmpty() || pointCount == 0 || !usable) {
        Text("没有可显示的数据", color = ion.text2, style = MaterialTheme.typography.bodySmall)
        return
    }
    val range = node.chartRange()
    var window by remember(node.id) { mutableStateOf(range?.value ?: pointCount.toFloat()) }
    var brushOrigin by remember(node.id) { mutableStateOf<Int?>(null) }
    val windowSize = window.toInt().coerceIn(1, pointCount)
    val tailStart = (brushOrigin ?: (pointCount - windowSize)).coerceIn(0, (pointCount - windowSize).coerceAtLeast(0))
    val cropped = rawSeries.map { ser ->
        val values = ser.values.drop(tailStart).take(windowSize)
        ser.copy(values = values, xs = ser.xs.drop(tailStart).take(values.size))
    }
    val labels = List(windowSize) { i -> fullLabels.getOrElse(tailStart + i) { "" } }
    var hidden by remember(node.id) { mutableStateOf(setOf<Int>()) }
    val visible = cropped.mapIndexedNotNull { index, ser -> if (index in hidden) null else index to ser }
    val drawSliceCount = visible.firstOrNull()?.second?.values?.size ?: 0
    val drawPieSum = visible.firstOrNull()?.second?.values?.sum() ?: 0f
    val drawKinds = openUiMixKinds(chosenKind, resolvedKind, visible.map { it.second.kind }).map { raw ->
        openUiChartKind(raw, drawSliceCount, drawPieSum)
    }
    val pieIsolated = drawKinds.size == 1 && drawKinds.first() == "pie" &&
        (chosenKind == "pie" || visible.any { it.second.kind == "pie" } || chosenKind == "mix")
    val cartesian = drawKinds.none { it == "pie" }
    var readout by remember(node.id, windowSize, hidden, chosenKind) { mutableStateOf<String?>(null) }
    var hit by remember(node.id, windowSize, hidden, chosenKind) { mutableStateOf<Pair<Int, Int>?>(null) }
    var brush by remember(node.id, windowSize, chosenKind) { mutableStateOf<Pair<Int, Int>?>(null) }
    val colors = ion.series
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!readout.isNullOrBlank()) {
            Text(readout.orEmpty(), color = ion.clayEmphasis, style = MaterialTheme.typography.labelSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chartKindChips.forEach { (id, label) ->
                val selected = chosenKind == id
                Text(
                    label,
                    color = if (selected) ion.onClay else ion.text2,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) ion.clay else ion.raised)
                        .border(1.dp, if (selected) ion.clay else ion.lineStrong, RoundedCornerShape(999.dp))
                        .clickable {
                            chosenKind = id
                            readout = null
                            hit = null
                            brush = null
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cropped.forEachIndexed { i, ser ->
                val off = i in hidden
                Row(
                    Modifier.clickable {
                        hidden = if (off) hidden - i else hidden + i
                        readout = null
                        hit = null
                        brush = null
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (off) ion.lineStrong else if (i == 0) ion.clay else colors[i % colors.size]),
                    )
                    Text(
                        ser.name,
                        color = if (off) ion.text3 else if (i == 0) ion.clayEmphasis else ion.text2,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            Text("没有可显示的数据", color = ion.text2, style = MaterialTheme.typography.bodySmall)
        } else {
            OpenUiChartCanvas(
                kinds = if (pieIsolated) listOf("pie") else drawKinds,
                series = if (pieIsolated) visible.take(1).map { it.second } else visible.map { it.second },
                colorIndexes = if (pieIsolated) visible.take(1).map { it.first } else visible.map { it.first },
                labels = labels,
                unit = unit,
                ion = ion,
                hit = hit,
                brush = if (cartesian) brush else null,
                onReadout = { seriesIndex, index ->
                    if (seriesIndex < 0 || index < 0) {
                        readout = null
                        hit = null
                        brush = null
                        return@OpenUiChartCanvas
                    }
                    val drawn = visible.getOrNull(seriesIndex)?.second ?: return@OpenUiChartCanvas
                    val value = drawn.values.getOrNull(index) ?: return@OpenUiChartCanvas
                    val label = if (pieIsolated) drawn.name else labels.getOrElse(index) { "" }
                    val name = if (pieIsolated) "" else drawn.name
                    readout = openUiChartReadout(label, name, value, unit)
                    hit = seriesIndex to index
                },
                onBrush = { span, released ->
                    if (!cartesian) return@OpenUiChartCanvas
                    if (!released) {
                        brush = span
                        return@OpenUiChartCanvas
                    }
                    brush = null
                    readout = null
                    hit = null
                    if (span == null) return@OpenUiChartCanvas
                    val (start, count) = openUiChartBrush(span.first, span.second, labels.size.coerceAtLeast(1))
                    if (count >= labels.size && start == 0) return@OpenUiChartCanvas
                    brushOrigin = (tailStart + start).coerceIn(0, (pointCount - 1).coerceAtLeast(0))
                    window = count.toFloat().coerceAtLeast(1f)
                },
            )
            val ticks = chartAxisLabels(labels)
            if (ticks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    ticks.forEach { label ->
                        Text(label, color = ion.text3, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (range != null && pointCount > 1) {
            val steps = (((range.max - range.min) / range.step).toInt()).coerceAtLeast(0)
            Slider(
                value = window.coerceIn(range.min, range.max.coerceAtMost(pointCount.toFloat())),
                onValueChange = {
                    window = it
                    brushOrigin = null
                    readout = null
                    hit = null
                    brush = null
                },
                valueRange = range.min..range.max.coerceAtLeast(range.min),
                steps = steps.coerceAtMost(pointCount - 1),
            )
            Text(
                "应用这些参数",
                color = ion.text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(ion.raised)
                    .border(1.dp, ion.lineStrong, RoundedCornerShape(999.dp))
                    .clickable {
                        val sent = onChartApply(
                            node.id,
                            openUiChartApplyValues(
                                window = windowSize,
                                kind = chosenKind,
                                start = if (cartesian) brushOrigin else null,
                            ),
                        )
                        if (!sent) onNotice("无法发给模型")
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private fun chartAxisLabels(labels: List<String>): List<String> {
    val present = labels.filter { it.isNotBlank() }
    if (present.size <= 8) return present
    val step = ((present.size + 7) / 8).coerceAtLeast(1)
    return present.filterIndexed { index, _ -> index % step == 0 }
}

@Composable
private fun OpenUiChartCanvas(
    kinds: List<String>,
    series: List<com.luckyagent.android.data.openui.OpenUiChartSeries>, // visible series in draw order
    colorIndexes: List<Int>,
    labels: List<String>,
    unit: String,
    ion: IonPalette,
    hit: Pair<Int, Int>? = null,
    brush: Pair<Int, Int>? = null,
    onReadout: (seriesIndex: Int, index: Int) -> Unit,
    onBrush: (span: Pair<Int, Int>?, released: Boolean) -> Unit = { _, _ -> },
) {
    val pie = kinds.firstOrNull() == "pie" && kinds.size <= 1
    val leftSeries = series.filterIndexed { i, _ -> kinds.getOrElse(i) { "bar" } != "pie" && series.getOrNull(i)?.yAxis != "right" }
    val rightSeries = series.filterIndexed { i, _ -> kinds.getOrElse(i) { "bar" } != "pie" && series.getOrNull(i)?.yAxis == "right" }
    val leftFinite = (if (rightSeries.isEmpty()) series else leftSeries.ifEmpty { series }).flatMap { it.values }.filter { !it.isNaN() }
    val rightFinite = rightSeries.flatMap { it.values }.filter { !it.isNaN() }
    val leftMax = (leftFinite.maxOrNull() ?: 0f).coerceAtLeast(0f)
    val leftMin = (leftFinite.minOrNull() ?: 0f).coerceAtMost(0f)
    val leftSpan = (leftMax - leftMin).let { if (it == 0f) 1f else it }
    val rightMax = (rightFinite.maxOrNull() ?: 0f).coerceAtLeast(0f)
    val rightMin = (rightFinite.minOrNull() ?: 0f).coerceAtMost(0f)
    val rightSpan = (rightMax - rightMin).let { if (it == 0f) 1f else it }
    val groups = series.maxOf { it.values.size }.coerceAtLeast(1)
    val colors = ion.series
    val mid = leftMin + leftSpan / 2f
    Row(Modifier.fillMaxWidth().height(148.dp)) {
        Column(
            Modifier.width(44.dp).fillMaxHeight().padding(bottom = 8.dp, top = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(chartTickLabel(leftMax, unit), color = ion.text3, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, maxLines = 1)
            Text(formatChartTick(mid), color = ion.text3, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, maxLines = 1)
            Text(formatChartTick(leftMin), color = ion.text3, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, maxLines = 1)
        }
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 8.dp)
                .pointerInput(kinds, groups, series, pie) {
                    if (!pie) {
                        var anchor = 0
                        var span = 0 to 0
                        detectDragGestures(
                            onDragStart = { offset ->
                                val pad = 8.dp.toPx()
                                val w = (size.width - pad).coerceAtLeast(1f)
                                val x = ((offset.x - pad) / w).coerceIn(0f, 0.999999f)
                                anchor = (x * groups).toInt().coerceIn(0, groups - 1)
                                span = anchor to anchor
                                onBrush(span, false)
                            },
                            onDrag = { change, _ ->
                                val pad = 8.dp.toPx()
                                val w = (size.width - pad).coerceAtLeast(1f)
                                val x = ((change.position.x - pad) / w).coerceIn(0f, 0.999999f)
                                val index = (x * groups).toInt().coerceIn(0, groups - 1)
                                span = anchor to index
                                onBrush(span, false)
                            },
                            onDragEnd = { onBrush(span, true) },
                            onDragCancel = { onBrush(null, true) },
                        )
                    }
                }
                .pointerInput(kinds, groups, series) {
                    detectTapGestures { offset ->
                        val pad = 8.dp.toPx()
                        val w = (size.width - pad).coerceAtLeast(1f)
                        val h = (size.height - pad * 2).coerceAtLeast(1f)
                        val tapX = (offset.x - pad) / w
                        val tapY = (offset.y - pad) / h
                        val hit = if (kinds.size > 1 && kinds.toSet().size > 1) {
                            openUiChartMixHit(kinds, groups, tapX, tapY, series.map { it.values }, series.map { it.xs })
                        } else {
                            openUiChartHit(
                                kind = kinds.firstOrNull() ?: "bar",
                                seriesCount = series.size,
                                groups = groups,
                                tapX = tapX,
                                tapY = tapY,
                                values = series.map { it.values },
                                xs = series.map { it.xs },
                            )
                        }
                        if (hit == null) onReadout(-1, -1) else onReadout(hit.first, hit.second)
                    }
                },
        ) {
            val pad = 8.dp.toPx()
            val w = size.width - pad
            val h = size.height - pad * 2
            val originX = pad
            val originY = pad + h
            val min = leftMin
            val span = leftSpan
            val zeroY = originY - ((0f - min) / span) * h
            if (!pie) {
                drawLine(ion.line, Offset(originX, pad), Offset(originX, originY), strokeWidth = 1.dp.toPx())
                drawLine(ion.line, Offset(originX, pad), Offset(originX + w, pad), strokeWidth = 1.dp.toPx())
                drawLine(ion.line, Offset(originX, originY - h / 2f), Offset(originX + w, originY - h / 2f), strokeWidth = 1.dp.toPx())
                drawLine(ion.line, Offset(originX, zeroY), Offset(originX + w, zeroY), strokeWidth = 1.dp.toPx())
                drawLine(ion.lineStrong, Offset(originX, originY), Offset(originX + w, originY), strokeWidth = 1.dp.toPx())
            }
            fun colorAt(s: Int): Color {
                val index = colorIndexes.getOrElse(s) { s }
                return if (index == 0) ion.clay else colors[index % colors.size]
            }
            fun scaleOf(s: Int): Triple<Float, Float, Float> {
                val right = series.getOrNull(s)?.yAxis == "right" && rightSeries.isNotEmpty()
                return if (right) Triple(rightMin, rightMax, rightSpan) else Triple(leftMin, leftMax, leftSpan)
            }
            if (!pie && brush != null) {
                val a = minOf(brush.first, brush.second).coerceIn(0, groups - 1)
                val b = maxOf(brush.first, brush.second).coerceIn(0, groups - 1)
                val slot = w / groups
                drawRoundRect(
                    ion.clay.copy(alpha = 0.18f),
                    Offset(originX + a * slot, pad),
                    Size(slot * (b - a + 1), h),
                    CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
            if (pie) {
                val slices = series.first().values.map { if (it.isNaN() || it < 0f) 0f else it }
                val total = slices.sum().coerceAtLeast(0.0001f)
                var start = -90f
                slices.forEachIndexed { i, v ->
                    val sweep = 360f * (v / total)
                    val marked = hit?.first == 0 && hit.second == i
                    val shift = if (marked) 6.dp.toPx() else 0f
                    val angle = Math.toRadians((start + sweep / 2f).toDouble())
                    drawArc(
                        color = colors[i % colors.size].let { if (i == 0) ion.clay else it },
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(originX + w * 0.25f + kotlin.math.cos(angle).toFloat() * shift, pad + kotlin.math.sin(angle).toFloat() * shift),
                        size = Size(h, h),
                        style = if (marked) Stroke(width = 2.dp.toPx()) else Fill,
                    )
                    start += sweep
                }
            } else {
                val barIndexes = kinds.mapIndexedNotNull { i, kind -> if (kind == "bar") i else null }
                val slot = w / groups
                val inner = slot * 0.72f
                val gap = 2.dp.toPx()
                val barW = ((inner - gap * (barIndexes.size - 1).coerceAtLeast(0)) / barIndexes.size.coerceAtLeast(1)).coerceAtLeast(3f)
                val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                barIndexes.forEachIndexed { order, s ->
                    val ser = series.getOrNull(s) ?: return@forEachIndexed
                    val (sMin, _, sSpan) = scaleOf(s)
                    val sZero = originY - ((0f - sMin) / sSpan) * h
                    ser.values.forEachIndexed { i, v ->
                        if (v.isNaN()) return@forEachIndexed
                        val y = originY - ((v - sMin) / sSpan) * h
                        val top = minOf(y, sZero)
                        val bh = kotlin.math.abs(sZero - y).coerceAtLeast(1f)
                        val x = originX + i * slot + (slot - inner) / 2 + order * (barW + gap)
                        val marked = hit?.first == s && hit.second == i
                        drawRoundRect(if (marked) ion.clayEmphasis else colorAt(s), Offset(x, top), Size(barW, bh), radius)
                    }
                }
                val scatterIndexes = kinds.mapIndexedNotNull { i, kind -> if (kind == "scatter") i else null }
                if (scatterIndexes.isNotEmpty()) {
                    val finiteX = scatterIndexes.flatMap { s ->
                        val ser = series.getOrNull(s) ?: return@flatMap emptyList()
                        ser.values.mapIndexedNotNull { i, v ->
                            if (v.isNaN()) null else ser.xs.getOrNull(i)?.takeUnless { it.isNaN() } ?: i.toFloat()
                        }
                    }
                    val minX = finiteX.minOrNull() ?: 0f
                    val spanX = ((finiteX.maxOrNull() ?: minX) - minX).let { if (it == 0f) 1f else it }
                    fun plotX(ser: com.luckyagent.android.data.openui.OpenUiChartSeries, i: Int): Float {
                        val rawX = ser.xs.getOrNull(i)?.takeUnless { it.isNaN() } ?: i.toFloat()
                        return originX + w * ((rawX - minX) / spanX)
                    }
                    scatterIndexes.forEach { s ->
                        val ser = series.getOrNull(s) ?: return@forEach
                        val (sMin, _, sSpan) = scaleOf(s)
                        ser.values.forEachIndexed { i, v ->
                            if (v.isNaN()) return@forEachIndexed
                            val marked = hit?.first == s && hit.second == i
                            val x = plotX(ser, i)
                            val y = originY - ((v - sMin) / sSpan) * h
                            drawCircle(if (marked) ion.clayEmphasis else colorAt(s), radius = if (marked) 6.dp.toPx() else 3.5.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
                val pathIndexes = kinds.mapIndexedNotNull { i, kind -> if (kind == "line" || kind == "area") i else null }
                val denom = (groups - 1).coerceAtLeast(1)
                pathIndexes.forEach { s ->
                    val ser = series.getOrNull(s) ?: return@forEach
                    val kind = kinds.getOrElse(s) { "line" }
                    val (sMin, _, sSpan) = scaleOf(s)
                    val sZero = originY - ((0f - sMin) / sSpan) * h
                    val path = Path()
                    val fill = Path().apply { fillType = PathFillType.NonZero }
                    ser.values.forEachIndexed { i, v ->
                        val value = if (v.isNaN()) sMin else v
                        val x = originX + w * i / denom
                        val y = originY - ((value - sMin) / sSpan) * h
                        if (i == 0) {
                            path.moveTo(x, y)
                            fill.moveTo(x, sZero)
                            fill.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fill.lineTo(x, y)
                        }
                        val marked = hit?.first == s && hit.second == i
                        drawCircle(if (marked) ion.clayEmphasis else colorAt(s), radius = if (marked) 6.dp.toPx() else 3.dp.toPx(), center = Offset(x, y))
                    }
                    if (ser.values.isNotEmpty()) {
                        val lastX = originX + w * ser.values.lastIndex / denom
                        fill.lineTo(lastX, sZero)
                        fill.close()
                    }
                    if (kind == "area") drawPath(fill, colorAt(s).copy(alpha = 0.18f))
                    drawPath(path, colorAt(s), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
                hit?.let { (s, i) ->
                    if (kinds.getOrElse(s) { "" } == "bar") return@let
                    val x = if (kinds.getOrElse(s) { "" } == "scatter") {
                        val ser = series.getOrNull(s) ?: return@let
                        val finiteX = scatterIndexes.flatMap { si ->
                            val row = series.getOrNull(si) ?: return@flatMap emptyList()
                            row.values.mapIndexedNotNull { pi, v ->
                                if (v.isNaN()) null else row.xs.getOrNull(pi)?.takeUnless { it.isNaN() } ?: pi.toFloat()
                            }
                        }
                        val minX = finiteX.minOrNull() ?: 0f
                        val spanX = ((finiteX.maxOrNull() ?: minX) - minX).let { if (it == 0f) 1f else it }
                        val rawX = ser.xs.getOrNull(i)?.takeUnless { it.isNaN() } ?: i.toFloat()
                        originX + w * ((rawX - minX) / spanX)
                    } else {
                        originX + w * i / denom
                    }
                    drawLine(
                        ion.lineStrong,
                        Offset(x, pad),
                        Offset(x, originY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                }
            }
        }
    }
}

private fun chartTickLabel(value: Float, unit: String): String {
    val number = formatChartTick(value)
    return if (unit.isBlank()) number else "$number $unit"
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
    var selected by remember(node.id) { mutableStateOf<Int?>(null) }
    var expanded by remember(node.id) { mutableStateOf(false) }
    val located = points.mapIndexedNotNull { index, point ->
        if (point.lat != null && point.lng != null) index to point else null
    }
    val shown = if (expanded) points.take(OpenUiListExpandedItems) else points.take(OpenUiListCollapsedItems)
    val extra = (points.size - shown.size).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (located.size >= 1) {
            val minLat = located.minOf { it.second.lat!! }
            val maxLat = located.maxOf { it.second.lat!! }
            val minLng = located.minOf { it.second.lng!! }
            val maxLng = located.maxOf { it.second.lng!! }
            val dLat = (maxLat - minLat).let { if (it == 0.0) 1.0 else it }
            val dLng = (maxLng - minLng).let { if (it == 0.0) 1.0 else it }
            val pin = ion.clay
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .pointerInput(located, minLat, maxLat, minLng, maxLng) {
                        detectTapGestures { offset ->
                            val pad = 14.dp.toPx()
                            val w = (size.width - pad * 2).coerceAtLeast(1f)
                            val h = (size.height - pad * 2).coerceAtLeast(1f)
                            var best = -1
                            var bestDist = Float.POSITIVE_INFINITY
                            located.forEach { (index, p) ->
                                val x = pad + (((p.lng!! - minLng) / dLng) * w).toFloat()
                                val y = pad + h - (((p.lat!! - minLat) / dLat) * h).toFloat()
                                val dist = (offset.x - x) * (offset.x - x) + (offset.y - y) * (offset.y - y)
                                if (dist < bestDist) {
                                    bestDist = dist
                                    best = index
                                }
                            }
                            val hitRadius = 18.dp.toPx()
                            selected = if (best >= 0 && bestDist <= hitRadius * hitRadius) best else null
                        }
                    },
            ) {
                val pad = 14.dp.toPx()
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                located.forEach { (index, p) ->
                    val marked = index == selected
                    val x = pad + (((p.lng!! - minLng) / dLng) * w).toFloat()
                    val y = pad + h - (((p.lat!! - minLat) / dLat) * h).toFloat()
                    val color = if (marked) ion.clayEmphasis else pin
                    drawCircle(color = color.copy(alpha = 0.22f), radius = if (marked) 14.dp.toPx() else 9.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = color, radius = if (marked) 6.dp.toPx() else 4.dp.toPx(), center = Offset(x, y))
                }
            }
            Text(
                "${located.size} 个点 · ${formatCoord(minLat)}–${formatCoord(maxLat)}",
                color = ion.text3,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val handler = LocalUriHandler.current
        shown.forEachIndexed { index, p ->
            val coord = listOfNotNull(p.lat, p.lng).joinToString(", ")
            val label = if (coord.isBlank()) p.name else "${p.name}  $coord"
            val geo = if (p.lat != null && p.lng != null) "geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${p.name})" else null
            val marked = index == selected
            Text(
                label,
                color = if (marked) ion.clayEmphasis else ion.text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (marked) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.clickable {
                    selected = index
                    if (geo != null) runCatching { handler.openUri(geo) }
                },
            )
        }
        if (extra > 0 && !expanded) {
            Text(
                "其余 $extra 个点",
                color = ion.clayEmphasis,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable { expanded = true },
            )
        } else if (extra > 0) {
            Text("仍有 $extra 个点未显示", color = ion.text3, style = MaterialTheme.typography.labelSmall)
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
                    val canSend = interactive && node.canSendAction()
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
                    val canSend = interactive && node.canSendAction()
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

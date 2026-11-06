package com.luckyagent.android.data.openui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
data class OpenUiDelta(
    @SerialName("tree_id") val treeId: String = "",
    val state: String = "streaming",
    val nodes: List<OpenUiNode> = emptyList(),
    val patch: Boolean = false,
)

@Serializable
data class OpenUiNode(
    val id: String = "",
    val type: String = "",
    val policy: String = "inspect",
    val props: JsonObject = JsonObject(emptyMap()),
    val children: List<String> = emptyList(),
)

@Serializable
data class OpenUiAction(
    @SerialName("tree_id") val treeId: String,
    @SerialName("widget_id") val widgetId: String,
    val values: Map<String, String> = emptyMap(),
)

private val catalog = setOf("Table", "Chart", "List", "Actions", "Image", "Svg", "Citation", "Map", "Markdown")

fun OpenUiDelta.keepCatalog(): OpenUiDelta {
    val kept = nodes.filter { it.id.isNotBlank() && it.type in catalog }
    return copy(nodes = kept)
}

fun JsonElement?.asOpenUiDelta(): OpenUiDelta? {
    val obj = this as? JsonObject ?: return null
    val treeId = obj.string("tree_id") ?: return null
    val nodes = obj["nodes"]?.jsonArray?.mapNotNull { el ->
        val n = el as? JsonObject ?: return@mapNotNull null
        OpenUiNode(
            id = n.string("id").orEmpty(),
            type = n.string("type").orEmpty(),
            policy = n.string("policy") ?: "inspect",
            props = n["props"] as? JsonObject ?: JsonObject(emptyMap()),
            children = n["children"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
        )
    } ?: emptyList()
    return OpenUiDelta(
        treeId = treeId,
        state = obj.string("state") ?: "streaming",
        nodes = nodes,
        patch = obj["patch"]?.jsonPrimitive?.contentOrNull == "true" || (obj["patch"] as? JsonPrimitive)?.content == "true",
    ).keepCatalog()
}

/** Only a closed tree is restored from history; a half-streamed one stays Markdown. */
private val closedStates = setOf("complete", "waiting_input")

/**
 * Rebuild a catalog-validated tree from a stored `present_ui` tool call.
 *
 * History reaches the client as plain messages, so the tree has to come back
 * out of the call arguments; otherwise the transcript keeps only a JSON dump.
 */
fun openUiTreeFromToolArguments(arguments: String?): OpenUiDelta? {
    val raw = arguments?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return null
    val treeElement = (element as? JsonObject)?.get("tree") ?: element
    val tree = treeElement.asOpenUiDelta() ?: return null
    if (tree.nodes.isEmpty()) return null
    return tree.takeIf { it.state in closedStates }
}

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.stringList(key: String): List<String> {
    val el = this[key] ?: return emptyList()
    return runCatching { el.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }.getOrElse {
        listOfNotNull((el as? JsonPrimitive)?.contentOrNull)
    }
}

const val OpenUiListCollapsedItems = 12
const val OpenUiListExpandedItems = 40
const val OpenUiTableCollapsedRows = 8
const val OpenUiTableExpandedRows = 40
const val OpenUiActionCollapsedItems = 4
const val OpenUiActionExpandedItems = 40

/** Shown items plus how many were left out. */
fun <T> openUiActionItems(items: List<T>, max: Int = OpenUiActionCollapsedItems): Pair<List<T>, Int> {
    val limit = max.coerceAtLeast(0)
    return items.take(limit) to (items.size - limit).coerceAtLeast(0)
}

/** Latin letters count as 1. CJK and fullwidth count as 2. */
fun openUiDisplayUnits(text: String): Int {
    var units = 0
    var index = 0
    while (index < text.length) {
        val code = text.codePointAt(index)
        units += if (openUiGlyphIsWide(code)) 2 else 1
        index += Character.charCount(code)
    }
    return units
}

fun openUiColumnWidthDp(grid: List<List<String>>, column: Int, cap: Int = 280): Int {
    val units = grid.maxOfOrNull { openUiDisplayUnits(it.getOrNull(column).orEmpty()) } ?: 0
    return (units * 8 + 16).coerceIn(64, cap)
}

private fun openUiGlyphIsWide(code: Int): Boolean = when (code) {
    in 0x1100..0x115F,
    in 0x2329..0x232A,
    in 0x2E80..0xA4CF,
    in 0xAC00..0xD7A3,
    in 0xF900..0xFAFF,
    in 0xFE10..0xFE19,
    in 0xFE30..0xFE6F,
    in 0xFF00..0xFF60,
    in 0xFFE0..0xFFE6,
    in 0x1F200..0x1F251,
    in 0x1F300..0x1FAFF,
    in 0x20000..0x3FFFD,
    -> true
    else -> false
}

/** Actions chips send. Go stamps `mutate`; a missing policy still defaults to inspect and must send. */
fun OpenUiNode.canSendAction(): Boolean = type == "Actions"

fun OpenUiNode.listItems(maxItems: Int = OpenUiListCollapsedItems): Pair<List<String>, Int> {
    val items = props["items"]?.jsonArray?.mapNotNull { el ->
        val obj = el as? JsonObject
        if (obj != null) obj.string("label") ?: obj.string("text")
        else (el as? JsonPrimitive)?.contentOrNull
    } ?: props.stringList("items")
    return items.take(maxItems) to (items.size - maxItems).coerceAtLeast(0)
}

/** Header plus at most [maxRows] body rows; extra is how many body rows were clipped. */
fun OpenUiNode.tableGrid(maxRows: Int = OpenUiTableCollapsedRows): Pair<List<List<String>>, Int> {
    val columns = props.stringList("columns")
    val rows = props["rows"]?.jsonArray.orEmpty().map { row ->
        runCatching { row.jsonArray.map { it.jsonPrimitive.content } }.getOrElse {
            listOf(row.toString())
        }
    }
    val grid = buildList {
        if (columns.isNotEmpty()) add(columns)
        addAll(rows.take(maxRows))
    }
    return grid to (rows.size - maxRows).coerceAtLeast(0)
}

enum class OpenUiTableSortMode { None, Asc, Desc }

/** Next header tap. none → asc → desc → none. */
fun openUiTableSortCycle(mode: OpenUiTableSortMode): OpenUiTableSortMode = when (mode) {
    OpenUiTableSortMode.None -> OpenUiTableSortMode.Asc
    OpenUiTableSortMode.Asc -> OpenUiTableSortMode.Desc
    OpenUiTableSortMode.Desc -> OpenUiTableSortMode.None
}

/**
 * Sorts the body of an already-clipped grid. [headerRow] keeps row 0 in place.
 * Numeric columns (half or more of the body parse as float) sort as float.
 * Everything else sorts as a string. [mode] none returns [grid] unchanged.
 */
fun openUiTableSort(
    grid: List<List<String>>,
    headerRow: Boolean,
    column: Int,
    mode: OpenUiTableSortMode,
): List<List<String>> {
    if (mode == OpenUiTableSortMode.None || grid.isEmpty() || column < 0) return grid
    val header = if (headerRow) grid.first() else null
    val body = if (headerRow) grid.drop(1) else grid
    if (body.isEmpty()) return grid
    val numeric = body.count { it.getOrNull(column)?.trim()?.toFloatOrNull() != null } >= (body.size / 2).coerceAtLeast(1)
    val sorted = body.sortedWith { left, right ->
        val a = left.getOrNull(column).orEmpty()
        val b = right.getOrNull(column).orEmpty()
        val cmp = if (numeric) {
            val fa = a.trim().toFloatOrNull()
            val fb = b.trim().toFloatOrNull()
            when {
                fa == null && fb == null -> a.compareTo(b)
                fa == null -> 1
                fb == null -> -1
                else -> fa.compareTo(fb)
            }
        } else {
            Collator.getInstance(Locale.CHINA).compare(a, b)
        }
        if (mode == OpenUiTableSortMode.Desc) -cmp else cmp
    }
    return if (header != null) listOf(header) + sorted else sorted
}

/** Detail text for one list item. A copy of the label is not a detail. */
fun openUiListDetail(item: JsonElement?, label: String): String? {
    val obj = item as? JsonObject ?: return null
    return obj.string("detail") ?: obj.string("description") ?: obj.string("text")?.takeIf { it != label }
}

data class OpenUiMapPoint(
    val name: String,
    val lat: Double?,
    val lng: Double?,
)

fun OpenUiNode.mapPoints(): List<OpenUiMapPoint> {
    val raw = props["points"] ?: return emptyList()
    val array = runCatching { raw.jsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { el ->
        val obj = el as? JsonObject
        if (obj != null) {
            val lat = obj.double("lat") ?: obj.double("latitude")
            val lng = obj.double("lng") ?: obj.double("lon") ?: obj.double("longitude")
            val name = obj.string("name") ?: obj.string("label") ?: obj.string("title")
                ?: listOfNotNull(lat, lng).joinToString(", ").ifBlank { null }
            OpenUiMapPoint(name = name.orEmpty(), lat = lat, lng = lng)
        } else {
            val text = (el as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            OpenUiMapPoint(name = text, lat = null, lng = null)
        }
    }
}

data class OpenUiChartSeries(
    val name: String,
    val values: List<Float>,
    val xs: List<Float> = emptyList(),
    /** Whitelisted draw kind, or null so the series follows the node chip. */
    val kind: String? = null,
    /** `"right"` uses the second axis. Anything else stays on the left. */
    val yAxis: String? = null,
)

data class OpenUiChartPoint(val x: Float, val y: Float)

/** Optional title/caption already present on a node. Missing keys stay blank. */
fun OpenUiNode.heading(): String? = props.string("title") ?: props.string("heading") ?: props.string("name")

fun OpenUiNode.caption(): String? = props.string("caption") ?: props.string("description") ?: props.string("subtitle")

private val openUiChartKinds = setOf("bar", "line", "area", "pie", "scatter")

/**
 * Whitelist chart kinds. Unknown kinds draw as bars. A pie with more than six
 * slices, or a non-positive sum, also falls back to bars.
 */
fun openUiChartKind(kind: String, sliceCount: Int, sum: Float): String {
    val normalized = kind.trim().lowercase()
    val resolved = if (normalized in openUiChartKinds) normalized else "bar"
    if (resolved == "pie" && (sliceCount > 6 || sum <= 0f || sum.isNaN())) return "bar"
    return resolved
}

/**
 * Series kind on the wire. Only the chart whitelist is kept. Unknown and
 * Chinese labels are null so the series follows the node kind.
 */
fun openUiSeriesKind(kind: String?): String? {
    val normalized = kind?.trim()?.lowercase().orEmpty()
    return normalized.takeIf { it in openUiChartKinds }
}

/** Right axis only when the wire says `"right"`. Missing and anything else stay left. */
fun openUiSeriesYAxis(yAxis: String?): String = if (yAxis?.trim()?.equals("right", ignoreCase = true) == true) "right" else "left"

/**
 * Draw kinds for the visible series. `mix` uses each series kind, and a null
 * series kind uses [resolvedKind]. A pie chip, or any visible pie series,
 * isolates to one pie of the first visible series.
 */
fun openUiMixKinds(chosenKind: String, resolvedKind: String, seriesKinds: List<String?>): List<String> {
    val fallback = openUiSeriesKind(resolvedKind) ?: "bar"
    val chip = chosenKind.trim().lowercase()
    if (chip == "pie") return listOf("pie")
    val usingMix = chip == "mix"
    val kinds = seriesKinds.map { raw ->
        val own = openUiSeriesKind(raw)
        if (usingMix) own ?: fallback else openUiSeriesKind(chip) ?: fallback
    }
    if (usingMix && kinds.any { it == "pie" }) return listOf("pie")
    return kinds
}

/** Last [window] points already present. A non-positive window keeps the full series. */
fun openUiChartWindow(values: List<Float>, labels: List<String>, window: Int): Pair<List<Float>, List<String>> {
    if (window <= 0 || values.size <= window) {
        val aligned = List(values.size) { i -> labels.getOrElse(i) { "" } }
        return values to aligned
    }
    val start = values.size - window
    return values.drop(start) to List(window) { i -> labels.getOrElse(start + i) { "" } }
}

/**
 * Hit-test a chart in plot coordinates. Chart taps stay local.
 *
 * [tapX] and [tapY] are fractions of the plot, each in `0..1`.
 * x grows to the right. y grows downward, so `0` is the top.
 *
 * Bars: group `i` is the slice `[i / groups, (i + 1) / groups)`.
 * The series in that group share the centered 72% of the slice, left to right.
 * A tap inside the group but outside a bar snaps to the nearest series.
 *
 * Line and area: x picks the nearest point. y picks the series whose value
 * is closest on the same top-down scale.
 *
 * Scatter: each series is a row of y values. When [xs] has a finite value at
 * that index, x is normalized across the finite xs of every series into 0..1.
 * Otherwise x is index / (n - 1). The nearest point by euclidean distance wins.
 *
 * Pie always returns series 0. The slice is the clockwise sector from 12
 * o'clock under the tap, measured from the plot center `(0.5, 0.5)`.
 *
 * Returns series index to point index, or null when there is nothing to hit.
 */
fun openUiChartHit(
    kind: String,
    seriesCount: Int,
    groups: Int,
    tapX: Float,
    tapY: Float,
    values: List<List<Float>>,
    xs: List<List<Float>> = emptyList(),
): Pair<Int, Int>? {
    if (seriesCount <= 0 || groups <= 0 || values.isEmpty()) return null
    val x = tapX.coerceIn(0f, 0.999999f)
    val y = tapY.coerceIn(0f, 1f)
    if (kind == "pie") {
        val slices = values.first()
        if (slices.isEmpty()) return null
        val dx = x - 0.5f
        val dy = y - 0.5f
        var angle = kotlin.math.atan2(dy, dx) * 180f / Math.PI.toFloat() + 90f
        if (angle < 0f) angle += 360f
        val weights = slices.map { if (it.isNaN() || it < 0f) 0f else it }
        val total = weights.sum().coerceAtLeast(0.0001f)
        var cursor = 0f
        var hit = 0
        weights.forEachIndexed { i, v ->
            if (angle >= cursor) hit = i
            cursor += 360f * (v / total)
        }
        return 0 to hit.coerceIn(0, slices.lastIndex)
    }
    if (kind == "scatter") {
        val finite = values.flatten().filter { !it.isNaN() }
        if (finite.isEmpty()) return null
        val max = finite.max().coerceAtLeast(0f)
        val min = finite.min().coerceAtMost(0f)
        val span = (max - min).let { if (it == 0f) 1f else it }
        val denom = (groups - 1).coerceAtLeast(1)
        val finiteX = buildList {
            for (s in 0 until seriesCount) {
                val row = values.getOrNull(s) ?: continue
                row.forEachIndexed { i, raw ->
                    if (raw.isNaN()) return@forEachIndexed
                    val explicit = xs.getOrNull(s)?.getOrNull(i)
                    add(if (explicit != null && !explicit.isNaN()) explicit else i.toFloat())
                }
            }
        }
        val minX = finiteX.minOrNull() ?: 0f
        val maxX = finiteX.maxOrNull() ?: minX
        val spanX = (maxX - minX).let { if (it == 0f) 1f else it }
        var bestSeries = 0
        var bestIndex = 0
        var bestDist = Float.POSITIVE_INFINITY
        for (s in 0 until seriesCount) {
            val row = values.getOrNull(s) ?: continue
            row.forEachIndexed { i, raw ->
                if (raw.isNaN()) return@forEachIndexed
                val explicit = xs.getOrNull(s)?.getOrNull(i)
                val rawX = if (explicit != null && !explicit.isNaN()) explicit else i.toFloat()
                val px = (rawX - minX) / spanX
                val py = 1f - (raw - min) / span
                val dist = (x - px) * (x - px) + (y - py) * (y - py)
                if (dist < bestDist) {
                    bestDist = dist
                    bestSeries = s
                    bestIndex = i
                }
            }
        }
        return bestSeries to bestIndex
    }
    val index = if (kind == "bar") {
        (x * groups).toInt().coerceIn(0, groups - 1)
    } else {
        val denom = (groups - 1).coerceAtLeast(1)
        (x * denom).roundToInt().coerceIn(0, groups - 1)
    }
    if (kind == "bar") {
        val slot = 1f / groups
        val innerStart = index * slot + slot * 0.14f
        val inner = slot * 0.72f
        val barW = inner / seriesCount
        val local = x - innerStart
        val seriesIndex = when {
            local <= 0f -> 0
            local >= inner -> seriesCount - 1
            else -> (local / barW).toInt().coerceIn(0, seriesCount - 1)
        }
        return seriesIndex to index
    }
    val finite = values.flatten().filter { !it.isNaN() }
    if (finite.isEmpty()) return null
    val max = finite.max().coerceAtLeast(0f)
    val min = finite.min().coerceAtMost(0f)
    val span = (max - min).let { if (it == 0f) 1f else it }
    var best = 0
    var bestDist = Float.POSITIVE_INFINITY
    for (s in 0 until seriesCount) {
        val raw = values.getOrNull(s)?.getOrNull(index) ?: continue
        if (raw.isNaN()) continue
        val valueY = 1f - (raw - min) / span
        val dist = kotlin.math.abs(y - valueY)
        if (dist < bestDist) {
            bestDist = dist
            best = s
        }
    }
    return best to index
}

/**
 * Mixed cartesian hit. A tap inside a bar slot prefers a bar series in that
 * group. Otherwise the nearest line, area, or scatter point wins.
 */
fun openUiChartMixHit(
    kinds: List<String>,
    groups: Int,
    tapX: Float,
    tapY: Float,
    values: List<List<Float>>,
    xs: List<List<Float>> = emptyList(),
): Pair<Int, Int>? {
    if (kinds.isEmpty() || groups <= 0 || values.isEmpty()) return null
    if (kinds.any { it == "pie" } || kinds.all { it == "pie" }) {
        return openUiChartHit("pie", 1, groups, tapX, tapY, values.take(1), xs)
    }
    val x = tapX.coerceIn(0f, 0.999999f)
    val slot = 1f / groups
    val index = (x / slot).toInt().coerceIn(0, groups - 1)
    val innerStart = index * slot + slot * 0.14f
    val inner = slot * 0.72f
    val barIndexes = kinds.mapIndexedNotNull { i, kind -> if (kind == "bar") i else null }
    if (barIndexes.isNotEmpty() && x >= innerStart && x <= innerStart + inner) {
        val barW = inner / barIndexes.size
        val local = (x - innerStart).coerceIn(0f, inner - 0.000001f)
        val pick = (local / barW).toInt().coerceIn(0, barIndexes.lastIndex)
        return barIndexes[pick] to index
    }
    val pointIndexes = kinds.mapIndexedNotNull { i, kind ->
        if (kind == "line" || kind == "area" || kind == "scatter") i else null
    }
    if (pointIndexes.isEmpty()) {
        return openUiChartHit("bar", kinds.size, groups, tapX, tapY, values, xs)
    }
    val pointValues = pointIndexes.map { values.getOrElse(it) { emptyList() } }
    val pointXs = pointIndexes.map { xs.getOrElse(it) { emptyList() } }
    val scatterOnly = pointIndexes.all { kinds[it] == "scatter" }
    val hit = if (scatterOnly) {
        openUiChartHit("scatter", pointIndexes.size, groups, tapX, tapY, pointValues, pointXs)
    } else {
        openUiChartHit("line", pointIndexes.size, groups, tapX, tapY, pointValues, pointXs)
    } ?: return null
    return pointIndexes[hit.first] to hit.second
}

/**
 * Inclusive brush indexes become a window start and count. Indexes are clamped
 * to `0 until n`. An empty series returns `0 to 0`.
 */
fun openUiChartBrush(startIndex: Int, endIndex: Int, n: Int): Pair<Int, Int> {
    if (n <= 0) return 0 to 0
    val a = startIndex.coerceIn(0, n - 1)
    val b = endIndex.coerceIn(0, n - 1)
    val start = minOf(a, b)
    val end = maxOf(a, b)
    return start to (end - start + 1)
}

/**
 * Scatter coordinates. Explicit [xs] wins. Otherwise x is the point index.
 * Length follows [values].
 */
fun openUiChartPoints(values: List<Float>, xs: List<Float> = emptyList()): List<OpenUiChartPoint> {
    return values.mapIndexed { i, y ->
        OpenUiChartPoint(x = xs.getOrNull(i) ?: i.toFloat(), y = y)
    }
}

/**
 * Chart apply payload. Window is an integer string. Kind is the local chip id.
 * [start] is the brushed index, sent only when the user released a brush.
 */
fun openUiChartApplyValues(window: Int, kind: String, start: Int? = null): Map<String, String> = buildMap {
    put("window", window.coerceAtLeast(0).toString())
    put("kind", kind.trim().lowercase())
    if (start != null) put("start", start.coerceAtLeast(0).toString())
}

/** Local inspect line. Chart taps never become ui_action. */
fun openUiChartReadout(label: String, seriesName: String, value: Float, unit: String): String {
    val number = if (value.isNaN()) "—" else if (value == value.toLong().toFloat()) value.toLong().toString() else "%.1f".format(value)
    val amount = listOf(seriesName.trim(), number, unit.trim()).filter { it.isNotBlank() }.joinToString(" ")
    return listOf(label.trim(), amount).filter { it.isNotBlank() }.joinToString(" · ")
}

data class OpenUiChartRange(
    val id: String,
    val min: Float,
    val max: Float,
    val step: Float,
    val value: Float,
)

/** First range control, if the node already carries one. Missing controls stay null. */
fun OpenUiNode.chartRange(): OpenUiChartRange? {
    val raw = props["controls"] ?: props["control"] ?: return null
    val array = runCatching { raw.jsonArray }.getOrNull()
    val obj = when {
        array != null -> array.firstNotNullOfOrNull { el ->
            val item = el as? JsonObject ?: return@firstNotNullOfOrNull null
            val type = item.string("type") ?: "range"
            if (type.equals("range", ignoreCase = true) || type.equals("slider", ignoreCase = true)) item else null
        }
        else -> raw as? JsonObject
    } ?: return null
    val type = obj.string("type")
    if (type != null && !type.equals("range", ignoreCase = true) && !type.equals("slider", ignoreCase = true)) return null
    val min = obj.float("min") ?: 1f
    val max = obj.float("max") ?: return null
    if (max < min) return null
    val step = (obj.float("step") ?: 1f).takeIf { it > 0f } ?: 1f
    val value = (obj.float("value") ?: max).coerceIn(min, max)
    return OpenUiChartRange(id = obj.string("id").orEmpty(), min = min, max = max, step = step, value = value)
}

private fun JsonObject.float(key: String): Float? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()

fun OpenUiNode.chartSeries(): List<OpenUiChartSeries> {
    val raw = props["series"] ?: return emptyList()
    val array = runCatching { raw.jsonArray }.getOrNull() ?: return emptyList()
    return array.mapIndexedNotNull { i, el ->
        val obj = el as? JsonObject
        if (obj != null) {
            val name = obj.string("name") ?: obj.string("label") ?: "系列 ${i + 1}"
            val points = obj["points"]?.let { runCatching { it.jsonArray }.getOrNull() }
            val fromPoints = points?.mapNotNull { el ->
                val point = el as? JsonObject ?: return@mapNotNull null
                val x = point.float("x") ?: return@mapNotNull null
                val y = point.float("y") ?: point.float("value") ?: return@mapNotNull null
                x to y
            }.orEmpty()
            val values = if (fromPoints.isNotEmpty()) {
                fromPoints.map { it.second }
            } else {
                (obj["values"] ?: obj["data"] ?: obj["y"])?.let { nums ->
                    runCatching { nums.jsonArray.mapNotNull { it.jsonPrimitive.content.toFloatOrNull() } }.getOrNull()
                }.orEmpty()
            }
            val xs = if (fromPoints.isNotEmpty()) {
                fromPoints.map { it.first }
            } else {
                obj["x"]?.let { nums ->
                    runCatching { nums.jsonArray.mapNotNull { it.jsonPrimitive.content.toFloatOrNull() } }.getOrNull()
                }.orEmpty()
            }
            OpenUiChartSeries(
                name = name,
                values = values,
                xs = xs,
                kind = openUiSeriesKind(obj.string("kind")),
                yAxis = openUiSeriesYAxis(obj.string("yAxis") ?: obj.string("y_axis")),
            )
        } else {
            val n = (el as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
            if (n != null) OpenUiChartSeries("系列 1", listOf(n)) else null
        }
    }
}

private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

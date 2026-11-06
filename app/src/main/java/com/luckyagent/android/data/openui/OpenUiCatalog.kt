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
)

/** Optional title/caption already present on a node. Missing keys stay blank. */
fun OpenUiNode.heading(): String? = props.string("title") ?: props.string("heading") ?: props.string("name")

fun OpenUiNode.caption(): String? = props.string("caption") ?: props.string("description") ?: props.string("subtitle")

private val openUiChartKinds = setOf("bar", "line", "area", "pie")

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

/** Last [window] points already present. A non-positive window keeps the full series. */
fun openUiChartWindow(values: List<Float>, labels: List<String>, window: Int): Pair<List<Float>, List<String>> {
    if (window <= 0 || values.size <= window) {
        val aligned = List(values.size) { i -> labels.getOrElse(i) { "" } }
        return values to aligned
    }
    val start = values.size - window
    return values.drop(start) to List(window) { i -> labels.getOrElse(start + i) { "" } }
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
            val values = (obj["values"] ?: obj["data"] ?: obj["y"])?.let { nums ->
                runCatching { nums.jsonArray.mapNotNull { it.jsonPrimitive.content.toFloatOrNull() } }.getOrNull()
            }.orEmpty()
            OpenUiChartSeries(name, values)
        } else {
            val n = (el as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
            if (n != null) OpenUiChartSeries("系列 1", listOf(n)) else null
        }
    }
}

private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

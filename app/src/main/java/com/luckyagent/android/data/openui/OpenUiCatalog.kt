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

fun OpenUiNode.listItems(maxItems: Int = 12): Pair<List<String>, Int> {
    val items = props["items"]?.jsonArray?.mapNotNull { el ->
        val obj = el as? JsonObject
        if (obj != null) obj.string("label") ?: obj.string("text")
        else (el as? JsonPrimitive)?.contentOrNull
    } ?: props.stringList("items")
    return items.take(maxItems) to (items.size - maxItems).coerceAtLeast(0)
}

/** Header plus at most [maxRows] body rows; extra is how many body rows were clipped. */
fun OpenUiNode.tableGrid(maxRows: Int = 8): Pair<List<List<String>>, Int> {
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

package com.luckyagent.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.openui.OpenUiDelta
import com.luckyagent.android.data.openui.OpenUiNode
import com.luckyagent.android.data.openui.string
import com.luckyagent.android.data.openui.stringList
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
            OpenUiNodeView(node, interactive && tree.state != "streaming", onAction)
        }
    }
}

@Composable
private fun OpenUiNodeView(node: OpenUiNode, interactive: Boolean, onAction: (String, String) -> Unit) {
    when (node.type) {
        "Table" -> OpenUiTable(node)
        "List" -> OpenUiList(node)
        "Chart" -> Text("图：${node.props.string("kind") ?: "chart"} · ${node.props.stringList("series").joinToString()}", color = CloverText2, style = MaterialTheme.typography.bodySmall)
        "Map" -> Text("地图 · ${node.props.stringList("points").size} 个点", color = CloverText2, style = MaterialTheme.typography.bodySmall)
        "Citation" -> Text("来源 ${node.props.string("source").orEmpty()}", color = CloverText3, style = MaterialTheme.typography.bodySmall)
        "Image" -> Text(node.props.string("alt") ?: node.props.string("src").orEmpty(), color = CloverText2, style = MaterialTheme.typography.bodySmall)
        "Svg" -> Text("示意图", color = CloverText2, style = MaterialTheme.typography.bodySmall)
        "Markdown" -> Text(node.props.string("text").orEmpty(), color = CloverText, style = MaterialTheme.typography.bodyMedium)
        "Actions" -> OpenUiActions(node, interactive, onAction)
        else -> {}
    }
}

@Composable
private fun OpenUiTable(node: OpenUiNode) {
    val columns = node.props.stringList("columns")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (columns.isNotEmpty()) {
            Text(columns.joinToString("  ·  "), color = CloverText3, style = MaterialTheme.typography.labelSmall)
        }
        val rows = node.props["rows"]?.jsonArray.orEmpty()
        rows.take(8).forEach { row ->
            val cells = runCatching { row.jsonArray.map { it.jsonPrimitive.content } }.getOrElse {
                listOf(row.toString())
            }
            Text(cells.joinToString("  "), color = CloverText, style = MaterialTheme.typography.bodySmall)
        }
        if (rows.size > 8) {
            Text("另有 ${rows.size - 8} 行", color = CloverText3, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OpenUiList(node: OpenUiNode) {
    val items = node.props.stringList("items").ifEmpty {
        node.props["items"]?.jsonArray?.mapNotNull { el ->
            (el.jsonObject["label"] ?: el.jsonObject["text"])?.jsonPrimitive?.content
        }.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.take(12).forEachIndexed { i, item ->
            Text("${i + 1}. $item", color = CloverText, style = MaterialTheme.typography.bodySmall)
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

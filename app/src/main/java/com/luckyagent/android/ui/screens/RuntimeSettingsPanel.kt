package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.luckyagent.android.data.api.RuntimeConfigSchemaField
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverText2
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.Json

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuntimeSettingsPanel(state: AppUiState, vm: AppViewModel) {
    val schema = state.runtimeConfigSchema
    var clearSecrets by remember { mutableStateOf(emptySet<String>()) }
    CloverCard {
        Text("运行时配置", fontWeight = FontWeight.SemiBold)
        Text("配置来自服务器。修改先暂存，可预览校验后保存；需要重启的项不会自动重启。", color = CloverText2)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { vm.loadRuntimeConfig(force = true) }) { Text("刷新") }
            OutlinedButton(onClick = { vm.reloadRuntimeConfig() }) { Text("从文件重载") }
            Button(onClick = { vm.previewRuntimeConfig() }, enabled = !state.runtimeConfigSaving) { Text("校验预览") }
            Button(onClick = { vm.saveRuntimeConfig(clearSecrets.toList()) }, enabled = !state.runtimeConfigSaving) { Text("保存") }
        }
        state.runtimeConfigError?.let { Text(it, color = CloverError) }
        if (state.runtimeConfigLoading) CircularProgressIndicator()
        schema?.let { currentSchema ->
            currentSchema.groups.entries.sortedBy { it.key }.forEach { (group, paths) ->
                val fields = paths.mapNotNull { path -> currentSchema.fields.firstOrNull { it.path == path } }
                if (fields.isNotEmpty()) {
                    Text(if (group == "hot") "即时生效" else if (group == "restart") "重启后生效" else "其他", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    fields.forEach { field -> RuntimeConfigField(field, state.runtimeConfigDraft, vm, clearSecrets.contains(field.path)) { cleared -> clearSecrets = if (cleared) clearSecrets + field.path else clearSecrets - field.path } }
                }
            }
        }
        state.runtimeConfigPreview?.let { result ->
            Text("预览：修改 ${result.changed.size} 项；即时生效 ${result.hotReloaded.size} 项；需重启 ${result.restartRequired.size} 项。", color = CloverText2)
        }
    }
}

@Composable
private fun RuntimeConfigField(field: RuntimeConfigSchemaField, root: JsonObject?, vm: AppViewModel, cleared: Boolean, onClearChanged: (Boolean) -> Unit) {
    val current = root?.getPath(field.path)
    val initial = (current as? JsonPrimitive)?.contentOrNull ?: current?.toString().orEmpty()
    var text by remember(field.path, initial) { mutableStateOf(initial) }
    if (field.type == "boolean") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(field.path + if (field.secret) "（密钥）" else "")
            Checkbox(checked = current?.jsonPrimitive?.booleanOrNull ?: false, onCheckedChange = { vm.updateRuntimeConfigDraft(field.path, JsonPrimitive(it)) })
        }
    } else {
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value
                val element = when (field.type) {
                    "integer" -> value.toIntOrNull()?.let { JsonPrimitive(it) }
                    "number" -> value.toDoubleOrNull()?.let { JsonPrimitive(it) }
                    "array", "object" -> runCatching { Json.parseToJsonElement(value) }.getOrNull()
                    else -> JsonPrimitive(value)
                }
                if (element != null) vm.updateRuntimeConfigDraft(field.path, element)
            },
            label = { Text(field.path + if (field.secret) "（留空保持不变）" else "") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            singleLine = field.type != "object" && field.type != "array",
        )
        if (field.secret) {
            TextButton(onClick = { onClearChanged(!cleared) }) { Text(if (cleared) "已标记清空" else "清空密钥") }
        }
    }
}

private fun JsonObject.getPath(path: String): JsonElement? {
    var value: JsonElement = this
    path.split('.').forEach { part -> value = (value as? JsonObject)?.get(part) ?: return null }
    return value
}

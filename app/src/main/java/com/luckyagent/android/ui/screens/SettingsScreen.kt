package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2

@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    var apiBase by rememberSaveable(state.settings.apiBase) { mutableStateOf(state.settings.apiBase) }
    var apiKey by rememberSaveable(state.settings.apiKey) { mutableStateOf(state.settings.apiKey) }
    var sessionId by rememberSaveable(state.settings.sessionId) { mutableStateOf(state.settings.sessionId) }
    var useBearer by rememberSaveable(state.settings.useBearer) { mutableStateOf(state.settings.useBearer) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = CloverText)
        Text(
            "对照 GUI Settings：连接电脑上的 lh serve。手机不是 Agent 运行时。",
            style = MaterialTheme.typography.bodyMedium,
            color = CloverText2,
        )

        Panel {
            OutlinedTextField(
                value = apiBase,
                onValueChange = { apiBase = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API Base") },
                supportingText = {
                    Text("模拟器默认 http://10.0.2.2:9090；真机用电脑局域网 IP")
                },
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API Key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    Text("对应 server.api_keys；非本机访问必填")
                },
                trailingIcon = {
                    FilterChip(
                        selected = showKey,
                        onClick = { showKey = !showKey },
                        label = { Text(if (showKey) "Hide" else "Show") },
                    )
                },
            )
            OutlinedTextField(
                value = sessionId,
                onValueChange = { sessionId = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Session ID") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = useBearer,
                    onClick = { useBearer = true },
                    label = { Text("Bearer") },
                )
                FilterChip(
                    selected = !useBearer,
                    onClick = { useBearer = false },
                    label = { Text("X-API-Key") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.saveSettings(apiBase, apiKey, sessionId, useBearer)
                    },
                ) { Text("保存并探测") }
                Button(onClick = vm::probeHealth) { Text("Health") }
                Button(onClick = vm::connectSocket) { Text("重连 WS") }
            }
            Text(
                "WS · ${state.socketState.name}" +
                    state.reconnectInfo?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = CloverText2,
            )
            state.healthText?.let {
                Text(
                    text = if (state.healthOk == true) "Health OK · $it" else "Health · $it",
                    color = if (state.healthOk == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.socketError?.let {
                Text("WS: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Panel {
            Text("安全提醒", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                """• 只走 HTTP(S) /api/v1/* + WS，不走未加固的 gRPC
• Key 存 EncryptedSharedPreferences，不进 URL
• 公网请用 Tailscale / 反代 TLS，不要裸端口
• Chat WS 断线会自动重试最多 8 次（指数退避）""",
                style = MaterialTheme.typography.bodyMedium,
                color = CloverText2,
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    val s = state.settings
    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            eyebrow = "Connection",
            title = "Settings",
            subtitle = "Phone talks only to host lh serve (HTTP + WS)",
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CloverCard {
                Text("Runtime endpoint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Point at the machine running `lh serve`. LAN IP works; localhost only if ADB reverse is set.",
                    color = CloverText2,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = s.apiBase,
                    onValueChange = { v -> vm.updateSettings { it.copy(apiBase = v) } },
                    label = { Text("API base URL") },
                    placeholder = { Text("http://192.168.x.x:18789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.wsUrl,
                    onValueChange = { v -> vm.updateSettings { it.copy(wsUrl = v) } },
                    label = { Text("WebSocket URL (optional override)") },
                    placeholder = { Text("auto from API base → /api/v1/ws") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.sessionId,
                    onValueChange = { v -> vm.updateSettings { it.copy(sessionId = v) } },
                    label = { Text("Session ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.apiKey,
                    onValueChange = { v -> vm.updateSettings { it.copy(apiKey = v) } },
                    label = { Text("API token / key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Send as Bearer", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Off → X-API-Key header",
                            color = CloverText3,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = s.useBearer,
                        onCheckedChange = { checked ->
                            vm.updateSettings { it.copy(useBearer = checked) }
                        },
                    )
                }
            }

            CloverCard {
                Text("Live checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::probeHealth) {
                        Icon(Icons.Outlined.HealthAndSafety, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Health")
                    }
                    OutlinedButton(onClick = vm::connectSocket) {
                        Icon(Icons.Outlined.Cable, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reconnect WS")
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetaChip(when (state.socketState) {
                        SocketState.Connected, SocketState.Running -> "WS connected"
                        SocketState.Connecting, SocketState.Reconnecting -> "WS connecting"
                        else -> "WS idle"
                    })
                    state.healthText?.let { MetaChip(it.take(48)) }
                }
                state.activityLine?.let {
                    Text(it, color = CloverText2, style = MaterialTheme.typography.bodyMedium)
                }
            }

            CloverCard {
                Text("About this client", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "LuckyAgent Android is a thin remote UI. Chat, trajectory, gateways, skills and memory are " +
                        "read from / written through the host HTTP API and websocket. No Go runtime is embedded.",
                    color = CloverText2,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Degraded on phone: gateway start/stop, skill install/enable, memory write, and full graph layout " +
                        "remain desktop-side when write APIs are not exposed.",
                    color = CloverText3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

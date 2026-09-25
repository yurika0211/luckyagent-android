package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.data.settings.ClientSettings
import com.luckyagent.android.data.settings.RuntimeEndpoint
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.UpdatePhase
import android.Manifest
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun SettingsScreen(state: AppUiState, vm: AppViewModel) {
    val s = state.settings
    val useTwoColumns = LocalConfiguration.current.screenWidthDp >= 1000
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

        if (useTwoColumns) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1.25f)) {
                    SettingsEndpointCard(s, vm)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsLiveCard(state, vm)
                    SettingsNotificationsCard(state, vm)
                    SettingsUpdateCard(state, vm)
                    SettingsAboutCard()
                }
            }
        } else {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsEndpointCard(s, vm)
                SettingsLiveCard(state, vm)
                SettingsNotificationsCard(state, vm)
                SettingsUpdateCard(state, vm)
                SettingsAboutCard()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsNotificationsCard(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val permissionMissing = android.os.Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    CloverCard {
        Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notify when a response is complete", style = MaterialTheme.typography.bodyMedium)
                Text("Sends one local notification for final results while you are away from the current chat.", color = CloverText2, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.settings.notifyOnChatCompleted,
                onCheckedChange = { enabled ->
                    vm.updateSettings { it.copy(notifyOnChatCompleted = enabled) }
                    if (enabled && android.os.Build.VERSION.SDK_INT >= 33 && context is Activity) {
                        ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
                    }
                },
            )
        }
        if (permissionMissing) {
            Text("Android notification permission is off.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = {
                    if (context is Activity) ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001)
                },
            ) { Text("Allow notifications") }
        }
    }
}

@Composable
private fun SettingsUpdateCard(state: AppUiState, vm: AppViewModel) {
    val update = state.update
    CloverCard {
        Text("App updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Current version ${update.currentVersion}", color = CloverText2, style = MaterialTheme.typography.bodyMedium)
        val status = when (update.phase) {
            UpdatePhase.Idle -> "Check GitHub Releases for a newer APK."
            UpdatePhase.Checking -> "Checking GitHub Releases…"
            UpdatePhase.UpToDate -> "You are on the latest release."
            UpdatePhase.Available -> "Update available: ${update.latest?.release?.tagName.orEmpty()}"
            UpdatePhase.Downloading -> "Downloading APK…"
            UpdatePhase.ReadyToInstall -> "APK downloaded. Tap Install to continue."
            UpdatePhase.Error -> update.error ?: "Update check failed."
        }
        Text(status, color = if (update.phase == UpdatePhase.Error) MaterialTheme.colorScheme.error else CloverText2, style = MaterialTheme.typography.bodySmall)
        update.latest?.release?.body?.trim()?.takeIf { it.isNotBlank() }?.let { body ->
            Text(body.replace(Regex("\\s+"), " ").take(180), color = CloverText3, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (update.phase) {
                UpdatePhase.Available -> Button(onClick = vm::downloadUpdate) { Text("Download APK") }
                UpdatePhase.Downloading, UpdatePhase.Checking -> OutlinedButton(onClick = {}, enabled = false) { Text("Working…") }
                UpdatePhase.ReadyToInstall -> Button(onClick = vm::installUpdate) { Text("Install") }
                else -> Button(onClick = vm::checkForUpdates) { Text("Check for updates") }
            }
        }
    }
}

@Composable
private fun SettingsEndpointCard(s: ClientSettings, vm: AppViewModel) {
    var editing by remember { mutableStateOf<RuntimeEndpoint?>(null) }
    var deleting by remember { mutableStateOf<RuntimeEndpoint?>(null) }
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Runtime endpoints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Manage hosts and switch the active runtime", color = CloverText2, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = {
                    editing = RuntimeEndpoint(id = UUID.randomUUID().toString(), name = "", apiBase = "")
                },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        Text(
            "Each endpoint keeps its own API URL, key and optional WebSocket URL. Keys are stored encrypted on this device.",
            color = CloverText2,
            style = MaterialTheme.typography.bodyMedium,
        )
        s.runtimeEndpoints.forEach { endpoint ->
            val active = endpoint.id == s.activeRuntimeEndpointId
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (active) CloverAccent.copy(alpha = .08f) else CloverBg, RoundedCornerShape(14.dp))
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(endpoint.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(endpoint.apiBase, style = MaterialTheme.typography.bodySmall, color = CloverText2, maxLines = 1)
                    if (active) Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = CloverAccent)
                }
                if (!active) {
                    TextButton(onClick = { vm.activateRuntimeEndpoint(endpoint.id) }) { Text("Switch") }
                }
                IconButton(onClick = { editing = endpoint }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit endpoint", tint = CloverText2)
                }
                if (s.runtimeEndpoints.size > 1) {
                    IconButton(onClick = { deleting = endpoint }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete endpoint", tint = CloverText3)
                    }
                }
            }
        }
        SettingsField("Session ID", s.sessionId, { v -> vm.updateSettings { it.copy(sessionId = v) } })
    }
    editing?.let { endpoint ->
        RuntimeEndpointDialog(
            endpoint = endpoint,
            onDismiss = { editing = null },
            onSave = { updated -> vm.saveRuntimeEndpoint(updated); editing = null },
        )
    }
    deleting?.let { endpoint ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete endpoint?") },
            text = { Text("${endpoint.name} will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRuntimeEndpoint(endpoint.id); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RuntimeEndpointDialog(
    endpoint: RuntimeEndpoint,
    onDismiss: () -> Unit,
    onSave: (RuntimeEndpoint) -> Unit,
) {
    var name by remember(endpoint.id) { mutableStateOf(endpoint.name) }
    var apiBase by remember(endpoint.id) { mutableStateOf(endpoint.apiBase) }
    var apiKey by remember(endpoint.id) { mutableStateOf(endpoint.apiKey) }
    var wsUrl by remember(endpoint.id) { mutableStateOf(endpoint.wsUrl) }
    var useBearer by remember(endpoint.id) { mutableStateOf(endpoint.useBearer) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (endpoint.name.isBlank()) "Add runtime endpoint" else "Edit runtime endpoint") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(apiBase, { apiBase = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API base URL") }, placeholder = { Text("http://192.168.x.x:18789") }, singleLine = true)
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API token / key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(wsUrl, { wsUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("WebSocket URL · optional") }, placeholder = { Text("Auto from API base") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Send as Bearer", style = MaterialTheme.typography.bodyMedium)
                        Text("Off uses X-API-Key", color = CloverText3, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = useBearer, onCheckedChange = { useBearer = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(endpoint.copy(name = name, apiBase = apiBase, apiKey = apiKey, wsUrl = wsUrl, useBearer = useBearer))
            }, enabled = apiBase.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String = "") {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (focused) CloverAccent else CloverText3)
        Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = CloverText3, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interaction,
                cursorBrush = SolidColor(CloverAccent),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = CloverText),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(if (focused) CloverAccent else CloverLine, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun SettingsLiveCard(state: AppUiState, vm: AppViewModel) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetaChip(when (state.socketState) {
                SocketState.Connected, SocketState.Running -> "WS connected"
                SocketState.Connecting, SocketState.Reconnecting -> "WS connecting"
                else -> "WS idle"
            })
            state.healthText?.let { MetaChip(it.take(48)) }
        }
        state.activityLine?.let { Text(it, color = CloverText2, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SettingsAboutCard() {
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
}

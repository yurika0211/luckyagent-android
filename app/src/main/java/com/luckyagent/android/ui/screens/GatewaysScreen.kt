package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.GatewayStatus
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CardTitleRow
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.components.StatusChip
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

private data class GatewayOption(
    val name: String,
    val label: String,
    val description: String,
    val configHint: String,
    val needsToken: Boolean = false,
)

private val gatewayOptions = listOf(
    GatewayOption(
        name = "telegram",
        label = "Telegram",
        description = "Telegram bot",
        configHint = "Token can be entered here or read from host config.",
        needsToken = true,
    ),
    GatewayOption("qqofficial", "QQ Official", "QQ 官方机器人", "Uses msg_gateway.qqofficial.* on the host."),
    GatewayOption("napcat", "NapCat", "OneBot v11 reverse WebSocket", "Uses msg_gateway.napcat.* on the host."),
    GatewayOption("feishu", "Feishu", "飞书机器人", "Uses msg_gateway.feishu.* on the host."),
    GatewayOption("weixin", "Weixin", "微信 iLink bot", "Uses msg_gateway.weixin.* on the host."),
    GatewayOption("openclawweixin", "OpenClaw Weixin", "OpenClaw account", "Uses the host OpenClaw account state."),
)

@Composable
fun GatewaysScreen(state: AppUiState, vm: AppViewModel) {
    var telegramDialogOpen by rememberSaveable { mutableStateOf(false) }
    var telegramToken by rememberSaveable { mutableStateOf("") }
    val statuses = state.gateways.associateBy { it.name.trim().lowercase() }
    val known = gatewayOptions.map { it.name }.toSet()
    val cards = gatewayOptions.map { option ->
        option to (statuses[option.name] ?: GatewayStatus(name = option.name, platform = option.name))
    } + state.gateways
        .filter { it.name.trim().lowercase() !in known }
        .map { GatewayOption(it.name, it.name, "Host gateway", "Configured on the host.") to it }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            eyebrow = "Channels",
            title = "渠道",
            subtitle = "在已连接的电脑上启动或停止渠道",
            actions = {
                IconButton(onClick = vm::refreshGateways, enabled = !state.gatewaysLoading && state.gatewayAction == null) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        if (state.gatewaysLoading) {
            Text("正在读取渠道", color = CloverText2, modifier = Modifier.padding(horizontal = 16.dp))
        }
        state.gatewaysError?.let { ErrorLine(it) }
        state.gatewayActionError?.let { ErrorLine(it) }
        Text(
            "启动使用电脑上已有的配置。Telegram 可以在这里填令牌；缺配置时会说明要去电脑上补哪一项。",
            color = CloverText3,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (cards.isEmpty()) {
            EmptyGatewayState(vm)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 420.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(cards, key = { (option, _) -> option.name }) { (option, gateway) ->
                    GatewayCard(
                        option = option,
                        gateway = gateway,
                        actionInFlight = state.gatewayAction == gateway.name.trim().lowercase(),
                        anyActionInFlight = state.gatewayAction != null || state.gatewaysLoading,
                        onStart = {
                            if (option.needsToken) {
                                telegramDialogOpen = true
                            } else {
                                vm.startGateway(option.name)
                            }
                        },
                        onStop = { vm.stopGateway(gateway.name.ifBlank { option.name }) },
                    )
                }
            }
        }
    }

    if (telegramDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (state.gatewayAction == null) {
                    telegramDialogOpen = false
                    telegramToken = ""
                }
            },
            title = { Text("启动 Telegram") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("可以填一个机器人令牌，只发给已连接的电脑，手机不保存。不填就用电脑上已有的配置。", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = telegramToken,
                        onValueChange = { telegramToken = it },
                        label = { Text("机器人令牌") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.startGateway("telegram", telegramToken.trim())
                        telegramDialogOpen = false
                        telegramToken = ""
                    },
                    enabled = state.gatewayAction == null,
                ) { Text("启动") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        telegramDialogOpen = false
                        telegramToken = ""
                    },
                    enabled = state.gatewayAction == null,
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyGatewayState(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.LinkOff, contentDescription = null, tint = CloverText3, modifier = Modifier.size(48.dp))
        Text("还没有渠道", style = MaterialTheme.typography.titleMedium)
        Text("连上电脑后重试这一条。渠道会从已连接的服务读出来。", color = CloverText3)
        TextButton(onClick = vm::refreshGateways) { Text("重试这一条") }
        TextButton(onClick = { vm.navigate(com.luckyagent.android.ui.AppDestination.Settings) }) { Text("打开设置") }
    }
}

@Composable
private fun GatewayCard(
    option: GatewayOption,
    gateway: GatewayStatus,
    actionInFlight: Boolean,
    anyActionInFlight: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    CloverCard {
        CardTitleRow(
            title = option.label,
            subtitle = listOfNotNull(
                option.description,
                if (gateway.connected == true) "已接通" else null,
            ).joinToString(" · "),
            trailing = { StatusChip(if (gateway.running) "running" else "stopped") },
        )
        Text(option.configHint, color = CloverText3, style = MaterialTheme.typography.bodySmall)
        gateway.stats?.let { stats ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("发出", stats.messagesSent?.toString() ?: "—")
                Stat("收到", stats.messagesReceived?.toString() ?: "—")
                Stat("失败", stats.errors?.toString() ?: "—")
            }
        }
        if (!gateway.error.isNullOrBlank()) {
            Text(gateway.error, color = CloverError, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (actionInFlight) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            if (gateway.running) {
                OutlinedButton(onClick = onStop, enabled = !anyActionInFlight) {
                    Text("停止")
                }
            } else {
                Button(onClick = onStart, enabled = !anyActionInFlight) {
                    Text("启动")
                }
            }
            if (actionInFlight) {
                Text("正在处理", color = CloverText2, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

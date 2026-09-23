package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.GatewayStatus
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun GatewaysScreen(state: AppUiState, vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg),
    ) {
        ScreenHeader(
            eyebrow = "Channels",
            title = "Gateways",
            subtitle = "Message gateway status from lh serve (read-only on phone)",
            actions = {
                IconButton(onClick = vm::refreshGateways) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        if (state.gatewaysLoading) {
            Text("Loading gateways…", color = CloverText2, modifier = Modifier.padding(horizontal = 16.dp))
        }
        state.gatewaysError?.let { ErrorLine(it) }

        Text(
            "Start/stop and token configuration stay on the desktop runtime. This page mirrors live status only.",
            color = CloverText3,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        when {
            !state.gatewaysLoading && state.gateways.isEmpty() && state.gatewaysError == null -> {
                EmptyState(
                    title = "No gateways reported",
                    body = "lh serve did not return gateway entries. Confirm msg-gateway is configured on the host.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 420.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.gateways, key = { it.name + it.platform.orEmpty() }) { gw ->
                        GatewayCard(gw)
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayCard(gw: GatewayStatus) {
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (gw.running) CloverLeaf else CloverText3),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    gw.name.ifBlank { gw.platform ?: "gateway" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val sub = listOfNotNull(
                    gw.platform?.takeIf { it.isNotBlank() && it != gw.name },
                    if (gw.connected == true) "connected" else null,
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, color = CloverText2, style = MaterialTheme.typography.bodyMedium)
                }
            }
            MetaChip(if (gw.running) "running" else "stopped")
        }
        gw.stats?.let { stats ->
            HorizontalDivider(color = CloverLine.copy(alpha = .75f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Sent", stats.messagesSent?.toString() ?: "—")
                Stat("Recv", stats.messagesReceived?.toString() ?: "—")
                Stat("Errors", stats.errors?.toString() ?: "—")
            }
        }
        if (!gw.error.isNullOrBlank()) {
            Text(gw.error, color = CloverError, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

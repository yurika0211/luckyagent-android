package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2

@Composable
fun GatewaysScreen(state: AppUiState, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg)
            .padding(16.dp),
    ) {
        Text("Gateways", style = MaterialTheme.typography.headlineSmall, color = CloverText)
        Text("对照 GUI Gateways：Telegram / QQ 等状态只读展示。", color = CloverText2, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRefresh, modifier = Modifier.padding(vertical = 12.dp)) { Text("Refresh") }
        Text(
            text = state.gatewaysJson ?: "尚未加载",
            style = MaterialTheme.typography.bodySmall,
            color = CloverText,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
    }
}

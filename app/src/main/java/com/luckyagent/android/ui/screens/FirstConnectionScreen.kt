package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.components.StartupPhase
import com.luckyagent.android.ui.components.StartupReconcileBar
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun FirstConnectionScreen(
    state: AppUiState,
    onScan: () -> Unit,
    onPickQrImage: () -> Unit,
    onSubmit: (String, String) -> Unit,
    onRetry: () -> Unit,
    onUseStandalone: () -> Unit,
) {
    var manual by rememberSaveable { mutableStateOf(state.settings.apiKey.isNotBlank()) }
    var address by rememberSaveable(state.settings.apiBase) {
        mutableStateOf(state.settings.apiBase.takeUnless { it.isBlank() }.orEmpty())
    }
    var key by rememberSaveable { mutableStateOf("") }
    val probing = state.computerLink == ComputerLink.Checking
    val failed = state.computerLink == ComputerLink.Failed
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("连接电脑", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "先扫电脑上的配对码，或填写地址和密钥。连上之后才能发消息。",
            color = CloverText2,
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onScan, enabled = !probing, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Text("扫码")
            }
        }
        TextButton(onClick = onPickQrImage, enabled = !probing, modifier = Modifier.fillMaxWidth()) {
            Text("从相册选择配对码")
        }
        if (!manual) {
            TextButton(onClick = { manual = true }, enabled = !probing, modifier = Modifier.fillMaxWidth()) {
                Text("手动填写")
            }
        } else {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("电脑地址") },
                placeholder = { Text("http://192.168.x.x:9090") },
                singleLine = true,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("连接密钥") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = { onSubmit(address, key) },
                enabled = !probing && address.isNotBlank() && key.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("连接") }
        }
        if (probing) {
            StartupReconcileBar(StartupPhase.Connecting)
        }
        if (failed) {
            Text(
                state.connectionNotice ?: "没能连上这台电脑。可以重试、改地址或重新扫码。",
                color = CloverError,
                style = MaterialTheme.typography.bodyMedium,
            )
            state.healthText?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = CloverText3, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRetry, enabled = !probing) { Text("重试") }
            TextButton(onClick = { manual = true }, enabled = !probing) { Text("改地址") }
            TextButton(onClick = onScan, enabled = !probing) { Text("重新扫码") }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onUseStandalone, enabled = !probing) { Text("不用电脑，直接问模型") }
    }
}

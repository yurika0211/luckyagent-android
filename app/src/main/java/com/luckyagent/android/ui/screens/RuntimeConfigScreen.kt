package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.ScreenHeader

@Composable
fun RuntimeConfigScreen(state: AppUiState, vm: AppViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = "服务器配置",
            subtitle = "保存后会影响这台电脑上的服务 · ${state.settings.apiBase.ifBlank { "未设置服务地址" }}",
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            RuntimeSettingsPanel(state, vm)
        }
    }
}

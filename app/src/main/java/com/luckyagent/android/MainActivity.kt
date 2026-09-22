package com.luckyagent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luckyagent.android.ui.LuckyAgentAppRoot
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.AppViewModelFactory
import com.luckyagent.android.ui.theme.CloverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LuckyAgentApp
        setContent {
            CloverTheme {
                val factory = remember { AppViewModelFactory(app.container) }
                val vm: AppViewModel = viewModel(factory = factory)
                LuckyAgentAppRoot(vm = vm)
            }
        }
    }
}

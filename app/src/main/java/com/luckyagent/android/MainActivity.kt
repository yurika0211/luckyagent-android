package com.luckyagent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.luckyagent.android.ui.LuckyAgentAppRoot
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.AppViewModelFactory
import com.luckyagent.android.ui.theme.CloverTheme

class MainActivity : ComponentActivity() {
    private lateinit var appViewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LuckyAgentApp
        appViewModel = ViewModelProvider(this, AppViewModelFactory(app.container))[AppViewModel::class.java]
        handleIntent(intent)
        setContent {
            CloverTheme {
                LuckyAgentAppRoot(vm = appViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::appViewModel.isInitialized) appViewModel.setAppForeground(true)
    }

    override fun onStop() {
        if (::appViewModel.isInitialized) appViewModel.setAppForeground(false)
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent?.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }?.let(appViewModel::openSessionFromNotification)
    }

    companion object { const val EXTRA_SESSION_ID = "session_id" }
}

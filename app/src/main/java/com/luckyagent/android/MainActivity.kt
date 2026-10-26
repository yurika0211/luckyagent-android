package com.luckyagent.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.luckyagent.android.ui.LuckyAgentAppRoot
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.AppViewModelFactory
import com.luckyagent.android.ui.theme.CloverTheme

class MainActivity : ComponentActivity() {
    private lateinit var appViewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // Drop the starting window as soon as Compose can draw. Do not hold it for the session API.
        setTheme(R.style.Theme_LuckyAgent)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val app = application as LuckyAgentApp
        appViewModel = ViewModelProvider(this, AppViewModelFactory(app.container))[AppViewModel::class.java]
        handleIntent(intent)
        setContent {
            val uiState by appViewModel.ui.collectAsStateWithLifecycle()
            CloverTheme(
                fontScale = uiState.settings.fontScale,
                fontFamily = uiState.settings.fontFamily,
            ) {
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
        val destination = intent?.getStringExtra(EXTRA_DESTINATION).orEmpty()
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
        when {
            // A linked execution session opens that chat. The task id still
            // rides along so the card stays the return target.
            sessionId.isNotBlank() && taskId.isNotBlank() -> {
                appViewModel.openWorkCardFromNotification(taskId, destination == "background")
                appViewModel.openSessionFromNotification(sessionId)
            }
            sessionId.isNotBlank() -> appViewModel.openSessionFromNotification(sessionId)
            taskId.isNotBlank() -> appViewModel.openWorkCardFromNotification(taskId, destination == "background")
            destination.isNotBlank() -> appViewModel.openDestinationFromNotification(destination)
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_TASK_ID = "task_id"
    }
}

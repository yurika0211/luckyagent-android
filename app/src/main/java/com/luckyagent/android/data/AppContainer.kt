package com.luckyagent.android.data

import android.content.Context
import com.luckyagent.android.data.api.LuckyAgentApi
import com.luckyagent.android.data.api.LuckyAgentWsClient
import com.luckyagent.android.data.cache.SessionCacheRepository
import com.luckyagent.android.data.settings.SettingsRepository
import com.luckyagent.android.data.standalone.StandaloneChatClient
import com.luckyagent.android.data.standalone.StandaloneStore
import com.luckyagent.android.data.notification.ChatNotificationHelper
import com.luckyagent.android.data.update.AppUpdateRepository
import com.luckyagent.android.perf.PerfDiagnosticsManager

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val perfDiagnostics = PerfDiagnosticsManager(appContext, settingsRepository)
    val sessionCache = SessionCacheRepository(appContext)
    val api = LuckyAgentApi(settingsRepository)
    val wsClient = LuckyAgentWsClient(settingsRepository)
    val updates = AppUpdateRepository(appContext)
    val notifications = ChatNotificationHelper(appContext)
    val standalone = StandaloneChatClient()
    val standaloneStore = StandaloneStore(appContext)
}

package com.luckyagent.android.data

import android.content.Context
import com.luckyagent.android.data.api.LuckyAgentApi
import com.luckyagent.android.data.api.LuckyAgentWsClient
import com.luckyagent.android.data.settings.SettingsRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val api = LuckyAgentApi(settingsRepository)
    val wsClient = LuckyAgentWsClient(settingsRepository)
}

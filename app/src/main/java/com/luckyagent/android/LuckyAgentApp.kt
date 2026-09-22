package com.luckyagent.android

import android.app.Application
import com.luckyagent.android.data.AppContainer

class LuckyAgentApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

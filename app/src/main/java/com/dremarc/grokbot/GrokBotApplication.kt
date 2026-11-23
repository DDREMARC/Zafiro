package com.dremarc.grokbot

import android.app.Application

class GrokBotApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

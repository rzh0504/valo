package com.rzh.valo

import android.app.Application
import android.content.Context
import com.rzh.valo.data.HaojiaoApi
import com.rzh.valo.data.MatchRepository
import com.rzh.valo.data.SettingsStore
import com.rzh.valo.data.SnapshotStore

class AppContainer(context: Context) {
    val settingsStore: SettingsStore = SettingsStore(context)
    val repository: MatchRepository = MatchRepository(HaojiaoApi(), SnapshotStore(context))
}

class ValoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

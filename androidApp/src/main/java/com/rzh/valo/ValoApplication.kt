package com.rzh.valo

import android.app.Application
import com.rzh.valo.data.AppContainer

class ValoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(filesDir.absolutePath)
    }
}

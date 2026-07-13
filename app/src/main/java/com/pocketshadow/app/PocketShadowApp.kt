package com.pocketshadow.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PocketShadowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Warm the SharedPreferences file on a background thread. Instances are
        // cached per name, so when SettingsRepository touches it during ViewModel
        // init on the main thread, the disk read has already happened.
        Thread {
            getSharedPreferences("pocketshadow_settings", MODE_PRIVATE).all
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }
}

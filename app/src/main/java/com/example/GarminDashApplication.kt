package com.example

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration

class GarminDashApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Global osmdroid configuration initialized ONCE before any MapView is created.
        val sharedPrefs = getSharedPreferences("osmdroid_config", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, sharedPrefs)

        // Set User-Agent compliant with OpenStreetMap tile usage policy (using unique applicationId)
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
    }
}


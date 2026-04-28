package com.linktolinux.wifidirect

import android.app.Application
import com.google.android.material.color.DynamicColors

class LinkToLinuxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

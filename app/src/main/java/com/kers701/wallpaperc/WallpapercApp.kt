package com.kers701.wallpaperc

import android.app.Application
import androidx.work.Configuration

class WallpapercApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}

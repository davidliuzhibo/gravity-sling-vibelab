package com.vrgames.gravitysling.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.gravitysling.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}

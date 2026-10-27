package com.moblized.nutritrack

import android.app.Application
import com.moblized.nutritrack.data.AppGraph
import com.moblized.nutritrack.data.Graph

class NutriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.app = AppGraph(this)
    }
}

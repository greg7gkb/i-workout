package io.github.greg7gkb.iworkout

import android.app.Application

class IWorkoutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

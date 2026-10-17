package io.github.greg7gkb.iworkout

import android.app.Application
import io.github.greg7gkb.iworkout.data.sync.SyncWorker

class IWorkoutApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedulePeriodic(this)
        SyncWorker.syncNow(this)
    }
}

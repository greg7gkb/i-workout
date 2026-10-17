package io.github.greg7gkb.iworkout.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.greg7gkb.iworkout.IWorkoutApp
import java.util.concurrent.TimeUnit

// Push finished Sessions, then pull the plan.
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as IWorkoutApp).container
        return try {
            container.log.push()
            container.plan.pull()
            container.syncStatus.markSynced()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            container.syncStatus.markFailed(e.message ?: e.javaClass.simpleName)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val NOW = "sync-now"
        private const val PERIODIC = "sync-periodic"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.APPEND_OR_REPLACE, // a request made mid-sync (e.g. on Finish) runs after it
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build(),
            )
        }

        // RUNNING while any sync is executing; WAITING when "sync now" is queued but blocked (e.g. offline).
        fun activity(context: Context): Flow<SyncActivity> {
            val wm = WorkManager.getInstance(context)
            return combine(wm.getWorkInfosForUniqueWorkFlow(NOW), wm.getWorkInfosForUniqueWorkFlow(PERIODIC)) { now, periodic ->
                when {
                    (now + periodic).any { it.state == WorkInfo.State.RUNNING } -> SyncActivity.RUNNING
                    now.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> SyncActivity.WAITING
                    else -> SyncActivity.IDLE
                }
            }.distinctUntilChanged()
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
            )
        }
    }
}

enum class SyncActivity { IDLE, WAITING, RUNNING }

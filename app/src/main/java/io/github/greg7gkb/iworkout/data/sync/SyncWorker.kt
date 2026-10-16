package io.github.greg7gkb.iworkout.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// Push dirty Sessions, then pull changed Routines/Exercises. Implemented in M1 (pull) and M3 (push).
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}

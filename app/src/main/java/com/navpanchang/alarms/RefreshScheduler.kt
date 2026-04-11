package com.navpanchang.alarms

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over [WorkManager] for scheduling [RefreshWorker].
 *
 * The periodic work runs once every 24h with the KEEP policy, so calling
 * [schedulePeriodic] repeatedly is idempotent. The one-shot variant is used by
 * [BootReceiver] and (Phase 8) the geofence receiver to trigger an immediate recompute.
 */
@Singleton
class RefreshScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RefreshWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueOneShot() {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RefreshWorker.WORK_NAME_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

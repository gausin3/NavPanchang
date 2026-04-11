package com.navpanchang.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms all pending alarms after device reboot or app upgrade.
 *
 * **Phase 3:** triggers a one-shot [RefreshWorker] via [RefreshScheduler] so the HOME
 * buffer is extended and the daily periodic work request is re-registered if WorkManager
 * lost it across the reboot.
 *
 * **Phase 4:** will additionally read `scheduled_alarms` and re-post every pending
 * `AlarmManager.PendingIntent` that still lies in the future.
 *
 * See TECH_DESIGN.md §Boot, timezone, and locale changes.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var refreshScheduler: RefreshScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Received ${intent.action} — re-registering refresh work.")
                // Re-register the periodic work (idempotent — WorkManager keeps it on
                // reboot, but being defensive here costs nothing).
                refreshScheduler.schedulePeriodic()
                // Also enqueue an immediate one-shot so the HOME buffer is fresh for
                // the user's first launch post-boot.
                refreshScheduler.enqueueOneShot()
                // Phase 4 TODO: re-arm scheduled_alarms PendingIntents.
            }
        }
    }

    private companion object {
        private const val TAG = "BootReceiver"
    }
}

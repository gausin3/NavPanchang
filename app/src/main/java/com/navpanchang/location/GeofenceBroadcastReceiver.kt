package com.navpanchang.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofencingEvent
import com.navpanchang.alarms.RefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives geofence transition broadcasts from [GeofenceManager] and enqueues a one-shot
 * [com.navpanchang.alarms.RefreshWorker] to recompute the Tier 2 CURRENT window at the
 * user's new location.
 *
 * We register with `GEOFENCE_TRANSITION_EXIT` only, so this fires exactly once per
 * boundary crossing. The worker that runs next will also register a new geofence
 * around the updated position, so subsequent crossings keep triggering fresh
 * recomputes as the user travels further.
 *
 * See TECH_DESIGN.md §Two-tier lookahead.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var refreshScheduler: RefreshScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.w(TAG, "GeofencingEvent.fromIntent returned null")
            return
        }
        if (event.hasError()) {
            Log.w(TAG, "Geofence error code: ${event.errorCode}")
            return
        }
        Log.i(TAG, "Geofence transition ${event.geofenceTransition} — enqueuing Tier 2 refresh")
        refreshScheduler.enqueueOneShot()
    }

    private companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
}

package com.navpanchang.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Registers and manages the **100 km travel geofence** that powers Tier 2 (CURRENT)
 * high-precision recomputation.
 *
 * **Lifecycle:**
 *  1. After a successful Tier 1 (HOME) lookahead, [com.navpanchang.alarms.RefreshWorker]
 *     calls [registerAroundLastCalc] with the `last_calc_lat/lon` stored in metadata.
 *  2. The GeofencingClient registers a single geofence with a 100 km radius + EXIT
 *     trigger. Android now monitors the user's location without waking the app.
 *  3. When the user crosses the boundary, Android fires
 *     [GeofenceBroadcastReceiver], which enqueues a one-shot RefreshWorker.
 *  4. The RefreshWorker recomputes Tier 2 for the new location and re-registers a new
 *     geofence around the new position (by calling back into this manager).
 *
 * **Permission gate:** background-location permission is required on Android 10+ for
 * geofences to keep working when the app is closed. If it's not granted, registration
 * silently no-ops and the app falls back to a foreground distance check via
 * [LocationProvider] on the next cold start.
 *
 * See TECH_DESIGN.md §Two-tier lookahead.
 */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationProvider: LocationProvider
) {

    private val client: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    /** `true` if we have the permission set needed for persistent geofencing. */
    fun canRegisterGeofences(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val background = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine && background
    }

    /**
     * Register a geofence of [RADIUS_METERS] radius around ([lat], [lon]) with a
     * [Geofence.GEOFENCE_TRANSITION_EXIT] trigger. Replaces any previously-registered
     * geofence for this manager.
     */
    @SuppressLint("MissingPermission")
    suspend fun registerAroundLastCalc(lat: Double, lon: Double): Boolean {
        if (!canRegisterGeofences()) {
            Log.i(TAG, "Skipping geofence registration — background location not granted")
            return false
        }

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(lat, lon, RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // don't fire if we're already outside — only on new exits
            .addGeofence(geofence)
            .build()

        // Remove any prior registration before adding the new one. We ignore the result
        // of the removal — if nothing was registered, that's fine.
        runCatching { client.removeGeofences(listOf(GEOFENCE_ID)) }

        return suspendCancellableCoroutine { cont ->
            client.addGeofences(request, pendingIntent())
                .addOnSuccessListener {
                    Log.i(TAG, "Geofence registered at ($lat, $lon) r=${RADIUS_METERS}m")
                    cont.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Geofence registration failed", e)
                    cont.resume(false)
                }
        }
    }

    fun unregister() {
        runCatching { client.removeGeofences(listOf(GEOFENCE_ID)) }
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_TRANSITION
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        const val ACTION_GEOFENCE_TRANSITION = "com.navpanchang.location.GEOFENCE_TRANSITION"

        /** 100 km — wide enough to absorb daily commute movement, tight enough to
         *  detect travel between cities. */
        private const val RADIUS_METERS = 100_000f

        /** Single stable geofence id — we only ever have one active at a time. */
        private const val GEOFENCE_ID = "navpanchang_home_buffer"

        private const val REQUEST_CODE = 0xEF0
        private const val TAG = "GeofenceManager"
    }
}

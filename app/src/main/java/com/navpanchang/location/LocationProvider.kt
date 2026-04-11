package com.navpanchang.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Thin wrapper over Google Play Services' [FusedLocationProviderClient][LocationServices].
 *
 * Exposes a single suspending method [getCurrentLocation] that resolves to the user's
 * latest known location or `null` if either permission is denied or Google Play
 * Services can't get a fix (indoor / airplane mode / disabled GPS).
 *
 * Used by:
 *  * [com.navpanchang.ui.onboarding.OnboardingScreen] to suggest a home city from GPS.
 *  * [com.navpanchang.alarms.GeofenceManager] to register a 100 km travel geofence.
 *  * [com.navpanchang.ui.settings.SettingsScreen] "use my current location" action.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * `true` if the process currently holds foreground fine location permission.
     * The caller is expected to request [Manifest.permission.ACCESS_FINE_LOCATION] via
     * `rememberLauncherForActivityResult` in the UI layer before calling
     * [getCurrentLocation].
     */
    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Resolve the user's current location as a single-shot read. Returns `null` if:
     *  * fine location permission is denied,
     *  * Google Play Services is unavailable,
     *  * no recent location fix is cached and Play Services can't obtain a new one.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasFineLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { /* no-op — Play Services cancels internally */ }
        }
    }
}

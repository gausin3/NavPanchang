package com.navpanchang.util.debug

import android.content.Context
import android.util.Log
import com.navpanchang.BuildConfig

/**
 * Hidden debug-only utility for firing alarms immediately, bypassing the 8 PM / sunrise
 * schedule. Used by the QA walkthrough to validate the Late-Subscription Gate, the
 * traveling-wall-clock receiver, and the audio channel configuration without waiting
 * 8 hours for 8 PM.
 *
 * Access: tap the version number on the About screen 7 times to reveal a hidden menu.
 *
 * **Guarded by [BuildConfig.DEBUG]** — every public entry point is a no-op on release
 * builds, so there is no way to surface it in production.
 *
 * See TECH_DESIGN.md §Phase 0 utilities.
 */
object NotificationTestingTool {

    private const val TAG = "NotificationTestingTool"

    fun firePlannerNow(context: Context) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "firePlannerNow (debug-only) — Phase 4 wires this to AlarmScheduler")
    }

    fun fireObserverNow(context: Context) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "fireObserverNow (debug-only) — Phase 4 wires this to AlarmScheduler")
    }

    fun fireParanaNow(context: Context) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "fireParanaNow (debug-only) — Phase 4 wires this to AlarmScheduler")
    }

    fun simulateLateSubscription(context: Context) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "simulateLateSubscription (debug-only) — Phase 4 wires this via LateSubscriptionGate")
    }

    fun simulateTimezoneChange(context: Context) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "simulateTimezoneChange (debug-only) — Phase 4 wires this via LocaleChangeReceiver")
    }
}

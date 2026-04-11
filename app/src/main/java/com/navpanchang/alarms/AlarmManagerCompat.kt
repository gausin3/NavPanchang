package com.navpanchang.alarms

import android.app.AlarmManager
import android.os.Build

/**
 * Compatibility shims for [AlarmManager] APIs that were added after our `minSdk = 26`.
 *
 * On API 26–30, exact alarms are always permitted — no per-app opt-out existed. The
 * [AlarmManager.canScheduleExactAlarms] method was introduced in API 31 (S) as part of
 * the SCHEDULE_EXACT_ALARM permission rollout. Calling it on older platforms would
 * crash with `NoSuchMethodError`, so we wrap it here and return `true` on older
 * versions.
 */
object AlarmManagerCompat {

    /** `true` if this device either permits exact alarms unconditionally (API ≤ 30) or
     *  has granted them via the SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM permission. */
    fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
}

package com.navpanchang.alarms

import android.app.AlarmManager
import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reliability status surface for the Settings screen's "Reliability Check" section.
 *
 * Xiaomi, Samsung, OnePlus, Vivo, Oppo, Realme and others aggressively kill background
 * workers — the generic way to detect this is via
 * [PowerManager.isIgnoringBatteryOptimizations]. When false, alarms may be delayed or
 * dropped entirely. We can't force the user to whitelist us, but we CAN surface the
 * status prominently so they can fix it proactively.
 *
 * Also exposes the API 31+ exact-alarm permission status from [AlarmManager].
 *
 * See TECH_DESIGN.md §Reliability check.
 */
@Singleton
class BatteryOptimizationCheck @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Status(
        val ignoringBatteryOptimizations: Boolean,
        val canScheduleExactAlarms: Boolean
    ) {
        /** True iff every reliability knob is in the "alarms will fire reliably" state. */
        val allGreen: Boolean
            get() = ignoringBatteryOptimizations && canScheduleExactAlarms
    }

    fun getStatus(): Status {
        val power = context.getSystemService<PowerManager>()
        val ignoring = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val alarmManager = context.getSystemService<AlarmManager>()
        val canSchedule = alarmManager?.let { AlarmManagerCompat.canScheduleExactAlarms(it) } ?: false

        return Status(
            ignoringBatteryOptimizations = ignoring,
            canScheduleExactAlarms = canSchedule
        )
    }
}

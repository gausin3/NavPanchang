package com.navpanchang.ui.settings

import com.navpanchang.alarms.BatteryOptimizationCheck

/**
 * State for the Settings tab and the Reliability Check panel within it.
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val homeCityName: String? = null,
    val homeCityDescription: String? = null,
    val sunriseOffsetMinutes: Int = 0,
    val reliability: BatteryOptimizationCheck.Status = BatteryOptimizationCheck.Status(
        ignoringBatteryOptimizations = false,
        canScheduleExactAlarms = false
    )
) {
    companion object {
        val INITIAL = SettingsUiState(loading = true)
    }
}

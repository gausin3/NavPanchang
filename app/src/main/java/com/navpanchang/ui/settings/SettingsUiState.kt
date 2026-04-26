package com.navpanchang.ui.settings

import com.navpanchang.alarms.BatteryOptimizationCheck
import com.navpanchang.panchang.AppLanguage
import com.navpanchang.panchang.LunarConvention
import com.navpanchang.panchang.NumeralSystem

/**
 * State for the Settings tab and the Reliability Check panel within it.
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val homeCityName: String? = null,
    val homeCityDescription: String? = null,
    val sunriseOffsetMinutes: Int = 0,
    val lunarConvention: LunarConvention = LunarConvention.PURNIMANTA,
    val appLanguage: AppLanguage = AppLanguage.HINDI,
    val numeralSystem: NumeralSystem = NumeralSystem.LATIN,
    val reliability: BatteryOptimizationCheck.Status = BatteryOptimizationCheck.Status(
        ignoringBatteryOptimizations = false,
        canScheduleExactAlarms = false
    )
) {
    companion object {
        val INITIAL = SettingsUiState(loading = true)
    }
}

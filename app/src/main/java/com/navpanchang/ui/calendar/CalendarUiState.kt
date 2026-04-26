package com.navpanchang.ui.calendar

import com.navpanchang.panchang.AppLanguage
import com.navpanchang.panchang.LunarConvention
import com.navpanchang.panchang.LunarMonth
import com.navpanchang.panchang.Nakshatra
import com.navpanchang.panchang.Occurrence
import com.navpanchang.panchang.Tithi
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendar month-grid state.
 *
 * Each [DayCell] contains everything needed to render a cell: the tithi at sunrise
 * (for the tiny label) and any subscribed-event occurrences that fall on that day
 * (for the colored dots).
 */
data class CalendarUiState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val days: List<DayCell> = emptyList(),
    val selectedDay: DayDetail? = null
) {
    companion object {
        val INITIAL = CalendarUiState(loading = true)
    }
}

data class DayCell(
    val date: LocalDate,
    val tithiAtSunrise: Tithi?,
    val occurrences: List<Occurrence>
) {
    val hasSubscribedEvent: Boolean get() = occurrences.isNotEmpty()
}

data class DayDetail(
    val date: LocalDate,
    val tithi: Tithi,
    val nakshatra: Nakshatra,
    val sunriseUtc: Long?,
    val sunsetUtc: Long?,
    val occurrences: List<Occurrence>,
    val isAdhik: Boolean,
    val isKshayaContext: Boolean,
    /**
     * The engine's canonical-Amanta lunar month for this day, when known. Currently
     * sourced from the first occurrence on this day; days without a subscribed
     * occurrence leave this null until a lightweight per-day classifier lands.
     * Translate to the user's [lunarConvention] at render time via
     * [com.navpanchang.panchang.displayLunarMonth].
     */
    val lunarMonth: LunarMonth? = null,
    /** The user's preferred lunar-month convention; resolved once at view-model load. */
    val lunarConvention: LunarConvention = LunarConvention.PURNIMANTA,
    /** The user's chosen *companion* language for bilingual rendering. */
    val appLanguage: AppLanguage = AppLanguage.HINDI
)

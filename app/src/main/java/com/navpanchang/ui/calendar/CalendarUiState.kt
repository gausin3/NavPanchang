package com.navpanchang.ui.calendar

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
    val isKshayaContext: Boolean
)

package com.navpanchang.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.data.repo.OccurrenceRepository
import com.navpanchang.panchang.PanchangCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backing ViewModel for [CalendarScreen] — the month-grid Panchang tab.
 *
 * For each day in the currently-selected month, computes the tithi at sunrise at the
 * user's home city plus any subscribed-event occurrences that land on that day. The
 * heavy computation happens on `Dispatchers.Default` and the result is cached in the
 * in-memory state — swapping months retriggers the compute pass.
 *
 * See TECH_DESIGN.md §Calendar.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val panchangCalculator: PanchangCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState.INITIAL)
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadMonth(YearMonth.now())
    }

    fun loadMonth(month: YearMonth) {
        _uiState.value = _uiState.value.copy(loading = true, month = month)
        viewModelScope.launch {
            val cells = buildCells(month)
            _uiState.value = _uiState.value.copy(loading = false, days = cells)
        }
    }

    fun onDayClick(day: LocalDate) {
        viewModelScope.launch {
            val detail = buildDayDetail(day)
            _uiState.value = _uiState.value.copy(selectedDay = detail)
        }
    }

    fun onDismissDayDetail() {
        _uiState.value = _uiState.value.copy(selectedDay = null)
    }

    fun onPreviousMonth() {
        loadMonth(_uiState.value.month.minusMonths(1))
    }

    fun onNextMonth() {
        loadMonth(_uiState.value.month.plusMonths(1))
    }

    // ------------------------------------------------------------------
    // Private computation.
    // ------------------------------------------------------------------

    private suspend fun buildCells(month: YearMonth): List<DayCell> =
        withContext(Dispatchers.Default) {
            val location = resolveLocation() ?: return@withContext emptyList()
            val zone = location.zone

            // One-shot read: take the first emission of the range flow. The Calendar
            // grid is a snapshot; live updates trigger a re-load via loadMonth() when
            // the user returns to the tab.
            val occurrenceList = occurrenceRepository
                .observeRange(from = month.atDay(1), to = month.atEndOfMonth())
                .first()
            val occurrenceByDate = occurrenceList.groupBy { it.dateLocal }

            val cells = mutableListOf<DayCell>()
            var day = month.atDay(1)
            while (day.monthValue == month.monthValue) {
                val snapshot = panchangCalculator.computeAtSunrise(
                    day, location.lat, location.lon, zone
                )
                cells.add(
                    DayCell(
                        date = day,
                        tithiAtSunrise = snapshot?.tithi,
                        occurrences = occurrenceByDate[day].orEmpty()
                    )
                )
                day = day.plusDays(1)
            }
            cells
        }

    private suspend fun buildDayDetail(day: LocalDate): DayDetail? =
        withContext(Dispatchers.Default) {
            val location = resolveLocation() ?: return@withContext null
            val snapshot = panchangCalculator.computeAtSunrise(
                day, location.lat, location.lon, location.zone
            ) ?: return@withContext null
            val sunset = panchangCalculator.sunsetUtc(
                day, location.lat, location.lon, location.zone
            )
            val occurrences = _uiState.value.days
                .firstOrNull { it.date == day }
                ?.occurrences.orEmpty()
            DayDetail(
                date = day,
                tithi = snapshot.tithi,
                nakshatra = snapshot.nakshatra,
                sunriseUtc = snapshot.epochMillisUtc,
                sunsetUtc = sunset,
                occurrences = occurrences,
                isAdhik = occurrences.any { it.isAdhik },
                isKshayaContext = occurrences.any { it.isKshayaContext }
            )
        }

    private suspend fun resolveLocation(): Location? {
        val metadata = metadataRepository.get() ?: return null
        val lat = metadata.homeLat ?: return null
        val lon = metadata.homeLon ?: return null
        val tzName = metadata.homeTz ?: ZoneId.systemDefault().id
        val zone = runCatching { ZoneId.of(tzName) }.getOrDefault(ZoneId.systemDefault())
        return Location(lat, lon, zone)
    }

    private data class Location(val lat: Double, val lon: Double, val zone: ZoneId)
}

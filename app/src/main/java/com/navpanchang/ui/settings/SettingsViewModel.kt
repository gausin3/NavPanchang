package com.navpanchang.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navpanchang.alarms.BatteryOptimizationCheck
import com.navpanchang.alarms.RefreshScheduler
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.location.CityCatalog
import com.navpanchang.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backing ViewModel for [SettingsScreen] and [HomeCityPickerScreen].
 *
 * Exposes reactive state for the Settings tab plus synchronous mutators for:
 *  * Home city (via [onSelectCity] or [onUseCurrentLocation])
 *  * Sunrise offset slider
 *  * Reliability status refresh
 *
 * Every mutation that affects occurrence computation triggers a one-shot
 * [RefreshScheduler.enqueueOneShot] so alarms are re-scheduled immediately.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val cityCatalog: CityCatalog,
    private val locationProvider: LocationProvider,
    private val batteryOptimizationCheck: BatteryOptimizationCheck,
    private val refreshScheduler: RefreshScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState.INITIAL)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    /** Called when the Settings tab regains focus so the Reliability Check dot refreshes. */
    fun onScreenResumed() {
        viewModelScope.launch { refresh() }
    }

    fun searchCities(query: String): List<CityCatalog.City> = cityCatalog.search(query)

    /**
     * Persist [city] as the new home city and enqueue a refresh. The Tier 1 24-month
     * lookahead will recompute for the new coordinates.
     */
    suspend fun onSelectCity(city: CityCatalog.City) {
        metadataRepository.setHomeCity(
            name = "${city.nameEn} (${city.nameHi})",
            lat = city.lat,
            lon = city.lon,
            tz = city.tz
        )
        refreshScheduler.enqueueOneShot()
        refresh()
    }

    /**
     * Resolve the user's current GPS location and try to match it to a known city in
     * the catalog (nearest match). If the catalog doesn't contain a nearby city, fall
     * back to using the raw coordinates with the device's current timezone ID.
     *
     * @param onResult invoked with `true` on success, `false` if GPS permission is
     *   denied or no fix is available.
     */
    suspend fun onUseCurrentLocation(onResult: (Boolean) -> Unit) {
        val fix = locationProvider.getCurrentLocation()
        if (fix == null) {
            onResult(false)
            return
        }
        // Find the nearest catalog city. Euclidean distance on lat/lon is fine at this
        // resolution — we only need to pick an IANA tz name that matches the user's
        // region.
        val nearest = cityCatalog.all().minByOrNull { city ->
            val dLat = city.lat - fix.latitude
            val dLon = city.lon - fix.longitude
            dLat * dLat + dLon * dLon
        }
        val tz = nearest?.tz ?: java.util.TimeZone.getDefault().id
        val name = nearest?.let { "${it.nameEn} (${it.nameHi})" } ?: "GPS location"
        metadataRepository.setHomeCity(
            name = name,
            lat = fix.latitude,
            lon = fix.longitude,
            tz = tz
        )
        refreshScheduler.enqueueOneShot()
        refresh()
        onResult(true)
    }

    fun onSunriseOffsetChange(minutes: Int) {
        viewModelScope.launch {
            metadataRepository.setSunriseOffsetMinutes(minutes)
            refresh()
            refreshScheduler.enqueueOneShot()
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val metadata = metadataRepository.getOrDefault()
        val status = batteryOptimizationCheck.getStatus()
        _uiState.value = SettingsUiState(
            loading = false,
            homeCityName = metadata.homeCityName,
            homeCityDescription = metadata.homeLat?.let { lat ->
                metadata.homeLon?.let { lon -> "%.3f, %.3f".format(lat, lon) }
            },
            sunriseOffsetMinutes = metadata.sunriseOffsetMinutes,
            reliability = status
        )
    }
}

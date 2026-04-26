package com.navpanchang.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navpanchang.alarms.BatteryOptimizationCheck
import com.navpanchang.alarms.RefreshScheduler
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.location.CityCatalog
import com.navpanchang.location.LocationProvider
import com.navpanchang.panchang.AppLanguage
import com.navpanchang.panchang.LunarConvention
import com.navpanchang.panchang.NumeralSystem
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
     *
     * **First-time only**, also persists the city's `defaultConvention` as the user's
     * lunar-month-display preference. On subsequent re-selections we leave the user's
     * existing convention alone — they may have intentionally toggled it in Settings,
     * and silently overwriting that on every city change would be surprising. They can
     * always change it manually in Settings.
     */
    suspend fun onSelectCity(city: CityCatalog.City) {
        val isFirstCityPick = metadataRepository.get()?.homeLat == null
        metadataRepository.setHomeCity(
            name = "${city.nameEn} (${city.nameHi})",
            lat = city.lat,
            lon = city.lon,
            tz = city.tz
        )
        if (isFirstCityPick) {
            metadataRepository.setLunarConvention(city.defaultConvention)
        }
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
        val isFirstCityPick = metadataRepository.get()?.homeLat == null
        metadataRepository.setHomeCity(
            name = name,
            lat = fix.latitude,
            lon = fix.longitude,
            tz = tz
        )
        // First-time only: seed convention from the nearest city. Same rationale as
        // `onSelectCity` — don't surprise users who've already configured a convention.
        if (isFirstCityPick && nearest != null) {
            metadataRepository.setLunarConvention(nearest.defaultConvention)
        }
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

    /**
     * Toggle the user's lunar-month-display convention. Display-only — does NOT trigger
     * a refresh because it doesn't affect rule matching or alarm scheduling, only how
     * existing month labels render in the UI.
     */
    fun onLunarConventionChange(convention: LunarConvention) {
        viewModelScope.launch {
            metadataRepository.setLunarConvention(convention)
            refresh()
        }
    }

    /**
     * Update the user's chosen companion language. Synchronous (SharedPreferences-backed)
     * so [SettingsScreen] can recreate the activity immediately afterward to pick up the
     * new locale via `attachBaseContext`. UI labels resolve to the new locale's
     * `values-<tag>/strings.xml` after recreate; data labels use the new companion via
     * the `bilingual*` helpers.
     */
    fun onAppLanguageChange(language: AppLanguage) {
        metadataRepository.setAppLanguage(language)
        // Refresh the in-memory state so the segmented selection updates instantly even
        // before recreate finishes.
        viewModelScope.launch { refresh() }
    }

    /**
     * Update the numeral system. Like the language toggle, the activity must be
     * recreated for the new locale (with or without `-u-nu-latn`) to take effect.
     */
    fun onNumeralSystemChange(system: NumeralSystem) {
        metadataRepository.setNumeralSystem(system)
        viewModelScope.launch { refresh() }
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
            lunarConvention = metadataRepository.lunarConvention(),
            appLanguage = metadataRepository.appLanguage(),
            numeralSystem = metadataRepository.numeralSystem(),
            reliability = status
        )
    }
}

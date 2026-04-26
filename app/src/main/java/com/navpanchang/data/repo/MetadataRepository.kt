package com.navpanchang.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.navpanchang.data.db.MetadataDao
import com.navpanchang.data.db.entities.CalcMetadataEntity
import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.panchang.AppLanguage
import com.navpanchang.panchang.LunarConvention
import com.navpanchang.panchang.NumeralSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the singleton `calc_metadata` row — home city, last-calculated
 * location, ayanamsha, tradition, event catalog version, and the user-adjustable
 * sunrise time offset.
 *
 * Treats the row as append-only from the caller's perspective: every update reads the
 * current value, patches specific fields, and writes the full record back. This keeps
 * field-level contention simple.
 */
@Singleton
class MetadataRepository @Inject constructor(
    private val dao: MetadataDao,
    @ApplicationContext context: Context
) {

    /**
     * SharedPreferences-backed mirror for [AppLanguage]. Why not Room like the rest of
     * this repository? Because [com.navpanchang.MainActivity.attachBaseContext] needs to
     * read the language *synchronously* before the Hilt graph is ready — Room's coroutine
     * APIs can't satisfy that. SharedPreferences is the standard pattern for resource-
     * loading-time preferences and is used here intentionally for that reason. Calc
     * metadata (ayanamsha, lunar convention) stays in Room because it participates in
     * coroutine-driven calculations, not activity startup.
     */
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun observe(): Flow<CalcMetadataEntity?> = dao.observe()

    suspend fun get(): CalcMetadataEntity? = dao.get()

    /**
     * Return the current metadata or a freshly-defaulted row if none exists yet. Useful
     * for the first call on a fresh install so we never have to deal with `null` in the
     * worker / UI layer.
     */
    suspend fun getOrDefault(): CalcMetadataEntity =
        dao.get() ?: CalcMetadataEntity().also { dao.upsert(it) }

    /**
     * Set the user's home city — the anchor for the Tier 1 24-month lookahead.
     * Clears `lastHomeCalcAt` so the next [com.navpanchang.alarms.RefreshWorker] run
     * knows it needs to recompute.
     */
    suspend fun setHomeCity(name: String, lat: Double, lon: Double, tz: String) {
        val current = getOrDefault()
        dao.upsert(
            current.copy(
                homeCityName = name,
                homeLat = lat,
                homeLon = lon,
                homeTz = tz,
                lastHomeCalcAt = null
            )
        )
    }

    /** Record that a HOME-tier lookahead just completed. */
    suspend fun recordHomeCalc(atEpochMillisUtc: Long) {
        val current = getOrDefault()
        dao.upsert(current.copy(lastHomeCalcAt = atEpochMillisUtc))
    }

    /**
     * Record that a CURRENT-tier (high-precision, GPS-anchored) lookahead just completed
     * for the given location. Stored so the 100 km geofence knows when the next recompute
     * is needed.
     */
    suspend fun recordCurrentCalc(lat: Double, lon: Double, atEpochMillisUtc: Long) {
        val current = getOrDefault()
        dao.upsert(
            current.copy(
                lastCalcLat = lat,
                lastCalcLon = lon,
                lastCurrentCalcAt = atEpochMillisUtc
            )
        )
    }

    /** Update the user's sunrise time offset override (Settings slider). */
    suspend fun setSunriseOffsetMinutes(minutes: Int) {
        val current = getOrDefault()
        dao.upsert(current.copy(sunriseOffsetMinutes = minutes))
    }

    /**
     * The user's configured [AyanamshaType], parsed from the stored string field. Returns
     * [AyanamshaType.LAHIRI] if the stored value is missing, blank, or not a known enum
     * member (forward-compatible in case we ever add new types and downgrade).
     *
     * This is the canonical resolution point for hot loops — resolve once at the top of
     * a worker or screen-load coroutine and thread the result down through
     * [com.navpanchang.panchang.PanchangCalculator] and its callers.
     */
    suspend fun ayanamshaType(): AyanamshaType {
        val stored = getOrDefault().ayanamshaType
        return runCatching { AyanamshaType.valueOf(stored) }
            .getOrDefault(AyanamshaType.LAHIRI)
    }

    /** Update the user's preferred ayanamsha. Wired up by the Phase 6 Settings screen. */
    suspend fun setAyanamsha(type: AyanamshaType) {
        val current = getOrDefault()
        dao.upsert(current.copy(ayanamshaType = type.name))
    }

    /**
     * The user's preferred [LunarConvention] for displaying lunar-month names. Returns
     * [LunarConvention.PURNIMANTA] if the stored value is missing, blank, or not a known
     * enum member.
     *
     * **Display only.** The engine and rule-matching always use canonical Amanta — see
     * [com.navpanchang.panchang.displayLunarMonth] for the translation rule and
     * TECH_DESIGN.md §Amanta vs Purnimanta. Resolve once per screen-load / worker run and
     * thread the value down to UI components; do not re-fetch per row.
     */
    suspend fun lunarConvention(): LunarConvention {
        val stored = getOrDefault().lunarConvention
        return runCatching { LunarConvention.valueOf(stored) }
            .getOrDefault(LunarConvention.PURNIMANTA)
    }

    /**
     * Update the user's preferred lunar-month convention. Wired up by onboarding (from the
     * picked home city's `defaultConvention`) and by the Settings convention toggle.
     */
    suspend fun setLunarConvention(convention: LunarConvention) {
        val current = getOrDefault()
        dao.upsert(current.copy(lunarConvention = convention.name))
    }

    /**
     * The user's chosen *companion* language. NavPanchang always renders English plus
     * one regional language; this enum picks the second one. Defaults to
     * [AppLanguage.HINDI]. Synchronous (SharedPreferences-backed) so it can be read from
     * `MainActivity.attachBaseContext` before Hilt is ready — see [prefs] kdoc.
     */
    fun appLanguage(): AppLanguage {
        val stored = prefs.getString(KEY_APP_LANGUAGE, null) ?: return AppLanguage.HINDI
        return runCatching { AppLanguage.valueOf(stored) }.getOrDefault(AppLanguage.HINDI)
    }

    /**
     * Persist the user's companion language. Synchronous. Caller is expected to recreate
     * `MainActivity` afterward so the new locale is picked up by Compose's resource
     * resolution — see Settings → Language toggle.
     */
    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
    }

    /**
     * The user's preferred numeral system — orthogonal to [appLanguage]. Defaults to
     * [NumeralSystem.LATIN] (ASCII digits everywhere) which matches most digital apps
     * and prevents mixed-script bugs in the fallback case. Users who want Devanagari
     * digits in Hindi UI can opt in via Settings.
     */
    fun numeralSystem(): NumeralSystem {
        val stored = prefs.getString(KEY_NUMERAL_SYSTEM, null) ?: return NumeralSystem.LATIN
        return runCatching { NumeralSystem.valueOf(stored) }.getOrDefault(NumeralSystem.LATIN)
    }

    fun setNumeralSystem(system: NumeralSystem) {
        prefs.edit().putString(KEY_NUMERAL_SYSTEM, system.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "navpanchang_prefs"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_NUMERAL_SYSTEM = "numeral_system"
    }
}

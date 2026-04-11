package com.navpanchang.data.repo

import com.navpanchang.data.db.MetadataDao
import com.navpanchang.data.db.entities.CalcMetadataEntity
import com.navpanchang.ephemeris.AyanamshaType
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
    private val dao: MetadataDao
) {

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
}

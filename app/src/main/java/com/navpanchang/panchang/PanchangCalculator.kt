package com.navpanchang.panchang

import com.navpanchang.ephemeris.AstroMath.normalizeDegrees
import com.navpanchang.ephemeris.Ayanamsha
import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.ephemeris.EphemerisEngine
import com.navpanchang.ephemeris.SunriseCalculator
import com.navpanchang.util.AstroTimeUtils
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level panchang calculator. Composes [EphemerisEngine] (sun + moon longitudes),
 * [Ayanamsha] (tropical → sidereal correction), and [SunriseCalculator] (time-of-sunrise)
 * into a single surface that answers the questions the rest of the app actually asks:
 *
 *  * "What is the panchang at this exact instant?" → [computeAtInstant]
 *  * "What is the panchang at sunrise on this date at this location?" → [computeAtSunrise]
 *  * "What time is sunrise on this date at this location?" → [sunriseUtc]
 *
 * All outputs use the user's configured ayanamsha (default [AyanamshaType.LAHIRI]) —
 * passed in at construction so the calculator can remain stateless across calls.
 *
 * **Stateless, reusable** — this class holds no per-call state, so a single instance is
 * safe to share across threads. The Hilt graph provides it as a [Singleton]. For the
 * 24-month lookahead (where we want to match the hygiene pattern from the plan's
 * `EphemerisScope` guidance) wrap the calls externally in
 * [com.navpanchang.ephemeris.EphemerisScope].
 *
 * See TECH_DESIGN.md §Panchang calculation.
 */
@Singleton
class PanchangCalculator @Inject constructor(
    private val engine: EphemerisEngine,
    private val sunriseCalculator: SunriseCalculator
) {

    /** The ayanamsha used for tropical → sidereal conversion. Default: Lahiri. */
    var ayanamshaType: AyanamshaType = AyanamshaType.LAHIRI

    /**
     * Compute a panchang snapshot at the given epoch-millis UTC. This is the finest-grained
     * API — everything else in this class is sugar on top of it.
     */
    fun computeAtInstant(epochMillisUtc: Long): PanchangSnapshot {
        val jd = AstroTimeUtils.epochMillisToJulianDay(epochMillisUtc)
        val ayanamshaDeg = Ayanamsha.compute(ayanamshaType, jd)

        val sunTropical = engine.sunApparentLongitudeDeg(jd)
        val moonTropical = engine.moonApparentLongitudeDeg(jd)

        val sunSidereal = normalizeDegrees(sunTropical - ayanamshaDeg)
        val moonSidereal = normalizeDegrees(moonTropical - ayanamshaDeg)

        val tithi = Tithi.fromMoonSunDiff(moonSidereal - sunSidereal)
        val nakshatra = Nakshatra.fromMoonSidereal(moonSidereal)

        return PanchangSnapshot(
            epochMillisUtc = epochMillisUtc,
            sunSiderealDegrees = sunSidereal,
            moonSiderealDegrees = moonSidereal,
            tithi = tithi,
            nakshatra = nakshatra
        )
    }

    /**
     * Compute a panchang snapshot at local sunrise on [date] at [latitudeDeg] / [longitudeDeg].
     *
     * Sunrise is computed from the engine's Sun ephemeris (not from a hardcoded table),
     * so the result is self-consistent: the tithi/nakshatra at the returned instant is
     * exactly what the Hindu calendar associates with "sunrise on that date at that place".
     *
     * Returns `null` at polar latitudes on dates where the Sun does not rise.
     */
    fun computeAtSunrise(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId
    ): PanchangSnapshot? {
        val sunriseMillis = sunriseCalculator.sunriseUtc(date, latitudeDeg, longitudeDeg, zone)
            ?: return null
        return computeAtInstant(sunriseMillis)
    }

    /**
     * Compute local sunrise in epoch-millis UTC for [date] at the given coordinates.
     * Thin wrapper over [SunriseCalculator] — kept here so call sites need only inject
     * [PanchangCalculator].
     */
    fun sunriseUtc(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId
    ): Long? = sunriseCalculator.sunriseUtc(date, latitudeDeg, longitudeDeg, zone)

    /** Compute local sunset in epoch-millis UTC — used as the anchor for Pradosh events. */
    fun sunsetUtc(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId
    ): Long? = sunriseCalculator.sunsetUtc(date, latitudeDeg, longitudeDeg, zone)

    /**
     * Compute the instant of **Arunodaya** — sunrise minus 96 minutes (4 ghatikas).
     *
     * This is the moment used by the **Dashami-Viddha** check: if Dashami (tithi 10 or 25)
     * is present at Arunodaya on what would otherwise be the Ekadashi day, the vrat shifts
     * to the next day (Dvadashi-yukta). See §Vrat logic in the plan and `VratLogic.kt`.
     */
    fun arunodayaUtc(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId
    ): Long? {
        val sunriseMillis = sunriseUtc(date, latitudeDeg, longitudeDeg, zone) ?: return null
        return sunriseMillis - ARUNODAYA_OFFSET_MILLIS
    }

    private companion object {
        /** 96 minutes = 4 ghatikas before sunrise, the Arunodaya interval. */
        private const val ARUNODAYA_OFFSET_MILLIS = 96L * 60L * 1000L
    }
}

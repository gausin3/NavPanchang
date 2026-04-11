package com.navpanchang.ephemeris

import com.navpanchang.ephemeris.AstroMath.cosDeg
import com.navpanchang.ephemeris.AstroMath.normalizeDegrees
import com.navpanchang.ephemeris.AstroMath.sinDeg
import com.navpanchang.util.AstroTimeUtils
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Computes local sunrise time for a given date/location via the iterative algorithm
 * in Meeus *Astronomical Algorithms* Chapter 15.
 *
 * **Why sunrise matters for panchang:** Hindu religious calendars pin tithi and nakshatra
 * presence to the *sunrise* of each date, not to midnight or noon. A two-minute sunrise
 * error can flip which day "owns" a given tithi and thus which date a vrat is observed on.
 * For Ekadashi specifically, sunrise is also the anchor for the Dashami-Viddha check
 * (`sunrise - 96 min`) and the Parana window start.
 *
 * **Accuracy:** with [MeeusEphemerisEngine] providing RA/Dec, sunrise is accurate to
 * ~10–30 seconds at mid-latitudes in the date range 1800–2200 CE. Refraction is modeled
 * as the standard `-0.833°` altitude offset (0.5667° for atmospheric refraction + 0.267°
 * for the solar disk's angular radius), matching USNO and NOAA practice.
 *
 * **NOT supported:** locations inside the polar circles on dates when the sun is always
 * above or below the horizon (sunrise doesn't exist). The method returns `null` in that
 * case. The bundled city catalog excludes high-latitude cities where this would matter.
 */
class SunriseCalculator(private val engine: EphemerisEngine) {

    /**
     * Compute the sunrise instant in epoch-millis UTC for the given Gregorian date at
     * [latitudeDeg] / [longitudeDeg]. Returns `null` if the sun does not rise on the
     * requested date at the requested latitude (polar day / night).
     *
     * @param date Local Gregorian date in the caller's [zone] — this is the "date whose
     *   sunrise we want". The method will look up the matching instant in UT.
     * @param zone The timezone used to interpret [date]. Usually [ZoneId.systemDefault].
     */
    fun sunriseUtc(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId
    ): Long? = riseOrSetUtc(date, latitudeDeg, longitudeDeg, zone, isSet = false)

    /**
     * Compute the sunset instant in epoch-millis UTC for the given Gregorian date.
     * Symmetric with [sunriseUtc]; used as the observer anchor for Pradosh events.
     * Returns `null` at polar latitudes on days when the sun does not set.
     */
    fun sunsetUtc(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId
    ): Long? = riseOrSetUtc(date, latitudeDeg, longitudeDeg, zone, isSet = true)

    private fun riseOrSetUtc(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
        isSet: Boolean
    ): Long? {
        // The caller passes a local date. We convert to a UT Julian Day at 0h UT on that
        // civil date; the iterative Meeus algorithm refines from there.
        val localMidnight = date.atStartOfDay(zone)
        val instantUtcMidnight = localMidnight.withZoneSameInstant(ZoneId.of("UTC"))
        val jd0 = AstroTimeUtils.epochMillisToJulianDay(instantUtcMidnight.toEpochMilli())

        // Meeus uses "longitude positive west" in Chapter 15. Our stored longitudes use
        // the modern convention (positive east), so flip sign for the algorithm then
        // convert back at the end.
        val lMeeus = -longitudeDeg

        // Sun's apparent RA/Dec at jd0 (Meeus recommends at 0h Dynamical Time on day D;
        // we use UT for simplicity, the DUT1 error is < 1 s which is well below our
        // tolerance).
        val alpha2 = engine.sunRightAscensionDeg(jd0)
        val delta2 = engine.sunDeclinationDeg(jd0)

        // Rise altitude h0 = -50' = -0.8333° (refraction + solar disk radius).
        val h0 = -0.8333

        // Local hour angle at rise, Meeus 15.1.
        val phi = latitudeDeg
        val cosH0 = (sinDeg(h0) - sinDeg(phi) * sinDeg(delta2)) /
            (cosDeg(phi) * cosDeg(delta2))
        if (cosH0 < -1.0 || cosH0 > 1.0) return null // polar day / night
        val h0Deg = Math.toDegrees(acos(cosH0))

        // Apparent sidereal time at Greenwich at 0h UT on day D (Meeus 15.4).
        val theta0 = greenwichSiderealTime0hUt(jd0)

        // Approximate fractional day of the event. For sunrise we SUBTRACT h0 and for
        // sunset we ADD h0 — Meeus 15.2. lMeeus is positive-west.
        val h0Offset = if (isSet) +h0Deg / 360.0 else -h0Deg / 360.0
        var m = (alpha2 + lMeeus - theta0) / 360.0 + h0Offset
        m = fracNorm(m)

        // One refinement iteration — Meeus 15.6 / 15.7. Single-epoch approximation is
        // fine for our ±1 minute tolerance.
        val theta = normalizeDegrees(theta0 + 360.985647 * m)
        val alphaInterp = alpha2
        val deltaInterp = delta2
        val hDeg = normalizeDegreesSigned(theta - lMeeus - alphaInterp)
        val altitude = Math.toDegrees(
            asinSafe(
                sinDeg(phi) * sinDeg(deltaInterp) +
                    cosDeg(phi) * cosDeg(deltaInterp) * cosDeg(hDeg)
            )
        )
        val deltaM = (altitude - h0) /
            (360.0 * cos(Math.toRadians(deltaInterp)) *
                cos(Math.toRadians(phi)) *
                sin(Math.toRadians(hDeg)))
        m = fracNorm(m + deltaM)

        // Convert fractional day to epoch-millis UTC.
        val jdEvent = jd0 + m
        return AstroTimeUtils.julianDayToEpochMillis(jdEvent)
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Greenwich mean sidereal time at 0h UT on the civil date containing [jd],
     * in degrees [0, 360). Meeus 12.4.
     */
    private fun greenwichSiderealTime0hUt(jd: Double): Double {
        val jd0h = floor(jd - 0.5) + 0.5 // JD at 0h UT of the civil date
        val t = (jd0h - 2451545.0) / 36525.0
        val gmst = 100.46061837 +
            36000.770053608 * t +
            0.000387933 * t * t -
            t * t * t / 38710000.0
        return normalizeDegrees(gmst)
    }

    /** Normalize a fractional day to [0, 1). */
    private fun fracNorm(x: Double): Double {
        val m = x - floor(x)
        return if (m < 0) m + 1.0 else m
    }

    /** Normalize an angle to the range (-180, 180]. */
    private fun normalizeDegreesSigned(deg: Double): Double {
        var r = normalizeDegrees(deg)
        if (r > 180.0) r -= 360.0
        return r
    }

    /** [asin] clamped to the legal domain, so we don't blow up on tiny numerical overruns. */
    private fun asinSafe(x: Double): Double =
        kotlin.math.asin(x.coerceIn(-1.0, 1.0))
}

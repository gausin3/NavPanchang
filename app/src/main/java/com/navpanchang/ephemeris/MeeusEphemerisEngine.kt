package com.navpanchang.ephemeris

import com.navpanchang.ephemeris.AstroMath.cosDeg
import com.navpanchang.ephemeris.AstroMath.normalizeDegrees
import com.navpanchang.ephemeris.AstroMath.sinDeg
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.pow

/**
 * Pure-Kotlin implementation of Jean Meeus's *Astronomical Algorithms* (2nd ed.) Sun and
 * Moon longitude formulas. Ships in v1 as the default engine, with accuracy characterized
 * below.
 *
 * **Sun accuracy** (Meeus Chapter 25): apparent longitude to ≤ 0.01° (36 arcseconds) over
 * 1800–2200 CE. RA/Dec derived from this via Chapter 13 with the same precision.
 *
 * **Moon accuracy** (Meeus Chapter 47): implements the top 35 ΣL periodic terms from Table
 * 47.A, giving apparent longitude to roughly 20 arcseconds for modern dates. Because the
 * Moon moves relative to the Sun at ~12.19°/day (~30.5"/minute), 20" of lunar longitude
 * error translates to ~40 seconds of tithi-boundary time error — well inside the < 60 s
 * tolerance specified in
 * [TECH_DESIGN.md](the plan, §Verification).
 *
 * When the Thomas Mack Java port is vendored in Phase 1b, [SwissEphemerisEngine] will
 * provide sub-arcsecond accuracy and become the Hilt-provided default.
 *
 * **NOTE:** This engine intentionally returns **tropical** (sayana) longitudes. Sidereal
 * conversion via [Ayanamsha.lahiri] is applied higher up in [com.navpanchang.panchang.PanchangCalculator]
 * because the ayanamsha choice is a user-configurable setting in `calc_metadata`.
 */
class MeeusEphemerisEngine : EphemerisEngine {

    override fun sunApparentLongitudeDeg(julianDayUt: Double): Double {
        val t = julianCenturiesFromJ2000(julianDayUt)

        // Mean longitude (L0), mean anomaly (M), equation of center (C) — Meeus 25.2–25.4.
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t

        val c =
            (1.914602 - 0.004817 * t - 0.000014 * t * t) * sinDeg(m) +
                (0.019993 - 0.000101 * t) * sinDeg(2 * m) +
                0.000289 * sinDeg(3 * m)

        // True longitude (θ) then apparent longitude (λ) with nutation+aberration.
        val theta = l0 + c
        val omega = 125.04 - 1934.136 * t
        val apparent = theta - 0.00569 - 0.00478 * sinDeg(omega)

        return normalizeDegrees(apparent)
    }

    override fun sunDeclinationDeg(julianDayUt: Double): Double {
        val t = julianCenturiesFromJ2000(julianDayUt)
        val lambda = sunApparentLongitudeDeg(julianDayUt)
        val epsilon = apparentObliquityDeg(t)

        val sinDelta = sinDeg(epsilon) * sinDeg(lambda)
        return Math.toDegrees(asin(sinDelta))
    }

    override fun sunRightAscensionDeg(julianDayUt: Double): Double {
        val t = julianCenturiesFromJ2000(julianDayUt)
        val lambda = sunApparentLongitudeDeg(julianDayUt)
        val epsilon = apparentObliquityDeg(t)

        val y = cosDeg(epsilon) * sinDeg(lambda)
        val x = cosDeg(lambda)
        val alpha = Math.toDegrees(atan2(y, x))
        return normalizeDegrees(alpha)
    }

    override fun moonApparentLongitudeDeg(julianDayUt: Double): Double {
        val t = julianCenturiesFromJ2000(julianDayUt)

        // Moon fundamental arguments — Meeus 47.1–47.6.
        val lPrime = 218.3164477 + 481267.88123421 * t - 0.0015786 * t * t +
            t.pow(3) / 538841 - t.pow(4) / 65194000
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t * t +
            t.pow(3) / 545868 - t.pow(4) / 113065000
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t * t +
            t.pow(3) / 24490000
        val mPrime = 134.9633964 + 477198.8675055 * t + 0.0087414 * t * t +
            t.pow(3) / 69699 - t.pow(4) / 14712000
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t * t -
            t.pow(3) / 3526000 + t.pow(4) / 863310000

        // Earth orbit eccentricity correction.
        val e = 1 - 0.002516 * t - 0.0000074 * t * t

        // Sum longitude periodic terms (Meeus Table 47.A, top 35 amplitudes).
        var sigmaL = 0.0
        for (term in LONGITUDE_TERMS) {
            val arg = term.d * d + term.m * m + term.mPrime * mPrime + term.f * f
            val eFactor = when (term.m) {
                0 -> 1.0
                1, -1 -> e
                2, -2 -> e * e
                else -> e.pow(term.m)
            }
            sigmaL += term.coeff * eFactor * sinDeg(arg)
        }

        // Add a planetary-perturbation correction (Venus) — single dominant term,
        // Meeus 47 page 338.
        val a1 = 119.75 + 131.849 * t
        sigmaL += 3958 * sinDeg(a1)

        // Mean longitude + periodic sum (coefficients are in millionths of a degree).
        val trueLong = lPrime + sigmaL / 1_000_000.0

        // Apparent longitude: add nutation in longitude (dominant term only — full IAU
        // expression has ~100 terms but the dominant one carries > 90% of the amplitude).
        val omega = 125.04452 - 1934.136261 * t
        val deltaPsi = -0.00478 * sinDeg(omega)

        return normalizeDegrees(trueLong + deltaPsi)
    }

    override fun moonLatitudeDeg(julianDayUt: Double): Double {
        val t = julianCenturiesFromJ2000(julianDayUt)

        // Latitude doesn't depend on the Moon's mean longitude L' — only on D, M, M', F
        // and the periodic term table 47.B.
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t * t
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t * t
        val mPrime = 134.9633964 + 477198.8675055 * t + 0.0087414 * t * t
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t * t

        val e = 1 - 0.002516 * t - 0.0000074 * t * t

        var sigmaB = 0.0
        for (term in LATITUDE_TERMS) {
            val arg = term.d * d + term.m * m + term.mPrime * mPrime + term.f * f
            val eFactor = when (term.m) {
                0 -> 1.0
                1, -1 -> e
                2, -2 -> e * e
                else -> e.pow(term.m)
            }
            sigmaB += term.coeff * eFactor * sinDeg(arg)
        }

        return sigmaB / 1_000_000.0
    }

    // ---------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------

    /** Julian centuries from J2000.0 (the Meeus standard epoch). */
    private fun julianCenturiesFromJ2000(jd: Double): Double =
        (jd - 2451545.0) / 36525.0

    /** Apparent obliquity of the ecliptic in degrees — Meeus 22.2 + 22.3. */
    private fun apparentObliquityDeg(t: Double): Double {
        val epsilon0 =
            23.4392911 - 0.0130042 * t - 1.64e-7 * t * t + 5.04e-7 * t * t * t
        val omega = 125.04 - 1934.136 * t
        return epsilon0 + 0.00256 * cosDeg(omega)
    }

    // ---------------------------------------------------------------------
    // Moon longitude perturbation table — Meeus Table 47.A, top 35 terms by amplitude.
    // Columns: (D coefficient, M coefficient, M' coefficient, F coefficient, Σl in 1e-6 deg)
    // ---------------------------------------------------------------------
    private data class LunarTerm(
        val d: Int,
        val m: Int,
        val mPrime: Int,
        val f: Int,
        val coeff: Double
    )

    private companion object {
        private val LONGITUDE_TERMS: List<LunarTerm> = listOf(
            LunarTerm(0, 0, 1, 0, 6288774.0),
            LunarTerm(2, 0, -1, 0, 1274027.0),
            LunarTerm(2, 0, 0, 0, 658314.0),
            LunarTerm(0, 0, 2, 0, 213618.0),
            LunarTerm(0, 1, 0, 0, -185116.0),
            LunarTerm(0, 0, 0, 2, -114332.0),
            LunarTerm(2, 0, -2, 0, 58793.0),
            LunarTerm(2, -1, -1, 0, 57066.0),
            LunarTerm(2, 0, 1, 0, 53322.0),
            LunarTerm(2, -1, 0, 0, 45758.0),
            LunarTerm(0, 1, -1, 0, -40923.0),
            LunarTerm(1, 0, 0, 0, -34720.0),
            LunarTerm(0, 1, 1, 0, -30383.0),
            LunarTerm(2, 0, 0, -2, 15327.0),
            LunarTerm(0, 0, 1, 2, -12528.0),
            LunarTerm(0, 0, 1, -2, 10980.0),
            LunarTerm(4, 0, -1, 0, 10675.0),
            LunarTerm(0, 0, 3, 0, 10034.0),
            LunarTerm(4, 0, -2, 0, 8548.0),
            LunarTerm(2, 1, -1, 0, -7888.0),
            LunarTerm(2, 1, 0, 0, -6766.0),
            LunarTerm(1, 0, -1, 0, -5163.0),
            LunarTerm(1, 1, 0, 0, 4987.0),
            LunarTerm(2, -1, 1, 0, 4036.0),
            LunarTerm(2, 0, 2, 0, 3994.0),
            LunarTerm(4, 0, 0, 0, 3861.0),
            LunarTerm(2, 0, -3, 0, 3665.0),
            LunarTerm(0, 1, -2, 0, -2689.0),
            LunarTerm(2, 0, -1, 2, -2602.0),
            LunarTerm(2, -1, -2, 0, 2390.0),
            LunarTerm(1, 0, 1, 0, -2348.0),
            LunarTerm(2, -2, 0, 0, 2236.0),
            LunarTerm(0, 1, 2, 0, -2120.0),
            LunarTerm(0, 2, 0, 0, -2069.0),
            LunarTerm(2, -2, -1, 0, 2048.0)
        )

        /** Top latitude terms from Meeus Table 47.B — coefficients in 1e-6 degrees. */
        private val LATITUDE_TERMS: List<LunarTerm> = listOf(
            LunarTerm(0, 0, 0, 1, 5128122.0),
            LunarTerm(0, 0, 1, 1, 280602.0),
            LunarTerm(0, 0, 1, -1, 277693.0),
            LunarTerm(2, 0, 0, -1, 173237.0),
            LunarTerm(2, 0, -1, 1, 55413.0),
            LunarTerm(2, 0, -1, -1, 46271.0),
            LunarTerm(2, 0, 0, 1, 32573.0),
            LunarTerm(0, 0, 2, 1, 17198.0),
            LunarTerm(2, 0, 1, -1, 9266.0),
            LunarTerm(0, 0, 2, -1, 8822.0)
        )
    }
}

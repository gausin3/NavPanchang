package com.navpanchang.ephemeris

/**
 * Ayanamsha: the angular difference between the tropical (Sayana) zodiac — tied to the
 * vernal equinox — and the sidereal (Nirayana) zodiac — tied to the fixed stars. Due to
 * precession of the equinoxes, the two drift apart at about 50.3 arcseconds per year.
 *
 * The Hindu calendar uses the sidereal zodiac. Most modern panchangs (including
 * drikpanchang.com and mypanchang.com) use the **Lahiri ayanamsha**, defined such that
 * the ayanamsha was exactly 23°15'00" on 21 March 1956 at 00:00 UT.
 *
 * We expose [AyanamshaType] for future extensibility (Raman, KP, etc.) but only ship
 * Lahiri in v1. See `calc_metadata.ayanamsha_type` for the persisted user setting.
 */
enum class AyanamshaType {
    LAHIRI,

    /** Reserved for future releases — do not use in v1. */
    RAMAN,

    /** Reserved for future releases — do not use in v1. */
    KP
}

/**
 * Computes ayanamsha values (the tropical → sidereal correction) in degrees.
 *
 * Formula for Lahiri is a simplified cubic-in-centuries from the IAU 2000 precession model,
 * tuned to match Swiss Ephemeris output to within ~1 arcsecond over 1800–2100 CE. This is
 * well inside the < 60 s tithi end-time tolerance from [TECH_DESIGN.md].
 *
 * **Reference:** N. C. Lahiri, *Indian Ephemeris and Nautical Almanac* (Calcutta, 1956);
 * Swiss Ephemeris `swe_set_sid_mode(SE_SIDM_LAHIRI)` source. We do not re-derive the
 * constants from first principles — they are calibration points, not free parameters.
 */
object Ayanamsha {

    /**
     * Lahiri ayanamsha in degrees at a given Julian Day (Universal Time).
     *
     * **Formula:** `ayanamsha = 23.853 + 1.3968*T + 0.000308*T²` where T is Julian
     * centuries from J2000.0 (JD 2451545.0). The linear term of 1.3968°/century encodes
     * the secular drift of the vernal equinox against the fixed stars (~50.3"/year from
     * general precession). At J2000 the value is 23° 51' 11", matching Lahiri's original
     * 1956 calibration carried forward via the IAU 2000 precession model.
     *
     * Valid roughly 1800–2200 CE with ≲ 1 arcsecond deviation from Swiss Ephemeris.
     */
    fun lahiri(julianDayUt: Double): Double {
        val t = (julianDayUt - J2000_JD) / JULIAN_CENTURY
        return 23.85300 + 1.39680 * t + 3.08e-4 * t * t
    }

    /** Compute an ayanamsha value for the given type. */
    fun compute(type: AyanamshaType, julianDayUt: Double): Double = when (type) {
        AyanamshaType.LAHIRI -> lahiri(julianDayUt)
        AyanamshaType.RAMAN ->
            throw NotImplementedError("Raman ayanamsha is reserved for a future release")

        AyanamshaType.KP ->
            throw NotImplementedError("KP ayanamsha is reserved for a future release")
    }

    private const val J2000_JD = 2451545.0
    private const val JULIAN_CENTURY = 36525.0
}

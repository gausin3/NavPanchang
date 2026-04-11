package com.navpanchang.ephemeris

/**
 * Abstraction over the astronomical engine that produces Sun and Moon positions for
 * panchang calculations.
 *
 * Two implementations exist:
 *
 * * [MeeusEphemerisEngine] — pure-Kotlin implementation of Jean Meeus's *Astronomical
 *   Algorithms* (2nd ed.) formulas. Works offline with zero dependencies; accuracy is
 *   ~20 arc-seconds on lunar longitude which translates to < 45 seconds on tithi end
 *   times. This is the **default engine shipped in v1**.
 *
 * * [SwissEphemerisEngine] — wraps the Thomas Mack Java port of Swiss Ephemeris. Higher
 *   precision (sub-arcsecond) but requires vendoring the JAR and shipping ~5 MB of `.se1`
 *   data files. Planned for Phase 1b once the JAR is downloaded and reviewed.
 *
 * All engines return **tropical** (sayana) longitudes in degrees in [0, 360). Callers
 * that need sidereal values (most of the panchang code) should add [Ayanamsha.lahiri]
 * explicitly — the engine does NOT apply ayanamsha on its own, because the choice of
 * ayanamsha is a user setting stored in `calc_metadata.ayanamsha_type`.
 *
 * **Input:** all methods take a Julian Day in Universal Time (UT). Convert with
 * [com.navpanchang.util.AstroTimeUtils.epochMillisToJulianDay].
 */
interface EphemerisEngine {

    /**
     * Sun's apparent geocentric tropical longitude in degrees [0, 360) at [julianDayUt].
     * Includes nutation and aberration (the "apparent" in the name).
     */
    fun sunApparentLongitudeDeg(julianDayUt: Double): Double

    /**
     * Moon's apparent geocentric tropical longitude in degrees [0, 360) at [julianDayUt].
     * Includes nutation and the main perturbation terms.
     */
    fun moonApparentLongitudeDeg(julianDayUt: Double): Double

    /**
     * Moon's geocentric latitude in degrees at [julianDayUt]. Needed for precise
     * sunrise-time-of-Moon calculations (phase-dependent). Range approximately [-6, +6]°.
     */
    fun moonLatitudeDeg(julianDayUt: Double): Double

    /**
     * Sun's declination in degrees at [julianDayUt], used by the sunrise calculator.
     * Range approximately [-23.5, +23.5]°.
     */
    fun sunDeclinationDeg(julianDayUt: Double): Double

    /**
     * Sun's right ascension in degrees [0, 360) at [julianDayUt], used by the sunrise
     * calculator for its local-hour-angle computation.
     */
    fun sunRightAscensionDeg(julianDayUt: Double): Double
}

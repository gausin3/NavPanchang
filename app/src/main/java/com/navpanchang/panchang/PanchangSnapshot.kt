package com.navpanchang.panchang

/**
 * Output of [PanchangCalculator.computeAtInstant] — a snapshot of panchang values at a
 * single instant in time for a specific location. This is the granular "what is the
 * panchang *right now*" result; higher-level daily summaries (full sunrise-to-sunrise
 * tithi coverage, events, Adhik classification) live in `OccurrenceComputer`.
 *
 * All longitudes are sidereal (Lahiri ayanamsha applied) in degrees in [0, 360).
 */
data class PanchangSnapshot(
    /** The instant these values were computed at, as epoch-millis UTC. */
    val epochMillisUtc: Long,

    /** Sun's apparent sidereal longitude in degrees, Lahiri ayanamsha applied. */
    val sunSiderealDegrees: Double,

    /** Moon's apparent sidereal longitude in degrees, Lahiri ayanamsha applied. */
    val moonSiderealDegrees: Double,

    /** The tithi in effect at this instant. */
    val tithi: Tithi,

    /** The nakshatra in effect at this instant. */
    val nakshatra: Nakshatra
) {
    /** Moon minus Sun longitude, normalized to [0, 360). */
    val moonMinusSunDegrees: Double
        get() {
            val diff = (moonSiderealDegrees - sunSiderealDegrees) % 360.0
            return if (diff < 0) diff + 360.0 else diff
        }
}

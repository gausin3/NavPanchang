package com.navpanchang.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Time conversions shared between Swiss Ephemeris (which speaks Julian Day) and the
 * rest of the app (which speaks epoch-millis / [ZonedDateTime]).
 *
 * **Why centralize this:** Swiss Eph's native time format is a `double` Julian Day in
 * Universal Time. Constructing JDs inline gets wrong subtly — months are 1-indexed,
 * the Gregorian/Julian cutoff is a trap, and the UT1↔UTC delta matters for the
 * tithi-end-time precision we need. One helper, one place to fix it.
 *
 * **UT1 vs UTC:** DUT1 (UT1 minus UTC) is always < 0.9 seconds. Panchang tithi
 * boundaries move by about 30 seconds per second of UT offset, so ignoring DUT1
 * gives us sub-minute accuracy — well inside the drikpanchang.com cross-reference
 * tolerance in our tests. We document this assumption here rather than baking it
 * in silently.
 *
 * See TECH_DESIGN.md §Phase 0 utilities.
 */
object AstroTimeUtils {

    /** 1970-01-01T00:00:00Z expressed as a Julian Day. */
    private const val JD_UNIX_EPOCH = 2440587.5
    private const val MILLIS_PER_DAY = 86_400_000.0

    /** Convert an epoch-millis instant to a Julian Day (UT, close enough to UTC). */
    fun epochMillisToJulianDay(epochMillis: Long): Double =
        JD_UNIX_EPOCH + epochMillis / MILLIS_PER_DAY

    /** Convert a Julian Day (UT) back to an epoch-millis instant. */
    fun julianDayToEpochMillis(jd: Double): Long =
        ((jd - JD_UNIX_EPOCH) * MILLIS_PER_DAY).toLong()

    /** Convenience: Julian Day at midnight UTC of the given [LocalDate]. */
    fun julianDayAtMidnightUtc(date: LocalDate): Double {
        val instant = date.atStartOfDay(ZoneId.of("UTC")).toInstant()
        return epochMillisToJulianDay(instant.toEpochMilli())
    }

    /** Convert an epoch-millis instant to a [ZonedDateTime] in the given timezone. */
    fun epochMillisToZoned(epochMillis: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone)
}

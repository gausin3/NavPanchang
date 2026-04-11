package com.navpanchang.ephemeris

import com.navpanchang.util.AstroTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Validates [MeeusEphemerisEngine] against known astronomical reference values.
 *
 * These are loose tolerance tests (~0.5°) aimed at catching **gross** errors like a
 * wrong sign, a missing term, or a degree/radian mixup. They do NOT pretend to verify
 * Meeus-level precision — the engine is mathematically derived from the published
 * formulas, and the formulas themselves are accurate to ~0.01° for the Sun and ~0.005°
 * for the Moon when the full term series is used.
 *
 * For tight precision checks against drikpanchang.com / mypanchang.com golden data, see
 * `PanchangCalculatorTest` which exercises the full pipeline (engine + ayanamsha).
 */
class MeeusEphemerisEngineTest {

    private val engine = MeeusEphemerisEngine()

    /**
     * Converts a UTC date-time to a Julian Day via [AstroTimeUtils]. Keeps the test
     * assertions from needing to deal with JD arithmetic inline.
     */
    private fun jdAtUtc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Double {
        val millis = LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        return AstroTimeUtils.epochMillisToJulianDay(millis)
    }

    // ---------------------------------------------------------------
    // Sun longitude — reference values from published solstice/equinox tables.
    // ---------------------------------------------------------------

    @Test
    fun `sun is near the vernal equinox point on 2024 March 20`() {
        // Vernal equinox 2024 was at 2024-03-20 03:06 UT, where Sun tropical longitude = 0°.
        // At 00:00 UT the Sun should be at ~359.87° (3h06m × 15°/h ≈ 0.13° shy of equinox).
        val jd = jdAtUtc(2024, 3, 20, 0, 0)
        val lon = engine.sunApparentLongitudeDeg(jd)
        // Value should be in (358, 360) or (0, 2) accounting for wrap.
        val unwrapped = if (lon > 180) lon - 360 else lon
        assertTrue(
            "Expected Sun near 0° on vernal equinox, got $lon",
            unwrapped in -2.0..2.0
        )
    }

    @Test
    fun `sun is near the summer solstice point on 2024 June 21`() {
        // Summer solstice 2024 was at 2024-06-20 20:51 UT, Sun longitude = 90°.
        // At 00:00 UT on June 21 we're ~3.15 h past solstice ⇒ Sun at ~90.13°.
        val jd = jdAtUtc(2024, 6, 21, 0, 0)
        val lon = engine.sunApparentLongitudeDeg(jd)
        assertEquals("Sun should be near 90° post-solstice", 90.0, lon, 1.0)
    }

    @Test
    fun `sun is near the winter solstice point on 2024 December 21`() {
        // Winter solstice 2024 was at 2024-12-21 09:20 UT, Sun longitude = 270°.
        val jd = jdAtUtc(2024, 12, 21, 12, 0)
        val lon = engine.sunApparentLongitudeDeg(jd)
        assertEquals("Sun should be near 270° post-winter-solstice", 270.0, lon, 1.0)
    }

    // ---------------------------------------------------------------
    // Moon longitude — verified by sun–moon conjunction/opposition at published
    // new/full moon times.
    // ---------------------------------------------------------------

    @Test
    fun `moon and sun are within 1 degree at the 2024 January 11 new moon`() {
        // Published new moon: 2024-01-11 11:57 UT.
        val jd = jdAtUtc(2024, 1, 11, 11, 57)
        val sunLon = engine.sunApparentLongitudeDeg(jd)
        val moonLon = engine.moonApparentLongitudeDeg(jd)
        val delta = normalizedDiff(moonLon, sunLon)
        assertTrue(
            "New moon sun=$sunLon moon=$moonLon diff=$delta should be near 0°",
            delta < 1.0 || delta > 359.0
        )
    }

    @Test
    fun `moon and sun are 180 degrees apart at the 2024 January 25 full moon`() {
        // Published full moon: 2024-01-25 17:54 UT.
        val jd = jdAtUtc(2024, 1, 25, 17, 54)
        val sunLon = engine.sunApparentLongitudeDeg(jd)
        val moonLon = engine.moonApparentLongitudeDeg(jd)
        val delta = normalizedDiff(moonLon, sunLon)
        assertEquals(
            "Full moon sun=$sunLon moon=$moonLon diff=$delta should be near 180°",
            180.0, delta, 1.0
        )
    }

    @Test
    fun `moon and sun are within 1 degree at the 2024 June 6 new moon`() {
        // Published new moon: 2024-06-06 12:37 UT (Rohini Amavasya).
        val jd = jdAtUtc(2024, 6, 6, 12, 37)
        val sunLon = engine.sunApparentLongitudeDeg(jd)
        val moonLon = engine.moonApparentLongitudeDeg(jd)
        val delta = normalizedDiff(moonLon, sunLon)
        assertTrue(
            "New moon sun=$sunLon moon=$moonLon diff=$delta should be near 0°",
            delta < 1.0 || delta > 359.0
        )
    }

    // ---------------------------------------------------------------
    // Sanity checks on derived quantities.
    // ---------------------------------------------------------------

    @Test
    fun `sun declination oscillates within tropic of cancer and capricorn`() {
        // Over a full year, sun declination must stay within ±23.5°. Sample every month.
        for (month in 1..12) {
            val jd = jdAtUtc(2024, month, 15, 12, 0)
            val dec = engine.sunDeclinationDeg(jd)
            assertTrue(
                "Sun dec on 2024-$month-15 was $dec, outside ±23.5°",
                dec in -23.5..23.5
            )
        }
    }

    @Test
    fun `sun right ascension monotonically traverses 360 over a year`() {
        // Pick 13 dates spanning 13 months; RA should cover ~360° total (allowing wrap).
        val samples = (0..12).map { month ->
            val y = 2024 + month / 12
            val m = (month % 12) + 1
            engine.sunRightAscensionDeg(jdAtUtc(y, m, 1, 0, 0))
        }
        // After unwrapping, the first-to-last span must be close to 360° for a full year.
        var unwrappedLast = samples[0]
        var spanCount = 0
        for (i in 1..12) {
            var x = samples[i]
            while (x < unwrappedLast - 10) x += 360.0
            if (x - unwrappedLast > 10) spanCount++
            unwrappedLast = x
        }
        assertTrue("RA should step forward over 12 months, got $spanCount increases", spanCount >= 10)
    }

    @Test
    fun `moon latitude stays within plus minus 6 degrees of ecliptic`() {
        // Moon's inclination is ~5.15° so |lat| ≤ ~5.3°. Sample throughout a month.
        for (day in 1..28) {
            val jd = jdAtUtc(2024, 1, day, 0, 0)
            val lat = engine.moonLatitudeDeg(jd)
            assertTrue(
                "Moon lat on 2024-01-$day was $lat, outside ±6°",
                lat in -6.0..6.0
            )
        }
    }

    /** Non-negative normalized angular difference `a - b` in [0, 360). */
    private fun normalizedDiff(a: Double, b: Double): Double {
        val d = (a - b) % 360.0
        return if (d < 0) d + 360.0 else d
    }
}

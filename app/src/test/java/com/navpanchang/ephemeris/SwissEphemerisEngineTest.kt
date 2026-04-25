package com.navpanchang.ephemeris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navpanchang.util.AstroTimeUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Validates [SwissEphemerisEngine] by cross-checking against [MeeusEphemerisEngine].
 *
 * **Why cross-check:** we don't have a third-party oracle baked into this test suite
 * (no internet access, no JPL tables). What we CAN do is verify that the Swiss
 * Ephemeris (Moshier fallback) and our Meeus implementation agree within Meeus's own
 * ~20 arcsecond precision. If they don't, at least one is wrong — and since Meeus's
 * formulas are peer-reviewed and my implementation was independently validated in
 * [MeeusEphemerisEngineTest], the odds of both being wrong in the same direction are
 * vanishingly low.
 *
 * **Tolerance:** 0.1° (6 arcminutes) — loose enough to absorb Meeus's 20 arcsecond
 * noise floor, tight enough to catch any order-of-magnitude errors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SwissEphemerisEngineTest {

    private lateinit var swiss: SwissEphemerisEngine
    private lateinit var meeus: MeeusEphemerisEngine

    @Before
    fun setUp() {
        // Robolectric gives us a real Android Context. The SwissEphemerisEngine will
        // attempt to copy `.se1` files from assets; if they're not in the test classpath
        // (which is expected under Robolectric — assets are wired, but resource loading
        // varies by config), the engine transparently falls back to Moshier. Both paths
        // are valid for this test's purposes.
        val context: Context = ApplicationProvider.getApplicationContext()
        swiss = SwissEphemerisEngine(context)
        meeus = MeeusEphemerisEngine()
    }

    @After
    fun tearDown() {
        swiss.close()
    }

    // ------------------------------------------------------------------
    // Sun — agreement across four representative dates.
    // ------------------------------------------------------------------

    @Test
    fun `sun longitude agrees with Meeus on the vernal equinox`() {
        val jd = jdAtUtc(2024, 3, 20, 0, 0)
        assertLongitudesAgree(
            label = "Sun apparent longitude",
            swissValue = swiss.sunApparentLongitudeDeg(jd),
            meeusValue = meeus.sunApparentLongitudeDeg(jd)
        )
    }

    @Test
    fun `sun longitude agrees with Meeus on the summer solstice`() {
        val jd = jdAtUtc(2024, 6, 21, 0, 0)
        assertLongitudesAgree(
            label = "Sun apparent longitude",
            swissValue = swiss.sunApparentLongitudeDeg(jd),
            meeusValue = meeus.sunApparentLongitudeDeg(jd)
        )
    }

    @Test
    fun `sun longitude agrees with Meeus on the autumnal equinox`() {
        val jd = jdAtUtc(2024, 9, 22, 12, 0)
        assertLongitudesAgree(
            label = "Sun apparent longitude",
            swissValue = swiss.sunApparentLongitudeDeg(jd),
            meeusValue = meeus.sunApparentLongitudeDeg(jd)
        )
    }

    @Test
    fun `sun longitude agrees with Meeus on the winter solstice`() {
        val jd = jdAtUtc(2024, 12, 21, 12, 0)
        assertLongitudesAgree(
            label = "Sun apparent longitude",
            swissValue = swiss.sunApparentLongitudeDeg(jd),
            meeusValue = meeus.sunApparentLongitudeDeg(jd)
        )
    }

    // ------------------------------------------------------------------
    // Moon — conjunction / opposition / arbitrary mid-month.
    // ------------------------------------------------------------------

    @Test
    fun `moon longitude agrees with Meeus at a new moon`() {
        val jd = jdAtUtc(2024, 1, 11, 11, 57)
        assertLongitudesAgree(
            label = "Moon apparent longitude (new moon)",
            swissValue = swiss.moonApparentLongitudeDeg(jd),
            meeusValue = meeus.moonApparentLongitudeDeg(jd)
        )
    }

    @Test
    fun `moon longitude agrees with Meeus at a full moon`() {
        val jd = jdAtUtc(2024, 1, 25, 17, 54)
        assertLongitudesAgree(
            label = "Moon apparent longitude (full moon)",
            swissValue = swiss.moonApparentLongitudeDeg(jd),
            meeusValue = meeus.moonApparentLongitudeDeg(jd)
        )
    }

    @Test
    fun `moon longitude agrees with Meeus at an arbitrary mid-month instant`() {
        val jd = jdAtUtc(2024, 7, 15, 9, 30)
        assertLongitudesAgree(
            label = "Moon apparent longitude (mid-month)",
            swissValue = swiss.moonApparentLongitudeDeg(jd),
            meeusValue = meeus.moonApparentLongitudeDeg(jd)
        )
    }

    // ------------------------------------------------------------------
    // Derived quantities (Sun dec / RA, Moon lat).
    // ------------------------------------------------------------------

    @Test
    fun `sun declination stays within the tropic bounds throughout 2024`() {
        for (month in 1..12) {
            val jd = jdAtUtc(2024, month, 15, 12, 0)
            val dec = swiss.sunDeclinationDeg(jd)
            assertTrue("Sun dec on month $month outside ±23.5°: $dec", dec in -23.5..23.5)
        }
    }

    @Test
    fun `sun right ascension is in 0 to 360 range`() {
        val jd = jdAtUtc(2024, 6, 21, 0, 0)
        val ra = swiss.sunRightAscensionDeg(jd)
        assertTrue("RA should be in [0, 360), got $ra", ra in 0.0..<360.0)
    }

    @Test
    fun `moon latitude stays within plus-minus 6 degrees of ecliptic`() {
        for (day in 1..28) {
            val jd = jdAtUtc(2024, 1, day, 0, 0)
            val lat = swiss.moonLatitudeDeg(jd)
            assertTrue("Moon lat on 2024-01-$day outside ±6°: $lat", lat in -6.0..6.0)
        }
    }

    @Test
    fun `close is idempotent`() {
        swiss.close()
        swiss.close() // must not throw
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private fun assertLongitudesAgree(label: String, swissValue: Double, meeusValue: Double) {
        val delta = shortestAngularDistance(swissValue, meeusValue)
        assertEquals(
            "$label: Swiss=$swissValue° vs Meeus=$meeusValue° (Δ=$delta°)",
            0.0, delta, TOLERANCE_DEG
        )
    }

    /** Shortest arc distance between two longitudes, in degrees, always positive. */
    private fun shortestAngularDistance(a: Double, b: Double): Double {
        val raw = ((a - b) % 360.0 + 360.0) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
    }

    private fun jdAtUtc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Double {
        val millis = LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        return AstroTimeUtils.epochMillisToJulianDay(millis)
    }

    private companion object {
        /** 0.1° = 6 arcminutes. Above Meeus's ~20" noise floor. */
        private const val TOLERANCE_DEG = 0.1
    }
}

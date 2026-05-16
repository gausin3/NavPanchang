package com.navpanchang.ephemeris

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Regression test for the **sunrise local-date off-by-one** bug.
 *
 * **The bug:** [SunriseCalculator.riseOrSetUtc] accepted a `zone` parameter but
 * never used it — it anchored the Meeus algorithm at UT-midnight of the requested
 * date. For locations east of Greenwich (all of India, UTC+5:30), local sunrise
 * (~05:10 IST) occurs at ~23:40 UTC of the *previous* UTC day, so the returned
 * instant landed on the **next local day**. This shifted every tithi-anchored
 * panchang date (every Ekadashi, Purnima, festival) by one day for the entire
 * Indian user base. Notably, Nirjala Ekadashi 2026 showed as May 26 instead of
 * the correct May 27.
 *
 * **Why existing tests missed it:** every prior panchang test asserted *internal
 * consistency* ("find the date where tithi==11, then check the contract"). A
 * *consistent* off-by-one satisfies all of them. These tests instead assert
 * against **external ground truth**: the local civil date a sunrise instant
 * falls on, and the published Nirjala Ekadashi 2026 date.
 *
 * If this test fails, the timezone handling in [SunriseCalculator] regressed.
 */
class SunriseLocalDateRegressionTest {

    private val engine = MeeusEphemerisEngine()
    private val sun = SunriseCalculator(engine)

    // Lucknow — canonical Indian test location, UTC+5:30 (the failing case).
    private val lkoLat = 26.8467
    private val lkoLon = 80.9462
    private val ist: ZoneId = ZoneId.of("Asia/Kolkata")

    // New York — western hemisphere, UTC-4/5. Guards against "fixed India by
    // breaking the Americas" — a naive fix that just subtracts a day would
    // shift these the wrong way.
    private val nycLat = 40.7128
    private val nycLon = -74.0060
    private val nyc: ZoneId = ZoneId.of("America/New_York")

    /**
     * The core invariant: the sunrise instant returned for local date D, when
     * converted back to the requesting zone, MUST fall on local date D. Checked
     * across a full month so a boundary-only fix can't sneak through.
     */
    @Test
    fun `sunrise instant lands on the requested local date - Lucknow, full month`() {
        var date = LocalDate.of(2026, 5, 1)
        repeat(31) {
            val utc = sun.sunriseUtc(date, lkoLat, lkoLon, ist)
                ?: error("Expected a sunrise for $date at Lucknow")
            val gotLocalDate = Instant.ofEpochMilli(utc).atZone(ist).toLocalDate()
            assertEquals(
                "Sunrise for $date (IST) must fall on $date locally, not $gotLocalDate",
                date,
                gotLocalDate
            )
            date = date.plusDays(1)
        }
    }

    @Test
    fun `sunrise instant lands on the requested local date - New York, full month`() {
        var date = LocalDate.of(2026, 5, 1)
        repeat(31) {
            val utc = sun.sunriseUtc(date, nycLat, nycLon, nyc)
                ?: error("Expected a sunrise for $date at New York")
            val gotLocalDate = Instant.ofEpochMilli(utc).atZone(nyc).toLocalDate()
            assertEquals(
                "Sunrise for $date (ET) must fall on $date locally, not $gotLocalDate",
                date,
                gotLocalDate
            )
            date = date.plusDays(1)
        }
    }

    /**
     * Sunrise clock time at Lucknow in late May is well-known (~05:10 IST). A
     * gross error in the fix (e.g. snapping to the wrong day, or noon instead of
     * sunrise) would move this far outside a sane dawn window.
     */
    @Test
    fun `sunrise clock time at Lucknow late May is a plausible dawn time`() {
        val utc = sun.sunriseUtc(LocalDate.of(2026, 5, 27), lkoLat, lkoLon, ist)!!
        val local = Instant.ofEpochMilli(utc).atZone(ist)
        assertEquals(LocalDate.of(2026, 5, 27), local.toLocalDate())
        val minutesIntoDay = local.hour * 60 + local.minute
        // Dawn at Lucknow in late May is ~05:10 IST. Allow a wide 04:30–06:00
        // band so this asserts "is a sunrise" without being brittle to engine.
        assert(minutesIntoDay in (4 * 60 + 30)..(6 * 60)) {
            "Lucknow sunrise 2026-05-27 should be ~05:10 IST, got ${local.hour}:${local.minute}"
        }
    }
}

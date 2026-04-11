package com.navpanchang.panchang

import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.ephemeris.MeeusEphemerisEngine
import com.navpanchang.ephemeris.SunriseCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Integration-style unit tests for [PanchangCalculator].
 *
 * These exercise the full pipeline: [MeeusEphemerisEngine] → [com.navpanchang.ephemeris.Ayanamsha]
 * → [Tithi] / [Nakshatra] identification. We pin expectations against well-known moon
 * phases (which are observational facts, independent of any particular panchang source).
 *
 * **Tolerance philosophy:** For gross-error guards we use ±1° on longitudes (~2 min of
 * time) and on boundary tests we sample ≥ 1 hour away from the actual transition so the
 * tithi identification is unambiguous regardless of our ~20-arcsec implementation error.
 *
 * When [com.navpanchang.ephemeris.SwissEphemerisEngine] lands in Phase 1b, a sister file
 * with the same scenarios but tighter tolerances will be added.
 */
class PanchangCalculatorTest {

    private lateinit var calculator: PanchangCalculator

    @Before
    fun setUp() {
        val engine = MeeusEphemerisEngine()
        val sunrise = SunriseCalculator(engine)
        calculator = PanchangCalculator(engine, sunrise)
    }

    // ------------------------------------------------------------------
    // New moon / full moon tithi identification.
    // ------------------------------------------------------------------

    @Test
    fun `just before the 2024 January 11 new moon we are in Amavasya`() {
        // Published new moon: 2024-01-11 11:57 UT. Sample 30 min before to safely land
        // inside tithi 30 (Amavasya).
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 1, 11, 11, 27))
        assertEquals("Expected Amavasya (tithi 30)", 30, snapshot.tithi.index)
        assertEquals(Paksha.Krishna, snapshot.tithi.paksha)
    }

    @Test
    fun `just after the 2024 January 11 new moon we enter Shukla Pratipada`() {
        // 30 minutes after the new moon, moon is ~0.25° ahead of sun → tithi 1.
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 1, 11, 12, 30))
        assertEquals("Expected Shukla Pratipada (tithi 1)", 1, snapshot.tithi.index)
        assertEquals(Paksha.Shukla, snapshot.tithi.paksha)
    }

    @Test
    fun `just before the 2024 January 25 full moon we are still in Purnima`() {
        // Published full moon: 2024-01-25 17:54 UT. 1 hour before → tithi 15 (Purnima).
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 1, 25, 16, 54))
        assertEquals("Expected Purnima (tithi 15)", 15, snapshot.tithi.index)
        assertEquals(Paksha.Shukla, snapshot.tithi.paksha)
    }

    @Test
    fun `just after the 2024 January 25 full moon we enter Krishna Pratipada`() {
        // 1 hour after full moon → tithi 16 (Krishna Pratipada).
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 1, 25, 18, 54))
        assertEquals("Expected Krishna Pratipada (tithi 16)", 16, snapshot.tithi.index)
        assertEquals(Paksha.Krishna, snapshot.tithi.paksha)
    }

    @Test
    fun `just before the 2024 June 6 new moon we are in Amavasya`() {
        // Published new moon: 2024-06-06 12:37 UT. Sample 1 hour before.
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 6, 6, 11, 37))
        assertEquals("Expected Amavasya (tithi 30)", 30, snapshot.tithi.index)
    }

    // ------------------------------------------------------------------
    // Sidereal consistency — sun in correct Rashi for given Gregorian date.
    // ------------------------------------------------------------------

    @Test
    fun `sun is in sidereal Dhanu on 2024 January 1`() {
        // Sidereal (Lahiri) zodiac: Dhanu = Sagittarius = 240°-270°.
        // On Jan 1, tropical sun is at ~280° and ayanamsha is ~24°, so sidereal ~256°.
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 1, 1, 0, 0))
        val siderealRashi = (snapshot.sunSiderealDegrees / 30.0).toInt() + 1
        assertEquals("Expected Rashi 9 (Dhanu/Sagittarius)", 9, siderealRashi)
    }

    @Test
    fun `sun is in sidereal Mesha around mid April 2024`() {
        // Mesha (Aries) = sidereal 0°-30°. Sun enters Mesha around April 13-14
        // (Mesha Sankranti, the solar new year in Tamil/Punjabi/Bengali calendars).
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 4, 20, 0, 0))
        val siderealRashi = (snapshot.sunSiderealDegrees / 30.0).toInt() + 1
        assertEquals("Expected Rashi 1 (Mesha/Aries)", 1, siderealRashi)
    }

    // ------------------------------------------------------------------
    // Sunrise calculation — sanity checks.
    // ------------------------------------------------------------------

    @Test
    fun `sunrise in New Delhi on 2024 June 21 is around 05-23 IST`() {
        // Published sunrise for New Delhi on 2024-06-21: ~05:23 IST = 23:53 UT (prev day).
        val sunriseUtc = calculator.sunriseUtc(
            date = LocalDate.of(2024, 6, 21),
            latitudeDeg = 28.6139,
            longitudeDeg = 77.2090,
            zone = ZoneId.of("Asia/Kolkata")
        )
        assertNotNull("Sunrise should exist for New Delhi", sunriseUtc)

        val ist = java.time.Instant.ofEpochMilli(sunriseUtc!!).atZone(ZoneId.of("Asia/Kolkata"))
        val hour = ist.hour
        val minute = ist.minute
        assertTrue(
            "Expected ~05:23 IST, got ${hour}:${minute.toString().padStart(2, '0')}",
            (hour == 5 && minute in 15..35) || (hour == 4 && minute >= 55) || (hour == 6 && minute <= 5)
        )
    }

    @Test
    fun `sunrise in New Delhi on 2024 December 21 is around 07-10 IST`() {
        // Published sunrise for New Delhi on 2024-12-21: ~07:10 IST (winter solstice, latest).
        val sunriseUtc = calculator.sunriseUtc(
            date = LocalDate.of(2024, 12, 21),
            latitudeDeg = 28.6139,
            longitudeDeg = 77.2090,
            zone = ZoneId.of("Asia/Kolkata")
        )
        assertNotNull("Sunrise should exist for New Delhi", sunriseUtc)

        val ist = java.time.Instant.ofEpochMilli(sunriseUtc!!).atZone(ZoneId.of("Asia/Kolkata"))
        assertTrue(
            "Expected ~07:10 IST in December, got ${ist.hour}:${ist.minute}",
            ist.hour in 6..7
        )
    }

    @Test
    fun `arunodaya is exactly 96 minutes before sunrise`() {
        val date = LocalDate.of(2024, 4, 15)
        val sunriseUtc = calculator.sunriseUtc(
            date, latitudeDeg = 26.8467, longitudeDeg = 80.9462, // Lucknow
            zone = ZoneId.of("Asia/Kolkata")
        )!!
        val arunodayaUtc = calculator.arunodayaUtc(
            date, latitudeDeg = 26.8467, longitudeDeg = 80.9462,
            zone = ZoneId.of("Asia/Kolkata")
        )!!
        assertEquals(
            "Arunodaya should be exactly 96 minutes before sunrise",
            96L * 60L * 1000L, sunriseUtc - arunodayaUtc
        )
    }

    // ------------------------------------------------------------------
    // Snapshot composition.
    // ------------------------------------------------------------------

    @Test
    fun `snapshot moonMinusSunDegrees is in the range 0 to 360`() {
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 3, 15, 12, 0))
        assertTrue(
            "Expected moon-sun diff in [0, 360), got ${snapshot.moonMinusSunDegrees}",
            snapshot.moonMinusSunDegrees in 0.0..<360.0
        )
    }

    @Test
    fun `snapshot tithi is consistent with moonMinusSunDegrees`() {
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 3, 15, 12, 0))
        val expectedIndex = (snapshot.moonMinusSunDegrees / 12.0).toInt() + 1
        assertEquals(expectedIndex, snapshot.tithi.index)
    }

    @Test
    fun `snapshot tithi paksha matches index`() {
        val snapshot = calculator.computeAtInstant(ayanamshaType = AyanamshaType.LAHIRI, epochMillisUtc = utcMillis(2024, 3, 15, 12, 0))
        val expectedPaksha = if (snapshot.tithi.index <= 15) Paksha.Shukla else Paksha.Krishna
        assertEquals(expectedPaksha, snapshot.tithi.paksha)
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
}

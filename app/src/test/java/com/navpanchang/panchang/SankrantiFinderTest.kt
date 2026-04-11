package com.navpanchang.panchang

import com.navpanchang.ephemeris.MeeusEphemerisEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Validates [SankrantiFinder] against known Solar Sankranti dates.
 *
 * The Hindu solar calendar pins the Sun's entry into each Rashi to predictable Gregorian
 * dates that shift only slowly with precession. Makara Sankranti (Sun into Capricorn) is
 * always Jan 14–15; Mesha Sankranti (Aries) is always April 13–14; etc. We verify that
 * the finder returns ~12 Sankrantis per year with the right Rashi sequence and each date
 * within the correct ±2 day window.
 */
class SankrantiFinderTest {

    private lateinit var finder: SankrantiFinder

    @Before
    fun setUp() {
        finder = SankrantiFinder(MeeusEphemerisEngine())
    }

    @Test
    fun `a full 2024 calendar year contains 12 sankrantis`() {
        val start = utcMillis(2024, 1, 1, 0, 0)
        val end = utcMillis(2025, 1, 1, 0, 0)
        val sankrantis = finder.findSankrantisInWindow(start, end)

        assertTrue(
            "Expected ~12 Sankrantis in 2024, got ${sankrantis.size}",
            sankrantis.size in 11..13 // Allow off-by-one at window edges
        )
    }

    @Test
    fun `sankrantis in 2024 cycle through all 12 rashis in order`() {
        val start = utcMillis(2024, 1, 1, 0, 0)
        val end = utcMillis(2025, 1, 1, 0, 0)
        val sankrantis = finder.findSankrantisInWindow(start, end)

        // Consecutive sankrantis must differ by exactly 1 (mod 12) in rashi index.
        for (i in 1 until sankrantis.size) {
            val prev = sankrantis[i - 1].rashiIndex
            val curr = sankrantis[i].rashiIndex
            val diff = ((curr - prev - 1 + 12) % 12) + 1
            assertTrue(
                "Sankrantis must step through rashis: prev=$prev curr=$curr",
                diff == 1
            )
        }
    }

    @Test
    fun `makara sankranti in 2024 falls on Jan 14 or 15`() {
        val start = utcMillis(2024, 1, 1, 0, 0)
        val end = utcMillis(2024, 1, 31, 0, 0)
        val sankrantis = finder.findSankrantisInWindow(start, end)

        // Makara = Capricorn = rashi index 10.
        val makara = sankrantis.firstOrNull { it.rashiIndex == 10 }
        assertTrue("Expected a Makara Sankranti in January 2024", makara != null)

        // Convert to IST date. Makara Sankranti 2024 was on Jan 15 IST (00:42 AM IST).
        val ist = java.time.Instant.ofEpochMilli(makara!!.epochMillisUtc)
            .atZone(java.time.ZoneId.of("Asia/Kolkata"))
        assertTrue(
            "Makara Sankranti 2024 should fall Jan 14–15 IST, got ${ist.toLocalDate()}",
            ist.dayOfMonth in 14..15
        )
    }

    @Test
    fun `mesha sankranti in 2024 falls on April 13 or 14`() {
        val start = utcMillis(2024, 4, 1, 0, 0)
        val end = utcMillis(2024, 4, 30, 0, 0)
        val sankrantis = finder.findSankrantisInWindow(start, end)

        val mesha = sankrantis.firstOrNull { it.rashiIndex == 1 }
        assertTrue("Expected a Mesha Sankranti in April 2024", mesha != null)

        val ist = java.time.Instant.ofEpochMilli(mesha!!.epochMillisUtc)
            .atZone(java.time.ZoneId.of("Asia/Kolkata"))
        assertTrue(
            "Mesha Sankranti 2024 should fall April 13–14 IST, got ${ist.toLocalDate()}",
            ist.dayOfMonth in 13..14
        )
    }

    @Test
    fun `rashiAt returns consistent rashi across one day`() {
        // Sun moves ~1°/day so it can cross at most one rashi boundary per day.
        // Within a 12-hour window mid-month (well away from boundaries), the rashi is stable.
        val rashiMidMonth = finder.rashiAt(utcMillis(2024, 5, 1, 0, 0))
        val rashi12hLater = finder.rashiAt(utcMillis(2024, 5, 1, 12, 0))
        assertEquals(
            "Rashi should be stable across 12h mid-month",
            rashiMidMonth, rashi12hLater
        )
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
}

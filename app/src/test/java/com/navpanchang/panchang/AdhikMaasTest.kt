package com.navpanchang.panchang

import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.ephemeris.MeeusEphemerisEngine
import com.navpanchang.ephemeris.SunriseCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Validates [AdhikMaasDetector] against known Adhik Maas years and verifies the
 * Sankranti → lunar month naming bijection.
 *
 * **Known Adhik Maas years (last ~10 years)** per drikpanchang.com / mypanchang.com:
 *  * 2015 — Adhik Ashadha
 *  * 2018 — Adhik Jyeshtha
 *  * 2020 — Adhik Ashwin
 *  * 2023 — **Adhik Shravana** (July 18 – Aug 16, 2023) — the test case we pin.
 *  * 2026 — Adhik Jyeshtha
 *
 * For a run across 2023, the detector must classify exactly ONE lunar month in the
 * range as Adhik.
 */
class AdhikMaasTest {

    private lateinit var detector: AdhikMaasDetector

    @Before
    fun setUp() {
        val engine = MeeusEphemerisEngine()
        val calculator = PanchangCalculator(engine, SunriseCalculator(engine))
        val tithiEndFinder = TithiEndFinder(engine)
        val amavasyaFinder = AmavasyaFinder(tithiEndFinder, calculator)
        val sankrantiFinder = SankrantiFinder(engine)
        detector = AdhikMaasDetector(amavasyaFinder, sankrantiFinder)
    }

    @Test
    fun `2023 lunar year contains exactly one Adhik month`() {
        // Classify from mid-April 2023 to mid-April 2024 — a full Hindu lunar year.
        val start = utcMillis(2023, 4, 1, 0, 0)
        val end = utcMillis(2024, 5, 1, 0, 0)
        val months = detector.classifyLunarMonthsInWindow(start, end, AyanamshaType.LAHIRI)

        assertTrue(
            "Expected 12–14 lunar months in the window, got ${months.size}",
            months.size in 12..14
        )

        val adhikCount = months.count { it.type == LunarMonthType.Adhik }
        assertEquals(
            "Expected exactly 1 Adhik month in 2023 lunar year, got $adhikCount:\n" +
                months.joinToString("\n") {
                    "  ${it.month.nameEn} ${it.type} (sankrantis=${it.sankrantis.map { s -> s.rashi }})"
                },
            1, adhikCount
        )
    }

    @Test
    fun `2023 Adhik month is Shravana`() {
        val start = utcMillis(2023, 6, 1, 0, 0)
        val end = utcMillis(2023, 10, 1, 0, 0)
        val months = detector.classifyLunarMonthsInWindow(start, end, AyanamshaType.LAHIRI)

        val adhik = months.firstOrNull { it.type == LunarMonthType.Adhik }
        assertTrue("Expected an Adhik month in mid-2023", adhik != null)
        assertEquals(
            "2023 Adhik month should be Shravana, got ${adhik!!.month}",
            LunarMonth.Shravana, adhik.month
        )
    }

    @Test
    fun `2024 lunar year contains no Adhik months`() {
        // 2024 is a normal year — all 12 months should be Nija.
        val start = utcMillis(2024, 4, 1, 0, 0)
        val end = utcMillis(2025, 4, 1, 0, 0)
        val months = detector.classifyLunarMonthsInWindow(start, end, AyanamshaType.LAHIRI)

        val adhikCount = months.count { it.type == LunarMonthType.Adhik }
        assertEquals(
            "Expected 0 Adhik months in 2024 lunar year, got $adhikCount",
            0, adhikCount
        )
    }

    @Test
    fun `lunarMonthForSankranti maps each Rashi to its named month`() {
        // Verify the full bijection — any regression here would silently break the
        // festival catalog (e.g. Mahashivratri would fire in the wrong month).
        assertEquals(LunarMonth.Chaitra, detector.lunarMonthForSankranti(Rashi.Mesha))
        assertEquals(LunarMonth.Vaisakha, detector.lunarMonthForSankranti(Rashi.Vrishabha))
        assertEquals(LunarMonth.Jyeshtha, detector.lunarMonthForSankranti(Rashi.Mithuna))
        assertEquals(LunarMonth.Ashadha, detector.lunarMonthForSankranti(Rashi.Karka))
        assertEquals(LunarMonth.Shravana, detector.lunarMonthForSankranti(Rashi.Simha))
        assertEquals(LunarMonth.Bhadrapada, detector.lunarMonthForSankranti(Rashi.Kanya))
        assertEquals(LunarMonth.Ashwin, detector.lunarMonthForSankranti(Rashi.Tula))
        assertEquals(LunarMonth.Kartika, detector.lunarMonthForSankranti(Rashi.Vrishchika))
        assertEquals(LunarMonth.Margashirsha, detector.lunarMonthForSankranti(Rashi.Dhanu))
        assertEquals(LunarMonth.Pausha, detector.lunarMonthForSankranti(Rashi.Makara))
        assertEquals(LunarMonth.Magha, detector.lunarMonthForSankranti(Rashi.Kumbha))
        assertEquals(LunarMonth.Phalguna, detector.lunarMonthForSankranti(Rashi.Meena))
    }

    @Test
    fun `classified lunar months are contiguous with no gaps`() {
        val start = utcMillis(2024, 1, 1, 0, 0)
        val end = utcMillis(2024, 12, 31, 0, 0)
        val months = detector.classifyLunarMonthsInWindow(start, end, AyanamshaType.LAHIRI)

        for (i in 1 until months.size) {
            val prev = months[i - 1]
            val curr = months[i]
            assertEquals(
                "Lunar months must be contiguous: prev ends ${prev.endEpochMillisUtc}, curr starts ${curr.startEpochMillisUtc}",
                prev.endEpochMillisUtc, curr.startEpochMillisUtc
            )
        }
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
}

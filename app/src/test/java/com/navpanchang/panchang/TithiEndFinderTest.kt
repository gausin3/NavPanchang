package com.navpanchang.panchang

import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.ephemeris.MeeusEphemerisEngine
import com.navpanchang.ephemeris.SunriseCalculator
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Exercises [TithiEndFinder]'s ability to locate tithi boundaries.
 *
 * We validate against **observational anchors** — moments we know must be tithi
 * boundaries from published lunar phase tables (new moon, full moon). The end-finder
 * is correct if the boundary it reports lands within a minute or two of the published
 * phase instant.
 */
class TithiEndFinderTest {

    private lateinit var calculator: PanchangCalculator
    private lateinit var finder: TithiEndFinder

    @Before
    fun setUp() {
        val engine = MeeusEphemerisEngine()
        calculator = PanchangCalculator(engine, SunriseCalculator(engine))
        finder = TithiEndFinder(engine)
    }

    // ------------------------------------------------------------------
    // End of Amavasya = moment of new moon.
    // ------------------------------------------------------------------

    @Test
    fun `end of tithi 30 coincides with the 2024 January 11 new moon`() {
        // Published new moon: 2024-01-11 11:57 UT. Starting at 2024-01-10 00:00 UT we're
        // inside Krishna Chaturdashi or Amavasya — step forward until we see a boundary.
        val start = utcMillis(2024, 1, 10, 0, 0)
        val publishedNewMoon = utcMillis(2024, 1, 11, 11, 57)

        // Walk forward through each tithi end until we find the transition from 30 → 1.
        var cursor = start
        var crossed = -1L
        for (i in 0..10) {
            val end = finder.findNextTithiEnd(cursor) ?: break
            val tithiAfter = calculator.computeAtInstant(end + 1000, AyanamshaType.LAHIRI).tithi.index
            if (calculator.computeAtInstant(end - 1000, AyanamshaType.LAHIRI).tithi.index == 30 && tithiAfter == 1) {
                crossed = end
                break
            }
            cursor = end + 1000
        }

        assertTrue("Expected to find a 30→1 transition near the new moon", crossed > 0)
        val diffMinutes = kotlin.math.abs(crossed - publishedNewMoon) / 60_000
        assertTrue(
            "Amavasya end $crossed should be within 10 min of new moon $publishedNewMoon (diff=$diffMinutes min)",
            diffMinutes < 10
        )
    }

    // ------------------------------------------------------------------
    // End of Purnima = moment of full moon.
    // ------------------------------------------------------------------

    @Test
    fun `end of tithi 15 coincides with the 2024 January 25 full moon`() {
        val start = utcMillis(2024, 1, 24, 0, 0)
        val publishedFullMoon = utcMillis(2024, 1, 25, 17, 54)

        var cursor = start
        var crossed = -1L
        for (i in 0..10) {
            val end = finder.findNextTithiEnd(cursor) ?: break
            val before = calculator.computeAtInstant(end - 1000, AyanamshaType.LAHIRI).tithi.index
            val after = calculator.computeAtInstant(end + 1000, AyanamshaType.LAHIRI).tithi.index
            if (before == 15 && after == 16) {
                crossed = end
                break
            }
            cursor = end + 1000
        }

        assertTrue("Expected to find a 15→16 transition near the full moon", crossed > 0)
        val diffMinutes = kotlin.math.abs(crossed - publishedFullMoon) / 60_000
        assertTrue(
            "Purnima end $crossed should be within 10 min of full moon $publishedFullMoon (diff=$diffMinutes min)",
            diffMinutes < 10
        )
    }

    // ------------------------------------------------------------------
    // Monotonicity and completeness.
    // ------------------------------------------------------------------

    @Test
    fun `findNextTithiEnd strictly increases on repeated walks`() {
        var cursor = utcMillis(2024, 3, 1, 0, 0)
        var prev = cursor
        for (i in 0..5) {
            val next = finder.findNextTithiEnd(cursor) ?: break
            assertTrue("Tithi end should be strictly after the start", next > prev)
            prev = next
            cursor = next + 1000
        }
    }

    @Test
    fun `findAllTithiEndsInWindow returns boundaries in chronological order`() {
        // A 7-day window should contain ~7 tithi boundaries.
        val start = utcMillis(2024, 3, 1, 0, 0)
        val end = utcMillis(2024, 3, 8, 0, 0)
        val boundaries = finder.findAllTithiEndsInWindow(start, end)

        assertTrue("Expected at least 6 tithi boundaries in 7 days, got ${boundaries.size}", boundaries.size >= 6)
        // Check ordering.
        for (i in 1 until boundaries.size) {
            assertTrue("Boundaries must be monotonic", boundaries[i] > boundaries[i - 1])
        }
    }

    @Test
    fun `refined boundary is accurate to within one second`() {
        // The bisection converges to <= 1 second — verify by checking the tithi index
        // changes across exactly that boundary.
        val start = utcMillis(2024, 3, 1, 0, 0)
        val boundary = finder.findNextTithiEnd(start) ?: fail("No boundary found")

        val before = calculator.computeAtInstant(boundary - 2000, AyanamshaType.LAHIRI).tithi.index
        val after = calculator.computeAtInstant(boundary + 2000, AyanamshaType.LAHIRI).tithi.index
        assertTrue(
            "Tithi index must change within ±2s of reported boundary: before=$before after=$after",
            before != after
        )
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun fail(message: String): Nothing = throw AssertionError(message)
}

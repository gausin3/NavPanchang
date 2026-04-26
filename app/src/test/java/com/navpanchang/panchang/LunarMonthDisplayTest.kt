package com.navpanchang.panchang

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for [displayLunarMonth] — the engine-Amanta → user-convention
 * translation rule. The engine always produces canonical Amanta labels; this function
 * is the only place that knows about Purnimanta naming.
 *
 * See TECH_DESIGN.md §Amanta vs Purnimanta.
 */
class LunarMonthDisplayTest {

    @Test
    fun `Amanta convention never shifts — Shukla paksha`() {
        for (month in LunarMonth.entries) {
            assertEquals(
                "Amanta + Shukla should be identity for $month",
                month,
                displayLunarMonth(month, Paksha.Shukla, LunarConvention.AMANTA)
            )
        }
    }

    @Test
    fun `Amanta convention never shifts — Krishna paksha`() {
        for (month in LunarMonth.entries) {
            assertEquals(
                "Amanta + Krishna should be identity for $month",
                month,
                displayLunarMonth(month, Paksha.Krishna, LunarConvention.AMANTA)
            )
        }
    }

    @Test
    fun `Purnimanta convention does NOT shift Shukla paksha`() {
        for (month in LunarMonth.entries) {
            assertEquals(
                "Purnimanta + Shukla should be identity for $month (only Krishna shifts)",
                month,
                displayLunarMonth(month, Paksha.Shukla, LunarConvention.PURNIMANTA)
            )
        }
    }

    @Test
    fun `Purnimanta convention shifts Krishna paksha to next month`() {
        // The full +1 cycle: Chaitra → Vaisakha → ... → Phalguna → Chaitra (wrap-around).
        val expected = mapOf(
            LunarMonth.Chaitra to LunarMonth.Vaisakha,
            LunarMonth.Vaisakha to LunarMonth.Jyeshtha,
            LunarMonth.Jyeshtha to LunarMonth.Ashadha,
            LunarMonth.Ashadha to LunarMonth.Shravana,
            LunarMonth.Shravana to LunarMonth.Bhadrapada,
            LunarMonth.Bhadrapada to LunarMonth.Ashwin,
            LunarMonth.Ashwin to LunarMonth.Kartika,
            LunarMonth.Kartika to LunarMonth.Margashirsha,
            LunarMonth.Margashirsha to LunarMonth.Pausha,
            LunarMonth.Pausha to LunarMonth.Magha,
            LunarMonth.Magha to LunarMonth.Phalguna,
            // Wrap-around: Phalguna is the last month, so its Krishna paksha belongs to
            // Chaitra under Purnimanta.
            LunarMonth.Phalguna to LunarMonth.Chaitra
        )

        for ((engineMonth, displayMonth) in expected) {
            assertEquals(
                "Purnimanta + Krishna should shift $engineMonth → $displayMonth",
                displayMonth,
                displayLunarMonth(engineMonth, Paksha.Krishna, LunarConvention.PURNIMANTA)
            )
        }
    }

    @Test
    fun `Mahashivratri canonical case — engine Magha Krishna 14 displays as Phalguna for Purnimanta`() {
        // The exact case that motivated the convention plumbing: Mahashivratri 2024
        // (March 8) is in the Amavasya window containing Kumbha Sankranti, so the engine
        // labels it Magha. North Indian (Purnimanta) users should see it as Phalguna.
        // Both labels refer to the same actual day.
        assertEquals(
            LunarMonth.Phalguna,
            displayLunarMonth(LunarMonth.Magha, Paksha.Krishna, LunarConvention.PURNIMANTA)
        )
        assertEquals(
            "Same day to an Amanta user is still Magha",
            LunarMonth.Magha,
            displayLunarMonth(LunarMonth.Magha, Paksha.Krishna, LunarConvention.AMANTA)
        )
    }
}

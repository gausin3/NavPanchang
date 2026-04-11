package com.navpanchang.panchang

/**
 * A rule describing *when* an event (vrat, puja, festival) occurs.
 *
 * Deliberately a small sealed hierarchy so we can exhaustively `when`-match on it in
 * [OccurrenceComputer]. New rule types are added by (a) adding a new subclass here,
 * (b) teaching `EventRuleParser` to read its JSON representation, and
 * (c) handling it in `OccurrenceComputer.matchesDay`.
 *
 * All tithi indices use the 1..30 absolute scheme from [Tithi] (1..15 Shukla, 16..30 Krishna).
 *
 * See TECH_DESIGN.md §Panchang calculation.
 */
sealed class EventRule {

    /**
     * The event occurs on the Gregorian date whose sunrise is inside tithi [tithiIndex].
     *
     * @param vratLogic if `true`, apply the **Dashami-Viddha** shift for Ekadashi-class
     *   vrats — see [VratLogic.applyDashamiViddha]. Only meaningful for tithis 11 and 26.
     */
    data class TithiAtSunrise(
        val tithiIndex: Int,
        val vratLogic: Boolean
    ) : EventRule() {
        init {
            require(tithiIndex in 1..30) { "tithiIndex must be 1..30, got $tithiIndex" }
        }
    }

    /**
     * The event is observed in the evening of the Gregorian date where [tithiIndex]
     * (typically Trayodashi, tithi 13 or 28 for Pradosh) is present at sunset. The
     * alarm-scheduling anchor is local sunset rather than sunrise.
     */
    data class EveningTithi(
        val tithiIndex: Int
    ) : EventRule() {
        init {
            require(tithiIndex in 1..30) { "tithiIndex must be 1..30, got $tithiIndex" }
        }
    }

    /**
     * The event is a specific tithi in a specific **named** lunar month (e.g. Mahashivratri =
     * Krishna Chaturdashi in Phalguna). Used for annual festivals whose recurrence is pinned
     * to the Hindu month, not to every month.
     *
     * If [suppressInAdhik] is `true` and the containing lunar month is classified as
     * [LunarMonthType.Adhik], the occurrence is suppressed entirely — no alarm fires.
     * This matches the general rule that annual festivals are observed in the *Nija*
     * month, not the Adhik one.
     */
    data class TithiInLunarMonth(
        val tithiIndex: Int,
        val lunarMonth: LunarMonth,
        val suppressInAdhik: Boolean = true
    ) : EventRule() {
        init {
            require(tithiIndex in 1..30) { "tithiIndex must be 1..30, got $tithiIndex" }
        }
    }
}

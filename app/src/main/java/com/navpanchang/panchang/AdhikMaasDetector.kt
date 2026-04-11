package com.navpanchang.panchang

import com.navpanchang.ephemeris.AyanamshaType
import javax.inject.Inject

/**
 * Classifies lunar months as Nija / Adhik / Kshaya and assigns each a [LunarMonth]
 * name, by counting Solar Sankrantis inside each Amavasya-to-Amavasya window.
 *
 * **Rules** (from the plan §Adhik Maas handling):
 *  * **0 Sankrantis → Adhik** (extra month — intercalary). The month repeats the name of
 *    the *next* Nija month, because the next Sankranti is what gives the real month its
 *    name.
 *  * **1 Sankranti → Nija** (normal). The month is named for the Rashi the Sun enters.
 *  * **2 Sankrantis → Kshaya** (skipped — extremely rare, last occurred in 1963, next
 *    around 2124). The two Sankranti months collapse into a single compound name.
 *
 * **Month-naming convention** (Amanta / most-of-India):
 *
 *  | Sankranti to | Lunar month name |
 *  |---|---|
 *  | Mesha (Aries) | Chaitra |
 *  | Vrishabha (Taurus) | Vaisakha |
 *  | Mithuna (Gemini) | Jyeshtha |
 *  | Karka (Cancer) | Ashadha |
 *  | Simha (Leo) | Shravana |
 *  | Kanya (Virgo) | Bhadrapada |
 *  | Tula (Libra) | Ashwin |
 *  | Vrishchika (Scorpio) | Kartika |
 *  | Dhanu (Sagittarius) | Margashirsha |
 *  | Makara (Capricorn) | Pausha |
 *  | Kumbha (Aquarius) | Magha |
 *  | Meena (Pisces) | Phalguna |
 *
 * So a lunar month containing Simha Sankranti (Sun entering Leo ~ mid-August) is named
 * Shravana. Simple, bijective mapping.
 */
class AdhikMaasDetector @Inject constructor(
    private val amavasyaFinder: AmavasyaFinder,
    private val sankrantiFinder: SankrantiFinder
) {

    /**
     * A single classified lunar month with its time bounds.
     */
    data class LunarMonthWindow(
        val month: LunarMonth,
        val type: LunarMonthType,
        /** Start of the lunar month: epoch-millis UTC of the preceding Amavasya. */
        val startEpochMillisUtc: Long,
        /** End of the lunar month: epoch-millis UTC of the *next* Amavasya (exclusive). */
        val endEpochMillisUtc: Long,
        /** The Sankrantis that fell inside this window (0 for Adhik, 1 for Nija, 2 for Kshaya). */
        val sankrantis: List<SankrantiFinder.Sankranti>
    )

    /**
     * Enumerate classified lunar months whose *start* (preceding Amavasya) falls in
     * `[startEpochMillisUtc, endEpochMillisUtc)`. We need at least one Amavasya before
     * the window and one after so the first/last months can be fully bounded; callers
     * should pass a window extended by ~45 days on each side if they care about edge
     * months.
     */
    fun classifyLunarMonthsInWindow(
        startEpochMillisUtc: Long,
        endEpochMillisUtc: Long,
        ayanamshaType: AyanamshaType
    ): List<LunarMonthWindow> {
        val amavasyas = amavasyaFinder.findAmavasyasInWindow(
            startEpochMillisUtc, endEpochMillisUtc, ayanamshaType
        )
        if (amavasyas.size < 2) return emptyList()

        // All Sankrantis in the same window, sorted by time.
        val sankrantis = sankrantiFinder.findSankrantisInWindow(
            amavasyas.first(), amavasyas.last(), ayanamshaType
        )

        val results = mutableListOf<LunarMonthWindow>()
        // We'll iterate through the Amavasya list pairwise. Track whether we just saw
        // an Adhik month so the *next* Nija gets a promoted-name or not — Adhik takes
        // the name of the following Nija month.
        val pendingAdhik = mutableListOf<Pair<Long, Long>>() // startAma, endAma for 0-sankranti months

        for (i in 0 until amavasyas.lastIndex) {
            val amaStart = amavasyas[i]
            val amaEnd = amavasyas[i + 1]
            val inWindow = sankrantis.filter { it.epochMillisUtc in amaStart..<amaEnd }

            when (inWindow.size) {
                0 -> {
                    // Adhik: hold until we find the next Nija to borrow its name.
                    pendingAdhik.add(amaStart to amaEnd)
                }

                1 -> {
                    val s = inWindow.first()
                    val nijaMonth = lunarMonthForSankranti(s.rashi)
                    // Any pending Adhik months become "Adhik <thisName>".
                    pendingAdhik.forEach { (start, end) ->
                        results.add(
                            LunarMonthWindow(
                                month = nijaMonth,
                                type = LunarMonthType.Adhik,
                                startEpochMillisUtc = start,
                                endEpochMillisUtc = end,
                                sankrantis = emptyList()
                            )
                        )
                    }
                    pendingAdhik.clear()
                    results.add(
                        LunarMonthWindow(
                            month = nijaMonth,
                            type = LunarMonthType.Nija,
                            startEpochMillisUtc = amaStart,
                            endEpochMillisUtc = amaEnd,
                            sankrantis = inWindow
                        )
                    )
                }

                2 -> {
                    // Kshaya: two Sankrantis in one lunar month — the next lunar month
                    // has zero, so effectively one lunar month is "compressed" and takes
                    // a compound name. We emit a single record with the *later* Sankranti's
                    // month name per traditional Kshaya practice.
                    pendingAdhik.forEach { (start, end) ->
                        // Unusual: Adhik immediately before a Kshaya is extremely rare.
                        // For safety, attach the pending Adhik to the later month name.
                        results.add(
                            LunarMonthWindow(
                                month = lunarMonthForSankranti(inWindow.last().rashi),
                                type = LunarMonthType.Adhik,
                                startEpochMillisUtc = start,
                                endEpochMillisUtc = end,
                                sankrantis = emptyList()
                            )
                        )
                    }
                    pendingAdhik.clear()
                    results.add(
                        LunarMonthWindow(
                            month = lunarMonthForSankranti(inWindow.last().rashi),
                            type = LunarMonthType.Kshaya,
                            startEpochMillisUtc = amaStart,
                            endEpochMillisUtc = amaEnd,
                            sankrantis = inWindow
                        )
                    )
                }

                else -> error("A lunar month cannot contain ${inWindow.size} Sankrantis")
            }
        }

        return results
    }

    /**
     * The lunar month a given Sankranti belongs to — this is the month-naming rule.
     * Public so tests can verify the mapping.
     */
    fun lunarMonthForSankranti(rashi: Rashi): LunarMonth = when (rashi) {
        Rashi.Mesha -> LunarMonth.Chaitra
        Rashi.Vrishabha -> LunarMonth.Vaisakha
        Rashi.Mithuna -> LunarMonth.Jyeshtha
        Rashi.Karka -> LunarMonth.Ashadha
        Rashi.Simha -> LunarMonth.Shravana
        Rashi.Kanya -> LunarMonth.Bhadrapada
        Rashi.Tula -> LunarMonth.Ashwin
        Rashi.Vrishchika -> LunarMonth.Kartika
        Rashi.Dhanu -> LunarMonth.Margashirsha
        Rashi.Makara -> LunarMonth.Pausha
        Rashi.Kumbha -> LunarMonth.Magha
        Rashi.Meena -> LunarMonth.Phalguna
    }
}

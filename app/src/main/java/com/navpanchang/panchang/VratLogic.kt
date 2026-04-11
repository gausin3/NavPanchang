package com.navpanchang.panchang

import com.navpanchang.ephemeris.AyanamshaType
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The religious-correctness layer that sits between [PanchangCalculator] and
 * [OccurrenceComputer] for **vrat** (fasting) events. Plain "tithi at sunrise" is
 * insufficient for Ekadashi-class vrats — Hindu smriti texts prescribe corrections for
 * edge cases that this class encodes.
 *
 * Two correctness rules ship in v1:
 *
 * 1. **Dashami-Viddha** — if Dashami (tithi 10 or 25) is present at *any* moment during
 *    the 96 minutes before sunrise (Arunodaya) on the nominal Ekadashi day, the day is
 *    "tainted" (viddha) and the vrat is shifted to the next day (Dvadashi-yukta). See
 *    [applyDashamiViddha].
 *
 * 2. **Parana window** — the fast is broken the *next* morning within a specific window
 *    starting at sunrise on Dvadashi and ending at the stricter of (end of Dvadashi,
 *    first quarter of Dvadashi = Harivasara rule). See [computeParanaWindow].
 *
 * **Kshaya fallback** is handled upstream in [TithiEndFinder] / [OccurrenceComputer];
 * this class assumes the caller has already identified a candidate day and only needs
 * the correctness tweaks applied.
 *
 * See TECH_DESIGN.md §Vrat logic.
 */
class VratLogic @Inject constructor(
    private val panchangCalculator: PanchangCalculator,
    private val tithiEndFinder: TithiEndFinder
) {

    /**
     * Result of [applyDashamiViddha] — either the original date or a shifted date,
     * together with a diagnostic reason.
     */
    data class ViddhaResult(
        val observationDate: LocalDate,
        val shifted: Boolean,
        val reason: String
    )

    /**
     * Apply the Dashami-Viddha shift rule to an Ekadashi candidate date.
     *
     * **Input:** the Gregorian date where tithi 11 (Shukla Ekadashi) or 26 (Krishna
     * Ekadashi) is present at sunrise, at the given location.
     *
     * **Output:** the observation date, shifted to the following day if Dashami was
     * present at Arunodaya (`sunrise - 96 min`).
     */
    fun applyDashamiViddha(
        ekadashiTithiIndex: Int,
        candidateDate: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
        ayanamshaType: AyanamshaType
    ): ViddhaResult {
        require(ekadashiTithiIndex == 11 || ekadashiTithiIndex == 26) {
            "Dashami-Viddha only applies to Ekadashi (tithi 11 or 26), got $ekadashiTithiIndex"
        }
        val dashamiIndex = ekadashiTithiIndex - 1 // 10 for Shukla, 25 for Krishna

        val arunodayaUtc = panchangCalculator.arunodayaUtc(
            candidateDate, latitudeDeg, longitudeDeg, zone
        ) ?: return ViddhaResult(
            observationDate = candidateDate,
            shifted = false,
            reason = "No sunrise at this latitude/date — cannot apply Dashami-Viddha"
        )

        val tithiAtArunodaya = panchangCalculator.computeAtInstant(arunodayaUtc, ayanamshaType).tithi.index

        return if (tithiAtArunodaya == dashamiIndex) {
            ViddhaResult(
                observationDate = candidateDate.plusDays(1),
                shifted = true,
                reason = "Dashami-Viddha: Dashami was present at Arunodaya on $candidateDate"
            )
        } else {
            ViddhaResult(
                observationDate = candidateDate,
                shifted = false,
                reason = "No Dashami-Viddha taint detected"
            )
        }
    }

    /**
     * Parana (fast-breaking) window for an Ekadashi observed on [ekadashiObservationDate].
     * Returns `null` at polar latitudes where sunrise on Dvadashi doesn't exist.
     *
     * * `paranaStart` = sunrise on the *next* day (Dvadashi).
     * * `paranaEnd` = min of (end of Dvadashi tithi, start of Dvadashi + Dvadashi duration / 4)
     *   — the stricter "Harivasara quarter" rule. This keeps the window conservative so the
     *   user can never be told to break the fast after the traditional deadline.
     */
    data class ParanaWindow(
        val startUtc: Long,
        val endUtc: Long
    )

    fun computeParanaWindow(
        ekadashiObservationDate: LocalDate,
        ekadashiTithiIndex: Int,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
        ayanamshaType: AyanamshaType
    ): ParanaWindow? {
        require(ekadashiTithiIndex == 11 || ekadashiTithiIndex == 26) {
            "Parana window only applies to Ekadashi"
        }

        // Sunrise on Dvadashi day (= day after the vrat).
        val dvadashiDate = ekadashiObservationDate.plusDays(1)
        val sunriseUtc = panchangCalculator.sunriseUtc(
            dvadashiDate, latitudeDeg, longitudeDeg, zone
        ) ?: return null

        // Start of Dvadashi = end of Ekadashi (the tithi-11 → tithi-12 or 26 → 27 transition).
        // We search backward / forward from sunrise to find the boundaries bracketing the
        // Dvadashi tithi. Since we know Dvadashi is "the tithi currently in effect" at
        // sunrise on Dvadashi morning, we can:
        //   1. Find the preceding tithi-11→12 (or 26→27) transition (start of Dvadashi).
        //   2. Find the next tithi-12→13 (or 27→28) transition (end of Dvadashi).
        //
        // For simplicity we search forward from (sunrise - 25h) — Dvadashi started within
        // the last ~24 hours because Ekadashi lasts ~24h — and collect the two nearest
        // boundaries.

        val searchStart = sunriseUtc - SEARCH_BACKWARD_MILLIS
        val searchEnd = sunriseUtc + SEARCH_FORWARD_MILLIS
        val boundaries = tithiEndFinder.findAllTithiEndsInWindow(searchStart, searchEnd, ayanamshaType)

        val dvadashiTithi = if (ekadashiTithiIndex == 11) 12 else 27
        val trayodashiTithi = if (ekadashiTithiIndex == 11) 13 else 28

        // Dvadashi start = the Ekadashi→Dvadashi transition in the window.
        val dvadashiStart = boundaries.firstOrNull { boundary ->
            val before = panchangCalculator.computeAtInstant(boundary - 1000, ayanamshaType).tithi.index
            val after = panchangCalculator.computeAtInstant(boundary + 1000, ayanamshaType).tithi.index
            before == ekadashiTithiIndex && after == dvadashiTithi
        } ?: return null

        // Dvadashi end = the Dvadashi→Trayodashi transition after that.
        val dvadashiEnd = boundaries.firstOrNull { boundary ->
            if (boundary <= dvadashiStart) return@firstOrNull false
            val after = panchangCalculator.computeAtInstant(boundary + 1000, ayanamshaType).tithi.index
            after == trayodashiTithi
        } ?: return null

        val dvadashiDuration = dvadashiEnd - dvadashiStart
        val harivasaraEnd = dvadashiStart + dvadashiDuration / 4

        val paranaEnd = minOf(dvadashiEnd, harivasaraEnd)
        return ParanaWindow(startUtc = sunriseUtc, endUtc = paranaEnd)
    }

    private companion object {
        /** Search back 25 hours to guarantee we find the start of Dvadashi. */
        private const val SEARCH_BACKWARD_MILLIS = 25L * 60L * 60L * 1000L

        /** Search forward 30 hours to guarantee we find the end of Dvadashi. */
        private const val SEARCH_FORWARD_MILLIS = 30L * 60L * 60L * 1000L
    }
}

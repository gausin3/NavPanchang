package com.navpanchang.panchang

import javax.inject.Inject

/**
 * Finds **Amavasya** (new moon) instants — the boundaries of lunar months in the Amanta
 * (new-moon-ending) tradition used across most of India. This is the anchor the Adhik
 * Maas detector works against: a lunar month is the span from one Amavasya to the next,
 * and the number of Solar Sankrantis inside it determines its type (Nija/Adhik/Kshaya).
 *
 * Implementation delegates to [TithiEndFinder] — an Amavasya boundary is exactly a
 * tithi-30 → tithi-1 transition, so we reuse the existing chunk-then-refine logic.
 * We don't re-scan the same window here; this class is a thin adapter that filters
 * [TithiEndFinder.findAllTithiEndsInWindow] results.
 */
class AmavasyaFinder @Inject constructor(
    private val tithiEndFinder: TithiEndFinder,
    private val panchangCalculator: PanchangCalculator
) {

    /**
     * Enumerate Amavasya instants in `[startEpochMillisUtc, endEpochMillisUtc)`.
     * Each result is the epoch-millis UTC at which the 30→1 tithi transition occurs
     * (so the returned instant is the *end* of Amavasya = the moment of new moon).
     */
    fun findAmavasyasInWindow(
        startEpochMillisUtc: Long,
        endEpochMillisUtc: Long
    ): List<Long> {
        val allBoundaries = tithiEndFinder.findAllTithiEndsInWindow(
            startEpochMillisUtc, endEpochMillisUtc
        )
        // Filter to those where the tithi just *before* was 30 (i.e. the wrap from
        // Amavasya back to Shukla Pratipada).
        return allBoundaries.filter { boundary ->
            val tithiBefore = panchangCalculator
                .computeAtInstant(boundary - BEFORE_OFFSET_MILLIS).tithi.index
            tithiBefore == 30
        }
    }

    private companion object {
        /** Sample the tithi 10 seconds before the boundary to classify it. */
        private const val BEFORE_OFFSET_MILLIS = 10_000L
    }
}

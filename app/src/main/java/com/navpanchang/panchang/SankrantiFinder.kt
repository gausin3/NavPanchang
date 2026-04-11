package com.navpanchang.panchang

import com.navpanchang.ephemeris.AstroMath.normalizeDegrees
import com.navpanchang.ephemeris.Ayanamsha
import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.ephemeris.EphemerisEngine
import com.navpanchang.util.AstroTimeUtils
import javax.inject.Inject

/**
 * Finds **Solar Sankrantis** — moments when the Sun's *sidereal* longitude crosses a
 * multiple of 30° and enters a new Rashi. These are the anchors the Hindu solar calendar
 * is built on, and also the measurement used by [AdhikMaasDetector] to classify a lunar
 * month as Nija / Adhik / Kshaya.
 *
 * The 12 Sankrantis of the Hindu year are (in order of entry):
 *  1. Mesha (Aries ~ April 14)
 *  2. Vrishabha (Taurus ~ May 15)
 *  3. Mithuna (Gemini ~ June 15)
 *  4. Karka (Cancer ~ July 16)
 *  5. Simha (Leo ~ August 17)
 *  6. Kanya (Virgo ~ September 17)
 *  7. Tula (Libra ~ October 17)
 *  8. Vrishchika (Scorpio ~ November 16)
 *  9. Dhanu (Sagittarius ~ December 15)
 * 10. Makara (Capricorn ~ January 14)
 * 11. Kumbha (Aquarius ~ February 13)
 * 12. Meena (Pisces ~ March 14)
 *
 * **Algorithm:** same chunk-then-refine shape as [TithiEndFinder] but applied to the
 * Sun's sidereal longitude divided by 30. Chunk size is 6 hours — the Sun only moves
 * ~0.25° per 6 h, so we can't miss a boundary. Refinement bisects to 1-second precision.
 */
class SankrantiFinder @Inject constructor(
    private val engine: EphemerisEngine
) {

    /** A single Solar Sankranti. */
    data class Sankranti(
        /** 1..12, matching [Rashi.index]. 1 = Mesha. */
        val rashiIndex: Int,
        /** Epoch-millis UTC at which the Sun crosses `(rashiIndex - 1) * 30°` sidereal. */
        val epochMillisUtc: Long
    ) {
        val rashi: Rashi get() = Rashi.ofIndex(rashiIndex)
    }

    /**
     * Enumerate every Solar Sankranti in `[startEpochMillisUtc, endEpochMillisUtc)`.
     * Returned in chronological order. Most 12-month windows contain exactly 12.
     */
    fun findSankrantisInWindow(
        startEpochMillisUtc: Long,
        endEpochMillisUtc: Long,
        ayanamshaType: AyanamshaType = AyanamshaType.LAHIRI
    ): List<Sankranti> {
        require(endEpochMillisUtc > startEpochMillisUtc) {
            "end must be after start: $startEpochMillisUtc -> $endEpochMillisUtc"
        }

        val results = mutableListOf<Sankranti>()
        var prevMillis = startEpochMillisUtc
        var prevRashi = rashiAt(prevMillis, ayanamshaType)
        var probe = prevMillis + CHUNK_MILLIS

        while (probe < endEpochMillisUtc) {
            val probeRashi = rashiAt(probe, ayanamshaType)
            if (probeRashi != prevRashi) {
                val boundary = refineBoundary(prevMillis, probe, prevRashi, ayanamshaType)
                if (boundary != null) {
                    results.add(Sankranti(rashiIndex = probeRashi, epochMillisUtc = boundary))
                }
            }
            prevMillis = probe
            prevRashi = probeRashi
            probe += CHUNK_MILLIS
        }

        // Check the trailing partial window.
        if (prevMillis < endEpochMillisUtc) {
            val endRashi = rashiAt(endEpochMillisUtc, ayanamshaType)
            if (endRashi != prevRashi) {
                val boundary = refineBoundary(prevMillis, endEpochMillisUtc, prevRashi, ayanamshaType)
                if (boundary != null) {
                    results.add(Sankranti(rashiIndex = endRashi, epochMillisUtc = boundary))
                }
            }
        }

        return results
    }

    /** Sidereal Rashi index (1..12) for the Sun at the given instant. */
    fun rashiAt(
        epochMillisUtc: Long,
        ayanamshaType: AyanamshaType = AyanamshaType.LAHIRI
    ): Int {
        val jd = AstroTimeUtils.epochMillisToJulianDay(epochMillisUtc)
        val ayanamsha = Ayanamsha.compute(ayanamshaType, jd)
        val tropical = engine.sunApparentLongitudeDeg(jd)
        val sidereal = normalizeDegrees(tropical - ayanamsha)
        return (sidereal / 30.0).toInt() + 1
    }

    private fun refineBoundary(
        loMillis: Long,
        hiMillis: Long,
        loRashi: Int,
        ayanamshaType: AyanamshaType
    ): Long? {
        var lo = loMillis
        var hi = hiMillis
        var iterations = 0
        while (hi - lo > REFINEMENT_TOLERANCE_MILLIS && iterations < MAX_ITERATIONS) {
            val mid = lo + (hi - lo) / 2
            if (rashiAt(mid, ayanamshaType) == loRashi) lo = mid else hi = mid
            iterations++
        }
        return hi
    }

    private companion object {
        /** 6 hours — much finer than the Sun's daily motion of ~1°. */
        private const val CHUNK_MILLIS = 6L * 60L * 60L * 1000L
        private const val REFINEMENT_TOLERANCE_MILLIS = 1000L
        private const val MAX_ITERATIONS = 40
    }
}

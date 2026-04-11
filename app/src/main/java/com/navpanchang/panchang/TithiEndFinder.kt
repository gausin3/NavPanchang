package com.navpanchang.panchang

import com.navpanchang.ephemeris.AstroMath.normalizeDegrees
import com.navpanchang.ephemeris.Ayanamsha
import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.ephemeris.EphemerisEngine
import com.navpanchang.util.AstroTimeUtils
import javax.inject.Inject

/**
 * Finds the exact epoch-millis UTC of the next tithi boundary after a given instant.
 *
 * **Why this isn't just a binary search:** a naive binary search on moon–sun longitude
 * difference fails on **Kshaya tithis** — tithis that begin and end between two sunrises.
 * The quantity `(moonLon - sunLon) mod 12` is only locally monotonic; it wraps every 12°,
 * and a Kshaya tithi produces *two* wraps between consecutive sunrises, so bisection will
 * miss one of them.
 *
 * **Algorithm (chunk-then-refine):**
 * 1. Step forward from the start instant in 15-minute chunks.
 * 2. At each step, compute `delta = (moonLon - sunLon) mod 12`. When the next step's
 *    delta is *smaller* than the current (a wrap), a boundary lies inside that chunk.
 * 3. Refine the boundary inside the chunk with bisection on the raw moon–sun difference,
 *    iterating until the boundary time is accurate to ≤ 1 second.
 *
 * This correctly reports every tithi boundary in the window, including the two boundaries
 * produced by a Kshaya tithi.
 *
 * See TECH_DESIGN.md §TithiEndFinder.
 */
class TithiEndFinder @Inject constructor(
    private val engine: EphemerisEngine
) {

    /**
     * Find the end-time of the tithi currently in effect at [startEpochMillisUtc].
     *
     * @param startEpochMillisUtc the instant at which the "current" tithi is sampled.
     * @param maxSearchMillis a safety cap on how far forward to search (default 2 days —
     *   well beyond any real tithi duration of ~24 hours).
     * @param ayanamshaType used to compute the sidereal difference consistently with
     *   [PanchangCalculator].
     * @return the epoch-millis UTC instant at which the tithi ends, or `null` if no
     *   boundary is found within [maxSearchMillis] (which would indicate a calculation
     *   bug — real tithis never last longer than ~26 hours).
     */
    fun findNextTithiEnd(
        startEpochMillisUtc: Long,
        maxSearchMillis: Long = DEFAULT_SEARCH_WINDOW_MILLIS,
        ayanamshaType: AyanamshaType = AyanamshaType.LAHIRI
    ): Long? {
        val startTithiIndex = tithiIndexAt(startEpochMillisUtc, ayanamshaType)

        var prevMillis = startEpochMillisUtc
        var prevIndex = startTithiIndex
        var probeMillis = startEpochMillisUtc + CHUNK_MILLIS

        while (probeMillis - startEpochMillisUtc <= maxSearchMillis) {
            val probeIndex = tithiIndexAt(probeMillis, ayanamshaType)
            if (probeIndex != prevIndex) {
                // Boundary is between prev and probe. Refine.
                return refineBoundary(prevMillis, probeMillis, prevIndex, ayanamshaType)
            }
            prevMillis = probeMillis
            prevIndex = probeIndex
            probeMillis += CHUNK_MILLIS
        }
        return null
    }

    /**
     * Find **all** tithi boundaries in the window `[startEpochMillisUtc, endEpochMillisUtc)`.
     * Essential for Kshaya detection (a Kshaya tithi produces two boundaries inside a
     * single sunrise-to-sunrise window).
     *
     * Boundaries are returned in chronological order.
     */
    fun findAllTithiEndsInWindow(
        startEpochMillisUtc: Long,
        endEpochMillisUtc: Long,
        ayanamshaType: AyanamshaType = AyanamshaType.LAHIRI
    ): List<Long> {
        require(endEpochMillisUtc > startEpochMillisUtc) {
            "end must be after start: start=$startEpochMillisUtc end=$endEpochMillisUtc"
        }

        val results = mutableListOf<Long>()
        var prevMillis = startEpochMillisUtc
        var prevIndex = tithiIndexAt(prevMillis, ayanamshaType)
        var probeMillis = prevMillis + CHUNK_MILLIS

        while (probeMillis < endEpochMillisUtc) {
            val probeIndex = tithiIndexAt(probeMillis, ayanamshaType)
            if (probeIndex != prevIndex) {
                val boundary = refineBoundary(prevMillis, probeMillis, prevIndex, ayanamshaType)
                if (boundary != null) results.add(boundary)
            }
            prevMillis = probeMillis
            prevIndex = probeIndex
            probeMillis += CHUNK_MILLIS
        }

        // Check the final partial chunk.
        if (prevMillis < endEpochMillisUtc) {
            val endIndex = tithiIndexAt(endEpochMillisUtc, ayanamshaType)
            if (endIndex != prevIndex) {
                val boundary = refineBoundary(prevMillis, endEpochMillisUtc, prevIndex, ayanamshaType)
                if (boundary != null) results.add(boundary)
            }
        }

        return results
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Bisect `[loMillis, hiMillis]` until the boundary time is accurate to <= 1 second.
     * Precondition: the tithi index at `lo` equals [loTithiIndex] and differs at `hi`.
     */
    private fun refineBoundary(
        loMillis: Long,
        hiMillis: Long,
        loTithiIndex: Int,
        ayanamshaType: AyanamshaType
    ): Long? {
        var lo = loMillis
        var hi = hiMillis
        var iterations = 0

        while (hi - lo > REFINEMENT_TOLERANCE_MILLIS && iterations < MAX_ITERATIONS) {
            val mid = lo + (hi - lo) / 2
            val midIndex = tithiIndexAt(mid, ayanamshaType)
            if (midIndex == loTithiIndex) lo = mid else hi = mid
            iterations++
        }

        return hi
    }

    /**
     * Compute the sidereal tithi index (1..30) at a given instant. Centralized so both
     * the chunk scan and the refinement loop use identical logic.
     */
    private fun tithiIndexAt(epochMillisUtc: Long, ayanamshaType: AyanamshaType): Int {
        val jd = AstroTimeUtils.epochMillisToJulianDay(epochMillisUtc)
        val ayanamsha = Ayanamsha.compute(ayanamshaType, jd)
        val sunSid = normalizeDegrees(engine.sunApparentLongitudeDeg(jd) - ayanamsha)
        val moonSid = normalizeDegrees(engine.moonApparentLongitudeDeg(jd) - ayanamsha)
        return Tithi.indexFromMoonSunDiff(moonSid - sunSid)
    }

    private companion object {
        /** Scan step size — 15 minutes. Fine enough that bisection always converges fast. */
        private const val CHUNK_MILLIS = 15L * 60L * 1000L

        /** Bisection stopping tolerance — 1 second. */
        private const val REFINEMENT_TOLERANCE_MILLIS = 1000L

        /** Hard cap on iterations so a pathological input can't spin forever. */
        private const val MAX_ITERATIONS = 40

        /** Two days — well beyond any real tithi duration (~26 hours max). */
        private const val DEFAULT_SEARCH_WINDOW_MILLIS = 2L * 24L * 60L * 60L * 1000L
    }
}

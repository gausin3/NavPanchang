package com.navpanchang.ephemeris

import android.content.Context
import android.util.Log
import com.navpanchang.ephemeris.AstroMath.normalizeDegrees
import swisseph.SweConst
import swisseph.SwissEph

/**
 * Production [EphemerisEngine] backed by the **Swiss Ephemeris** Thomas Mack Java port
 * (v2.00.00-01, vendored at `app/libs/swisseph-2.00.00-01.jar`).
 *
 * **Accuracy tiers:**
 *
 *  * **With bundled `.se1` files** (the default on first install) — Swiss Ephemeris
 *    reads `sepl_18.se1`, `semo_18.se1`, and `seas_18.se1` from `filesDir/ephe/`. Sun
 *    and Moon longitudes are accurate to **sub-milliarcsecond** for 1800–2399 CE.
 *    That's ~20,000× tighter than [MeeusEphemerisEngine] and gives tithi-boundary
 *    precision well under a second.
 *
 *  * **Moshier fallback** — if [EphemerisAssetInstaller] can't copy the files (corrupt
 *    assets, filesystem write failure), the engine transparently switches to the
 *    library's built-in Moshier approximation (~1 arcsecond) which requires no data
 *    files. Still better than Meeus; the tests treat both paths the same way.
 *
 * The engine inspects [ephePath] at construction time and picks [FLAG_SWIEPH] or
 * [FLAG_MOSEPH] accordingly. Callers never see the distinction.
 *
 * **Licensing:** Swiss Ephemeris is AGPL v3 — see
 * [com.navpanchang.ui.settings.AboutScreen] for attribution. NavPanchang as a whole
 * inherits AGPL v3 because of this dependency.
 *
 * **Thread-safety:** [SwissEph] instances hold mutable state. Each injected
 * `SwissEphemerisEngine` is a `@Singleton`; for parallel use, wrap calls in
 * [EphemerisScope().use { ... }][EphemerisScope].
 */
class SwissEphemerisEngine(context: Context) : EphemerisEngine {

    private val ephePath: String? = EphemerisAssetInstaller.ensureInstalled(context)

    /** Base flags for every call. `SWIEPH` reads the `.se1` data files; `MOSEPH` uses
     *  the built-in Moshier analytic approximation as a fallback. */
    private val flagBase: Int = if (ephePath != null) FLAG_SWIEPH else FLAG_MOSEPH

    /** Lazy construction — avoids touching the native state until first use. */
    private val swe: SwissEph by lazy {
        SwissEph().apply {
            if (ephePath != null) {
                swe_set_ephe_path(ephePath)
                Log.i(TAG, "Swiss Ephemeris initialized with .se1 path: $ephePath")
            } else {
                Log.i(TAG, "Swiss Ephemeris initialized with Moshier fallback (no .se1 files)")
            }
        }
    }

    private val scratch = DoubleArray(6)
    private val errorBuffer = StringBuffer(INITIAL_ERROR_BUFFER_LENGTH)

    override fun sunApparentLongitudeDeg(julianDayUt: Double): Double =
        calcLongitude(julianDayUt, SweConst.SE_SUN)

    override fun moonApparentLongitudeDeg(julianDayUt: Double): Double =
        calcLongitude(julianDayUt, SweConst.SE_MOON)

    override fun moonLatitudeDeg(julianDayUt: Double): Double {
        errorBuffer.setLength(0)
        val result = swe.swe_calc_ut(
            julianDayUt, SweConst.SE_MOON, flagBase, scratch, errorBuffer
        )
        require(result >= 0) { "swe_calc_ut failed for moon lat: $errorBuffer" }
        return scratch[1]
    }

    override fun sunDeclinationDeg(julianDayUt: Double): Double {
        errorBuffer.setLength(0)
        val flags = flagBase or SweConst.SEFLG_EQUATORIAL
        val result = swe.swe_calc_ut(
            julianDayUt, SweConst.SE_SUN, flags, scratch, errorBuffer
        )
        require(result >= 0) { "swe_calc_ut failed for sun dec: $errorBuffer" }
        // xx[0] = RA, xx[1] = declination when SEFLG_EQUATORIAL is set.
        return scratch[1]
    }

    override fun sunRightAscensionDeg(julianDayUt: Double): Double {
        errorBuffer.setLength(0)
        val flags = flagBase or SweConst.SEFLG_EQUATORIAL
        val result = swe.swe_calc_ut(
            julianDayUt, SweConst.SE_SUN, flags, scratch, errorBuffer
        )
        require(result >= 0) { "swe_calc_ut failed for sun ra: $errorBuffer" }
        return normalizeDegrees(scratch[0])
    }

    /**
     * Release any native file handles opened against the `.se1` files. Idempotent.
     * Called by [EphemerisScope.close].
     */
    fun close() {
        runCatching { swe.swe_close() }
    }

    private fun calcLongitude(julianDayUt: Double, body: Int): Double {
        errorBuffer.setLength(0)
        val result = swe.swe_calc_ut(julianDayUt, body, flagBase, scratch, errorBuffer)
        require(result >= 0) { "swe_calc_ut failed for body=$body: $errorBuffer" }
        return normalizeDegrees(scratch[0])
    }

    companion object {
        private const val TAG = "SwissEphemerisEngine"

        /** Full Swiss Ephemeris — reads `.se1` data files. Sub-milliarcsecond precision. */
        private const val FLAG_SWIEPH: Int = SweConst.SEFLG_SWIEPH

        /** Moshier fallback — analytic approximation, ~1 arcsecond precision, no data files. */
        private const val FLAG_MOSEPH: Int = SweConst.SEFLG_MOSEPH

        private const val INITIAL_ERROR_BUFFER_LENGTH = 128
    }
}

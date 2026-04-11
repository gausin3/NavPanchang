package com.navpanchang.ephemeris

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Copies the bundled Swiss Ephemeris `.se1` data files from `assets/ephe/` into
 * `filesDir/ephe/` on first launch and returns the absolute path so
 * [SwissEphemerisEngine] can pass it to `swe_set_ephe_path`.
 *
 * **Why the copy:** Swiss Ephemeris opens `.se1` files via standard `FILE*` I/O, which
 * can't read directly from an Android APK (assets are zip-compressed entries, not
 * stand-alone files). The one-time copy to `filesDir` makes them behave like regular
 * files on disk.
 *
 * **Files:** the three files in [REQUIRED_FILES] together cover 1800–2399 CE and are
 * the standard panchang window:
 *
 *  * `sepl_18.se1` — planetary ephemeris (Sun through Pluto)
 *  * `semo_18.se1` — high-precision Moon ephemeris
 *  * `seas_18.se1` — main-belt asteroids (not used by panchang directly but required
 *    by the library's internal checksums)
 *
 * **Idempotency:** on subsequent launches, files already present (and non-empty) are
 * left alone. This is the hot path for 99% of launches after the first install.
 *
 * **Failure behavior:** if a file is missing from assets or can't be copied, this
 * method logs at `WARN` level and returns `null`. [SwissEphemerisEngine] treats a
 * `null` path as "use the Moshier fallback" and still produces correct output — just
 * at ~1 arcsecond precision instead of sub-milliarcsecond.
 *
 * See TECH_DESIGN.md §Swiss Ephemeris
 * integration.
 */
object EphemerisAssetInstaller {

    private val REQUIRED_FILES = listOf(
        "sepl_18.se1",
        "semo_18.se1",
        "seas_18.se1"
    )

    /**
     * Ensure the `.se1` files exist under `filesDir/ephe/` and return the absolute
     * path to the directory. Safe to call repeatedly; subsequent calls just verify
     * existence and return the path.
     *
     * @return the absolute path to the ephemeris directory, or `null` if at least one
     *   required file could not be installed (in which case the caller should fall
     *   back to Moshier-only mode).
     */
    fun ensureInstalled(context: Context): String? {
        val dir = File(context.filesDir, EPHE_SUBDIR)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Failed to create $dir")
            return null
        }
        for (name in REQUIRED_FILES) {
            val outFile = File(dir, name)
            if (outFile.exists() && outFile.length() > 0) continue
            val ok = runCatching {
                context.assets.open("$ASSET_SUBDIR/$name").use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.isSuccess
            if (!ok) {
                Log.w(TAG, "Failed to copy $name from assets — falling back to Moshier")
                return null
            }
            Log.i(TAG, "Installed ephemeris file: ${outFile.name} (${outFile.length()} bytes)")
        }
        return dir.absolutePath
    }

    private const val TAG = "EphemerisAssetInstaller"
    private const val EPHE_SUBDIR = "ephe"
    private const val ASSET_SUBDIR = "ephe"
}

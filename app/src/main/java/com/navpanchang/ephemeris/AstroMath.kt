package com.navpanchang.ephemeris

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small utility functions used throughout the ephemeris and panchang modules.
 *
 * Math here is elementary but error-prone (degree/radian mixups, normalization
 * failures on negatives). Centralizing it in one place with explicit suffixes
 * makes calling code read clearly and keeps mistakes out.
 */
object AstroMath {

    private const val DEG_TO_RAD = PI / 180.0
    private const val RAD_TO_DEG = 180.0 / PI

    fun degToRad(degrees: Double): Double = degrees * DEG_TO_RAD
    fun radToDeg(radians: Double): Double = radians * RAD_TO_DEG

    /** Normalize an angle in degrees to the range [0, 360). Handles negative inputs. */
    fun normalizeDegrees(angleDegrees: Double): Double {
        val r = angleDegrees % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Sine of an angle given in degrees. */
    fun sinDeg(angleDegrees: Double): Double = sin(degToRad(angleDegrees))

    /** Cosine of an angle given in degrees. */
    fun cosDeg(angleDegrees: Double): Double = cos(degToRad(angleDegrees))
}

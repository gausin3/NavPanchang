package com.navpanchang.panchang

/**
 * A tithi is one of 30 lunar days in a synodic month. Each tithi spans 12° of moon–sun
 * longitudinal separation, so a lunar month of 29.53 solar days contains exactly 30 tithis.
 *
 * We use a 1-based index covering both pakshas:
 * * 1..15 = Shukla Paksha (waxing), ending at tithi 15 (Purnima, the full moon).
 * * 16..30 = Krishna Paksha (waning), ending at tithi 30 (Amavasya, the new moon).
 *
 * **Naming note:** The traditional Sanskrit names (Pratipada, Dvitiya, ..., Chaturdashi +
 * Purnima/Amavasya) repeat across both pakshas, so "Ekadashi" is tithi 11 *and* tithi 26.
 * We keep both pieces of information — the absolute [index] and the [nameIndex] within its
 * paksha (1..15) — so the UI can format as "Shukla Ekadashi" or "शुक्ल एकादशी".
 */
data class Tithi(
    /** Absolute tithi index in 1..30. */
    val index: Int,
    val paksha: Paksha
) {
    init {
        require(index in 1..30) { "Tithi index must be 1..30, got $index" }
        require(paksha == Paksha.ofTithiIndex(index)) { "Paksha $paksha inconsistent with index $index" }
    }

    /** Tithi index within its paksha, in 1..15. */
    val nameIndex: Int get() = ((index - 1) % 15) + 1

    /** English name of the tithi (e.g. "Ekadashi"). */
    val nameEn: String get() = NAMES_EN[nameIndex - 1]

    /** Hindi (Devanagari) name of the tithi (e.g. "एकादशी"). */
    val nameHi: String get() = NAMES_HI[nameIndex - 1]

    /** Qualified name including paksha (e.g. "Shukla Ekadashi"). */
    val qualifiedNameEn: String get() = "${paksha.nameEn} $nameEn"

    /** Qualified name in Hindi (e.g. "शुक्ल एकादशी"). */
    val qualifiedNameHi: String get() = "${paksha.nameHi} $nameHi"

    companion object {
        private val NAMES_EN = listOf(
            "Pratipada", "Dvitiya", "Tritiya", "Chaturthi", "Panchami",
            "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
            "Ekadashi", "Dvadashi", "Trayodashi", "Chaturdashi", "Purnima"
        )

        private val NAMES_HI = listOf(
            "प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पञ्चमी",
            "षष्ठी", "सप्तमी", "अष्टमी", "नवमी", "दशमी",
            "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा"
        )

        /** Name of tithi 15 (Purnima) vs tithi 30 (Amavasya). */
        private const val AMAVASYA_EN = "Amavasya"
        private const val AMAVASYA_HI = "अमावस्या"

        /**
         * Compute the tithi index (1..30) for the given moon–sun longitude difference.
         * The argument is normalized into the range 0..360 internally.
         */
        fun indexFromMoonSunDiff(moonMinusSunDegrees: Double): Int {
            val normalized = ((moonMinusSunDegrees % 360) + 360) % 360
            return (normalized / 12.0).toInt() + 1
        }

        /** Construct a [Tithi] from a moon–sun longitude difference (in degrees). */
        fun fromMoonSunDiff(moonMinusSunDegrees: Double): Tithi {
            val idx = indexFromMoonSunDiff(moonMinusSunDegrees)
            return Tithi(index = idx, paksha = Paksha.ofTithiIndex(idx))
        }
    }

    /**
     * Tithi 30 is called "Amavasya" in the naming convention we expose — not "Purnima".
     * Override for that special case.
     */
    fun displayNameEn(): String = if (index == 30) AMAVASYA_EN else nameEn
    fun displayNameHi(): String = if (index == 30) AMAVASYA_HI else nameHi
}

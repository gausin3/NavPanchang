package com.navpanchang.panchang

/**
 * A nakshatra is one of 27 lunar mansions — each spans 360°/27 = 13°20' of sidereal longitude.
 *
 * For a given moon sidereal longitude, the nakshatra index is `floor(longitude / (360/27)) + 1`.
 */
data class Nakshatra(
    /** 1-based nakshatra index in 1..27. */
    val index: Int
) {
    init {
        require(index in 1..27) { "Nakshatra index must be 1..27, got $index" }
    }

    val nameEn: String get() = NAMES_EN[index - 1]
    val nameHi: String get() = NAMES_HI[index - 1]

    companion object {
        /** 360° / 27 nakshatras = 13.333… degrees per nakshatra. */
        private const val DEGREES_PER_NAKSHATRA = 360.0 / 27.0

        private val NAMES_EN = listOf(
            "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira",
            "Ardra", "Punarvasu", "Pushya", "Ashlesha", "Magha",
            "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Swati",
            "Vishakha", "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha",
            "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha",
            "Purva Bhadrapada", "Uttara Bhadrapada", "Revati"
        )

        private val NAMES_HI = listOf(
            "अश्विनी", "भरणी", "कृत्तिका", "रोहिणी", "मृगशीर्ष",
            "आर्द्रा", "पुनर्वसु", "पुष्य", "आश्लेषा", "मघा",
            "पूर्व फाल्गुनी", "उत्तर फाल्गुनी", "हस्त", "चित्रा", "स्वाति",
            "विशाखा", "अनुराधा", "ज्येष्ठा", "मूल", "पूर्वाषाढ़ा",
            "उत्तराषाढ़ा", "श्रवण", "धनिष्ठा", "शतभिषा",
            "पूर्व भाद्रपद", "उत्तर भाद्रपद", "रेवती"
        )

        /** Compute the nakshatra index for a sidereal moon longitude (0..360 degrees). */
        fun indexFromMoonSidereal(moonSiderealDegrees: Double): Int {
            val normalized = ((moonSiderealDegrees % 360) + 360) % 360
            return (normalized / DEGREES_PER_NAKSHATRA).toInt() + 1
        }

        /** Construct a [Nakshatra] from a sidereal moon longitude. */
        fun fromMoonSidereal(moonSiderealDegrees: Double): Nakshatra =
            Nakshatra(indexFromMoonSidereal(moonSiderealDegrees))
    }
}

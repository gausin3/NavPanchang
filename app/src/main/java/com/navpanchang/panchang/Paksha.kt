package com.navpanchang.panchang

/**
 * The two halves of a lunar month in the Hindu calendar.
 *
 * * [Shukla] — the waxing half (bright fortnight). Tithis 1–15, ending at Purnima (full moon).
 * * [Krishna] — the waning half (dark fortnight). Tithis 16–30, ending at Amavasya (new moon).
 *
 * In our tithi numbering (1–30), 1–15 = Shukla and 16–30 = Krishna. The "tithi within paksha"
 * (1–15) can be recovered as `((tithiIndex - 1) % 15) + 1`.
 */
enum class Paksha(val nameEn: String, val nameHi: String) {
    Shukla("Shukla", "शुक्ल"),
    Krishna("Krishna", "कृष्ण");

    companion object {
        /** Returns the paksha for a 1-based [tithiIndex] in the range 1..30. */
        fun ofTithiIndex(tithiIndex: Int): Paksha {
            require(tithiIndex in 1..30) { "tithiIndex must be 1..30, got $tithiIndex" }
            return if (tithiIndex <= 15) Shukla else Krishna
        }
    }
}

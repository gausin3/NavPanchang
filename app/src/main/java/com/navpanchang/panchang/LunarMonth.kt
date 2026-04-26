package com.navpanchang.panchang

/**
 * The twelve lunar months of the Hindu calendar. In the most common Amanta (new-moon-ending)
 * reckoning used across most of India, Chaitra is the first month of the year and Phalguna
 * the last. Each month is tied to the zodiac sign (Rashi) the Sun is in at the start of the
 * month — if the Sun does not change Rashi during a lunar month, that month is Adhik (extra).
 */
enum class LunarMonth(val ordinalIndex: Int, val nameEn: String, val nameHi: String) {
    Chaitra(1, "Chaitra", "चैत्र"),
    Vaisakha(2, "Vaisakha", "वैशाख"),
    Jyeshtha(3, "Jyeshtha", "ज्येष्ठ"),
    Ashadha(4, "Ashadha", "आषाढ़"),
    Shravana(5, "Shravana", "श्रावण"),
    Bhadrapada(6, "Bhadrapada", "भाद्रपद"),
    Ashwin(7, "Ashwin", "अश्विन"),
    Kartika(8, "Kartika", "कार्तिक"),
    Margashirsha(9, "Margashirsha", "मार्गशीर्ष"),
    Pausha(10, "Pausha", "पौष"),
    Magha(11, "Magha", "माघ"),
    Phalguna(12, "Phalguna", "फाल्गुन");

    companion object {
        fun ofIndex(index: Int): LunarMonth =
            entries.firstOrNull { it.ordinalIndex == index }
                ?: throw IllegalArgumentException("Unknown lunar month index: $index")
    }
}

/**
 * Classification of a lunar month's relationship to the solar year.
 *
 * * [Nija] — normal: exactly one Solar Sankranti occurs within the lunar month.
 * * [Adhik] — extra/intercalary: zero Solar Sankrantis (the month is repeated so the
 *   lunar and solar years stay aligned). Occurs roughly every 32 months.
 * * [Kshaya] — skipped: two Solar Sankrantis in the same lunar month, so a month gets dropped
 *   from the calendar. Extremely rare — last occurred in 1963, next around 2124.
 *
 * See TECH_DESIGN.md §Adhik Maas handling.
 */
enum class LunarMonthType {
    Nija,
    Adhik,
    Kshaya
}

/**
 * A lunar month with its classification. Produced by `AdhikMaasDetector` in Phase 1b.
 */
data class ClassifiedLunarMonth(
    val month: LunarMonth,
    val type: LunarMonthType
) {
    val displayEn: String get() = when (type) {
        LunarMonthType.Nija -> month.nameEn
        LunarMonthType.Adhik -> "Adhik ${month.nameEn}"
        LunarMonthType.Kshaya -> "Kshaya ${month.nameEn}"
    }

    val displayHi: String get() = when (type) {
        LunarMonthType.Nija -> month.nameHi
        LunarMonthType.Adhik -> "अधिक ${month.nameHi}"
        LunarMonthType.Kshaya -> "क्षय ${month.nameHi}"
    }
}

/**
 * Which lunar-month boundary convention the user prefers for *displaying* month names.
 * Both conventions agree on the actual day a vrat falls on — they only differ in what
 * month name is attached to the *Krishna paksha* (waning fortnight). Shukla paksha is
 * identical in both.
 *
 * * [AMANTA] — month ends on Amavasya (new moon). Common in Maharashtra, Gujarat,
 *   Karnataka, Andhra Pradesh, Telangana, Tamil Nadu, Kerala, Goa.
 * * [PURNIMANTA] — month ends on Purnima (full moon). Common in UP, Bihar, Rajasthan,
 *   Punjab, Haryana, Delhi, MP, Chhattisgarh, Jharkhand, Odisha, HP, Uttarakhand.
 *
 * **Important architectural note:** the engine ([com.navpanchang.panchang.AdhikMaasDetector]
 * and the rules in `events.json`) is canonically Amanta. The user's convention preference
 * affects display only — never matching. See [displayLunarMonth] for the translation rule
 * and TECH_DESIGN.md §Amanta vs Purnimanta.
 */
enum class LunarConvention {
    AMANTA,
    PURNIMANTA
}

/**
 * Translate the engine's canonical (Amanta) lunar-month label to what the user should
 * see given their chosen [convention].
 *
 * Rule: in Purnimanta, Krishna paksha (waning fortnight) is labeled with the *next* lunar
 * month's name. So engine `Magha Krishna 14` (Mahashivratri) = "Phalguna Krishna 14" to
 * a Purnimanta user. Shukla paksha is identical in both conventions and never shifts.
 *
 * Pure / stateless — safe to call from Compose recomposition.
 */
fun displayLunarMonth(
    month: LunarMonth,
    paksha: Paksha,
    convention: LunarConvention
): LunarMonth = if (convention == LunarConvention.PURNIMANTA && paksha == Paksha.Krishna) {
    LunarMonth.ofIndex((month.ordinalIndex % 12) + 1)
} else {
    month
}

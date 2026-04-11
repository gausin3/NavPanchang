package com.navpanchang.panchang

/**
 * The twelve sidereal zodiac signs of the Hindu/Vedic system. A Solar Sankranti is the
 * moment when the Sun crosses from one Rashi to the next.
 *
 * Lunar months are named for the Rashi the Sun enters *during* that month — for example,
 * the lunar month containing Mesha Sankranti is called Chaitra in the Amanta tradition
 * (actually: the month where Mesha Sankranti occurs gives the month its name per most
 * modern panchangs).
 */
enum class Rashi(val index: Int, val nameEn: String, val nameHi: String) {
    Mesha(1, "Mesha", "मेष"),
    Vrishabha(2, "Vrishabha", "वृषभ"),
    Mithuna(3, "Mithuna", "मिथुन"),
    Karka(4, "Karka", "कर्क"),
    Simha(5, "Simha", "सिंह"),
    Kanya(6, "Kanya", "कन्या"),
    Tula(7, "Tula", "तुला"),
    Vrishchika(8, "Vrishchika", "वृश्चिक"),
    Dhanu(9, "Dhanu", "धनु"),
    Makara(10, "Makara", "मकर"),
    Kumbha(11, "Kumbha", "कुम्भ"),
    Meena(12, "Meena", "मीन");

    companion object {
        fun ofIndex(index: Int): Rashi =
            entries.firstOrNull { it.index == index }
                ?: throw IllegalArgumentException("Unknown rashi index: $index")
    }
}

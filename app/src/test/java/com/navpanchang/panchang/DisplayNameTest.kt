package com.navpanchang.panchang

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for the bilingual display helpers. These functions are the only
 * place in the codebase that decides how a tithi/nakshatra/lunar-month is shown to
 * the user — the rest of the code calls them and threads through the user's
 * [AppLanguage]. See `DisplayName.kt` and `DESIGN.md` §Language toggle.
 */
class DisplayNameTest {

    @Test
    fun `bilingual collapses to English when companion is null or blank`() {
        assertEquals("Ekadashi", bilingual("Ekadashi", null))
        assertEquals("Ekadashi", bilingual("Ekadashi", ""))
        assertEquals("Ekadashi", bilingual("Ekadashi", "   "))
    }

    @Test
    fun `bilingual collapses to English when companion equals English`() {
        // Cities and proper nouns where there is no native translation distinct from
        // the English form (e.g. "GPS location") should not render as
        // "GPS location (GPS location)".
        assertEquals("GPS location", bilingual("GPS location", "GPS location"))
    }

    @Test
    fun `bilingual produces parens form when companion differs`() {
        assertEquals("Ekadashi (एकादशी)", bilingual("Ekadashi", "एकादशी"))
    }

    @Test
    fun `Tithi bilingualQualified returns paksha-prefixed form for regular tithis`() {
        // Tithi 11 = Shukla Ekadashi.
        val t = Tithi(index = 11, paksha = Paksha.Shukla)
        assertEquals(
            "Shukla Ekadashi (शुक्ल एकादशी)",
            t.bilingualQualified(AppLanguage.HINDI)
        )
    }

    @Test
    fun `Tithi bilingualQualified honors Amavasya special case for tithi 30`() {
        // Tithi 30's display is "Amavasya" — never "Krishna Purnima".
        val t = Tithi(index = 30, paksha = Paksha.Krishna)
        assertEquals(
            "Amavasya (अमावस्या)",
            t.bilingualQualified(AppLanguage.HINDI)
        )
    }

    @Test
    fun `English mode collapses bilingual to single English form`() {
        // Picking ENGLISH as the companion language means the user wants English-only
        // labels — no parens, no Devanagari. The bilingual() helper collapses when
        // English equals the companion.
        val t = Tithi(index = 11, paksha = Paksha.Shukla)
        assertEquals("Shukla Ekadashi", t.bilingualQualified(AppLanguage.ENGLISH))

        val tithi30 = Tithi(index = 30, paksha = Paksha.Krishna)
        assertEquals("Amavasya", tithi30.bilingualQualified(AppLanguage.ENGLISH))

        val n = Nakshatra(index = 1)
        assertEquals("Ashwini", bilingual(n.nameEn, n.companionName(AppLanguage.ENGLISH)))

        val m = LunarMonth.Magha
        assertEquals("Magha", bilingual(m.nameEn, m.companionName(AppLanguage.ENGLISH)))
    }

    @Test
    fun `Marathi Tamil Gujarati fall back to Hindi until per-language data ships`() {
        val t = Tithi(index = 11, paksha = Paksha.Shukla)
        // Until we have nameMr/nameTa/nameGu fields on Tithi, all three companion
        // languages render the same as Hindi — the user always sees two languages.
        val hindi = t.bilingualQualified(AppLanguage.HINDI)
        assertEquals(hindi, t.bilingualQualified(AppLanguage.MARATHI))
        assertEquals(hindi, t.bilingualQualified(AppLanguage.TAMIL))
        assertEquals(hindi, t.bilingualQualified(AppLanguage.GUJARATI))
    }

    @Test
    fun `Nakshatra bilingual works for canonical case`() {
        val n = Nakshatra(index = 1) // Ashwini
        assertEquals(
            "Ashwini (अश्विनी)",
            bilingual(n.nameEn, n.companionName(AppLanguage.HINDI))
        )
    }

    @Test
    fun `LunarMonth bilingual works for canonical case`() {
        val m = LunarMonth.Magha
        assertEquals(
            "Magha (माघ)",
            bilingual(m.nameEn, m.companionName(AppLanguage.HINDI))
        )
    }
}

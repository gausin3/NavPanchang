package com.navpanchang.panchang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure unit tests for the [Tithi], [Paksha], and [Nakshatra] value types — no ephemeris
 * engine involved. Verifies indexing, naming, paksha classification, and edge cases like
 * the Amavasya/Purnima display-name override.
 */
class TithiTest {

    @Test
    fun `tithi 1 is Shukla Pratipada`() {
        val t = Tithi(1, Paksha.Shukla)
        assertEquals("Pratipada", t.nameEn)
        assertEquals("प्रतिपदा", t.nameHi)
        assertEquals(Paksha.Shukla, t.paksha)
        assertEquals(1, t.nameIndex)
        assertEquals("Shukla Pratipada", t.qualifiedNameEn)
        assertEquals("शुक्ल प्रतिपदा", t.qualifiedNameHi)
    }

    @Test
    fun `tithi 11 is Shukla Ekadashi`() {
        val t = Tithi(11, Paksha.Shukla)
        assertEquals("Ekadashi", t.nameEn)
        assertEquals("एकादशी", t.nameHi)
        assertEquals(11, t.nameIndex)
    }

    @Test
    fun `tithi 15 is Shukla Purnima`() {
        val t = Tithi(15, Paksha.Shukla)
        assertEquals("Purnima", t.nameEn)
        assertEquals("पूर्णिमा", t.nameHi)
        assertEquals(Paksha.Shukla, t.paksha)
    }

    @Test
    fun `tithi 16 is Krishna Pratipada`() {
        val t = Tithi(16, Paksha.Krishna)
        assertEquals("Pratipada", t.nameEn)
        assertEquals(1, t.nameIndex)
        assertEquals(Paksha.Krishna, t.paksha)
    }

    @Test
    fun `tithi 26 is Krishna Ekadashi`() {
        val t = Tithi(26, Paksha.Krishna)
        assertEquals("Ekadashi", t.nameEn)
        assertEquals(11, t.nameIndex)
        assertEquals(Paksha.Krishna, t.paksha)
    }

    @Test
    fun `tithi 30 displays as Amavasya not Purnima`() {
        val t = Tithi(30, Paksha.Krishna)
        assertEquals("Purnima", t.nameEn) // nameIndex = 15, so raw nameEn is Purnima
        assertEquals("Amavasya", t.displayNameEn())
        assertEquals("अमावस्या", t.displayNameHi())
    }

    @Test
    fun `fromMoonSunDiff at exactly zero gives tithi 1`() {
        val t = Tithi.fromMoonSunDiff(0.0)
        assertEquals(1, t.index)
        assertEquals(Paksha.Shukla, t.paksha)
    }

    @Test
    fun `fromMoonSunDiff just before 12 degrees gives tithi 1`() {
        val t = Tithi.fromMoonSunDiff(11.999)
        assertEquals(1, t.index)
    }

    @Test
    fun `fromMoonSunDiff at exactly 12 degrees gives tithi 2`() {
        val t = Tithi.fromMoonSunDiff(12.0)
        assertEquals(2, t.index)
    }

    @Test
    fun `fromMoonSunDiff at 180 degrees gives tithi 16 Krishna Pratipada`() {
        val t = Tithi.fromMoonSunDiff(180.0)
        assertEquals(16, t.index)
        assertEquals(Paksha.Krishna, t.paksha)
    }

    @Test
    fun `fromMoonSunDiff at 179 point 99 gives tithi 15 Purnima`() {
        val t = Tithi.fromMoonSunDiff(179.99)
        assertEquals(15, t.index)
        assertEquals(Paksha.Shukla, t.paksha)
    }

    @Test
    fun `fromMoonSunDiff at 348 degrees gives tithi 30 Amavasya`() {
        val t = Tithi.fromMoonSunDiff(348.0)
        assertEquals(30, t.index)
    }

    @Test
    fun `fromMoonSunDiff handles negative input via modular arithmetic`() {
        val t = Tithi.fromMoonSunDiff(-1.0) // equivalent to 359°
        assertEquals(30, t.index)
    }

    @Test
    fun `fromMoonSunDiff handles input over 360 via modular arithmetic`() {
        val t = Tithi.fromMoonSunDiff(365.0) // equivalent to 5°
        assertEquals(1, t.index)
    }

    @Test
    fun `constructing tithi with index out of range throws`() {
        assertThrows(IllegalArgumentException::class.java) { Tithi(0, Paksha.Shukla) }
        assertThrows(IllegalArgumentException::class.java) { Tithi(31, Paksha.Krishna) }
    }

    @Test
    fun `constructing tithi with inconsistent paksha throws`() {
        assertThrows(IllegalArgumentException::class.java) { Tithi(5, Paksha.Krishna) }
        assertThrows(IllegalArgumentException::class.java) { Tithi(20, Paksha.Shukla) }
    }

    @Test
    fun `nakshatra indexing covers all 27 mansions`() {
        for (i in 1..27) {
            val n = Nakshatra(i)
            assertEquals(i, n.index)
        }
    }

    @Test
    fun `nakshatra index out of range throws`() {
        assertThrows(IllegalArgumentException::class.java) { Nakshatra(0) }
        assertThrows(IllegalArgumentException::class.java) { Nakshatra(28) }
    }

    @Test
    fun `nakshatra fromMoonSidereal at 0 is Ashwini`() {
        val n = Nakshatra.fromMoonSidereal(0.0)
        assertEquals(1, n.index)
        assertEquals("Ashwini", n.nameEn)
    }

    @Test
    fun `nakshatra fromMoonSidereal at 13 point 5 degrees is Bharani`() {
        val n = Nakshatra.fromMoonSidereal(13.5)
        assertEquals(2, n.index)
        assertEquals("Bharani", n.nameEn)
    }

    @Test
    fun `nakshatra fromMoonSidereal handles wrap around 360`() {
        val n = Nakshatra.fromMoonSidereal(359.9)
        assertEquals(27, n.index)
        assertEquals("Revati", n.nameEn)
    }

}

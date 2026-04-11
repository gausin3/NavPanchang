package com.navpanchang.data.mapper

import com.navpanchang.panchang.LunarMonth
import com.navpanchang.panchang.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Pure unit tests for [OccurrenceMapper]. No Room, no Context — just verifying that
 * domain objects round-trip cleanly through the entity representation.
 */
class OccurrenceMapperTest {

    private val baseOccurrence = Occurrence(
        eventId = "shukla_ekadashi",
        dateLocal = LocalDate.of(2024, 4, 19),
        sunriseUtc = 1713480000000L,
        observanceUtc = 1713480000000L,
        paranaStartUtc = 1713566400000L,
        paranaEndUtc = 1713584400000L,
        shiftedDueToViddha = false,
        isKshayaContext = false,
        lunarMonth = LunarMonth.Vaisakha,
        isAdhik = false,
        locationTag = "HOME",
        isHighPrecision = false
    )

    @Test
    fun `round trip preserves every field`() {
        val computedAt = 1713484000000L
        val entity = OccurrenceMapper.toEntity(baseOccurrence, computedAt)
        val back = OccurrenceMapper.fromEntity(entity)
        assertEquals(baseOccurrence, back)
    }

    @Test
    fun `entity id is zero for insert`() {
        val entity = OccurrenceMapper.toEntity(baseOccurrence, 0L)
        assertEquals(0L, entity.id)
    }

    @Test
    fun `dateLocal serializes as ISO yyyy-mm-dd`() {
        val entity = OccurrenceMapper.toEntity(baseOccurrence, 0L)
        assertEquals("2024-04-19", entity.dateLocal)
    }

    @Test
    fun `lunarMonth null round trips as null`() {
        val o = baseOccurrence.copy(lunarMonth = null)
        val entity = OccurrenceMapper.toEntity(o, 0L)
        assertNull(entity.lunarMonth)
        assertNull(OccurrenceMapper.fromEntity(entity).lunarMonth)
    }

    @Test
    fun `shifted and kshaya flags propagate`() {
        val o = baseOccurrence.copy(shiftedDueToViddha = true, isKshayaContext = true)
        val entity = OccurrenceMapper.toEntity(o, 0L)
        val back = OccurrenceMapper.fromEntity(entity)
        assertEquals(true, back.shiftedDueToViddha)
        assertEquals(true, back.isKshayaContext)
    }

    @Test
    fun `unknown lunar month string decodes as null without throwing`() {
        // Simulate a corrupted row left behind by a future release.
        val entity = OccurrenceMapper.toEntity(baseOccurrence, 0L)
            .copy(lunarMonth = "NotARealMonth")
        val back = OccurrenceMapper.fromEntity(entity)
        assertNull(back.lunarMonth)
    }

    @Test
    fun `CURRENT tier round trips isHighPrecision true`() {
        val o = baseOccurrence.copy(locationTag = "CURRENT", isHighPrecision = true)
        val entity = OccurrenceMapper.toEntity(o, 0L)
        val back = OccurrenceMapper.fromEntity(entity)
        assertEquals("CURRENT", back.locationTag)
        assertEquals(true, back.isHighPrecision)
    }
}

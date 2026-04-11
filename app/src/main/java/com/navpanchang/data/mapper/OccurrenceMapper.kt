package com.navpanchang.data.mapper

import com.navpanchang.data.db.entities.OccurrenceEntity
import com.navpanchang.panchang.LunarMonth
import com.navpanchang.panchang.Occurrence
import java.time.LocalDate

/**
 * Translates between the domain-level [Occurrence] (produced by
 * [com.navpanchang.panchang.OccurrenceComputer]) and the Room-level [OccurrenceEntity]
 * (stored in the database).
 *
 * Why separate types: the computer produces pure, immutable value objects with no Room
 * dependencies, so it can be unit-tested without a database. The entity carries Room's
 * auto-generated primary key and string-encoded `lunarMonth`. This mapper is the single
 * place the two representations meet.
 */
object OccurrenceMapper {

    /** Domain → entity. `id = 0` means "auto-generate on insert". */
    fun toEntity(occurrence: Occurrence, computedAtUtc: Long): OccurrenceEntity =
        OccurrenceEntity(
            id = 0,
            eventId = occurrence.eventId,
            dateLocal = occurrence.dateLocal.toString(),  // YYYY-MM-DD
            sunriseUtc = occurrence.sunriseUtc,
            observanceUtc = occurrence.observanceUtc,
            paranaStartUtc = occurrence.paranaStartUtc,
            paranaEndUtc = occurrence.paranaEndUtc,
            shiftedDueToViddha = occurrence.shiftedDueToViddha,
            isKshayaContext = occurrence.isKshayaContext,
            lunarMonth = occurrence.lunarMonth?.name,
            isAdhik = occurrence.isAdhik,
            locationTag = occurrence.locationTag,
            isHighPrecision = occurrence.isHighPrecision,
            computedAt = computedAtUtc
        )

    /** Entity → domain. */
    fun fromEntity(entity: OccurrenceEntity): Occurrence = Occurrence(
        eventId = entity.eventId,
        dateLocal = LocalDate.parse(entity.dateLocal),
        sunriseUtc = entity.sunriseUtc,
        observanceUtc = entity.observanceUtc,
        paranaStartUtc = entity.paranaStartUtc,
        paranaEndUtc = entity.paranaEndUtc,
        shiftedDueToViddha = entity.shiftedDueToViddha,
        isKshayaContext = entity.isKshayaContext,
        lunarMonth = entity.lunarMonth?.let { runCatching { LunarMonth.valueOf(it) }.getOrNull() },
        isAdhik = entity.isAdhik,
        locationTag = entity.locationTag,
        isHighPrecision = entity.isHighPrecision
    )
}

package com.navpanchang.data.repo

import com.navpanchang.data.db.OccurrenceDao
import com.navpanchang.data.mapper.OccurrenceMapper
import com.navpanchang.panchang.Occurrence
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single query path for reading and writing [Occurrence] rows.
 *
 * **Query precedence:** when the UI or alarm scheduler asks "what's the next occurrence
 * of Ekadashi?", it ALWAYS goes through [getNextOccurrence] which asks the DAO with
 * `ORDER BY isHighPrecision DESC`. That way, if a Tier 2 `CURRENT` row exists for a date
 * (travel-adjusted), it wins over the Tier 1 `HOME` row for the same date. If no Tier 2
 * row exists, the HOME row is returned naturally.
 *
 * **Write paths:** the computer produces either a HOME window (24 months, triggered by
 * [com.navpanchang.alarms.RefreshWorker]) or a CURRENT window (30–60 days, triggered by
 * the travel geofence). Both land here via [replaceWindow], which is a transaction:
 * delete existing rows with the same `locationTag`, then bulk-insert the new set. This
 * ensures no stale occurrences linger after a recompute.
 */
@Singleton
class OccurrenceRepository @Inject constructor(
    private val dao: OccurrenceDao
) {

    /**
     * Return the next occurrence of [eventId] on or after [today], preferring a
     * `HIGH_PRECISION` (CURRENT) row over a `HOME` row for the same date. Returns `null`
     * if the computer hasn't populated any occurrences yet for this event.
     */
    suspend fun getNextOccurrence(eventId: String, today: LocalDate): Occurrence? =
        dao.getNextOccurrence(eventId, today.toString())
            ?.let { OccurrenceMapper.fromEntity(it) }

    /**
     * Return the next [limit] occurrences for the given event, in chronological order.
     * Used by the Event Detail screen which shows "next 12 occurrences".
     */
    suspend fun getUpcomingForEvent(
        eventId: String,
        fromDate: LocalDate,
        limit: Int
    ): List<Occurrence> =
        dao.getUpcomingForEvent(eventId, fromDate.toString(), limit)
            .map(OccurrenceMapper::fromEntity)

    /**
     * Observe occurrences in a date range — powers the Calendar month view. Emits on
     * every insert/delete affecting the range.
     */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Occurrence>> =
        dao.observeRange(from.toString(), to.toString())
            .map { entities -> entities.map(OccurrenceMapper::fromEntity) }

    /**
     * Atomically replace every occurrence row with [locationTag] by the freshly-computed
     * [newOccurrences]. Used by [com.navpanchang.alarms.RefreshWorker] after a lookahead
     * computation.
     *
     * @param locationTag either `"HOME"` or `"CURRENT"`.
     * @param newOccurrences the fresh set of domain occurrences. Must all carry the same
     *   [locationTag] as the function argument — asserted at runtime.
     * @param computedAtUtc the epoch-millis instant the computation was performed,
     *   stamped on each row.
     */
    suspend fun replaceWindow(
        locationTag: String,
        newOccurrences: List<Occurrence>,
        computedAtUtc: Long
    ) {
        require(newOccurrences.all { it.locationTag == locationTag }) {
            "replaceWindow for '$locationTag' received occurrences with mismatched tags"
        }
        dao.deleteByLocationTag(locationTag)
        if (newOccurrences.isNotEmpty()) {
            val entities = newOccurrences.map { OccurrenceMapper.toEntity(it, computedAtUtc) }
            dao.upsertAll(entities)
        }
    }

    /**
     * Prune all rows older than [cutoff]. Called by the daily refresh worker so the
     * database doesn't accumulate historical occurrences indefinitely.
     */
    suspend fun pruneBefore(cutoff: LocalDate) {
        dao.deleteOlderThan(cutoff.toString())
    }
}

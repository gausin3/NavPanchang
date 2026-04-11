package com.navpanchang.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navpanchang.data.db.entities.OccurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OccurrenceDao {

    /**
     * Returns the next occurrence of an event on or after [todayLocal], preferring
     * high-precision (CURRENT) rows over HOME rows. This is the single query path used
     * by [com.navpanchang.alarms.AlarmScheduler].
     *
     * See §Two-tier lookahead > Query precedence in the plan.
     */
    @Query(
        """
        SELECT * FROM occurrences
         WHERE eventId = :eventId AND dateLocal >= :todayLocal
         ORDER BY isHighPrecision DESC, dateLocal ASC
         LIMIT 1
        """
    )
    suspend fun getNextOccurrence(eventId: String, todayLocal: String): OccurrenceEntity?

    @Query(
        """
        SELECT * FROM occurrences
         WHERE eventId = :eventId AND dateLocal >= :fromLocal
         ORDER BY isHighPrecision DESC, dateLocal ASC
         LIMIT :limit
        """
    )
    suspend fun getUpcomingForEvent(eventId: String, fromLocal: String, limit: Int): List<OccurrenceEntity>

    @Query(
        """
        SELECT * FROM occurrences
         WHERE dateLocal BETWEEN :fromLocal AND :toLocal
         ORDER BY dateLocal ASC
        """
    )
    fun observeRange(fromLocal: String, toLocal: String): Flow<List<OccurrenceEntity>>

    @Upsert
    suspend fun upsertAll(occurrences: List<OccurrenceEntity>)

    @Query("DELETE FROM occurrences WHERE locationTag = :tag")
    suspend fun deleteByLocationTag(tag: String)

    @Query("DELETE FROM occurrences WHERE dateLocal < :beforeLocal")
    suspend fun deleteOlderThan(beforeLocal: String)

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun getById(id: Long): OccurrenceEntity?
}

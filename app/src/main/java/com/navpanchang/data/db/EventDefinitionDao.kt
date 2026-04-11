package com.navpanchang.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navpanchang.data.db.entities.EventDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDefinitionDao {

    @Query("SELECT * FROM event_definitions WHERE deprecated = 0")
    fun observeActive(): Flow<List<EventDefinitionEntity>>

    @Query("SELECT * FROM event_definitions")
    suspend fun getAll(): List<EventDefinitionEntity>

    @Query("SELECT * FROM event_definitions WHERE id = :id")
    suspend fun getById(id: String): EventDefinitionEntity?

    /**
     * Upsert used by [com.navpanchang.data.seed.EventCatalogSyncer] on catalog version bump.
     * This is the ONLY supported write path — see §Seed data governance.
     */
    @Upsert
    suspend fun upsertAll(events: List<EventDefinitionEntity>)

    @Query("UPDATE event_definitions SET deprecated = 1 WHERE id IN (:ids)")
    suspend fun markDeprecated(ids: List<String>)
}

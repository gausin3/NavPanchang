package com.navpanchang.data.repo

import com.navpanchang.data.db.EventDefinitionDao
import com.navpanchang.data.db.SubscriptionDao
import com.navpanchang.data.db.entities.SubscriptionEntity
import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.EventRuleParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read/write access to the user's event subscription state.
 *
 * **Sanctity rule:** the `subscriptions` table is user-owned state and must NEVER be
 * written to by [com.navpanchang.alarms.RefreshWorker] or
 * [com.navpanchang.data.seed.EventCatalogSyncer]. Only UI screens (via ViewModels) and
 * this repository should touch it. See `MEMORY.md` §Conventions.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val eventDefinitionDao: EventDefinitionDao
) {

    /** Observe all subscription rows. Emits on every toggle. */
    fun observeAll(): Flow<List<SubscriptionEntity>> = subscriptionDao.observeAll()

    /**
     * Return the parsed [EventDefinition]s for every event the user is currently
     * subscribed to with [SubscriptionEntity.enabled] == true. This is what
     * [com.navpanchang.alarms.RefreshWorker] feeds into
     * [com.navpanchang.panchang.OccurrenceComputer.computeWindow].
     */
    suspend fun getEnabledDefinitions(): List<EventDefinition> {
        val enabledIds = subscriptionDao.getEnabled().map { it.eventId }.toSet()
        if (enabledIds.isEmpty()) return emptyList()
        return eventDefinitionDao.getAll()
            .filter { it.id in enabledIds && !it.deprecated }
            .map(EventRuleParser::fromEntity)
    }

    /**
     * Return every non-deprecated event definition, regardless of subscription state.
     * Used by the "+ Add more events" picker.
     */
    suspend fun getAllAvailableDefinitions(): List<EventDefinition> =
        eventDefinitionDao.getAll()
            .filter { !it.deprecated }
            .map(EventRuleParser::fromEntity)

    /**
     * Enable or disable a subscription. Creates the row if it doesn't exist yet.
     * Preserves any custom planner time, sound, and per-sub-alarm toggles the user
     * may have set previously.
     */
    suspend fun setEnabled(eventId: String, enabled: Boolean) {
        val existing = subscriptionDao.getById(eventId)
        val updated = existing?.copy(enabled = enabled)
            ?: SubscriptionEntity(eventId = eventId, enabled = enabled)
        subscriptionDao.upsert(updated)
    }

    /**
     * Observe subscription state alongside its definition, filtered to enabled rows.
     * Used by the Home screen to render the subscription list with event names.
     */
    fun observeEnabledWithDefinitions(): Flow<List<Pair<SubscriptionEntity, EventDefinition>>> =
        subscriptionDao.observeAll().map { subs ->
            val enabled = subs.filter { it.enabled }
            if (enabled.isEmpty()) return@map emptyList()
            val defs = eventDefinitionDao.getAll().associateBy { it.id }
            enabled.mapNotNull { sub ->
                val entity = defs[sub.eventId]?.takeIf { !it.deprecated } ?: return@mapNotNull null
                sub to EventRuleParser.fromEntity(entity)
            }
        }
}

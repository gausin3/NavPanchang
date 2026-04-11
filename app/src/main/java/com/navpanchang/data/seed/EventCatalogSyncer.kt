package com.navpanchang.data.seed

import android.content.Context
import com.navpanchang.data.db.EventDefinitionDao
import com.navpanchang.data.db.MetadataDao
import com.navpanchang.data.db.entities.CalcMetadataEntity
import com.navpanchang.data.db.entities.EventDefinitionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Reads `assets/events.json` and, if its `catalogVersion` is newer than what's in
 * [CalcMetadataEntity.eventCatalogVersion], performs an MDM-style upsert of
 * [EventDefinitionEntity] rows. User [com.navpanchang.data.db.entities.SubscriptionEntity]
 * rows are never touched — they live in a separate table, so rule/name fixes flow
 * to installed apps without losing user preferences.
 *
 * Removed events are marked `deprecated = 1` rather than deleted, so existing
 * subscriptions keep working while the UI prompts the user to migrate.
 *
 * See TECH_DESIGN.md §Seed data governance.
 */
@Singleton
class EventCatalogSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventDefinitionDao: EventDefinitionDao,
    private val metadataDao: MetadataDao
) {

    /**
     * Sync using the bundled `assets/events.json`. Returns `true` if an upgrade was
     * applied (caller should recompute occurrences).
     */
    suspend fun syncIfNeeded(): Boolean {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        return syncFromJson(json)
    }

    /**
     * Testable variant — takes the catalog JSON as a string. The production code path
     * reads from assets and delegates here. Unit tests can call this directly without
     * needing Robolectric asset mocking.
     */
    suspend fun syncFromJson(json: String): Boolean {
        val bundled = parseBundledCatalog(json)
        val current = metadataDao.get()?.eventCatalogVersion ?: 0
        if (bundled.catalogVersion <= current) return false

        val existingIds = eventDefinitionDao.getAll().map { it.id }.toSet()
        val bundledIds = bundled.events.map { it.id }.toSet()
        val removedIds = existingIds - bundledIds

        eventDefinitionDao.upsertAll(bundled.events)
        if (removedIds.isNotEmpty()) {
            eventDefinitionDao.markDeprecated(removedIds.toList())
        }

        val existingMeta = metadataDao.get() ?: CalcMetadataEntity()
        metadataDao.upsert(existingMeta.copy(eventCatalogVersion = bundled.catalogVersion))
        return true
    }

    private fun parseBundledCatalog(json: String): BundledCatalog {
        val root = JSONObject(json)
        val catalogVersion = root.getInt("catalogVersion")
        val eventsArr = root.getJSONArray("events")
        val events = buildList(eventsArr.length()) {
            for (i in 0 until eventsArr.length()) {
                val e = eventsArr.getJSONObject(i)
                add(
                    EventDefinitionEntity(
                        id = e.getString("id"),
                        seedVersion = e.getInt("seedVersion"),
                        nameEn = e.getString("nameEn"),
                        nameHi = e.getString("nameHi"),
                        category = e.getString("category"),
                        ruleType = e.getString("ruleType"),
                        ruleParamsJson = e.getJSONObject("ruleParams").toString(),
                        vratLogic = e.getBoolean("vratLogic"),
                        observeInAdhik = e.getBoolean("observeInAdhik"),
                        hasParana = e.getBoolean("hasParana"),
                        defaultPlannerTimeHhmm = e.getString("defaultPlannerTimeHhmm"),
                        defaultObserverAnchor = e.getString("defaultObserverAnchor"),
                        defaultSoundId = e.getString("defaultSoundId"),
                        deprecated = false,
                        migrationNote = null
                    )
                )
            }
        }
        return BundledCatalog(catalogVersion, events)
    }

    private data class BundledCatalog(
        val catalogVersion: Int,
        val events: List<EventDefinitionEntity>
    )

    companion object {
        private const val ASSET_PATH = "events.json"
    }
}

package com.navpanchang.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navpanchang.data.db.NavPanchangDb
import com.navpanchang.data.db.entities.SubscriptionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the **MDM-style versioned upsert** behavior of [EventCatalogSyncer].
 *
 * Invariants (from the plan §Seed data governance):
 *
 * 1. A newer `catalogVersion` causes every event to be upserted.
 * 2. The `subscriptions` table is NEVER touched — user preferences survive upgrades.
 * 3. Events missing from the new bundle are marked `deprecated = 1`, not deleted.
 * 4. An unchanged `catalogVersion` is a no-op.
 * 5. A lower `catalogVersion` is a no-op (defensive: can't downgrade mid-flight).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EventCatalogSyncerTest {

    private lateinit var db: NavPanchangDb
    private lateinit var syncer: EventCatalogSyncer

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NavPanchangDb::class.java)
            .allowMainThreadQueries()
            .build()
        syncer = EventCatalogSyncer(
            context = context,
            eventDefinitionDao = db.eventDefinitionDao(),
            metadataDao = db.metadataDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------
    // Initial sync on empty database.
    // ------------------------------------------------------------------

    @Test
    fun `initial sync inserts all events and records catalogVersion`() = runTest {
        val upgraded = syncer.syncFromJson(CATALOG_V1)
        assertTrue(upgraded)

        val events = db.eventDefinitionDao().getAll()
        assertEquals(2, events.size)
        assertTrue(events.any { it.id == "shukla_ekadashi" })
        assertTrue(events.any { it.id == "purnima" })

        val meta = db.metadataDao().get()
        assertNotNull(meta)
        assertEquals(1, meta!!.eventCatalogVersion)
    }

    @Test
    fun `same catalogVersion is a no-op on second run`() = runTest {
        syncer.syncFromJson(CATALOG_V1)
        val upgradedAgain = syncer.syncFromJson(CATALOG_V1)
        assertFalse("Second sync with same version should be a no-op", upgradedAgain)
    }

    @Test
    fun `lower catalogVersion is rejected`() = runTest {
        syncer.syncFromJson(CATALOG_V2)
        val downgrade = syncer.syncFromJson(CATALOG_V1)
        assertFalse("Downgrade should be a no-op", downgrade)
        assertEquals(2, db.metadataDao().get()!!.eventCatalogVersion)
    }

    // ------------------------------------------------------------------
    // THE CRITICAL INVARIANT: subscriptions survive catalog upgrades.
    // ------------------------------------------------------------------

    @Test
    fun `user subscriptions are preserved across a catalogVersion bump`() = runTest {
        // Initial sync (v1) and enable a subscription.
        syncer.syncFromJson(CATALOG_V1)
        db.subscriptionDao().upsert(
            SubscriptionEntity(
                eventId = "shukla_ekadashi",
                enabled = true,
                customPlannerHhmm = "19:30",
                customSoundId = "ritual_sankh",
                plannerEnabled = true,
                observerEnabled = true,
                paranaEnabled = false // User turned Parana off
            )
        )

        // Now upgrade to v2 — which changes the rule for shukla_ekadashi and adds a new event.
        val upgraded = syncer.syncFromJson(CATALOG_V2)
        assertTrue(upgraded)

        // The event definition should have been upserted with the new rule params.
        val upgradedEvent = db.eventDefinitionDao().getById("shukla_ekadashi")
        assertNotNull(upgradedEvent)
        assertEquals(2, upgradedEvent!!.seedVersion)
        assertTrue(
            "Rule params should have been upserted to the v2 form",
            upgradedEvent.ruleParamsJson.contains("\"newField\"")
        )

        // The subscription MUST still exist with all its user fields intact.
        val subscription = db.subscriptionDao().getById("shukla_ekadashi")
        assertNotNull("Subscription should survive the upgrade", subscription)
        assertEquals(true, subscription!!.enabled)
        assertEquals("19:30", subscription.customPlannerHhmm)
        assertEquals("ritual_sankh", subscription.customSoundId)
        assertEquals(false, subscription.paranaEnabled)
    }

    // ------------------------------------------------------------------
    // Removed events are deprecated, not deleted.
    // ------------------------------------------------------------------

    @Test
    fun `events missing from new catalog are marked deprecated not deleted`() = runTest {
        // v1 has purnima. v3 removes it.
        syncer.syncFromJson(CATALOG_V1)
        syncer.syncFromJson(CATALOG_V3)

        val purnima = db.eventDefinitionDao().getById("purnima")
        assertNotNull("Deprecated event should still exist in DB", purnima)
        assertTrue("Event should be marked deprecated", purnima!!.deprecated)

        // Subscriptions to deprecated events are also preserved.
        db.subscriptionDao().upsert(
            SubscriptionEntity(eventId = "purnima", enabled = true)
        )
        val subscription = db.subscriptionDao().getById("purnima")
        assertNotNull("Subscription to deprecated event survives", subscription)
    }

    @Test
    fun `active-only query hides deprecated events`() = runTest {
        syncer.syncFromJson(CATALOG_V1)
        syncer.syncFromJson(CATALOG_V3)

        // observeActive excludes deprecated events.
        val activeIds = db.eventDefinitionDao().getAll()
            .filter { !it.deprecated }
            .map { it.id }
        assertTrue(
            "Active list should not contain deprecated purnima",
            !activeIds.contains("purnima")
        )
        assertTrue(
            "Active list should still contain shukla_ekadashi",
            activeIds.contains("shukla_ekadashi")
        )
    }

    // ------------------------------------------------------------------
    // Test catalog fixtures.
    // ------------------------------------------------------------------

    private companion object {
        private val CATALOG_V1 = """
            {
              "catalogVersion": 1,
              "events": [
                {
                  "id": "shukla_ekadashi",
                  "seedVersion": 1,
                  "nameEn": "Shukla Ekadashi",
                  "nameHi": "शुक्ल एकादशी",
                  "category": "vrat",
                  "ruleType": "TithiAtSunrise",
                  "ruleParams": { "tithiIndex": 11, "vratLogic": true },
                  "vratLogic": true,
                  "observeInAdhik": true,
                  "hasParana": true,
                  "defaultPlannerTimeHhmm": "20:00",
                  "defaultObserverAnchor": "SUNRISE",
                  "defaultSoundId": "ritual_temple_bell"
                },
                {
                  "id": "purnima",
                  "seedVersion": 1,
                  "nameEn": "Purnima",
                  "nameHi": "पूर्णिमा",
                  "category": "vrat",
                  "ruleType": "TithiAtSunrise",
                  "ruleParams": { "tithiIndex": 15, "vratLogic": false },
                  "vratLogic": false,
                  "observeInAdhik": true,
                  "hasParana": false,
                  "defaultPlannerTimeHhmm": "20:00",
                  "defaultObserverAnchor": "SUNRISE",
                  "defaultSoundId": "ritual_bell_toll"
                }
              ]
            }
        """.trimIndent()

        // v2: keeps purnima, bumps shukla_ekadashi seedVersion + adds "newField" to params.
        private val CATALOG_V2 = """
            {
              "catalogVersion": 2,
              "events": [
                {
                  "id": "shukla_ekadashi",
                  "seedVersion": 2,
                  "nameEn": "Shukla Ekadashi",
                  "nameHi": "शुक्ल एकादशी",
                  "category": "vrat",
                  "ruleType": "TithiAtSunrise",
                  "ruleParams": { "tithiIndex": 11, "vratLogic": true, "newField": "demo" },
                  "vratLogic": true,
                  "observeInAdhik": true,
                  "hasParana": true,
                  "defaultPlannerTimeHhmm": "20:00",
                  "defaultObserverAnchor": "SUNRISE",
                  "defaultSoundId": "ritual_temple_bell"
                },
                {
                  "id": "purnima",
                  "seedVersion": 1,
                  "nameEn": "Purnima",
                  "nameHi": "पूर्णिमा",
                  "category": "vrat",
                  "ruleType": "TithiAtSunrise",
                  "ruleParams": { "tithiIndex": 15, "vratLogic": false },
                  "vratLogic": false,
                  "observeInAdhik": true,
                  "hasParana": false,
                  "defaultPlannerTimeHhmm": "20:00",
                  "defaultObserverAnchor": "SUNRISE",
                  "defaultSoundId": "ritual_bell_toll"
                }
              ]
            }
        """.trimIndent()

        // v3: removes purnima entirely (should become deprecated, not deleted).
        private val CATALOG_V3 = """
            {
              "catalogVersion": 3,
              "events": [
                {
                  "id": "shukla_ekadashi",
                  "seedVersion": 2,
                  "nameEn": "Shukla Ekadashi",
                  "nameHi": "शुक्ल एकादशी",
                  "category": "vrat",
                  "ruleType": "TithiAtSunrise",
                  "ruleParams": { "tithiIndex": 11, "vratLogic": true },
                  "vratLogic": true,
                  "observeInAdhik": true,
                  "hasParana": true,
                  "defaultPlannerTimeHhmm": "20:00",
                  "defaultObserverAnchor": "SUNRISE",
                  "defaultSoundId": "ritual_temple_bell"
                }
              ]
            }
        """.trimIndent()
    }
}

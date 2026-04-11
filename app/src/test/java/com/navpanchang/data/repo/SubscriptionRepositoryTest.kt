package com.navpanchang.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navpanchang.data.db.NavPanchangDb
import com.navpanchang.data.db.entities.EventDefinitionEntity
import com.navpanchang.data.db.entities.SubscriptionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room-integration tests for [SubscriptionRepository]. Focus on the repository's
 * responsibility to:
 *  * Only return non-deprecated events from [SubscriptionRepository.getAllAvailableDefinitions].
 *  * Only return ENABLED, non-deprecated events from [SubscriptionRepository.getEnabledDefinitions].
 *  * Preserve existing subscription fields when [SubscriptionRepository.setEnabled]
 *    toggles the `enabled` flag (Planner/Observer/Parana sub-toggles should survive).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SubscriptionRepositoryTest {

    private lateinit var db: NavPanchangDb
    private lateinit var repository: SubscriptionRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NavPanchangDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SubscriptionRepository(
            subscriptionDao = db.subscriptionDao(),
            eventDefinitionDao = db.eventDefinitionDao()
        )

        // Seed three events, one deprecated.
        runTest {
            db.eventDefinitionDao().upsertAll(
                listOf(
                    eventDefinition("shukla_ekadashi"),
                    eventDefinition("purnima"),
                    eventDefinition("legacy_event", deprecated = true)
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getAllAvailableDefinitions excludes deprecated events`() = runTest {
        val defs = repository.getAllAvailableDefinitions()
        assertEquals(2, defs.size)
        val ids = defs.map { it.id }
        assertTrue(ids.contains("shukla_ekadashi"))
        assertTrue(ids.contains("purnima"))
        assertTrue("Deprecated should be hidden", !ids.contains("legacy_event"))
    }

    @Test
    fun `getEnabledDefinitions returns only enabled subscriptions`() = runTest {
        repository.setEnabled("shukla_ekadashi", true)
        repository.setEnabled("purnima", false) // Disabled

        val enabled = repository.getEnabledDefinitions()
        assertEquals(1, enabled.size)
        assertEquals("shukla_ekadashi", enabled.first().id)
    }

    @Test
    fun `setEnabled preserves custom planner time and sub-toggle state`() = runTest {
        // First enable with customizations.
        db.subscriptionDao().upsert(
            SubscriptionEntity(
                eventId = "shukla_ekadashi",
                enabled = true,
                customPlannerHhmm = "18:45",
                customSoundId = "ritual_sankh",
                plannerEnabled = true,
                observerEnabled = true,
                paranaEnabled = false
            )
        )

        // Disable via the repo.
        repository.setEnabled("shukla_ekadashi", enabled = false)

        val after = db.subscriptionDao().getById("shukla_ekadashi")!!
        assertEquals(false, after.enabled)
        assertEquals("User's custom time must survive the disable", "18:45", after.customPlannerHhmm)
        assertEquals("User's sound preference must survive", "ritual_sankh", after.customSoundId)
        assertEquals("Parana off preference must survive", false, after.paranaEnabled)
    }

    @Test
    fun `getEnabledDefinitions excludes subscriptions pointing at deprecated events`() = runTest {
        db.subscriptionDao().upsert(
            SubscriptionEntity(eventId = "legacy_event", enabled = true)
        )
        val enabled = repository.getEnabledDefinitions()
        assertTrue(
            "A subscription to a deprecated event should not be returned as an enabled definition",
            enabled.none { it.id == "legacy_event" }
        )
    }

    @Test
    fun `setEnabled on a brand new event creates the subscription row`() = runTest {
        repository.setEnabled("purnima", true)
        val row = db.subscriptionDao().getById("purnima")
        assertEquals(true, row?.enabled)
    }

    private fun eventDefinition(id: String, deprecated: Boolean = false) = EventDefinitionEntity(
        id = id,
        seedVersion = 1,
        nameEn = id,
        nameHi = id,
        category = "vrat",
        ruleType = "TithiAtSunrise",
        ruleParamsJson = """{"tithiIndex": 11, "vratLogic": true}""",
        vratLogic = true,
        observeInAdhik = true,
        hasParana = false,
        defaultPlannerTimeHhmm = "20:00",
        defaultObserverAnchor = "SUNRISE",
        defaultSoundId = "ritual_temple_bell",
        deprecated = deprecated
    )
}

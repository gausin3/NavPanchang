package com.navpanchang.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navpanchang.alarms.AlarmKind
import com.navpanchang.data.db.NavPanchangDb
import com.navpanchang.data.db.entities.EventDefinitionEntity
import com.navpanchang.data.db.entities.OccurrenceEntity
import com.navpanchang.data.db.entities.ScheduledAlarmEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room-integration tests for [AlarmRepository]. Validates the two workflows that
 * [com.navpanchang.alarms.BootReceiver] and [com.navpanchang.alarms.LocaleChangeReceiver]
 * depend on: querying pending alarms (optionally filtered by kind) and pruning expired rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AlarmRepositoryTest {

    private lateinit var db: NavPanchangDb
    private lateinit var repository: AlarmRepository

    private var lastOccurrenceId: Long = 0

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NavPanchangDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AlarmRepository(db.alarmDao())

        runTest {
            db.eventDefinitionDao().upsertAll(
                listOf(
                    EventDefinitionEntity(
                        id = "shukla_ekadashi",
                        seedVersion = 1,
                        nameEn = "Shukla Ekadashi",
                        nameHi = "शुक्ल एकादशी",
                        category = "vrat",
                        ruleType = "TithiAtSunrise",
                        ruleParamsJson = """{"tithiIndex": 11, "vratLogic": true}""",
                        vratLogic = true,
                        observeInAdhik = true,
                        hasParana = true,
                        defaultPlannerTimeHhmm = "20:00",
                        defaultObserverAnchor = "SUNRISE",
                        defaultSoundId = "ritual_temple_bell"
                    )
                )
            )

            // Two occurrences: one in-window, one past. We'll attach alarms to both.
            db.occurrenceDao().upsertAll(
                listOf(
                    occurrence(dateLocal = "2024-06-01", sunrise = 1_717_200_000_000L),
                    occurrence(dateLocal = "2024-04-01", sunrise = 1_711_900_000_000L)
                )
            )
            lastOccurrenceId = db.occurrenceDao().getUpcomingForEvent(
                "shukla_ekadashi", "2000-01-01", 10
            ).maxOf { it.id }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------
    // Pending queries.
    // ------------------------------------------------------------------

    @Test
    fun `getPending returns only alarms in the future`() = runTest {
        repository.upsert(alarm(1, fireAt = 1_000L, AlarmKind.PLANNER))
        repository.upsert(alarm(2, fireAt = 5_000L, AlarmKind.OBSERVER))
        repository.upsert(alarm(3, fireAt = 10_000L, AlarmKind.PARANA))

        val pending = repository.getPending(nowUtc = 4_000L)
        assertEquals("2 alarms should still be in the future", 2, pending.size)
        assertTrue(pending.all { it.fireAtUtc > 4_000L })
    }

    @Test
    fun `getPending returns alarms sorted by fireAt`() = runTest {
        repository.upsert(alarm(1, fireAt = 30_000L, AlarmKind.PARANA))
        repository.upsert(alarm(2, fireAt = 10_000L, AlarmKind.PLANNER))
        repository.upsert(alarm(3, fireAt = 20_000L, AlarmKind.OBSERVER))

        val pending = repository.getPending(nowUtc = 0L)
        assertEquals(3, pending.size)
        val fireTimes = pending.map { it.fireAtUtc }
        assertEquals(fireTimes, fireTimes.sorted())
    }

    @Test
    fun `getPendingByKind filters by kind name`() = runTest {
        repository.upsert(alarm(1, fireAt = 10_000L, AlarmKind.PLANNER))
        repository.upsert(alarm(2, fireAt = 20_000L, AlarmKind.OBSERVER))
        repository.upsert(alarm(3, fireAt = 30_000L, AlarmKind.PLANNER))

        val planners = repository.getPendingByKind("PLANNER", nowUtc = 0L)
        assertEquals(2, planners.size)
        assertTrue(planners.all { it.kind == "PLANNER" })
    }

    // ------------------------------------------------------------------
    // Upsert replaces by primary key.
    // ------------------------------------------------------------------

    @Test
    fun `upsert replaces an existing row with the same request code`() = runTest {
        val requestCode = 42
        repository.upsert(
            ScheduledAlarmEntity(
                requestCode = requestCode,
                occurrenceId = lastOccurrenceId,
                kind = "PLANNER",
                fireAtUtc = 1_000L,
                channelId = "channel_event_reminders"
            )
        )
        repository.upsert(
            ScheduledAlarmEntity(
                requestCode = requestCode,
                occurrenceId = lastOccurrenceId,
                kind = "PLANNER",
                fireAtUtc = 2_000L, // different fire time
                channelId = "channel_event_reminders"
            )
        )
        val all = repository.getPending(nowUtc = 0L)
        assertEquals("Upsert should replace, not add", 1, all.size)
        assertEquals(2_000L, all.first().fireAtUtc)
    }

    // ------------------------------------------------------------------
    // Deletion.
    // ------------------------------------------------------------------

    @Test
    fun `deleteByRequestCode removes only the targeted row`() = runTest {
        repository.upsert(alarm(1, fireAt = 1_000L, AlarmKind.PLANNER))
        repository.upsert(alarm(2, fireAt = 2_000L, AlarmKind.OBSERVER))

        repository.deleteByRequestCode(AlarmScheduler_requestCodeFor(1, AlarmKind.PLANNER))

        val remaining = repository.getPending(0L)
        assertEquals(1, remaining.size)
        assertEquals("OBSERVER", remaining.first().kind)
    }

    @Test
    fun `pruneExpired removes rows whose fire time is in the past`() = runTest {
        repository.upsert(alarm(1, fireAt = 1_000L, AlarmKind.PLANNER))
        repository.upsert(alarm(2, fireAt = 5_000L, AlarmKind.OBSERVER))
        repository.upsert(alarm(3, fireAt = 10_000L, AlarmKind.PARANA))

        repository.pruneExpired(nowUtc = 6_000L)

        val remaining = repository.getPending(0L)
        assertEquals(1, remaining.size)
        assertEquals(10_000L, remaining.first().fireAtUtc)
    }

    @Test
    fun `pruneExpired on empty table is a no-op`() = runTest {
        repository.pruneExpired(nowUtc = 1_000L)
        assertTrue(repository.getPending(0L).isEmpty())
    }

    @Test
    fun `deleteByRequestCode for missing row is a no-op`() = runTest {
        repository.deleteByRequestCode(999)
        assertNull(repository.getPending(0L).firstOrNull())
    }

    // ------------------------------------------------------------------
    // Helpers — kept at the bottom so the test body reads top to bottom.
    // ------------------------------------------------------------------

    private fun alarm(
        occId: Int,
        fireAt: Long,
        kind: AlarmKind
    ): ScheduledAlarmEntity = ScheduledAlarmEntity(
        requestCode = AlarmScheduler_requestCodeFor(occId.toLong(), kind),
        occurrenceId = lastOccurrenceId,
        kind = kind.name,
        fireAtUtc = fireAt,
        channelId = "channel_event_reminders"
    )

    /**
     * Local alias so the helper doesn't shadow the production import. Compute the
     * stable request code the same way [com.navpanchang.alarms.AlarmScheduler] does.
     */
    private fun AlarmScheduler_requestCodeFor(occurrenceId: Long, kind: AlarmKind): Int =
        (occurrenceId * 3 + kind.ordinal).toInt()

    private fun occurrence(dateLocal: String, sunrise: Long): OccurrenceEntity =
        OccurrenceEntity(
            id = 0,
            eventId = "shukla_ekadashi",
            dateLocal = dateLocal,
            sunriseUtc = sunrise,
            observanceUtc = sunrise,
            paranaStartUtc = sunrise + 86_400_000,
            paranaEndUtc = sunrise + 86_400_000 + 3_600_000,
            shiftedDueToViddha = false,
            isKshayaContext = false,
            lunarMonth = "Vaisakha",
            isAdhik = false,
            locationTag = "HOME",
            isHighPrecision = false,
            computedAt = 0L
        )
}

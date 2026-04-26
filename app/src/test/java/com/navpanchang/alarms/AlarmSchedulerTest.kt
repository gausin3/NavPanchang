package com.navpanchang.alarms

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navpanchang.data.db.NavPanchangDb
import com.navpanchang.data.db.entities.EventDefinitionEntity
import com.navpanchang.data.db.entities.OccurrenceEntity
import com.navpanchang.data.db.entities.SubscriptionEntity
import com.navpanchang.data.repo.AlarmRepository
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.data.repo.OccurrenceRepository
import com.navpanchang.panchang.EventCategory
import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.EventRule
import com.navpanchang.panchang.LunarMonth
import com.navpanchang.panchang.Occurrence
import com.navpanchang.panchang.ObserverAnchor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Robolectric tests for [AlarmScheduler] with `ShadowAlarmManager`. Validates the
 * full scheduling path end-to-end: fire-time computation → AlarmManager.set →
 * scheduled_alarms row creation → LateSubscriptionGate behavior → cancel-for-occurrence.
 *
 * Shadow-only; no real alarms fire. We inspect `shadowOf(alarmManager).scheduledAlarms`
 * to confirm the correct number of alarms were posted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var db: NavPanchangDb
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var occurrenceRepository: OccurrenceRepository
    private lateinit var metadataRepository: MetadataRepository
    private lateinit var scheduler: AlarmScheduler

    private val zoneLucknow: ZoneId = ZoneId.of("Asia/Kolkata")

    private val shuklaEkadashi = EventDefinition(
        id = "shukla_ekadashi",
        nameEn = "Shukla Ekadashi",
        nameHi = "शुक्ल एकादशी",
        category = EventCategory.VRAT,
        rule = EventRule.TithiAtSunrise(tithiIndex = 11, vratLogic = true),
        observeInAdhik = true,
        hasParana = true,
        defaultPlannerTimeHhmm = "20:00",
        defaultObserverAnchor = ObserverAnchor.SUNRISE,
        defaultSoundId = "ritual_temple_bell"
    )

    private val purnima = EventDefinition(
        id = "purnima",
        nameEn = "Purnima",
        nameHi = "पूर्णिमा",
        category = EventCategory.VRAT,
        rule = EventRule.TithiAtSunrise(tithiIndex = 15, vratLogic = false),
        observeInAdhik = true,
        hasParana = false,
        defaultPlannerTimeHhmm = "20:00",
        defaultObserverAnchor = ObserverAnchor.SUNRISE,
        defaultSoundId = "ritual_bell_toll"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NavPanchangDb::class.java)
            .allowMainThreadQueries()
            .build()
        alarmRepository = AlarmRepository(db.alarmDao())
        occurrenceRepository = OccurrenceRepository(db.occurrenceDao())
        metadataRepository = MetadataRepository(db.metadataDao(), context)
        scheduler = AlarmScheduler(context, alarmRepository, occurrenceRepository, metadataRepository)

        // ShadowAlarmManager defaults vary across Robolectric versions. Pin the exact-
        // alarm permission to granted so our scheduler's gate doesn't short-circuit.
        // Method signature isn't stable across versions, so invoke reflectively.
        val am = context.getSystemService<AlarmManager>()!!
        val shadow = shadowOf(am)
        runCatching {
            shadow.javaClass
                .getMethod("setCanScheduleExactAlarms", Boolean::class.javaPrimitiveType)
                .invoke(shadow, true)
        }

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
            metadataRepository.upsertInitial("Lucknow", 26.8467, 80.9462, "Asia/Kolkata")

            // Insert a placeholder OccurrenceEntity with id=100 to satisfy the
            // scheduled_alarms → occurrences foreign key. The test's fixture values
            // (sunriseUtc, paranaStartUtc, etc.) are ignored by the scheduler because
            // callers pass an `Occurrence` domain object directly; only the FK target
            // must exist.
            db.occurrenceDao().upsertAll(
                listOf(
                    OccurrenceEntity(
                        id = 100L,
                        eventId = "shukla_ekadashi",
                        dateLocal = "2099-12-31",
                        sunriseUtc = 0L,
                        observanceUtc = 0L,
                        locationTag = "HOME",
                        isHighPrecision = false,
                        computedAt = 0L
                    )
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------
    // scheduleForOccurrence — full path.
    // ------------------------------------------------------------------

    @Test
    fun `Ekadashi occurrence schedules PLANNER OBSERVER and PARANA alarms`() = runTest {
        val occurrence = occurrenceFarInFuture()
        val subscription = SubscriptionEntity(
            eventId = "shukla_ekadashi",
            enabled = true,
            plannerEnabled = true,
            observerEnabled = true,
            paranaEnabled = true
        )

        val scheduled = scheduler.scheduleForOccurrence(
            occurrenceId = 100L,
            occurrence = occurrence,
            event = shuklaEkadashi,
            subscription = subscription,
            nowUtc = BASE_NOW
        )

        assertEquals("3 alarms should be scheduled", 3, scheduled)

        // All three rows should be persisted in scheduled_alarms.
        val pending = alarmRepository.getPending(nowUtc = 0L)
        assertEquals(3, pending.size)

        val kinds = pending.map { it.kind }.toSet()
        assertEquals(
            setOf(AlarmKind.PLANNER.name, AlarmKind.OBSERVER.name, AlarmKind.PARANA.name),
            kinds
        )

        // ShadowAlarmManager should have 3 scheduled alarms.
        val am = context.getSystemService<AlarmManager>()!!
        val shadow = shadowOf(am)
        assertEquals(3, shadow.scheduledAlarms.size)
    }

    @Test
    fun `PLANNER fire time is 8pm local the day before observation`() = runTest {
        val occurrence = occurrenceFarInFuture()
        scheduler.scheduleForOccurrence(
            occurrenceId = 100L,
            occurrence = occurrence,
            event = shuklaEkadashi,
            subscription = enabledSubscription(),
            nowUtc = BASE_NOW
        )
        val planners = alarmRepository.getPendingByKind("PLANNER", 0L)
        assertEquals(1, planners.size)
        val fireAt = planners.first().fireAtUtc
        val expected = ZonedDateTime
            .of(occurrence.dateLocal.minusDays(1), java.time.LocalTime.of(20, 0), zoneLucknow)
            .toInstant().toEpochMilli()
        assertEquals(expected, fireAt)
    }

    @Test
    fun `custom planner hhmm overrides the event default`() = runTest {
        val occurrence = occurrenceFarInFuture()
        val sub = enabledSubscription().copy(customPlannerHhmm = "18:30")
        scheduler.scheduleForOccurrence(100L, occurrence, shuklaEkadashi, sub, BASE_NOW)

        val planner = alarmRepository.getPendingByKind("PLANNER", 0L).first()
        val fireTime = java.time.Instant.ofEpochMilli(planner.fireAtUtc).atZone(zoneLucknow)
        assertEquals(18, fireTime.hour)
        assertEquals(30, fireTime.minute)
    }

    @Test
    fun `Purnima has no PARANA alarm`() = runTest {
        val occurrence = occurrenceFarInFuture().copy(
            eventId = "purnima",
            paranaStartUtc = null,
            paranaEndUtc = null
        )
        val scheduled = scheduler.scheduleForOccurrence(
            occurrenceId = 100L,
            occurrence = occurrence,
            event = purnima,
            subscription = SubscriptionEntity(
                eventId = "purnima",
                enabled = true,
                paranaEnabled = true // User said yes but the event has no Parana
            ),
            nowUtc = BASE_NOW
        )
        assertEquals("2 alarms (PLANNER + OBSERVER), no PARANA", 2, scheduled)
    }

    @Test
    fun `observer alarm uses the ritual channel matching the sound choice`() = runTest {
        val occurrence = occurrenceFarInFuture()
        val sub = enabledSubscription().copy(customSoundId = "ritual_sankh")
        scheduler.scheduleForOccurrence(100L, occurrence, shuklaEkadashi, sub, BASE_NOW)

        val observer = alarmRepository.getPendingByKind("OBSERVER", 0L).first()
        assertEquals(NotificationChannels.RITUAL_SANKH, observer.channelId)
    }

    // ------------------------------------------------------------------
    // LateSubscriptionGate integration — Planner in the past is skipped.
    // ------------------------------------------------------------------

    @Test
    fun `Planner in the past writes READY_FOR_TOMORROW status without scheduling`() = runTest {
        // Occurrence tomorrow at 8 AM local, but it's already 10 PM today → 8 PM Planner
        // yesterday has already passed.
        val occurrenceDate = LocalDate.now(zoneLucknow).plusDays(1)
        val sunriseNextDay = ZonedDateTime
            .of(occurrenceDate, java.time.LocalTime.of(6, 0), zoneLucknow)
            .toInstant().toEpochMilli()
        val occurrence = Occurrence(
            eventId = "shukla_ekadashi",
            dateLocal = occurrenceDate,
            sunriseUtc = sunriseNextDay,
            observanceUtc = sunriseNextDay,
            paranaStartUtc = sunriseNextDay + 86_400_000,
            paranaEndUtc = sunriseNextDay + 86_400_000 + 3_600_000,
            shiftedDueToViddha = false,
            isKshayaContext = false,
            lunarMonth = LunarMonth.Vaisakha,
            isAdhik = false,
            locationTag = "HOME",
            isHighPrecision = false
        )

        // "Now" = 10 PM today local, AFTER the 8 PM planner threshold.
        val now = ZonedDateTime
            .of(LocalDate.now(zoneLucknow), java.time.LocalTime.of(22, 0), zoneLucknow)
            .toInstant().toEpochMilli()

        val scheduled = scheduler.scheduleForOccurrence(
            occurrenceId = 100L,
            occurrence = occurrence,
            event = shuklaEkadashi,
            subscription = enabledSubscription(),
            nowUtc = now
        )

        // Planner skipped; Observer + Parana still scheduled.
        assertEquals(2, scheduled)

        val planner = alarmRepository.getPendingByKind("PLANNER", 0L).firstOrNull()
        assertEquals(
            "Planner should be persisted with READY_FOR_TOMORROW status, not scheduled",
            AlarmScheduler.PENDING_READY_FOR_TOMORROW, planner?.pendingStatus
        )
    }

    // ------------------------------------------------------------------
    // Per-sub-alarm toggles.
    // ------------------------------------------------------------------

    @Test
    fun `disabling plannerEnabled skips the planner only`() = runTest {
        val occurrence = occurrenceFarInFuture()
        val sub = enabledSubscription().copy(plannerEnabled = false)
        val scheduled = scheduler.scheduleForOccurrence(100L, occurrence, shuklaEkadashi, sub, BASE_NOW)
        assertEquals(2, scheduled)
        assertTrue(alarmRepository.getPendingByKind("PLANNER", 0L).isEmpty())
    }

    @Test
    fun `disabling paranaEnabled skips parana only`() = runTest {
        val occurrence = occurrenceFarInFuture()
        val sub = enabledSubscription().copy(paranaEnabled = false)
        val scheduled = scheduler.scheduleForOccurrence(100L, occurrence, shuklaEkadashi, sub, BASE_NOW)
        assertEquals(2, scheduled)
        assertTrue(alarmRepository.getPendingByKind("PARANA", 0L).isEmpty())
    }

    // ------------------------------------------------------------------
    // Cancellation.
    // ------------------------------------------------------------------

    @Test
    fun `cancelForOccurrence removes every scheduled_alarms row for that id`() = runTest {
        val occurrence = occurrenceFarInFuture()
        scheduler.scheduleForOccurrence(100L, occurrence, shuklaEkadashi, enabledSubscription(), BASE_NOW)
        assertEquals(3, alarmRepository.getPending(0L).size)

        scheduler.cancelForOccurrence(100L)
        assertTrue(alarmRepository.getPending(0L).isEmpty())
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private fun occurrenceFarInFuture(): Occurrence {
        // Ekadashi 60 days after BASE_NOW — far enough that the Planner (T-1d 20:00)
        // is unambiguously in the future, close enough that it sits inside
        // AlarmScheduler.ALARM_HORIZON_DAYS (90d). Anchoring to BASE_NOW rather than
        // LocalDate.now() keeps the test deterministic as real wall-clock drifts.
        val baseDate = java.time.Instant.ofEpochMilli(BASE_NOW).atZone(zoneLucknow).toLocalDate()
        val date = baseDate.plusDays(60)
        val sunrise = ZonedDateTime
            .of(date, java.time.LocalTime.of(5, 30), zoneLucknow)
            .toInstant().toEpochMilli()
        return Occurrence(
            eventId = "shukla_ekadashi",
            dateLocal = date,
            sunriseUtc = sunrise,
            observanceUtc = sunrise,
            paranaStartUtc = sunrise + 86_400_000,
            paranaEndUtc = sunrise + 86_400_000 + 3_600_000,
            shiftedDueToViddha = false,
            isKshayaContext = false,
            lunarMonth = LunarMonth.Vaisakha,
            isAdhik = false,
            locationTag = "HOME",
            isHighPrecision = false
        )
    }

    private fun enabledSubscription() = SubscriptionEntity(
        eventId = "shukla_ekadashi",
        enabled = true,
        plannerEnabled = true,
        observerEnabled = true,
        paranaEnabled = true
    )

    /**
     * [MetadataRepository] doesn't expose a one-shot setter that doesn't reset
     * `lastHomeCalcAt`, so we add a tiny extension for the test setup.
     */
    private suspend fun MetadataRepository.upsertInitial(
        name: String, lat: Double, lon: Double, tz: String
    ) {
        setHomeCity(name, lat, lon, tz)
    }

    companion object {
        /** Fixed "now" in 2024 — irrelevant to the test since we use occurrenceFarInFuture. */
        private const val BASE_NOW = 1_713_000_000_000L
    }
}

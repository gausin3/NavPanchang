package com.navpanchang.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navpanchang.data.db.NavPanchangDb
import com.navpanchang.data.db.entities.EventDefinitionEntity
import com.navpanchang.panchang.LunarMonth
import com.navpanchang.panchang.Occurrence
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Room-integration unit tests for [OccurrenceRepository]. Uses Robolectric to provide an
 * Android Context on the host JVM, and an in-memory Room database.
 *
 * Focus: verify the two-tier query precedence — when a `CURRENT` (high-precision) row
 * exists for the same event+date as a `HOME` row, the repository MUST return the CURRENT
 * row. This is the core correctness invariant of the travel-aware lookahead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class OccurrenceRepositoryTest {

    private lateinit var db: NavPanchangDb
    private lateinit var repository: OccurrenceRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NavPanchangDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OccurrenceRepository(db.occurrenceDao())

        // Seed one event definition so the foreign-key constraint on occurrences is satisfied.
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
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------------
    // Query precedence: CURRENT > HOME for the same date.
    // ------------------------------------------------------------------

    @Test
    fun `getNextOccurrence prefers CURRENT tier over HOME for same date`() = runTest {
        val date = LocalDate.of(2024, 5, 19)

        // Same event+date in both tiers. CURRENT has a slightly different sunrise time
        // (simulating Dubai vs Lucknow).
        val homeOccurrence = occurrence(date, sunriseUtc = 1_000_000_000L, tag = "HOME", high = false)
        val currentOccurrence = occurrence(date, sunriseUtc = 1_000_300_000L, tag = "CURRENT", high = true)

        repository.replaceWindow("HOME", listOf(homeOccurrence), computedAtUtc = 0L)
        repository.replaceWindow("CURRENT", listOf(currentOccurrence), computedAtUtc = 0L)

        val result = repository.getNextOccurrence("shukla_ekadashi", LocalDate.of(2024, 5, 1))
        assertNotNull("Expected a result", result)
        assertEquals("CURRENT row should win", "CURRENT", result!!.locationTag)
        assertEquals(1_000_300_000L, result.sunriseUtc)
    }

    @Test
    fun `getNextOccurrence falls back to HOME when no CURRENT exists for that date`() = runTest {
        val home = occurrence(LocalDate.of(2024, 5, 19), 1_000_000_000L, "HOME", false)
        repository.replaceWindow("HOME", listOf(home), computedAtUtc = 0L)

        val result = repository.getNextOccurrence("shukla_ekadashi", LocalDate.of(2024, 5, 1))
        assertNotNull(result)
        assertEquals("HOME", result!!.locationTag)
    }

    @Test
    fun `getNextOccurrence returns null when no rows exist at all`() = runTest {
        val result = repository.getNextOccurrence("shukla_ekadashi", LocalDate.of(2024, 5, 1))
        assertNull(result)
    }

    @Test
    fun `getNextOccurrence skips dates before today`() = runTest {
        val past = occurrence(LocalDate.of(2024, 4, 1), 900_000_000L, "HOME", false)
        val future = occurrence(LocalDate.of(2024, 5, 19), 1_000_000_000L, "HOME", false)
        repository.replaceWindow("HOME", listOf(past, future), computedAtUtc = 0L)

        val result = repository.getNextOccurrence("shukla_ekadashi", LocalDate.of(2024, 5, 1))
        assertNotNull(result)
        assertEquals(LocalDate.of(2024, 5, 19), result!!.dateLocal)
    }

    // ------------------------------------------------------------------
    // replaceWindow semantics.
    // ------------------------------------------------------------------

    @Test
    fun `replaceWindow clears existing rows with the same tag`() = runTest {
        val initial = listOf(
            occurrence(LocalDate.of(2024, 5, 19), 1_000_000_000L, "HOME", false),
            occurrence(LocalDate.of(2024, 6, 18), 1_100_000_000L, "HOME", false)
        )
        repository.replaceWindow("HOME", initial, computedAtUtc = 0L)

        // Now replace with a completely different set.
        val replaced = listOf(
            occurrence(LocalDate.of(2024, 7, 17), 1_200_000_000L, "HOME", false)
        )
        repository.replaceWindow("HOME", replaced, computedAtUtc = 0L)

        val upcoming = repository.getUpcomingForEvent(
            "shukla_ekadashi", LocalDate.of(2024, 5, 1), limit = 10
        )
        assertEquals("Only the replacement row should remain", 1, upcoming.size)
        assertEquals(LocalDate.of(2024, 7, 17), upcoming.first().dateLocal)
    }

    @Test
    fun `replaceWindow HOME does not touch CURRENT rows`() = runTest {
        val home = occurrence(LocalDate.of(2024, 5, 19), 1_000_000_000L, "HOME", false)
        val current = occurrence(LocalDate.of(2024, 5, 19), 1_000_300_000L, "CURRENT", true)
        repository.replaceWindow("HOME", listOf(home), 0L)
        repository.replaceWindow("CURRENT", listOf(current), 0L)

        // Replace HOME only.
        val newHome = occurrence(LocalDate.of(2024, 6, 18), 1_100_000_000L, "HOME", false)
        repository.replaceWindow("HOME", listOf(newHome), 0L)

        // CURRENT should still be findable via the next-occurrence query.
        val result = repository.getNextOccurrence("shukla_ekadashi", LocalDate.of(2024, 5, 1))
        assertNotNull(result)
        assertEquals("CURRENT row should still exist after HOME replace", "CURRENT", result!!.locationTag)
    }

    @Test
    fun `replaceWindow asserts every occurrence carries the matching tag`() = runTest {
        val mismatched = occurrence(LocalDate.of(2024, 5, 19), 1_000_000_000L, "HOME", false)
        val thrown = runCatching {
            repository.replaceWindow("CURRENT", listOf(mismatched), 0L)
        }.exceptionOrNull()
        assertTrue(
            "Expected IllegalArgumentException, got $thrown",
            thrown is IllegalArgumentException
        )
    }

    // ------------------------------------------------------------------
    // Pruning.
    // ------------------------------------------------------------------

    @Test
    fun `pruneBefore deletes all rows older than cutoff`() = runTest {
        val rows = listOf(
            occurrence(LocalDate.of(2024, 1, 1), 900_000_000L, "HOME", false),
            occurrence(LocalDate.of(2024, 5, 19), 1_000_000_000L, "HOME", false),
            occurrence(LocalDate.of(2024, 8, 15), 1_100_000_000L, "HOME", false)
        )
        repository.replaceWindow("HOME", rows, 0L)

        repository.pruneBefore(LocalDate.of(2024, 5, 1))

        val remaining = repository.getUpcomingForEvent(
            "shukla_ekadashi", LocalDate.of(2023, 1, 1), limit = 10
        )
        assertEquals(2, remaining.size)
        assertTrue(remaining.none { it.dateLocal.isBefore(LocalDate.of(2024, 5, 1)) })
    }

    // ------------------------------------------------------------------
    // Helper.
    // ------------------------------------------------------------------

    private fun occurrence(
        date: LocalDate,
        sunriseUtc: Long,
        tag: String,
        high: Boolean
    ): Occurrence = Occurrence(
        eventId = "shukla_ekadashi",
        dateLocal = date,
        sunriseUtc = sunriseUtc,
        observanceUtc = sunriseUtc,
        paranaStartUtc = sunriseUtc + 86_400_000,
        paranaEndUtc = sunriseUtc + 86_400_000 + 3_600_000,
        shiftedDueToViddha = false,
        isKshayaContext = false,
        lunarMonth = LunarMonth.Vaisakha,
        isAdhik = false,
        locationTag = tag,
        isHighPrecision = high
    )
}

package com.navpanchang.ui.subscriptions

import com.navpanchang.panchang.EventCategory
import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.EventRule
import com.navpanchang.panchang.LunarMonth
import com.navpanchang.panchang.ObserverAnchor
import com.navpanchang.panchang.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure unit tests for [PreparationCardState.from] — the yellow→green→cyan→hidden state
 * machine that drives the Home screen's preparation card.
 *
 * All inputs are synthetic (no ephemeris, no DB), so we can pin expectations against
 * wall-clock offsets without worrying about test flakiness on CI.
 */
class PreparationCardStateTest {

    private val ekadashi = EventDefinition(
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

    // ------------------------------------------------------------------
    // Hidden: nothing near in time.
    // ------------------------------------------------------------------

    @Test
    fun `hidden when there are no candidates`() {
        val state = PreparationCardState.from(nowUtc = NOW, candidates = emptyList())
        assertEquals(PreparationCardState.Hidden, state)
    }

    @Test
    fun `hidden when the nearest event is more than 36 hours away`() {
        val twoDaysAway = NOW + 48 * HOUR
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(occurrenceAt(twoDaysAway) to ekadashi)
        )
        assertEquals(PreparationCardState.Hidden, state)
    }

    // ------------------------------------------------------------------
    // Preparing: yellow, event within 36 hours but not yet started.
    // ------------------------------------------------------------------

    @Test
    fun `preparing when event is 12 hours away`() {
        val twelveHoursOut = NOW + 12 * HOUR
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(occurrenceAt(twelveHoursOut) to ekadashi)
        )
        assertTrue(state is PreparationCardState.Preparing)
        val prep = state as PreparationCardState.Preparing
        assertEquals(12L, prep.hoursUntilSunrise)
        assertEquals(ekadashi, prep.event)
    }

    @Test
    fun `preparing at the very edge of the 36 hour window`() {
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(occurrenceAt(NOW + 36 * HOUR) to ekadashi)
        )
        assertTrue(state is PreparationCardState.Preparing)
    }

    @Test
    fun `preparing picks the nearest of multiple candidates`() {
        val near = occurrenceAt(NOW + 10 * HOUR)
        val far = occurrenceAt(NOW + 30 * HOUR)
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(far to ekadashi, near to ekadashi)
        )
        val prep = state as PreparationCardState.Preparing
        assertEquals(near, prep.occurrence)
    }

    // ------------------------------------------------------------------
    // Observing: green, event in progress.
    // ------------------------------------------------------------------

    @Test
    fun `observing when we're between sunrise and Parana end`() {
        val sunrise = NOW - 2 * HOUR // sunrise was 2 hours ago
        val occurrence = occurrenceAt(sunrise).copy(
            paranaEndUtc = NOW + 4 * HOUR // Parana ends in 4 hours
        )
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(occurrence to ekadashi)
        )
        assertTrue(state is PreparationCardState.Observing)
    }

    @Test
    fun `observing when Parana is null and we're within 24 hours of sunrise`() {
        val sunrise = NOW - 6 * HOUR
        val occurrence = occurrenceAt(sunrise).copy(paranaStartUtc = null, paranaEndUtc = null)
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(occurrence to ekadashi)
        )
        assertTrue(state is PreparationCardState.Observing)
    }

    // ------------------------------------------------------------------
    // Parana: cyan, specifically inside the Parana window.
    // ------------------------------------------------------------------

    @Test
    fun `parana when inside the Parana window for an Ekadashi`() {
        val occurrence = occurrenceAt(sunriseUtc = NOW - 24 * HOUR).copy(
            paranaStartUtc = NOW - 1 * HOUR,
            paranaEndUtc = NOW + 2 * HOUR
        )
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(occurrence to ekadashi)
        )
        assertTrue(state is PreparationCardState.Parana)
        val parana = state as PreparationCardState.Parana
        assertEquals(NOW - 1 * HOUR, parana.startUtc)
        assertEquals(NOW + 2 * HOUR, parana.endUtc)
    }

    @Test
    fun `Parana takes priority over Observing and Preparing`() {
        // Active Parana PLUS another near-future event — Parana should win.
        val paranaOccurrence = occurrenceAt(sunriseUtc = NOW - 24 * HOUR).copy(
            paranaStartUtc = NOW - 1 * HOUR,
            paranaEndUtc = NOW + 2 * HOUR
        )
        val nearFuture = occurrenceAt(NOW + 10 * HOUR)
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(nearFuture to ekadashi, paranaOccurrence to ekadashi)
        )
        assertTrue(state is PreparationCardState.Parana)
    }

    @Test
    fun `past Parana window with no other candidate is Hidden`() {
        val occurrence = occurrenceAt(sunriseUtc = NOW - 48 * HOUR).copy(
            paranaStartUtc = NOW - 30 * HOUR,
            paranaEndUtc = NOW - 24 * HOUR
        )
        val state = PreparationCardState.from(
            nowUtc = NOW,
            candidates = listOf(occurrence to ekadashi)
        )
        assertEquals(PreparationCardState.Hidden, state)
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    private fun occurrenceAt(sunriseUtc: Long): Occurrence = Occurrence(
        eventId = "shukla_ekadashi",
        dateLocal = LocalDate.of(2024, 4, 19),
        sunriseUtc = sunriseUtc,
        observanceUtc = sunriseUtc,
        paranaStartUtc = sunriseUtc + 24 * HOUR,
        paranaEndUtc = sunriseUtc + 24 * HOUR + 6 * HOUR,
        shiftedDueToViddha = false,
        isKshayaContext = false,
        lunarMonth = LunarMonth.Vaisakha,
        isAdhik = false,
        locationTag = "HOME",
        isHighPrecision = false
    )

    private companion object {
        /** Fixed "now" — Jan 1 2024 noon UTC. */
        private const val NOW = 1_704_110_400_000L
        private const val HOUR = 3_600_000L
    }
}

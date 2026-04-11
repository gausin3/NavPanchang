package com.navpanchang.ui.subscriptions

import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.Nakshatra
import com.navpanchang.panchang.Occurrence
import com.navpanchang.panchang.Tithi

/**
 * The rendered state of the subscription-first Home screen.
 *
 * Shape mirrors the plan's home-screen mock:
 *  1. [today] — always-visible status card at the top ("Today: Shukla Dashami").
 *  2. [preparationCard] — yellow / green / cyan state card below it.
 *  3. [subscribedEvents] — the main toggle list of enabled events.
 *  4. [availableEvents] — events the user hasn't subscribed to yet (for the
 *     "+ Add more events" picker).
 *  5. [homeCitySet] — when false, render a "Set your home city" CTA instead of the
 *     normal state.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val homeCitySet: Boolean = false,
    val today: TodayStatus? = null,
    val preparationCard: PreparationCardState = PreparationCardState.Hidden,
    val subscribedEvents: List<SubscriptionRowState> = emptyList(),
    val availableEvents: List<EventDefinition> = emptyList()
) {
    companion object {
        val INITIAL = HomeUiState(loading = true)
    }
}

/**
 * Panchang at-a-glance for the current moment — feeds [com.navpanchang.ui.subscriptions.TodayStatusCard].
 */
data class TodayStatus(
    val tithi: Tithi,
    val nakshatra: Nakshatra,
    val sunriseUtc: Long?
)

/**
 * A single row in the subscription list — event definition + the next occurrence we
 * know about. `nextOccurrence` may be `null` if the lookahead hasn't run yet (e.g. on
 * fresh install before the first `RefreshWorker` completes).
 */
data class SubscriptionRowState(
    val event: EventDefinition,
    val enabled: Boolean,
    val nextOccurrence: Occurrence?
)

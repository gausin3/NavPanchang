package com.navpanchang.ui.subscriptions

import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.Occurrence

/**
 * State for [EventDetailScreen] — the per-event settings page reached by tapping a
 * subscription row on [SubscriptionsScreen].
 */
data class EventDetailUiState(
    val loading: Boolean = true,
    val event: EventDefinition? = null,

    /** User's subscription state, nullable while loading or if not-yet-subscribed. */
    val subscriptionEnabled: Boolean = false,
    val plannerEnabled: Boolean = true,
    val observerEnabled: Boolean = true,
    val paranaEnabled: Boolean = true,
    val customPlannerHhmm: String? = null,
    val customSoundId: String? = null,

    /** Next N occurrences for this event — shown as a list of upcoming dates. */
    val upcomingOccurrences: List<Occurrence> = emptyList()
) {
    companion object {
        val INITIAL = EventDetailUiState(loading = true)
        const val UPCOMING_COUNT = 12
    }
}

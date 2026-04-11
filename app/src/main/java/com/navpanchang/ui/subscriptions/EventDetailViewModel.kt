package com.navpanchang.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navpanchang.alarms.RefreshScheduler
import com.navpanchang.data.db.EventDefinitionDao
import com.navpanchang.data.db.SubscriptionDao
import com.navpanchang.data.db.entities.SubscriptionEntity
import com.navpanchang.data.repo.OccurrenceRepository
import com.navpanchang.panchang.EventRuleParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backing ViewModel for [EventDetailScreen]. Reads the event definition + subscription
 * + next N occurrences, and exposes mutators for every user-configurable knob
 * (enabled, planner/observer/parana sub-toggles, custom planner time, sound).
 *
 * Writes only touch the `subscriptions` table — event definitions are read-only from
 * this screen. See `MEMORY.md` §Conventions ("sanctity of user state").
 */
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val subscriptionDao: SubscriptionDao,
    private val eventDefinitionDao: EventDefinitionDao,
    private val occurrenceRepository: OccurrenceRepository,
    private val refreshScheduler: RefreshScheduler
) : ViewModel() {

    private val eventId: String = requireNotNull(savedStateHandle[ARG_EVENT_ID]) {
        "EventDetailViewModel requires an eventId in the SavedStateHandle"
    }

    private val _uiState = MutableStateFlow(EventDetailUiState.INITIAL)
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun onToggleEnabled(enabled: Boolean) = updateSubscription { it.copy(enabled = enabled) }

    fun onTogglePlanner(enabled: Boolean) =
        updateSubscription { it.copy(plannerEnabled = enabled) }

    fun onToggleObserver(enabled: Boolean) =
        updateSubscription { it.copy(observerEnabled = enabled) }

    fun onToggleParana(enabled: Boolean) =
        updateSubscription { it.copy(paranaEnabled = enabled) }

    fun onSetCustomPlannerTime(hhmm: String?) =
        updateSubscription { it.copy(customPlannerHhmm = hhmm) }

    fun onSetCustomSound(soundId: String?) =
        updateSubscription { it.copy(customSoundId = soundId) }

    // ------------------------------------------------------------------
    // Internals.
    // ------------------------------------------------------------------

    private fun updateSubscription(block: (SubscriptionEntity) -> SubscriptionEntity) {
        viewModelScope.launch {
            val existing = subscriptionDao.getById(eventId)
                ?: SubscriptionEntity(eventId = eventId, enabled = false)
            subscriptionDao.upsert(block(existing))
            refresh()
            // Schedule a one-shot refresh so alarm bookkeeping catches up immediately.
            refreshScheduler.enqueueOneShot()
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val defEntity = eventDefinitionDao.getById(eventId) ?: return@withContext
        val event = EventRuleParser.fromEntity(defEntity)
        val subscription = subscriptionDao.getById(eventId)
        val upcoming = occurrenceRepository.getUpcomingForEvent(
            eventId = eventId,
            fromDate = LocalDate.now(),
            limit = EventDetailUiState.UPCOMING_COUNT
        )

        _uiState.value = EventDetailUiState(
            loading = false,
            event = event,
            subscriptionEnabled = subscription?.enabled ?: false,
            plannerEnabled = subscription?.plannerEnabled ?: true,
            observerEnabled = subscription?.observerEnabled ?: true,
            paranaEnabled = subscription?.paranaEnabled ?: true,
            customPlannerHhmm = subscription?.customPlannerHhmm,
            customSoundId = subscription?.customSoundId,
            upcomingOccurrences = upcoming
        )
    }

    companion object {
        const val ARG_EVENT_ID = "eventId"
    }
}

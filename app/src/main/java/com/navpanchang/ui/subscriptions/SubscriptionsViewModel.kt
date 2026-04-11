package com.navpanchang.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navpanchang.alarms.RefreshScheduler
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.data.repo.OccurrenceRepository
import com.navpanchang.data.repo.SubscriptionRepository
import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.Occurrence
import com.navpanchang.panchang.PanchangCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backing ViewModel for [SubscriptionsScreen] — the Home tab.
 *
 * **Reactive composition:**
 *  * Observes [SubscriptionRepository.observeEnabledWithDefinitions] — emits when the
 *    user toggles any subscription.
 *  * Observes [MetadataRepository.observe] — emits when the home city or any calc
 *    metadata changes.
 *  * Ticks every minute via [minuteTicker] so the [PreparationCardState] machine
 *    advances through yellow→green→cyan→hidden without requiring user interaction.
 *
 * Every emission re-reads the "next occurrence" per subscribed event and the current
 * panchang snapshot, then derives the state synchronously. This is cheap — we're doing
 * a handful of DB reads and one Meeus sun+moon calc per tick, well under 50 ms.
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val metadataRepository: MetadataRepository,
    private val panchangCalculator: PanchangCalculator,
    private val refreshScheduler: RefreshScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState.INITIAL)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                subscriptionRepository.observeEnabledWithDefinitions(),
                metadataRepository.observe(),
                minuteTicker()
            ) { enabledPairs, metadata, _ ->
                buildState(enabledPairs, metadata)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onToggleSubscription(eventId: String, enabled: Boolean) {
        viewModelScope.launch {
            subscriptionRepository.setEnabled(eventId, enabled)
            // Immediately refresh so the newly-enabled event's alarms are scheduled
            // without waiting for the next periodic tick.
            refreshScheduler.enqueueOneShot()
        }
    }

    // ------------------------------------------------------------------
    // Private helpers.
    // ------------------------------------------------------------------

    private suspend fun buildState(
        enabled: List<Pair<com.navpanchang.data.db.entities.SubscriptionEntity, EventDefinition>>,
        metadata: com.navpanchang.data.db.entities.CalcMetadataEntity?
    ): HomeUiState = withContext(Dispatchers.Default) {
        val homeCitySet = metadata?.homeLat != null && metadata.homeLon != null

        // Today's panchang — simple snapshot at the current instant.
        val nowUtc = System.currentTimeMillis()
        val ayanamshaType = metadataRepository.ayanamshaType()
        val todaySnapshot = panchangCalculator.computeAtInstant(nowUtc, ayanamshaType)
        val today = TodayStatus(
            tithi = todaySnapshot.tithi,
            nakshatra = todaySnapshot.nakshatra,
            sunriseUtc = null // Phase 5b will compute sunrise for the status card
        )

        // Next occurrence per enabled subscription. These reads come straight from
        // OccurrenceRepository which already applies HIGH_PRECISION-first precedence.
        val todayLocal = LocalDate.now()
        val rows = enabled.map { (_, event) ->
            val next = runCatching {
                occurrenceRepository.getNextOccurrence(event.id, todayLocal)
            }.getOrNull()
            SubscriptionRowState(event = event, enabled = true, nextOccurrence = next)
        }

        // Preparation card state: consider only occurrences we actually have.
        val candidates: List<Pair<Occurrence, EventDefinition>> = rows.mapNotNull { row ->
            row.nextOccurrence?.let { it to row.event }
        }
        val prepState = PreparationCardState.from(nowUtc, candidates)

        // Catalog of events the user could add — everything non-deprecated that they
        // haven't already subscribed to.
        val available = subscriptionRepository.getAllAvailableDefinitions()
            .filter { def -> rows.none { it.event.id == def.id } }

        HomeUiState(
            loading = false,
            homeCitySet = homeCitySet,
            today = today,
            preparationCard = prepState,
            subscribedEvents = rows,
            availableEvents = available
        )
    }

    /**
     * Emits on subscription and then once every [INTERVAL_MILLIS] so the
     * [PreparationCardState] machine can tick past wall-clock thresholds without the
     * user interacting with the screen.
     */
    private fun minuteTicker() = flow {
        while (true) {
            emit(Unit)
            delay(INTERVAL_MILLIS)
        }
    }

    private companion object {
        /** Coarse refresh interval — 60 seconds is enough for state transitions. */
        private const val INTERVAL_MILLIS = 60_000L
    }
}

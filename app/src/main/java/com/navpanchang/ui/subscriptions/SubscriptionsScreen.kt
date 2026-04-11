package com.navpanchang.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navpanchang.R

/**
 * The subscription-first Home screen. Layout top to bottom:
 *
 *  1. [TodayStatusCard] — always visible.
 *  2. [PreparationCard] — yellow/green/cyan state card. Hidden when no subscribed
 *     event is within ~36 hours.
 *  3. Subscription list — one [SubscriptionRow] per enabled event.
 *
 * When the home city hasn't been set yet, renders a single onboarding CTA instead.
 */
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    onEventClick: (eventId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.loading -> LoadingState()
        !uiState.homeCitySet -> HomeCityMissingState()
        else -> SubscriptionsContent(
            state = uiState,
            onToggle = { eventId, enabled -> viewModel.onToggleSubscription(eventId, enabled) },
            onEventClick = onEventClick
        )
    }
}

@Composable
private fun SubscriptionsContent(
    state: HomeUiState,
    onToggle: (String, Boolean) -> Unit,
    onEventClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.today?.let { today ->
            item { TodayStatusCard(today) }
        }

        if (state.preparationCard !is PreparationCardState.Hidden) {
            item { PreparationCard(state.preparationCard) }
        }

        item {
            Text(
                text = stringResource(R.string.home_subscriptions_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.subscribedEvents.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_no_subscriptions),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(state.subscribedEvents, key = { it.event.id }) { row ->
                SubscriptionRow(
                    row = row,
                    onToggle = { enabled -> onToggle(row.event.id, enabled) },
                    onClick = { onEventClick(row.event.id) }
                )
            }
        }

        if (state.availableEvents.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_add_more),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            items(state.availableEvents, key = { it.id }) { event ->
                SubscriptionRow(
                    row = SubscriptionRowState(
                        event = event,
                        enabled = false,
                        nextOccurrence = null
                    ),
                    onToggle = { enabled -> onToggle(event.id, enabled) },
                    onClick = { onEventClick(event.id) }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun HomeCityMissingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_city_missing_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.home_city_missing_body),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

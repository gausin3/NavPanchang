package com.navpanchang.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navpanchang.R
import com.navpanchang.panchang.Occurrence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Detail screen for a single subscribed event. Shows the event's name, the next 12
 * occurrences with Parana windows where applicable, and toggles for the three sub-
 * alarm kinds plus a customization section.
 *
 * Reached by tapping any row on [SubscriptionsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.event?.nameEn ?: stringResource(R.string.event_detail_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }

            state.event != null -> EventDetailContent(
                state = state,
                innerPaddingTop = innerPadding.calculateTopPadding(),
                onToggleEnabled = viewModel::onToggleEnabled,
                onTogglePlanner = viewModel::onTogglePlanner,
                onToggleObserver = viewModel::onToggleObserver,
                onToggleParana = viewModel::onToggleParana
            )
        }
    }
}

@Composable
private fun EventDetailContent(
    state: EventDetailUiState,
    innerPaddingTop: androidx.compose.ui.unit.Dp,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePlanner: (Boolean) -> Unit,
    onToggleObserver: (Boolean) -> Unit,
    onToggleParana: (Boolean) -> Unit
) {
    val event = state.event ?: return
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPaddingTop)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.nameEn, style = MaterialTheme.typography.headlineSmall)
                Text(event.nameHi, style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            ToggleRow(
                label = stringResource(R.string.event_detail_subscription_enabled),
                checked = state.subscriptionEnabled,
                onChange = onToggleEnabled
            )
        }

        item {
            Text(
                stringResource(R.string.event_detail_alarms_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            ToggleRow(
                label = stringResource(R.string.event_detail_planner_toggle),
                checked = state.plannerEnabled,
                onChange = onTogglePlanner,
                enabled = state.subscriptionEnabled
            )
        }

        item {
            ToggleRow(
                label = stringResource(R.string.event_detail_observer_toggle),
                checked = state.observerEnabled,
                onChange = onToggleObserver,
                enabled = state.subscriptionEnabled
            )
        }

        if (event.hasParana) {
            item {
                ToggleRow(
                    label = stringResource(R.string.event_detail_parana_toggle),
                    checked = state.paranaEnabled,
                    onChange = onToggleParana,
                    enabled = state.subscriptionEnabled
                )
            }
        }

        item {
            Text(
                stringResource(R.string.event_detail_upcoming_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (state.upcomingOccurrences.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.event_detail_no_upcoming),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(state.upcomingOccurrences, key = { it.dateLocal.toString() }) { occ ->
                UpcomingOccurrenceRow(occ)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
private fun UpcomingOccurrenceRow(occ: Occurrence) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = occ.dateLocal.format(DATE_FORMATTER),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.event_detail_sunrise_at,
                    Instant.ofEpochMilli(occ.sunriseUtc)
                        .atZone(ZoneId.systemDefault())
                        .format(TIME_FORMATTER)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (occ.paranaStartUtc != null && occ.paranaEndUtc != null) {
                val start = Instant.ofEpochMilli(occ.paranaStartUtc)
                    .atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
                val end = Instant.ofEpochMilli(occ.paranaEndUtc)
                    .atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
                Text(
                    text = stringResource(R.string.event_detail_parana_window, start, end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (occ.shiftedDueToViddha) {
                Text(
                    text = stringResource(R.string.event_detail_shifted_viddha),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (occ.isKshayaContext) {
                Text(
                    text = stringResource(R.string.event_detail_kshaya_context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

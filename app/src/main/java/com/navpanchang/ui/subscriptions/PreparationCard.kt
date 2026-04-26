package com.navpanchang.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.navpanchang.R
import com.navpanchang.ui.theme.StateObservingGreen
import com.navpanchang.ui.theme.StateParanaCyan
import com.navpanchang.ui.theme.StatePreparingYellow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The live preparation card that sits between [TodayStatusCard] and the subscription
 * list. Its colour and copy change as the user's nearest subscribed event moves through
 * its lifecycle — "preparing for tomorrow" → "observing today" → "parana window" → hidden.
 *
 * The state machine itself lives in [PreparationCardState.from]; this composable is a
 * pure render of the current state.
 */
@Composable
fun PreparationCard(
    state: PreparationCardState,
    modifier: Modifier = Modifier
) {
    when (state) {
        PreparationCardState.Hidden -> Unit
        is PreparationCardState.Preparing -> PreparingCardContent(state, modifier)
        is PreparationCardState.Observing -> ObservingCardContent(state, modifier)
        is PreparationCardState.Parana -> ParanaCardContent(state, modifier)
    }
}

@Composable
private fun PreparingCardContent(
    state: PreparationCardState.Preparing,
    modifier: Modifier
) {
    StateCard(
        modifier = modifier,
        color = StatePreparingYellow,
        label = stringResource(R.string.state_preparing_tomorrow),
        primary = state.event.nameEn,
        secondary = state.event.nameHi,
        tertiary = com.navpanchang.util.safeStringResource(
            R.string.state_preparing_sunrise_in,
            state.hoursUntilSunrise
        )
    )
}

@Composable
private fun ObservingCardContent(
    state: PreparationCardState.Observing,
    modifier: Modifier
) {
    StateCard(
        modifier = modifier,
        color = StateObservingGreen,
        label = stringResource(R.string.state_observing_today),
        primary = state.event.nameEn,
        secondary = state.event.nameHi,
        tertiary = formatSunriseLine(state.occurrence.sunriseUtc)
    )
}

@Composable
private fun ParanaCardContent(
    state: PreparationCardState.Parana,
    modifier: Modifier
) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(state.startUtc).atZone(zone).format(TIME_FORMATTER)
    val end = Instant.ofEpochMilli(state.endUtc).atZone(zone).format(TIME_FORMATTER)
    StateCard(
        modifier = modifier,
        color = StateParanaCyan,
        label = stringResource(R.string.state_parana_window),
        primary = state.event.nameEn,
        secondary = state.event.nameHi,
        tertiary = stringResource(R.string.state_parana_between, start, end)
    )
}

@Composable
private fun StateCard(
    modifier: Modifier,
    color: Color,
    label: String,
    primary: String,
    secondary: String,
    tertiary: String
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(primary, style = MaterialTheme.typography.titleLarge)
            Text(secondary, style = MaterialTheme.typography.titleMedium)
            Text(tertiary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun formatSunriseLine(sunriseUtc: Long): String {
    val text = Instant.ofEpochMilli(sunriseUtc)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMATTER)
    return stringResource(R.string.state_observing_sunrise, text)
}

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

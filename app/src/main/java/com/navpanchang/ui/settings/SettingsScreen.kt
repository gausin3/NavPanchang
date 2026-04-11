package com.navpanchang.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navpanchang.R

/**
 * The Settings tab. Lays out the following sections top to bottom:
 *
 *  1. Home City (with description + tap to change)
 *  2. Reliability Check (battery optimization + exact alarms)
 *  3. Sunrise Time Offset slider (-2..+2 minutes)
 *  4. About (AGPL attribution, source link placeholder)
 *
 * Phase 5b/7 — adds the home city picker route, the reliability section, and the
 * sunrise offset slider. Theme picker, language switcher, and Daily Morning Briefing
 * toggle are documented as follow-ups.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onHomeCityClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        HomeCityCard(
            cityName = uiState.homeCityName,
            description = uiState.homeCityDescription,
            onClick = onHomeCityClick
        )

        ReliabilityCheckSection(status = uiState.reliability)

        SunriseOffsetCard(
            initialMinutes = uiState.sunriseOffsetMinutes,
            onChanged = viewModel::onSunriseOffsetChange
        )

        AboutCard(onClick = onAboutClick)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeCityCard(
    cityName: String?,
    description: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_home_city_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = cityName ?: stringResource(R.string.settings_home_city_not_set),
                style = MaterialTheme.typography.bodyLarge
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SunriseOffsetCard(
    initialMinutes: Int,
    onChanged: (Int) -> Unit
) {
    // Local state for the slider — we commit to the ViewModel on every change so the
    // Tier 1 refresh runs. The live value is rendered alongside the slider.
    var current by remember { mutableFloatStateOf(initialMinutes.toFloat()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_sunrise_offset_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_sunrise_offset_value, current.toInt()),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = current,
                onValueChange = { current = it },
                onValueChangeFinished = { onChanged(current.toInt()) },
                valueRange = -2f..2f,
                steps = 3
            )
            Text(
                text = stringResource(R.string.settings_sunrise_offset_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.licensing_summary),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

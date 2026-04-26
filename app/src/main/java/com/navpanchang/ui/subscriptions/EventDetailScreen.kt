package com.navpanchang.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import android.media.MediaPlayer
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.navpanchang.alarms.RitualSound
import com.navpanchang.alarms.RitualSounds
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
                onToggleParana = viewModel::onToggleParana,
                onSetSound = viewModel::onSetCustomSound
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
    onToggleParana: (Boolean) -> Unit,
    onSetSound: (String?) -> Unit
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
            SoundPickerSection(
                event = event,
                currentSoundId = state.customSoundId ?: event.defaultSoundId,
                enabled = state.subscriptionEnabled,
                onSetSound = onSetSound
            )
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
    val dateFormatter = com.navpanchang.util.rememberFormatter("EEE, d MMM yyyy")
    val timeFormatter = com.navpanchang.util.rememberFormatter("h:mm a")
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
                text = occ.dateLocal.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.event_detail_sunrise_at,
                    Instant.ofEpochMilli(occ.sunriseUtc)
                        .atZone(ZoneId.systemDefault())
                        .format(timeFormatter)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (occ.paranaStartUtc != null && occ.paranaEndUtc != null) {
                val start = Instant.ofEpochMilli(occ.paranaStartUtc)
                    .atZone(ZoneId.systemDefault()).format(timeFormatter)
                val end = Instant.ofEpochMilli(occ.paranaEndUtc)
                    .atZone(ZoneId.systemDefault()).format(timeFormatter)
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

/**
 * Per-event ritual sound picker. Five radio rows, one per channel; tapping a row
 * persists the choice via [SubscriptionRepository.setSubscription] (through the
 * ViewModel's `onSetSound` callback). Tapping the play icon plays a foreground
 * preview via [MediaPlayer] — this is a UI feedback action, not the alarm path
 * (alarms still fire via NotificationChannel as designed).
 *
 * Player is held in a Composable-local `MediaPlayer?` and released on dispose.
 * Single-instance: tapping a different preview while one is playing stops the
 * old one first so we don't stack overlapping audio.
 */
@Composable
private fun SoundPickerSection(
    event: com.navpanchang.panchang.EventDefinition,
    currentSoundId: String,
    enabled: Boolean,
    onSetSound: (String?) -> Unit
) {
    val context = LocalContext.current
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            activePlayer?.release()
            activePlayer = null
        }
    }

    Text(
        stringResource(R.string.event_detail_sound_header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )

    RitualSounds.ALL.forEach { sound ->
        SoundPickerRow(
            sound = sound,
            selected = sound.id == currentSoundId,
            enabled = enabled,
            isEventDefault = sound.id == event.defaultSoundId,
            onSelect = {
                // Persist null when the user picks the event default — keeps the row
                // tracking the seed default if `events.json` ever changes it.
                onSetSound(if (sound.id == event.defaultSoundId) null else sound.id)
            },
            onPreview = {
                activePlayer?.release()
                activePlayer = MediaPlayer.create(context, sound.rawResId)?.apply {
                    setOnCompletionListener {
                        it.release()
                        if (activePlayer === it) activePlayer = null
                    }
                    start()
                }
            }
        )
    }

    Text(
        text = stringResource(R.string.event_detail_sound_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SoundPickerRow(
    sound: RitualSound,
    selected: Boolean,
    enabled: Boolean,
    isEventDefault: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(sound.displayName),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (isEventDefault) {
                    Text(
                        text = stringResource(R.string.event_detail_sound_default,
                            stringResource(sound.displayName)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onPreview,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.event_detail_sound_preview),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.navpanchang.R
import com.navpanchang.panchang.AppLanguage
import com.navpanchang.panchang.LunarConvention
import com.navpanchang.panchang.NumeralSystem
import com.navpanchang.util.safeStringResource

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
    val context = LocalContext.current

    // Re-fetch state every time the screen comes back to the foreground — covers the
    // back-navigation from `HomeCityPickerScreen` (the picker writes to the database, but
    // this screen's StateFlow snapshot was captured before the write). Also picks up
    // permission-state changes if the user toggled battery optimization in system Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }

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

        LunarConventionCard(
            current = uiState.lunarConvention,
            onChanged = viewModel::onLunarConventionChange
        )

        LanguageCard(
            current = uiState.appLanguage,
            onChanged = { lang ->
                viewModel.onAppLanguageChange(lang)
                // Re-create the activity so `attachBaseContext` picks up the new locale
                // and Compose remounts with the right `values-<tag>/strings.xml`.
                (context as? androidx.activity.ComponentActivity)?.recreate()
            }
        )

        NumeralsCard(
            current = uiState.numeralSystem,
            onChanged = { system ->
                viewModel.onNumeralSystemChange(system)
                // Recreate so the new locale (with or without -u-nu-latn) takes effect
                // for date formatters and `%d` interpolation.
                (context as? androidx.activity.ComponentActivity)?.recreate()
            }
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
                text = safeStringResource(R.string.settings_sunrise_offset_value, current.toInt()),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LunarConventionCard(
    current: LunarConvention,
    onChanged: (LunarConvention) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_lunar_convention_title),
                style = MaterialTheme.typography.titleMedium
            )
            // Two-segment toggle. Order: Purnimanta first (matches the on-disk default),
            // Amanta second.
            val options = listOf(LunarConvention.PURNIMANTA, LunarConvention.AMANTA)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = current == option,
                        onClick = { onChanged(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        )
                    ) {
                        Text(
                            text = stringResource(
                                when (option) {
                                    LunarConvention.PURNIMANTA -> R.string.settings_lunar_convention_purnimanta
                                    LunarConvention.AMANTA -> R.string.settings_lunar_convention_amanta
                                }
                            )
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_lunar_convention_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageCard(
    current: AppLanguage,
    onChanged: (AppLanguage) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium
            )

            // Four radio rows — one per companion language. Hindi has full translations
            // today; the others currently fall back to Hindi for data labels and to
            // English for UI chrome (Android resource resolution). The pending hint is
            // only shown for those three so users understand why selecting them doesn't
            // change much *yet*.
            AppLanguage.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = current == option,
                            onClick = { onChanged(option) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = current == option,
                        onClick = null
                    )
                    Column {
                        // Display label rule:
                        //  * ENGLISH       → just "English" (no parens — same name twice).
                        //  * Other Indian  → "नेटिव-नाम (English-name)" so a non-Hindi
                        //                    speaker can still recognize what's selected.
                        val englishName = option.name.lowercase()
                            .replaceFirstChar { it.titlecase() }
                        val label = if (option == AppLanguage.ENGLISH) englishName
                            else "${option.nativeName} ($englishName)"
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        // Pending hint only on languages without translations yet.
                        // ENGLISH and HINDI ship full translations.
                        if (option != AppLanguage.ENGLISH && option != AppLanguage.HINDI) {
                            Text(
                                text = stringResource(R.string.settings_language_pending),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumeralsCard(
    current: NumeralSystem,
    onChanged: (NumeralSystem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_numerals_title),
                style = MaterialTheme.typography.titleMedium
            )
            val options = listOf(NumeralSystem.LATIN, NumeralSystem.DEVANAGARI)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = current == option,
                        onClick = { onChanged(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        )
                    ) {
                        Text(
                            text = stringResource(
                                when (option) {
                                    NumeralSystem.LATIN -> R.string.settings_numerals_latin
                                    NumeralSystem.DEVANAGARI -> R.string.settings_numerals_native
                                }
                            )
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_numerals_hint),
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

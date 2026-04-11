package com.navpanchang.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.navpanchang.R
import com.navpanchang.location.CityCatalog
import kotlinx.coroutines.launch

/**
 * Searchable home-city picker. Reached from Onboarding ("set your home city") and from
 * Settings ("change home city"). Taps persist the selected city via [SettingsViewModel]
 * and pop the back stack.
 *
 * Also exposes a "Use current location" shortcut at the top that kicks off
 * [SettingsViewModel.onUseCurrentLocation] — best-effort GPS resolve that falls back to
 * the list if permission is denied or no fix is available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCityPickerScreen(
    onCitySelected: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // We read the catalog directly on every recomposition — cheap (in-memory lazy list).
    val filteredCities: List<CityCatalog.City> = remember(query) {
        viewModel.searchCities(query)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_home_city_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text(stringResource(R.string.settings_home_city_search_hint)) },
                singleLine = true
            )

            TextButton(
                onClick = {
                    scope.launch {
                        viewModel.onUseCurrentLocation { success ->
                            if (success) onCitySelected()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Text(
                    text = stringResource(R.string.settings_home_city_use_gps),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredCities, key = { it.id }) { city ->
                    CityRow(
                        city = city,
                        onClick = {
                            scope.launch {
                                viewModel.onSelectCity(city)
                                onCitySelected()
                            }
                        }
                    )
                }
                if (filteredCities.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.settings_home_city_no_match),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CityRow(city: CityCatalog.City, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "${city.nameEn} (${city.nameHi})",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = city.country,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

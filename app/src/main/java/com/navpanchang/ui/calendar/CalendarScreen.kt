package com.navpanchang.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.navpanchang.R
import com.navpanchang.ui.theme.StateObservingGreen
import com.navpanchang.ui.theme.StatePreparingYellow
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month-grid calendar view. Each cell shows a date plus a tiny tithi label and, if the
 * user is subscribed to any events landing on that day, a colored dot. Tapping a day
 * opens [DayDetailSheet] with the full panchang.
 *
 * Navigation buttons at the top let the user page forward/back through months — the
 * full 24-month HOME lookahead is available for scrolling without any network call.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    onPickHomeCity: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(
            state = state,
            onPrev = viewModel::onPreviousMonth,
            onNext = viewModel::onNextMonth
        )

        WeekdayHeader()

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !state.homeCitySet -> CalendarEmptyState(onPickCity = onPickHomeCity)
            else -> MonthGrid(
                state = state,
                onDayClick = viewModel::onDayClick
            )
        }
    }

    if (state.selectedDay != null) {
        DayDetailSheet(
            detail = state.selectedDay!!,
            onDismiss = viewModel::onDismissDayDetail
        )
    }
}

@Composable
private fun MonthHeader(
    state: CalendarUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null)
        }
        Text(
            text = com.navpanchang.util.safeStringResource(
                R.string.calendar_month_header,
                state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                state.month.year
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun MonthGrid(
    state: CalendarUiState,
    onDayClick: (LocalDate) -> Unit
) {
    // Pad the grid so the first-of-month lines up under its weekday column. Monday = 1.
    val firstDayOfWeek = (state.month.atDay(1).dayOfWeek.value + 6) % 7
    val padding = List(firstDayOfWeek) { null }
    val cells: List<DayCell?> = padding + state.days

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        items(cells.size) { index ->
            val cell = cells[index]
            if (cell == null) {
                Box(Modifier.aspectRatio(1f))
            } else {
                DayCellView(cell = cell, onClick = { onDayClick(cell.date) })
            }
        }
    }
}

@Composable
private fun DayCellView(cell: DayCell, onClick: () -> Unit) {
    val isToday = cell.date == LocalDate.now()
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isToday) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall
            )
            cell.tithiAtSunrise?.let { tithi ->
                Text(
                    text = "${tithi.paksha.name.take(2)} ${tithi.nameIndex}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cell.hasSubscribedEvent) {
                val anyKshaya = cell.occurrences.any { it.isKshayaContext }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (anyKshaya) StatePreparingYellow else StateObservingGreen
                        )
                )
            }
        }
    }
}

/**
 * Shown when [CalendarUiState.homeCitySet] is false — typically a fresh install
 * or a reinstall before the user has picked a city. Mirrors the Home tab's
 * "Set your home city" empty-state copy so both surfaces give the user the same
 * pointer to Settings.
 */
@Composable
private fun CalendarEmptyState(onPickCity: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.calendar_no_home_city_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.calendar_no_home_city_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onPickCity) {
            Text(androidx.compose.ui.res.stringResource(R.string.home_city_missing_cta))
        }
    }
}

package com.navpanchang.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.navpanchang.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One row in the subscription toggle list. Shows the event name (English + Hindi)
 * and the next occurrence's date with a "in N days" subtitle, plus a Material Switch
 * bound to [onToggle].
 *
 * Tapping the row itself (not the switch) opens [EventDetailScreen] via [onClick].
 */
@Composable
fun SubscriptionRow(
    row: SubscriptionRowState,
    onToggle: (enabled: Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = row.event.nameEn,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = row.event.nameHi,
                    style = MaterialTheme.typography.bodyMedium
                )
                val next = row.nextOccurrence
                if (next != null) {
                    Text(
                        text = formatNextLine(next.dateLocal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.subscription_row_computing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = row.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun formatNextLine(date: LocalDate): String {
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(today, date)
    val absolute = date.format(DATE_FORMATTER)
    return when {
        days == 0L -> stringResource(R.string.subscription_row_next_today, absolute)
        days == 1L -> stringResource(R.string.subscription_row_next_tomorrow, absolute)
        days in 2..30 -> stringResource(R.string.subscription_row_next_in_days, absolute, days)
        else -> stringResource(R.string.subscription_row_next_generic, absolute)
    }
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

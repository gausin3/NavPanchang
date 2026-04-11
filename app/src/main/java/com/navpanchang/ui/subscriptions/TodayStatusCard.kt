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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.navpanchang.R

/**
 * Always-visible status card at the top of the Home screen. Shows today's tithi and
 * nakshatra with both English and Hindi names, so the user learns the app is
 * calculating correctly before they trust it with a vrat alarm.
 *
 * Displayed even when there are zero subscriptions — it's the "the app is alive"
 * signal.
 */
@Composable
fun TodayStatusCard(
    status: TodayStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.today_status_label),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = status.tithi.qualifiedNameEn,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = status.tithi.qualifiedNameHi,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.today_status_nakshatra,
                    status.nakshatra.nameEn,
                    status.nakshatra.nameHi
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

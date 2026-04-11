package com.navpanchang.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.navpanchang.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bottom sheet shown when the user taps a day in [CalendarScreen]. Displays the full
 * panchang for that day plus Adhik Maas / Kshaya tithi indicators where relevant.
 *
 * Per the plan's §Calendar UI, this is the "verification" surface — the user's mother
 * will cross-check its output against her printed panchang, so every label is bilingual
 * and every anomaly (Adhik, Kshaya, shifted-due-to-viddha) is called out explicitly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(
    detail: DayDetail,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = detail.date.format(DATE_FORMATTER),
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = stringResource(
                    R.string.calendar_day_detail_tithi,
                    detail.tithi.qualifiedNameEn + " (" + detail.tithi.qualifiedNameHi + ")"
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    R.string.calendar_day_detail_nakshatra,
                    detail.nakshatra.nameEn + " (" + detail.nakshatra.nameHi + ")"
                ),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(4.dp))

            detail.sunriseUtc?.let { sunrise ->
                Text(
                    text = stringResource(R.string.calendar_day_detail_sunrise, formatTime(sunrise)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            detail.sunsetUtc?.let { sunset ->
                Text(
                    text = stringResource(R.string.calendar_day_detail_sunset, formatTime(sunset)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (detail.isAdhik) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.calendar_day_detail_adhik_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (detail.isKshayaContext) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.calendar_day_detail_kshaya_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (detail.occurrences.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_subscriptions_header),
                    style = MaterialTheme.typography.titleSmall
                )
                detail.occurrences.forEach { occ ->
                    Text(
                        text = "• ${occ.eventId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMATTER)

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

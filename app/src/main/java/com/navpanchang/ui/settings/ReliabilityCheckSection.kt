package com.navpanchang.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.navpanchang.R
import com.navpanchang.alarms.BatteryOptimizationCheck
import com.navpanchang.ui.theme.StateObservingGreen
import com.navpanchang.ui.theme.StateWarning

/**
 * The Reliability Check card on [SettingsScreen]. Shows a green pill if everything is
 * configured correctly for reliable alarm delivery, or a red pill with a one-tap "Fix"
 * action that deep-links to the relevant system setting.
 *
 * Checks covered:
 *  * Battery optimization whitelist (via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
 *  * Exact-alarm permission (via `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, API 31+)
 *
 * Both are common reasons alarms fail to fire on OEMs like Xiaomi, Samsung, OnePlus,
 * Vivo, Oppo and Realme. See TECH_DESIGN.md
 * §Reliability check.
 */
@Composable
fun ReliabilityCheckSection(
    status: BatteryOptimizationCheck.Status,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_reliability_title),
                style = MaterialTheme.typography.titleMedium
            )

            CheckRow(
                label = stringResource(R.string.settings_reliability_battery),
                ok = status.ignoringBatteryOptimizations,
                onFix = { openBatterySettings(context) }
            )

            CheckRow(
                label = stringResource(R.string.settings_reliability_exact_alarms),
                ok = status.canScheduleExactAlarms,
                onFix = { openExactAlarmSettings(context) }
            )

            if (status.allGreen) {
                Text(
                    text = stringResource(R.string.settings_reliability_all_green),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StateObservingGreen
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_reliability_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StateWarning
                )
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color: Color = if (ok) StateObservingGreen else StateWarning
        Text(
            text = if (ok) "✓" else "✗",
            color = color,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        if (!ok) {
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.settings_reliability_fix))
            }
        }
    }
}

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

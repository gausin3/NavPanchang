package com.navpanchang.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.navpanchang.R
import com.navpanchang.alarms.AlarmManagerCompat

/**
 * Phase 5 onboarding shell — a two-step welcome → permissions → done flow. Intentionally
 * minimal: no city picker or subscription pre-selection yet. Its job is to walk the
 * user through the three critical permission grants that make alarms reliable:
 *
 *  1. `POST_NOTIFICATIONS` (API 33+)
 *  2. `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` (API 31+)
 *  3. Battery optimization whitelist
 *
 * Phase 5b will add GPS and home-city selection. For now the app falls back to
 * "set home city in Settings" if this flow doesn't cover it.
 */
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(OnboardingStep.Welcome) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Ignore result — the user can re-open Settings from the Reliability Check */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (step) {
            OnboardingStep.Welcome -> WelcomeStep(onNext = { step = OnboardingStep.Permissions })

            OnboardingStep.Permissions -> PermissionsStep(
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRequestExactAlarm = { requestExactAlarmPermission(context) },
                onRequestBatteryWhitelist = { requestBatteryWhitelist(context) },
                onNext = { step = OnboardingStep.Done }
            )

            OnboardingStep.Done -> DoneStep(onFinish = onCompleted)
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Text(
        stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_continue))
    }
}

@Composable
private fun PermissionsStep(
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onNext: () -> Unit
) {
    Text(
        stringResource(R.string.onboarding_permissions_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        stringResource(R.string.onboarding_permissions_body),
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
        Text("Grant notifications")
    }
    Button(onClick = onRequestExactAlarm, modifier = Modifier.fillMaxWidth()) {
        Text("Grant exact alarms")
    }
    Button(onClick = onRequestBatteryWhitelist, modifier = Modifier.fillMaxWidth()) {
        Text("Disable battery optimization")
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_continue))
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Text(
        stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_done))
    }
}

// ------------------------------------------------------------------
// Permission request helpers.
// ------------------------------------------------------------------

private fun requestExactAlarmPermission(context: Context) {
    // The permission model only exists on API 31+; earlier versions always allow exact
    // alarms, so there is nothing to request there.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val alarmManager = context.getSystemService<AlarmManager>() ?: return
    if (AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

private fun requestBatteryWhitelist(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

private enum class OnboardingStep { Welcome, Permissions, Done }

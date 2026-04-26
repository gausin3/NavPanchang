package com.navpanchang.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.navpanchang.BuildConfig
import com.navpanchang.alarms.NotificationChannels
import com.navpanchang.util.debug.NotificationTestingTool

/**
 * Hidden QA / debug menu — accessed by tapping the version number on the About
 * screen 7 times. Provides one-tap entry points to the [NotificationTestingTool]
 * methods so a developer or tester can validate alarm copy, channel-to-sound
 * mapping, and edge cases without waiting for real upcoming Ekadashis.
 *
 * **Debug builds only.** The whole composable short-circuits to a stub on
 * release builds via [BuildConfig.DEBUG] so even if a wrong nav route somehow
 * reaches this in production, nothing renders.
 *
 * See `util/debug/NotificationTestingTool.kt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuScreen(onBack: () -> Unit) {
    if (!BuildConfig.DEBUG) {
        // Defensive: prod builds get an empty scaffold even if routed here.
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("Not available") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }) { padding -> Column(Modifier.padding(padding)) {} }
        return
    }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QA — Notification Testing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Debug build — these controls are stripped from release.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Alarm-kind tests ────────────────────────────────────────────────
            DebugSectionCard(title = "Fire alarm types") {
                Button(
                    onClick = { NotificationTestingTool.firePlannerNow(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Fire Planner now") }
                Button(
                    onClick = { NotificationTestingTool.fireObserverNow(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Fire Observer now") }
                Button(
                    onClick = { NotificationTestingTool.fireParanaNow(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Fire Parana now") }
            }

            // ── Per-channel sound audition ──────────────────────────────────────
            DebugSectionCard(title = "Audition ritual sounds") {
                Text(
                    text = "Each button posts a notification on the named channel — " +
                        "use this to confirm each R.raw.ritual_*.wav file is correctly " +
                        "bound and audible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NotificationChannels.RITUAL_CHANNELS.forEach { channelId ->
                    OutlinedButton(
                        onClick = { NotificationTestingTool.fireOnChannel(context, channelId) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(channelId.removePrefix("channel_ritual_")) }
                }
            }

            // ── Edge cases ──────────────────────────────────────────────────────
            DebugSectionCard(title = "Edge cases") {
                OutlinedButton(
                    onClick = { NotificationTestingTool.simulateLateSubscription(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Late subscription (yesterday's Planner)") }
                Text(
                    text = "Note: timezone change is exercised by changing emulator " +
                        "locale via `adb shell setprop persist.sys.timezone <tz>` — no " +
                        "in-app button gives the same coverage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DebugSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

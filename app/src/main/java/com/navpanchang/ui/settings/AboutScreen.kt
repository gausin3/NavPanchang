package com.navpanchang.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.navpanchang.BuildConfig
import com.navpanchang.R

/**
 * About screen — shows the AGPL attribution, Swiss Ephemeris credit, and a source-code
 * link. Required by the AGPL compliance checklist (see §Licensing in the plan).
 *
 * **Hidden debug entry:** in debug builds, tapping the version number 7 times opens
 * the QA/debug menu. The 7-tap chord stays disabled on release builds so production
 * users can't trip into it accidentally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onUnlockDebugMenu: () -> Unit = {}
) {
    var tapCount by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )

            // Version line — clickable in debug builds. Seven taps in a row opens the
            // QA debug menu. Release builds ignore the clicks entirely so a production
            // user never accidentally lands there.
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (BuildConfig.DEBUG) {
                    Modifier.clickable {
                        tapCount += 1
                        when {
                            tapCount >= 7 -> {
                                tapCount = 0
                                onUnlockDebugMenu()
                            }
                            tapCount >= 4 -> {
                                Toast.makeText(
                                    context,
                                    "${7 - tapCount} more taps…",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } else Modifier
            )

            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.about_licensing_header),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.licensing_summary),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.about_swisseph_credit),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.about_source_code_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

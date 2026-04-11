package com.navpanchang

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.navpanchang.alarms.NotificationChannels
import com.navpanchang.alarms.RefreshScheduler
import com.navpanchang.data.seed.EventCatalogSyncer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Responsibilities:
 *  - Host the Hilt component graph.
 *  - Provide a [HiltWorkerFactory] so [androidx.work.WorkManager] can inject into workers.
 *  - Run [EventCatalogSyncer] on cold start to upsert event_definitions from assets/events.json
 *    when the bundled catalogVersion is newer than what's in the database.
 *  - (Phase 1) Copy Swiss Ephemeris `.se1` files from assets to internal storage on first run.
 *
 * See TECH_DESIGN.md §Architecture.
 */
@HiltAndroidApp
class NavPanchangApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var eventCatalogSyncer: EventCatalogSyncer

    @Inject
    lateinit var refreshScheduler: RefreshScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Ensure notification channels exist before any alarm can fire. Idempotent —
        // channels the user has customized (DND, vibration override) are preserved.
        NotificationChannels.ensureChannels(this)

        appScope.launch {
            runCatching { eventCatalogSyncer.syncIfNeeded() }
                .onSuccess { upgraded ->
                    if (upgraded) {
                        Log.i(TAG, "Event catalog upgraded — enqueuing refresh.")
                        refreshScheduler.enqueueOneShot()
                    }
                }
                .onFailure { Log.e(TAG, "EventCatalogSyncer failed", it) }
        }
        // Schedule the daily HOME refresh. KEEP policy is idempotent across cold starts.
        refreshScheduler.schedulePeriodic()

        // Phase 1b TODO: copy assets/ephe/*.se1 → filesDir/ephe/ and call swe_set_ephe_path.
    }

    companion object {
        private const val TAG = "NavPanchangApp"
    }
}

package com.navpanchang.alarms

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.navpanchang.data.db.OccurrenceDao
import com.navpanchang.data.db.SubscriptionDao
import com.navpanchang.data.repo.AlarmRepository
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.data.repo.OccurrenceRepository
import com.navpanchang.data.repo.SubscriptionRepository
import com.navpanchang.location.GeofenceManager
import com.navpanchang.location.LocationProvider
import com.navpanchang.panchang.OccurrenceComputer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Periodic background worker that keeps the HOME-tier 24-month occurrence buffer
 * fresh. Runs once per day and also on boot / timezone-change / home-city-change events.
 *
 * **What it does:**
 *  1. Reads the user's home city from [MetadataRepository].
 *  2. Reads enabled [com.navpanchang.panchang.EventDefinition]s from [SubscriptionRepository].
 *  3. Runs [OccurrenceComputer.computeWindow] for the next 24 months at the home city.
 *  4. Atomically replaces the HOME tier in the `occurrences` table via
 *     [OccurrenceRepository.replaceWindow].
 *  5. Prunes rows older than yesterday.
 *  6. Records the calc instant in `calc_metadata.last_home_calc_at`.
 *
 * **Tier 2 (Phase 8):** after the HOME tier lands, the worker also tries to run a
 * 30-day CURRENT-tier recomputation using the live GPS fix. If location permission is
 * granted and we can get a fix, it computes high-precision occurrences, writes them
 * with `locationTag = "CURRENT"` / `isHighPrecision = true`, and re-registers the
 * 100 km travel geofence around the new position. The `OccurrenceRepository` query
 * precedence then serves CURRENT rows over HOME rows on matching dates.
 *
 * **What it doesn't do:**
 *  * Does NOT touch subscriptions (user state — §MEMORY.md sanctity rule).
 *  * Does NOT schedule AlarmManager alarms directly — that's the [AlarmScheduler]'s
 *    job; the worker hands occurrences to it.
 *
 * See TECH_DESIGN.md §RefreshWorker.
 */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val occurrenceComputer: OccurrenceComputer,
    private val occurrenceRepository: OccurrenceRepository,
    private val occurrenceDao: OccurrenceDao,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionDao: SubscriptionDao,
    private val metadataRepository: MetadataRepository,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val locationProvider: LocationProvider,
    private val geofenceManager: GeofenceManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val metadata = metadataRepository.getOrDefault()
            val homeLat = metadata.homeLat
            val homeLon = metadata.homeLon
            val homeTz = metadata.homeTz

            if (homeLat == null || homeLon == null || homeTz == null) {
                // Onboarding hasn't completed yet — nothing to compute. Report success
                // so WorkManager doesn't back off retrying; the next run will try again.
                Log.i(TAG, "Skipping refresh: home city not set")
                return Result.success()
            }

            val subscribed = subscriptionRepository.getEnabledDefinitions()
            if (subscribed.isEmpty()) {
                // No subscriptions → clear any stale rows and exit.
                Log.i(TAG, "No enabled subscriptions — clearing HOME window")
                occurrenceRepository.replaceWindow(
                    locationTag = LOCATION_TAG_HOME,
                    newOccurrences = emptyList(),
                    computedAtUtc = System.currentTimeMillis()
                )
                return Result.success()
            }

            val zone = ZoneId.of(homeTz)
            val today = LocalDate.now(zone)
            val end = today.plusMonths(HOME_LOOKAHEAD_MONTHS)

            val request = OccurrenceComputer.Request(
                events = subscribed,
                startDate = today,
                endDate = end,
                latitudeDeg = homeLat,
                longitudeDeg = homeLon,
                zone = zone,
                locationTag = LOCATION_TAG_HOME,
                isHighPrecision = false
            )

            val computedAtUtc = System.currentTimeMillis()
            val occurrences = occurrenceComputer.computeWindow(request)

            occurrenceRepository.replaceWindow(
                locationTag = LOCATION_TAG_HOME,
                newOccurrences = occurrences,
                computedAtUtc = computedAtUtc
            )
            occurrenceRepository.pruneBefore(today.minusDays(1))
            metadataRepository.recordHomeCalc(computedAtUtc)
            alarmRepository.pruneExpired(computedAtUtc)

            // Now that the fresh occurrences are in the DB with their auto-generated
            // IDs, iterate them and schedule alarms. We read the rows back from the DAO
            // so we have the `id` needed for AlarmScheduler.requestCodeFor().
            val eventIds = subscribed.map { it.id }.toSet()
            val subsById = subscribed.associateBy { it.id }
            var alarmCount = 0
            for (eventId in eventIds) {
                val subscription = subscriptionDao.getById(eventId) ?: continue
                val event = subsById[eventId] ?: continue
                val entities = occurrenceDao.getUpcomingForEvent(
                    eventId, today.toString(), ALARM_HORIZON_LIMIT
                )
                for (entity in entities) {
                    val occurrence = com.navpanchang.data.mapper.OccurrenceMapper.fromEntity(entity)
                    alarmCount += alarmScheduler.scheduleForOccurrence(
                        occurrenceId = entity.id,
                        occurrence = occurrence,
                        event = event,
                        subscription = subscription,
                        nowUtc = computedAtUtc
                    )
                }
            }

            Log.i(TAG, "HOME refresh complete: ${occurrences.size} occurrences, $alarmCount alarms")

            // Tier 2 — high-precision CURRENT window if we have live GPS.
            runCurrentTierIfPossible(subscribed, computedAtUtc)

            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "HOME refresh failed", t)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    /**
     * Tier 2 — high-precision recomputation for the user's actual current GPS location.
     * Runs after the HOME tier is in place. If location permission is denied or we
     * can't get a fix, quietly skip — the HOME tier handles the alarm scheduling in
     * that case.
     *
     * Also (re-)registers the 100 km geofence so the next boundary crossing triggers
     * a fresh recompute. See [com.navpanchang.location.GeofenceManager].
     */
    private suspend fun runCurrentTierIfPossible(
        subscribed: List<com.navpanchang.panchang.EventDefinition>,
        computedAtUtc: Long
    ) {
        if (subscribed.isEmpty()) return
        val location = locationProvider.getCurrentLocation()
        if (location == null) {
            Log.i(TAG, "Skipping Tier 2 — no GPS fix available")
            return
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val end = today.plusDays(CURRENT_LOOKAHEAD_DAYS)

        val request = OccurrenceComputer.Request(
            events = subscribed,
            startDate = today,
            endDate = end,
            latitudeDeg = location.latitude,
            longitudeDeg = location.longitude,
            zone = zone,
            locationTag = LOCATION_TAG_CURRENT,
            isHighPrecision = true
        )
        val tier2 = occurrenceComputer.computeWindow(request)
        occurrenceRepository.replaceWindow(
            locationTag = LOCATION_TAG_CURRENT,
            newOccurrences = tier2,
            computedAtUtc = computedAtUtc
        )
        metadataRepository.recordCurrentCalc(location.latitude, location.longitude, computedAtUtc)

        // Re-arm alarms for the Tier 2 rows that now take precedence over HOME for
        // their dates. Query precedence in OccurrenceRepository handles the choice.
        val subsById = subscribed.associateBy { it.id }
        var tier2AlarmCount = 0
        for (event in subscribed) {
            val subscription = subscriptionDao.getById(event.id) ?: continue
            val entities = occurrenceDao.getUpcomingForEvent(
                event.id, today.toString(), ALARM_HORIZON_LIMIT
            )
            for (entity in entities) {
                val occurrence = com.navpanchang.data.mapper.OccurrenceMapper.fromEntity(entity)
                tier2AlarmCount += alarmScheduler.scheduleForOccurrence(
                    occurrenceId = entity.id,
                    occurrence = occurrence,
                    event = subsById[event.id] ?: event,
                    subscription = subscription,
                    nowUtc = computedAtUtc
                )
            }
        }

        // Refresh the 100 km geofence anchor so the next travel event fires.
        geofenceManager.registerAroundLastCalc(location.latitude, location.longitude)

        Log.i(TAG, "Tier 2 CURRENT refresh complete: ${tier2.size} occurrences, $tier2AlarmCount alarms")
    }

    companion object {
        private const val TAG = "RefreshWorker"
        const val WORK_NAME_PERIODIC = "com.navpanchang.RefreshWorker.PERIODIC"
        const val WORK_NAME_ONESHOT = "com.navpanchang.RefreshWorker.ONESHOT"

        const val LOCATION_TAG_HOME = "HOME"
        const val LOCATION_TAG_CURRENT = "CURRENT"

        private const val HOME_LOOKAHEAD_MONTHS = 24L

        /** Tier 2 high-precision window — ~30 days is enough for the next few events
         *  at any subscribed cadence. The next daily refresh will extend it. */
        private const val CURRENT_LOOKAHEAD_DAYS = 30L

        private const val MAX_RETRIES = 3

        /**
         * Only schedule alarms for the nearest N occurrences per event. The other 180+
         * will be picked up by the next daily refresh. Keeps AlarmManager's PendingIntent
         * count under control (Android soft-caps at ~500 per app).
         */
        private const val ALARM_HORIZON_LIMIT = 6
    }
}

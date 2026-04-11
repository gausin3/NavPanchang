package com.navpanchang.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.navpanchang.data.db.entities.ScheduledAlarmEntity
import com.navpanchang.data.db.entities.SubscriptionEntity
import com.navpanchang.data.repo.AlarmRepository
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.data.repo.OccurrenceRepository
import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.Occurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules [AlarmManager] alarms for NavPanchang occurrences. The single surface for
 * the entire dual-alarm model:
 *
 *  * [PLANNER][AlarmKind.PLANNER] — fires at the user's configured wall-clock local
 *    time on the day *before* the occurrence. Anchored to wall-clock so it follows the
 *    user across timezones (see [LocaleChangeReceiver]).
 *  * [OBSERVER][AlarmKind.OBSERVER] — fires at local sunrise on the day of the
 *    occurrence (GPS-anchored via [Occurrence.observanceUtc]).
 *  * [PARANA][AlarmKind.PARANA] — fires at sunrise on Dvadashi (day after the vrat),
 *    only when [Occurrence.paranaStartUtc] is non-null.
 *
 * **Invariants:**
 *  * Every scheduled alarm writes a [ScheduledAlarmEntity] row so [BootReceiver] and
 *    [LocaleChangeReceiver] can re-arm it. Cancellation deletes the row in the same
 *    transaction (best-effort).
 *  * Request codes are stable and derived from `(occurrenceId * 3 + kind.ordinal)` so
 *    re-arming finds and replaces the existing PendingIntent, not a stale copy.
 *  * Past fire times are quietly skipped via [LateSubscriptionGate] — not an error.
 *  * Per-sub-alarm toggles on [SubscriptionEntity] let the user turn off any of the
 *    three kinds independently without unsubscribing from the event entirely.
 *
 * See TECH_DESIGN.md §AlarmScheduler.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val metadataRepository: MetadataRepository
) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    /**
     * Schedule (or reschedule) every alarm for [occurrence] per the subscription's
     * sub-toggles and the event's defaults.
     *
     * Safe to call repeatedly — existing alarms for the same (occurrence, kind) tuple
     * are cancelled and replaced.
     *
     * @return number of alarms actually scheduled (0-3).
     */
    suspend fun scheduleForOccurrence(
        occurrenceId: Long,
        occurrence: Occurrence,
        event: EventDefinition,
        subscription: SubscriptionEntity,
        nowUtc: Long = System.currentTimeMillis()
    ): Int {
        val am = alarmManager ?: return 0
        if (!AlarmManagerCompat.canScheduleExactAlarms(am)) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted — deferring all alarms for $occurrenceId")
            return 0
        }

        val plannerZone = resolvePlannerZone()
        var scheduled = 0

        if (subscription.plannerEnabled) {
            val fireAt = computePlannerFireTime(occurrence, subscription, event, plannerZone)
            if (fireAt != null && scheduleOne(occurrenceId, AlarmKind.PLANNER, fireAt, subscription, event, nowUtc)) {
                scheduled++
            }
        }

        if (subscription.observerEnabled) {
            if (scheduleOne(
                    occurrenceId, AlarmKind.OBSERVER, occurrence.observanceUtc,
                    subscription, event, nowUtc
                )
            ) scheduled++
        }

        if (subscription.paranaEnabled && occurrence.paranaStartUtc != null) {
            if (scheduleOne(
                    occurrenceId, AlarmKind.PARANA, occurrence.paranaStartUtc,
                    subscription, event, nowUtc
                )
            ) scheduled++
        }

        return scheduled
    }

    /**
     * Cancel every alarm kind for the given occurrence. Called when a subscription is
     * disabled or an occurrence is pruned.
     */
    suspend fun cancelForOccurrence(occurrenceId: Long) {
        val am = alarmManager ?: return
        for (kind in AlarmKind.entries) {
            val requestCode = requestCodeFor(occurrenceId, kind)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                buildBroadcastIntent(occurrenceId, kind),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                am.cancel(pendingIntent)
                pendingIntent.cancel()
            }
            alarmRepository.deleteByRequestCode(requestCode)
        }
    }

    /**
     * Re-arm every still-in-future alarm row in [AlarmRepository]. Called by
     * [BootReceiver] after a reboot and by [com.navpanchang.alarms.RefreshWorker] as
     * defense-in-depth on OEMs that drop alarms aggressively.
     */
    suspend fun rearmAllPending(nowUtc: Long = System.currentTimeMillis()) {
        val am = alarmManager ?: return
        if (!AlarmManagerCompat.canScheduleExactAlarms(am)) return

        val pending = alarmRepository.getPending(nowUtc)
        for (row in pending) {
            val intent = buildBroadcastIntent(row.occurrenceId, AlarmKind.fromName(row.kind))
            val pendingIntent = PendingIntent.getBroadcast(
                context, row.requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, row.fireAtUtc, pendingIntent
            )
        }
        Log.i(TAG, "Re-armed ${pending.size} pending alarms")
    }

    // ------------------------------------------------------------------
    // Internals.
    // ------------------------------------------------------------------

    private suspend fun scheduleOne(
        occurrenceId: Long,
        kind: AlarmKind,
        fireAtUtc: Long,
        subscription: SubscriptionEntity,
        event: EventDefinition,
        nowUtc: Long
    ): Boolean {
        val gate = LateSubscriptionGate.decide(fireAtUtc, nowUtc)
        if (gate is LateSubscriptionGate.Decision.Skip) {
            Log.i(TAG, "Skipping $kind for occurrence $occurrenceId: ${gate.reason}")
            // Record a pending_status on the Planner so the UI can show a "ready for
            // tomorrow" card instead of acting like nothing happened.
            if (kind == AlarmKind.PLANNER) {
                alarmRepository.upsert(
                    ScheduledAlarmEntity(
                        requestCode = requestCodeFor(occurrenceId, kind),
                        occurrenceId = occurrenceId,
                        kind = kind.name,
                        fireAtUtc = fireAtUtc,
                        channelId = channelIdFor(kind, subscription, event),
                        pendingStatus = PENDING_READY_FOR_TOMORROW
                    )
                )
            }
            return false
        }

        val am = alarmManager ?: return false
        val requestCode = requestCodeFor(occurrenceId, kind)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            buildBroadcastIntent(occurrenceId, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtUtc, pendingIntent)

        alarmRepository.upsert(
            ScheduledAlarmEntity(
                requestCode = requestCode,
                occurrenceId = occurrenceId,
                kind = kind.name,
                fireAtUtc = fireAtUtc,
                channelId = channelIdFor(kind, subscription, event),
                pendingStatus = null
            )
        )
        return true
    }

    /**
     * Compute the PLANNER fire instant: "the day before the observation date, at
     * `customPlannerHhmm` (or the event default) in the user's home timezone". Returns
     * `null` if the home timezone isn't set yet.
     */
    private suspend fun computePlannerFireTime(
        occurrence: Occurrence,
        subscription: SubscriptionEntity,
        event: EventDefinition,
        plannerZone: ZoneId
    ): Long? {
        val hhmm = subscription.customPlannerHhmm ?: event.defaultPlannerTimeHhmm
        val localTime = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return null
        val plannerDate = occurrence.dateLocal.minusDays(1)
        return ZonedDateTime.of(plannerDate, localTime, plannerZone).toInstant().toEpochMilli()
    }

    private suspend fun resolvePlannerZone(): ZoneId {
        val meta = metadataRepository.get()
        val tzName = meta?.homeTz ?: ZoneId.systemDefault().id
        return runCatching { ZoneId.of(tzName) }.getOrDefault(ZoneId.systemDefault())
    }

    private fun channelIdFor(
        kind: AlarmKind,
        subscription: SubscriptionEntity,
        event: EventDefinition
    ): String = when (kind) {
        AlarmKind.PLANNER -> NotificationChannels.EVENT_REMINDERS
        AlarmKind.OBSERVER, AlarmKind.PARANA -> {
            val soundId = subscription.customSoundId ?: event.defaultSoundId
            NotificationChannels.channelIdForSound(soundId)
        }
    }

    private fun buildBroadcastIntent(occurrenceId: Long, kind: AlarmKind): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE_ALARM
            putExtra(AlarmReceiver.EXTRA_KIND, kind.name)
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
        }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_FIRE_ALARM = "com.navpanchang.alarms.FIRE_ALARM"

        /** Written to `pending_status` when a Planner alarm is skipped due to late subscription. */
        const val PENDING_READY_FOR_TOMORROW = "READY_FOR_TOMORROW"

        /**
         * Stable request code derived from the persistent occurrence id and the alarm
         * kind. `Long.toInt()` truncation is safe: with ~180K alarms/year we have 11 000
         * years before `Int.MAX_VALUE / 3` wraps.
         */
        fun requestCodeFor(occurrenceId: Long, kind: AlarmKind): Int =
            (occurrenceId * 3 + kind.ordinal).toInt()
    }
}

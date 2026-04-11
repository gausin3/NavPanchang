package com.navpanchang.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.navpanchang.data.db.EventDefinitionDao
import com.navpanchang.data.db.OccurrenceDao
import com.navpanchang.data.db.SubscriptionDao
import com.navpanchang.data.mapper.OccurrenceMapper
import com.navpanchang.data.repo.AlarmRepository
import com.navpanchang.data.repo.MetadataRepository
import com.navpanchang.panchang.EventRuleParser
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Broadcast target for every scheduled alarm (PLANNER / OBSERVER / PARANA).
 *
 * **Responsibilities:**
 *  1. Look up the occurrence by id.
 *  2. Validate the subscription is still enabled and the matching sub-alarm toggle
 *     is still on. If not, bail silently — the user unsubscribed since the alarm was
 *     scheduled.
 *  3. Load the parsed [com.navpanchang.panchang.EventDefinition] via
 *     [EventRuleParser.fromEntity].
 *  4. Build a notification via [NotificationBuilder] and post on the right channel.
 *  5. Delete the `scheduled_alarms` row so it's not re-armed on next boot.
 *  6. Also handles the [ACTION_SNOOZE_TO_SUNRISE] action from the "Remind me at
 *     Sunrise" Planner action: schedules a one-shot Observer alarm.
 *
 * **Design note — async work in a BroadcastReceiver:** we use
 * [BroadcastReceiver.goAsync] with a supervisor coroutine scope so we can hit Room
 * without holding up the main thread. The scope is short-lived — only for the duration
 * of the notification build.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var occurrenceDao: OccurrenceDao
    @Inject lateinit var subscriptionDao: SubscriptionDao
    @Inject lateinit var eventDefinitionDao: EventDefinitionDao
    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var metadataRepository: MetadataRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: return
        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1L)
        if (occurrenceId < 0) {
            Log.w(TAG, "Missing occurrenceId in intent")
            return
        }

        val kind = runCatching { AlarmKind.fromName(kindName) }.getOrNull()
            ?: run {
                Log.w(TAG, "Unknown kind: $kindName")
                return
            }

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_FIRE_ALARM -> fireNotification(
                        context, kind, occurrenceId
                    )

                    ACTION_SNOOZE_TO_SUNRISE -> snoozeToSunrise(occurrenceId)

                    else -> Log.w(TAG, "Unknown action: $action")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "AlarmReceiver failed: $action / $kindName / $occurrenceId", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fireNotification(
        context: Context,
        kind: AlarmKind,
        occurrenceId: Long
    ) {
        // 1. Load the occurrence.
        val entity = occurrenceDao.getById(occurrenceId) ?: run {
            Log.w(TAG, "Occurrence $occurrenceId not found — was it pruned?")
            alarmRepository.deleteByRequestCode(
                AlarmScheduler.requestCodeFor(occurrenceId, kind)
            )
            return
        }
        val occurrence = OccurrenceMapper.fromEntity(entity)

        // 2. Validate the subscription is still enabled with the matching sub-toggle.
        val subscription = subscriptionDao.getById(occurrence.eventId) ?: run {
            Log.i(TAG, "No subscription for ${occurrence.eventId} — skipping")
            return
        }
        if (!subscription.enabled) {
            Log.i(TAG, "Subscription disabled for ${occurrence.eventId} — skipping")
            return
        }
        val subToggle = when (kind) {
            AlarmKind.PLANNER -> subscription.plannerEnabled
            AlarmKind.OBSERVER -> subscription.observerEnabled
            AlarmKind.PARANA -> subscription.paranaEnabled
        }
        if (!subToggle) {
            Log.i(TAG, "$kind toggle off for ${occurrence.eventId} — skipping")
            return
        }

        // 3. Load the parsed event definition.
        val defEntity = eventDefinitionDao.getById(occurrence.eventId)
            ?.takeIf { !it.deprecated }
            ?: run {
                Log.i(TAG, "Event ${occurrence.eventId} deprecated or missing — skipping")
                return
            }
        val event = EventRuleParser.fromEntity(defEntity)

        // 4. Resolve the display timezone. Home tz for wall-clock display.
        val metadata = metadataRepository.get()
        val zone = metadata?.homeTz?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

        // 5. Build and post the notification.
        val soundChoice = subscription.customSoundId ?: event.defaultSoundId
        val channelId = when (kind) {
            AlarmKind.PLANNER -> NotificationChannels.EVENT_REMINDERS
            AlarmKind.OBSERVER, AlarmKind.PARANA ->
                NotificationChannels.channelIdForSound(soundChoice)
        }
        val notification = NotificationBuilder
            .build(
                context = context,
                kind = kind,
                event = event,
                occurrence = occurrence,
                occurrenceId = occurrenceId,
                channelId = channelId,
                zone = zone
            )
            .build()

        val notifId = AlarmScheduler.requestCodeFor(occurrenceId, kind)
        NotificationManagerCompat.from(context).notify(notifId, notification)

        // 6. Prune the scheduled_alarms row — the alarm has fired.
        alarmRepository.deleteByRequestCode(notifId)
    }

    /**
     * Handle the "Remind me at Sunrise" notification action. Loads the occurrence, its
     * subscription, and the event definition, then re-schedules all three alarm kinds —
     * delegating the work to [AlarmScheduler] which is idempotent. The Observer alarm
     * in particular will now be armed even if the user had it toggled off, because
     * they explicitly asked for it from the Planner notification.
     */
    private suspend fun snoozeToSunrise(occurrenceId: Long) {
        val entity = occurrenceDao.getById(occurrenceId) ?: run {
            Log.w(TAG, "snoozeToSunrise: occurrence $occurrenceId missing")
            return
        }
        val occurrence = OccurrenceMapper.fromEntity(entity)

        val defEntity = eventDefinitionDao.getById(occurrence.eventId)
            ?.takeIf { !it.deprecated }
            ?: return
        val event = EventRuleParser.fromEntity(defEntity)

        val existingSub = subscriptionDao.getById(occurrence.eventId)
            ?: return // Nothing to snooze against
        // Force observerEnabled ON for this one-shot schedule. We don't persist the
        // change — the user's underlying preference stays as it was.
        val snoozeSub = existingSub.copy(
            enabled = true,
            observerEnabled = true,
            plannerEnabled = false, // Planner already fired; don't re-fire
            paranaEnabled = existingSub.paranaEnabled
        )

        alarmScheduler.scheduleForOccurrence(
            occurrenceId = occurrenceId,
            occurrence = occurrence,
            event = event,
            subscription = snoozeSub
        )
        Log.i(TAG, "Snooze-to-sunrise scheduled for occurrence=$occurrenceId")
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        /** Extra keys shared with [AlarmScheduler]. */
        const val EXTRA_KIND = "com.navpanchang.alarms.EXTRA_KIND"
        const val EXTRA_OCCURRENCE_ID = "com.navpanchang.alarms.EXTRA_OCCURRENCE_ID"

        /**
         * Fired from the "Remind me at Sunrise" action in a Planner notification.
         * Handled by [snoozeToSunrise] which schedules a one-shot Observer alarm via
         * [AlarmScheduler] without touching the user's persisted subscription state.
         */
        const val ACTION_SNOOZE_TO_SUNRISE = "com.navpanchang.alarms.SNOOZE_TO_SUNRISE"

        /** Supervisor scope kept at receiver level so cancelled coroutines don't bubble. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

package com.navpanchang.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.navpanchang.data.db.EventDefinitionDao
import com.navpanchang.data.db.OccurrenceDao
import com.navpanchang.data.db.SubscriptionDao
import com.navpanchang.data.mapper.OccurrenceMapper
import com.navpanchang.data.repo.AlarmRepository
import com.navpanchang.panchang.EventRuleParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The "traveling wall-clock" receiver.
 *
 * **Problem:** PLANNER alarms are anchored to wall-clock local time ("8:00 PM on the
 * day before the event"). When the user flies Lucknow → Dubai, `AlarmManager` sees the
 * same epoch-millis — but that epoch-millis now maps to 18:30 local instead of 20:00.
 * The alarm fires an hour and a half early and looks buggy.
 *
 * **Fix:** on [Intent.ACTION_TIMEZONE_CHANGED], read every pending PLANNER row from
 * `scheduled_alarms`, look up the source occurrence + subscription, and reschedule
 * each via [AlarmScheduler.scheduleForOccurrence] against the NEW device timezone.
 * Observer and Parana alarms are anchored to astronomical events (absolute epoch-millis)
 * and need no change.
 *
 * See TECH_DESIGN.md §Boot, timezone, and locale changes.
 */
@AndroidEntryPoint
class LocaleChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var occurrenceDao: OccurrenceDao
    @Inject lateinit var subscriptionDao: SubscriptionDao
    @Inject lateinit var eventDefinitionDao: EventDefinitionDao
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_TIMEZONE_CHANGED -> reschedulePlannerAlarms()
                    Intent.ACTION_LOCALE_CHANGED ->
                        Log.i(TAG, "Locale changed — future notification posts will pick up new strings")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "LocaleChangeReceiver failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun reschedulePlannerAlarms() {
        val now = System.currentTimeMillis()
        val pendingPlanners = alarmRepository.getPendingByKind(AlarmKind.PLANNER.name, now)
        Log.i(TAG, "Timezone changed — rescheduling ${pendingPlanners.size} planner alarms")

        for (row in pendingPlanners) {
            val entity = occurrenceDao.getById(row.occurrenceId) ?: continue
            val occurrence = OccurrenceMapper.fromEntity(entity)

            val subscription = subscriptionDao.getById(occurrence.eventId) ?: continue
            if (!subscription.enabled || !subscription.plannerEnabled) continue

            val defEntity = eventDefinitionDao.getById(occurrence.eventId)
                ?.takeIf { !it.deprecated }
                ?: continue
            val event = EventRuleParser.fromEntity(defEntity)

            // Delegating back to AlarmScheduler re-arms all three kinds. Idempotent —
            // setExactAndAllowWhileIdle with an existing PendingIntent replaces atomically.
            alarmScheduler.scheduleForOccurrence(
                occurrenceId = row.occurrenceId,
                occurrence = occurrence,
                event = event,
                subscription = subscription,
                nowUtc = now
            )
        }
    }

    private companion object {
        private const val TAG = "LocaleChangeReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

package com.navpanchang.data.repo

import com.navpanchang.data.db.AlarmDao
import com.navpanchang.data.db.entities.ScheduledAlarmEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin repository over [AlarmDao] for reading and writing [ScheduledAlarmEntity] rows.
 *
 * The `scheduled_alarms` table is the durable mirror of every `AlarmManager`
 * PendingIntent NavPanchang has in flight. It's consulted by:
 *  * [com.navpanchang.alarms.BootReceiver] to re-arm alarms after reboot
 *  * [com.navpanchang.alarms.LocaleChangeReceiver] to reschedule Planner alarms on
 *    timezone change
 *  * [com.navpanchang.alarms.AlarmReceiver] to validate a fired alarm still matches a
 *    live subscription
 *  * [com.navpanchang.alarms.RefreshScheduler] / [com.navpanchang.alarms.RefreshWorker]
 *    to prune stale rows after a bulk recompute
 */
@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao
) {

    suspend fun getPending(nowUtc: Long): List<ScheduledAlarmEntity> =
        dao.getPending(nowUtc)

    suspend fun getPendingByKind(kind: String, nowUtc: Long): List<ScheduledAlarmEntity> =
        dao.getPendingByKind(kind, nowUtc)

    suspend fun upsert(alarm: ScheduledAlarmEntity) = dao.upsert(alarm)

    suspend fun deleteByRequestCode(requestCode: Int) = dao.deleteByRequestCode(requestCode)

    suspend fun pruneExpired(nowUtc: Long) = dao.pruneExpired(nowUtc)
}

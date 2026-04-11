package com.navpanchang.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navpanchang.data.db.entities.ScheduledAlarmEntity

@Dao
interface AlarmDao {

    @Query("SELECT * FROM scheduled_alarms WHERE fireAtUtc > :nowUtc ORDER BY fireAtUtc ASC")
    suspend fun getPending(nowUtc: Long): List<ScheduledAlarmEntity>

    @Query("SELECT * FROM scheduled_alarms WHERE kind = :kind AND fireAtUtc > :nowUtc")
    suspend fun getPendingByKind(kind: String, nowUtc: Long): List<ScheduledAlarmEntity>

    @Upsert
    suspend fun upsert(alarm: ScheduledAlarmEntity)

    @Query("DELETE FROM scheduled_alarms WHERE requestCode = :requestCode")
    suspend fun deleteByRequestCode(requestCode: Int)

    @Query("DELETE FROM scheduled_alarms WHERE fireAtUtc < :nowUtc")
    suspend fun pruneExpired(nowUtc: Long)
}

package com.navpanchang.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A single scheduled AlarmManager PendingIntent — one row per (occurrence, kind) tuple.
 *
 * `requestCode` is stable and derived from `(occurrenceId, kind)` so cancellations and
 * re-arms (after boot, timezone change, or location geofence trigger) can find and replace
 * the exact PendingIntent without duplicates.
 */
@Entity(
    tableName = "scheduled_alarms",
    foreignKeys = [
        ForeignKey(
            entity = OccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ScheduledAlarmEntity(
    @PrimaryKey val requestCode: Int,
    val occurrenceId: Long,
    val kind: String,               // "PLANNER" | "OBSERVER" | "PARANA"
    val fireAtUtc: Long,
    val channelId: String,
    val pendingStatus: String? = null   // e.g. "READY_FOR_TOMORROW" for late-subscription gate
)

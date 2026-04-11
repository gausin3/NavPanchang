package com.navpanchang.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single computed occurrence of an event on a specific date for a specific location tier.
 *
 * Two-tier lookahead model:
 *  - `locationTag = "HOME"` / `isHighPrecision = false` → powers the Calendar UI (24-month window).
 *  - `locationTag = "CURRENT"` / `isHighPrecision = true` → powers alarms (30–60 day window).
 *
 * Queries prefer high-precision rows and fall back to HOME — see `OccurrenceDao.getNextOccurrence`
 * (Phase 3). Diagnostic flags (`shiftedDueToViddha`, `isKshayaContext`, `isAdhik`) let the UI
 * explain anomalies honestly.
 *
 * See TECH_DESIGN.md §Two-tier lookahead + §Vrat logic.
 */
@Entity(
    tableName = "occurrences",
    foreignKeys = [
        ForeignKey(
            entity = EventDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId", "dateLocal", "isHighPrecision"]),
        Index(value = ["eventId", "dateLocal", "locationTag"], unique = true)
    ]
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val dateLocal: String,          // YYYY-MM-DD in the location's tz
    val sunriseUtc: Long,
    val observanceUtc: Long,        // fire-target for OBSERVER alarm
    val paranaStartUtc: Long? = null,
    val paranaEndUtc: Long? = null,
    val shiftedDueToViddha: Boolean = false,
    val isKshayaContext: Boolean = false,
    val lunarMonth: String? = null, // "Chaitra" ... "Phalguna"
    val isAdhik: Boolean = false,
    val locationTag: String,        // "HOME" | "CURRENT"
    val isHighPrecision: Boolean,
    val computedAt: Long
)

package com.navpanchang.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * User's per-event subscription state.
 *
 * Kept in a separate table from [EventDefinitionEntity] so the event catalog can be
 * upserted on app upgrade without losing the user's enabled/custom-time preferences.
 */
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = EventDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SubscriptionEntity(
    @PrimaryKey val eventId: String,
    val enabled: Boolean,
    val customPlannerHhmm: String? = null,
    val customSoundId: String? = null,
    val plannerEnabled: Boolean = true,
    val observerEnabled: Boolean = true,
    val paranaEnabled: Boolean = true
)

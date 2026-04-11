package com.navpanchang.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Master data row for a subscribable event (Ekadashi, Purnima, ...).
 *
 * Seeded from assets/events.json via [com.navpanchang.data.seed.EventCatalogSyncer]
 * using an MDM-style versioned upsert: a new catalog version overwrites rows with
 * the latest rule params while preserving the user's [SubscriptionEntity] state.
 *
 * See TECH_DESIGN.md §Seed data governance.
 */
@Entity(tableName = "event_definitions")
data class EventDefinitionEntity(
    @PrimaryKey val id: String,
    val seedVersion: Int,
    val nameEn: String,
    val nameHi: String,
    val category: String,          // "vrat", "puja", "festival"
    val ruleType: String,          // "TithiAtSunrise", "EveningTithi", "TithiInLunarMonth"
    val ruleParamsJson: String,    // JSON-encoded rule params — Phase 2 parses this
    val vratLogic: Boolean,        // true → apply Dashami-Viddha shift
    val observeInAdhik: Boolean,   // true → emit in Adhik Maas, false → suppress
    val hasParana: Boolean,        // true → compute Parana window + schedule PARANA alarm
    val defaultPlannerTimeHhmm: String,
    val defaultObserverAnchor: String, // "SUNRISE" | "EVENING"
    val defaultSoundId: String,
    val deprecated: Boolean = false,
    val migrationNote: String? = null
)

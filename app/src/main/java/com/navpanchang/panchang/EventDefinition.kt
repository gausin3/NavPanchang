package com.navpanchang.panchang

/**
 * In-memory, parsed representation of an event from `events.json` / the `event_definitions`
 * table.
 *
 * **Why the duplication with `EventDefinitionEntity`:** the Room entity holds the raw
 * JSON-serialized rule params (`ruleParamsJson`) because Room doesn't know how to persist
 * a sealed [EventRule] hierarchy directly. [EventDefinition] is the deserialized form that
 * [OccurrenceComputer] actually works with. `EventRuleParser.fromEntity` does the mapping.
 */
data class EventDefinition(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val category: EventCategory,
    val rule: EventRule,
    val observeInAdhik: Boolean,
    val hasParana: Boolean,
    val defaultPlannerTimeHhmm: String,
    val defaultObserverAnchor: ObserverAnchor,
    val defaultSoundId: String
) {
    /** Convenience: does this rule carry vrat (Dashami-Viddha) semantics? */
    val isVrat: Boolean
        get() = rule is EventRule.TithiAtSunrise && rule.vratLogic
}

enum class EventCategory {
    /** Religious observance involving fasting (Ekadashi, Purnima vrat). */
    VRAT,

    /** Ritual / puja that doesn't involve fasting (Pradosh). */
    PUJA,

    /** Annual festival (Mahashivratri, Diwali, Holi). */
    FESTIVAL;

    companion object {
        fun fromString(s: String): EventCategory = when (s.lowercase()) {
            "vrat" -> VRAT
            "puja" -> PUJA
            "festival" -> FESTIVAL
            else -> throw IllegalArgumentException("Unknown category: $s")
        }
    }
}

/** Whether an event is anchored to sunrise or sunset on its observation date. */
enum class ObserverAnchor {
    SUNRISE,
    EVENING;

    companion object {
        fun fromString(s: String): ObserverAnchor = when (s.uppercase()) {
            "SUNRISE" -> SUNRISE
            "EVENING" -> EVENING
            else -> throw IllegalArgumentException("Unknown observer anchor: $s")
        }
    }
}

package com.navpanchang.panchang

import com.navpanchang.data.db.entities.EventDefinitionEntity
import org.json.JSONObject

/**
 * Converts between the on-disk representation of an event rule (stringy JSON in
 * `event_definitions.rule_params_json` / `assets/events.json`) and the typed
 * [EventRule] sealed hierarchy used by [OccurrenceComputer].
 *
 * Separating parse from execution lets us keep [OccurrenceComputer] pure and fast —
 * it never has to touch JSON during a 24-month lookahead loop.
 */
object EventRuleParser {

    /**
     * Convert a Room-backed [EventDefinitionEntity] into a domain-level [EventDefinition].
     * Throws [IllegalArgumentException] if the rule params are malformed — upgrade
     * failures during `EventCatalogSyncer` should surface immediately rather than
     * silently producing a broken catalog.
     */
    fun fromEntity(entity: EventDefinitionEntity): EventDefinition {
        val rule = parseRule(entity.ruleType, entity.ruleParamsJson, entity.vratLogic)
        return EventDefinition(
            id = entity.id,
            nameEn = entity.nameEn,
            nameHi = entity.nameHi,
            category = EventCategory.fromString(entity.category),
            rule = rule,
            observeInAdhik = entity.observeInAdhik,
            hasParana = entity.hasParana,
            defaultPlannerTimeHhmm = entity.defaultPlannerTimeHhmm,
            defaultObserverAnchor = ObserverAnchor.fromString(entity.defaultObserverAnchor),
            defaultSoundId = entity.defaultSoundId
        )
    }

    /** Parse a single rule from its `ruleType` discriminator and the JSON params blob. */
    fun parseRule(ruleType: String, ruleParamsJson: String, vratLogicHint: Boolean): EventRule {
        val params = JSONObject(ruleParamsJson)
        return when (ruleType) {
            "TithiAtSunrise" -> EventRule.TithiAtSunrise(
                tithiIndex = params.getInt("tithiIndex"),
                // The entity's `vratLogic` column is the authoritative source, but the
                // JSON may also carry it for round-trip correctness.
                vratLogic = if (params.has("vratLogic")) {
                    params.getBoolean("vratLogic")
                } else {
                    vratLogicHint
                }
            )

            "EveningTithi" -> EventRule.EveningTithi(
                tithiIndex = params.getInt("tithiIndex")
            )

            "TithiInLunarMonth" -> EventRule.TithiInLunarMonth(
                tithiIndex = params.getInt("tithi"),
                lunarMonth = LunarMonth.valueOf(params.getString("lunarMonth")),
                suppressInAdhik = if (params.has("suppressInAdhik")) {
                    params.getBoolean("suppressInAdhik")
                } else {
                    true
                }
            )

            else -> throw IllegalArgumentException("Unknown rule type: $ruleType")
        }
    }
}

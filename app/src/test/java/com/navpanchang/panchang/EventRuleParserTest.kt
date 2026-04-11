package com.navpanchang.panchang

import com.navpanchang.data.db.entities.EventDefinitionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [EventRuleParser] — verifies that every rule type in the bundled
 * [`events.json`](../../../../../main/assets/events.json) catalog round-trips through
 * [EventRuleParser.parseRule] correctly.
 */
class EventRuleParserTest {

    @Test
    fun `parseRule handles TithiAtSunrise with vratLogic true`() {
        val rule = EventRuleParser.parseRule(
            ruleType = "TithiAtSunrise",
            ruleParamsJson = """{"tithiIndex": 11, "vratLogic": true}""",
            vratLogicHint = true
        )
        assertTrue(rule is EventRule.TithiAtSunrise)
        rule as EventRule.TithiAtSunrise
        assertEquals(11, rule.tithiIndex)
        assertTrue(rule.vratLogic)
    }

    @Test
    fun `parseRule handles TithiAtSunrise without explicit vratLogic`() {
        val rule = EventRuleParser.parseRule(
            ruleType = "TithiAtSunrise",
            ruleParamsJson = """{"tithiIndex": 15}""",
            vratLogicHint = false
        )
        assertTrue(rule is EventRule.TithiAtSunrise)
        rule as EventRule.TithiAtSunrise
        assertEquals(15, rule.tithiIndex)
        assertTrue("Hint should apply when JSON omits the field", !rule.vratLogic)
    }

    @Test
    fun `parseRule handles EveningTithi`() {
        val rule = EventRuleParser.parseRule(
            ruleType = "EveningTithi",
            ruleParamsJson = """{"tithiIndex": 13}""",
            vratLogicHint = false
        )
        assertTrue(rule is EventRule.EveningTithi)
        assertEquals(13, (rule as EventRule.EveningTithi).tithiIndex)
    }

    @Test
    fun `parseRule handles TithiInLunarMonth with default suppressInAdhik`() {
        val rule = EventRuleParser.parseRule(
            ruleType = "TithiInLunarMonth",
            ruleParamsJson = """{"tithi": 29, "lunarMonth": "Phalguna"}""",
            vratLogicHint = false
        )
        assertTrue(rule is EventRule.TithiInLunarMonth)
        rule as EventRule.TithiInLunarMonth
        assertEquals(29, rule.tithiIndex)
        assertEquals(LunarMonth.Phalguna, rule.lunarMonth)
        assertTrue("Default should suppress in Adhik", rule.suppressInAdhik)
    }

    @Test
    fun `parseRule rejects unknown rule types`() {
        assertThrows(IllegalArgumentException::class.java) {
            EventRuleParser.parseRule(
                ruleType = "TithiInEquinox",
                ruleParamsJson = "{}",
                vratLogicHint = false
            )
        }
    }

    @Test
    fun `fromEntity constructs a full EventDefinition`() {
        val entity = EventDefinitionEntity(
            id = "shukla_ekadashi",
            seedVersion = 1,
            nameEn = "Shukla Ekadashi",
            nameHi = "शुक्ल एकादशी",
            category = "vrat",
            ruleType = "TithiAtSunrise",
            ruleParamsJson = """{"tithiIndex": 11, "vratLogic": true}""",
            vratLogic = true,
            observeInAdhik = true,
            hasParana = true,
            defaultPlannerTimeHhmm = "20:00",
            defaultObserverAnchor = "SUNRISE",
            defaultSoundId = "ritual_temple_bell"
        )
        val def = EventRuleParser.fromEntity(entity)
        assertEquals("shukla_ekadashi", def.id)
        assertEquals(EventCategory.VRAT, def.category)
        assertEquals(ObserverAnchor.SUNRISE, def.defaultObserverAnchor)
        assertTrue(def.isVrat)
        assertTrue(def.rule is EventRule.TithiAtSunrise)
        assertEquals(11, (def.rule as EventRule.TithiAtSunrise).tithiIndex)
    }
}

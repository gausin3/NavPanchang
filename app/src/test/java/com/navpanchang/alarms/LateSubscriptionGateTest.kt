package com.navpanchang.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [LateSubscriptionGate]. No Android runtime needed — the gate is
 * deliberately side-effect-free so it can be fully exercised as simple data-in / decision-out.
 */
class LateSubscriptionGateTest {

    @Test
    fun `future alarm returns Schedule`() {
        val now = 1_000_000_000_000L
        val decision = LateSubscriptionGate.decide(
            fireAtUtc = now + 60_000,
            nowUtc = now
        )
        assertEquals(LateSubscriptionGate.Decision.Schedule, decision)
    }

    @Test
    fun `alarm at now returns Schedule`() {
        val now = 1_000_000_000_000L
        val decision = LateSubscriptionGate.decide(fireAtUtc = now, nowUtc = now)
        assertEquals(LateSubscriptionGate.Decision.Schedule, decision)
    }

    @Test
    fun `alarm within default slack window returns Schedule`() {
        val now = 1_000_000_000_000L
        val decision = LateSubscriptionGate.decide(
            fireAtUtc = now - 30_000, // 30 s in the past, slack is 60 s
            nowUtc = now
        )
        assertEquals(LateSubscriptionGate.Decision.Schedule, decision)
    }

    @Test
    fun `alarm past the slack window returns Skip`() {
        val now = 1_000_000_000_000L
        val decision = LateSubscriptionGate.decide(
            fireAtUtc = now - 120_000, // 2 minutes in the past
            nowUtc = now
        )
        assertTrue("Expected Skip, got $decision", decision is LateSubscriptionGate.Decision.Skip)
    }

    @Test
    fun `Skip reason mentions how far in the past the alarm is`() {
        val now = 1_000_000_000_000L
        val decision = LateSubscriptionGate.decide(
            fireAtUtc = now - 3_600_000, // 1 hour in the past
            nowUtc = now
        )
        require(decision is LateSubscriptionGate.Decision.Skip)
        assertTrue(
            "Reason should mention seconds: ${decision.reason}",
            decision.reason.contains("3600s")
        )
    }

    @Test
    fun `custom slack window overrides default`() {
        val now = 1_000_000_000_000L
        val decision = LateSubscriptionGate.decide(
            fireAtUtc = now - 300_000, // 5 minutes in the past
            nowUtc = now,
            slackMillis = 600_000 // 10 minute tolerance
        )
        assertEquals(LateSubscriptionGate.Decision.Schedule, decision)
    }
}

package com.navpanchang.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure unit tests for [AlarmKind] and the request-code derivation in [AlarmScheduler].
 */
class AlarmKindTest {

    @Test
    fun `all three kinds are defined`() {
        assertEquals(3, AlarmKind.entries.size)
    }

    @Test
    fun `fromName round-trips each kind`() {
        for (kind in AlarmKind.entries) {
            assertEquals(kind, AlarmKind.fromName(kind.name))
        }
    }

    @Test
    fun `fromName rejects unknown names`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlarmKind.fromName("NOT_A_KIND")
        }
    }

    // ------------------------------------------------------------------
    // requestCodeFor is a static helper on AlarmScheduler — verifying here keeps the
    // whole derivation covered without needing a full Android context.
    // ------------------------------------------------------------------

    @Test
    fun `requestCodeFor is distinct for each kind of the same occurrence`() {
        val id = 100L
        val planner = AlarmScheduler.requestCodeFor(id, AlarmKind.PLANNER)
        val observer = AlarmScheduler.requestCodeFor(id, AlarmKind.OBSERVER)
        val parana = AlarmScheduler.requestCodeFor(id, AlarmKind.PARANA)
        assertEquals(300, planner)
        assertEquals(301, observer)
        assertEquals(302, parana)
    }

    @Test
    fun `requestCodeFor is distinct across occurrences`() {
        val a = AlarmScheduler.requestCodeFor(1L, AlarmKind.OBSERVER)
        val b = AlarmScheduler.requestCodeFor(2L, AlarmKind.OBSERVER)
        assertEquals(4, a)
        assertEquals(7, b)
    }
}

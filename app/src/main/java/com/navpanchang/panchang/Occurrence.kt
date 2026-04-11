package com.navpanchang.panchang

import java.time.LocalDate

/**
 * A computed occurrence of a subscribable event on a specific local date at a specific
 * location. The domain counterpart of
 * [com.navpanchang.data.db.entities.OccurrenceEntity] — conversion between the two lives
 * in the repository layer.
 *
 * This is what [OccurrenceComputer] produces and what alarms are scheduled against.
 */
data class Occurrence(
    val eventId: String,

    /** Local date of observance in the location's tz. */
    val dateLocal: LocalDate,

    /** Sunrise on [dateLocal] as epoch-millis UTC. */
    val sunriseUtc: Long,

    /**
     * The Observer alarm fire target. Usually equals [sunriseUtc]; for events with
     * `defaultObserverAnchor = EVENING` it's local sunset (Phase 2 uses sunrise for
     * all events; sunset anchoring lands in Phase 2b).
     */
    val observanceUtc: Long,

    /** Parana window start — only set for Ekadashi. */
    val paranaStartUtc: Long? = null,

    /** Parana window end — only set for Ekadashi. */
    val paranaEndUtc: Long? = null,

    /** True iff [VratLogic.applyDashamiViddha] moved this occurrence from the nominal day. */
    val shiftedDueToViddha: Boolean = false,

    /**
     * True iff the target tithi was Kshaya (began and ended between two sunrises) and
     * this occurrence was emitted via the safe-fallback rule (longest-prevailing day).
     * The UI should surface a `[!]` indicator and an explanation.
     */
    val isKshayaContext: Boolean = false,

    /** The lunar month this occurrence falls in, if known. */
    val lunarMonth: LunarMonth? = null,

    /** True iff [lunarMonth] is classified as Adhik by [AdhikMaasDetector]. */
    val isAdhik: Boolean = false,

    /** Tag for the two-tier lookahead — "HOME" or "CURRENT". Set by the caller. */
    val locationTag: String,

    /** True for Tier 2 CURRENT rows, false for Tier 1 HOME. */
    val isHighPrecision: Boolean
)

package com.navpanchang.ui.subscriptions

import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.Occurrence

/**
 * The live state of the [PreparationCard] above the subscription list.
 *
 * See TECH_DESIGN.md §Home — Subscriptions.
 *
 *  * [Hidden] — no subscribed event is within ~36 hours.
 *  * [Preparing] — yellow card, shown from ~36h before the next observed event up to
 *    that event's sunrise. Copy: "Preparing for tomorrow: <event>".
 *  * [Observing] — green card, shown from sunrise on the event day through the end of
 *    the event (Parana window close for vrats, sunset for others). Copy:
 *    "Observing today: <event>".
 *  * [Parana] — cyan card, only for Ekadashi. Shown during the Parana window on the
 *    morning after the vrat. Copy: "Parana window: break your fast between X and Y".
 */
sealed class PreparationCardState {

    object Hidden : PreparationCardState()

    data class Preparing(
        val event: EventDefinition,
        val occurrence: Occurrence,
        /** Hours until the occurrence's sunrise. */
        val hoursUntilSunrise: Long
    ) : PreparationCardState()

    data class Observing(
        val event: EventDefinition,
        val occurrence: Occurrence
    ) : PreparationCardState()

    data class Parana(
        val event: EventDefinition,
        val occurrence: Occurrence,
        val startUtc: Long,
        val endUtc: Long
    ) : PreparationCardState()

    companion object {
        /**
         * Derive the state from a sorted list of upcoming occurrences at the current
         * instant. Takes the *nearest* subscribed occurrence and classifies which
         * "phase" of the observation life-cycle we're in.
         *
         * @param nowUtc the current instant (epoch-millis UTC).
         * @param candidates the list of (occurrence, definition) pairs to consider.
         *   Does NOT need to be sorted — we pick the best candidate internally.
         */
        fun from(
            nowUtc: Long,
            candidates: List<Pair<Occurrence, EventDefinition>>
        ): PreparationCardState {
            // 1. Is there an active Parana window? Parana takes priority over Preparing.
            val active = candidates.firstOrNull { (occ, _) ->
                occ.paranaStartUtc != null && occ.paranaEndUtc != null &&
                    nowUtc in occ.paranaStartUtc..occ.paranaEndUtc
            }
            if (active != null) {
                val (occ, ev) = active
                return Parana(
                    event = ev,
                    occurrence = occ,
                    startUtc = occ.paranaStartUtc!!,
                    endUtc = occ.paranaEndUtc!!
                )
            }

            // 2. Is an event *happening today*? From sunrise through the end of its
            //    tithi-specific window (Parana end for vrats, 24h for others).
            val observing = candidates.firstOrNull { (occ, _) ->
                val end = occ.paranaEndUtc ?: (occ.sunriseUtc + DAY_MILLIS)
                nowUtc in occ.sunriseUtc..end
            }
            if (observing != null) {
                return Observing(event = observing.second, occurrence = observing.first)
            }

            // 3. Is there an event within PREPARATION_WINDOW_HOURS?
            val upcoming = candidates
                .filter { (occ, _) -> occ.sunriseUtc > nowUtc }
                .minByOrNull { (occ, _) -> occ.sunriseUtc - nowUtc }
            if (upcoming != null) {
                val hours = (upcoming.first.sunriseUtc - nowUtc) / HOUR_MILLIS
                if (hours <= PREPARATION_WINDOW_HOURS) {
                    return Preparing(
                        event = upcoming.second,
                        occurrence = upcoming.first,
                        hoursUntilSunrise = hours
                    )
                }
            }

            return Hidden
        }

        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val DAY_MILLIS = 24L * HOUR_MILLIS

        /** Yellow "Preparing" card is shown up to 36 hours before an event's sunrise. */
        const val PREPARATION_WINDOW_HOURS = 36L
    }
}

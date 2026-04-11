package com.navpanchang.alarms

/**
 * The three kinds of alarm NavPanchang can schedule per [com.navpanchang.panchang.Occurrence].
 *
 *  * [PLANNER] — fires the *evening before* the event (wall-clock local time, usually
 *    8:00 PM). Low-importance reminder for the user to prepare a meal, inform the
 *    household, etc. Travels with the device timezone — see [LocaleChangeReceiver].
 *
 *  * [OBSERVER] — fires at *local sunrise* on the day of the event (GPS-anchored).
 *    High-importance ritual alarm with a bell / sankh / toll / Om sound. The actual
 *    fire target is [com.navpanchang.panchang.Occurrence.observanceUtc].
 *
 *  * [PARANA] — fires at *sunrise on Dvadashi* (day after an Ekadashi fast) with the
 *    fast-breaking window. Only scheduled for Ekadashi-class occurrences where
 *    [com.navpanchang.panchang.Occurrence.paranaStartUtc] is non-null.
 *
 * **Request codes:** persisted into `scheduled_alarms` via
 * `(occurrenceId * 3 + kind.ordinal).toInt()`. That formula is stable across restarts
 * so boot / timezone-change re-scheduling can find the exact `PendingIntent` that was
 * previously posted and cancel/replace it.
 */
enum class AlarmKind {
    PLANNER,
    OBSERVER,
    PARANA;

    companion object {
        fun fromName(name: String): AlarmKind = valueOf(name)
    }
}

package com.navpanchang.alarms

/**
 * Late-subscription gating logic.
 *
 * **The problem:** if the user enables an Ekadashi subscription at 10:00 PM Monday for
 * an Ekadashi on Tuesday, the 8:00 PM Monday Planner alarm has already passed. We must
 * NOT fire it retroactively — that would be confusing. We must also NOT silently drop
 * the whole subscription — the Observer alarm on Tuesday sunrise is still valid and
 * valuable.
 *
 * **The solution:** [decide] takes the alarm's target fire time and the current time,
 * and returns:
 *  * [Decision.Schedule] — normal case, go ahead and post to `AlarmManager`.
 *  * [Decision.Skip] — the fire time already passed; don't schedule.
 *
 * The caller (typically [AlarmScheduler]) then treats `Skip` as a non-error — it's
 * expected behavior for short-notice subscriptions.
 *
 * See TECH_DESIGN.md §Late-subscription gate.
 */
object LateSubscriptionGate {

    sealed class Decision {
        object Schedule : Decision()
        data class Skip(val reason: String) : Decision()
    }

    /**
     * @param fireAtUtc the instant the alarm should fire.
     * @param nowUtc the current instant.
     * @param slackMillis how far in the past an alarm can be and still be scheduled
     *   (AlarmManager will fire it immediately). Default 60 s — covers the race between
     *   the worker computing and `AlarmManager.setExactAndAllowWhileIdle` being called.
     */
    fun decide(
        fireAtUtc: Long,
        nowUtc: Long,
        slackMillis: Long = DEFAULT_SLACK_MILLIS
    ): Decision =
        if (fireAtUtc >= nowUtc - slackMillis) {
            Decision.Schedule
        } else {
            Decision.Skip("fireAtUtc=$fireAtUtc is ${(nowUtc - fireAtUtc) / 1000}s in the past")
        }

    const val DEFAULT_SLACK_MILLIS: Long = 60_000L
}

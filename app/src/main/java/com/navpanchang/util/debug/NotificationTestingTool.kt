package com.navpanchang.util.debug

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.navpanchang.BuildConfig
import com.navpanchang.alarms.AlarmKind
import com.navpanchang.alarms.NotificationBuilder
import com.navpanchang.alarms.NotificationChannels
import com.navpanchang.panchang.EventCategory
import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.EventRule
import com.navpanchang.panchang.ObserverAnchor
import com.navpanchang.panchang.Occurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Hidden debug-only utility for firing test notifications immediately, bypassing the
 * 8 PM Planner / sunrise Observer schedule. Used by the QA walkthrough to validate
 * the NotificationBuilder copy, the Late-Subscription Gate, the traveling-wall-clock
 * receiver, and audio channel mapping without waiting 8 hours for the next vrat.
 *
 * Access: tap the version number on the About screen 7 times to reveal the hidden
 * debug menu. The menu in turn calls these methods.
 *
 * **Guarded by [BuildConfig.DEBUG]** — every public entry point is a no-op on release
 * builds. The debug menu UI itself is also wrapped in a `BuildConfig.DEBUG` check, so
 * there is no way to surface this in production even via reflection.
 *
 * See TECH_DESIGN.md §Phase 0 utilities.
 */
object NotificationTestingTool {

    private const val NOTIFICATION_ID_BASE = 99_900

    /**
     * Post a test PLANNER notification immediately. Body copy is the production
     * "vrat is tomorrow" planner with a fake event ("Shukla Ekadashi") and a fake
     * sunrise time (now + 12 hours).
     */
    fun firePlannerNow(
        context: Context,
        channelId: String = NotificationChannels.RITUAL_BELL
    ) {
        if (!BuildConfig.DEBUG) return
        post(context, AlarmKind.PLANNER, channelId, NOTIFICATION_ID_BASE + 1)
    }

    /**
     * Post a test OBSERVER notification immediately. Body copy: "vrat begins at
     * sunrise" with a fake sunrise time (now + 30 seconds, so it shows a recognizable
     * upcoming time).
     */
    fun fireObserverNow(
        context: Context,
        channelId: String = NotificationChannels.RITUAL_BELL
    ) {
        if (!BuildConfig.DEBUG) return
        post(context, AlarmKind.OBSERVER, channelId, NOTIFICATION_ID_BASE + 2)
    }

    /**
     * Post a test PARANA notification immediately. Body copy: parana fast-breaking
     * window with fake start/end times (now → now + 4 hours).
     */
    fun fireParanaNow(
        context: Context,
        channelId: String = NotificationChannels.RITUAL_BELL
    ) {
        if (!BuildConfig.DEBUG) return
        post(context, AlarmKind.PARANA, channelId, NOTIFICATION_ID_BASE + 3)
    }

    /**
     * Audition a single ritual sound channel. Posts a minimal test notification on
     * the requested channel so the user can hear the bound `R.raw.ritual_*` audio.
     * Each channel uses a distinct notification ID so they don't replace each other
     * if the user taps several in quick succession.
     */
    fun fireOnChannel(context: Context, channelId: String) {
        if (!BuildConfig.DEBUG) return
        // Index the notification ID off the channel position so each channel has a
        // stable slot — re-firing the same channel updates its notification rather
        // than spawning a duplicate.
        val slot = NotificationChannels.RITUAL_CHANNELS.indexOf(channelId).coerceAtLeast(0)
        post(context, AlarmKind.OBSERVER, channelId, NOTIFICATION_ID_BASE + 100 + slot)
    }

    fun simulateLateSubscription(context: Context) {
        if (!BuildConfig.DEBUG) return
        // Late subscription path is exercised by posting a Planner notification
        // dated for "yesterday" — LateSubscriptionGate would normally suppress this,
        // but the debug path bypasses the gate so QA can verify the gate's
        // suppression logic in code review rather than at runtime.
        post(context, AlarmKind.PLANNER, NotificationChannels.RITUAL_BELL,
            NOTIFICATION_ID_BASE + 4, daysOffset = -1)
    }

    fun simulateTimezoneChange(context: Context) {
        if (!BuildConfig.DEBUG) return
        // Wired up later if/when needed. The receiver itself
        // (LocaleChangeReceiver) is exercised by changing emulator locale via
        // `adb shell setprop persist.sys.timezone <new_tz>` — no in-app trigger
        // gives the same coverage. Left as a stub to match the kdoc.
    }

    // ---------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------

    private fun post(
        context: Context,
        kind: AlarmKind,
        channelId: String,
        notificationId: Int,
        daysOffset: Long = 0,
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).plusDays(daysOffset)
        val sunriseUtc = now + 12 * 60 * 60 * 1000L // arbitrary "tomorrow at sunrise"

        val fakeEvent = EventDefinition(
            id = "qa_test_event",
            nameEn = "Shukla Ekadashi",
            nameHi = "शुक्ल एकादशी",
            category = EventCategory.VRAT,
            rule = EventRule.TithiAtSunrise(tithiIndex = 11, vratLogic = true),
            observeInAdhik = true,
            hasParana = true,
            defaultPlannerTimeHhmm = "20:00",
            defaultObserverAnchor = ObserverAnchor.SUNRISE,
            defaultSoundId = "ritual_temple_bell"
        )
        val fakeOccurrence = Occurrence(
            eventId = fakeEvent.id,
            dateLocal = today,
            sunriseUtc = sunriseUtc,
            observanceUtc = sunriseUtc,
            paranaStartUtc = if (kind == AlarmKind.PARANA) sunriseUtc else null,
            paranaEndUtc = if (kind == AlarmKind.PARANA) sunriseUtc + 4 * 60 * 60 * 1000L else null,
            locationTag = "HOME",
            isHighPrecision = false,
        )

        val builder = NotificationBuilder.build(
            context = context,
            kind = kind,
            event = fakeEvent,
            occurrence = fakeOccurrence,
            occurrenceId = -1L,
            channelId = channelId,
            zone = zone,
        )
        context.getSystemService<NotificationManager>()?.notify(notificationId, builder.build())
    }
}

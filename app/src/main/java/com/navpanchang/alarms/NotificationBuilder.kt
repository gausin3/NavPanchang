package com.navpanchang.alarms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.navpanchang.MainActivity
import com.navpanchang.R
import com.navpanchang.panchang.EventDefinition
import com.navpanchang.panchang.Occurrence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds [NotificationCompat] instances for each [AlarmKind]. Copy is looked up via
 * string resources so it respects the user's language setting (en/hi) and renders
 * Devanagari correctly on an English-locale phone.
 *
 * **Why this is a separate class from [AlarmScheduler]:** posting notifications lives
 * in the receiver (runs in a receiver context after the alarm fires), while scheduling
 * lives in a `@Singleton` that runs in the worker/UI context. Keeping the builder pure
 * (no DI) lets either side use it.
 */
object NotificationBuilder {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun build(
        context: Context,
        kind: AlarmKind,
        event: EventDefinition,
        occurrence: Occurrence,
        occurrenceId: Long,
        channelId: String,
        zone: ZoneId
    ): NotificationCompat.Builder {
        val title = buildTitle(context, kind, event)
        val body = buildBody(context, kind, event, occurrence, zone)

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(
                if (kind == AlarmKind.PLANNER)
                    NotificationCompat.PRIORITY_DEFAULT
                else
                    NotificationCompat.PRIORITY_HIGH
            )

        // Planner gets a "Remind me at Sunrise" safety-net action — taps schedule a
        // one-shot Observer alarm so the user is covered even if they forget to set up
        // observer notifications explicitly.
        if (kind == AlarmKind.PLANNER) {
            val remindIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_SNOOZE_TO_SUNRISE
                putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
                putExtra(AlarmReceiver.EXTRA_KIND, AlarmKind.OBSERVER.name)
            }
            val remindPending = PendingIntent.getBroadcast(
                context,
                AlarmScheduler.requestCodeFor(occurrenceId, AlarmKind.OBSERVER),
                remindIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    context.getString(R.string.notification_action_remind_at_sunrise),
                    remindPending
                ).build()
            )
        }

        return builder
    }

    private fun buildTitle(context: Context, kind: AlarmKind, event: EventDefinition): String =
        when (kind) {
            AlarmKind.PLANNER -> context.getString(
                R.string.notification_planner_title, event.nameEn
            )

            AlarmKind.OBSERVER -> context.getString(
                R.string.notification_observer_title, event.nameEn
            )

            AlarmKind.PARANA -> context.getString(
                R.string.notification_parana_title, event.nameEn
            )
        }

    private fun buildBody(
        context: Context,
        kind: AlarmKind,
        event: EventDefinition,
        occurrence: Occurrence,
        zone: ZoneId
    ): String {
        val sunriseText = formatTime(occurrence.sunriseUtc, zone)
        return when (kind) {
            AlarmKind.PLANNER -> context.getString(
                R.string.notification_planner_body,
                event.nameEn,
                sunriseText
            )

            AlarmKind.OBSERVER -> context.getString(
                R.string.notification_observer_body,
                event.nameEn,
                sunriseText
            )

            AlarmKind.PARANA -> {
                val start = occurrence.paranaStartUtc?.let { formatTime(it, zone) } ?: "—"
                val end = occurrence.paranaEndUtc?.let { formatTime(it, zone) } ?: "—"
                context.getString(R.string.notification_parana_body, start, end)
            }
        }
    }

    private fun formatTime(epochMillisUtc: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillisUtc).atZone(zone).format(TIME_FORMATTER)
}

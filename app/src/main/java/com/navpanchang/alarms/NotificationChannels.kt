package com.navpanchang.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.getSystemService
import com.navpanchang.R

/**
 * Notification channel definitions and creation logic.
 *
 * **Why four ritual channels** (one per audio variant): on Android 12+ a channel's
 * sound is immutable after creation. Deleting and recreating a channel to change its
 * sound would wipe the user's per-channel DND, vibration, and LED preferences. Instead
 * we pre-create one channel per audio variant up-front; the user's chosen sound simply
 * decides which channel a given alarm posts on. See
 * TECH_DESIGN.md §Audio customization.
 *
 * **Bundled audio (Phase 4 placeholder):** the channels currently use the system default
 * notification sound. Phase 5 will swap in four bundled `.ogg` files under
 * `app/src/main/res/raw/` — `ritual_temple_bell`, `ritual_sankh`, `ritual_bell_toll`,
 * `ritual_om_mantra`. When those land, the `SoundAsset` sealed values below will return
 * the actual `R.raw.*` URIs instead of `null`.
 */
object NotificationChannels {

    const val EVENT_REMINDERS = "channel_event_reminders"
    const val DAILY_BRIEFING = "channel_daily_briefing"

    const val RITUAL_BELL = "channel_ritual_bell"
    const val RITUAL_SANKH = "channel_ritual_sankh"
    const val RITUAL_TOLL = "channel_ritual_toll"
    const val RITUAL_OM = "channel_ritual_om"

    /** Default ritual channel ID if the user hasn't picked one yet. */
    const val DEFAULT_RITUAL = RITUAL_BELL

    /** Bell-toll vibration pattern: long-short-long-short-long (≈2.2 seconds). */
    val RITUAL_VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500, 200, 800)

    /**
     * Map an arbitrary sound ID (as stored on [com.navpanchang.panchang.EventDefinition]
     * or [com.navpanchang.data.db.entities.SubscriptionEntity]) to the channel it should
     * post on.
     */
    fun channelIdForSound(soundId: String?): String = when (soundId) {
        "ritual_sankh" -> RITUAL_SANKH
        "ritual_bell_toll" -> RITUAL_TOLL
        "ritual_om_mantra" -> RITUAL_OM
        "ritual_temple_bell", null -> RITUAL_BELL
        else -> RITUAL_BELL
    }

    /**
     * Idempotently create all notification channels. Safe to call on every cold start;
     * existing channels are left untouched (including any user DND / LED / vibration
     * overrides).
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        // Event reminders — low-importance Planner channel.
        manager.createNotificationChannel(
            NotificationChannel(
                EVENT_REMINDERS,
                context.getString(R.string.channel_event_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_event_reminders_desc)
                enableVibration(false)
                setShowBadge(true)
            }
        )

        // Daily briefing — low-importance summary.
        manager.createNotificationChannel(
            NotificationChannel(
                DAILY_BRIEFING,
                context.getString(R.string.channel_daily_briefing_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_daily_briefing_desc)
                enableVibration(false)
                setShowBadge(false)
            }
        )

        // Ritual channels — four variants, one per audio option.
        listOf(RITUAL_BELL, RITUAL_SANKH, RITUAL_TOLL, RITUAL_OM).forEach { channelId ->
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.channel_ritual_alarms_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_ritual_alarms_desc)
                    enableVibration(true)
                    vibrationPattern = RITUAL_VIBRATION_PATTERN
                    // Phase 5 will swap this for R.raw.ritual_*.ogg once the assets land.
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .build()
                    )
                    setShowBadge(true)
                }
            )
        }
    }
}

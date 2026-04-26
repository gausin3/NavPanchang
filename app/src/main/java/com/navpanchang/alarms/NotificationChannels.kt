package com.navpanchang.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.content.getSystemService
import com.navpanchang.R

/**
 * Notification channel definitions and creation logic.
 *
 * **Why five ritual channels** (one per audio variant): on Android 12+ a channel's
 * sound is immutable after creation. Deleting and recreating a channel to change its
 * sound would wipe the user's per-channel DND, vibration, and LED preferences. Instead
 * we pre-create one channel per audio variant up-front; the user's chosen sound simply
 * decides which channel a given alarm posts on. See
 * TECH_DESIGN.md §Audio customization.
 *
 * **Bundled audio:** each ritual channel is bound to a specific `R.raw.*` resource:
 *
 *  * `channel_ritual_bell`          → `R.raw.ritual_temple_bell`
 *  * `channel_ritual_sankh`         → `R.raw.ritual_sankh`
 *  * `channel_ritual_toll`          → `R.raw.ritual_bell_toll`
 *  * `channel_ritual_om`            → `R.raw.ritual_om_mantra`
 *  * `channel_ritual_singing_bowl`  → `R.raw.ritual_singing_bowl`
 *
 * Each file in `app/src/main/res/raw/` is mono 22.05 kHz 16-bit PCM, peak-normalized
 * to about -3 dBFS, ≤ 4 seconds, ≤ 200 KB. To replace one with a different recording,
 * drop a new file at the same path with the same filename and rebuild — no Kotlin
 * changes needed. **Post-v1.0.0 audio swaps** must also bump the channel ID
 * (e.g. `channel_ritual_bell` → `channel_ritual_bell_v2`) because Android caches
 * channel→sound URI bindings on first creation; existing users won't pick up new
 * audio at the same channel ID.
 */
object NotificationChannels {

    const val EVENT_REMINDERS = "channel_event_reminders"
    const val DAILY_BRIEFING = "channel_daily_briefing"

    const val RITUAL_BELL = "channel_ritual_bell"
    const val RITUAL_SANKH = "channel_ritual_sankh"
    const val RITUAL_TOLL = "channel_ritual_toll"
    const val RITUAL_OM = "channel_ritual_om"
    const val RITUAL_SINGING_BOWL = "channel_ritual_singing_bowl"

    /** Default ritual channel ID if the user hasn't picked one yet. */
    const val DEFAULT_RITUAL = RITUAL_BELL

    /**
     * The full ordered list of ritual channels. Public so the QA debug tool
     * (NotificationTestingTool) can iterate through them when auditioning sounds.
     * **The IDs above are the v1 public set** — when changing the audio behind any of
     * them after v1.0.0, suffix the ID with `_v2` so existing users get fresh channels
     * with the new sound (Android caches the channel→sound URI binding on first
     * creation; see MEMORY.md "channel-sound immutability").
     */
    val RITUAL_CHANNELS: List<String> = listOf(
        RITUAL_BELL, RITUAL_SANKH, RITUAL_TOLL, RITUAL_OM, RITUAL_SINGING_BOWL
    )

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
        "ritual_singing_bowl" -> RITUAL_SINGING_BOWL
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

        // Ritual channels — four variants, one per bundled audio asset.
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()

        RitualChannelSpec.ALL.forEach { spec ->
            manager.createNotificationChannel(
                NotificationChannel(
                    spec.id,
                    context.getString(R.string.channel_ritual_alarms_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_ritual_alarms_desc)
                    enableVibration(true)
                    vibrationPattern = RITUAL_VIBRATION_PATTERN
                    setSound(uriForRawResource(context, spec.rawResourceId), audioAttributes)
                    setShowBadge(true)
                }
            )
        }
    }

    /**
     * Build an `android.resource://<package>/<resourceId>` URI that
     * [NotificationChannel.setSound] can resolve at notification post time.
     */
    private fun uriForRawResource(context: Context, rawResourceId: Int): Uri =
        Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + context.packageName + "/" + rawResourceId
        )

    /**
     * Static table mapping each ritual channel to its bundled `R.raw.*` sound.
     * Keeping this as a data class list (rather than four ad-hoc `when` branches)
     * makes it trivial to add a fifth ritual variant later.
     */
    private data class RitualChannelSpec(val id: String, val rawResourceId: Int) {
        companion object {
            val ALL: List<RitualChannelSpec> = listOf(
                RitualChannelSpec(RITUAL_BELL, R.raw.ritual_temple_bell),
                RitualChannelSpec(RITUAL_SANKH, R.raw.ritual_sankh),
                RitualChannelSpec(RITUAL_TOLL, R.raw.ritual_bell_toll),
                RitualChannelSpec(RITUAL_OM, R.raw.ritual_om_mantra),
                RitualChannelSpec(RITUAL_SINGING_BOWL, R.raw.ritual_singing_bowl),
            )
        }
    }
}

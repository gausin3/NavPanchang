package com.navpanchang.alarms

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.navpanchang.R

/**
 * Static catalog of the five ritual sounds shipped in `app/src/main/res/raw/`.
 *
 * Each entry pairs:
 *  * [id] — the string ID stored on `EventDefinition.defaultSoundId` and
 *    `SubscriptionEntity.customSoundId`. Matches the `R.raw.*` filename without
 *    extension (e.g. `R.raw.ritual_temple_bell` → `"ritual_temple_bell"`).
 *  * [displayName] — a string resource for the user-facing label, looked up via
 *    `stringResource(...)` in Compose.
 *  * [rawResId] — the bundled `R.raw.*` resource, used by the preview player and
 *    by [NotificationChannels] when binding the channel sound URI.
 *
 * Adding a sixth sound = add a `.wav` file under `res/raw/`, add a new entry here,
 * add a string resource for its label, and bump `NotificationChannels.RITUAL_CHANNELS`.
 * No other code changes needed.
 */
data class RitualSound(
    val id: String,
    @StringRes val displayName: Int,
    @RawRes val rawResId: Int,
    val channelId: String
)

object RitualSounds {

    val ALL: List<RitualSound> = listOf(
        RitualSound(
            id = "ritual_temple_bell",
            displayName = R.string.ritual_sound_temple_bell,
            rawResId = R.raw.ritual_temple_bell,
            channelId = NotificationChannels.RITUAL_BELL
        ),
        RitualSound(
            id = "ritual_sankh",
            displayName = R.string.ritual_sound_sankh,
            rawResId = R.raw.ritual_sankh,
            channelId = NotificationChannels.RITUAL_SANKH
        ),
        RitualSound(
            id = "ritual_bell_toll",
            displayName = R.string.ritual_sound_bell_toll,
            rawResId = R.raw.ritual_bell_toll,
            channelId = NotificationChannels.RITUAL_TOLL
        ),
        RitualSound(
            id = "ritual_om_mantra",
            displayName = R.string.ritual_sound_om_mantra,
            rawResId = R.raw.ritual_om_mantra,
            channelId = NotificationChannels.RITUAL_OM
        ),
        RitualSound(
            id = "ritual_singing_bowl",
            displayName = R.string.ritual_sound_singing_bowl,
            rawResId = R.raw.ritual_singing_bowl,
            channelId = NotificationChannels.RITUAL_SINGING_BOWL
        ),
    )

    fun byId(id: String?): RitualSound =
        ALL.firstOrNull { it.id == id } ?: ALL.first()
}

package com.navpanchang.alarms

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for [NotificationChannels]. Verifies that:
 *  * All 6 channels (event reminders, daily briefing, 4 ritual variants) are created.
 *  * The channel importance levels match what the plan specifies.
 *  * [NotificationChannels.channelIdForSound] maps sound IDs to the correct channels.
 *  * `ensureChannels` is idempotent (safe to call on every cold start).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class NotificationChannelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager: NotificationManager = context.getSystemService()!!

    @Test
    fun `ensureChannels creates all seven channels`() {
        NotificationChannels.ensureChannels(context)

        val ids = notificationManager.notificationChannels.map { it.id }
        assertEquals(
            "Expected 7 channels, got ${ids.size}: $ids",
            7, ids.size
        )
        listOf(
            NotificationChannels.EVENT_REMINDERS,
            NotificationChannels.DAILY_BRIEFING,
            NotificationChannels.RITUAL_BELL,
            NotificationChannels.RITUAL_SANKH,
            NotificationChannels.RITUAL_TOLL,
            NotificationChannels.RITUAL_OM,
            NotificationChannels.RITUAL_SINGING_BOWL
        ).forEach { expected ->
            assertNotNull("Channel $expected should exist", notificationManager.getNotificationChannel(expected))
        }
    }

    @Test
    fun `event reminders channel has default importance`() {
        NotificationChannels.ensureChannels(context)
        val channel = notificationManager.getNotificationChannel(NotificationChannels.EVENT_REMINDERS)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel!!.importance)
    }

    @Test
    fun `daily briefing channel has low importance`() {
        NotificationChannels.ensureChannels(context)
        val channel = notificationManager.getNotificationChannel(NotificationChannels.DAILY_BRIEFING)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)
    }

    @Test
    fun `all five ritual channels have high importance`() {
        NotificationChannels.ensureChannels(context)
        for (id in NotificationChannels.RITUAL_CHANNELS) {
            val channel = notificationManager.getNotificationChannel(id)
            assertEquals(
                "Ritual channel $id must be high-importance",
                NotificationManager.IMPORTANCE_HIGH, channel!!.importance
            )
        }
    }

    @Test
    fun `calling ensureChannels twice does not duplicate channels`() {
        NotificationChannels.ensureChannels(context)
        NotificationChannels.ensureChannels(context)
        assertEquals(7, notificationManager.notificationChannels.size)
    }

    // ------------------------------------------------------------------
    // channelIdForSound mapping — pure, no Context needed, but we test alongside.
    // ------------------------------------------------------------------

    @Test
    fun `channelIdForSound maps each sound id to its channel`() {
        assertEquals(
            NotificationChannels.RITUAL_BELL,
            NotificationChannels.channelIdForSound("ritual_temple_bell")
        )
        assertEquals(
            NotificationChannels.RITUAL_SANKH,
            NotificationChannels.channelIdForSound("ritual_sankh")
        )
        assertEquals(
            NotificationChannels.RITUAL_TOLL,
            NotificationChannels.channelIdForSound("ritual_bell_toll")
        )
        assertEquals(
            NotificationChannels.RITUAL_OM,
            NotificationChannels.channelIdForSound("ritual_om_mantra")
        )
        assertEquals(
            NotificationChannels.RITUAL_SINGING_BOWL,
            NotificationChannels.channelIdForSound("ritual_singing_bowl")
        )
    }

    @Test
    fun `channelIdForSound null defaults to temple bell`() {
        assertEquals(NotificationChannels.RITUAL_BELL, NotificationChannels.channelIdForSound(null))
    }

    @Test
    fun `channelIdForSound unknown defaults to temple bell`() {
        assertEquals(
            NotificationChannels.RITUAL_BELL,
            NotificationChannels.channelIdForSound("ritual_from_future_version")
        )
    }

    @Test
    fun `ritual vibration pattern encodes bell-toll cadence`() {
        val pattern = NotificationChannels.RITUAL_VIBRATION_PATTERN
        // 6 entries: [0, long, short, long, short, long]
        assertEquals(6, pattern.size)
        assertEquals(0L, pattern[0])
        // Long pulses are longer than short gaps.
        assertEquals(true, pattern[1] > pattern[2])
        assertEquals(true, pattern[3] > pattern[4])
    }
}

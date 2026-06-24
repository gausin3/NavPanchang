package com.navpanchang.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms NavPanchang's `AlarmManager` exact alarms after device reboot or app
 * upgrade. Android wipes all PendingIntents on reboot; the `scheduled_alarms`
 * Room table survives, so we read it and replay every future alarm via
 * [AlarmScheduler.rearmAllPending].
 *
 * **Intents handled:**
 *
 *  * [Intent.ACTION_BOOT_COMPLETED] — canonical post-unlock boot. Full re-arm.
 *  * [Intent.ACTION_LOCKED_BOOT_COMPLETED] — direct-boot phase, pre-unlock.
 *    Room is in credential-encrypted storage by default, so [AlarmRepository]
 *    cannot read pending rows yet. We restrict to WorkManager kicks here; the
 *    matching [ACTION_USER_UNLOCKED][Intent.ACTION_USER_UNLOCKED] or
 *    [ACTION_BOOT_COMPLETED][Intent.ACTION_BOOT_COMPLETED] that follows will
 *    handle the actual alarm re-arm.
 *  * [Intent.ACTION_USER_UNLOCKED] — fires when the credential-encrypted
 *    storage becomes readable. Some OEMs delay BOOT_COMPLETED until well
 *    after unlock; USER_UNLOCKED is the earliest reliable point at which
 *    Room is queryable, so we re-arm here too. Idempotent — rearmAllPending
 *    uses `PendingIntent.FLAG_UPDATE_CURRENT`, so a duplicate call from a
 *    later BOOT_COMPLETED is a no-op.
 *  * [Intent.ACTION_MY_PACKAGE_REPLACED] — app upgrade. PendingIntents are
 *    cleared on most OEMs across the upgrade; re-arm immediately.
 *
 * **WorkManager kicks** ([RefreshScheduler.schedulePeriodic] +
 * [enqueueOneShot][RefreshScheduler.enqueueOneShot]) run on EVERY intent
 * regardless of unlock state — WorkManager persists across reboots and is
 * direct-boot aware enough for these idempotent enqueues.
 *
 * **Async safety:** [BroadcastReceiver.goAsync] is required because
 * [AlarmScheduler.rearmAllPending] is a `suspend` function reading Room. The
 * supervisor scope ensures one slow re-arm doesn't cancel a sibling.
 *
 * See TECH_DESIGN.md §Boot, timezone, and locale changes.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var refreshScheduler: RefreshScheduler
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        Log.i(TAG, "Received $action — re-arming alarms + refresh work.")

        // WorkManager kicks happen on every handled intent. WorkManager itself
        // is durable across reboot; these calls are idempotent (KEEP-policy on
        // the periodic, REPLACE on the one-shot).
        refreshScheduler.schedulePeriodic()
        refreshScheduler.enqueueOneShot()

        // LOCKED_BOOT_COMPLETED arrives BEFORE the user unlocks. Room lives in
        // credential-encrypted storage by default, so AlarmRepository cannot
        // read the scheduled_alarms table yet. Skip the re-arm in that case —
        // the USER_UNLOCKED or BOOT_COMPLETED that follows the unlock will
        // catch up.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i(TAG, "Locked-boot phase — deferring alarm re-arm until USER_UNLOCKED.")
            return
        }

        // Async — rearmAllPending() suspends on Room I/O.
        val pendingResult = goAsync()
        scope.launch {
            try {
                alarmScheduler.rearmAllPending()
            } catch (t: Throwable) {
                Log.e(TAG, "rearmAllPending failed for $action", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        private const val TAG = "BootReceiver"

        private val HANDLED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        /** Supervisor scope so one slow re-arm doesn't cancel sibling work. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

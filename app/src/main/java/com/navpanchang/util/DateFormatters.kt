package com.navpanchang.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/**
 * Locale-aware [DateTimeFormatter] helpers.
 *
 * **Why this exists:** the obvious shape — a top-level
 *
 * ```kotlin
 * private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
 * ```
 *
 * is a footgun. Top-level vals initialize once per class load, with whatever
 * `Locale.getDefault()` was at that moment. Our [LanguageSwitchInterceptor] swaps
 * `Locale.setDefault(...)` on activity attach, but that runs LATER than first
 * class-load on a fresh process. Result: even after the user picks Hindi+Native,
 * times keep rendering in the original (en-US) locale forever — the formatter
 * is pinned.
 *
 * Use [rememberFormatter] from any Composable. It re-creates the formatter
 * whenever the active configuration locale changes (which happens on
 * `MainActivity.recreate()` after a Settings → Language toggle), and Compose's
 * [LocalConfiguration] correctly reflects the post-interceptor locale.
 *
 * For non-Composable contexts (e.g. [com.navpanchang.alarms.NotificationBuilder]
 * running in a BroadcastReceiver), call [makeFormatter] each time you need to
 * format. It reads [Locale.getDefault] at the call site, which IS reset by
 * `LanguageSwitchInterceptor.wrap()` and is correct by the time an alarm fires.
 */
@Composable
fun rememberFormatter(pattern: String): DateTimeFormatter {
    // Two locale gotchas combined here:
    //
    // 1. `LocalConfiguration.current.locales[0]` strips BCP-47 unicode extensions
    //    (-u-nu-deva, etc.) during the Configuration round-trip. Using it for the
    //    formatter would silently produce Latin digits even when the user picked
    //    Native numerals. We read `Locale.getDefault()` instead because
    //    `LanguageSwitchInterceptor.wrap` sets it with the full tag intact.
    //
    // 2. `DateTimeFormatter` does NOT honor the `nu-deva` (or `nu-tamldec`, etc.)
    //    numbering-system extension by itself — even when given a Locale with the
    //    extension, it falls back to Latin digits. We have to chain
    //    `.withDecimalStyle(DecimalStyle.of(locale))` to force the right
    //    zero-digit. `DecimalStyle.of(...)` DOES read the extension correctly.
    //    (`String.format` and `NumberFormat.getInstance` both already honor the
    //    extension natively, so [safeStringResource] doesn't need this trick.)
    //
    // We still read `LocalConfiguration` as the recomposition signal so the
    // formatter rebuilds after a Settings → Language toggle + recreate().
    val config = LocalConfiguration.current
    return remember(config, pattern) {
        val locale = Locale.getDefault()
        DateTimeFormatter.ofPattern(pattern, locale)
            .withDecimalStyle(DecimalStyle.of(locale))
    }
}

/**
 * Non-Composable equivalent — call from [BroadcastReceiver]s or anywhere else
 * outside the Compose tree. Cheap (microseconds); the locale-correct alternative
 * to a stale top-level `val`. Applies the same `DecimalStyle.of(locale)` trick
 * so notification bodies render times in the user's chosen numeral system.
 */
fun makeFormatter(
    pattern: String,
    locale: Locale = Locale.getDefault()
): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, locale)
    .withDecimalStyle(DecimalStyle.of(locale))

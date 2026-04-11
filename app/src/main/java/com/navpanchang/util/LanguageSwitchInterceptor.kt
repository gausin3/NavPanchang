package com.navpanchang.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps a [Context] to force a specific locale for the app, independent of the
 * system locale. Used by the Language picker in Settings (Phase 7) to switch
 * between `en` and `hi` without requiring the user to change the system language.
 *
 * Phase 0 scaffold: no override is applied yet. Wire-up happens in Phase 7 via
 * `MainActivity.attachBaseContext(LanguageSwitchInterceptor.wrap(newBase, savedLocale))`.
 *
 * **Devanagari rendering note:** Android 8+ ships Noto Sans Devanagari as a
 * fallback font, so Hindi tithi labels (एकादशी) render correctly even inside an
 * English-locale UI without any font-loading gymnastics. This interceptor only
 * needs to switch the *resource* locale so that `R.string.event_shukla_ekadashi`
 * resolves to the Hindi translation. The glyphs come from the system fallback.
 *
 * See TECH_DESIGN.md §Phase 0 utilities.
 */
class LanguageSwitchInterceptor private constructor(base: Context) : ContextWrapper(base) {

    companion object {
        /**
         * Wrap [base] so the app uses [locale] regardless of the system locale.
         * Pass `null` to keep the system locale.
         */
        fun wrap(base: Context, locale: Locale?): Context {
            if (locale == null) return LanguageSwitchInterceptor(base)

            val config = Configuration(base.resources.configuration)
            Locale.setDefault(locale)
            config.setLocale(locale)
            val localized = base.createConfigurationContext(config)
            return LanguageSwitchInterceptor(localized)
        }
    }
}

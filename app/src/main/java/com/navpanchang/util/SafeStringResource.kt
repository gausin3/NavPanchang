package com.navpanchang.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * `stringResource(...)` formatted with a numeral system that matches the *resolved*
 * template's language — not the active app locale.
 *
 * **Why this exists:**
 * Android's stock [stringResource] formats `%d` arguments using the active
 * [LocalConfiguration] locale. When the user picks a companion language (e.g. Marathi)
 * via [LanguageSwitchInterceptor], the active locale becomes `mr-IN` — but until
 * `values-mr/strings.xml` ships, the *string template* falls back to English from
 * `values/`. The result is mixed scripts: an English template with Devanagari digits
 * ("Sunrise in ११ hours — prepare tonight.").
 *
 * This helper formats numeric args in the active locale **only when that language has
 * a translated `values-<lang>/` folder**. Otherwise (the fallback case) it uses
 * [Locale.ROOT] so the digits stay ASCII and match the English fallback template.
 *
 * **Add a language to [TRANSLATED_LANGUAGES] when its `values-<tag>/strings.xml`
 * lands** — that switches numbers in that language back to native script.
 *
 * Use this helper instead of `stringResource(id, arg1, arg2)` whenever any arg is a
 * number. For pure `%s` / no-arg templates, plain `stringResource` is fine.
 */
/**
 * The set of BCP-47 language tags for which we ship a `values-<tag>/strings.xml` folder
 * **and** translated data fields (event nameXx, tithi nameXx, etc.). Picking a language
 * NOT in this set is allowed — the Settings UI surfaces those for forward compatibility —
 * but the activity falls back to a translated language for resource resolution and the
 * data-label helpers fall back to Hindi values. **When you ship a new translation, add
 * the tag here in the same PR as the new `values-<tag>/` folder.**
 */
val TRANSLATED_LANGUAGES = setOf("en", "hi")

@Composable
fun safeStringResource(@StringRes id: Int, vararg formatArgs: Any): String {
    val resources = LocalContext.current.resources
    val template = resources.getString(id)
    val activeLocale = LocalConfiguration.current.locales[0]
    val formatLocale = remember(activeLocale) {
        if (activeLocale.language in TRANSLATED_LANGUAGES) activeLocale else Locale.ROOT
    }
    // Convert vararg to typed Array for String.format. Kotlin spreads via *args.
    return String.format(formatLocale, template, *formatArgs)
}

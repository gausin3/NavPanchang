package com.navpanchang.panchang

/**
 * The user's chosen *companion* language for bilingual rendering.
 *
 * NavPanchang always shows English plus one regional language. This enum is just the
 * "second" language that pairs with English. See `DESIGN.md` and TECH_DESIGN.md
 * §Language toggle.
 *
 * `tag` is a BCP-47 language tag, used to resolve `values-<tag>/strings.xml` and to
 * pass to `LanguageSwitchInterceptor`.
 *
 * `nativeName` is how the language calls itself — the form shown in the Settings
 * picker so a Marathi-speaking user sees "मराठी" rather than "Marathi".
 *
 * Tier 1 ships Hindi only (translations exist). Marathi/Tamil/Gujarati are listed
 * here so the picker shape is fixed and the data layer is forward-compatible. Until
 * `values-mr/`, `values-ta/`, `values-gu/` ship and `events.json` / `cities.json`
 * grow `nameMr`/`nameTa`/`nameGu` fields, selecting one of those falls back to
 * the Hindi translation gracefully (see `DisplayName.kt`).
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    /**
     * English-only mode for users who prefer English UI and don't want bilingual labels.
     * Picking this collapses the bilingual data-label format to plain English (no parens)
     * because the "companion" equals the English form — see `bilingual()` in DisplayName.kt.
     */
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    MARATHI("mr", "मराठी"),
    TAMIL("ta", "தமிழ்"),
    GUJARATI("gu", "ગુજરાતી")
}

/**
 * Numeral system used for digits in the UI — both `%d` interpolations and
 * date/time output. Orthogonal to [AppLanguage]: a Hindi-UI user can choose either
 * Devanagari (matches a printed पंचांग) or Latin (matches most digital apps).
 *
 * Default is [LATIN] for everyone — matches mainstream apps and avoids mixed-script
 * bugs in fallback scenarios. Users who want native digits can opt in via Settings.
 *
 * Implementation: when [LATIN], we append the BCP-47 unicode extension `-u-nu-latn` to
 * the active locale tag in `MainActivity.attachBaseContext`. That tells Java/Android's
 * locale-aware formatters (`String.format`, `DateTimeFormatter`) to emit Latin digits
 * regardless of the language. When [DEVANAGARI] (or whatever the language's native
 * numbering system is), we leave the locale alone and the language's default numbering
 * applies — Devanagari for Hindi, etc.
 *
 * For non-Devanagari languages (Tamil, Gujarati) the [DEVANAGARI] choice falls back to
 * the language's own native numbering (Tamil digits or Gujarati digits) — naming the
 * enum value [DEVANAGARI] is shorthand for "use the language's own numerals." The
 * Settings UI surfaces it with a more accurate label like "Native".
 */
enum class NumeralSystem {
    LATIN,
    DEVANAGARI
}

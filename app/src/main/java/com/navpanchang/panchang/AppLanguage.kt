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
 * Numeral system used for digits in the UI — both `%d` interpolations and date/time
 * output. Orthogonal to [AppLanguage]: a Hindi-UI user can choose either Latin
 * (matches most digital apps) or Native (matches a printed पंचांग — Devanagari
 * digits like ११ for 11).
 *
 * Default is [LATIN] for everyone. Mainstream apps default to Latin even in
 * non-Latin script UIs (Google, Microsoft, Apple all do this in Hindi UI), and it
 * eliminates mixed-script bugs when a translation hasn't shipped yet for the
 * picked language. Users who explicitly want native digits can flip the Settings
 * toggle.
 *
 * **Implementation note — JVM default does NOT give native digits.**
 * `Locale.forLanguageTag("hi")` returns Latin digits by default on modern JVMs;
 * native digits require the explicit BCP-47 unicode extension `-u-nu-<system>`.
 * `MainActivity.attachBaseContext` composes the right tag based on this enum AND
 * the user's [AppLanguage]:
 *
 *  * [LATIN]  → `<lang>-u-nu-latn` (ASCII for any language).
 *  * [NATIVE] → `<lang>-u-nu-<numbering>` where `<numbering>` is `deva` for
 *               Hindi/Marathi, `tamldec` for Tamil, `gujr` for Gujarati, and
 *               falls back to `latn` for English (which has no native non-Latin
 *               digit system).
 */
enum class NumeralSystem {
    LATIN,
    NATIVE
}

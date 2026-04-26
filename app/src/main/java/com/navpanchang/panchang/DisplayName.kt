package com.navpanchang.panchang

/**
 * Bilingual display utilities — the single place that decides "English (companion)"
 * versus other shapes. Pure / stateless / Compose-safe.
 *
 * NavPanchang always shows two languages: English plus one regional companion. The
 * companion is the user's chosen [AppLanguage]. Today only Hindi has a real translation
 * across all our data (event names, tithi/nakshatra names, lunar months, cities). Until
 * Marathi/Tamil/Gujarati translations land in the seed files and `values-<tag>/` string
 * resources, selecting one of those languages **falls back to the Hindi value** so the
 * user always sees two languages, never one.
 *
 * Format is fixed: `English (Devanagari/Tamil/Gujarati)` — parens, single space. Per
 * the simplified design choice in MEMORY.md §Locked decisions.
 *
 * **Adding a new companion language** (when its translations arrive):
 *  1. Add a new entry to [AppLanguage].
 *  2. Add `nameMr` / `nameTa` / `nameGu` etc. to `Tithi`/`Nakshatra`/`LunarMonth`/
 *     `Paksha` / `EventDefinition` / `CityCatalog.City` companion data — with a
 *     `null`-safe fallback to `nameHi` so partial translations work.
 *  3. Extend the `when (language)` arms below to return the new field, falling through
 *     to `nameHi` when null.
 */

/** Shape any (English, companion) pair into the canonical bilingual form. */
fun bilingual(english: String, companion: String?): String =
    if (companion.isNullOrBlank() || companion == english) english
    else "$english ($companion)"

// ---------------------------------------------------------------------------
// Companion-name resolution.
// All resolvers fall back to the Hindi name until per-language data ships.
// ---------------------------------------------------------------------------

fun Paksha.companionName(language: AppLanguage): String = when (language) {
    AppLanguage.HINDI,
    AppLanguage.MARATHI,
    AppLanguage.TAMIL,
    AppLanguage.GUJARATI -> nameHi
    AppLanguage.ENGLISH -> nameEn
}

fun Tithi.companionName(language: AppLanguage): String = when (language) {
    AppLanguage.HINDI,
    AppLanguage.MARATHI,
    AppLanguage.TAMIL,
    AppLanguage.GUJARATI -> displayNameHi()
    AppLanguage.ENGLISH -> displayNameEn()
}

fun Tithi.qualifiedCompanion(language: AppLanguage): String =
    "${paksha.companionName(language)} ${companionName(language)}"

fun Nakshatra.companionName(language: AppLanguage): String = when (language) {
    AppLanguage.HINDI,
    AppLanguage.MARATHI,
    AppLanguage.TAMIL,
    AppLanguage.GUJARATI -> nameHi
    AppLanguage.ENGLISH -> nameEn
}

fun LunarMonth.companionName(language: AppLanguage): String = when (language) {
    AppLanguage.HINDI,
    AppLanguage.MARATHI,
    AppLanguage.TAMIL,
    AppLanguage.GUJARATI -> nameHi
    AppLanguage.ENGLISH -> nameEn
}

// ---------------------------------------------------------------------------
// Bilingual convenience helpers — what UI code typically calls.
// ---------------------------------------------------------------------------

fun Tithi.bilingualQualified(language: AppLanguage): String {
    // Tithi 30's "displayName" is "Amavasya" rather than "Krishna Purnima"; honor that
    // special case so the day-detail row reads naturally on new-moon days.
    return if (index == 30) bilingual(displayNameEn(), companionName(language))
    else bilingual(qualifiedNameEn, qualifiedCompanion(language))
}

fun Nakshatra.bilingual(language: AppLanguage): String =
    bilingual(nameEn, companionName(language))

fun LunarMonth.bilingual(language: AppLanguage): String =
    bilingual(nameEn, companionName(language))

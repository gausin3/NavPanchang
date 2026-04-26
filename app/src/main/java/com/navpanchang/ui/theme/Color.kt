package com.navpanchang.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NavPanchang palette — manuscript-devotional. See `DESIGN.md` §2 for full role mapping
 * and contrast guarantees. Use these constants via `MaterialTheme.colorScheme.*` rather
 * than raw `Color(0xFF...)` at call sites.
 *
 * Naming convention:
 *  * `Brand*` — light-theme tokens.
 *  * `Dark*`  — dark-theme tokens.
 *
 * The `error` slot is intentionally repurposed for *festival emphasis* (auspicious sindoor)
 * because this app has no destructive flows that need Material's default error color.
 * Form / reliability warnings use `BrandWarning` instead.
 */

// ---------------------------------------------------------------------------
// Light theme — "Daylight Manuscript"
// ---------------------------------------------------------------------------

/** Default app background. Warm cream-paper, NOT pure white. */
val BrandSandalPaper = Color(0xFFFFF8EC)

/** Card surfaces, dividers, calendar grid lines. Subtle lift over background. */
val BrandSandalwood = Color(0xFFF1E6CC)

/** All body type. Soft warm black, not pure #000 — easier on older eyes. */
val BrandInk = Color(0xFF1A1410)

/** Hint text, secondary metadata, sunrise/sunset values. */
val BrandAsh = Color(0xFF7A6F5C)

/** Primary accent — vrat-active states, Planner CTA, today-marker on calendar. */
val BrandSaffron = Color(0xFFE07B2C)

/** Type / icons on saffron surfaces. */
val BrandSaffronOn = Color(0xFFFFF8EC)

/**
 * Festival emphasis — sindoor red. Reserved for confirmed festival cards
 * (Mahashivratri, Holi, Diwali). Maps to Material 3 `error` slot.
 */
val BrandSindoor = Color(0xFFB23A2E)

/** Type / icons on sindoor festival cards. */
val BrandSindoorOn = Color(0xFFFFF8EC)

/** Heading rules, Adhik/Kshaya badges, ritual emphasis. Maps to `tertiary`. */
val BrandGoldLeaf = Color(0xFFB8893A)

/** Evening Planner alarm, "tomorrow is…" copy, dusk surfaces. */
val BrandIndigoEve = Color(0xFF1F1B4D)

/** Type on indigo evening surfaces. */
val BrandIndigoEveOn = Color(0xFFFFF8EC)

// ---------------------------------------------------------------------------
// Dark theme — "Temple Night"
// ---------------------------------------------------------------------------

val DarkMidnight = Color(0xFF0E0B23)
val DarkTempleNight = Color(0xFF1A1640)
val DarkBrass = Color(0xFFD4AF37)
val DarkOnBrass = Color(0xFF0E0B23)
val DarkParchment = Color(0xFFF5E8C6)
val DarkAsh = Color(0xFFA89B82)
val DarkSaffronEmber = Color(0xFFD8783D)
val DarkSindoorEmber = Color(0xFFA8413C)

// ---------------------------------------------------------------------------
// Extended state palette (PreparationCard etc.)
// ---------------------------------------------------------------------------

/**
 * Earthier "preparing" yellow — gold-leaf rather than warning-yellow. Use for
 * the day-before Planner state.
 */
val StatePreparing = BrandGoldLeaf

/** Calmer "observing tomorrow / observing today" sage. */
val StateObserving = Color(0xFF5D7A5C)

/** Restful day-after blue for Parana fast-breaking window. */
val StateParana = Color(0xFF3F8FA8)

/** Earthier warning red for reliability/permission issues. */
val StateWarning = Color(0xFFC2552B)

// ---------------------------------------------------------------------------
// Backwards-compat aliases — preserve existing call sites until migration PR.
// See `PreparationCard` consumers; migrate then remove. DESIGN-rationale.md §
// "Existing semantic state colors".
// ---------------------------------------------------------------------------

@Deprecated(
    message = "Use StatePreparing (BrandGoldLeaf) — earthier per DESIGN.md §2.",
    replaceWith = ReplaceWith("StatePreparing")
)
val StatePreparingYellow = StatePreparing

@Deprecated(
    message = "Use StateObserving — calmer sage per DESIGN.md §2.",
    replaceWith = ReplaceWith("StateObserving")
)
val StateObservingGreen = StateObserving

@Deprecated(
    message = "Use StateParana — restful day-after blue per DESIGN.md §2.",
    replaceWith = ReplaceWith("StateParana")
)
val StateParanaCyan = StateParana

@Deprecated(
    message = "Use the brand sandalwood/cream tokens directly per DESIGN.md §2.",
    replaceWith = ReplaceWith("BrandSandalPaper")
)
val BrandCream = BrandSandalPaper

@Deprecated(
    message = "Use BrandIndigoEve — same role, manuscript-aligned name.",
    replaceWith = ReplaceWith("BrandIndigoEve")
)
val BrandDeepIndigo = BrandIndigoEve

@Deprecated(
    message = "Use BrandGoldLeaf — same role, manuscript-aligned name.",
    replaceWith = ReplaceWith("BrandGoldLeaf")
)
val BrandGold = BrandGoldLeaf

@Deprecated(
    message = "Use BrandInk — same role, manuscript-aligned name.",
    replaceWith = ReplaceWith("BrandInk")
)
val BrandTextOnLight = BrandInk

@Deprecated(
    message = "Use DarkMidnight — same role, manuscript-aligned name.",
    replaceWith = ReplaceWith("DarkMidnight")
)
val DarkBackground = DarkMidnight

@Deprecated(
    message = "Use DarkTempleNight — same role, manuscript-aligned name.",
    replaceWith = ReplaceWith("DarkTempleNight")
)
val DarkSurface = DarkTempleNight

@Deprecated(
    message = "Use DarkParchment — same role, manuscript-aligned name.",
    replaceWith = ReplaceWith("DarkParchment")
)
val DarkOnSurface = DarkParchment

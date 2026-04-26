# Design Context

This project uses a design system adapted from **Notion** (manuscript-scholarly minimalism), with all neutrals re-mapped to a Hindu devotional palette (sandal-paper / ink / saffron / sindoor / gold-leaf / indigo-eve). Inspiration source: https://getdesign.md/notion/design-md.

Skill that scaffolded it: **design-picker** (v1)
Date: 2026-04-26

## Project signals captured

- **Product:** subscription-first Hindu पंचांग + alarm app for Android. Users toggle vrats once (Ekadashi, Purnima, Mahashivratri, etc.); the app schedules Planner / Observer / Parana alarms using offline Swiss Ephemeris computations. Bilingual EN/HI from day one.
- **Audience:** primary persona — older Indian devotees who verify against a printed paper पंचांग. Mom-test driven. Multi-generational households.
- **Feeling:** restraint with ceremonial moments. Calm sandal-paper most days; saffron on vrat days; sindoor + gold-leaf on festivals. The contrast between calm and ceremonial *is* the brand.
- **Constraints:** Devanagari typographic discipline, WCAG AA contrast, dark mode shipped, AGPL (open-source fonts only), older eyes (`bodyLarge` floor 16sp), AGPL — no proprietary assets.
- **Surfaces:** Compose-only, Android phone, portrait-primary. Three top-level tabs (Subscriptions / Calendar / Settings).

## Adaptations from the source

- Color palette is **fully custom** — Notion's grays/blacks/whites replaced with sandal-paper, ink, saffron, sindoor, gold-leaf, indigo-eve.
- Material 3 `error` slot **repurposed** for festival emphasis (sindoor). App has no destructive flows. Reliability/permission warnings use the extended `StateWarning` token directly.
- Type pairing is **bilingual-first** — Source Serif 4 + Inter + Tiro Devanagari Hindi recommended (currently using `FontFamily.Default` = Roboto + Noto Sans Devanagari fallback; bundling the recommended fonts is a deferred follow-up).
- **Dynamic color (Android 12+ wallpaper sampling) defaulted to `false`** — religious palette must not be hijacked by the user's wallpaper. This is a *fix* relative to the previous `Theme.kt` which defaulted to `true`.
- Calendar today-marker: a saffron underline (manuscript pen-mark feel), not a filled circle.
- Ornament: a single 1px gold-leaf horizontal rule used **once per screen** as the manuscript divider.

## Files

- [`DESIGN.md`](../DESIGN.md) — full 9-section design brief.
- [`DESIGN-rationale.md`](../DESIGN-rationale.md) — reasoning, what was adapted, caveats, screens to scaffold first.
- [`app/src/main/java/com/navpanchang/ui/theme/Color.kt`](../app/src/main/java/com/navpanchang/ui/theme/Color.kt) — semantic-role palette tokens. Compose-native equivalent of `tokens.css`.
- [`app/src/main/java/com/navpanchang/ui/theme/Theme.kt`](../app/src/main/java/com/navpanchang/ui/theme/Theme.kt) — `lightColorScheme` / `darkColorScheme` wired to the palette, `dynamicColor = false` default.
- [`app/src/main/java/com/navpanchang/ui/theme/Type.kt`](../app/src/main/java/com/navpanchang/ui/theme/Type.kt) — `Typography` adjusted to the manuscript scale.

## Notes for future sessions

When extending this design:

1. **Read `DESIGN.md` before adding any new component, especially §4 (component priorities) and §7 (don'ts).**
2. **Use semantic role names** — `MaterialTheme.colorScheme.primary` etc. Reference `Color.kt` constants by name (`BrandSandalPaper`, `BrandSaffron`); never inline `Color(0xFF...)`.
3. **Stay flat by default** — `containerColor = MaterialTheme.colorScheme.surfaceVariant`, `elevation = 0.dp`. Lift only per §6.
4. **Type slots, not raw sizes** — `MaterialTheme.typography.bodyLarge` etc.
5. **Bilingual same weight** — Hindi and English at the same `FontWeight` always.
6. **Reserve saffron and sindoor.** If you reach for them outside vrat / festival contexts, you're using them wrong.
7. **Deviations are OK for ceremonial moments** (e.g., a fullscreen Mahashivratri card). Note the deviation in the diff comment as a *deliberate ceremonial exception per DESIGN.md §1*, not a new default.

When this brief evolves, update both `DESIGN.md` and this file, and link the PR.

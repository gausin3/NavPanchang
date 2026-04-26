# Design Rationale

Why the design picks in [`DESIGN.md`](DESIGN.md) are what they are, what was adapted from the inspiration, and what to watch for.

---

## What was chosen

**Manuscript-devotional** direction with **Notion** as the structural inspiration:

- Notion's calm minimalism, scholarly type hierarchy, soft surfaces, and color restraint match how the primary user (the maintainer's mother in Lucknow) actually interacts with this app — verifying daily against a printed Hindi panchang. The app should feel like that printed panchang, not a productivity SaaS.
- The whole-app feeling is restraint. The system's emotional dynamic range comes from the contrast between quiet days (sandal-paper, ink, no accent) and ceremonial days (saffron, sindoor, gold-leaf rule). That rhythm is the brand.

The other shortlist candidates and why they didn't win:

- **NYT Editorial** — strong serif hierarchy was tempting for the bilingual pairing, but a panchang is a *reference document* most days, not a reading layout. NYT's tonal contrast also reads slightly cold for a religious surface.
- **Calm (meditation)** — gradient-heavy, dramatic, dark-mode-first. Wrong for a glance-and-go reference. Reserved as a possible *exception* treatment for the Observer sunrise alarm screen — that single moment can borrow Calm's emotional warmth without infecting the rest of the system.

---

## What was adapted away from the source

- **Color palette is fully custom.** Notion's grays/blacks/whites are replaced with sandal-paper, ink, saffron, sindoor, gold-leaf, and indigo-eve — a Hindu devotional vocabulary grounded in temple/manuscript aesthetics, not borrowed from any SaaS brand.
- **`error` slot repurposed for festival emphasis.** Material 3 reserves `error` for destructive states; this app legitimately has none. Sindoor (auspicious red) lives in that slot, and form/reliability warnings use a separate earthier `BrandWarning`.
- **Type pairing is bilingual-first.** Notion uses one serif + one sans optimized for English. NavPanchang's recommended pairing (Source Serif 4 + Inter + Tiro Devanagari Hindi) explicitly co-designs for Devanagari shirorekha and matra clearance. Hindi is not a fallback; it's a primary script.
- **Dynamic color is OFF by default.** Material 3's wallpaper-sampled dynamic color must not be allowed to recolor a religious palette. The current [`Theme.kt`](app/src/main/java/com/navpanchang/ui/theme/Theme.kt) defaults `dynamicColor = true` — that's a real bug for this app and is being fixed in the same change.
- **Calendar today-marker** is a saffron underline, not a filled circle. Filled circles felt productivity-app-y; underlines feel like a manuscript reader's pen mark.
- **Ornament is exactly one rule** — the 1px gold-leaf horizontal divider, used once per screen. Notion has no ornament; this app earns one.

---

## First screens / components to scaffold from this system

Suggested order — small enough to land independently, big enough to validate the system:

1. **`Color.kt` + `Theme.kt`** — palette + Material 3 ColorScheme + `dynamicColor = false` default. (Done in this change.)
2. **`DayDetailSheet`** — already touched in Phase 4 of the Amanta/Purnimanta work. Re-skin per §4 component spec: ink type on sandal-paper, gold-leaf rule under the date heading, label/value rows in `BrandAsh` / `BrandInk`. Smallest visible win.
3. **`SubscriptionRow`** — most-used component. Apply the new palette and bilingual-same-weight rule.
4. **`TodayStatusCard`** — add the three states (quiet / vrat / festival) with sandal / saffron / sindoor card backgrounds. Validates the calm-vs-ceremonial dynamic.
5. **`CalendarScreen` cells** — saffron-underline today marker; saffron / sindoor dots for occurrence indicators. Validates the manuscript-grid aesthetic at scale.
6. **Bundle Source Serif 4 + Inter + Tiro Devanagari Hindi.** Separate PR — adds APK weight, needs deliberate review.

---

## Caveats — review before committing

- **Trademark / inspiration disclosure.** This system is *inspired by* Notion's design language; nothing is licensed. The visual outcome diverges sufficiently that there's no realistic IP risk, but if you ever want to be explicit, a short line in `README.md` ("design system inspired by Notion's manuscript aesthetic, adapted for Hindu devotional content") is honest and costs nothing.
- **Color contrast spot-check.** The light-theme contrast ratios in DESIGN.md §2 were calculated; please re-verify the saffron-on-sandal pair (`#E07B2C` on `#FFF8EC` ≈ 3.2:1) — *this is fine for icons and 18pt+ headings but NOT body text.* Any place where saffron currently backs body text needs `BrandSaffronOn` (`#FFF8EC`) for the type. Audit during the `SubscriptionRow` re-skin.
- **Dark-theme `error` slot.** `DarkSindoorEmber` (`#A8413C`) on `DarkTempleNight` (`#1A1640`) is around 4.2:1 — borderline AA. If a contrast checker fails it for body text, lighten to `#B85952`. Headings are safe.
- **Devanagari font pairing is a recommendation, not a commit.** The current Compose theme uses `FontFamily.Default` everywhere, which falls back to Roboto + Noto Sans Devanagari. That is *good enough* for v1 ship — bundling Tiro Devanagari Hindi adds ~150 KB to APK and introduces font-loading complexity. Defer to a separate PR after the palette ships.
- **Existing semantic state colors.** [`Color.kt`](app/src/main/java/com/navpanchang/ui/theme/Color.kt) currently has `StatePreparingYellow / StateObservingGreen / StateParanaCyan / StateWarning` — kept in this change for backwards compatibility with `PreparationCard`. They should migrate to the earthier extended values (`BrandGoldLeaf / BrandSage / BrandCyanParana / BrandWarning`) in a follow-up. Don't migrate inline with the palette change — keep the diff reviewable.
- **`error` slot semantic load.** Repurposing `error` for festival emphasis means a future contributor looking for "the destructive color" will find sindoor and possibly use it wrong. Mitigated by the §2 doc + a kdoc comment on `Color.kt` — if it ever bites, split `error` back out and create a real `festivalAccent` extended color.
- **Mom-test before merge.** The manuscript direction is a deliberate aesthetic choice that may not match the maintainer's mother's expectation of an "app." Before merging the visible re-skin (steps 2–5 above), put it in front of her on a real device. If she squints or looks for "the colorful one," reconsider.

---

## Files in this scaffold

- [`DESIGN.md`](DESIGN.md) — the 9-section design brief.
- [`DESIGN-rationale.md`](DESIGN-rationale.md) — this document.
- [`app/src/main/java/com/navpanchang/ui/theme/Color.kt`](app/src/main/java/com/navpanchang/ui/theme/Color.kt) — palette in semantic role names. Compose-native equivalent of `tokens.css`.
- [`app/src/main/java/com/navpanchang/ui/theme/Theme.kt`](app/src/main/java/com/navpanchang/ui/theme/Theme.kt) — Material 3 `ColorScheme` wired to the palette; `dynamicColor` default flipped to `false`.
- [`app/src/main/java/com/navpanchang/ui/theme/Type.kt`](app/src/main/java/com/navpanchang/ui/theme/Type.kt) — Material 3 `Typography` adjusted to the manuscript scale.
- [`.claude/design-context.md`](.claude/design-context.md) — project memory for future sessions.

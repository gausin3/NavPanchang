# NavPanchang — Design System

**Direction:** manuscript-devotional. Calm sandal-paper backdrop, ink-toned typography, restrained saffron and sindoor accents, gold reserved for ritual emphasis. The app should feel like a printed पंचांग your grandmother trusts, not a SaaS dashboard.

**Inspiration bones:** Notion (warm minimalism, scholarly type hierarchy, soft surfaces, restraint). **Adaptation:** all neutrals shifted to a Hindu devotional palette; type pairing tuned for Devanagari + English bilingual rendering; ornament reserved for festival days only.

---

## 1. Visual Theme & Atmosphere

NavPanchang is a verification surface for a daily religious practice. Most days the user wants a glance-and-go answer — *which tithi today, when's the next vrat, what time is sunrise?* Twice a month, on the day of an actual vrat, the same surface needs to feel ceremonial — Mahashivratri, Ekadashi, Purnima.

**The whole-app feeling is restraint.** Sandal-cream backgrounds, ink type, narrow color vocabulary, generous breathing room. The brand only reaches its full devotional voice — saffron primary, gold accents, optional ornament — on cards that mark something auspicious. Quiet days look quiet. Sacred days look sacred. The contrast is the point.

Avoid: saturated festive UI on a regular Wednesday; gradients that read as marketing; emoji-heavy notifications; corporate clean white.

Embrace: paper-warmth (`#FFF8EC`-family), ink-soft black (`#1A1410`), saffron only when vrat-relevant, gold only on confirmed festival cards, a single horizontal divider treatment that recalls a manuscript's ruled line.

---

## 2. Color Palette & Roles

All values are sRGB hex. Roles map to Material 3 `ColorScheme` slots so the existing Compose theme can absorb them without restructuring.

### Light theme — "Daylight Manuscript"

| Role | Hex | Material 3 slot | When it appears |
|---|---|---|---|
| `BrandSandalPaper` | `#FFF8EC` | `background`, `surface` | Default app background. Warm cream, not white. |
| `BrandSandalwood` | `#F1E6CC` | `surfaceVariant` | Card bg, subtle dividers, calendar grid lines. |
| `BrandInk` | `#1A1410` | `onBackground`, `onSurface` | All body type. Soft warm black, not pure `#000`. |
| `BrandAsh` | `#7A6F5C` | `onSurfaceVariant` | Hint text, secondary metadata, sunrise/sunset times. |
| `BrandSaffron` | `#E07B2C` | `primary` | Vrat-active states, Planner CTA, today-marker on calendar grid. |
| `BrandSaffronOn` | `#FFF8EC` | `onPrimary` | Text on saffron buttons. |
| `BrandSindoor` | `#B23A2E` | `error` (repurposed: auspicious) | Festival cards (Mahashivratri, Holi, Diwali). Reserved. |
| `BrandSindoorOn` | `#FFF8EC` | `onError` | Text on sindoor festival cards. |
| `BrandGoldLeaf` | `#B8893A` | `tertiary` | Heading rules, Adhik/Kshaya badges, ritual emphasis only. |
| `BrandIndigoEve` | `#1F1B4D` | `secondary` | Evening Planner alarm, "tomorrow is…" copy, dusk imagery. |
| `BrandIndigoEveOn` | `#FFF8EC` | `onSecondary` | Text on indigo evening surfaces. |
| `BrandSage` | `#5D7A5C` | (extended: stateObserving) | Observer "ready / observing tomorrow" state. Calmer than the current `#4CAF50`. |
| `BrandWarning` | `#C2552B` | (extended: stateWarning) | Reliability Check warnings. Earthier than alarming red. |
| `BrandCyanParana` | `#3F8FA8` | (extended: stateParana) | Parana fast-breaking window. Restful day-after blue. |

**Why sindoor as `error`:** Material 3 reserves `error` semantically for "destructive / attention." We're reusing the slot for festival emphasis (auspicious red) because the app legitimately has nothing destructive that needs that color. Forms' error feedback uses `BrandWarning` instead.

### Dark theme — "Temple Night"

| Role | Hex | Material 3 slot | Notes |
|---|---|---|---|
| `DarkMidnight` | `#0E0B23` | `background` | Deep indigo, evokes night-aarti. |
| `DarkTempleNight` | `#1A1640` | `surface`, `surfaceVariant` | Card surfaces, slight lift over background. |
| `DarkBrass` | `#D4AF37` | `primary` | Gold on dark — temple metal. |
| `DarkOnBrass` | `#0E0B23` | `onPrimary` | Type on gold pills. |
| `DarkParchment` | `#F5E8C6` | `onBackground`, `onSurface` | All type. Warm cream against indigo. |
| `DarkAsh` | `#A89B82` | `onSurfaceVariant` | Hint text. |
| `DarkSaffronEmber` | `#D8783D` | `secondary` | Saffron, slightly muted for night legibility. |
| `DarkSindoorEmber` | `#A8413C` | `error` (auspicious) | Festival cards in dark mode. |

### Contrast guarantees

- All foreground/background pairs above clear **WCAG AA (4.5:1 body text)** at minimum. Body type on `BrandSandalwood` cards is 8.7:1.
- Saffron-on-sandal hits 3.2:1 — **not legible for body**, only for icons, buttons, and 18pt+ headings. Body text on saffron surfaces uses `BrandSaffronOn`.
- Sindoor festival cards use `BrandSindoorOn` exclusively for type — sindoor is too saturated for sub-headline text against itself.

### Existing semantic colors — keep as-is or migrate

The existing `StatePreparingYellow / StateObservingGreen / StateParanaCyan / StateWarning` in [`Color.kt`](app/src/main/java/com/navpanchang/ui/theme/Color.kt) used by `PreparationCard` should migrate to the calmer earthier values above (`BrandGoldLeaf` for preparing, `BrandSage` for observing, `BrandCyanParana` for parana, `BrandWarning` for warning). Migrate in one PR after the palette is in.

---

## 3. Typography Rules

**Pairing recommendation (target — implement in a follow-up PR; current build uses system defaults):**

| Use | Font | License | Rationale |
|---|---|---|---|
| English headings | **Source Serif 4** | OFL | Warm transitional serif, pairs with Devanagari at the same optical weight without feeling colonial. |
| English body / UI | **Inter** | OFL | Workhorse sans, same x-height ballpark as Source Serif body. |
| Hindi / Devanagari (all weights) | **Tiro Devanagari Hindi** | OFL | Manuscript-style Devanagari with proper shirorekha (top-line) discipline; pairs visually with Source Serif. Avoid Noto Sans Devanagari for headings — too modern/screen-y for the manuscript feel. |
| Numerals & sunrise times | **Inter** with `tnum` (tabular nums) feature on | OFL | Sunrise/sunset times must align column-wise. |

Until those bundle in, current system fonts (Roboto / Noto Sans Devanagari fallback) work — see follow-up note in `DESIGN-rationale.md`.

### Type scale (Compose Material 3 slots)

The scale below is tightened from the default M3 — manuscript reading wants slightly less display drama and more body comfort. **Older eyes** (mom-test) drive the body floor of 16sp.

| Slot | Size | Line | Weight | Use |
|---|---|---|---|---|
| `displayLarge` | 40 / 48sp | 56sp | 600 (Source Serif) | Onboarding hero only. |
| `headlineLarge` | 28sp | 36sp | 600 | Screen titles ("व्रत / Vrats"). |
| `headlineMedium` | 22sp | 30sp | 600 | Day-detail date heading ("Friday, 8 March 2024"). |
| `titleLarge` | 20sp | 28sp | 500 | Card titles, event names. |
| `titleMedium` | 17sp | 24sp | 500 | Section headers ("Today", "Lunar month"). |
| `bodyLarge` | 16sp | 26sp (tighter than M3 default 24) | 400 | Default body. **Floor for any user-readable text.** |
| `bodyMedium` | 14sp | 22sp | 400 | Hints, secondary metadata. |
| `labelLarge` | 14sp | 20sp | 500 (uppercase off) | Buttons, segmented buttons. |
| `labelMedium` | 12sp | 16sp | 500 | Calendar grid weekday header, badges. |

### Bilingual rules

- **Never mix Hindi and English in the same line at different weights.** If both appear (e.g., "Ekadashi एकादशी"), they take the same weight.
- **Devanagari needs ~1.15× the line-height of equivalent English** because of matra ascenders and descenders. The `bodyLarge` 26sp lineheight is set for Hindi comfort; English looks slightly airier — that's the right trade.
- **Don't bold Devanagari to substitute for emphasis.** Bold Hindi often looks like a font-rendering bug. Use `BrandGoldLeaf` color or a small italicized English caption instead.

---

## 4. Component Stylings

Top components for NavPanchang's actual surfaces, prioritized:

### 1. `SubscriptionRow` (home screen, the most-used component)
- Background: `BrandSandalPaper`. Selected/subscribed row: `BrandSandalwood`.
- Hindi title and English title on the same line, separated by `· ` middot.
- Trailing switch in `BrandSaffron` when on, ash when off.
- One-line description in `BrandAsh`, 14sp — shows next occurrence date, never two lines.
- Divider: 1dp `BrandSandalwood`, full-bleed.

### 2. `TodayStatusCard` (top of home)
- Quiet days: `BrandSandalwood` card, ink type, no accent.
- Day-of-vrat: `BrandSaffron` card, `BrandSaffronOn` type, single gold-leaf `B̄` glyph or simple ornament rule top-right.
- Day-of-festival (Mahashivratri etc.): `BrandSindoor` card, `BrandSindoorOn` type, gold-leaf top rule (3px `BrandGoldLeaf`).

### 3. `DayDetailSheet` (calendar tap)
- Bottom sheet, `BrandSandalPaper` background.
- Heading: `headlineMedium`, ink. Below it a 1px `BrandGoldLeaf` rule full-bleed — manuscript divider.
- Tithi / Nakshatra / Lunar month / Sunrise / Sunset rows: `bodyLarge`, label in `BrandAsh`, value in `BrandInk`. Tabular nums for times.
- Adhik / Kshaya callouts: small badge in `BrandGoldLeaf` outline + text, never a full bar.

### 4. `CalendarScreen` month grid
- Cell padding 6dp, day number `labelMedium` ink.
- Today marker: a 4dp wide `BrandSaffron` underline below the day number — never a filled circle (felt too modern).
- Subscribed-event indicator: a single 4dp `BrandSaffron` dot bottom-center (festival = `BrandSindoor` dot, gold-leaf ring).
- Weekend column shading: subtly tinted `BrandSandalwood` background, 3% opacity.

### 5. `PreparationCard` (Planner / Observer / Parana state)
- State-tinted left edge (4dp solid), card body `BrandSandalwood`.
- States migrate from current bright palette to the earthier extended colors above.

### 6. `SubscriptionsScreen` segmented filters
- Material 3 `SingleChoiceSegmentedButtonRow`, container `BrandSandalwood`, selected segment `BrandSaffron` with `BrandSaffronOn` type.

### 7. `SettingsScreen` cards
- Each section in its own card on `BrandSandalPaper`. Card background `BrandSandalwood`. Card spacing 12dp.
- Lunar Convention toggle uses the same segmented-button treatment as #6.

### Notification copy (system-rendered, but tuned)
- Planner channel icons: gold leaf glyph.
- Observer channel: saffron sun glyph.
- Parana: cyan crescent.
- Title type weight: Medium, never bold.

---

## 5. Layout Principles

- **Margins.** All screens: 16dp horizontal margin. Cards inside: 16dp internal padding. Section spacing: 12dp.
- **Vertical rhythm.** 4dp grid. Most spacing rounds to 4 / 8 / 12 / 16 / 24 / 32.
- **Manuscript line.** Use the 1px `BrandGoldLeaf` horizontal rule as a hierarchical divider once per screen — directly under the screen title, or under the day-detail date. Never repeat it within a screen; that's what destroys the manuscript feel.
- **Whitespace is correct.** A panchang's content is dense by nature. The temptation to fill space is wrong. Empty space carries the calm.
- **Avoid full-width hero images.** The ic_launcher art and a single ornament glyph per festival card are the limit. No banner photography of temples / sunrises / etc.
- **Bottom nav** (`Subscriptions / Calendar / Settings`) stays — three items, ink icons on `BrandSandalPaper`, selected indicator a saffron underline, never a pill.

---

## 6. Depth & Elevation

NavPanchang is **mostly flat** — a manuscript has no shadows. Elevation appears in three places only:

| Surface | Elevation | Shadow color |
|---|---|---|
| `TopAppBar` when scrolled | 2dp | `BrandInk` at 6% opacity |
| `BottomSheet` (DayDetail) | 8dp | `BrandInk` at 8% opacity |
| `Snackbar` | 6dp | `BrandInk` at 8% opacity |

Cards sit flat (0dp) on the background — the `BrandSandalwood` color difference is the visual lift. Avoid Material 3's default elevated card colors which add a tonal overlay; use `containerColor = BrandSandalwood` and `elevation = 0.dp` explicitly.

Dark theme: same elevations, shadow color `#000000` at 30%. The lift is the surface-color delta.

---

## 7. Do's and Don'ts

**Do:**
- Reserve saffron for events that *are* vrats, never as decoration.
- Reserve sindoor for confirmed festivals, never as a brand color used freely.
- Use gold-leaf rules (1px) as the only "ornament." It's enough.
- Pair Hindi and English at the same visual weight on bilingual labels.
- Tabular numerals on every time display (sunrise, sunset, parana window).
- Treat the user's mother as the primary reader. If she'd squint at it, it's wrong.

**Don't:**
- Use saturated festival colors on quiet days. The contrast between calm and ceremonial is the system.
- Add gradient backgrounds. Manuscripts don't have gradients.
- Use emoji-as-icon (🪔 🛕 🕉) for ritual states — they look like Slack reactions. Use the gold-leaf glyph or restrained iconography.
- Bold Devanagari to convey emphasis. Use color or italic English caption.
- Add "lite/pro/premium" patterns. The app is offline, AGPL, no monetization — its visual language should reflect that.
- Rely on dynamic-color from Android 12+ wallpaper sampling. The user's wallpaper must NOT recolor a religious palette. Default `dynamicColor = false` in `NavPanchangTheme`.

---

## 8. Responsive Behavior

NavPanchang is mobile-only (Compose, no Compose-for-Web in scope). Sizes:

| Breakpoint | Width range | Adaptation |
|---|---|---|
| Compact (default) | < 600dp | Single-column list. Bottom nav. Calendar grid 7-column with cell aspect 1:1.2 (slightly tall to fit day number + dot). Pictures-of-week thumbnails — none. |
| Medium (foldable inner / large phone) | 600–840dp | Same single-column; widen card padding to 24dp; calendar cells gain 2dp inner padding. No two-pane yet. |
| Expanded (tablets) | ≥ 840dp | Out-of-scope for v1. Reuse Compact layout — usable, not optimized. |

**Orientation:** primary is portrait. Landscape on phones works (Compose handles it) but no special layout. Day Detail bottom sheet caps at 60% height in landscape so the calendar grid stays visible above.

**Accessibility scaling:** support up to 200% font scale. The `bodyLarge` floor of 16sp scales to 32sp at 200% — the layout must accommodate without truncation. Card contents must use `wrapContentHeight` and never fixed `height`.

---

## 9. Agent Prompt Guide

When extending this design (new screens, new components, new state), follow this in order:

1. **Read DESIGN.md first.** Don't grep for similar patterns; check this brief. Especially section 4 (component priorities) and section 7 (don'ts).
2. **Use semantic color roles, not raw hex.** Reference `BrandSandalPaper` / `BrandInk` etc. from [`Color.kt`](app/src/main/java/com/navpanchang/ui/theme/Color.kt). Never `Color(0xFF...)` inline.
3. **Stay flat by default.** New cards: `containerColor = MaterialTheme.colorScheme.surfaceVariant`, `elevation = 0.dp`. Only deviate per section 6.
4. **Type slot, not raw size.** `MaterialTheme.typography.bodyLarge` etc. Never `fontSize = 14.sp` ad-hoc. Section 3 lists every defined slot.
5. **Bilingual = same weight.** Both Hindi and English at the same `FontWeight`. Different sizes are fine if the design genuinely needs it; different weights are not.
6. **Reserve saffron / sindoor.** New components default to ink-on-sandal. If you find yourself reaching for accent color, ask: is this surface specifically about a vrat or a festival? If not, no.
7. **No new Material colors without a roleref.** If a new state genuinely needs a new color (e.g., "Sankranti countdown"), add it to `Color.kt` with a semantic role-named constant *and* document it in this section.
8. **Extend, don't replace.** When in doubt, extend an existing component. Three top-level surfaces (Subscriptions / Calendar / Settings) are the whole app — new pages are usually wrong; new sections within pages are usually right.

When designing a one-off ceremonial moment (e.g., a fullscreen Mahashivratri card or the Observer sunrise alarm screen), it's OK to reach for more drama: gold-leaf rule, sindoor card, an ornament glyph. Note in the change comment that this is a *deliberate ceremonial exception* per DESIGN.md §1, not the new default.

---

**Maintainers:** when this brief evolves, bump the inline version note in `.claude/design-context.md` and link the PR. The brief is the contract — code references it.

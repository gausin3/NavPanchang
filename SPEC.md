# NavPanchang — Functional Specification

## 1. Scope

This document specifies **what** NavPanchang does from the user's perspective. For **how** it does it, see [`TECH_DESIGN.md`](TECH_DESIGN.md). For the architectural picture, see [`ARCH.md`](ARCH.md).

## 2. Personas

| Persona | Description |
|---|---|
| **Devotee (primary)** | Observes one or more recurring Hindu vrats. Wants a reliable alarm for every occurrence without hunting through a panchang. Example: fasts every Ekadashi. |
| **Panchang reader (secondary)** | Wants a clean daily/monthly panchang view with tithi, nakshatra, paksha, and festival markers. May or may not subscribe to alarms. |
| **Traveler** | Moves between cities (e.g. Lucknow ↔ Dubai) and needs location-accurate sunrise times so their fast starts and ends at the right moment. |

## 3. Core user stories

### 3.1 Subscription & alarms

- **US-1.** As a devotee, I want to toggle "notify me for every Ekadashi" once and then never worry about it, so I never miss a fast.
- **US-2.** As a devotee, I want the app to tell me *the evening before* that tomorrow is Ekadashi, so I can prepare my meal.
- **US-3.** As a devotee, I want an alarm *at sunrise* on Ekadashi itself, so I know when the fast starts.
- **US-4.** As a devotee, I want an alarm the *next morning* with the exact Parana window, so I break the fast at the right time.
- **US-5.** As a devotee traveling from Lucknow to Dubai, I want the sunrise alarm to fire at the *Dubai* sunrise, not Lucknow's, and I want the app to tell me it's adjusting.
- **US-6.** As a devotee, I want to silence "planning" alerts but keep "ritual" alerts loud, so a midnight recompute doesn't wake me but sunrise alarms still do.
- **US-7.** As a devotee who enabled a subscription late (e.g. 10 PM the night before), I want the app to *not* fire a confusing 8 PM alert retroactively, but still fire the sunrise alert correctly.

### 3.2 Panchang view

- **US-8.** As a panchang reader, I want to see today's tithi, nakshatra, sunrise, and sunset on the home screen, so I trust the app is calculating correctly.
- **US-9.** As a panchang reader, I want to open any day in the month and see the full panchang details.
- **US-10.** As an advanced user, I want to see a `[!]` indicator on Kshaya tithi days, with a timeline showing why the tithi was skipped.
- **US-11.** As an advanced user, I want the month header to label Adhik Maas explicitly with a tooltip explaining why this month is extra.

### 3.3 Trust & verification

- **US-12.** As a devotee, I want to compare the app's times against my printed paper panchang, so I can trust it. If there's a small consistent offset, I want to correct it without editing the database.
- **US-13.** As a devotee, I want labels in Hindi script (एकादशी), not just transliterated Roman.
- **US-14.** As a user on a budget phone, I want a "Reliability Check" that warns me if Android's battery optimization will kill my alarms.

## 4. Feature requirements

### FR-1. Event subscription

- FR-1.1. User can enable/disable any event from the home screen with a single tap.
- FR-1.2. Each event has three independent sub-alarms (Planner, Observer, Parana) which can be toggled separately in the event detail screen.
- FR-1.3. Each event has a customizable Planner time (default 8:00 PM wall-clock) and notification sound.
- FR-1.4. MVP event catalog: Shukla Ekadashi, Krishna Ekadashi, Purnima, Amavasya, Pradosh (Shukla/Krishna), Sankashti Chaturthi, Vinayaka Chaturthi, Masik Shivratri, Mahashivratri.

### FR-2. Panchang correctness

- FR-2.1. Panchang calculations are computed offline via Swiss Ephemeris using the **Lahiri ayanamsha** and **Drik observational** tradition (matching drikpanchang.com and mypanchang.com).
- FR-2.2. Tithi end-times are accurate to within 60 seconds of drikpanchang.com's published values.
- FR-2.3. **Dashami-Viddha rule:** for Ekadashi, if Dashami is present at any moment during the 96 minutes before sunrise (Arunodaya), the vrat shifts to the next day.
- FR-2.4. **Parana window:** computed as `(sunrise of Dvadashi)` to `min(end of Dvadashi, first quarter of Dvadashi)` — the stricter Harivasara rule.
- FR-2.5. **Adhik Maas detection** via Solar Sankranti counting between Amavasyas. Events with `observeInAdhik = false` are suppressed in Adhik months; events with `observeInAdhik = true` still fire.
- FR-2.6. **Kshaya tithi handling:** emit a safe-fallback occurrence on the day the tithi was longest-prevailing, tagged with `[!]` in the UI.

### FR-3. Alarms

- FR-3.1. Planner alarms fire at a wall-clock local time (default 8:00 PM) on the day *before* an event.
- FR-3.2. Observer alarms fire at local sunrise (GPS-based) on the day of the event.
- FR-3.3. Parana alarms fire at sunrise on Dvadashi (Ekadashi only).
- FR-3.4. Alarms are delivered on three notification channels: `channel_event_reminders` (low), `channel_ritual_alarms` (high, four sound variants), `channel_daily_briefing` (low).
- FR-3.5. Alarms survive device reboot via `BootReceiver`.
- FR-3.6. Alarms survive timezone change (e.g. travel Lucknow → Dubai) via `LocaleChangeReceiver`, which cancels and re-schedules all Planner alarms in the new wall-clock time.
- FR-3.7. Late-subscription gate: if a Planner alarm's fire time has already passed when the subscription is enabled, do NOT fire it retroactively; instead show a "Ready for Tomorrow" card in the UI.
- FR-3.8. The Planner notification includes a "Remind me at Sunrise" action that schedules a one-shot Observer alarm for safety.

### FR-4. Location

- FR-4.1. On first run, request `ACCESS_FINE_LOCATION` with a skippable city-picker fallback.
- FR-4.2. Optionally request `ACCESS_BACKGROUND_LOCATION` to enable automatic travel detection via geofencing.
- FR-4.3. Bundled city catalog covers major Indian and global cities with lat/lon/tz.
- FR-4.4. When the user moves > 100 km from their last calculation point, Tier 2 (30–60 day high-precision window) is recomputed automatically.
- FR-4.5. When a Tier 2 record overrides a HOME record by ≥ 2 minutes, the UI shows a "(Adjusted for [city] time)" badge.

### FR-5. UI

- FR-5.1. Three-tab bottom navigation: Home (Subscriptions), Calendar, Settings.
- FR-5.2. Home screen shows an always-visible TodayStatusCard with today's tithi and sunrise.
- FR-5.3. Home screen shows a PreparationCard with a yellow/green/cyan state for the nearest subscribed event.
- FR-5.4. Calendar tab shows a month grid with tithi labels and subscribed-event dots.
- FR-5.5. Day detail screen shows full panchang (tithi, nakshatra, yoga, karana, vara, sunrise, sunset, moonrise, moonset).
- FR-5.6. Adhik Maas months show an `(i)` tooltip in the month header.
- FR-5.7. Kshaya tithi days show a timeline diagram on DayDetailScreen.
- FR-5.8. Settings has a Reliability Check section that surfaces battery-optimization and exact-alarm permission status.

### FR-6. Languages

- FR-6.1. English and Hindi (Devanagari script) ship in the first release.
- FR-6.2. Language can be switched in Settings independent of the device locale.
- FR-6.3. Event names always show Hindi transliteration alongside the English name (e.g. "Ekadashi • एकादशी").

### FR-7. Data governance

- FR-7.1. Event definitions are versioned and upserted from `assets/events.json` on every cold start.
- FR-7.2. Upgrades never modify the `subscriptions` table — user preferences survive all rule/name fixes.
- FR-7.3. Removed events are marked `deprecated = 1`, never deleted; existing subscribers get a migration banner.

## 5. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Works fully offline after first install. Only optional network is future remote config. |
| NFR-2 | Initial 24-month lookahead completes in ≤ 6 seconds on a mid-range device (Snapdragon 6-series, 4 GB RAM). |
| NFR-3 | Alarms fire within ±30 seconds of scheduled time on devices with battery optimization whitelisted. |
| NFR-4 | No data leaves the device. No analytics, no crash-reporting SDKs in v1. |
| NFR-5 | APK/AAB under 25 MB (most of which is Swiss Ephemeris data files). |
| NFR-6 | Supports Android 8.0 (API 26) through Android 14 (API 34). |
| NFR-7 | Source code licensed under AGPL v3 and published on GitHub. |
| NFR-8 | Accessibility: all interactive elements have content descriptions; text scales with system font size up to `fontScale = 2.0`. |

## 6. Out of scope (v1)

- Regional scripts beyond Hindi.
- Surya Siddhanta / Vakya panchang traditions.
- Annual Hindu festivals (Diwali, Holi, Navratri, etc.).
- Widgets, wear OS, Android Auto.
- Monetization (ads or IAPs).
- Cloud sync of subscriptions across devices.
- Multi-user profiles on a single device.

## 7. Acceptance criteria (release blockers)

- All unit tests green including `PanchangCalculatorTest`, `VratLogicTest` (Dashami-Viddha), `AdhikMaasTest`, `TithiEndFinderTest` (Kshaya), `EventCatalogSyncerTest`.
- Manual mom-test passes on a real phone (Lucknow, Hindi locale, full onboarding, alarm fires correctly, travel to Dubai shows Location Badge).
- AGPL compliance checklist fully satisfied (LICENSE file, About screen attribution, source link in Play listing).
- Play Store 12-tester / 14-day Closed Testing cycle completed.
- ProGuard rules verified — no Swiss Eph classes stripped.

See [`TECH_DESIGN.md`](TECH_DESIGN.md) for how these requirements are implemented.

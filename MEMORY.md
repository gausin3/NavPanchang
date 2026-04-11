# NavPanchang — Project Memory

A single place for durable context about the project — the "why" behind decisions, the traps we've already discovered, and the conventions new contributors (or future-you) should know before touching the code.

For day-to-day questions, check the code. For historical context and hard-won lessons, come here.

---

## Locked decisions (do not re-litigate without explicit user sign-off)

| Decision | Locked on | Why |
|---|---|---|
| **Kotlin + Jetpack Compose native Android** | 2026-04-11 | Reliable `AlarmManager`, easiest Play Store path, best battery behavior. Ruled out Flutter (alarm reliability) and React Native (same). |
| **Swiss Ephemeris offline, AGPL v3** | 2026-04-11 | Accurate, no API cost, no rate limits, no network dependency. AGPL inheritance is embraced, not worked around — source ships publicly on GitHub. |
| **Subscription-first home screen** | 2026-04-11 | The user's mother doesn't want a calendar — she wants an alarm. Calendar is a *verification* tool, not the primary surface. |
| **Two-tier lookahead (HOME + CURRENT)** | 2026-04-11 | Recomputing 24 months for a weekend trip is wasteful. HOME is the stable 24-month buffer, CURRENT is a 30–60 day high-precision window triggered by a 100 km geofence. |
| **Dual-alarm model (Planner + Observer, + Parana for Ekadashi)** | 2026-04-11 | Fixed 8 PM wall-clock the night before, plus GPS-anchored sunrise the day of. A single "8 hours before sunrise" alarm fires at confusing times (e.g. 9:45 PM) and doesn't match how humans plan. |
| **Lahiri ayanamsha + Drik observational** | 2026-04-11 | Matches drikpanchang.com and mypanchang.com which the primary user cross-references. Surya Siddhanta / Vakya are out of scope for v1. |
| **Hindi labels from day one** | 2026-04-11 | The primary user verifies against a printed Hindi panchang. Roman transliteration alone is a trust-breaker. |
| **MDM-style versioned seed data** | 2026-04-11 | Seed bugs (wrong tithi index for Sankashti Chaturthi etc.) must be fixable in a new release without losing user subscriptions. Versioned upsert of `event_definitions`, never touch `subscriptions`. |

---

## Religious-calendar nuances that hurt if you get them wrong

These are all handled explicitly in code (see `panchang/VratLogic.kt`, `panchang/PanchangCalculator.getLunarMonth()`, `panchang/TithiEndFinder.kt`). Don't "simplify" them without understanding why they're there.

### Dashami-Viddha (Ekadashi shift)

> If Dashami (tithi 10 or 25) is present at *any* moment during the 96 minutes before sunrise, the Ekadashi vrat is observed on the **next** day (Dvadashi), not the day where Ekadashi is at sunrise.

Plain "tithi at sunrise" is a common beginner mistake and gives the wrong date every time Dashami stretches into Arunodaya. We check the tithi at `sunrise - 96 min` and shift when necessary. Flagged in the UI as `shiftedDueToViddha`.

### Adhik Maas (intercalary month)

> Every ~32 months a lunar month contains zero Solar Sankrantis. That month is repeated, and the repeat is called Adhik Maas. Regular monthly vrats (Ekadashi, Purnima) are still observed in Adhik months, but annual festivals (Mahashivratri, Ganesh Chaturthi) are performed in the following (Nija) month.

Detected by counting Solar Sankrantis between consecutive Amavasyas. Each event in `events.json` carries an `observeInAdhik` boolean. Events with `observeInAdhik = false` are suppressed entirely in Adhik months — no alarm ever fires.

Test case: 2023 had an Adhik Shravan. Ekadashi was observed; Nag Panchami (annual festival rule) was not.

### Kshaya tithi

> A tithi that begins and ends between two sunrises and so doesn't "own" any sunrise.

Naive "tithi at sunrise" produces no occurrence for a Kshaya target — silently skipping the vrat. Unacceptable. We emit a safe-fallback occurrence on the day the tithi was longest-prevailing, tag it with `isKshayaContext = true`, and surface an explanation in the UI. Kshaya tithis are rare but visible: the Calendar shows a `[!]` and the Day Detail screen draws a timeline.

### Parana window

> The fast is broken the *next* morning (Dvadashi) in a specific window. Ending the fast outside this window is considered a violation.

`paranaStart` = sunrise of Dvadashi. `paranaEnd` = `min(endOfDvadashi, startOfDvadashi + dvadashiDuration / 4)` — the stricter "Harivasara quarter" rule. Both times are stored on the occurrence and a dedicated `PARANA` alarm fires at `paranaStart`.

### Kshaya *month* (extremely rare)

A full lunar month can be skipped when two Sankrantis fall in the same month. Last happened in 1963, next in 2124. `getLunarMonth()` classifies it correctly but we don't surface it specially — the occurrence computer just emits the events for the (shorter) window naturally.

---

## Android alarm reliability traps

### The "traveling wall-clock" problem

A `PLANNER` alarm scheduled for "8 PM tomorrow" in Lucknow is stored as an epoch-millis representing IST 20:00. After the user flies to Dubai, that same epoch-millis is 18:30 GST — which is **not** 8 PM any more. `LocaleChangeReceiver` watches `ACTION_TIMEZONE_CHANGED` and rescheduls every future PLANNER alarm from the stored `(dateLocal, customPlannerHhmm)` tuple in the new timezone. Observer and Parana alarms are astronomy-anchored (GPS sunrise) so their epoch-millis is already correct.

### Battery optimization

Xiaomi, Samsung, OnePlus, Vivo, Oppo, Realme all kill background workers aggressively. Generic detection: `PowerManager.isIgnoringBatteryOptimizations()`. If false, show a warning in Settings → Reliability Check and a button to `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. A red dot on the Settings bottom-nav tab surfaces unresolved issues. This one step is the difference between "alarms fire reliably" and "my mom deletes the app".

### `SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM` (API 31+)

We apply under **`USE_EXACT_ALARM`** (Play Store-safe for calendar/alarm-clock use cases) with a religious-observance justification. Fall back to `SCHEDULE_EXACT_ALARM` request flow on older OS versions.

### Boot survival

Alarms are wiped on reboot. `BootReceiver` listens for `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, and `MY_PACKAGE_REPLACED`, and enqueues a `RefreshWorker` that re-posts every pending row in `scheduled_alarms`. A daily `RefreshWorker` also runs as defense-in-depth against flaky OEMs.

### Notification channel sound immutability (Android 12+)

Channel sound cannot be changed after the channel is created without deleting and recreating the channel — which loses the user's per-channel preferences (DND, LED, vibration override). We create **four separate ritual channels** (bell, sankh, toll, om) up front, and the user's sound preference simply decides which channel a given alarm posts to.

### Kshaya vrats and late subscriptions

If a user enables a subscription 30 minutes before sunrise on the day of an event, the Planner alarm for the *previous* evening has already passed. Do NOT fire it retroactively — that's confusing. `LateSubscriptionGate` writes `pendingStatus = "READY_FOR_TOMORROW"` to the occurrence and the home screen shows a green "Observing Tomorrow" card instead.

---

## Conventions

- **Time storage:** Always epoch-millis UTC in Room. Convert to local times in the repository layer or UI via `AstroTimeUtils` / `ZoneId.systemDefault()`. Never store local time as a string except for `dateLocal` (which is explicitly tagged to the location).
- **Location of truth for rules:** `assets/events.json` is authoritative. Any change to a rule bumps that event's `seedVersion` *and* the top-level `catalogVersion`. Never edit rows in `event_definitions` outside `EventCatalogSyncer`.
- **User preferences sanctity:** Never write to `subscriptions` during a catalog upgrade. Never write to `subscriptions` from any worker. Only the UI writes to it.
- **Ephemeris scope:** Every panchang computation goes through `EphemerisScope().use { ... }`. Never create a bare `SwissEph` instance.
- **Feature gating:** Code inside `util.debug.*` is `if (BuildConfig.DEBUG) return` at every public entry point. The debug menu is accessed by tapping the About screen version number 7 times.

---

## Files to read before making changes

| Area | Start here |
|---|---|
| Event catalog | `app/src/main/assets/events.json` + `data/seed/EventCatalogSyncer.kt` + this MEMORY.md section on "MDM-style versioned seed data" |
| Tithi math | `panchang/PanchangCalculator.kt` (Phase 1) + `panchang/TithiEndFinder.kt` + this MEMORY.md section on "Kshaya tithi" |
| Vrat logic | `panchang/VratLogic.kt` (Phase 2) + this MEMORY.md section on "Dashami-Viddha" and "Parana window" |
| Alarms | `alarms/AlarmScheduler.kt` (Phase 4) + `alarms/LocaleChangeReceiver.kt` + this MEMORY.md section on "Android alarm reliability traps" |
| Data model | `data/db/NavPanchangDb.kt` + all files in `data/db/entities/` |
| Architecture | `ARCH.md` for the picture, `TECH_DESIGN.md` for the implementation detail |
| Specs | `SPEC.md` for functional requirements |
| Release plan | `TECH_DESIGN.md` (the full approved plan) |

---

## Release checklist (before flipping Internal → Production)

- [ ] All unit tests pass including `AdhikMaasTest`, `VratLogicTest`, `TithiEndFinderTest`, `EventCatalogSyncerTest`.
- [ ] Manual mom-test passes on a real device in Hindi locale.
- [ ] 12 Closed Testing track testers installed for 14 consecutive days (Play Store 2026 rule for new developer accounts).
- [ ] AGPL compliance: LICENSE file, About attribution, Play Store description source link, bundled Swiss Eph LICENSE + LEGAL in assets.
- [ ] Reliability Check green on a Xiaomi and a Samsung test device.
- [ ] `USE_EXACT_ALARM` declared and approved in Play Console.
- [ ] Privacy policy live (no data leaves device).
- [ ] ProGuard rules verified — alarm flow still works on a `release` build.

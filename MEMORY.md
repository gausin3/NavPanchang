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
| **No analytics, no phone-home, no telemetry — usage = GitHub Releases download counts** | 2026-04-26 | The app's privacy posture is part of its product value. Every "we should know who's using it" temptation has been considered (Cloudflare Worker counter, Plausible, Firebase Analytics) and rejected. The single allowed source of usage signal is the per-asset `download_count` exposed by the GitHub Releases API, queryable via `scripts/download_counts.sh`. Caveat: download counts overcount unique users by roughly the average number of releases a user updates through; fine for early-stage signal. Do not add an analytics SDK without a unanimous, documented decision to revisit this. |
| **App update check uses GitHub Releases API directly, with Play-Store-aware guard** | 2026-04-26 | Sideloaded distribution (outside Play Store) means users won't auto-update unless the app tells them to. The check calls `api.github.com/repos/gausin3/NavPanchang/releases/latest` (no proxy, no Worker). When `getInstallerPackageName() == "com.android.vending"` the in-app card and notification disable themselves so Play Store handles updates. Opt-out toggle in Settings respects users who really want offline-only. See `docs/RELEASE.md`. |

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

### POST_NOTIFICATIONS runtime permission (Android 13+)

On Android 13+ (API 33+), `POST_NOTIFICATIONS` must be granted at runtime before any notification — including our vrat alarms — actually appears. Without it every alarm silently no-ops; the user has no signal anything is wrong.

`MainActivity.onCreate` requests this on every launch where it isn't yet granted (registered via `ActivityResultContracts.RequestPermission`). The Reliability Check section on Settings has a third row "Notifications enabled" that surfaces the state and offers a Fix button: re-prompts via the same launcher if not permanently denied, otherwise opens `Settings.ACTION_APP_NOTIFICATION_SETTINGS` so the user can re-enable manually.

`BatteryOptimizationCheck.Status.allGreen` requires this third checkbox alongside battery-whitelist and exact-alarm. Three things have to be right for alarms to fire reliably; surface all three, fix all three from one card.

No-op on Android < 33 — the permission is auto-granted at install on those versions, but `NotificationManagerCompat.areNotificationsEnabled()` still returns the right value because the user can also disable notifications via the app-level toggle on older Android.

---

## Locale and i18n traps

These fired silently in production-ish code and cost a meaningful amount of debugging time. Document so future contributors don't re-discover them.

### `LocalConfiguration.current.locales[0]` strips BCP-47 unicode extensions

In Compose, reading the active locale via `LocalConfiguration.current.locales[0]` returns a `Locale` with the **language and region intact but the unicode extension stripped**. So if `LanguageSwitchInterceptor` wrapped the activity context with `hi-u-nu-deva` (Hindi locale, Devanagari numbering system), `LocalConfiguration.current.locales[0]` reads back as plain `hi`. Anything depending on the extension (Devanagari numerals, Tamil digits, Gujarati digits) silently degrades to Latin.

**Right pattern** in Compose:

```kotlin
val configTrigger = LocalConfiguration.current  // recomposition signal only
val locale = remember(configTrigger) { Locale.getDefault() }   // ← keeps extension
```

`Locale.getDefault()` preserves the extension because `LanguageSwitchInterceptor.wrap` sets it via `Locale.setDefault(locale)` with the full BCP-47 tag intact. We still observe `LocalConfiguration` so the composable recomposes after a `recreate()`.

`util/DateFormatters.kt` is the canonical home for this pattern; copy from there if you need locale-extension-sensitive formatting in a new screen.

### `DateTimeFormatter` doesn't honor `nu-*` unicode extensions by itself

Even given a `Locale.forLanguageTag("hi-u-nu-deva")`, `DateTimeFormatter.ofPattern(...)` outputs **Latin digits** for dates and times. `String.format(locale, "%d", n)` and `NumberFormat.getInstance(locale).format(n)` both honor the extension; `DateTimeFormatter` does not.

**Right pattern:** chain `.withDecimalStyle(DecimalStyle.of(locale))`. `DecimalStyle.of(...)` DOES read the extension correctly and returns a style with the right zero-digit (e.g. `०` U+0966 for Devanagari).

```kotlin
DateTimeFormatter.ofPattern(pattern, locale)
    .withDecimalStyle(DecimalStyle.of(locale))
```

Without this the user can pick Numerals = Native in Settings, the toggle persists, the locale propagates correctly through `String.format`, but every `DateTimeFormatter`-rendered time/date stays Latin — half the screen renders correctly and half doesn't.

`rememberFormatter` and `makeFormatter` in `util/DateFormatters.kt` apply this automatically; use those instead of constructing `DateTimeFormatter` directly.

### Top-level `private val FORMATTER = DateTimeFormatter.ofPattern(..., Locale.getDefault())` is a footgun

Top-level Kotlin `val`s initialize once per class load with whatever `Locale.getDefault()` was at that moment. `LanguageSwitchInterceptor.wrap` runs in `attachBaseContext` later than first class-load on a fresh process. Result: even after the user picks Hindi+Native, the formatter stays bound to the original device locale forever — no amount of activity recreation fixes it.

Use `rememberFormatter(pattern)` from any `@Composable`, or `makeFormatter(pattern)` for non-Composable contexts (e.g., `BroadcastReceiver`). Never store a `DateTimeFormatter` in a `val` that survives across configuration changes.

---

## Conventions

- **Time storage:** Always epoch-millis UTC in Room. Convert to local times in the repository layer or UI via `AstroTimeUtils` / `ZoneId.systemDefault()`. Never store local time as a string except for `dateLocal` (which is explicitly tagged to the location).
- **Location of truth for rules:** `assets/events.json` is authoritative. Any change to a rule bumps that event's `seedVersion` *and* the top-level `catalogVersion`. Never edit rows in `event_definitions` outside `EventCatalogSyncer`.
- **User preferences sanctity:** Never write to `subscriptions` during a catalog upgrade. Never write to `subscriptions` from any worker. Only the UI writes to it.
- **Ephemeris scope:** Every panchang computation goes through `EphemerisScope().use { ... }`. Never create a bare `SwissEph` instance.
- **Ayanamsha resolution:** the user's persisted ayanamsha lives in `calc_metadata.ayanamshaType`. Resolve it **once per coroutine** via `metadataRepository.ayanamshaType()` (returns `LAHIRI` if missing/unknown) and thread the value down through `PanchangCalculator`, `OccurrenceComputer.Request`, etc. Never re-fetch per call inside a hot loop. `PanchangCalculator` does NOT carry ayanamsha state — every method takes it as an explicit parameter.
- **Feature gating:** Code inside `util.debug.*` is `if (BuildConfig.DEBUG) return` at every public entry point. The debug menu is accessed by tapping the About screen version number 7 times.
- **Audio assets are placeholders:** the four `app/src/main/res/raw/ritual_*.wav` files are procedurally synthesized by `scripts/generate_placeholder_ritual_sounds.py`, NOT field recordings. They're audibly distinct (so channel testing works) but clearly synthetic. Replace with CC0 recordings from Freesound or own recordings before any public Play Store release. Filename must stay the same — `NotificationChannels` resolves them by `R.raw.*` id at build time.

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

- [ ] All unit tests pass including `AdhikMaasTest`, `VratLogicTest`, `TithiEndFinderTest`, `EventCatalogSyncerTest`, `SwissEphemerisEngineTest`.
- [ ] Manual mom-test passes on a real device in Hindi locale.
- [ ] **Replace placeholder ritual audio** at `app/src/main/res/raw/ritual_*.wav` with real CC0 recordings (or attributable CC-BY with About-screen credit).
- [ ] 12 Closed Testing track testers installed for 14 consecutive days (Play Store 2026 rule for new developer accounts).
- [ ] AGPL compliance: top-level `LICENSE` file, About attribution, Play Store description source link, bundled `swisseph_NOTICE.txt` + `agpl-v3.txt` in `assets/licenses/`.
- [ ] Reliability Check green on a Xiaomi and a Samsung test device.
- [ ] `USE_EXACT_ALARM` declared and approved in Play Console.
- [ ] Privacy policy live (no data leaves device).
- [ ] ProGuard rules verified — alarm flow still works on a `release` build.
- [ ] CI tag-trigger gap fixed (`build` job's `if` extended to also accept `refs/tags/v*`).
- [ ] All five GitHub Actions secrets set: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_STORE_CONFIG_JSON`.

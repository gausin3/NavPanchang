# NavPanchang — Technical Design

This document describes *how* NavPanchang is implemented. For *what* it does, see [`SPEC.md`](SPEC.md). For the high-level picture, see [`ARCH.md`](ARCH.md).

---

## 1. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.0.20 | |
| UI | Jetpack Compose + Material 3 (BOM 2024.09.02) | |
| DI | Hilt 2.52 | Works well with WorkManager via `hilt-work`. |
| Persistence | Room 2.6.1 + DataStore Preferences 1.1.1 | Room for events/occurrences/alarms; DataStore for single-value prefs. |
| Background | WorkManager 2.9.1 + AlarmManager | WorkManager for the periodic 24-month top-up; AlarmManager for exact sunrise firings. |
| Location | `com.google.android.gms:play-services-location:21.3.0` | `FusedLocationProviderClient` + `GeofencingClient`. |
| Ephemeris | Swiss Ephemeris Java port by Thomas Mack (vendored in Phase 1) | AGPL inherits into the whole app. |
| Concurrency | Kotlinx Coroutines 1.9.0 + Flow | |
| Debug tooling | LeakCanary 2.14 (`debugImplementation`) | Monitors `RefreshWorker` and `OccurrenceComputer` during 24-month lookahead. |
| Min/target SDK | 26 / 34 | |
| JDK | 17 | |

---

## 2. Database schema (Room)

Five entities keyed around the idea that **user state** (`subscriptions`, `calc_metadata`) and **derived state** (`occurrences`, `scheduled_alarms`) are strictly separate from **seed state** (`event_definitions`) so the seed can be upgraded without touching the user's preferences.

```
event_definitions  ─────┐
  id (PK)               │ seed, versioned upsert
  seedVersion           │
  nameEn/nameHi         │
  ruleType/Params       │
  observeInAdhik        │
  hasParana             │
  deprecated            │
                        │
subscriptions  ◄────────┘
  eventId (PK, FK)        user preference, never touched on upgrade
  enabled                 planner/observer/parana toggles
  customPlannerHhmm
  customSoundId

occurrences                 derived, recomputable
  id (PK)
  eventId (FK)
  dateLocal                 ("YYYY-MM-DD" in the location's tz)
  sunriseUtc
  observanceUtc             fire target for OBSERVER
  paranaStartUtc/EndUtc
  shiftedDueToViddha        diagnostic flags for UI
  isKshayaContext
  lunarMonth / isAdhik
  locationTag               "HOME" | "CURRENT"
  isHighPrecision           0 for HOME, 1 for CURRENT
  UNIQUE(eventId, dateLocal, locationTag)

scheduled_alarms            1:1 with AlarmManager PendingIntents
  requestCode (PK)          derived from (occurrenceId, kind)
  occurrenceId (FK)
  kind                      "PLANNER" | "OBSERVER" | "PARANA"
  fireAtUtc
  channelId
  pendingStatus

calc_metadata               singleton (id = 1)
  homeCityName/Lat/Lon/Tz
  lastCalcLat/Lon
  eventCatalogVersion       used by EventCatalogSyncer
  ayanamshaType / calculationTradition
  sunriseOffsetMinutes      user override (see §8 UX knobs)
```

Schemas are exported to `app/schemas/` via `ksp { arg("room.schemaLocation", ...) }` to drive future AutoMigration tests.

---

## 3. Panchang calculation

### 3.1 Swiss Ephemeris integration

- **Library:** Thomas Mack's Java port of Swiss Ephemeris, vendored under `ephemeris/swisseph/` in Phase 1.
- **Ephemeris files:** `sepl_18.se1`, `semo_18.se1`, `seas_18.se1` (1800–2399 CE, ~5 MB) in `assets/ephe/`. Copied to `filesDir/ephe/` on first launch; `swe_set_ephe_path` points there.
- **Configuration:** Lahiri ayanamsha (`SE_SIDM_LAHIRI`), default tidal acceleration.

### 3.2 `EphemerisScope` — GC hygiene

The Java port returns values via out-parameter wrappers (`DoubleRef`, `IntRef`). A 24-month lookahead allocates ~50 000 of these. To avoid GC churn:

```kotlin
class EphemerisScope : AutoCloseable {
    private val swe = SwissEph()
    private val scratch = ArrayDeque<DoubleRef>(8)

    fun calc(jd: Double, body: Int, flags: Int): DoubleArray { ... }

    override fun close() {
        swe.swe_close()
        scratch.clear()
    }
}
```

All computations go through `EphemerisScope().use { ... }`. LeakCanary specifically watches `RefreshWorker` to catch scopes that escape the `use` block.

### 3.3 `PanchangCalculator`

For a given `(date, lat, lon, tz)`, produces:

1. **Sunrise** — `swe_rise_trans(SE_CALC_RISE, SEFLG_SWIEPH)`.
2. **Arunodaya** — `sunrise - 96 minutes` (4 ghatikas), used for Vrat logic.
3. **Sun longitude** at any instant — `swe_calc_ut(SE_SUN)` with Lahiri.
4. **Moon longitude** — `swe_calc_ut(SE_MOON)` with Lahiri.
5. **Tithi index**: `floor(((moonLon - sunLon) mod 360) / 12) + 1`. 1–15 Shukla, 16–30 Krishna, 30 = Amavasya.
6. **Nakshatra**: `floor(moonLon / (360/27)) + 1`.
7. **Yoga, Karana, Vara** — standard formulas.
8. **Lunar month** — via `getLunarMonth()` (§3.5).

All time I/O goes through `AstroTimeUtils` (epoch-millis ↔ Julian Day).

### 3.4 `TithiEndFinder`

Replaces naive binary search with a **chunk-then-refine** strategy that handles Kshaya tithis:

1. Step forward in 15-minute chunks, computing `delta = (moonLon − sunLon) mod 12` at each step.
2. When `delta` wraps (next value < current), the boundary lies inside that chunk.
3. Refine with **golden-section search** on the residual function until end-time is accurate to < 1 second.
4. Record *all* tithi boundaries crossed between sunrise(D) and sunrise(D+1) — a Kshaya tithi produces two.

### 3.5 Adhik Maas detection

A lunar month is delimited by two consecutive Amavasyas. Count the Solar Sankrantis (Sun enters a new Rashi — longitude crosses a multiple of 30°) strictly between them:

| Sankranti count | Month type |
|---|---|
| 0 | **Adhik** (extra) — repeats the previous month with "Adhik" prefix |
| 1 | **Nija** (normal) |
| 2 | **Kshaya** (extremely rare — last in 1963, next in 2124) |

Each `OccurrenceEntity` is tagged with `lunarMonth` and `isAdhik`. Events with `observeInAdhik = false` are suppressed entirely in Adhik months (no alarm ever fires).

### 3.6 Vrat logic (`VratLogic.kt`)

**Dashami-Viddha for Ekadashi:**

```
for each day D where tithi-at-sunrise == 11 (or 26):
    arunodaya = sunrise(D) - 96 min
    if tithi-at-arunodaya in {10, 25}:
        emit Ekadashi on D+1  (shifted_due_to_viddha = true)
    else:
        emit Ekadashi on D
```

**Parana window:**

```
paranaStart = sunrise(D+1)
paranaEnd = min(
    endOfDvadashi,
    startOfDvadashi + dvadashiDuration / 4   // Harivasara quarter
)
```

Both bounds are emitted to `OccurrenceEntity.paranaStartUtc/paranaEndUtc` and scheduled as a third `PARANA` alarm.

**Kshaya tithi fallback:**

If the target tithi is Kshaya (begins and ends between two sunrises and so no day "owns" its sunrise), emit the occurrence on the day the tithi was longest-prevailing and set `isKshayaContext = true`. The UI shows a `[!]` with an explanation rather than silently skipping.

### 3.7 `OccurrenceComputer`

Walks a date range, computes panchang at sunrise for each day, checks each subscribed event's `EventRule`, and emits matching occurrences. Performance: ~730 days × ~5 ms/day ≈ 4 s on a mid-range device. Runs inside `EphemerisScope().use` on `Dispatchers.Default`.

---

## 4. Two-tier lookahead (travel-aware)

### 4.1 Tier 1 — HOME (24 months)

- Computed for the user's **Home city** (not current GPS position).
- Triggered by: explicit Home city change, or `RefreshWorker` monthly top-up when the window drops below 3 months ahead.
- Rows tagged `locationTag = "HOME"`, `isHighPrecision = false`.

### 4.2 Tier 2 — CURRENT (30–60 days)

- Computed for the **current GPS position**.
- Triggered by a **100 km geofence exit** (not polled distance).
- Rows tagged `locationTag = "CURRENT"`, `isHighPrecision = true`.
- Pruned continuously: rows older than yesterday are deleted.
- Fallback when background-location permission is denied: foreground distance check on every app open.

### 4.3 Geofence lifecycle

```
User sets Home city (Lucknow) → compute Tier 1 24 months.
Onboarding completes → register geofence (100 km radius around Lucknow).
User flies to Dubai → Android fires GeofenceBroadcastReceiver.
Receiver enqueues RefreshWorker.
RefreshWorker:
    1. Read fused location
    2. Compute Tier 2 30–60 days for Dubai
    3. Re-arm Observer/Parana alarms (Planner is unchanged — wall-clock)
    4. Register new geofence around Dubai
    5. Update calc_metadata.last_calc_lat/lon
```

### 4.4 Query precedence

`OccurrenceDao.getNextOccurrence(eventId, today)` orders by `isHighPrecision DESC` then `dateLocal ASC` — so a Tier 2 row always wins if one exists for the date, and the app falls back to Tier 1 otherwise.

---

## 5. Alarms

### 5.1 Dual-alarm model

For every occurrence of a subscribed event:

| Kind | Trigger | Anchor | Channel |
|---|---|---|---|
| `PLANNER` | Day before, 8:00 PM local wall-clock (configurable) | Wall-clock | `channel_event_reminders` |
| `OBSERVER` | Day of, local sunrise | Epoch-millis (UTC) | `channel_ritual_<sound>` |
| `PARANA` (Ekadashi only) | D+1, sunrise | Epoch-millis (UTC) | `channel_ritual_<sound>` |

### 5.2 `AlarmScheduler`

Uses `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` with a stable `requestCode` derived from `(occurrenceId * 3 + kindOrdinal)`. Writes a row to `scheduled_alarms` for every PendingIntent it posts so boot/timezone receivers can re-arm.

API 31+ permission handling:

1. Declare `USE_EXACT_ALARM` (Play Store-safe — we apply with a religious-observance justification).
2. Check `alarmManager.canScheduleExactAlarms()` on first run and before every schedule.
3. If denied, show a dialog with a deep link to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.

### 5.3 `BootReceiver`

Listens for `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`. Enqueues a one-shot `RefreshWorker` → the worker re-posts every row in `scheduled_alarms` whose `fireAtUtc > now`.

### 5.4 `LocaleChangeReceiver` — the traveling wall-clock

`ACTION_TIMEZONE_CHANGED` handling:

1. Read all `scheduled_alarms WHERE kind = 'PLANNER' AND fireAtUtc > now`.
2. For each, recompute `fireAtUtc` from `(dateLocal, customPlannerHhmm)` in the **new** device timezone.
3. Cancel the old `PendingIntent` and re-post.

Observer and Parana alarms are epoch-millis UTC and need no adjustment. Only display times refresh (via `ZoneId.systemDefault()` at render time).

`ACTION_LOCALE_CHANGED` refreshes string resources in any pending notifications.

### 5.5 `LateSubscriptionGate`

When a new occurrence is being scheduled, if `fireAt < now`:

- Do not schedule the alarm.
- If the skipped alarm was a PLANNER whose OBSERVER is still in the future, write `pendingStatus = "READY_FOR_TOMORROW"` to the occurrence.
- The home screen reads this flag and shows a green "Observing Tomorrow" card instead of a yellow "Preparing" card.

No stale notification ever fires.

### 5.6 Notification channels and audio

Four ritual-alarm channels are created up-front (channel sound is immutable after creation on Android 12+):

- `channel_ritual_bell` — `ritual_temple_bell.ogg`
- `channel_ritual_sankh` — `ritual_sankh.ogg`
- `channel_ritual_toll` — `ritual_bell_toll.ogg`
- `channel_ritual_om` — `ritual_om_mantra.ogg`

The Settings sound picker chooses which channel an alarm posts on. Vibration pattern for ritual channels: `[500, 200, 500, 200, 800]` — a bell-toll cadence recognizable on vibrate-only.

Two more channels: `channel_event_reminders` (low importance, default sound) and `channel_daily_briefing` (low importance).

### 5.7 Notification actions

- Planner notifications: "Remind me at Sunrise" (safety-net action that schedules a one-shot Observer alarm) + "Dismiss".
- Observer (Ekadashi) notifications: "Parana reminder" (confirms/re-arms the Parana alarm) + "Dismiss".

### 5.8 `BatteryOptimizationCheck`

Called on first run and whenever Settings is opened. Checks `PowerManager.isIgnoringBatteryOptimizations(packageName)`. If false, the Settings reliability section shows a warning row with a button to launch `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. A visible dot on the bottom-nav Settings tab surfaces unresolved issues.

### 5.9 `RefreshWorker` (daily `PeriodicWorkRequest`)

- Extends Tier 1 HOME buffer to maintain a 24-month rolling horizon.
- Prunes Tier 2 CURRENT rows older than yesterday.
- Prunes `scheduled_alarms` rows with `fireAtUtc < now`.
- Re-arms any alarms that got dropped by aggressive OEMs (defense in depth).

---

## 6. Seed data governance

See `assets/events.json`:

```json
{
  "catalogVersion": 1,
  "events": [
    {
      "id": "shukla_ekadashi",
      "seedVersion": 1,
      "nameEn": "Shukla Ekadashi",
      "nameHi": "शुक्ल एकादशी",
      "ruleType": "TithiAtSunrise",
      "ruleParams": { "tithiIndex": 11, "vratLogic": true },
      "observeInAdhik": true,
      "hasParana": true,
      ...
    }
  ]
}
```

`EventCatalogSyncer` runs on every cold start:

1. Read `catalogVersion` from the bundle.
2. Read `calc_metadata.eventCatalogVersion` from DB.
3. If `bundled > db`:
   - Upsert all bundled events into `event_definitions`.
   - Mark any DB event_ids missing from the bundle as `deprecated = 1`.
   - Update `calc_metadata.eventCatalogVersion`.
   - Signal `OccurrenceComputer` to recompute any event with a changed `seedVersion`.
4. The `subscriptions` table is **never touched** — user preferences survive every upgrade.

Deprecated events stay visible to existing subscribers with a "This rule was updated — tap to migrate" banner, but are hidden from the "+ Add more events" picker.

---

## 7. UI technical notes

### 7.1 Navigation

`androidx.navigation:navigation-compose`. Three top-level routes: `subscriptions`, `calendar`, `settings`. Bottom bar selection uses `currentBackStackEntryAsState()` to highlight the active tab. Nested routes for detail screens (`event_detail/{eventId}`, `day_detail/{date}`).

### 7.2 State management

- `SubscriptionsViewModel` collects `subscriptionDao.observeAll()` + per-event `getNextOccurrence()` and exposes a single `UiState` via `StateFlow`.
- `CalendarViewModel` observes `occurrenceDao.observeRange(from, to)` for the visible month.
- ViewModels are Hilt-injected with `@HiltViewModel`.

### 7.3 Theme

Material 3 dynamic color where available (Android 12+), with a brand saffron + deep indigo fallback. Typography uses system fonts — Noto Sans Devanagari is an Android 8+ fallback font so Hindi glyphs (एकादशी) render correctly in an English-locale UI without font loading.

### 7.4 `LanguageSwitchInterceptor`

A `ContextWrapper` that lets Settings switch the app language independent of the system locale. Wraps the base context in `attachBaseContext()`, swapping `Resources.configuration.locale`. Wired in Phase 7.

### 7.5 `NotificationTestingTool` (debug builds only)

Hidden menu accessed by tapping the About version number 7 times. Fires PLANNER/OBSERVER/PARANA alarms immediately, simulates a late subscription, simulates a timezone change. Every entry point is guarded by `BuildConfig.DEBUG` so it cannot ship to production.

---

## 8. UX correctness knobs

- **`calc_metadata.sunriseOffsetMinutes`** — user-configurable ±1 / ±2 min override for reconciliation with a trusted local paper panchang. Applied to every sunrise-anchored output.
- **PreparationCard state machine** — yellow "Preparing", green "Observing", cyan "Parana", hidden otherwise. Driven by time-to-next-event + current time vs Observer/Parana windows.
- **Location Badge** — whenever a Tier 2 (CURRENT) row overrides a Tier 1 (HOME) row by ≥ 2 minutes, the Home screen and notification body append "(Adjusted for [city] time)".

---

## 9. Testing

| Test | What it covers |
|---|---|
| `PanchangCalculatorTest` | 30+ golden (date, city) tuples cross-referenced against drikpanchang.com, < 60 s tolerance on tithi end-times |
| `TithiEndFinderTest` | Normal boundary, exact-midnight boundary, synthetic Kshaya boundary |
| `AdhikMaasTest` | 2023 Shravan Adhik, 2020 Ashwin Adhik, 2024 Chaitra Nija; Mahashivratri suppression in Adhik Phalguna; Ekadashi firing in Adhik Shravan |
| `VratLogicTest` | Dashami-Viddha shift, Kshaya Ekadashi safe-fallback, Parana window (including Harivasara quarter) |
| `OccurrenceComputerTest` | 24-month Delhi run; assert Ekadashi count = 24 or 25 and all dates match mypanchang.com |
| `EventCatalogSyncerTest` | Versioned upsert preserves subscriptions across catalog upgrade; deprecated events marked correctly |
| `AlarmSchedulerTest` (instrumented) | Dual-alarm wiring, boot re-arm, timezone-change re-arm, late-subscription gate, "Remind me at Sunrise" action |
| `OccurrenceRepositoryTest` (instrumented) | HOME/CURRENT precedence query |

---

## 10. Build & release

### 10.1 Build

Gradle version catalog in `gradle/libs.versions.toml` (no hard-coded versions in `build.gradle.kts`). KSP for Room and Hilt (not KAPT) — ~2× faster builds. Schemas exported to `app/schemas/`.

Release builds use R8 with `isMinifyEnabled = true` and `isShrinkResources = true`. ProGuard keeps:

- `swisseph.**` — reflection access from the Java port.
- `com.navpanchang.data.db.entities.**` — Room reflection.
- `com.navpanchang.data.seed.**` — JSON parsing.
- Hilt generated classes.

### 10.2 Licensing

AGPL v3, public GitHub. Compliance checklist:

- [x] Project `LICENSE` = AGPL v3
- [ ] `assets/licenses/swiss_ephemeris_LICENSE` + `swiss_ephemeris_LEGAL` copied from upstream (added in Phase 1)
- [ ] About screen attribution + clickable source link (Phase 7)
- [ ] Third-party licenses list (`gradle-licensee` plugin) (Phase 7)
- [ ] Play Store description ends with "Open source. AGPLv3. Source at: ..."
- [ ] GitHub README covers build instructions and AGPL inheritance

### 10.3 CI/CD (GitHub Actions)

Three-stage pipeline: **Validate** → **Build** (signed AAB + SBOM) → **Deploy** (Internal track). Secrets: base64-encoded keystore, Play service account JSON. `versionCode = github.run_number` so every CI build is unique and uploadable.

See `.github/workflows/ci.yml` (added in Phase 9).

---

## 11. Phase 0 status

✅ Gradle module, version catalog, manifest, permissions, backup rules
✅ Hilt-wired `NavPanchangApp` + `MainActivity` + 3-tab Compose nav
✅ Material 3 theme, en/hi string resources
✅ Room `NavPanchangDb` with 5 entities + 5 DAOs
✅ Placeholder receivers (Boot, LocaleChange, Alarm, Geofence)
✅ `EventCatalogSyncer` + bundled `events.json` (10 events)
✅ Phase 0 utilities — `AstroTimeUtils`, `LanguageSwitchInterceptor`, `NotificationTestingTool`
✅ LeakCanary wired into `debugImplementation`

## 12. Phase 1a status (this commit)

Phase 1 is split into two sub-phases so work can progress without the Thomas Mack JAR
being immediately available:

**Phase 1a — Pure-Kotlin Meeus engine (shipped now):**
- ✅ Domain types: `Tithi`, `Paksha`, `Nakshatra`, `LunarMonth`, `PanchangSnapshot`
- ✅ Ephemeris math: `AstroMath`, `Ayanamsha` (Lahiri with correct ~1.4°/century drift)
- ✅ `EphemerisEngine` interface + `MeeusEphemerisEngine` (top 35 lunar terms + full
     solar series, ~20-arcsec precision on lunar longitude → ~40-second tithi-boundary
     error, inside the < 60 s plan tolerance)
- ✅ `SunriseCalculator` (Meeus Chapter 15 with single-iteration refinement, accurate to
     ~1–2 min at mid-latitudes)
- ✅ `EphemerisScope` (`AutoCloseable` wrapper — no-op for Meeus, ready for Swiss Eph)
- ✅ `SwissEphemerisEngine` stub with the Phase 1b vendoring checklist in its KDoc
- ✅ `PanchangCalculator` (tithi/nakshatra/sunrise + Arunodaya helper for Dashami-Viddha)
- ✅ `TithiEndFinder` (chunk-then-refine bisection, handles Kshaya tithis by exposing
     `findAllTithiEndsInWindow`)
- ✅ `EphemerisModule` (Hilt provider — one-line swap to Swiss Eph when vendored)
- ✅ Unit tests: `MeeusEphemerisEngineTest`, `PanchangCalculatorTest`, `TithiEndFinderTest`,
     `TithiTest` — validated against published new/full moon times and sidereal Rashi
     identification, loose tolerance (~1° longitude / ~10 min tithi-boundary) to catch
     gross errors

**Phase 1b — Swiss Ephemeris vendoring (deferred):**
- ⬜ Download Thomas Mack Java port from `http://www.th-mack.de/international/download/`
- ⬜ Vendor JAR under `app/libs/swisseph.jar`
- ⬜ Bundle `.se1` data files under `app/src/main/assets/ephe/`
- ⬜ Copy `swiss_ephemeris_LICENSE` and `swiss_ephemeris_LEGAL` under
     `app/src/main/assets/licenses/` for AGPL compliance
- ⬜ Wire `NavPanchangApp.onCreate()` to copy `.se1` files to `filesDir/ephe/` once
- ⬜ Fill in `SwissEphemerisEngine` method bodies (calls to `swe_calc_ut`, `swe_rise_trans`,
     etc. with Lahiri sidereal mode)
- ⬜ Flip `EphemerisModule.provideEphemerisEngine()` to return `SwissEphemerisEngine()`
- ⬜ Tighten test tolerances once the precision upgrade is in place

## 13. Phase 2 status (this commit)

**Religious correctness layer — ships now:**

- ✅ `EventRule` sealed hierarchy: `TithiAtSunrise(tithiIndex, vratLogic)`,
     `EveningTithi(tithiIndex)`, `TithiInLunarMonth(tithi, lunarMonth, suppressInAdhik)`
- ✅ `EventDefinition` + `EventCategory` + `ObserverAnchor` — parsed domain types
     separate from the Room entity
- ✅ `EventRuleParser` — `EventDefinitionEntity` → `EventDefinition` and JSON-blob →
     `EventRule` mapping with upfront validation
- ✅ `Rashi` enum (Mesha…Meena) with English + Devanagari names
- ✅ `SankrantiFinder` — chunk-then-refine over sidereal Sun longitude, 6h chunks,
     1-second bisection. Validated against 2024 Makara (Jan 14–15) and Mesha (Apr 13–14)
- ✅ `AmavasyaFinder` — filter on top of `TithiEndFinder` boundaries selecting 30→1
     transitions
- ✅ `AdhikMaasDetector` — counts Sankrantis between consecutive Amavasyas, classifies
     each lunar month as Nija/Adhik/Kshaya, assigns names via the Sankranti-of-the-month
     bijection (Mesha → Chaitra, Simha → Shravana, Meena → Phalguna, etc.). Adhik months
     borrow the *next* Nija month's name. Validated against 2023 Adhik Shravana.
- ✅ `VratLogic.applyDashamiViddha` — checks tithi at Arunodaya (sunrise − 96 min), shifts
     Ekadashi to D+1 if Dashami is taint-present, returns a diagnostic `ViddhaResult`
- ✅ `VratLogic.computeParanaWindow` — finds Dvadashi tithi bounds via `TithiEndFinder`
     and applies the Harivasara quarter rule to cap `paranaEnd`
- ✅ `Occurrence` domain data class + `OccurrenceComputer` — walks a date range day by
     day, pre-classifies lunar months once, runs each `EventDefinition` against the
     current day, applies Dashami-Viddha + Adhik suppression + Kshaya fallback,
     dedupes via `alreadyEmittedVrat`, and emits `Occurrence` rows tagged with
     `locationTag` / `isHighPrecision`
- ✅ Double-sunrise edge case guard — for unusually long tithis (~26h) that span two
     consecutive sunrises, observation defers to the second day (traditional rule)
- ✅ Tests: `EventRuleParserTest` (JSON round-trip), `SankrantiFinderTest` (12/year
     sanity + Makara/Mesha date windows), `AdhikMaasTest` (2023 Adhik Shravana, 2024
     Nija-only, Sankranti→month bijection, contiguity), `VratLogicTest` (Dashami-Viddha
     contract, Parana window bounds, Harivasara quarter), `OccurrenceComputerTest`
     (year-long Ekadashi count, Purnima count, Mahashivratri suppression outside Nija
     Phalguna, no-duplicates invariant)
- ✅ `org.json:json` added as `testImplementation` so `EventRuleParserTest` runs as a
     local unit test instead of instrumented

**Phase 2b — partially cleared in Phase 3:**
- ✅ Kshaya-tithi context wiring: `OccurrenceComputer.detectKshayaForDay` now scans the
  sunrise(D)→sunrise(D+1) window; any tithi with 2 boundaries in it is identified as
  Kshaya, and if it matches a subscribed vrat target, an `Occurrence` is emitted with
  `isKshayaContext = true`.
- ⬜ Evening-sunset anchor for Pradosh — still deferred; Phase 2 `OccurrenceComputer`
  uses sunrise as a proxy for Pradosh events. Will land in a future release.

## 14. Phase 3 status (this commit)

**Data layer + background refresh — ships now:**

- ✅ `OccurrenceMapper` — pure domain/entity mapper; `LocalDate` ↔ ISO string,
     `LunarMonth` enum ↔ string name, graceful decode of unknown strings to `null`
- ✅ `OccurrenceRepository` — the single query path with `getNextOccurrence()` using
     `ORDER BY isHighPrecision DESC` precedence, `replaceWindow(locationTag, ...)` for
     tier-scoped atomic replacement, `pruneBefore()` for daily housekeeping, and a
     Flow-backed `observeRange()` for the Calendar UI
- ✅ `SubscriptionRepository` — `getEnabledDefinitions()` (what `RefreshWorker` feeds
     into `OccurrenceComputer`), `getAllAvailableDefinitions()` (the "+ Add events"
     picker), `setEnabled()` that preserves all customization fields
- ✅ `MetadataRepository` — `getOrDefault()`, `setHomeCity()` (clears
     `lastHomeCalcAt` to force recompute), `recordHomeCalc()` / `recordCurrentCalc()`,
     `setSunriseOffsetMinutes()`
- ✅ `RefreshWorker` (`@HiltWorker` + `@AssistedInject`) — reads metadata + enabled
     definitions, computes the 24-month HOME lookahead, replaces Tier 1 atomically,
     prunes stale rows, records the calc instant. Handles missing home city as a
     no-op success, zero subscriptions as an explicit clear. Retries up to 3 times
     on failure.
- ✅ `RefreshScheduler` — `schedulePeriodic()` (daily, KEEP policy, idempotent across
     cold starts) and `enqueueOneShot()` (for boot and catalog-upgrade triggers)
- ✅ `NavPanchangApp.onCreate()` — schedules the periodic worker, enqueues a one-shot
     when `EventCatalogSyncer` reports an upgrade
- ✅ `BootReceiver` (`@AndroidEntryPoint`) — re-registers the periodic worker and
     enqueues an immediate one-shot on reboot / package replace
- ✅ Kshaya detection wired into `OccurrenceComputer.detectKshayaForDay` — closes the
     Phase 2b carry-over
- ✅ `EventCatalogSyncer` refactored with `syncFromJson(json: String)` for testability;
     `syncIfNeeded()` now reads assets and delegates

**Tests — 4 new suites, all runnable locally via Robolectric:**

- ✅ `OccurrenceMapperTest` (pure unit) — round-trip fidelity, ISO date serialization,
     null `lunarMonth` handling, graceful decode of unknown month strings, both tiers
- ✅ `OccurrenceRepositoryTest` (Robolectric + in-memory Room) — HIGH_PRECISION
     precedence, HOME fallback, `replaceWindow` tier isolation (HOME replace doesn't
     touch CURRENT), tag assertion, pruning
- ✅ `SubscriptionRepositoryTest` (Robolectric) — deprecated-event filtering,
     preservation of custom planner time + sound + Parana sub-toggle across
     `setEnabled()`, enabled-only query
- ✅ `EventCatalogSyncerTest` (Robolectric) — **the critical MDM invariant**: user
     subscriptions survive catalog-version bumps unchanged; unchanged / downgraded
     versions are no-ops; events missing from the new bundle are marked
     `deprecated = 1`, not deleted

**Build changes:**
- Added `org.robolectric:robolectric:4.13`, `androidx.test:core-ktx:1.6.1`, and
  `androidx.room:room-testing` as `testImplementation`
- Enabled `testOptions.unitTests.isIncludeAndroidResources = true` so Robolectric can
  find the merged resource IDs

## 15. Phase 4 status (this commit)

**Alarm + notification layer — ships now:**

- ✅ `AlarmKind` — enum PLANNER / OBSERVER / PARANA with stable `requestCodeFor()` that
     encodes `(occurrenceId * 3 + kind.ordinal)` for idempotent re-arming.
- ✅ `NotificationChannels` — creates 6 channels idempotently on every cold start:
     `EVENT_REMINDERS` (low), `DAILY_BRIEFING` (low), and four ritual variants
     (`RITUAL_BELL`, `RITUAL_SANKH`, `RITUAL_TOLL`, `RITUAL_OM`, all high). Channel
     sound is set to the system default for Phase 4; Phase 5 will swap in bundled
     `R.raw.ritual_*.ogg` assets. `channelIdForSound(soundId)` maps a stored sound
     preference to the correct channel.
- ✅ `NotificationBuilder` — pure helper that builds `NotificationCompat.Builder` per
     [AlarmKind] with localized title/body, time formatting via `DateTimeFormatter`,
     tap-to-open `MainActivity`, and the "Remind me at Sunrise" action button on
     Planner notifications.
- ✅ `AlarmRepository` — thin wrapper over `AlarmDao`; `getPending`, `getPendingByKind`,
     `upsert`, `deleteByRequestCode`, `pruneExpired`.
- ✅ `LateSubscriptionGate` — pure decision helper that turns "alarm fire time is in the
     past" into a clean `Schedule` / `Skip` result. Default slack 60 seconds covers the
     race between worker compute and `AlarmManager.set`.
- ✅ `BatteryOptimizationCheck` — surfaces `isIgnoringBatteryOptimizations()` and
     `canScheduleExactAlarms()` as a single `Status(allGreen)` result for the Settings
     Reliability Check section (Phase 7 will render this as UI).
- ✅ `AlarmScheduler` (`@Singleton`) — the single surface for scheduling all three
     alarm kinds. Handles wall-clock Planner computation via `ZonedDateTime`, resolves
     the user's home timezone from `MetadataRepository`, routes Observer/Parana to the
     right ritual channel, persists every PendingIntent to `scheduled_alarms`, and
     guards late alarms via `LateSubscriptionGate` (Planner skips write a
     `pending_status = "READY_FOR_TOMORROW"` row for the home-screen state card).
     `cancelForOccurrence` reaps all three kinds. `rearmAllPending` re-posts everything
     after boot.
- ✅ `AlarmReceiver` (`@AndroidEntryPoint`) — validates the subscription is still
     enabled and the sub-alarm toggle matches, loads the parsed `EventDefinition`,
     resolves the display timezone from metadata, builds + posts the notification via
     `NotificationBuilder`, deletes the `scheduled_alarms` row. Uses `goAsync` +
     supervisor coroutine scope to hit Room off the main thread. Also handles the
     `ACTION_SNOOZE_TO_SUNRISE` action (Phase 5 will wire the schedule call).
- ✅ `LocaleChangeReceiver` (`@AndroidEntryPoint`) — on `ACTION_TIMEZONE_CHANGED`, reads
     every pending PLANNER row and delegates back to
     `AlarmScheduler.scheduleForOccurrence` which recomputes in the NEW timezone. On
     `ACTION_LOCALE_CHANGED`, logs — future notification posts pick up new strings
     automatically.
- ✅ `BootReceiver` (`@AndroidEntryPoint`) — now also calls
     `RefreshScheduler.enqueueOneShot()` so the HOME buffer is fresh for the user's
     first launch post-boot.
- ✅ `NavPanchangApp.onCreate()` — calls `NotificationChannels.ensureChannels(this)`
     before any alarm can fire.
- ✅ `RefreshWorker` — extended to also **schedule alarms** after the lookahead.
     Iterates enabled subscriptions, reads back the freshly-inserted `OccurrenceEntity`
     rows, and calls `AlarmScheduler.scheduleForOccurrence` for each. Capped at
     `ALARM_HORIZON_LIMIT = 6` occurrences per event to stay well under Android's
     ~500-PendingIntent soft cap.
- ✅ English + Hindi string resources for all notification copy (`notification_planner_*`,
     `notification_observer_*`, `notification_parana_*`, `notification_action_*`).

**Tests — 5 new suites:**

- ✅ `AlarmKindTest` (pure) — enum sanity + `AlarmScheduler.requestCodeFor` distinctness
     across kinds and occurrences.
- ✅ `LateSubscriptionGateTest` (pure) — future/past/slack-window/custom-slack cases,
     Skip reason formatting.
- ✅ `NotificationChannelsTest` (Robolectric) — verifies all 6 channels are created
     idempotently, importance levels match spec, `channelIdForSound` mapping,
     ritual-vibration pattern is a valid bell-toll cadence.
- ✅ `AlarmRepositoryTest` (Robolectric + in-memory Room) — pending queries sorted by
     fire time, `getPendingByKind` filter, upsert-by-request-code replacement, targeted
     delete, pruneExpired, empty-table no-ops.
- ✅ `AlarmSchedulerTest` (Robolectric + ShadowAlarmManager + in-memory Room) — Ekadashi
     schedules 3 alarms, Planner fires at 8 PM home timezone, `customPlannerHhmm`
     overrides default, Purnima has no Parana, Observer routes to the ritual sound
     channel, late Planner is skipped with `READY_FOR_TOMORROW` while Observer+Parana
     still fire, per-sub-alarm toggles, `cancelForOccurrence` reaps all three kinds.

**Open for Phase 5:**
- Bundled `R.raw.ritual_{bell,sankh,toll,om_mantra}.ogg` audio assets
- `ACTION_SNOOZE_TO_SUNRISE` action: currently logs-only; needs to invoke
  `AlarmScheduler.scheduleForOccurrence` with Observer-only.

## 16. Phase 5 status (this commit)

**Subscription-first Home screen UI — ships now:**

- ✅ `PreparationCardState` — sealed class + `PreparationCardState.from(nowUtc,
     candidates)` pure state machine. `Hidden` → `Preparing` (yellow, within 36h) →
     `Observing` (green, between sunrise and Parana end / 24h) → `Parana` (cyan, inside
     the Parana window). Priority order: Parana > Observing > Preparing > Hidden.
- ✅ `HomeUiState` + `TodayStatus` + `SubscriptionRowState` — immutable data classes
     that shape what the Home screen renders
- ✅ `SubscriptionsViewModel` (`@HiltViewModel`) — combines subscription state,
     calc metadata, and a 60-second ticker into a single `StateFlow<HomeUiState>`. On
     toggle, writes to `SubscriptionRepository.setEnabled` and enqueues a one-shot
     `RefreshScheduler` so alarms are scheduled immediately rather than waiting for the
     next periodic tick.
- ✅ `TodayStatusCard` — always-visible card with today's tithi (bilingual) and nakshatra
- ✅ `PreparationCard` — yellow/green/cyan state-driven card with bell-toll-style copy
- ✅ `SubscriptionRow` — Material 3 card with event name, next-date subtitle,
     Material Switch for enable/disable, tap-to-detail
- ✅ `SubscriptionsScreen` — full home screen layout (loading state,
     home-city-missing state, `LazyColumn` with `TodayStatusCard` +
     conditional `PreparationCard` + subscription list + "Add more events" section)
- ✅ `EventDetailUiState` + `EventDetailViewModel` — loads event definition +
     subscription + next 12 occurrences; exposes mutators for every knob (enabled,
     planner/observer/parana sub-toggles, custom planner time, custom sound). Writes
     only touch the `subscriptions` table — never event definitions
- ✅ `EventDetailScreen` — `TopAppBar` with back navigation, English + Hindi header,
     four toggle cards (subscribed + per-sub-alarm), upcoming occurrence list with
     Parana windows and Dashami-Viddha / Kshaya indicators
- ✅ `NavPanchangNavGraph` — added `EVENT_DETAIL` route with `eventId` string argument;
     `SubscriptionsScreen.onEventClick` navigates there
- ✅ `OnboardingScreen` — three-step shell (Welcome → Permissions → Done) with three
     permission-request handlers: `POST_NOTIFICATIONS` via
     `rememberLauncherForActivityResult`, `SCHEDULE_EXACT_ALARM` via
     `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, battery whitelist via
     `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Phase 5b will add a city picker
     step and subscription pre-selection.
- ✅ `AlarmManagerCompat` — thin compat helper over `AlarmManager.canScheduleExactAlarms()`
     (introduced in API 31 (S)). Our `minSdk = 26` meant three sites were calling an
     API-31-only method directly; all three now go through the helper which returns
     `true` on older platforms where exact alarms are always permitted.
- ✅ **`ACTION_SNOOZE_TO_SUNRISE` wired**: `AlarmReceiver.snoozeToSunrise(occurrenceId)`
     loads the occurrence, forces `observerEnabled = true` / `plannerEnabled = false`
     on an in-memory copy of the user's subscription (no persisted writes), and
     delegates to `AlarmScheduler.scheduleForOccurrence`. Closes the Phase 4 carry-over.
- ✅ English + Hindi string resources for every Phase 5 UI surface — home screen
     headers, subscription row subtitles, preparation card copy, event detail labels,
     onboarding copy

**Tests:**
- ✅ `PreparationCardStateTest` (pure) — 10 tests covering every transition: hidden
     with no candidates, hidden beyond 36h, preparing at 12h / 36h edge, nearest
     candidate wins, observing between sunrise and Parana end, observing with null
     Parana, active Parana window, Parana takes priority over preparing, past Parana
     is hidden

## 17. Phase 5b — Location & city picker

- ✅ `CityCatalog` loads `assets/cities.json` with ~45 Indian + global cities (lat,
     lon, IANA tz, Hindi name)
- ✅ `LocationProvider` wraps `FusedLocationProviderClient` for a single-shot
     suspending `getCurrentLocation()` with permission guards
- ✅ `HomeCityPickerScreen` searchable list + "Use current location" GPS shortcut,
     reachable from Settings

## 18. Phase 7 — Settings surface

- ✅ `SettingsViewModel` reactive state (home city, sunrise offset, reliability)
- ✅ `SettingsScreen` with four cards: Home City, Reliability Check, Sunrise Time
     Offset slider, About
- ✅ `ReliabilityCheckSection` — green/red pills for battery optimization and
     exact-alarm permission, one-tap "Fix" actions
- ✅ `AboutScreen` with AGPL attribution and Swiss Ephemeris credit
- ✅ Nav graph wired — Settings → HomeCityPicker → back, Settings → About → back
- ✅ English + Hindi strings

## 19. Phase 6 — Calendar tab + sunset anchor

- ✅ Sunset calculator: `SunriseCalculator.sunsetUtc`,
     `PanchangCalculator.sunsetUtc`. `OccurrenceComputer` now anchors `EveningTithi`
     rules (Pradosh) to real local sunset — closes the Phase 2b carry-over.
- ✅ `CalendarViewModel` + `CalendarUiState` — per-day cells with tithi-at-sunrise
     and subscribed-event occurrences; `DayDetail` for the bottom sheet
- ✅ `CalendarScreen` month grid with `LazyVerticalGrid`, tithi labels, colored
     dots, today highlight, prev/next month nav
- ✅ `DayDetailSheet` with full panchang, Adhik/Kshaya indicators, bilingual labels
- ✅ English + Hindi Calendar strings

## 20. Phase 1b — **Swiss Ephemeris integrated**

- ✅ Thomas Mack Java port v2.00.00-01 vendored at `app/libs/swisseph-2.00.00-01.jar`
     (~586 KB)
- ✅ Ephemeris data files `sepl_18.se1`, `semo_18.se1`, `seas_18.se1` (~1.9 MB
     total) bundled at `app/src/main/assets/ephe/`, covering 1800–2399 CE
- ✅ `EphemerisAssetInstaller` copies `.se1` files to `filesDir/ephe/` on first
     launch; idempotent; transparent Moshier fallback on copy failure
- ✅ `SwissEphemerisEngine` is now a real implementation:
   - Injected with `@ApplicationContext` via Hilt
   - Installs the `.se1` assets on construction, picks `SEFLG_SWIEPH` (full
     precision, sub-milliarcsecond) or `SEFLG_MOSEPH` (~1″ fallback) automatically
   - Returns *tropical* longitudes so our Lahiri ayanamsha in `PanchangCalculator`
     still applies — no downstream behavior change
   - `close()` calls `swe_close()` for file-handle cleanup
- ✅ `EphemerisScope.close()` calls `SwissEphemerisEngine.close()`
- ✅ `EphemerisModule` flipped — production uses `SwissEphemerisEngine`; Meeus
     remains as a reference implementation for cross-check tests
- ✅ `SwissEphemerisEngineTest` (Robolectric) cross-checks Swiss Eph vs Meeus
     within 0.1° tolerance on sun/moon longitudes, dec range, RA range, moon lat
- ✅ AGPL compliance: `app/src/main/assets/licenses/swisseph_NOTICE.txt` +
     `agpl-v3.txt` bundled; About screen attributes the library
- ✅ ProGuard rules keep the full `swisseph.**` package plus top-level `Transit*`

**Precision upgrade:** Sun and Moon longitudes go from ~20 arcseconds (Meeus) to
sub-milliarcsecond (Swiss Eph with `.se1`) — roughly **20 000× tighter**, giving
tithi-boundary precision well under a second. Dashami-Viddha and Parana window
computations are now limited only by the sunrise calculator's ~10-second
uncertainty from horizon-refraction modeling.

## 21. Phase 8 — Travel detection (geofence)

- ✅ `GeofenceManager` registers a 100 km `GEOFENCE_TRANSITION_EXIT` geofence
     around `last_calc_lat/lon`. Android's internal location engine handles the
     monitoring. Permission-gated by `ACCESS_BACKGROUND_LOCATION`.
- ✅ `GeofenceBroadcastReceiver` now `@AndroidEntryPoint`; enqueues a one-shot
     `RefreshWorker` on transition.
- ✅ `RefreshWorker.runCurrentTierIfPossible()` — after HOME tier, resolves live
     GPS, computes a 30-day CURRENT tier at that position, re-schedules alarms,
     re-registers the geofence around the new position.
- ✅ `OccurrenceRepository` HIGH_PRECISION-first query precedence (unchanged since
     Phase 3) serves CURRENT rows over HOME rows on matching dates automatically.

## 22. Phase 9 — Release prep

- ✅ Top-level `LICENSE` (AGPL v3) with Swiss Ephemeris attribution
- ✅ `README.md` with build instructions and AGPL compliance summary
- ✅ `.gitignore`
- ✅ `.github/workflows/ci.yml` — four-stage pipeline (validate → build →
     deploy → source-archive publish) using base64-decoded keystore from GitHub
     Secrets and `github.run_number` for `versionCode`
- ✅ `app/build.gradle.kts` `signingConfigs { release { ... } }` reading env vars,
     with a guard so local debug builds work without a keystore

## 23. Post-initial-commit refinements

These landed after the repo went public at
[github.com/gausin3/NavPanchang](https://github.com/gausin3/NavPanchang) and are part
of `HEAD`.

### 23.1 Ritual audio bundled (closes the Phase 4 audio carry-over)

Four placeholder `.wav` files at `app/src/main/res/raw/`:

```
ritual_temple_bell.wav   — decaying sine + bell harmonics (~880 Hz)
ritual_sankh.wav         — vibrato horn drone (~220 Hz)
ritual_bell_toll.wav     — three-strike rhythmic pattern (~660 Hz, 0.6 s spacing)
ritual_om_mantra.wav     — sustained low harmonic stack (~131 Hz)
```

Each is ~86 KB (2 s, 22.05 kHz, 16-bit mono). Generated by
`scripts/generate_placeholder_ritual_sounds.py` — a pure-Python-stdlib synthesizer
that produces audibly distinct tones using elementary DSP. No external deps, no
license bookkeeping, regeneratable on any machine with Python 3.

`NotificationChannels.ensureChannels` now binds each ritual channel to its
`R.raw.*` resource via an `android.resource://<package>/<id>` URI instead of falling
back to the system default. A new private `RitualChannelSpec` data class table makes
adding a fifth audio variant a one-line change.

**Caveat:** these are placeholders, not field recordings. They sound synthetic
because they *are* synthetic — sine waves with envelopes. Before the first public
Play Store release, replace them with CC0 recordings from Freesound or own
field-recordings. Filenames must stay the same; no Kotlin change is needed.

### 23.2 Ayanamsha is now an explicit parameter, not ViewModel state

The previous mutable `var ayanamshaType: AyanamshaType` on `PanchangCalculator` was
a thread-safety smell — two callers using different ayanamsha types could race.
Now:

- `PanchangCalculator.computeAtInstant(epochMillisUtc, ayanamshaType)` etc. take
  the type explicitly. Sunrise/sunset helpers don't (they only depend on the Sun's
  tropical position, which is ayanamsha-independent).
- `OccurrenceComputer.Request` gains an `ayanamshaType` field; the computer threads
  it into every sub-call (`AdhikMaasDetector`, `AmavasyaFinder`, `SankrantiFinder`,
  `TithiEndFinder`, `VratLogic`).
- `MetadataRepository.ayanamshaType()` is the canonical resolve point — parses the
  persisted string with a LAHIRI default for forward compatibility. A
  `setAyanamsha(type)` mutator is wired up for the (future) Settings ayanamsha
  picker.
- All existing tests updated to pass `AyanamshaType.LAHIRI` explicitly.

**Convention:** hot loops should resolve the user's ayanamsha **once** at the top
of a worker / screen-load coroutine and thread it down. Don't re-fetch per call.

### 23.3 Toggle → instant refresh

Both `SubscriptionsViewModel.onToggleSubscription` and every mutator on
`EventDetailViewModel` now call `refreshScheduler.enqueueOneShot()` after writing
to `subscriptions`. Without this, a freshly-enabled event would have to wait up to
24 hours (until the next periodic refresh) before alarms got scheduled. With it,
the worker fires within seconds and the user sees their first alarm row populate
into `scheduled_alarms` immediately.

This intentionally *does* re-trigger the heavy 24-month HOME compute on every
toggle — the cost is ~4 seconds on a mid-range device, and the user experience
of "I toggled this and now it's working" is worth it. If aggregate cost becomes a
concern later, debouncing in the ViewModel is a five-line change.

### 23.4 Removed unused FOREGROUND_SERVICE permissions

Two unused permissions dropped from the manifest:
`android.permission.FOREGROUND_SERVICE` and
`android.permission.FOREGROUND_SERVICE_SPECIAL_USE`. `RefreshWorker` is a plain
`CoroutineWorker` running through WorkManager's normal scheduling — never a
foreground service. Declaring those permissions would have prompted Play Store
review questions we don't want to answer.

### 23.5 Documentation cross-references

All `See /Users/gauravps/.claude/plans/generic-juggling-sphinx.md §X` references in
source-file kdoc were rewritten to `See TECH_DESIGN.md §X` during the initial-commit
prep. Those are the references you see across `alarms/`, `panchang/`, `ephemeris/`,
`location/`, etc. The original plan file stays in `~/.claude/plans/` as a
maintainer-side artifact; the in-repo doc set is the canonical reference for
contributors.

---

**Status as of HEAD:** every originally-planned phase is implemented in code, plus
the post-initial-commit refinements above. Repo is live at
[github.com/gausin3/NavPanchang](https://github.com/gausin3/NavPanchang) under AGPL v3.
What remains is non-code:

1. **Replace placeholder audio** with real CC0 recordings before public release.
2. **Set GitHub Actions secrets** (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_STORE_CONFIG_JSON`) so `build` and `deploy`
   CI jobs can run.
3. **Patch the CI tag-trigger gap** — the `build` job's `if` currently requires
   `refs/heads/main`, so tag pushes skip it and `deploy` cascades. One-line fix
   already documented for when the user is ready.
4. **Play Store 12-tester / 14-day Closed Testing cycle** before promoting from
   Internal Testing to Production.

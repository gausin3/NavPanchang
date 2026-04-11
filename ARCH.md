# NavPanchang — Architecture

## Purpose

NavPanchang is an Android app that combines an **Indian panchang (Hindu calendar)** with a **subscription-based alarm system** for recurring religious observances — Ekadashi, Purnima, Amavasya, Pradosh, Sankashti Chaturthi, Shivratri, and more. Primary users are devotees who observe these vrats and don't want to hunt through a panchang every month.

## Guiding principles

1. **Subscription-first, not calendar-first.** The home screen is a list of event toggles. The calendar is a *secondary* verification tab.
2. **Offline-first, accurate.** Panchang is computed from Swiss Ephemeris locally. No API dependency, no rate limits, no "device has no internet" failure modes.
3. **Religious correctness is non-negotiable.** Dashami-Viddha, Adhik Maas, Kshaya tithi, Parana window, and location-dependent sunrise are all handled explicitly, not approximated.
4. **Alarms must fire.** Dual-alarm model, battery-optimization detection, boot receiver, geofence-based travel handling, and a dedicated "Reliability Check" in Settings.
5. **Respect the user.** AGPL v3 open source. Privacy-preserving (no data leaves the device). Languages: English and Hindi from day one.

## High-level architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         UI (Jetpack Compose)                       │
│  Subscriptions   Calendar   Settings   Onboarding   EventDetail    │
└───────────────────┬───────────────────┬────────────────────────────┘
                    │                   │
                    ▼                   ▼
          ┌─────────────────┐   ┌───────────────────┐
          │   ViewModels    │   │  Repositories     │
          │   (Hilt)        │◄──┤  (Room + Flow)    │
          └─────────────────┘   └─────────┬─────────┘
                                          │
                ┌─────────────────────────┼──────────────────────┐
                │                         │                      │
                ▼                         ▼                      ▼
      ┌──────────────────┐      ┌─────────────────┐   ┌───────────────────┐
      │  Panchang domain │      │  Room database  │   │  Alarms & workers │
      │  — Calculator    │      │  — event_defs   │   │  — AlarmScheduler │
      │  — VratLogic     │      │  — subscriptions│   │  — BootReceiver   │
      │  — Occurrence-   │      │  — occurrences  │   │  — LocaleChange-  │
      │    Computer      │      │  — scheduled    │   │    Receiver       │
      │  — Adhik Maas    │      │  — calc_meta    │   │  — RefreshWorker  │
      └─────────┬────────┘      └─────────────────┘   │  — LateSubGate    │
                │                                      └─────────┬─────────┘
                ▼                                                │
      ┌──────────────────┐                                       ▼
      │   Ephemeris      │                              ┌──────────────────┐
      │   — SwissEph JAR │                              │  Location        │
      │   — EphScope     │                              │  — FusedProvider │
      │   — se1 files    │                              │  — Geofencing    │
      └──────────────────┘                              │  — CityCatalog   │
                                                        └──────────────────┘
```

## Module / package layout

Single Gradle module `:app` with packages under `com.navpanchang`:

| Package | Responsibility |
|---|---|
| `ephemeris` | Swiss Ephemeris Java port + Kotlin `EphemerisEngine` / `EphemerisScope` wrapper |
| `panchang` | `PanchangCalculator`, `TithiEndFinder`, `VratLogic` (Dashami-Viddha + Parana), `Events`, `EventRule`, `OccurrenceComputer` |
| `data.db` | Room database, DAOs, and entities (see Schema section) |
| `data.db.entities` | `EventDefinitionEntity`, `SubscriptionEntity`, `OccurrenceEntity`, `ScheduledAlarmEntity`, `CalcMetadataEntity` |
| `data.repo` | `SubscriptionRepository`, `OccurrenceRepository` (HIGH_PRECISION precedence), `LocationRepository`, `MetadataRepository` |
| `data.seed` | `EventCatalogSyncer` — MDM-style versioned upsert from `assets/events.json` |
| `data.prefs` | DataStore-backed `UserPrefs` |
| `alarms` | `AlarmScheduler`, `AlarmReceiver`, `BootReceiver`, `LocaleChangeReceiver`, `RefreshWorker`, `LateSubscriptionGate`, `BatteryOptimizationCheck` |
| `location` | `LocationProvider`, `GeofenceManager`, `GeofenceBroadcastReceiver`, `CityCatalog` |
| `ui` | Nav graph, theme, components, per-screen packages |
| `ui.subscriptions` | `SubscriptionsScreen`, `TodayStatusCard`, `PreparationCard`, `EventDetailScreen` |
| `ui.calendar` | `CalendarScreen`, `DayDetailScreen` |
| `ui.settings` | `SettingsScreen`, `ReliabilityCheckSection`, `HomeCityPicker` |
| `ui.onboarding` | `OnboardingScreen` |
| `util` | `AstroTimeUtils`, `LanguageSwitchInterceptor` |
| `util.debug` | `NotificationTestingTool` (debug builds only) |
| `di` | Hilt `AppModule` |

## Two-tier lookahead

The core architectural insight: **don't recompute 24 months of occurrences every time the user travels to Dubai for the weekend.**

| Tier | Window | Purpose | Trigger |
|---|---|---|---|
| **Tier 1 — HOME** | 24 months | Calendar UI, default alarms | Home city change, monthly top-up |
| **Tier 2 — CURRENT** | 30–60 days | High-precision alarms when traveling | OS-level geofence (100 km radius around `last_calc_lat/lon`) |

Queries use `OccurrenceDao.getNextOccurrence()` which prefers `isHighPrecision = true` rows and falls back to HOME rows. A single `AlarmScheduler` path serves both tiers.

## Dual-alarm model

For every occurrence of a subscribed event, schedule **two** (or **three** for Ekadashi) alarms:

| Kind | Anchor | Channel |
|---|---|---|
| `PLANNER` | Wall-clock 8:00 PM *day before* | `channel_event_reminders` (low) |
| `OBSERVER` | Local sunrise (GPS-based) *day of* | `channel_ritual_alarms` (high) |
| `PARANA` (Ekadashi only) | Sunrise on Dvadashi (day after) | `channel_ritual_alarms` (high) |

The Planner is wall-clock-anchored so "8 PM to prepare" makes sense wherever you are. Observer and Parana are astronomy-anchored so they travel with the user's GPS.

## Data governance (MDM-style)

The `events.json` bundle in assets carries a `catalogVersion`. On every cold start, `EventCatalogSyncer` compares it to `calc_metadata.event_catalog_version` and upserts event definitions when the bundled version is newer. **The `subscriptions` table is never touched** — user preferences survive every upgrade. Removed events are marked `deprecated = 1`, never deleted. A migration banner in the UI lets users move from a deprecated rule to its replacement.

## Key flows

### Cold start

1. `NavPanchangApp.onCreate()` → launch `EventCatalogSyncer.syncIfNeeded()` on IO dispatcher.
2. `MainActivity` sets the Compose content → `NavPanchangNavGraph` starts on the Subscriptions tab.
3. Subscriptions ViewModel collects `subscriptionDao.observeAll()` + `occurrenceDao.getNextOccurrence()` for each enabled event.
4. If no HOME lookahead exists, onboarding is triggered.

### Travel detection

1. User has granted `ACCESS_BACKGROUND_LOCATION`.
2. `GeofenceManager` registered a 100 km-radius geofence around `calc_metadata.last_calc_lat/lon`.
3. User crosses the boundary → Android fires `GeofenceBroadcastReceiver`.
4. Receiver enqueues a one-shot `RefreshWorker` → recomputes Tier 2 → re-arms Observer/Parana alarms → registers a new geofence around the new position.

### Alarm firing

1. `AlarmManager` fires the `PendingIntent` → `AlarmReceiver` runs.
2. Receiver validates the occurrence still exists and the subscription is still enabled.
3. Builds a notification on the channel associated with the `AlarmKind`.
4. Adds actions: "Remind me at Sunrise" (Planner), "Parana reminder" (Observer for Ekadashi).
5. Posts via `NotificationManagerCompat`.

## Testing strategy

- **Unit:** `PanchangCalculatorTest`, `VratLogicTest`, `AdhikMaasTest`, `TithiEndFinderTest`, `OccurrenceComputerTest`, `EventCatalogSyncerTest` — all cross-reference drikpanchang.com / mypanchang.com golden data.
- **Instrumented:** `AlarmSchedulerTest` (real AlarmManager), `OccurrenceRepositoryTest` (HOME/CURRENT fallback).
- **Manual "mom test":** fresh install → onboarding in Hindi → subscribe to Ekadashi → verify times match a printed Lucknow panchang → mock GPS to Dubai → verify the Location Badge appears.

## Non-goals (explicitly out of scope for v1)

- Regional scripts beyond Hindi (Tamil, Telugu, Kannada, Bengali, Marathi, Gujarati) — Phase 10+.
- Surya Siddhanta / Vakya panchang traditions — reserved by `calc_metadata.calculation_tradition` but not exposed.
- Annual Hindu festivals (Diwali, Holi, Navratri, etc.) — rule system already generalizes; added in a later release.
- Home-screen widget.
- Monetization (ads, IAP).

See [`SPEC.md`](SPEC.md) for functional specs, [`TECH_DESIGN.md`](TECH_DESIGN.md) for implementation detail, and [`FEATURES.md`](FEATURES.md) for the feature catalog.

# Changelog

All notable changes to NavPanchang. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/).

This file is the source of truth for the Play Store "What's new" field and the
GitHub Release body. When you ship a new version, write the entry here first,
then paste a trimmed-to-500-character variant into the Play Console release
notes (per-locale limit) and into the GitHub Release page.

## [Unreleased]

(Nothing yet — next release will land here.)

## [0.2.0] — 2026-06-24

First Google Play Store release (Internal Testing track). Same offline panchang
and alarm engine as v0.1.0, plus Play-policy hardening and reliability fixes
surfaced during the submission audit.

### Added

- First Play Store release on the Internal Testing track. The GitHub sideload
  track stays at v0.1.x for AGPL distribution; the Play track is the long-term
  home for v0.2.x onward.
- Privacy policy hosted on GitHub Pages
  ([`docs/privacy-policy.md`](docs/privacy-policy.md) →
  <https://gausin3.github.io/NavPanchang/privacy-policy.html>) and linked from
  the README and the Play Console listing. Includes a dedicated "Data deletion"
  section with a stable anchor (`#data-deletion`) so the Play Data Safety form
  can deep-link.
- Boot/unlock/upgrade now re-arms OS-level `AlarmManager` exact alarms via
  `AlarmScheduler.rearmAllPending()`. Closes a reliability gap where reboots
  could silently drop alarms until the next WorkManager-deferred refresh. The
  receiver also handles `ACTION_USER_UNLOCKED` so direct-boot devices catch up
  even when an OEM delays `BOOT_COMPLETED` until well after unlock.
- Lint baseline (`app/lint-baseline.xml`) so CI fails only on **new** warnings.
  Existing warnings are snapshotted to drain over time.

### Changed

- Target SDK raised to 35 (Android 15). Compile SDK matched. App-side: Compose
  predictive-back already handled; edge-to-edge already on; no native libraries,
  so the 16 KB page-size requirement is N/A.
- Manifest hardened for Play Store policy compliance: explicit
  `android:exported` on every receiver, removed unused permissions, USE_EXACT_ALARM
  justification staged in `docs/RELEASE.md` for the Play declaration form.

### Removed

- `ACCESS_BACKGROUND_LOCATION` permission. The geofence-triggered Tier 2 travel
  refresh now runs on next app open instead of from a fully-killed Doze state.
  Play treats `ACCESS_BACKGROUND_LOCATION` as its highest-risk permission
  (declaration form + demo video + frequent rejections) and we were paying that
  cost for zero observable user benefit.

### Fixed

- Removed the README and release-body claim that an in-app updater exists in
  v0.1.x — it does not. The opt-in, default-off update checker is queued for a
  follow-up release. While the app is on Play, Play handles updates anyway.

## [0.1.0] — 2026-05-16

First public release on GitHub Releases. Signed sideloaded APK; AGPL source
attached. Offline Hindu panchang with sub-milliarcsecond Swiss Ephemeris
calculations and an alarm engine for recurring vrats.

### Added

- Subscribe-and-forget alarms for Ekadashi (Shukla / Krishna), Purnima,
  Amavasya, Pradosh (Shukla / Krishna), Sankashti Chaturthi, Vinayaka
  Chaturthi, Masik Shivratri, and Mahashivratri. Every subscribed event
  fires a Planner (evening before), Observer (sunrise), and Parana
  (fast-breaking window, where applicable) alarm.
- Per-event ritual sound picker with inline preview: Temple bell, Conch
  (sankh), Bell toll, Om mantra, Singing bowl.
- Bilingual UI in English + Hindi (Devanagari) across every screen, with
  a Native-numeral toggle (Devanagari / Tamil / Gujarati).
- Calendar tab with month grid, tithi shorthand, paksha colour coding, and
  a marker on every subscribed day. Tap any cell for full panchang detail
  including Parana times.
- One-tap "Pick a city" CTA on Home and Calendar when no home city is set,
  replacing a blank calendar with no guidance.
- Settings → Contact us card with deep links to WhatsApp and Email so users
  can reach the maintainer directly.
- Settings → Reliability check section verifying POST_NOTIFICATIONS,
  battery optimization whitelist, and SCHEDULE_EXACT_ALARM, each with a
  one-tap Fix button when a row is red.
- Runtime POST_NOTIFICATIONS permission request on Android 13+ so vrat
  alarms actually surface notifications on fresh installs.
- Travel awareness: when the user moves > 100 km from their home city, the
  app recomputes the next 30 days of panchang for the new location on next
  open. Sunrise / sunset / tithi times follow the user.
- Bundled procedural ritual notification sounds (real recordings ship in a
  follow-up release).
- CI builds both signed APK and signed AAB on tag push and attaches the
  APK + an AGPL source archive to the GitHub Release.

### Changed

- Visual design refresh to the "manuscript-devotional" palette
  (sandal-paper / saffron / sindoor / indigo eve / gold leaf) per
  [`DESIGN.md`](DESIGN.md). Settings now offers a language picker
  (English / Hindi / Marathi / Tamil / Gujarati) and a numerals picker
  (Latin / Devanagari / Tamil / Gujarati).
- Replaced the placeholder launcher icon with a sun-mandala + crescent
  design matching the new palette.
- Day Detail sheet shows friendly event names (not raw event IDs) and
  Parana times for Ekadashi-class days; the Home "Preparing for tomorrow"
  card is now tappable to jump straight to the event.

### Fixed

- **Sunrise computation ignored device timezone — every Indian date was
  off by one day.** `SunriseCalculator.riseOrSetUtc` accepted a `zone`
  parameter but anchored Meeus Ch.15 at UT-midnight, not local-midnight.
  For India (UTC+5:30) the algorithm returned the next local day's
  sunrise; every tithi-anchored alarm fired one day early. Replaced with
  a deterministic ±1 UT-anchor selection that picks the instant whose
  local date in `zone` equals the requested date. Verified against Drik
  Panchang (15/15 Ekadashi dates exact for New Delhi).
- **Non-vrat events on Kshaya tithi months were silently dropped.**
  Purnima, Amavasya, Sankashti Chaturthi, Vinayaka Chaturthi, and Masik
  Shivratri now surface on Kshaya months with an on-screen
  "tithi ends before sunrise" marker instead of vanishing.
  Margashirsha Purnima 2026 (Dec 23) verified against published
  references. Sunset-anchored Pradosh on Kshaya Trayodashi is a known
  limitation still tracked for a follow-up release.
- Serialized `SwissEph` access — resolves an intermittent
  "file coefficient" crash when multiple panchang computations raced
  through the Java port at app launch.
- `DateTimeFormatter` now honours the Native-numeral setting (Devanagari,
  Tamil, Gujarati). Previously dates rendered in Latin even after the
  user picked a native script.
- Mahashivratri seed corrected: tithi-in-Magha (Amanta canonical mapping)
  instead of Phalguna; Amanta / Purnimanta lunar-month plumbing wired
  through so Purnimanta-region users see "Phalguna" labels via the
  display-time translation.

### Security

- Released under [GNU Affero General Public License v3](LICENSE).
- Zero network calls during normal use. No analytics, no crash-reporting
  SDKs, no advertising. Privacy policy details every permission and
  what each is used for, on-device only.

[Unreleased]: https://github.com/gausin3/NavPanchang/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/gausin3/NavPanchang/releases/tag/v0.2.0
[0.1.0]: https://github.com/gausin3/NavPanchang/releases/tag/v0.1.0

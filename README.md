# NavPanchang

> A subscription-first Hindu panchang and alarm app for Android. Built for people
> who observe recurring vrats — Ekadashi, Purnima, Amavasya, Pradosh, Sankashti
> Chaturthi, Shivratri — and don't want to hunt through a panchang to find the
> next date every month.

**License:** [AGPL v3](LICENSE) • **Stack:** Kotlin + Jetpack Compose •
**Min SDK:** 26 (Android 8) • **Target SDK:** 34

---

## What it does

- **Subscribe once, never miss a vrat.** Toggle "Ekadashi" on and the app schedules
  every Planner (evening-before) + Observer (sunrise) + Parana (fast-breaking
  window) alarm for the next 24 months.
- **Runs fully offline.** Panchang is computed locally using the Swiss Ephemeris
  library with sub-milliarcsecond accuracy from 1800–2399 CE.
- **Respects religious-calendar nuances.** Dashami-Viddha shift for Ekadashi, Adhik
  Maas detection via Solar Sankranti counting, Kshaya tithi safe-fallback, and the
  Harivasara quarter rule for Parana window computation.
- **Travels with you.** When the user moves > 100 km from their home city, a
  geofence-triggered background refresh recomputes the next 30 days at the new
  location so sunrise times match wherever they are.
- **Privacy-preserving.** Nothing leaves the device. No analytics, no crash-reporting
  SDKs, no cloud sync, no ads. The app makes **zero network calls** today — not even
  an update check. A future build will add an *optional, off-by-default* "check for
  updates" control; until you explicitly enable or tap it, the app stays fully offline.
- **Bilingual.** English and Hindi (Devanagari) strings ship in the first release.

See [ARCH.md](ARCH.md), [SPEC.md](SPEC.md), [TECH_DESIGN.md](TECH_DESIGN.md),
[FEATURES.md](FEATURES.md), and [USER_GUIDE.md](USER_GUIDE.md) for the full picture.

---

## Known limitations

- **Pradosh + Kshaya tithi.** Pradosh (Shukla/Krishna) is observed at *pradosh-kaal*
  (sunset). In the rare month where the Trayodashi tithi is *Kshaya* — it begins and
  ends entirely between two sunrises, touching neither (a few times a year) — Pradosh
  for that month is not yet shown. Every other event (Ekadashi, Purnima, Amavasya,
  Sankashti, Vinayaka Chaturthi, Masik Shivratri) correctly surfaces Kshaya months
  with an on-screen "tithi ends before sunrise" note instead of silently skipping
  them. The sunset-anchored Pradosh Kshaya case needs a separate sunset-based
  resolution and is tracked for a follow-up release. As always, cross-check borderline
  dates against your trusted local panchang.
- **No automatic updates yet.** This is a sideloaded app with no in-app updater in
  v0.1.0. Watch the [Releases page](https://github.com/gausin3/NavPanchang/releases)
  for new versions; to update, download the newer APK and tap to install over the
  existing app (same signing key, so subscriptions and alarms are preserved). An
  optional, off-by-default in-app update check is planned for a follow-up release;
  once the app is on the Play Store, Play handles updates automatically.

---

## Building from source

### Prerequisites

- Android Studio Koala (2024.1+) or later
- JDK 17
- Android Gradle Plugin 8.5+
- The Swiss Ephemeris JAR at `app/libs/swisseph-2.00.00-01.jar` (already vendored)
- The Swiss Ephemeris data files at `app/src/main/assets/ephe/*.se1` (already vendored)

### Debug build

```bash
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Release build

```bash
./gradlew :app:bundleRelease
```

The signed AAB lands at `app/build/outputs/bundle/release/app-release.aab`.

The release build expects the following environment variables (set by CI, or
via `~/.gradle/gradle.properties` locally):

- `KEYSTORE_FILE` — path to the upload keystore (`.jks`)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `VERSION_CODE` — integer build number (CI uses `github.run_number`)

### Running tests

```bash
./gradlew :app:testDebugUnitTest
```

Test suites — ~120 unit tests across the ephemeris, panchang, alarm, data, and UI
layers. Robolectric is used for Room-integration tests and for the Swiss
Ephemeris engine cross-check.

---

## Licensing & AGPL compliance

NavPanchang is AGPL v3 because it links against the Swiss Ephemeris library,
which is itself AGPL (with an optional commercial license from Astrodienst AG).
NavPanchang uses the AGPL option.

**Your obligations when modifying or redistributing NavPanchang:**

1. Make the full source code of your modified version available to everyone who
   uses it — AGPL §13 requires this even for network-accessible versions.
2. Keep the AGPL license notice in place.
3. Keep the Swiss Ephemeris attribution in `app/src/main/assets/licenses/` and
   in the About screen.

See [LICENSE](LICENSE) for the full terms and
[`app/src/main/assets/licenses/swisseph_NOTICE.txt`](app/src/main/assets/licenses/swisseph_NOTICE.txt)
for upstream attribution.

---

## Audio assets

The four ritual notification channels ship with **procedural placeholder tones** at:

```
app/src/main/res/raw/ritual_temple_bell.wav
app/src/main/res/raw/ritual_sankh.wav
app/src/main/res/raw/ritual_bell_toll.wav
app/src/main/res/raw/ritual_om_mantra.wav
```

Each is ~86 KB (2 s, 22 kHz, 16-bit mono). They are **audibly distinct** — a decaying
bell, a vibrato horn, a three-strike bell toll, a low drone — so the four ritual
channels can be distinguished during testing, but they are clearly synthetic and not
suitable for a polished release.

**To regenerate the placeholders:**

```bash
python3 scripts/generate_placeholder_ritual_sounds.py
```

**To replace them with real recordings:** drop your own `ritual_*.wav` (or `.ogg` /
`.mp3` / `.flac`) over the placeholder with the same filename. No Kotlin changes are
required — `NotificationChannels.ensureChannels()` looks them up by `R.raw.*` id at
build time. Recommended sources:

- [Freesound.org](https://freesound.org/) — filter by License: "Creative Commons 0"
  for zero attribution hassle; CC-BY requires adding credit to the About screen.
- Your own recording — a phone voice-memo of an actual temple bell or conch will
  comfortably outperform the placeholder for authenticity.

Target specs: ≤ 4 seconds, ≤ 150 KB, mono or stereo, any Android-decodable format.

---

## Key references

- **Memory & conventions:** [`MEMORY.md`](MEMORY.md)
- **Religious correctness notes:** [`TECH_DESIGN.md`](TECH_DESIGN.md) §Panchang calculation + §Vrat logic
- **Phase-by-phase status:** [`TECH_DESIGN.md`](TECH_DESIGN.md) §§11–23 (§23 "Post-initial-commit refinements" covers audio bundling, the ayanamsha threading refactor, and the Toggle→instant-refresh ViewModel wiring)

---

## Contributing

Bug reports and PRs welcome. Religious-tradition contributions (alternate Parana
rules, regional language strings) are especially welcome — please open an issue
describing the tradition and citing a reference before sending code.

---

**🙏 May your vrats be undisturbed and your Parana windows always fall within time.**

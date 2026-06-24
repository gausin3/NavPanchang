# Release runbook

How to cut a release of NavPanchang. Covers the keystore, GitHub Actions secrets, the tag-driven release flow, and what changes when (eventually) the app moves to Play Store.

This document is the source of truth for "how do we ship a new version." When something here changes, update it in the same PR.

---

## Why the keystore matters more than anything else

NavPanchang is distributed outside Play Store today. Every APK we ship is signed with a release keystore. Android refuses to install an update over an existing app **unless the new APK is signed with the same key as the old one**. That means:

- **Lose the keystore → existing users cannot update.** Their only path forward is uninstall (which deletes their subscriptions, alarms, and home-city setting) and reinstall the new APK fresh. There is no recovery from this. Google can't help. Anthropic can't help. Nobody can help.
- **Compromise the keystore → anyone can ship malicious "updates" that install over real users' apps.**

Treat the keystore like a domain registrar password. Three independent backups, minimum. Verified annually.

---

## One-time setup

### 1. Generate the keystore

```bash
./scripts/generate_keystore.sh
```

The script wraps `keytool` with sensible defaults (4096-bit RSA, 100-year validity, PKCS12 format). It writes to `~/keystores/navpanchang-release.jks` by default. It refuses to overwrite an existing keystore.

You'll be prompted for:
- Distinguishing-name fields (CN / O / C). Defaults are fine.
- One password — PKCS12 keystores use the same password for both the store and the key inside it (this is the modern format Play Store and Android prefer). `keytool` asks you to enter it twice for confirmation. Use a password manager to generate it.

After it finishes, the script prints the SHA-256 fingerprint and writes a base64 backup file. **Save the password in your password manager immediately.**

### 2. Back up the keystore (three places, minimum)

| Location | What to put there |
|---|---|
| Encrypted local folder (FileVault / LUKS / VeraCrypt) | The `.jks` file itself. |
| Password-manager attachment (1Password Documents / Bitwarden Send) | A copy of the `.jks` file plus the two passwords. |
| Offline (printed base64 dump or USB stick in a drawer) | A copy of the `.jks.base64.txt` file. Yes, really print it. |

Once a year, decode the printed/cloud backup and confirm the SHA-256 fingerprint matches the one printed when you generated the keystore. Catches silent data rot before you need the backup.

### 3. Set GitHub Actions secrets

Go to `Settings → Secrets and variables → Actions → New repository secret` on the GitHub repo. Add five secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Contents of `~/keystores/navpanchang-release.jks.base64.txt` |
| `KEYSTORE_PASSWORD` | The password you chose during keystore generation |
| `KEY_ALIAS` | `navpanchang` (matches the alias used by the generator script) |
| `KEY_PASSWORD` | **Same value as `KEYSTORE_PASSWORD`** — PKCS12 unifies the two passwords. The Gradle release signing config still expects two named secrets, so we set both to the same value. |
| `PLAY_STORE_CONFIG_JSON` | Empty / `null` for now. Add real value when you have a Play Console service account (see "Going to Play Store" below). |

Without these secrets, the CI build job that produces a signed release APK will fail.

### 4. Patch the CI tag-trigger gap

Per the release-checklist note in [`MEMORY.md`](../MEMORY.md), the existing `.github/workflows/ci.yml` `build` job has an `if` condition that only runs on `refs/heads/main`. Tag pushes (`refs/tags/v*`) skip it, which means `deploy` cascades with no signed artifact. Fix the condition to:

```yaml
if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v')
```

One-line change. Do it before cutting `v1.0.0`.

---

## Cutting a release

Per-release checklist before any of this:

- [ ] All tests pass: `./gradlew :app:testDebugUnitTest`
- [ ] Manual mom-test on a real device.
- [ ] Reliability check green on Xiaomi + Samsung.
- [ ] Privacy policy live and reachable.
- [ ] `versionCode` and `versionName` bumped in `app/build.gradle.kts`.
- [ ] [`MEMORY.md`](../MEMORY.md) and [`TECH_DESIGN.md`](../TECH_DESIGN.md) updated for any architectural changes.

Then:

```bash
# 1. Confirm working tree is clean and on main.
git status
git pull

# 2. Tag.
git tag -a v1.0.0 -m "v1.0.0 — initial public release"
git push origin v1.0.0

# 3. CI runs. The `build` job:
#      - Decodes KEYSTORE_BASE64 to a tempfile.
#      - Builds release APK + AAB, signed with the keystore.
#      - Attaches APK to the GitHub Release for that tag.
#
# 4. Monitor: GitHub → Actions tab. Failure modes are usually missing/wrong
#    secrets or a flaky test.

# 5. Verify the published Release has an APK asset attached.
#    Download it. Install on a real device. Confirm it opens and the version
#    string matches.
```

Once the asset is up, it is visible on the [Releases page](https://github.com/gausin3/NavPanchang/releases). **As of v0.1.0 there is NO in-app update check** — users update manually by downloading the newer APK and installing over the existing app (same signing key → subscriptions and alarms preserved). The updater below is a planned v0.1.1 feature, not yet built.

---

## Update flow for sideloaded users (PLANNED — v0.1.1, NOT yet implemented)

> Status: **not built.** v0.1.0 ships with zero networking and no updater.
> This is the spec for v0.1.1. Do not describe it as existing in release
> notes, the README, or the Play data-safety form until it actually ships.

Design (locked):

- **Opt-in, default OFF.** A Settings toggle "Automatic update checks"
  (default **off**) plus an always-available "Check for updates" button on
  the About screen. Default-off keeps "runs fully offline" literally true
  out of the box — the only network call ever made is one the user
  explicitly enabled or tapped. This is opt-**in**, not opt-out; the earlier
  "opt-out via Settings" wording here was wrong and contradicted the
  no-phone-home stance in [`MEMORY.md`](../MEMORY.md).
- **Implementation:** `UpdateChecker` using `java.net.HttpURLConnection`
  (JDK built-in — do **not** add OkHttp/Retrofit; the app has zero
  networking dependencies today, keep it that way for one GET). Single
  unauthenticated GET to
  `https://api.github.com/repos/gausin3/NavPanchang/releases/latest`,
  parse `tag_name`, strip `v`, semver-compare vs `BuildConfig.VERSION_NAME`.
- **Trigger:** manual button always works; auto-check only if the toggle is
  on, throttled ≤1×/24h, on app open. No background `WorkManager` job —
  network only while the user is actively in the app.
- **Found newer:** dialog → "Download" opens the APK asset URL in the system
  browser → user taps the file → Android install prompt. No in-app
  `PackageInstaller` (would need `REQUEST_INSTALL_PACKAGES` — avoid).
- **Any failure** (offline / API down / rate-limited) → silent no-op. Never
  blocks or degrades core offline function.
- **When it ships:** update the README privacy bullet and the Play
  data-safety form to disclose the optional, off-by-default, user-enabled
  call to `api.github.com`.

### The Play Store guard (build into the updater from day one)

The updater must detect Play installs (`getInstallerPackageName() ==
"com.android.vending"`) and **stay completely silent** there — Play updates
the app natively, and Play policy forbids a Play-distributed app from
self-updating by downloading an APK. Not just UX: shipping the GitHub
self-update path in a Play build is a suspension risk.

**Distribution strategy (decided):** we are **not** matching signing keys
across channels. Once the app is live on Play Store, ship a final GitHub
build whose update check is a **one-way bridge** — it opens the Play listing
(`market://details?id=com.navpanchang`, https fallback) and tells the user
to install from Play. Signatures differ, so this is a deliberate one-time
manual migration for the (near-zero) GitHub cohort: fresh Play install, old
sideload build removed manually, subscriptions reconfigured. Accepted
tradeoff — avoids contorting the keystore setup for a handful of users.

---

## Going to Play Store (later)

When (if) you eventually get a Play Console developer account:

### What stays the same

- The keystore. Continue signing with the same key.
- All AGPL obligations.
- The same source code.
- The GitHub release flow, for sideloaded users.

### What changes

| Change | Notes |
|---|---|
| **Play App Signing decision** | On first AAB upload, Google offers to take over your signing key. **Recommend: opt in.** Pros: if your local key is lost or compromised, Google can rotate it. Your local key becomes the "upload key" — used to sign builds before they're sent to Google, where they're re-signed with the real key. Sideloaded APKs and Play Store APKs end up signed with the same Google-managed key, so they're inter-update-compatible. **You cannot opt out cleanly once enrolled** — make this decision deliberately. |
| **Data-safety form** | Declare what data the app collects and transmits. Right now: nothing user-identifying, only the GitHub Releases API call. Easy form. **Do not regress this** by adding analytics later — every new collected field means form re-submission. |
| **Closed Testing requirement** | Play Store rule for new dev accounts since 2024: 12 testers must install and use for 14 consecutive days before you can promote to Production. Plan for this delay in your launch timeline. |
| **`USE_EXACT_ALARM` justification** | Required written justification for the alarm permission. Suggested copy: "calendar/alarm-clock application used to schedule religious-observance reminders (Hindu vrats), where the timing is anchored to astronomical events (sunrise) at the user's location and must fire at the exact computed time." |
| **APK + AAB** | Play Store requires AAB. Sideloaded users still need APKs. Both ship from the same build (`./gradlew :app:bundleRelease` + `:app:assembleRelease`). |
| **Privacy policy URL** | Mandatory for Play Store. GitHub Pages hosting is fine; one page with "no data leaves the device, except a daily call to api.github.com to check for app updates (opt-out in Settings)." |
| **Package name** | Confirm release builds use `com.navpanchang` (no `.debug` suffix). Already configured in `app/build.gradle.kts`. |
| **`PLAY_STORE_CONFIG_JSON` secret** | Generate a Play Console service account, download its JSON key, store as a GitHub Actions secret. Enables the `deploy` CI job to push AAB to Play Store internal track automatically. |
| **In-app updater auto-disables on Play installs** | The `getInstallerPackageName()` guard kicks in. No code change needed at this point; the guard was built-in from day one. |

### Closed Testing → Production sequence

1. Upload first AAB to Play Console as Internal Testing.
2. Decide on Play App Signing. (Recommend yes.)
3. Submit data-safety form, content rating questionnaire, privacy policy URL.
4. Add 12 testers to a Closed Testing track. Send them the opt-in link.
5. Wait 14 days while they install + use.
6. Promote to Production via the Play Console.

Total elapsed time from "first upload" to "available on Play Store": ~2-3 weeks if testers are responsive.

---

## Play Console submission content (paste-ready)

The Play Console will demand a handful of fields and declaration forms before
your first AAB can graduate from Internal Testing → Closed Testing →
Production. Everything below is the exact text you can paste, plus the
expected answer to each questionnaire prompt. Keep this section in sync with
the live Play Console state — when you re-edit something there, mirror it
back here so a future re-submission is reproducible.

### Privacy policy URL

`https://gausin3.github.io/NavPanchang/privacy-policy.html`

(Or whatever URL GitHub Pages produces for `docs/privacy-policy.md`. The
canonical source is [`docs/privacy-policy.md`](privacy-policy.md). Update the
URL here to whatever the Pages site actually returns once enabled.)

### Store listing — Short description (≤ 80 chars)

```
Vrat alarms with sunrise-precise on-device panchang. Free. Offline. AGPL.
```

(73 chars, room to tighten further if needed.)

### Store listing — Full description (≤ 4000 chars)

```
NavPanchang is a free, open-source Hindu panchang for Android — sunrise-precise vrat alarms, computed entirely on your device.

WHAT IT DOES
• Subscribe to Ekadashi (Shukla / Krishna), Purnima, Amavasya, Pradosh, Sankashti Chaturthi, Vinayaka Chaturthi, Masik Shivratri, and Mahashivratri.
• Get an evening-before Planner reminder, a sunrise Observer alarm, and a Parana (fast-breaking) window alarm — for the next 24 months, automatically.
• Pick a ritual sound per event: Temple bell, Conch (sankh), Bell toll, Om mantra, or Singing bowl.
• See a clean month calendar with tithi shorthand, paksha colour, and a marker on every subscribed day.
• Tap any day for full panchang: tithi, nakshatra, paksha, lunar month, sunrise, sunset, plus Parana times for vrat days.

WHY IT'S DIFFERENT
• 100% offline. The Swiss Ephemeris is bundled — your device computes everything, accurate to sub-milliarcsecond from 1800 to 2399.
• Religious-calendar nuance respected. Dashami-Viddha shift for Ekadashi, Adhik Maas detection via Solar Sankranti counting, Kshaya tithi safe-fallback, Harivasara quarter rule for Parana windows.
• Bilingual. English + Hindi (Devanagari) across every screen; Devanagari numerals optional.
• Travels with you. Move more than 100 km from your home city and the app recomputes sunrise times at your new location on next open.
• Free as in freedom. Released under the GNU Affero General Public License v3. The full source — including every dependency — is on GitHub.

WHAT IT DOES NOT DO
• No data collection. No analytics. No third-party SDKs. No advertising. No login. No cloud sync. There is no server.
• Zero network calls during normal use. An optional, off-by-default "Check for updates" control will be added in a future release; until you turn it on, the app makes no network requests of any kind.

ABOUT THE NAME
"Navpanchang" — a new panchang, computed for you, where you are, for the next 24 months.

OPEN SOURCE
Source code: https://github.com/gausin3/NavPanchang
Privacy policy: https://gausin3.github.io/NavPanchang/privacy-policy.html
Contact: gaurav@navtakniq.com
```

### App category

**Lifestyle** — closer to peer panchang / religious-observance apps than to
"Tools," and avoids the Tools content-rating heuristic noise.

### Content rating (IARC questionnaire)

Every answer is "no" — this is the most boring questionnaire in the
Play Console.

- Violence: **No**
- Sexual content: **No**
- Profanity: **No**
- Controlled substances (alcohol / drugs / tobacco / gambling): **No**
- User-generated content: **No** (the app has no UGC; no in-app messages,
  reviews, or sharing).
- Loot boxes / gambling mechanics: **No**
- Location sharing or user-to-user communication: **No**
- Crude humor / scary imagery: **No**

Expected rating: **Everyone** (PEGI 3 equivalent).

### Data Safety form

Open Play Console → "App content" → "Data safety" → fill in:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | _(skipped — no data collected)_ |
| Do you provide a way for users to request that their data be deleted? | **Yes — by uninstalling the app.** All app data is on-device only; there is no server data to delete. |
| Has your data collection and security practices been independently validated against a global security standard? | **No** (we're not, and we don't claim to be) |

After this question set, Play presents the per-category data type form
(Location, Personal info, etc.). For every category the answer is the same:
"Data type collected: **No**." The form's review will reflect the
single-line summary "This app does not collect or share any user data."

If Play surfaces follow-up location prompts because the manifest declares
`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`, answer:

- Used for: **App functionality** (only).
- Shared: **No.**
- Collected: **No.** (Location stays on the device; it's used as an input to
  the on-device sunrise/sunset calculation, not stored on a server or sent
  anywhere.)
- Optional or required: **Required** (the app needs a location to be useful;
  you can pick a city manually if you deny precise GPS).

### `USE_EXACT_ALARM` declaration (Play Console "App content" → "Alarms & reminders")

```
NavPanchang is a religious-observance alarm app. Its core function is firing
vrat (Hindu fasting) alarms at astronomically computed times that have no
substitute:

  - The Planner alarm fires at a user-chosen evening time on the day before
    a vrat, so the user can prepare (procure ingredients, plan their day).
  - The Observer alarm fires at LOCAL SUNRISE on the vrat day. Sunrise is
    computed on-device from the user's GPS coordinates and is the canonical
    start of the fast in Hindu religious practice.
  - The Parana alarm fires at the OPENING of the post-fast eating window,
    typically the sunrise of the day after Ekadashi — within a 0–4 hour
    window whose closing time is dictated by tithi mechanics. A Parana
    fired 5 minutes late literally breaks the fast.

These times are user-meaningful to the minute. Inexact alarms (which Android
defers by 15+ minutes under Doze on several major OEM ROMs) are unusable for
this purpose — a user would either miss the religious observance entirely or
break the fast incorrectly.

The app is the only app on the device firing these alarms; they're scheduled
at user-controlled times the user explicitly subscribes to per event.
```

### `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` justification (if Play Console asks)

```
NavPanchang fires religious-observance alarms at local sunrise — typically
between 04:50 and 07:30 IST depending on date and latitude. The device is
almost always idle / in Doze at that hour.

Empirical telemetry from Android alarm reliability studies shows
setExactAndAllowWhileIdle() can be deferred 15+ minutes by Doze on several
major OEM ROMs (Xiaomi, Vivo, Oppo, Samsung One UI in aggressive battery
mode). A 15-minute delay on a sunrise vrat alarm makes the observance
incorrect — sunrise has already passed.

To deliver alarms reliably, the app prompts the user — once, on the
in-app onboarding step, with clear explanatory text — to whitelist
NavPanchang from battery optimization. The request is gated on user
consent and is not silent. Users who deny the prompt can re-enable
battery optimization at any time from system Settings; the app continues
to function on a best-effort basis in that case.
```

### App content declarations

| Question | Answer |
|---|---|
| Privacy policy URL | (paste the GitHub Pages URL once Pages is enabled) |
| App access — Is all the app's functionality available without any restrictions? | **Yes** — no login, no paywall, no region lock |
| Ads | **No ads** |
| Content rating | (from the IARC section above) |
| Target audience and content | **Ages 18+** — the app is religious software intended for adult observers; a children's category designation would be misleading |
| News app | **No** |
| COVID-19 contact-tracing or vaccination status | **No** |
| Data safety | (from the Data safety section above) |
| Government app | **No** |
| Financial features | **No** |
| Health features | **No** |

---

## Failure modes and how to debug them

| Symptom | Likely cause | Fix |
|---|---|---|
| CI build fails: `Keystore was tampered with, or password was incorrect` | `KEYSTORE_PASSWORD` secret has trailing whitespace or wrong value | Re-paste the password. Beware of trailing newlines. |
| CI build fails: `Could not find alias 'navpanchang'` | `KEY_ALIAS` secret doesn't match what's in the keystore | Run `keytool -list -keystore ... -storetype PKCS12` locally to confirm the alias name |
| Users report "App not installed" on update | New APK signed with a different keystore | Verify the CI is using the right `KEYSTORE_BASE64`. Compare SHA-256 fingerprint of installed APK (`apksigner verify --print-certs`) against your local keystore |
| In-app updater says "couldn't check" forever | Device offline, or `api.github.com` blocked, or rate-limited (60 req/IP/hour unauthenticated) | Daily worker will retry. Manual "Check now" button gives the user agency. Rate-limit unlikely at any reasonable scale. |
| Play Store rejects update | Could be many things — read the rejection email | Most common: data-safety form out of date, missing privacy policy, or `USE_EXACT_ALARM` without justification |

---

## Related docs

- [`MEMORY.md`](../MEMORY.md) — locked decisions, including "no analytics" and update-check architecture.
- [`TECH_DESIGN.md`](../TECH_DESIGN.md) — full architecture; release pipeline section near the end.
- [`scripts/generate_keystore.sh`](../scripts/generate_keystore.sh) — keystore generator.
- [`scripts/download_counts.sh`](../scripts/download_counts.sh) — sole "usage measurement" tool. Reports per-tag download counts from GitHub.

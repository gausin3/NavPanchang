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
- Two passwords (keystore + key). Use a password manager to generate both.

After it finishes, the script prints the SHA-256 fingerprint and writes a base64 backup file. **Save the passwords in your password manager immediately.**

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
| `KEYSTORE_PASSWORD` | The keystore password you just chose |
| `KEY_ALIAS` | `navpanchang` (matches the alias used by the generator script) |
| `KEY_PASSWORD` | The key password you just chose |
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

Once the asset is up, sideloaded users see it via the in-app update check (see "Update flow for sideloaded users" below).

---

## Update flow for sideloaded users

The in-app updater (`UpdateChecker` + daily `UpdateCheckWorker`) calls
`https://api.github.com/repos/gausin3/NavPanchang/releases/latest` and compares
the `tag_name` against `BuildConfig.VERSION_NAME`. If the tag is newer:

- The Settings → "App updates" card shows "Update available: vX.Y.Z" with a Download button.
- A one-shot system notification fires (dedicated channel, mutable independently of vrat alarms).
- Tapping Download opens the APK URL in the system browser. Browser downloads → user taps the file → Android prompts "install from unknown sources" → user confirms → Android installs.

The check is **opt-out via Settings**. Per [`MEMORY.md`](../MEMORY.md) "Locked decisions": no analytics, no phone-home, no telemetry. The update check is the single network call the app makes; it's a courtesy, not measurement.

### The Play Store guard

The app must detect when it was installed from Play Store (`getInstallerPackageName() == "com.android.vending"`) and **disable the GitHub-update card entirely** in that case. Otherwise sideloaded users who later install from Play Store would see two competing update sources.

This guard is one `if` statement. **Build it into the updater from day one** — adding it later means existing GitHub-installed users keep checking GitHub forever, even if you eventually re-install them via Play Store.

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

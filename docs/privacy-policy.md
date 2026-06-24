# NavPanchang — Privacy Policy

_Last updated: 2026-06-24_

NavPanchang is a free, open-source Hindu panchang and vrat-alarm app for Android,
distributed via Google Play Store and as a sideloaded APK on GitHub Releases.
This policy describes what the app does — and, more importantly, what it does
not do — with your data.

## Summary

- **The app makes zero network requests during normal use.** Everything is
  computed on your device.
- **No data is collected, stored on a server, or transmitted to us or anyone
  else.** There is no account, no login, no cloud sync.
- **No third-party analytics, crash-reporting, advertising, or tracking SDKs
  are bundled.** The full list of dependencies is in the open-source
  [`app/build.gradle.kts`](https://github.com/gausin3/NavPanchang/blob/main/app/build.gradle.kts).

## What the app needs to function

NavPanchang computes sunrise, sunset, and lunar-day boundaries for your
location. To do that on-device, it asks for the following permissions:

| Permission | Why |
| --- | --- |
| **Location (foreground)** — `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Your latitude and longitude are used in the on-device sunrise / sunset calculation. The coordinates are stored locally in the app's database and **never transmitted off the device**. |
| **Notifications** — `POST_NOTIFICATIONS` (Android 13+) | To show vrat alarms (the entire purpose of the app). |
| **Exact alarms** — `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Religious observance alarms (sunrise, Parana, Pradosh) must fire at astronomically computed times, accurate to the minute. Inexact / Doze-deferred alarms are unusable for this purpose. |
| **Battery optimization whitelist** — `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Doze defers alarms on many devices, which breaks the core product. The app explicitly asks for your consent on the in-app onboarding step; you can deny it. |
| **Boot completed**, **Wake lock**, **Vibrate** | Re-arm alarms after a reboot, wake the device for the alarm, vibrate the phone — all local to your device. |

If you grant any of the above, the data stays on your device. The app does not
have internet permission for any function described above.

## What is stored locally

The app saves the following data on your device only:

- Your selected **home city** (latitude, longitude, timezone label).
- Your **event subscriptions** (which vrats you've toggled on, your alarm
  sound preference, custom planner time).
- Computed **occurrence dates** for the next 24 months (a local cache that is
  recomputed daily).
- App settings (language, lunar-month convention, numeral system).

All of this is in the app's private storage and is included in Android's
standard Auto Backup so it can survive a phone migration. We — the developers —
have no access to any of it.

## Optional: future update check

A future release of NavPanchang will include an **optional, off-by-default**
"Check for updates" control. If you explicitly enable it, the app will make a
single unauthenticated HTTPS request to
[`api.github.com`](https://api.github.com) to read the latest release tag and
compare it to your installed version. No personal data is sent; the request
includes only the standard `User-Agent` header. You can leave this toggle off
permanently and the app remains fully offline.

This feature is not yet shipped. When it ships, this section will be updated.

## Data deletion

NavPanchang collects no data on any server. There is no account, no
cloud sync, no backup we hold. The only place your data exists is on
your own device.

**To delete all NavPanchang data**, uninstall the app from your Android
device:

1. Long-press the NavPanchang icon in your launcher.
2. Tap **Uninstall** (or drag to the "Uninstall" target, depending on
   launcher).
3. Confirm.

Uninstalling removes:

- Your **home city** (latitude / longitude / timezone).
- Your **event subscriptions** and alarm-sound preferences.
- Computed **occurrence dates** and the next-24-months cache.
- App settings (language, lunar-month convention, numeral system).
- All notifications and scheduled alarms.

Android may retain a copy of the above in its
[Auto Backup](https://developer.android.com/identity/data/autobackup)
mechanism (so that a re-install on a new phone can restore your
subscriptions). To delete that cloud-side Auto Backup copy as well,
go to Google's
[Drive backups page](https://drive.google.com/drive/backups) → find
NavPanchang in the list → delete its backup. (That data is in *your*
Google Drive, not ours — we never have access to it.)

**If you want help with deletion, or have a question about data
handling we don't address here**, email
[gaurav@navtakniq.com](mailto:gaurav@navtakniq.com) and we will respond
within a reasonable time. There is no support ticket queue because
there is no data on our side to act on — but a human will answer.

## Children's privacy

The app is not directed at children and does not knowingly collect any data
from anyone, including children. There is no account system and no data
collection.

## Open source

The full source code is available under the
[GNU Affero General Public License v3](https://github.com/gausin3/NavPanchang/blob/main/LICENSE)
at <https://github.com/gausin3/NavPanchang>. If you don't trust this policy,
read the code; if it's wrong, file an issue.

## Third-party software

The app embeds the
[Swiss Ephemeris Thomas Mack Java port](https://github.com/Tom-Mack/swisseph)
(AGPL v3) for astronomical calculations. The library runs entirely on-device
and makes no network calls of its own.

## Contact

If you have any question about this policy or how the app handles your data:

- **Email:** gaurav@navtakniq.com
- **GitHub Issues:** <https://github.com/gausin3/NavPanchang/issues>

## Changes to this policy

If this policy changes, the "Last updated" date at the top will be revised and
the change will appear in the file history at
<https://github.com/gausin3/NavPanchang/commits/main/docs/privacy-policy.md>.

Material changes (e.g., the introduction of any kind of telemetry, ever) will
also be called out in the app's release notes.

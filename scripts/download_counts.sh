#!/usr/bin/env bash
# Download-count summary for the NavPanchang GitHub Releases.
#
# Per MEMORY.md "Locked decisions": no analytics, no phone-home, no telemetry.
# The only signal we look at is GitHub's per-asset `download_count`. Each row
# below = one tagged release; the number is total APK downloads across all
# assets for that release.
#
# Caveats — read these before drawing conclusions:
#   * One user updating from v1.0.0 → v1.0.1 → v1.0.2 contributes three
#     downloads, not one. The total OVERCOUNTS unique users by roughly
#     (avg releases per user). For early-stage adoption, divide by ~1.5–2 for
#     a rough unique-user estimate; for a stable user base across many
#     releases, divide by the count of releases shipped.
#   * Uninstalls are invisible. Once downloaded, always counted.
#   * No DAU/MAU. We don't measure ongoing usage. We chose not to.
#   * Re-downloads of the same APK by the same user (e.g., reinstall after
#     factory reset) also count. Negligible at this scale.
#
# Requires: bash, curl, python3.
# No auth needed for public repos. GitHub rate limits unauthenticated callers
# to 60 requests/IP/hour, which is fine for this script.
#
# Usage:
#   ./scripts/download_counts.sh              # default repo
#   ./scripts/download_counts.sh OWNER/REPO   # any repo

set -euo pipefail

REPO="${1:-gausin3/NavPanchang}"

# curl handles TLS via the system trust store, avoiding Python SSL hiccups on
# Macs running a python.org build. Pipe JSON through Python only for parsing.
curl -fsSL \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${REPO}/releases" \
| python3 -c '
import json
import sys
from datetime import datetime

repo = sys.argv[1]
data = json.load(sys.stdin)

if not data:
    print("No releases yet for " + repo + ".")
    sys.exit(0)

print("NavPanchang download counts (" + repo + ")")
print()
header = "{:<14} {:<12} {:>14}  Notes".format("Tag", "Published", "APK downloads")
divider = "{} {} {}  {}".format("-" * 14, "-" * 12, "-" * 14, "-" * 30)
print(header)
print(divider)

total = 0
for release in data:
    tag = release.get("tag_name", "-")
    published = release.get("published_at", "")[:10] or "-"
    apk_assets = [a for a in release.get("assets", []) if a["name"].endswith(".apk")]
    count = sum(a["download_count"] for a in apk_assets)
    total += count
    notes = ""
    if release.get("draft"):
        notes = "(draft)"
    elif release.get("prerelease"):
        notes = "(pre-release)"
    print("{:<14} {:<12} {:>14}  {}".format(tag, published, count, notes))

print("{} {} {}".format("-" * 14, "-" * 12, "-" * 14))
print("{:<14} {:<12} {:>14}".format("TOTAL", "", total))
print()
print("Generated: " + datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S") + "Z")
print()
print("Reminder: download counts are not unique users. See script header for caveats.")
' "$REPO"

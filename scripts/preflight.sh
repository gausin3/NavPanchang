#!/usr/bin/env bash
# Local mirror of the GitHub Actions CI pipeline (.github/workflows/ci.yml).
#
# Runs the SAME three Gradle invocations CI does, with the SAME env-var contract
# the build job uses for signing. If this script exits 0, CI almost certainly
# will too — the only differences left are the runner OS (Ubuntu vs macOS) and
# network flakes pulling Gradle plugins, neither of which usually moves the
# needle.
#
# Why bother: tags are sticky. Cutting v0.1.0 then watching CI fail forces
# either a "v0.1.0-1" hack-tag or a delete-and-retag dance that confuses
# anyone watching the GitHub Releases page. A 4-minute local run dodges that.
#
# Usage:
#   scripts/preflight.sh                       # full pipeline (lint + tests + signed release build)
#   scripts/preflight.sh --skip-release        # just lint + tests (faster — for quick validation)
#   scripts/preflight.sh --keystore <path>     # override keystore location
#
# Defaults match scripts/generate_keystore.sh and docs/RELEASE.md:
#   keystore = ~/keystores/navpanchang-release.jks
#   alias    = navpanchang
#
# Password: read interactively (no echo). Same value used for KEYSTORE_PASSWORD
# and KEY_PASSWORD because PKCS12 unifies them.
#
# CI parity:
#   - Same JDK major version assumed (17). Script warns if your local java -version differs.
#   - Same VERSION_CODE handling (env var with sane local default).
#   - Same Gradle tasks in the same order.

set -euo pipefail

# ----- Parse args ---------------------------------------------------------

SKIP_RELEASE=0
KEYSTORE="${HOME}/keystores/navpanchang-release.jks"
ALIAS="navpanchang"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-release) SKIP_RELEASE=1; shift ;;
        --keystore)     KEYSTORE="$2"; shift 2 ;;
        -h|--help)
            grep -E '^# (Usage|  scripts)' "$0" | sed 's/^# //'
            exit 0
            ;;
        *) echo "Unknown flag: $1" >&2; exit 1 ;;
    esac
done

# ----- Pre-flight environment checks --------------------------------------

cd "$(dirname "$0")/.."  # repo root

if [ ! -f "./gradlew" ]; then
    echo "ERROR: gradlew not found. Run from the NavPanchang repo." >&2
    exit 1
fi

if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
        echo "ANDROID_HOME not set; defaulting to $ANDROID_HOME"
    else
        echo "ERROR: ANDROID_HOME is not set and no SDK at ~/Library/Android/sdk." >&2
        exit 1
    fi
fi

# JDK 17 nudge — CI uses Temurin 17. Mismatched majors don't always fail
# (Gradle 8.x tolerates 17 / 21), but a warning here saves a confused
# debug session if something does.
JAVA_MAJOR=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"' | head -1)
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then
    echo "ERROR: Java $JAVA_MAJOR detected; CI uses 17. Install JDK 17 first." >&2
    exit 1
elif [ "${JAVA_MAJOR}" != "17" ]; then
    echo "WARN: Local Java is $JAVA_MAJOR but CI uses 17. Usually fine, but if"
    echo "      you hit a Gradle/AGP compatibility error, switch to JDK 17."
fi

# ----- Stage 1 — Lint -----------------------------------------------------

echo
echo "================================================================"
echo "[1/3] Lint  (mirrors CI: ./gradlew :app:lintDebug)"
echo "================================================================"
./gradlew :app:lintDebug

# ----- Stage 2 — Unit tests -----------------------------------------------

echo
echo "================================================================"
echo "[2/3] Unit tests  (mirrors CI: ./gradlew :app:testDebugUnitTest)"
echo "================================================================"
./gradlew :app:testDebugUnitTest

# ----- Stage 3 — Signed release build -------------------------------------

if [ "$SKIP_RELEASE" -eq 1 ]; then
    echo
    echo "Skipping signed-release build (--skip-release). Lint + tests passed."
    echo "Re-run without --skip-release before tagging."
    exit 0
fi

if [ ! -f "$KEYSTORE" ]; then
    echo "ERROR: Keystore not found at $KEYSTORE" >&2
    echo "       Run scripts/generate_keystore.sh first, or pass --keystore <path>." >&2
    exit 1
fi

echo
echo "================================================================"
echo "[3/3] Signed release build  (mirrors CI build job)"
echo "================================================================"
echo "Keystore: $KEYSTORE"
echo "Alias:    $ALIAS"
echo

# Read password silently. Same value for KEYSTORE_PASSWORD and KEY_PASSWORD
# because the keystore is PKCS12 (single-password format).
read -rsp "Keystore password: " KS_PASS
echo
if [ -z "$KS_PASS" ]; then
    echo "ERROR: Empty password." >&2
    exit 1
fi

# Quick sanity check before kicking off a multi-minute build: can keytool
# actually open the keystore with this password? Catches typos in 1 second
# instead of 4 minutes.
if ! keytool -list -keystore "$KEYSTORE" -storepass "$KS_PASS" -alias "$ALIAS" >/dev/null 2>&1; then
    echo "ERROR: keystore failed to open with the given password+alias." >&2
    echo "       (alias=$ALIAS, keystore=$KEYSTORE)" >&2
    unset KS_PASS
    exit 1
fi

echo "Password OK. Building signed release APK + AAB…"
echo

export KEYSTORE_FILE="$KEYSTORE"
export KEYSTORE_PASSWORD="$KS_PASS"
export KEY_ALIAS="$ALIAS"
export KEY_PASSWORD="$KS_PASS"
export VERSION_CODE="${VERSION_CODE:-1}"   # CI uses github.run_number; locally just use 1 unless caller overrode.

./gradlew :app:assembleRelease :app:bundleRelease

# Don't leave the password in the environment any longer than necessary.
unset KS_PASS KEYSTORE_PASSWORD KEY_PASSWORD

# ----- Summary ------------------------------------------------------------

APK=$(find app/build/outputs/apk/release -name "*.apk" 2>/dev/null | head -1 || true)
AAB=$(find app/build/outputs/bundle/release -name "*.aab" 2>/dev/null | head -1 || true)

echo
echo "================================================================"
echo "✅ All preflight checks passed."
echo "================================================================"
[ -n "$APK" ] && echo "  Signed APK: $APK"
[ -n "$AAB" ] && echo "  Signed AAB: $AAB"
echo
echo "Verify the APK signature matches your release keystore:"
echo "    \$ANDROID_HOME/build-tools/*/apksigner verify --print-certs $APK"
echo
echo "If everything looks good, you're safe to tag:"
echo "    git tag -a v0.1.0 -m \"v0.1.0 — first public release\""
echo "    git push origin v0.1.0"
echo "================================================================"

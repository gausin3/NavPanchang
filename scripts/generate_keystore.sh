#!/usr/bin/env bash
# Generate the NavPanchang release keystore.
#
# Run this ONCE on your local machine. The keystore signs every released APK and
# AAB. Lose it and existing users cannot install future updates without
# uninstalling first (which loses their subscriptions and alarms). Treat it like
# a domain registrar password.
#
# After generation:
#   1. Back up the .jks to at least three locations (encrypted external drive,
#      password-manager attachment, optionally a printed base64 dump).
#   2. Save the keystore + key passwords in a password manager.
#   3. Add four GitHub Actions secrets per docs/RELEASE.md.
#   4. Verify periodically that you can still decode and read the backup.
#
# This script does NOT store passwords. It does NOT upload anything. It only
# runs `keytool` locally with sensible defaults.
#
# Usage:
#   ./scripts/generate_keystore.sh [output-path]
#
# Defaults:
#   output path = ~/keystores/navpanchang-release.jks

set -euo pipefail

OUT="${1:-$HOME/keystores/navpanchang-release.jks}"
ALIAS="navpanchang"

# ----- Pre-flight checks --------------------------------------------------

if ! command -v keytool >/dev/null 2>&1; then
    echo "ERROR: keytool not found. Install a JDK (e.g., 'brew install openjdk' on macOS)."
    exit 1
fi

OUT_DIR="$(dirname "$OUT")"
if [ ! -d "$OUT_DIR" ]; then
    echo "Creating output directory: $OUT_DIR"
    mkdir -p "$OUT_DIR"
fi

if [ -e "$OUT" ]; then
    echo
    echo "A keystore already exists at:  $OUT"
    echo
    echo "REFUSING TO OVERWRITE. Generating a new keystore would orphan every"
    echo "user who installed an APK signed with the existing one — they could"
    echo "never update again without uninstalling first."
    echo
    echo "If you genuinely meant to start over, move the existing file aside"
    echo "first. Confirm you understand the consequences:"
    echo "    mv \"$OUT\" \"$OUT.archived-\$(date +%Y%m%d)\""
    echo
    exit 1
fi

# ----- Pick distinguishing-name fields ------------------------------------

read -rp "Common Name (e.g., 'NavPanchang' — appears in the cert): " CN
CN="${CN:-NavPanchang}"

read -rp "Organization (e.g., 'NavPanchang') [NavPanchang]: " ORG
ORG="${ORG:-NavPanchang}"

read -rp "Country code (2-letter, e.g., IN, US, GB) [IN]: " COUNTRY
COUNTRY="${COUNTRY:-IN}"

# ----- Generate -----------------------------------------------------------

echo
echo "About to generate a 4096-bit RSA key, valid 100 years, in PKCS12 format."
echo "  Output: $OUT"
echo "  Alias:  $ALIAS"
echo "  CN=$CN, O=$ORG, C=$COUNTRY"
echo
echo "You will be prompted for two passwords:"
echo "  1) Keystore password — protects the .jks file."
echo "  2) Key password — protects the key inside it."
echo "Many users use the same value for both. Use a long random string."
echo "Save both passwords in a password manager IMMEDIATELY."
echo
read -rp "Continue? (y/N) " confirm
if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "Aborted."
    exit 0
fi

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 36500 \
  -keystore "$OUT" \
  -storetype PKCS12 \
  -dname "CN=$CN, O=$ORG, C=$COUNTRY"

# ----- Post-generation: fingerprint + backup-base64 -----------------------

echo
echo "Keystore generated successfully."
echo
echo "----- Fingerprint (record this; you'll compare it to verify backups) -----"
keytool -list -v -keystore "$OUT" -alias "$ALIAS" 2>/dev/null \
  | grep -E "SHA1:|SHA256:" || true
echo

BASE64_OUT="${OUT}.base64.txt"
base64 -i "$OUT" > "$BASE64_OUT" 2>/dev/null || base64 "$OUT" > "$BASE64_OUT"
echo "Base64-encoded backup written to:"
echo "  $BASE64_OUT"
echo
echo "Print this file (yes, on paper) and store it offline as a last-resort backup."
echo

# ----- Next steps ---------------------------------------------------------

cat <<EOF
================================================================
NEXT STEPS  (do these now, before you forget)
================================================================

1. Save BOTH passwords in your password manager (1Password / Bitwarden /
   KeePass). The keystore is useless without them.

2. Copy the .jks file to at least three secure locations:
     - Local encrypted folder (e.g., FileVault / LUKS / VeraCrypt).
     - Cloud password manager attachment (1Password Documents, etc.).
     - External drive or printed base64 dump kept offline.

3. Set GitHub Actions secrets. Run this and paste each value via the
   GitHub UI (Settings → Secrets and variables → Actions):

     KEYSTORE_BASE64       = contents of $BASE64_OUT
     KEYSTORE_PASSWORD     = the keystore password you just chose
     KEY_ALIAS             = $ALIAS
     KEY_PASSWORD          = the key password you just chose

   See docs/RELEASE.md for the full secret list.

4. Once a year, decode the printed/cloud backup and verify it matches the
   fingerprint above. Catches data rot before you need the backup.

================================================================
EOF

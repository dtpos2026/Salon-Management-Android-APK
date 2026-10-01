#!/usr/bin/env bash
# Creates YOUR release signing key on YOUR computer (Linux, macOS or Git Bash on Windows).
# Needs Java's keytool (installed with Android Studio or any JDK).
#
# Output (in the current folder):
#   dt-salon-release.p12      the key itself - keep a safe backup, never upload it anywhere public
#   DT_KEYSTORE_BASE64.txt    the same key as text, for the GitHub secret DT_KEYSTORE_BASE64
# It also prints the SHA-1 / SHA-256 fingerprints to add in Firebase.
set -euo pipefail

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool not found. Install Android Studio or a JDK, or run this from Android Studio's terminal." >&2
  exit 1
fi
if [ -e dt-salon-release.p12 ]; then
  echo "dt-salon-release.p12 already exists here. Move it away first (never overwrite your key)." >&2
  exit 1
fi

read -r -s -p "Choose a strong password (min 12 characters): " PW; echo
read -r -s -p "Type it again: " PW2; echo
[ "$PW" = "$PW2" ] || { echo "Passwords do not match." >&2; exit 1; }
[ ${#PW} -ge 12 ] || { echo "Password too short." >&2; exit 1; }

keytool -genkeypair -storetype PKCS12 -keystore dt-salon-release.p12 \
  -storepass "$PW" -keypass "$PW" -alias dtsalon -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=DT Salon Management, O=Digital Target, C=PK"

base64 < dt-salon-release.p12 | tr -d '\n' > DT_KEYSTORE_BASE64.txt

echo
echo "Fingerprints to add in Firebase (Project settings > Your apps > Android > Add fingerprint):"
keytool -list -v -keystore dt-salon-release.p12 -storepass "$PW" -alias dtsalon | grep -E "SHA1:|SHA256:"
echo
echo "Next: add GitHub secrets DT_KEYSTORE_BASE64 (contents of DT_KEYSTORE_BASE64.txt) and"
echo "DT_SIGNING_PASSWORD (your password). Keep dt-salon-release.p12 and the password safe:"
echo "without them you can never publish an update to installed apps."

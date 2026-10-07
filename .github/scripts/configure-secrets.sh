#!/usr/bin/env bash
# Writes the release signing key and the Firebase config from GitHub secrets, if they exist.
# Nothing secret is ever stored in the repository. See docs/SETUP_GUIDE.md ("GitHub secrets").
set -euo pipefail

if [ -n "${DT_KEYSTORE_BASE64:-}" ] && [ -n "${DT_SIGNING_PASSWORD:-}" ]; then
  mkdir -p app/signing
  echo "$DT_KEYSTORE_BASE64" | base64 -d > app/signing/dt-salon-release.p12
  cat > keystore.properties <<PROPS
storeFile=app/signing/dt-salon-release.p12
storePassword=${DT_SIGNING_PASSWORD}
keyAlias=dtsalon
keyPassword=${DT_SIGNING_PASSWORD}
PROPS
  echo "Signing: release key configured (debug and release APKs are signed with it)."
  echo "Fingerprints to register in Firebase (public information):"
  keytool -list -v -keystore app/signing/dt-salon-release.p12 -storepass "$DT_SIGNING_PASSWORD" -alias dtsalon \
    | grep -E "SHA1:|SHA256:" || echo "::warning::Could not read the key: check the alias is 'dtsalon' and the password."
else
  echo "::warning::DT_KEYSTORE_BASE64 / DT_SIGNING_PASSWORD secrets are not set: the release APK is signed with the build's debug key (kept between builds by the Actions cache). Set the secrets for a permanent release key."
fi

if [ -n "${GOOGLE_SERVICES_JSON:-}" ]; then
  printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json
  python3 -c "import json; d=json.load(open('app/google-services.json')); print('Firebase project:', d['project_info']['project_id'])"
else
  echo "::warning::GOOGLE_SERVICES_JSON secret is not set: the app is built without Firebase (login shows 'setup required')."
fi

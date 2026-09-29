# Licensing (commercial edition)

Salon Manager can be sold to many salons with time-limited licences that are verified
**offline**. No server is required today; the design allows an online licence service later.

## How it works

1. Each installation has an **Installation ID** (e.g. `7KQM-2XWD-HP9R`), shown in
   *Settings → Licence*. It is stored in private preferences (not in the database), so a backup
   restored on another phone does not carry over a device-bound licence.
2. The vendor issues a **licence key** for that ID:
   `SLN1.<base64url payload>.<base64url ECDSA P-256 signature>`.
   The payload contains: licence ID, business name, plan, issue date, expiry date (or none for
   lifetime), installation ID (or `*` for any device), max businesses and feature flags.
3. The app verifies the signature with the **public key compiled into the build** and evaluates
   the status (`LicenseEvaluator`): trial, active, expiring soon (7 days), expired, invalid
   device, or clock tampering (phone date moved back more than 2 days).
4. When enforcement is on and the licence/trial is no longer valid, the app becomes
   **read-only**: all data, reports and backups stay available; new sales are blocked.

Plans: `MONTH_1`, `MONTH_3`, `MONTH_6`, `YEAR_1`, `CUSTOM` (explicit expiry) and `LIFETIME`.

## Build configuration

`gradle.properties` (or `-P` flags / CI secrets):

| Property | Default | Meaning |
|----------|---------|---------|
| `salon.enforceLicense` | `false` | `false` = owner edition, no limits. `true` = commercial build with trial and licence checks. |
| `salon.trialDays` | `30` | Trial length from first launch when no licence is activated. |
| `salon.licensePublicKey` | *(empty)* | Base64 X.509 public key from the licence tool. Without it, activation is disabled. |

## Vendor tool

Run from the repository root (requires JDK 17+, downloads the Kotlin compiler on first run):

```bash
# 1. Once: create the signing key pair (writes tools/license/out/, which is git-ignored)
./gradlew -p tools/license run --args="keygen"

# 2. Per customer: issue a key for their installation ID
./gradlew -p tools/license run --args='issue --id LIC-2026-0001 --business "Royal Barber Shop" --plan YEAR_1 --device 7KQM-2XWD-HP9R'

# 3. Optional: check a key
./gradlew -p tools/license run --args="verify --key SLN1...."
```

The customer pastes the key in *Settings → Licence → Activate*.

**Protect `tools/license/out/license-private.key`.** Anyone holding it can create licences, and
if it is lost you cannot issue keys that existing builds accept. Keep an encrypted offline copy.

## Adding online verification later

`LicenseManager` stores the key and asks `LicenseEvaluator` for the status. An online service
can be added without changing the key format: periodically send the licence ID and installation
ID, and store a revocation flag or a renewed key returned by the server. Offline operation must
keep working when the server is unreachable (grace period), which is why the evaluator is pure
and date-based. The app would also need the `INTERNET` permission, which v1 intentionally omits.

# Creates YOUR release signing key on YOUR Windows computer (PowerShell).
# Run from this folder:  powershell -ExecutionPolicy Bypass -File create-signing-key.ps1
# Output: dt-salon-release.p12 (keep it safe) and DT_KEYSTORE_BASE64.txt (for the GitHub secret).
$ErrorActionPreference = "Stop"

$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
        "$env:ProgramFiles\Android\Android Studio\jre\bin\keytool.exe"
    )
    $keytool = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $keytool) { throw "keytool not found. Install Android Studio first." }
if (Test-Path "dt-salon-release.p12") { throw "dt-salon-release.p12 already exists here. Move it away first." }

$pw1 = Read-Host "Choose a strong password (min 12 characters)" -AsSecureString
$pw2 = Read-Host "Type it again" -AsSecureString
$p1 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw1))
$p2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($pw2))
if ($p1 -ne $p2) { throw "Passwords do not match." }
if ($p1.Length -lt 12) { throw "Password too short." }

& $keytool -genkeypair -storetype PKCS12 -keystore dt-salon-release.p12 -storepass $p1 -keypass $p1 `
    -alias dtsalon -keyalg RSA -keysize 4096 -validity 10000 `
    -dname "CN=DT Salon Management, O=Digital Target, C=PK"

[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path "dt-salon-release.p12"))) |
    Set-Content -NoNewline -Encoding ascii "DT_KEYSTORE_BASE64.txt"

Write-Host ""
Write-Host "Fingerprints to add in Firebase (Project settings > Your apps > Android > Add fingerprint):"
& $keytool -list -v -keystore dt-salon-release.p12 -storepass $p1 -alias dtsalon | Select-String "SHA1:|SHA256:"
Write-Host ""
Write-Host "Next: add GitHub secrets DT_KEYSTORE_BASE64 (contents of DT_KEYSTORE_BASE64.txt) and DT_SIGNING_PASSWORD."
Write-Host "Keep dt-salon-release.p12 and the password safe: without them you cannot update installed apps."

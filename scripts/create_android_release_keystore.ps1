param(
    [string]$Keystore = "$env:USERPROFILE\Documents\FFacio-signing\ffacio-android-release.jks",
    [string]$Alias = "ffacio-android-release",
    [string]$DName = "CN=FFacio Android Release,O=sampple-korea,C=KR",
    [int]$ValidityDays = 10000,
    [switch]$Force,
    [switch]$AllowRepoPath
)

$ErrorActionPreference = "Stop"

function ConvertTo-PlainText([securestring]$Secure) {
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Read-PasswordOrEnv([string]$EnvName, [string]$Prompt) {
    $fromEnv = [Environment]::GetEnvironmentVariable($EnvName)
    if ($fromEnv) { return $fromEnv }
    return ConvertTo-PlainText (Read-Host -Prompt $Prompt -AsSecureString)
}

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "$env:ProgramFiles\Android\Android Studio\jbr" }
$keytool = Join-Path $javaHome "bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    throw "keytool.exe not found. Install Android Studio JBR or set JAVA_HOME."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$targetDir = Split-Path -Parent $Keystore
if (-not $targetDir) { throw "Keystore path must include a directory." }
New-Item -ItemType Directory -Force $targetDir | Out-Null
$resolvedDir = (Resolve-Path $targetDir).Path
$targetPath = Join-Path $resolvedDir (Split-Path -Leaf $Keystore)

if (-not $AllowRepoPath -and $targetPath.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to create a production Android keystore inside the repo. Use a path outside $repoRoot or pass -AllowRepoPath for disposable testing."
}
if ((Test-Path $targetPath) -and -not $Force) {
    throw "Keystore already exists: $targetPath. Reuse it for upgrade-compatible releases, or pass -Force only if you intentionally want a new signing lineage."
}

$storePassword = Read-PasswordOrEnv "FFACIO_ANDROID_KEYSTORE_PASSWORD" "Android keystore password"
if ($storePassword.Length -lt 12) {
    throw "Use a keystore password of at least 12 characters."
}
$keyPassword = [Environment]::GetEnvironmentVariable("FFACIO_ANDROID_KEY_PASSWORD")
if (-not $keyPassword) {
    $keyPassword = ConvertTo-PlainText (Read-Host -Prompt "Android key password (press Enter to reuse keystore password)" -AsSecureString)
}
if (-not $keyPassword) { $keyPassword = $storePassword }
if ($keyPassword.Length -lt 12) {
    throw "Use a key password of at least 12 characters."
}

if (Test-Path $targetPath) {
    Remove-Item -LiteralPath $targetPath -Force
}

& $keytool -genkeypair `
    -v `
    -keystore $targetPath `
    -storepass $storePassword `
    -alias $Alias `
    -keypass $keyPassword `
    -keyalg RSA `
    -keysize 4096 `
    -validity $ValidityDays `
    -dname $DName
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE." }

& $keytool -list -v -keystore $targetPath -storepass $storePassword -alias $Alias | Out-String | Write-Host

Write-Host ""
Write-Host "Android release keystore created outside the repo:"
Write-Host "  $targetPath"
Write-Host ""
Write-Host "Set these in the same PowerShell session before production builds:"
Write-Host "`$env:FFACIO_ANDROID_KEYSTORE = `"$targetPath`""
Write-Host "`$env:FFACIO_ANDROID_KEYSTORE_PASSWORD = `"<store-password>`""
Write-Host "`$env:FFACIO_ANDROID_KEY_ALIAS = `"$Alias`""
Write-Host "`$env:FFACIO_ANDROID_KEY_PASSWORD = `"<key-password>`""
Write-Host ""
Write-Host "Then run:"
Write-Host "powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build_android.ps1"
Write-Host ""
Write-Host "Back this file up securely. Losing or replacing it breaks APK upgrade compatibility."

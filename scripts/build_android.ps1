param(
    [string]$AndroidDir = "$PSScriptRoot\..\android",
    [string]$ReleaseDir = "$PSScriptRoot\..\release",
    [string]$RuntimeApk = "",
    [switch]$AllowGeneratedSigningKey
)

$ErrorActionPreference = "Stop"

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path $sdk)) { throw "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." }

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "$env:ProgramFiles\Android\Android Studio\jbr" }
if (-not (Test-Path (Join-Path $javaHome "bin\java.exe"))) { throw "JDK not found. Set JAVA_HOME." }

$gradle = Join-Path $AndroidDir "gradlew.bat"
if (-not (Test-Path $gradle)) { throw "Gradle wrapper is missing: $gradle" }

$env:ANDROID_HOME = (Resolve-Path $sdk).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:JAVA_HOME = (Resolve-Path $javaHome).Path
$env:PATH = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:PATH
New-Item -ItemType Directory -Force $ReleaseDir | Out-Null

$keyStore = $env:FFACIO_KEYSTORE_PATH
if (-not $keyStore) { $keyStore = $env:FFACIO_ANDROID_KEYSTORE }
$storePassword = $env:FFACIO_KEYSTORE_PASSWORD
if (-not $storePassword) { $storePassword = $env:FFACIO_ANDROID_KEYSTORE_PASSWORD }
$keyAlias = $env:FFACIO_KEY_ALIAS
if (-not $keyAlias) { $keyAlias = $env:FFACIO_ANDROID_KEY_ALIAS }
$keyPassword = $env:FFACIO_KEY_PASSWORD
if (-not $keyPassword) { $keyPassword = $env:FFACIO_ANDROID_KEY_PASSWORD }
$storeType = $env:FFACIO_KEYSTORE_TYPE
if (-not $storeType) { $storeType = "PKCS12" }
$generatedKey = $false

if (-not $keyStore) {
    if (-not $AllowGeneratedSigningKey) {
        throw "Set FFacio Runtime signing variables (FFACIO_KEYSTORE_PATH, FFACIO_KEYSTORE_PASSWORD, FFACIO_KEY_ALIAS, FFACIO_KEY_PASSWORD). The client and Runtime must use the same certificate."
    }
    $keyStore = Join-Path $ReleaseDir "ffacio-local-runtime-pair.p12"
    if (-not $storePassword) { $storePassword = "ffacio-local-change-me" }
    if (-not $keyAlias) { $keyAlias = "ffacio-local-runtime-pair" }
    if (-not $keyPassword) { $keyPassword = $storePassword }
    $storeType = "PKCS12"
    $generatedKey = $true
}
if (-not $storePassword -or -not $keyAlias -or -not $keyPassword) {
    throw "Signing password, alias, and key password are required."
}

if (-not (Test-Path $keyStore)) {
    if (-not $generatedKey) { throw "Signing key does not exist: $keyStore" }
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    & $keytool -genkeypair -v -storetype PKCS12 -keystore $keyStore -storepass $storePassword -alias $keyAlias -keypass $keyPassword -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=FFacio Local Runtime Pair,O=sampple-korea,C=KR"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE." }
}
$keyStore = (Resolve-Path $keyStore).Path

# Runtime uses these names; exporting the same values prevents accidental certificate drift.
$env:FFACIO_KEYSTORE_PATH = $keyStore
$env:FFACIO_KEYSTORE_PASSWORD = $storePassword
$env:FFACIO_KEY_ALIAS = $keyAlias
$env:FFACIO_KEY_PASSWORD = $keyPassword
$env:FFACIO_KEYSTORE_TYPE = $storeType

Push-Location $AndroidDir
try {
    $log = Join-Path $ReleaseDir "android-gradle-verification.log"
    $tasks = @("clean", "testDebugUnitTest", "lintDebug", "assembleDebug", "assembleRelease")
    & $gradle --no-daemon @tasks `
        "-PffacioStoreFile=$keyStore" `
        "-PffacioStorePassword=$storePassword" `
        "-PffacioKeyAlias=$keyAlias" `
        "-PffacioKeyPassword=$keyPassword" `
        "-PffacioStoreType=$storeType" *>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
}
finally {
    Pop-Location
}

$debugSource = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
$releaseSource = Join-Path $AndroidDir "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $debugSource)) { throw "Debug APK was not produced: $debugSource" }
if (-not (Test-Path $releaseSource)) { throw "Release APK was not produced: $releaseSource" }
$debugOut = Join-Path $ReleaseDir "FFacio-Android-debug.apk"
$releaseOut = Join-Path $ReleaseDir "FFacio-Android-release.apk"
Copy-Item -Force $debugSource $debugOut
Copy-Item -Force $releaseSource $releaseOut

$apksigner = Get-ChildItem (Join-Path $env:ANDROID_HOME "build-tools") -Recurse -Filter apksigner.bat -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $apksigner) { throw "apksigner.bat is required. Install Android build-tools." }

function Get-SignerSha256([string]$Path) {
    $result = (& $apksigner.FullName verify --print-certs $Path) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $Path" }
    if ($result -notmatch "SHA-256 digest:\s*([0-9A-Fa-f:]+)") { throw "Could not read signer digest: $Path" }
    return $Matches[1].Replace(":", "").ToLowerInvariant()
}

$appSigner = Get-SignerSha256 $releaseOut
$runtimeSigner = $null
$runtimePairVerified = $false
if ($RuntimeApk) {
    if (-not (Test-Path $RuntimeApk)) { throw "Runtime APK not found: $RuntimeApk" }
    $RuntimeApk = (Resolve-Path $RuntimeApk).Path
    $runtimeSigner = Get-SignerSha256 $RuntimeApk
    if ($runtimeSigner -ne $appSigner) {
        throw "FFacio and FFacio Runtime are signed with different certificates. App=$appSigner Runtime=$runtimeSigner"
    }
    $runtimePairVerified = $true
} else {
    Write-Warning "Runtime APK was not supplied. Pair-signature verification was skipped; pass -RuntimeApk before deployment."
}

$gradleText = Get-Content -Raw (Join-Path $AndroidDir "app\build.gradle")
$versionName = if ($gradleText -match 'versionName\s+"([^"]+)"') { $Matches[1] } else { "unknown" }
$versionCode = if ($gradleText -match 'versionCode\s+([0-9]+)') { [int]$Matches[1] } else { 0 }
$gitRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$gitCommit = (& git -C $gitRoot rev-parse HEAD 2>$null)
if ($LASTEXITCODE -ne 0) { $gitCommit = $null }

$manifest = [ordered]@{
    name = "FFacio Android Runtime Client"
    version = $versionName
    version_code = $versionCode
    artifact = (Split-Path -Leaf $releaseOut)
    size = (Get-Item $releaseOut).Length
    sha256 = (Get-FileHash $releaseOut -Algorithm SHA256).Hash.ToLowerInvariant()
    debug_artifact = (Split-Path -Leaf $debugOut)
    debug_sha256 = (Get-FileHash $debugOut -Algorithm SHA256).Hash.ToLowerInvariant()
    generated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    git_commit = $gitCommit
    signed = $true
    signer_cert_sha256 = $appSigner
    runtime_package = "com.kbyai.faceattribute"
    runtime_service = "io.ffacio.runtime.FFacioRuntimeService"
    runtime_signature_permission = "io.ffacio.sdk.permission.BIND_RUNTIME"
    runtime_pair_signature_verified = $runtimePairVerified
    runtime_signer_cert_sha256 = $runtimeSigner
    legacy_models_bundled = $false
    unit_tests_verified = $true
    lint_verified = $true
    generated_signing_key = $generatedKey
}
$manifestPath = Join-Path $ReleaseDir "android-release-manifest.json"
$manifest | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 $manifestPath

$verifyArgs = @("-Apk", $releaseOut, "-Manifest", $manifestPath)
if ($RuntimeApk) { $verifyArgs += @("-RuntimeApk", $RuntimeApk) }
& (Join-Path $PSScriptRoot "verify_android_static.ps1") @verifyArgs
if ($LASTEXITCODE -ne 0) { throw "Static APK verification failed with exit code $LASTEXITCODE." }

Write-Host "Build complete: $releaseOut"
Write-Host "FFacio signer SHA-256: $appSigner"
if ($runtimePairVerified) { Write-Host "Runtime pair signer verified: $runtimeSigner" }

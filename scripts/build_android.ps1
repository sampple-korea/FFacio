param(
    [string]$AndroidDir = "$PSScriptRoot\..\android",
    [string]$ReleaseDir = "$PSScriptRoot\..\release",
    [switch]$SkipEmulatorVerification
)

$ErrorActionPreference = "Stop"

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path $sdk)) { throw "Android SDK not found. Set ANDROID_HOME or install Android Studio SDK." }

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "$env:ProgramFiles\Android\Android Studio\jbr" }
if (-not (Test-Path (Join-Path $javaHome "bin\java.exe"))) { throw "Java runtime not found. Set JAVA_HOME." }

$gradle = Join-Path $AndroidDir "gradlew.bat"
if (-not (Test-Path $gradle)) { throw "Gradle wrapper is missing: $gradle" }

$env:ANDROID_HOME = (Resolve-Path $sdk).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:JAVA_HOME = (Resolve-Path $javaHome).Path
$env:PATH = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:PATH

New-Item -ItemType Directory -Force $ReleaseDir | Out-Null

$modelManifest = Join-Path $PSScriptRoot "..\resources\models\models.manifest.json"
if (-not (Test-Path $modelManifest)) {
    & (Join-Path $PSScriptRoot "prepare_models.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Model preparation failed with exit code $LASTEXITCODE." }
}
if (-not (Test-Path $modelManifest)) {
    throw "Offline model manifest is missing after preparation: $modelManifest"
}

$keyStore = $env:FFACIO_ANDROID_KEYSTORE
if (-not $keyStore) { $keyStore = Join-Path $ReleaseDir "ffacio-local-release.jks" }
$storePassword = $env:FFACIO_ANDROID_KEYSTORE_PASSWORD
if (-not $storePassword) { $storePassword = "ffacio-local-release-change-me" }
$keyAlias = $env:FFACIO_ANDROID_KEY_ALIAS
if (-not $keyAlias) { $keyAlias = "ffacio-local-release" }
$keyPassword = $env:FFACIO_ANDROID_KEY_PASSWORD
if (-not $keyPassword) { $keyPassword = $storePassword }

if (-not (Test-Path $keyStore)) {
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    & $keytool -genkeypair -v -keystore $keyStore -storepass $storePassword -alias $keyAlias -keypass $keyPassword -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=FFacio Local Release,O=sampple-korea,C=KR"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE." }
}

Push-Location $AndroidDir
try {
    & $gradle --no-daemon clean assembleDebug assembleRelease "-PffacioStoreFile=$keyStore" "-PffacioStorePassword=$storePassword" "-PffacioKeyAlias=$keyAlias" "-PffacioKeyPassword=$keyPassword"
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
}
finally {
    Pop-Location
}

$debugApk = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
$releaseApk = Join-Path $AndroidDir "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $debugApk)) { throw "Debug APK was not produced: $debugApk" }
if (-not (Test-Path $releaseApk)) { throw "Release APK was not produced: $releaseApk" }
$debugOut = Join-Path $ReleaseDir "FFacio-Android-debug.apk"
$releaseOut = Join-Path $ReleaseDir "FFacio-Android-release.apk"
Copy-Item -Force $debugApk $debugOut
Copy-Item -Force $releaseApk $releaseOut

$debugFile = Get-Item $debugOut
$releaseFile = Get-Item $releaseOut
$debugHash = (Get-FileHash $debugOut -Algorithm SHA256).Hash.ToLowerInvariant()
$releaseHash = (Get-FileHash $releaseOut -Algorithm SHA256).Hash.ToLowerInvariant()
$gitCommit = (& git -C (Join-Path $PSScriptRoot "..") rev-parse HEAD 2>$null)
if ($LASTEXITCODE -ne 0) { $gitCommit = $null }
$gitStatus = (& git -C (Join-Path $PSScriptRoot "..") status --porcelain 2>$null)
if ($LASTEXITCODE -ne 0) { $gitStatus = @() }
$gradleText = Get-Content -Raw (Join-Path $AndroidDir "app\build.gradle")
$versionName = if ($gradleText -match 'versionName\s+"([^"]+)"') { $Matches[1] } else { "unknown" }
$versionCode = if ($gradleText -match 'versionCode\s+([0-9]+)') { [int]$Matches[1] } else { 0 }
$apksigner = Get-ChildItem (Join-Path $env:ANDROID_HOME "build-tools") -Recurse -Filter apksigner.bat -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1
if (-not $apksigner) { throw "apksigner.bat is required to build a verifiable Android release." }
$certOutput = (& $apksigner.FullName verify --print-certs $releaseOut) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "apksigner verification failed with exit code $LASTEXITCODE." }
$signerCertSha256 = $null
if ($certOutput -match "SHA-256 digest:\s*([0-9A-Fa-f:]+)") {
    $signerCertSha256 = $Matches[1].Replace(":", "").ToLowerInvariant()
}
if (-not $signerCertSha256) { throw "Could not read release signer certificate digest." }
$manifest = [ordered]@{
    name = "FFacio Android"
    version = $versionName
    version_code = $versionCode
    artifact = "FFacio-Android-release.apk"
    size = $releaseFile.Length
    sha256 = $releaseHash
    debug_artifact = "FFacio-Android-debug.apk"
    debug_size = $debugFile.Length
    debug_sha256 = $debugHash
    generated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    git_commit = $gitCommit
    git_dirty = @($gitStatus).Count -gt 0
    git_dirty_paths = @($gitStatus)
    signed = $true
    signer_cert_sha256 = $signerCertSha256
    signing = "local sideload release key; replace with your own keystore for production distribution"
    debug_signed = $true
    static_verified = $false
    emulator_verified = $false
    launch_verified = $false
    model_ready_verified = $false
    verified_apk_sha256 = $null
    verified_at = $null
    notes = "Release APK is locally signed for sideload testing, not Play production signing. OpenCV YuNet/SFace and the shared model bundle are included; no cloud subscription is used."
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $ReleaseDir "android-release-manifest.json")
& (Join-Path $PSScriptRoot "verify_android_static.ps1") -Apk $releaseOut -Manifest (Join-Path $ReleaseDir "android-release-manifest.json") -ModelManifest $modelManifest
if ($LASTEXITCODE -ne 0) { throw "Android static verification failed with exit code $LASTEXITCODE." }
$manifestPath = Join-Path $ReleaseDir "android-release-manifest.json"
$verified = Get-Content -Raw $manifestPath -Encoding UTF8 | ConvertFrom-Json
$verified.static_verified = $true
if (-not $SkipEmulatorVerification) {
    & (Join-Path $PSScriptRoot "verify_android_emulator.ps1") -Apk $releaseOut
    if ($LASTEXITCODE -ne 0) { throw "Android emulator verification failed with exit code $LASTEXITCODE." }
    $verified.emulator_verified = $true
    $verified.launch_verified = $true
    $verified.model_ready_verified = $true
    $verified.verified_apk_sha256 = $releaseHash
    $verified.verified_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
}
$verified | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $manifestPath
Write-Host "Android release APK ready: $releaseOut"
Write-Host "Android debug APK ready: $debugOut"

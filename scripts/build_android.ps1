param(
    [string]$AndroidDir = "$PSScriptRoot\..\android",
    [string]$ReleaseDir = "$PSScriptRoot\..\release",
    [switch]$AllowGeneratedSigningKey,
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

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$modelRoot = Join-Path $repoRoot "resources\models"
$modelManifest = Join-Path $modelRoot "models.manifest.json"
$androidModelFiles = @(
    (Join-Path $modelRoot "opencv\face_detection_yunet_2023mar.onnx"),
    (Join-Path $modelRoot "opencv\face_recognition_sface_2021dec.onnx"),
    (Join-Path $modelRoot "antispoof\minifasnet_v2.onnx")
)
$missingAndroidModelFiles = @($androidModelFiles | Where-Object { -not (Test-Path $_) })
if ((-not (Test-Path $modelManifest)) -or $missingAndroidModelFiles.Count -gt 0) {
    $python = Join-Path $repoRoot ".venv\Scripts\python.exe"
    if (-not (Test-Path $python)) {
        $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
        if (-not $pythonCmd) { throw "Python is required to fetch pinned Android model assets." }
        $python = $pythonCmd.Source
    }
    if ($missingAndroidModelFiles.Count -gt 0) {
        Write-Host "Missing Android model assets; fetching pinned model set:"
        $missingAndroidModelFiles | ForEach-Object { Write-Host "  - $_" }
    }
    & $python (Join-Path $PSScriptRoot "fetch_models.py")
    if ($LASTEXITCODE -ne 0) { throw "Pinned model fetch failed with exit code $LASTEXITCODE." }
}
if (-not (Test-Path $modelManifest)) {
    throw "Offline model manifest is missing after preparation: $modelManifest"
}
$missingAndroidModelFiles = @($androidModelFiles | Where-Object { -not (Test-Path $_) })
if ($missingAndroidModelFiles.Count -gt 0) {
    throw "Android model assets are missing after preparation: $($missingAndroidModelFiles -join ', ')"
}

$keyStore = $env:FFACIO_ANDROID_KEYSTORE
$storePassword = $env:FFACIO_ANDROID_KEYSTORE_PASSWORD
$keyAlias = $env:FFACIO_ANDROID_KEY_ALIAS
$keyPassword = $env:FFACIO_ANDROID_KEY_PASSWORD
$signingSource = "external environment keystore"
$usingGeneratedSigningKey = $false
if (-not $keyStore) {
    if (-not $AllowGeneratedSigningKey) {
        throw "FFACIO_ANDROID_KEYSTORE, FFACIO_ANDROID_KEYSTORE_PASSWORD, FFACIO_ANDROID_KEY_ALIAS, and FFACIO_ANDROID_KEY_PASSWORD are required for reproducible release signing. Use -AllowGeneratedSigningKey only for disposable local sideload builds."
    }
    $keyStore = Join-Path $ReleaseDir "ffacio-local-release.jks"
    if (-not $storePassword) { $storePassword = "ffacio-local-release-change-me" }
    if (-not $keyAlias) { $keyAlias = "ffacio-local-release" }
    if (-not $keyPassword) { $keyPassword = $storePassword }
    $signingSource = "generated disposable local sideload keystore"
    $usingGeneratedSigningKey = $true
}
if (-not $storePassword -or -not $keyAlias -or -not $keyPassword) {
    throw "Android release signing requires FFACIO_ANDROID_KEYSTORE_PASSWORD, FFACIO_ANDROID_KEY_ALIAS, and FFACIO_ANDROID_KEY_PASSWORD."
}

if (-not (Test-Path $keyStore)) {
    if (-not $usingGeneratedSigningKey) {
        throw "External Android keystore does not exist: $keyStore. Refusing to generate a new key for external signing provenance."
    }
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    & $keytool -genkeypair -v -keystore $keyStore -storepass $storePassword -alias $keyAlias -keypass $keyPassword -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=FFacio Local Release,O=sampple-korea,C=KR"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE." }
}
$keyStore = (Resolve-Path $keyStore).Path

Push-Location $AndroidDir
try {
    $gradleLog = Join-Path $ReleaseDir "android-gradle-verification.log"
    $gradleTasks = @("clean", "testDebugUnitTest", "testReleaseUnitTest", "lintVitalRelease", "assembleDebug", "assembleRelease")
    & $gradle --no-daemon @gradleTasks "-PffacioStoreFile=$keyStore" "-PffacioStorePassword=$storePassword" "-PffacioKeyAlias=$keyAlias" "-PffacioKeyPassword=$keyPassword" *>&1 |
        Tee-Object -FilePath $gradleLog
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
}
finally {
    Pop-Location
}

$debugApk = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
$releaseApk = Join-Path $AndroidDir "app\build\outputs\apk\release\app-release.apk"
$androidModelManifest = Join-Path $AndroidDir "app\build\generated\ffacioAssets\models\models.manifest.json"
if (-not (Test-Path $debugApk)) { throw "Debug APK was not produced: $debugApk" }
if (-not (Test-Path $releaseApk)) { throw "Release APK was not produced: $releaseApk" }
if (-not (Test-Path $androidModelManifest)) { throw "Android model manifest was not produced: $androidModelManifest" }
$debugOut = Join-Path $ReleaseDir "FFacio-Android-debug.apk"
$releaseOut = Join-Path $ReleaseDir "FFacio-Android-release.apk"
Copy-Item -Force $debugApk $debugOut
Copy-Item -Force $releaseApk $releaseOut

$debugFile = Get-Item $debugOut
$releaseFile = Get-Item $releaseOut
$gradleLogFile = Get-Item (Join-Path $ReleaseDir "android-gradle-verification.log")
$gradleLogHash = (Get-FileHash $gradleLogFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
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
    signing = $signingSource
    production_signing = (-not $usingGeneratedSigningKey)
    upgrade_compatible_with_external_signing = (-not $usingGeneratedSigningKey)
    signing_reproducibility = "APK SHA-256 is reproducible only when the same private keystore and pinned model bundle are supplied; the private key is intentionally not stored in git."
    debug_signed = $true
    gradle_tasks = $gradleTasks
    gradle_verification_log = $gradleLogFile.Name
    gradle_verification_log_sha256 = $gradleLogHash
    unit_tests_verified = $true
    lint_verified = $true
    static_verified = $false
    emulator_verified = $false
    launch_verified = $false
    model_ready_verified = $false
    emulator_report = $null
    emulator_serial = $null
    emulator_avd = $null
    emulator_boot_completed = $null
    emulator_app_pid = $null
    emulator_launch_method = $null
    verified_apk_sha256 = $null
    verified_at = $null
    notes = "Release APK signing source: $signingSource. OpenCV YuNet/SFace and MiniFASNet-V2 are bundled for Android; desktop-only InsightFace assets are not packaged. No cloud subscription is used."
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $ReleaseDir "android-release-manifest.json")
& (Join-Path $PSScriptRoot "verify_android_static.ps1") -Apk $releaseOut -Manifest (Join-Path $ReleaseDir "android-release-manifest.json") -ModelManifest $androidModelManifest
if ($LASTEXITCODE -ne 0) { throw "Android static verification failed with exit code $LASTEXITCODE." }
$manifestPath = Join-Path $ReleaseDir "android-release-manifest.json"
$verified = Get-Content -Raw $manifestPath -Encoding UTF8 | ConvertFrom-Json
$verified.static_verified = $true
if (-not $SkipEmulatorVerification) {
    $emulatorReport = Join-Path $ReleaseDir "android-emulator-verification.json"
    & (Join-Path $PSScriptRoot "verify_android_emulator.ps1") -Apk $releaseOut -Manifest $manifestPath -Report $emulatorReport
    if ($LASTEXITCODE -ne 0) { throw "Android emulator verification failed with exit code $LASTEXITCODE." }
    $emulatorEvidence = Get-Content -Raw $emulatorReport -Encoding UTF8 | ConvertFrom-Json
    $verified.emulator_verified = $true
    $verified.launch_verified = $true
    $verified.model_ready_verified = $true
    $verified.emulator_report = Split-Path -Leaf $emulatorReport
    $verified.emulator_serial = $emulatorEvidence.serial
    $verified.emulator_avd = $emulatorEvidence.avd_name
    $verified.emulator_boot_completed = $emulatorEvidence.boot_completed
    $verified.emulator_app_pid = $emulatorEvidence.app_pid
    $verified.emulator_launch_method = $emulatorEvidence.launch_method
    $verified.verified_apk_sha256 = $releaseHash
    $verified.verified_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
}
$verified | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $manifestPath
if ((-not $SkipEmulatorVerification) -and (Test-Path $emulatorReport)) {
    $finalManifestHash = (Get-FileHash $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $finalReport = Get-Content -Raw $emulatorReport -Encoding UTF8 | ConvertFrom-Json
    $finalReport.manifest_sha256 = $finalManifestHash
    $finalReport | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $emulatorReport
}
Write-Host "Android release APK ready: $releaseOut"
Write-Host "Android debug APK ready: $debugOut"

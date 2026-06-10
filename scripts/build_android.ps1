param(
    [string]$AndroidDir = "$PSScriptRoot\..\android",
    [string]$ReleaseDir = "$PSScriptRoot\..\release"
)

$ErrorActionPreference = "Stop"

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path $sdk)) { throw "Android SDK not found. Set ANDROID_HOME or install Android Studio SDK." }

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "$env:ProgramFiles\Android\Android Studio\jbr" }
if (-not (Test-Path (Join-Path $javaHome "bin\java.exe"))) { throw "Java runtime not found. Set JAVA_HOME." }

$gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin" -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $gradle) {
    throw "Gradle 8.13 was not found in the local wrapper cache. Install/sync Gradle 8.13 or run Android Studio once."
}

$env:ANDROID_HOME = (Resolve-Path $sdk).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:JAVA_HOME = (Resolve-Path $javaHome).Path

New-Item -ItemType Directory -Force $ReleaseDir | Out-Null
Push-Location $AndroidDir
try {
    & $gradle.FullName --no-daemon clean assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle assembleDebug failed with exit code $LASTEXITCODE." }
}
finally {
    Pop-Location
}

$apk = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "APK was not produced: $apk" }
$out = Join-Path $ReleaseDir "FFacio-Android-debug.apk"
Copy-Item -Force $apk $out

$file = Get-Item $out
$hash = (Get-FileHash $out -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    name = "FFacio Android"
    version = "0.1.0"
    artifact = "FFacio-Android-debug.apk"
    size = $file.Length
    sha256 = $hash
    generated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    signed = $false
    debug_signed = $true
    notes = "Debug-key signed APK for local sideload testing, not a Play/release-signed artifact. OpenCV YuNet/SFace and the shared model bundle are included; no cloud subscription is used."
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $ReleaseDir "android-release-manifest.json")
Write-Host "Android APK ready: $out"

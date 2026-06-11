param(
    [string]$Apk = "$PSScriptRoot\..\release\FFacio-Android-release.apk",
    [string]$AvdName = "FFacio_API36",
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$env:ANDROID_HOME = (Resolve-Path $sdk).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr" }

$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$emulator = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"
$avdmanager = Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin\avdmanager.bat"
if (-not (Test-Path $adb)) { throw "adb missing: $adb" }
if (-not (Test-Path $emulator)) { throw "emulator missing: $emulator" }
if (-not (Test-Path $avdmanager)) { throw "avdmanager missing: $avdmanager" }
if (-not (Test-Path $Apk)) { throw "APK missing: $Apk" }

$existing = & $avdmanager list avd | Select-String -Pattern "Name: $AvdName"
if (-not $existing) {
    "no" | & $avdmanager create avd -n $AvdName -k "system-images;android-36;google_apis_playstore;x86_64" -d "medium_phone" --force | Out-Null
}

$devices = (& $adb devices) -join "`n"
if ($devices -notmatch "emulator-\d+\s+device") {
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName, "-no-snapshot-load", "-no-audio", "-no-window", "-gpu", "swiftshader_indirect", "-no-boot-anim") -WindowStyle Hidden
}

& $adb wait-for-device
$online = $false
for ($i = 0; $i -lt 150; $i++) {
    $devices = (& $adb devices) -join "`n"
    $boot = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
    if ($devices -match "emulator-\d+\s+device" -and $boot -eq "1") {
        $online = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $online) { throw "Android emulator did not become online and booted." }

& $adb uninstall com.ffacio.mobile | Out-Null
& $adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed with exit code $LASTEXITCODE" }
& $adb logcat -c
& $adb shell pm grant com.ffacio.mobile android.permission.CAMERA 2>$null
& $adb shell am force-stop com.ffacio.mobile
$launch = (& $adb shell monkey -p com.ffacio.mobile -c android.intent.category.LAUNCHER 1) -join "`n"
$launch | Out-Host
if ($launch -notmatch "Events injected:\s*1") {
    throw "Launcher start failed.`n$launch"
}

$appPid = ""
$logs = ""
$modelsReady = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    $appPidOut = & $adb shell pidof com.ffacio.mobile 2>$null
    $appPid = if ($appPidOut) { ($appPidOut -join "").Trim() } else { "" }
    if (-not $appPid) { throw "FFacio Android process stopped during launch verification.`n$logs" }
    $logs = (& $adb logcat -d --pid=$appPid -t 500 2>$null) -join "`n"
    if ($logs -match "FATAL EXCEPTION|AndroidRuntime") { throw "App crash detected in FFacio logcat.`n$logs" }
    if ($logs -match "Offline models ready") {
        $modelsReady = $true
        break
    }
}
if (-not $modelsReady) { throw "FFacio Android did not report bundled offline model readiness within 60 seconds.`n$logs" }

Write-Host "FFacio Android emulator smoke passed with pid $appPid and bundled model readiness confirmed"
if (-not $KeepRunning) {
    & $adb emu kill 2>$null | Out-Null
}

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
& $adb shell am start -W -n com.ffacio.mobile/.MainActivity | Out-Host
Start-Sleep -Seconds 8

$appPidOut = & $adb shell pidof com.ffacio.mobile 2>$null
$appPid = if ($appPidOut) { ($appPidOut -join "").Trim() } else { "" }
$logs = (& $adb logcat -d -t 800 2>$null) -join "`n"
if ($logs -match "FATAL EXCEPTION|AndroidRuntime") { throw "App crash detected in logcat.`n$logs" }
if (-not $appPid) { throw "FFacio Android process is not running after launch." }

Write-Host "FFacio Android emulator smoke passed with pid $appPid"
if (-not $KeepRunning) {
    & $adb emu kill 2>$null | Out-Null
}

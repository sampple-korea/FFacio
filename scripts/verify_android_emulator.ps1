param(
    [string]$Apk = "$PSScriptRoot\..\release\FFacio-Android-release.apk",
    [string]$AvdName = "FFacio_API36",
    [string]$Serial = "",
    [string]$Report = "",
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
$actualAvd = ""
if (-not $Serial -and $devices -notmatch "emulator-\d+\s+device") {
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName, "-no-snapshot-load", "-no-audio", "-no-window", "-gpu", "swiftshader_indirect", "-no-boot-anim") -WindowStyle Hidden
}

& $adb wait-for-device
$online = $false
for ($i = 0; $i -lt 150; $i++) {
    $devices = (& $adb devices) -join "`n"
    $serials = @()
    foreach ($line in ($devices -split "`n")) {
        if ($line -match "^(emulator-\d+)\s+device\b") { $serials += $Matches[1] }
    }
    if ($Serial) {
        if ($serials -contains $Serial) { $serials = @($Serial) } else { $serials = @() }
    } elseif ($serials.Count -gt 1) {
        throw "Multiple emulators are online. Pass -Serial to choose one: $($serials -join ', ')"
    }
    if ($serials.Count -eq 1) {
        $Serial = $serials[0]
        $boot = (& $adb -s $Serial shell getprop sys.boot_completed 2>$null).Trim()
        $actualAvd = (& $adb -s $Serial emu avd name 2>$null | Select-Object -First 1).Trim()
        if ($boot -eq "1" -and $actualAvd -eq $AvdName) {
            $online = $true
            break
        }
    }
    Start-Sleep -Seconds 2
}
if (-not $online) { throw "Android emulator did not become online and booted." }

& $adb -s $Serial uninstall com.ffacio.mobile | Out-Null
& $adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed with exit code $LASTEXITCODE" }
& $adb -s $Serial logcat -c
& $adb -s $Serial shell pm grant com.ffacio.mobile android.permission.CAMERA 2>$null
& $adb -s $Serial shell am force-stop com.ffacio.mobile
$launch = (& $adb -s $Serial shell monkey -p com.ffacio.mobile -c android.intent.category.LAUNCHER 1) -join "`n"
$launch | Out-Host
if ($launch -notmatch "Events injected:\s*1") {
    throw "Launcher start failed.`n$launch"
}

$appPid = ""
$logs = ""
$modelsReady = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    $appPidOut = & $adb -s $Serial shell pidof com.ffacio.mobile 2>$null
    $appPid = if ($appPidOut) { ($appPidOut -join "").Trim() } else { "" }
    if (-not $appPid) { throw "FFacio Android process stopped during launch verification.`n$logs" }
    $logs = (& $adb -s $Serial logcat -d --pid=$appPid -t 500 2>$null) -join "`n"
    if ($logs -match "FATAL EXCEPTION|AndroidRuntime") { throw "App crash detected in FFacio logcat.`n$logs" }
    if ($logs -match "Offline models ready") {
        $modelsReady = $true
        break
    }
}
if (-not $modelsReady) { throw "FFacio Android did not report bundled offline model readiness within 60 seconds.`n$logs" }

Write-Host "FFacio Android emulator smoke passed with pid $appPid and bundled model readiness confirmed"
if ($Report) {
    $reportPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Report)
    $reportDir = Split-Path -Parent $reportPath
    if ($reportDir) { New-Item -ItemType Directory -Force $reportDir | Out-Null }
    [ordered]@{
        status = "passed"
        apk = (Split-Path -Leaf $Apk)
        apk_sha256 = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
        requested_avd = $AvdName
        avd_name = $actualAvd
        serial = $Serial
        app_pid = $appPid
        launch_method = "adb monkey launcher"
        model_ready_verified = $true
        verified_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        keep_running = [bool]$KeepRunning
    } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 $reportPath
}
if (-not $KeepRunning) {
    & $adb -s $Serial emu kill 2>$null | Out-Null
}

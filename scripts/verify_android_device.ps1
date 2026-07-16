param(
    [Parameter(Mandatory = $true)][string]$RuntimeApk,
    [string]$AppApk = "$PSScriptRoot\..\release\FFacio-Android-release.apk",
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb.exe missing: $adb" }
if (-not (Test-Path $RuntimeApk)) { throw "Runtime APK missing: $RuntimeApk" }
if (-not (Test-Path $AppApk)) { throw "FFacio APK missing: $AppApk" }

$target = @()
if ($Serial) { $target = @("-s", $Serial) }
& $adb @target wait-for-device
& $adb @target install -r $RuntimeApk
if ($LASTEXITCODE -ne 0) { throw "Runtime install failed." }
& $adb @target install -r $AppApk
if ($LASTEXITCODE -ne 0) { throw "FFacio install failed. Check that both APKs use the same certificate." }
& $adb @target shell pm grant com.ffacio.mobile android.permission.CAMERA 2>$null
& $adb @target logcat -c
& $adb @target shell am force-stop com.ffacio.mobile
& $adb @target shell monkey -p com.ffacio.mobile -c android.intent.category.LAUNCHER 1 | Out-Host

$ready = $false
$logs = ""
for ($i = 0; $i -lt 45; $i++) {
    Start-Sleep -Seconds 1
    $pid = ((& $adb @target shell pidof com.ffacio.mobile 2>$null) -join "").Trim()
    if (-not $pid) { throw "FFacio process stopped during launch verification." }
    $logs = (& $adb @target logcat -d --pid=$pid -t 500 2>$null) -join "`n"
    if ($logs -match "FATAL EXCEPTION|AndroidRuntime") { throw "FFacio crashed during Runtime verification.`n$logs" }
    if ($logs -match "FFacio Runtime ready") { $ready = $true; break }
}
if (-not $ready) { throw "FFacio did not reach Runtime ready state.`n$logs" }
Write-Host "Real-device Runtime integration smoke test passed."

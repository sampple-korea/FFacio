param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JavaHome = "$env:ProgramFiles\Android\Android Studio\jbr",
    [string]$CommandLineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) {
    throw "Java runtime not found. Install Android Studio or set -JavaHome."
}

New-Item -ItemType Directory -Force $SdkRoot | Out-Null
$manager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $manager)) {
    $tmp = Join-Path $env:TEMP "ffacio-commandlinetools.zip"
    $extract = Join-Path $env:TEMP ("ffacio-cmdline-tools-" + [guid]::NewGuid().ToString("N"))
    $target = Join-Path $SdkRoot "cmdline-tools\latest"
    & curl.exe -L --retry 5 --retry-delay 3 -o $tmp $CommandLineToolsUrl
    if ($LASTEXITCODE -ne 0) { throw "curl failed with exit code $LASTEXITCODE" }
    Expand-Archive -Force -Path $tmp -DestinationPath $extract
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
    Move-Item -Force (Join-Path $extract "cmdline-tools") $target
    Remove-Item -Recurse -Force $extract -ErrorAction SilentlyContinue
    Remove-Item -Force $tmp -ErrorAction SilentlyContinue
}

$env:ANDROID_HOME = (Resolve-Path $SdkRoot).Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:JAVA_HOME = (Resolve-Path $JavaHome).Path
$manager = Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin\sdkmanager.bat"

$duplicateLatest = Join-Path $env:ANDROID_HOME "cmdline-tools\latest-2"
if (Test-Path $duplicateLatest) {
    Remove-Item -Recurse -Force $duplicateLatest -ErrorAction SilentlyContinue
}

& $manager --install "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0" "system-images;android-36;google_apis_playstore;x86_64"
if ($LASTEXITCODE -ne 0) { throw "sdkmanager install failed with exit code $LASTEXITCODE" }

"y`n" * 20 | & $manager --licenses | Out-Null

Write-Host "Android dependencies are installed under $env:ANDROID_HOME"

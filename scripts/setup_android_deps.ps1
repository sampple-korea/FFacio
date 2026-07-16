param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JavaHome = "$env:ProgramFiles\Android\Android Studio\jbr",
    [string]$CommandLineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path (Join-Path $JavaHome "bin\java.exe"))) { throw "JDK not found. Install Android Studio or set -JavaHome." }
New-Item -ItemType Directory -Force $SdkRoot | Out-Null
$manager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $manager)) {
    $tmp = Join-Path $env:TEMP "ffacio-commandlinetools.zip"
    $extract = Join-Path $env:TEMP ("ffacio-cmdline-tools-" + [guid]::NewGuid().ToString("N"))
    & curl.exe -L --retry 5 --retry-delay 3 -o $tmp $CommandLineToolsUrl
    if ($LASTEXITCODE -ne 0) { throw "Android command-line tools download failed." }
    Expand-Archive -Force -Path $tmp -DestinationPath $extract
    $target = Join-Path $SdkRoot "cmdline-tools\latest"
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
& $manager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"
if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed." }
"y`n" * 20 | & $manager --licenses | Out-Null
Write-Host "Android build dependencies installed under $env:ANDROID_HOME"

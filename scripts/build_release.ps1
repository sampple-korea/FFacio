param(
    [switch]$InstallInno,
    [switch]$VerifyInstaller
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

function Invoke-FFacioSmoke([string]$Exe, [string]$Arguments, [int]$TimeoutSeconds = 90) {
    $resolvedExe = (Resolve-Path $Exe).Path
    $process = Start-Process -FilePath $resolvedExe -ArgumentList $Arguments -PassThru
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "Packaged FFacio smoke test timed out after $TimeoutSeconds seconds: $Arguments"
    }
    if ($process.ExitCode -ne 0) {
        throw "Packaged FFacio smoke test failed with exit code $($process.ExitCode): $Arguments"
    }
    Start-Sleep -Milliseconds 300
    $leaf = Split-Path -Leaf $resolvedExe
    $lingering = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq $leaf -and $_.ExecutablePath -eq $resolvedExe -and $_.CommandLine -like "*--smoke-test*"
    }
    if ($lingering) {
        $lingering | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        throw "Packaged FFacio smoke left lingering process(es): $Arguments"
    }
}

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    python -m venv .venv
}

.\.venv\Scripts\python.exe -m pip install --upgrade pip==26.1.2
.\.venv\Scripts\python.exe -m pip uninstall -y opencv-python opencv-python-headless opencv-contrib-python
.\.venv\Scripts\python.exe -m pip install -r requirements.txt pyinstaller==6.20.0 pyinstaller-hooks-contrib==2026.6
.\.venv\Scripts\python.exe -m pip uninstall -y opencv-python opencv-python-headless
.\.venv\Scripts\python.exe -m pip install --force-reinstall --no-deps opencv-contrib-python==4.13.0.92

.\scripts\prepare_models.ps1

Remove-Item -Recurse -Force dist, build -ErrorAction SilentlyContinue
.\.venv\Scripts\python.exe -m PyInstaller --clean --noconfirm FFacio.spec

$distExe = Join-Path $PWD "dist\FFacio\FFacio.exe"
$manifest = Get-ChildItem -Path "dist\FFacio" -Recurse -Filter "models.manifest.json" -ErrorAction SilentlyContinue | Select-Object -First 1
$qwindows = Get-ChildItem -Path "dist\FFacio" -Recurse -Filter "qwindows.dll" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not (Test-Path $distExe)) { throw "PyInstaller output missing: $distExe" }
if (-not $manifest) { throw "Bundled model manifest missing in dist\FFacio." }
if (-not $qwindows) { throw "PySide6 qwindows.dll was not bundled." }

$smokeCases = @(
    "--smoke-test --strict-insightface",
    "--smoke-test --strict-opencv",
    "--smoke-test --strict-insightface --ui-smoke --offscreen",
    "--smoke-test --strict-opencv --ui-smoke --runtime-smoke --offscreen"
)
foreach ($args in $smokeCases) {
    Invoke-FFacioSmoke $distExe $args
}

$iscc = Get-Command iscc.exe -ErrorAction SilentlyContinue
if (-not $iscc) {
    $common = @(
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe"
    )
    foreach ($candidate in $common) {
        if (Test-Path $candidate) {
            $iscc = Get-Item $candidate
            break
        }
    }
}

if (-not $iscc -and $InstallInno) {
    $winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if (-not $winget) { throw "Inno Setup is missing and winget is not available." }
    winget install --id JRSoftware.InnoSetup -e --accept-source-agreements --accept-package-agreements
    $iscc = Get-Command iscc.exe -ErrorAction SilentlyContinue
    if (-not $iscc) {
        foreach ($candidate in $common) {
            if (Test-Path $candidate) {
                $iscc = Get-Item $candidate
                break
            }
        }
    }
}

if (-not $iscc) {
    throw "Inno Setup compiler (ISCC.exe) is missing. Install Inno Setup 6 or rerun with -InstallInno."
}

New-Item -ItemType Directory -Force release | Out-Null
$isccPath = if ($iscc.Source) { $iscc.Source } else { $iscc.FullName }
$setupPath = Join-Path $PWD "release\FFacio-Setup.exe"
Remove-Item -Force $setupPath -ErrorAction SilentlyContinue
$isccStartedAt = Get-Date
& $isccPath "installer\FFacio.iss"
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup compiler failed with exit code $LASTEXITCODE."
}
if (-not (Test-Path $setupPath)) {
    throw "Inno Setup did not produce expected installer: $setupPath"
}
$setupFile = Get-Item $setupPath
if ($setupFile.LastWriteTime -lt $isccStartedAt) {
    throw "Installer timestamp predates current Inno compile: $setupPath"
}
.\.venv\Scripts\python.exe scripts\write_release_manifest.py
.\scripts\verify_release_static.ps1

if ($VerifyInstaller) {
    .\scripts\verify_installer.ps1
    Write-Host "Release ready: release\FFacio-Setup.exe"
} else {
    Write-Host "Installer verification not run. For release acceptance, rerun elevated with -VerifyInstaller or run scripts\verify_installer.ps1."
    Write-Host "Build artifact ready: release\FFacio-Setup.exe"
}

param(
    [string]$Installer = "$PSScriptRoot\..\release\FFacio-Setup.exe",
    [string]$InstallDir = "$env:TEMP\FFacioInstallSmoke"
)

$ErrorActionPreference = "Stop"

function Invoke-FFacioSmoke([string]$Exe, [string]$Arguments, [int]$TimeoutSeconds = 90) {
    $resolvedExe = (Resolve-Path $Exe).Path
    $process = Start-Process -FilePath $resolvedExe -ArgumentList $Arguments -PassThru
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "Installed FFacio smoke timed out after $TimeoutSeconds seconds: $Arguments"
    }
    if ($process.ExitCode -ne 0) {
        throw "Installed FFacio smoke test failed with exit code $($process.ExitCode): $Arguments"
    }
    Start-Sleep -Milliseconds 300
    $leaf = Split-Path -Leaf $resolvedExe
    $lingering = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq $leaf -and $_.ExecutablePath -eq $resolvedExe -and $_.CommandLine -like "*--smoke-test*"
    }
    if ($lingering) {
        $lingering | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        throw "Installed FFacio smoke left lingering process(es): $Arguments"
    }
}

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Installer verification requires an elevated PowerShell session because FFacio is a per-machine install."
}

$installerPath = Resolve-Path $Installer
$resolvedTemp = [System.IO.Path]::GetFullPath($env:TEMP)
$resolvedTempWithSlash = $resolvedTemp.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$resolvedInstallDir = [System.IO.Path]::GetFullPath($InstallDir)
if (-not $resolvedInstallDir.StartsWith($resolvedTempWithSlash, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to use install smoke directory outside TEMP: $resolvedInstallDir"
}
if ((Split-Path -Leaf $resolvedInstallDir) -notlike "FFacioInstallSmoke*") {
    throw "Install smoke directory must be a dedicated FFacioInstallSmoke* folder: $resolvedInstallDir"
}
if (Test-Path $InstallDir) {
    Remove-Item -Recurse -Force $InstallDir
}

$installArgs = @(
    "/VERYSILENT",
    "/SUPPRESSMSGBOXES",
    "/NORESTART",
    "/DIR=$InstallDir"
)
$installed = $false
try {
    $install = Start-Process -FilePath $installerPath -ArgumentList $installArgs -PassThru -Wait
    if ($install.ExitCode -ne 0) {
        throw "Installer failed with exit code $($install.ExitCode)."
    }
    $installed = $true

    $exe = Join-Path $InstallDir "FFacio.exe"
    if (-not (Test-Path $exe)) {
        throw "Installed FFacio.exe is missing: $exe"
    }

    $smokeCases = @(
    "--smoke-test --strict-insightface",
    "--smoke-test --strict-opencv",
    "--smoke-test --strict-insightface --ui-smoke --offscreen",
    "--smoke-test --strict-opencv --ui-smoke --runtime-smoke --offscreen"
)
    foreach ($args in $smokeCases) {
        Invoke-FFacioSmoke $exe $args
    }
}
finally {
    $uninstaller = Join-Path $InstallDir "unins000.exe"
    if ($installed) {
        if (-not (Test-Path $uninstaller)) {
            throw "Uninstaller is missing: $uninstaller"
        }
        $uninstall = Start-Process -FilePath $uninstaller -ArgumentList "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART" -PassThru -Wait
        if ($uninstall.ExitCode -ne 0) {
            throw "Uninstaller failed with exit code $($uninstall.ExitCode)."
        }
    }
    if (Test-Path (Join-Path $InstallDir "FFacio.exe")) {
        throw "Uninstall left FFacio.exe behind in $InstallDir"
    }
}

$manifest = Join-Path (Split-Path -Parent $installerPath) "release-manifest.json"
if (Test-Path $manifest) {
    $json = Get-Content -Raw $manifest | ConvertFrom-Json
    $testedFile = Get-Item $installerPath
    $testedHash = (Get-FileHash $installerPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($json.artifact -ne (Split-Path -Leaf $installerPath)) {
        throw "Manifest artifact does not match tested installer: $($json.artifact)"
    }
    if ([int64]$json.size -ne [int64]$testedFile.Length) {
        throw "Manifest size does not match tested installer."
    }
    if ([string]$json.sha256 -ne $testedHash) {
        throw "Manifest SHA-256 does not match tested installer."
    }
    $json.installer_verified = $true
    $json.installer_verified_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $json | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $manifest
}

Write-Host "Installer verification passed."

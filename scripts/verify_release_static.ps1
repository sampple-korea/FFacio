param(
    [string]$DistDir = "$PSScriptRoot\..\dist\FFacio",
    [string]$Installer = "$PSScriptRoot\..\release\FFacio-Setup.exe",
    [string]$Manifest = "$PSScriptRoot\..\release\release-manifest.json",
    [switch]$SkipSmoke
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredPath([string]$PathValue, [string]$Label) {
    if (-not (Test-Path $PathValue)) {
        throw "$Label is missing: $PathValue"
    }
    return (Resolve-Path $PathValue).Path
}

function Assert-AnyFile([string]$Root, [string]$Filter, [string]$Label) {
    $match = Get-ChildItem -Path $Root -Recurse -File -Filter $Filter -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $match) {
        throw "$Label was not found under $Root"
    }
    if ($match.Length -le 0) {
        throw "$Label is empty: $($match.FullName)"
    }
    return $match.FullName
}

function Set-StaticVerification([object]$ManifestJson, [string]$ManifestPath, [bool]$Verified) {
    $ManifestJson | Add-Member -NotePropertyName static_verified -NotePropertyValue $Verified -Force
    $verifiedAt = $null
    if ($Verified) {
        $verifiedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    }
    $ManifestJson | Add-Member -NotePropertyName static_verified_at -NotePropertyValue $verifiedAt -Force
    $ManifestJson | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $ManifestPath
}

function Invoke-FFacioSmoke([string]$Exe, [string]$Arguments, [int]$TimeoutSeconds = 90) {
    $resolvedExe = (Resolve-Path $Exe).Path
    $leaf = Split-Path -Leaf $resolvedExe
    Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq $leaf -and $_.ExecutablePath -eq $resolvedExe -and $_.CommandLine -like "*--smoke-test*"
    } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

    $process = Start-Process -FilePath $resolvedExe -ArgumentList $Arguments -PassThru
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "Packaged smoke timed out after $TimeoutSeconds seconds: $Arguments"
    }
    if ($process.ExitCode -ne 0) {
        throw "Packaged smoke failed with exit code $($process.ExitCode): $Arguments"
    }
    $deadline = (Get-Date).AddSeconds(5)
    do {
        Start-Sleep -Milliseconds 250
        $lingering = Get-CimInstance Win32_Process | Where-Object {
            $_.Name -eq $leaf -and $_.ExecutablePath -eq $resolvedExe -and $_.CommandLine -like "*--smoke-test*"
        }
        if (-not $lingering) {
            return
        }
    } while ((Get-Date) -lt $deadline)

    $lingering = Get-CimInstance Win32_Process | Where-Object {
        $_.Name -eq $leaf -and $_.ExecutablePath -eq $resolvedExe -and $_.CommandLine -like "*--smoke-test*"
    }
    if ($lingering) {
        $lingering | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        throw "Packaged smoke left lingering process(es): $Arguments"
    }
}

$dist = Resolve-RequiredPath $DistDir "PyInstaller dist directory"
$installerPath = Resolve-RequiredPath $Installer "Installer"
$manifestPath = Resolve-RequiredPath $Manifest "Release manifest"
$distExe = Resolve-RequiredPath (Join-Path $dist "FFacio.exe") "Packaged executable"

$manifestJson = Get-Content -Raw $manifestPath -Encoding UTF8 | ConvertFrom-Json
Set-StaticVerification $manifestJson $manifestPath $false
$installerFile = Get-Item $installerPath
$installerHash = (Get-FileHash $installerPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($manifestJson.artifact -ne (Split-Path -Leaf $installerPath)) {
    throw "Manifest artifact '$($manifestJson.artifact)' does not match installer '$((Split-Path -Leaf $installerPath))'."
}
if ([int64]$manifestJson.size -ne [int64]$installerFile.Length) {
    throw "Manifest size $($manifestJson.size) does not match installer size $($installerFile.Length)."
}
if ([string]$manifestJson.sha256 -ne $installerHash) {
    throw "Manifest SHA-256 $($manifestJson.sha256) does not match installer SHA-256 $installerHash."
}

Assert-AnyFile $dist "qwindows.dll" "PySide6 qwindows.dll" | Out-Null
Assert-AnyFile $dist "cv2.pyd" "OpenCV cv2.pyd" | Out-Null
Assert-AnyFile $dist "onnxruntime.dll" "ONNX Runtime DLL" | Out-Null
$modelManifest = Assert-AnyFile $dist "models.manifest.json" "Bundled model manifest"

$modelRoot = Split-Path -Parent $modelManifest
$modelJson = Get-Content -Raw $modelManifest -Encoding UTF8 | ConvertFrom-Json
$requiredIds = @(
    "opencv.yunet",
    "opencv.sface",
    "insightface.detector",
    "insightface.recognition",
    "insightface.landmark3d",
    "insightface.landmark2d",
    "insightface.genderage"
)
$ids = @($modelJson.files | ForEach-Object { $_.id })
foreach ($id in $requiredIds) {
    if ($ids -notcontains $id) {
        throw "Bundled model manifest is missing required id: $id"
    }
}
foreach ($item in $modelJson.files) {
    $path = Join-Path $modelRoot ([string]$item.path)
    if (-not (Test-Path $path)) {
        throw "Bundled model is missing: $($item.path)"
    }
    $file = Get-Item $path
    if ([int64]$item.size -ne [int64]$file.Length) {
        throw "Bundled model size mismatch for $($item.path)."
    }
    $hash = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([string]$item.sha256 -ne $hash) {
        throw "Bundled model hash mismatch for $($item.path)."
    }
}

if (-not $SkipSmoke) {
    $oldLocalAppData = $env:LOCALAPPDATA
    $oldUserProfile = $env:USERPROFILE
    $smokeRoot = Join-Path $env:TEMP ("FFacioStaticSmoke-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force $smokeRoot | Out-Null
    try {
        $env:LOCALAPPDATA = $smokeRoot
        $env:USERPROFILE = $smokeRoot
        $smokeCases = @(
            "--smoke-test --strict-insightface",
            "--smoke-test --strict-opencv",
            "--smoke-test --strict-insightface --ui-smoke --offscreen",
            "--smoke-test --strict-opencv --ui-smoke --runtime-smoke --offscreen"
        )
        foreach ($args in $smokeCases) {
            Invoke-FFacioSmoke $distExe $args
        }
        $insightfaceCache = Join-Path $smokeRoot ".insightface"
        if (Test-Path $insightfaceCache) {
            throw "Packaged smoke created an InsightFace user cache: $insightfaceCache"
        }
    }
    finally {
        $env:LOCALAPPDATA = $oldLocalAppData
        $env:USERPROFILE = $oldUserProfile
        Remove-Item -Recurse -Force $smokeRoot -ErrorAction SilentlyContinue
    }
}

Set-StaticVerification $manifestJson $manifestPath $true

Write-Host "Static release verification passed."

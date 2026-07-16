param(
    [string]$Apk = "$PSScriptRoot\..\release\FFacio-Android-release.apk",
    [string]$Manifest = "$PSScriptRoot\..\release\android-release-manifest.json",
    [string]$RuntimeApk = ""
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $Apk)) { throw "Android APK missing: $Apk" }
if (-not (Test-Path $Manifest)) { throw "Release manifest missing: $Manifest" }

$apkPath = (Resolve-Path $Apk).Path
$manifestPath = (Resolve-Path $Manifest).Path
$json = Get-Content -Raw -Encoding UTF8 $manifestPath | ConvertFrom-Json
$file = Get-Item $apkPath
$hash = (Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($json.artifact -ne (Split-Path -Leaf $apkPath)) { throw "Manifest artifact name mismatch." }
if ([int64]$json.size -ne [int64]$file.Length) { throw "Manifest APK size mismatch." }
if ([string]$json.sha256 -ne $hash) { throw "Manifest APK hash mismatch." }
if (-not [bool]$json.signed) { throw "Release manifest does not mark the APK signed." }
if ([string]$json.runtime_package -ne "com.kbyai.faceattribute") { throw "Runtime package contract is missing from release manifest." }
if ([bool]$json.legacy_models_bundled) { throw "Release manifest claims legacy models are bundled." }

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$apksigner = Get-ChildItem (Join-Path $sdk "build-tools") -Recurse -Filter apksigner.bat -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $apksigner) { throw "apksigner.bat is required for verification." }

function Get-SignerSha256([string]$Path) {
    $result = (& $apksigner.FullName verify --print-certs $Path) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $Path" }
    if ($result -notmatch "SHA-256 digest:\s*([0-9A-Fa-f:]+)") { throw "Could not read signer digest: $Path" }
    return $Matches[1].Replace(":", "").ToLowerInvariant()
}

$appSigner = Get-SignerSha256 $apkPath
if ($json.signer_cert_sha256 -and ([string]$json.signer_cert_sha256).ToLowerInvariant() -ne $appSigner) {
    throw "Signer certificate does not match release manifest."
}
if ($RuntimeApk) {
    if (-not (Test-Path $RuntimeApk)) { throw "Runtime APK missing: $RuntimeApk" }
    $runtimeSigner = Get-SignerSha256 (Resolve-Path $RuntimeApk).Path
    if ($runtimeSigner -ne $appSigner) { throw "FFacio and Runtime signer certificates differ." }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    $entries = @{}
    foreach ($entry in $zip.Entries) { $entries[$entry.FullName] = $entry }
    if (-not $entries.ContainsKey("AndroidManifest.xml")) { throw "APK is missing required entry: AndroidManifest.xml" }
    $dexEntries = @($zip.Entries | Where-Object { $_.FullName -match '^classes([0-9]+)?\.dex$' })
    if ($dexEntries.Count -eq 0) { throw "APK does not contain any classes*.dex entry." }
    $legacyAssets = @($entries.Keys | Where-Object { $_ -like "assets/models/*" })
    if ($legacyAssets.Count -gt 0) { throw "APK still contains legacy model assets: $($legacyAssets[0])" }

    $dexTexts = foreach ($dexEntry in $dexEntries) {
        $dex = $dexEntry.Open()
        $memory = $null
        try {
            $memory = New-Object System.IO.MemoryStream
            $dex.CopyTo($memory)
            [System.Text.Encoding]::ASCII.GetString($memory.ToArray())
        }
        finally {
            $dex.Dispose()
            if ($memory) { $memory.Dispose() }
        }
    }
    foreach ($forbidden in @("ai/onnxruntime", "org/opencv", "w600k_r50.onnx", "minifasnet_v2.onnx", "face_detection_yunet")) {
        if ($dexTexts | Where-Object { $_.Contains($forbidden) }) {
            throw "Legacy engine marker remains in classes*.dex: $forbidden"
        }
    }
    foreach ($requiredMarker in @("io/ffacio/sdk/FFacioRuntimeClient", "com/kbyai/facesdk/FaceSDK")) {
        if (-not ($dexTexts | Where-Object { $_.Contains($requiredMarker) })) {
            throw "Runtime client marker is missing from classes*.dex: $requiredMarker"
        }
    }
}
finally {
    $zip.Dispose()
}

Write-Host "Android Runtime-client static verification passed: $apkPath"

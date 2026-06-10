param(
    [string]$Apk = "$PSScriptRoot\..\release\FFacio-Android-debug.apk",
    [string]$Manifest = "$PSScriptRoot\..\release\android-release-manifest.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Apk)) { throw "Android APK missing: $Apk" }
if (-not (Test-Path $Manifest)) { throw "Android release manifest missing: $Manifest" }

$apkPath = (Resolve-Path $Apk).Path
$manifestPath = (Resolve-Path $Manifest).Path
$json = Get-Content -Raw $manifestPath -Encoding UTF8 | ConvertFrom-Json
$file = Get-Item $apkPath
$hash = (Get-FileHash $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()

if ($json.artifact -ne (Split-Path -Leaf $apkPath)) {
    throw "Manifest artifact '$($json.artifact)' does not match APK '$((Split-Path -Leaf $apkPath))'."
}
if ([int64]$json.size -ne [int64]$file.Length) {
    throw "Manifest size $($json.size) does not match APK size $($file.Length)."
}
if ([string]$json.sha256 -ne $hash) {
    throw "Manifest SHA-256 $($json.sha256) does not match APK SHA-256 $hash."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    $entries = @($zip.Entries | ForEach-Object { $_.FullName })
    $required = @(
        "assets/models/models.manifest.json",
        "assets/models/opencv/face_detection_yunet_2023mar.onnx",
        "assets/models/opencv/face_recognition_sface_2021dec.onnx",
        "assets/models/insightface/models/buffalo_l/w600k_r50.onnx",
        "classes.dex",
        "AndroidManifest.xml"
    )
    foreach ($entry in $required) {
        if ($entries -notcontains $entry) {
            throw "APK is missing required entry: $entry"
        }
    }
    $modelEntries = @($zip.Entries | Where-Object { $_.FullName -like "assets/models/*.json" -or $_.FullName -like "assets/models/*.onnx" -or $_.FullName -like "assets/models/*/*.onnx" -or $_.FullName -like "assets/models/*/*/*/*.onnx" })
    if (($modelEntries | Measure-Object Length -Sum).Sum -lt 200MB) {
        throw "APK bundled model payload is unexpectedly small."
    }
}
finally {
    $zip.Dispose()
}

Write-Host "Android static verification passed."

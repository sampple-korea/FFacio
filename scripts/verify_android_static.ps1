param(
    [string]$Apk = "$PSScriptRoot\..\release\FFacio-Android-release.apk",
    [string]$Manifest = "$PSScriptRoot\..\release\android-release-manifest.json",
    [string]$ModelManifest = "$PSScriptRoot\..\resources\models\models.manifest.json",
    [switch]$AllowStaleSourceState
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Apk)) { throw "Android APK missing: $Apk" }
if (-not (Test-Path $Manifest)) { throw "Android release manifest missing: $Manifest" }
if (-not (Test-Path $ModelManifest)) { throw "Model manifest missing: $ModelManifest" }

$apkPath = (Resolve-Path $Apk).Path
$manifestPath = (Resolve-Path $Manifest).Path
$modelManifestPath = (Resolve-Path $ModelManifest).Path
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
if (-not [bool]$json.signed) {
    throw "Android manifest does not mark the release APK as signed."
}
if (-not $AllowStaleSourceState) {
    $repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
    $currentCommit = (& git -C $repoRoot rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -eq 0 -and $json.git_commit -and ([string]$json.git_commit).Trim() -ne ([string]$currentCommit).Trim()) {
        throw "Manifest git_commit $($json.git_commit) does not match current HEAD $currentCommit."
    }
    $status = @(& git -C $repoRoot status --porcelain 2>$null)
    if ($LASTEXITCODE -eq 0 -and $json.PSObject.Properties.Name -contains "git_dirty") {
        $manifestRelative = (Resolve-Path $manifestPath).Path.Substring((Resolve-Path $repoRoot).Path.Length).TrimStart("\", "/").Replace("\", "/")
        $statusComparable = @(
            $status |
                ForEach-Object { [string]$_ } |
                Where-Object { (-not $_.Replace("\", "/").EndsWith(" $manifestRelative")) -and ($_.Replace("\", "/") -ne "?? $manifestRelative") }
        )
        $dirty = $statusComparable.Count -gt 0
        if ([bool]$json.git_dirty -ne $dirty) {
            throw "Manifest git_dirty=$($json.git_dirty) does not match current dirty state $dirty."
        }
        if ($json.PSObject.Properties.Name -contains "git_dirty_paths") {
            $manifestDirtyPaths = @(
                $json.git_dirty_paths |
                    ForEach-Object { [string]$_ } |
                    Where-Object { (-not $_.Replace("\", "/").EndsWith(" $manifestRelative")) -and ($_.Replace("\", "/") -ne "?? $manifestRelative") }
            )
            $expected = @($manifestDirtyPaths | Sort-Object)
            $actual = @($statusComparable | Sort-Object)
            if ($expected.Count -ne $actual.Count) {
                throw "Manifest git_dirty_paths count $($expected.Count) does not match current dirty path count $($actual.Count). Manifest=[$($expected -join '; ')] Current=[$($actual -join '; ')]"
            }
            for ($i = 0; $i -lt $expected.Count; $i++) {
                if ($expected[$i] -ne $actual[$i]) {
                    throw "Manifest git_dirty_paths do not match current dirty paths. Manifest=[$($expected -join '; ')] Current=[$($actual -join '; ')]"
                }
            }
        }
    }
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "$env:ProgramFiles\Android\Android Studio\jbr" }
if (Test-Path (Join-Path $javaHome "bin\java.exe")) {
    $env:JAVA_HOME = (Resolve-Path $javaHome).Path
    $env:PATH = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:PATH
}
$apksigner = Get-ChildItem (Join-Path $sdk "build-tools") -Recurse -Filter apksigner.bat -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1
if (-not $apksigner) {
    throw "apksigner.bat is required for release verification. Install Android build-tools."
}
$certOutput = (& $apksigner.FullName verify --print-certs $apkPath) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "apksigner verification failed with exit code $LASTEXITCODE." }
$certDigest = $null
if ($certOutput -match "SHA-256 digest:\s*([0-9A-Fa-f:]+)") {
    $certDigest = $Matches[1].Replace(":", "").ToLowerInvariant()
}
if (-not $certDigest) { throw "Could not read signer SHA-256 certificate digest from apksigner output." }
if ($json.signer_cert_sha256 -and ([string]$json.signer_cert_sha256).ToLowerInvariant() -ne $certDigest) {
    throw "Signer certificate digest $certDigest does not match manifest $($json.signer_cert_sha256)."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$sha = [System.Security.Cryptography.SHA256]::Create()
$zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    $entries = @{}
    foreach ($entry in $zip.Entries) { $entries[$entry.FullName] = $entry }
    $required = @(
        "assets/models/models.manifest.json",
        "assets/models/opencv/face_detection_yunet_2023mar.onnx",
        "assets/models/opencv/face_recognition_sface_2021dec.onnx",
        "assets/models/antispoof/minifasnet_v2.onnx",
        "assets/models/insightface/models/buffalo_l/w600k_r50.onnx",
        "classes.dex",
        "AndroidManifest.xml"
    )
    foreach ($entry in $required) {
        if (-not $entries.ContainsKey($entry)) {
            throw "APK is missing required entry: $entry"
        }
    }

    $embeddedManifest = $entries["assets/models/models.manifest.json"]
    $embeddedManifestStream = $embeddedManifest.Open()
    try {
        $embeddedManifestHash = [System.BitConverter]::ToString($sha.ComputeHash($embeddedManifestStream)).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $embeddedManifestStream.Dispose()
    }
    $sourceManifestHash = (Get-FileHash $modelManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($embeddedManifestHash -ne $sourceManifestHash) {
        throw "Embedded models.manifest.json SHA-256 $embeddedManifestHash does not match source manifest $sourceManifestHash."
    }

    $modelManifestJson = Get-Content -Raw $modelManifestPath -Encoding UTF8 | ConvertFrom-Json
    foreach ($model in $modelManifestJson.files) {
        $entryName = "assets/models/$($model.path)"
        if (-not $entries.ContainsKey($entryName)) { throw "APK is missing bundled model: $entryName" }
        $entry = $entries[$entryName]
        if ([int64]$entry.Length -ne [int64]$model.size) {
            throw "Model size mismatch for $entryName. APK=$($entry.Length), manifest=$($model.size)."
        }
        $stream = $entry.Open()
        try {
            $entryHash = [System.BitConverter]::ToString($sha.ComputeHash($stream)).Replace("-", "").ToLowerInvariant()
            if ($entryHash -ne [string]$model.sha256) {
                throw "Model SHA-256 mismatch for $entryName. APK=$entryHash, manifest=$($model.sha256)."
            }
        }
        finally {
            $stream.Dispose()
        }
    }
}
finally {
    $zip.Dispose()
    $sha.Dispose()
}

Write-Host "Android static verification passed: $apkPath"

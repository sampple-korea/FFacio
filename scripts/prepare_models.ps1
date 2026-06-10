$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

$resourceRoot = Join-Path $PWD "resources\models"
$opencvRoot = Join-Path $resourceRoot "opencv"
$insightRoot = Join-Path $resourceRoot "insightface\models\buffalo_l"
New-Item -ItemType Directory -Force -Path $opencvRoot | Out-Null
New-Item -ItemType Directory -Force -Path $insightRoot | Out-Null

$opencvSource = Join-Path $PWD "models"
$insightSource = Join-Path $env:USERPROFILE ".insightface\models\buffalo_l"

$requiredOpenCv = @(
    "face_detection_yunet_2023mar.onnx",
    "face_recognition_sface_2021dec.onnx"
)
$requiredInsight = @(
    "det_10g.onnx",
    "w600k_r50.onnx",
    "1k3d68.onnx",
    "2d106det.onnx",
    "genderage.onnx"
)

foreach ($file in $requiredOpenCv) {
    $source = Join-Path $opencvSource $file
    if (-not (Test-Path $source)) {
        throw "Missing OpenCV model: $source"
    }
    Copy-Item -Force $source (Join-Path $opencvRoot $file)
}

foreach ($file in $requiredInsight) {
    $source = Join-Path $insightSource $file
    if (-not (Test-Path $source)) {
        throw "Missing InsightFace buffalo_l model: $source. Run the app once with InsightFace installed or download buffalo_l before building."
    }
    Copy-Item -Force $source (Join-Path $insightRoot $file)
}

.\.venv\Scripts\python.exe scripts\generate_model_manifest.py --root $resourceRoot
Write-Host "Prepared offline model resources at $resourceRoot"


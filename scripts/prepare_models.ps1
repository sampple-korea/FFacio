$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

$python = Join-Path $PWD ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    $python = "python"
}
& $python scripts\fetch_models.py
if ($LASTEXITCODE -ne 0) {
    throw "Model preparation failed with exit code $LASTEXITCODE."
}
$resourceRoot = Join-Path $PWD "resources\models"
Write-Host "Prepared offline model resources at $resourceRoot"

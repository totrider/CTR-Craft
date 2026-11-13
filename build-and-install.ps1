# Builds Craft Team Racing and copies the jar into the ATLauncher test instance.
# Run it from anywhere:  powershell -ExecutionPolicy Bypass -File "H:\CTR Craft\build-and-install.ps1"
# (or just double-click build-and-install.bat)

$ErrorActionPreference = "Stop"

# ---- settings -------------------------------------------------------------
$Instances = @(
    "F:\ATLauncher\instances\CTRCraft",
    "F:\ATLauncher\instances\CTRCraftMP"
)
# ---------------------------------------------------------------------------

Set-Location $PSScriptRoot

$javaw = Get-Process javaw -ErrorAction SilentlyContinue
if ($javaw) {
    Write-Host "Warning: Java (javaw) is running. If Minecraft is open, close it first or the copy may fail." -ForegroundColor Yellow
}

# Model and sound generators (tools\*.py): the kart model from blockbench/kart/kart.bbmodel, the synthesised placeholder sounds.
# They need Python 3 with Pillow (kart texture) and numpy + Krita (sounds, see tools/make_sounds.py); if Python is missing the
# committed outputs under src/main/resources are used as they are.
if (Get-Command python -ErrorAction SilentlyContinue) {
    Write-Host "Generating model and sounds..." -ForegroundColor Cyan
    cmd /c "python tools\make_kart.py 2>&1"
    cmd /c "python tools\make_boost_pad.py 2>&1"
    cmd /c "python tools\make_track_items.py 2>&1"
    cmd /c "python tools\make_pickup_spots.py 2>&1"
    cmd /c "python tools\make_kart_fx.py 2>&1"
    cmd /c "python tools\make_pickup_models.py 2>&1"
    cmd /c "python tools\make_power_icons.py 2>&1"
    cmd /c "python tools\make_track_overlays.py 2>&1"
    cmd /c "python tools\make_sounds.py 2>&1"
}

Write-Host "Building..." -ForegroundColor Cyan
cmd /c ".\gradlew.bat build --no-daemon 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBUILD FAILED - nothing was copied." -ForegroundColor Red
    exit 1
}

$jar = Get-ChildItem "build\libs\*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    Write-Host "No jar found in build\libs." -ForegroundColor Red
    exit 1
}

$installed = 0
foreach ($instance in $Instances) {
    $mods = Join-Path $instance "mods"
    if (-not (Test-Path $mods)) {
        Write-Host "Skipping $instance (no mods folder)" -ForegroundColor Yellow
        continue
    }
    # remove older ctrcraft jars so two versions never load together
    Get-ChildItem $mods -Filter "ctrcraft-*.jar" | Remove-Item -Force
    Copy-Item $jar.FullName $mods -Force
    Write-Host "`nInstalled $($jar.Name) -> $mods" -ForegroundColor Green
    $installed++
}
if ($installed -eq 0) {
    Write-Host "No instance found: edit `$Instances at the top of this script." -ForegroundColor Red
    exit 1
}
Write-Host "Launch the instance to test."

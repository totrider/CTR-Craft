@echo off
rem Opens a local web page listing the textures on YOUR OWN disc image (see tools\browse_ctr_assets.py). Local only, never commit or share it.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location '%~dp0'; python tools\browse_ctr_assets.py --open"
echo.
pause

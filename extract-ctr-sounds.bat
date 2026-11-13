@echo off
rem Extracts CTR's original kart sounds from YOUR OWN disc image into a local resource pack (see tools\extract_ctr_sounds.py)
rem and copies it into both test instances' resourcepacks folders. The pack stays on this machine (git-ignored, never in the jar).
powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location '%~dp0'; python tools\extract_ctr_sounds.py --install 'F:\ATLauncher\instances\CTRCraft\resourcepacks'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location '%~dp0'; python tools\extract_ctr_sounds.py --install 'F:\ATLauncher\instances\CTRCraftMP\resourcepacks'"
echo.
pause

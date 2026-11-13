@echo off
rem Extracts CTR's race jingles from YOUR OWN raw disc image into a local resource pack (see tools\extract_ctr_music.py, docs\MUSIC.md)
rem and copies it into both test instances' resourcepacks folders. The pack stays on this machine (git-ignored, never in the jar).
powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location '%~dp0'; python tools\extract_ctr_music.py --install 'F:\ATLauncher\instances\CTRCraft\resourcepacks' --install 'F:\ATLauncher\instances\CTRCraftMP\resourcepacks'"
echo.
pause

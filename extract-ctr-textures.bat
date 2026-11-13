@echo off
rem Extracts CTR's original textures (crates, power-up icons, Wumpa fruit, start lamps) from YOUR OWN disc image into two local resource packs
rem (Minecraft size, and original size; see tools\extract_ctr_textures.py) and copies them into the test instances' resourcepacks folders.
rem The packs stay on this machine (git-ignored, never in the jar). Enable ONE of them in Options > Resource Packs.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location '%~dp0'; python tools\extract_ctr_textures.py --install 'F:\ATLauncher\instances\CTRCraft\resourcepacks' --install 'F:\ATLauncher\instances\CTRCraftMP\resourcepacks'"
echo.
pause
